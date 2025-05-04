package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Handler for spending limits related endpoints.
 * Provides functionality to create, retrieve, update, and delete spending limits.
 */
public class SpendingLimitsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("SpendingLimitsHandler invoked: " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            System.out.println("401 - Missing idToken/localId in cookies");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        
        if (idToken == null || localId == null) {
            System.out.println("401 - null tokens");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        if (path.equals("/api/spending-limits") && "GET".equalsIgnoreCase(method)) {
            handleGetSpendingLimits(exchange, idToken, localId);
        } else if (path.equals("/api/spending-limits") && "POST".equalsIgnoreCase(method)) {
            handleCreateSpendingLimit(exchange, idToken, localId);
        } else if (path.matches("/api/spending-limits/[^/]+") && "PUT".equalsIgnoreCase(method)) {
            String limitId = path.substring(path.lastIndexOf('/') + 1);
            handleUpdateSpendingLimit(exchange, idToken, localId, limitId);
        } else if (path.matches("/api/spending-limits/[^/]+") && "DELETE".equalsIgnoreCase(method)) {
            String limitId = path.substring(path.lastIndexOf('/') + 1);
            handleDeleteSpendingLimit(exchange, idToken, localId, limitId);
        } else if (path.equals("/api/spending-limits/check") && "POST".equalsIgnoreCase(method)) {
            handleCheckSpendingLimits(exchange, idToken, localId);
        } else {
            System.out.println("404 - Unsupported path/method: " + path + " " + method);
            exchange.sendResponseHeaders(404, -1);
        }
    }
    
    /**
     * Handle GET request to retrieve all spending limits
     */
    private void handleGetSpendingLimits(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/SpendingLimits";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                // Format response
                JSONObject firebaseResponse = new JSONObject(response.toString());
                JSONArray limits = new JSONArray();
                
                if (firebaseResponse.has("documents")) {
                    JSONArray documents = firebaseResponse.getJSONArray("documents");
                    
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        JSONObject limit = new JSONObject();
                        
                        // Extract document ID from the name
                        String name = document.getString("name");
                        String id = name.substring(name.lastIndexOf('/') + 1);
                        limit.put("id", id);
                        
                        if (fields.has("category")) {
                            limit.put("category", fields.getJSONObject("category").getString("stringValue"));
                        }
                        
                        if (fields.has("amount")) {
                            if (fields.getJSONObject("amount").has("doubleValue")) {
                                limit.put("amount", fields.getJSONObject("amount").getDouble("doubleValue"));
                            } else if (fields.getJSONObject("amount").has("integerValue")) {
                                limit.put("amount", fields.getJSONObject("amount").getInt("integerValue"));
                            }
                        }
                        
                        if (fields.has("period")) {
                            limit.put("period", fields.getJSONObject("period").getString("stringValue"));
                        }
                        
                        if (fields.has("startDate")) {
                            limit.put("startDate", fields.getJSONObject("startDate").getString("stringValue"));
                        }
                        
                        if (fields.has("description")) {
                            limit.put("description", fields.getJSONObject("description").getString("stringValue"));
                        }
                        
                        // Calculate current spending for this limit
                        double currentSpending = getCurrentSpending(idToken, localId, fields);
                        limit.put("currentSpending", currentSpending);
                        
                        // Calculate percentage
                        double amount = limit.getDouble("amount");
                        double percentage = amount > 0 ? (currentSpending / amount) * 100 : 0;
                        limit.put("percentage", Math.min(percentage, 100)); // Cap at 100%
                        
                        limits.put(limit);
                    }
                }
                
                JSONObject appResponse = new JSONObject();
                appResponse.put("limits", limits);
                
                byte[] responseBytes = appResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                // If no spending limits, return an empty array
                if (responseCode == 404) {
                    JSONObject emptyResponse = new JSONObject();
                    emptyResponse.put("limits", new JSONArray());
                    
                    byte[] responseBytes = emptyResponse.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                } else {
                    System.out.println("Error fetching spending limits: " + responseCode);
                    exchange.sendResponseHeaders(responseCode, -1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    /**
     * Handle POST request to create a new spending limit
     */
    private void handleCreateSpendingLimit(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Read request body
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            
            // Parse the limit data
            JSONObject limitData = new JSONObject(requestBody.toString());
            
            // Generate a unique ID
            String limitId = UUID.randomUUID().toString();
            
            // Create the Firestore document
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("category", new JSONObject().put("stringValue", limitData.getString("category")));
            fields.put("amount", new JSONObject().put("doubleValue", limitData.getDouble("amount")));
            fields.put("period", new JSONObject().put("stringValue", limitData.getString("period")));
            
            // Set start date if provided, otherwise use current date
            String startDate = limitData.optString("startDate", LocalDate.now().toString());
            fields.put("startDate", new JSONObject().put("stringValue", startDate));
            
            // Optional description
            String description = limitData.optString("description", "");
            fields.put("description", new JSONObject().put("stringValue", description));
            
            // Add created timestamp
            fields.put("created", new JSONObject().put("timestampValue", Instant.now().toString()));
            
            document.put("fields", fields);
            
            // Save to Firestore
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/SpendingLimits/" + limitId;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            OutputStream os = conn.getOutputStream();
            os.write(document.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 201) {
                JSONObject successResponse = new JSONObject();
                successResponse.put("success", true);
                successResponse.put("id", limitId);
                
                byte[] responseBytes = successResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream responseOs = exchange.getResponseBody();
                responseOs.write(responseBytes);
                responseOs.close();
            } else {
                System.out.println("Error creating spending limit: " + responseCode);
                exchange.sendResponseHeaders(responseCode, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "{\"error\":\"" + e.getMessage() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorMessage.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorMessage.getBytes());
            os.close();
        }
    }
    
    /**
     * Handle PUT request to update an existing spending limit
     */
    private void handleUpdateSpendingLimit(HttpExchange exchange, String idToken, String localId, String limitId) throws IOException {
        try {
            // Read request body
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            
            // Parse the limit data
            JSONObject limitData = new JSONObject(requestBody.toString());
            
            // Create the update data
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            
            // Only update the fields that are provided
            if (limitData.has("category")) {
                fields.put("category", new JSONObject().put("stringValue", limitData.getString("category")));
            }
            
            if (limitData.has("amount")) {
                fields.put("amount", new JSONObject().put("doubleValue", limitData.getDouble("amount")));
            }
            
            if (limitData.has("period")) {
                fields.put("period", new JSONObject().put("stringValue", limitData.getString("period")));
            }
            
            if (limitData.has("startDate")) {
                fields.put("startDate", new JSONObject().put("stringValue", limitData.getString("startDate")));
            }
            
            if (limitData.has("description")) {
                fields.put("description", new JSONObject().put("stringValue", limitData.getString("description")));
            }
            
            // Add updated timestamp
            fields.put("updated", new JSONObject().put("timestampValue", Instant.now().toString()));
            
            document.put("fields", fields);
            
            // Update in Firestore
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/SpendingLimits/" + limitId;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            OutputStream os = conn.getOutputStream();
            os.write(document.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                JSONObject successResponse = new JSONObject();
                successResponse.put("success", true);
                
                byte[] responseBytes = successResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream responseOs = exchange.getResponseBody();
                responseOs.write(responseBytes);
                responseOs.close();
            } else {
                System.out.println("Error updating spending limit: " + responseCode);
                exchange.sendResponseHeaders(responseCode, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "{\"error\":\"" + e.getMessage() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorMessage.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorMessage.getBytes());
            os.close();
        }
    }
    
    /**
     * Handle DELETE request to delete a spending limit
     */
    private void handleDeleteSpendingLimit(HttpExchange exchange, String idToken, String localId, String limitId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/SpendingLimits/" + limitId;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                JSONObject successResponse = new JSONObject();
                successResponse.put("success", true);
                
                byte[] responseBytes = successResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                System.out.println("Error deleting spending limit: " + responseCode);
                exchange.sendResponseHeaders(responseCode, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    /**
     * Handle POST request to check all spending limits and create alerts for any that are over the limit
     */
    private void handleCheckSpendingLimits(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/SpendingLimits";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                JSONObject firebaseResponse = new JSONObject(response.toString());
                JSONArray checkedLimits = new JSONArray();
                
                if (firebaseResponse.has("documents")) {
                    JSONArray documents = firebaseResponse.getJSONArray("documents");
                    
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        // Extract document ID
                        String name = document.getString("name");
                        String limitId = name.substring(name.lastIndexOf('/') + 1);
                        
                        // Get current spending
                        double currentSpending = getCurrentSpending(idToken, localId, fields);
                        
                        // Get limit amount
                        double limitAmount = 0;
                        if (fields.has("amount")) {
                            if (fields.getJSONObject("amount").has("doubleValue")) {
                                limitAmount = fields.getJSONObject("amount").getDouble("doubleValue");
                            } else if (fields.getJSONObject("amount").has("integerValue")) {
                                limitAmount = fields.getJSONObject("amount").getInt("integerValue");
                            }
                        }
                        
                        // Get category
                        String category = fields.has("category") ? 
                                fields.getJSONObject("category").getString("stringValue") : "Uncategorized";
                        
                        // Get period
                        String period = fields.has("period") ? 
                                fields.getJSONObject("period").getString("stringValue") : "monthly";
                        
                        // Check if over limit
                        boolean overLimit = currentSpending > limitAmount;
                        
                        // Create alert if over limit
                        if (overLimit) {
                            createSpendingLimitAlert(idToken, localId, category, limitAmount, currentSpending, period);
                        }
                        
                        // Add to checked limits
                        JSONObject checkedLimit = new JSONObject();
                        checkedLimit.put("id", limitId);
                        checkedLimit.put("category", category);
                        checkedLimit.put("amount", limitAmount);
                        checkedLimit.put("currentSpending", currentSpending);
                        checkedLimit.put("period", period);
                        checkedLimit.put("overLimit", overLimit);
                        
                        checkedLimits.put(checkedLimit);
                    }
                }
                
                JSONObject successResponse = new JSONObject();
                successResponse.put("success", true);
                successResponse.put("checkedLimits", checkedLimits);
                
                byte[] responseBytes = successResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                // If no spending limits, return empty response
                if (responseCode == 404) {
                    JSONObject emptyResponse = new JSONObject();
                    emptyResponse.put("success", true);
                    emptyResponse.put("checkedLimits", new JSONArray());
                    
                    byte[] responseBytes = emptyResponse.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                } else {
                    System.out.println("Error checking spending limits: " + responseCode);
                    exchange.sendResponseHeaders(responseCode, -1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "{\"error\":\"" + e.getMessage() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorMessage.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorMessage.getBytes());
            os.close();
        }
    }
    
    /**
     * Calculate the current spending for a given limit
     */
    private double getCurrentSpending(String idToken, String localId, JSONObject limitFields) throws Exception {
        double totalSpending = 0;
        
        // Get category
        String category = "";
        if (limitFields.has("category")) {
            category = limitFields.getJSONObject("category").getString("stringValue");
        }
        
        // Get period
        String period = "monthly";
        if (limitFields.has("period")) {
            period = limitFields.getJSONObject("period").getString("stringValue");
        }
        
        // Get start date
        LocalDate startDate = LocalDate.now().withDayOfMonth(1); // Default to first day of current month
        if (limitFields.has("startDate")) {
            startDate = LocalDate.parse(limitFields.getJSONObject("startDate").getString("stringValue"));
        }
        
        // Calculate date range for the period
        LocalDate startOfPeriod;
        LocalDate endOfPeriod = LocalDate.now();
        
        switch (period) {
            case "daily":
                startOfPeriod = endOfPeriod;
                break;
            case "weekly":
                startOfPeriod = endOfPeriod.minusDays(endOfPeriod.getDayOfWeek().getValue() - 1);
                break;
            case "monthly":
                startOfPeriod = endOfPeriod.withDayOfMonth(1);
                break;
            case "quarterly":
                int monthOffset = (endOfPeriod.getMonthValue() - 1) % 3;
                startOfPeriod = endOfPeriod.withDayOfMonth(1).minusMonths(monthOffset);
                break;
            case "yearly":
                startOfPeriod = endOfPeriod.withDayOfYear(1);
                break;
            case "custom":
                startOfPeriod = startDate;
                break;
            default:
                startOfPeriod = endOfPeriod.withDayOfMonth(1); // Default to monthly
        }
        
        // Make sure we don't go earlier than the start date
        if (startOfPeriod.isBefore(startDate)) {
            startOfPeriod = startDate;
        }
        
        // Get expenses in the date range
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/Expenses";
        
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            JSONObject firebaseResponse = new JSONObject(response.toString());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            if (firebaseResponse.has("documents")) {
                JSONArray documents = firebaseResponse.getJSONArray("documents");
                
                for (int i = 0; i < documents.length(); i++) {
                    JSONObject document = documents.getJSONObject(i);
                    JSONObject fields = document.getJSONObject("fields");
                    
                    // Check category match if category is specified
                    if (!category.isEmpty() && fields.has("category")) {
                        String expenseCategory = fields.getJSONObject("category").getString("stringValue");
                        if (!category.equalsIgnoreCase(expenseCategory) && !category.equalsIgnoreCase("All")) {
                            continue;
                        }
                    }
                    
                    // Check date within period
                    if (fields.has("date")) {
                        LocalDate expenseDate = LocalDate.parse(fields.getJSONObject("date").getString("stringValue"), formatter);
                        
                        if ((expenseDate.isEqual(startOfPeriod) || expenseDate.isAfter(startOfPeriod)) && 
                            (expenseDate.isEqual(endOfPeriod) || expenseDate.isBefore(endOfPeriod))) {
                            
                            // Add amount to total
                            if (fields.has("amount")) {
                                if (fields.getJSONObject("amount").has("doubleValue")) {
                                    totalSpending += fields.getJSONObject("amount").getDouble("doubleValue");
                                } else if (fields.getJSONObject("amount").has("integerValue")) {
                                    totalSpending += fields.getJSONObject("amount").getInt("integerValue");
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return totalSpending;
    }
    
    /**
     * Create an alert for a spending limit that has been exceeded
     */
    private void createSpendingLimitAlert(String idToken, String localId, String category, double limitAmount, 
                                        double currentSpending, String period) {
        try {
            // Format period for display
            String periodDisplay;
            switch (period) {
                case "daily": periodDisplay = "daily"; break;
                case "weekly": periodDisplay = "weekly"; break;
                case "monthly": periodDisplay = "monthly"; break;
                case "quarterly": periodDisplay = "quarterly"; break;
                case "yearly": periodDisplay = "yearly"; break;
                case "custom": periodDisplay = "custom period"; break;
                default: periodDisplay = period;
            }
            
            String alertId = UUID.randomUUID().toString();
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            
            String title = "Spending Limit Exceeded: " + category;
            String message = String.format(
                    "Your %s spending in the category '%s' has reached $%.2f, which exceeds your %s limit of $%.2f.",
                    periodDisplay, category, currentSpending, periodDisplay, limitAmount);
            
            fields.put("title", new JSONObject().put("stringValue", title));
            fields.put("message", new JSONObject().put("stringValue", message));
            fields.put("type", new JSONObject().put("stringValue", "spending-limit"));
            fields.put("created", new JSONObject().put("timestampValue", Instant.now().toString()));
            fields.put("read", new JSONObject().put("booleanValue", false));
            
            document.put("fields", fields);
            
            // Save to Firestore
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Alerts/" + alertId;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            OutputStream os = conn.getOutputStream();
            os.write(document.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && responseCode != 201) {
                System.out.println("Error creating spending limit alert: " + responseCode);
            }
        } catch (Exception e) {
            System.out.println("Error creating spending limit alert: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Extract a cookie value from the cookie string
     */
    private static String extractCookieValue(String cookies, String name) {
        if (cookies == null) {
            return null;
        }
        
        String[] parts = cookies.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring((name + "=").length());
            }
        }
        return null;
    }
}
