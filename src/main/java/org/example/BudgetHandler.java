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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class BudgetHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("BudgetHandler invoked: " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
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
        
        try {
            // Handle budgets endpoints
            if (path.equals("/api/budgets")) {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    getBudgets(exchange, idToken, localId);
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    createBudget(exchange, idToken, localId);
                } else {
                    exchange.sendResponseHeaders(405, -1); // Method not allowed
                }
            } else if (path.matches("/api/budgets/[^/]+")) {
                String budgetId = path.substring("/api/budgets/".length());
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    getBudget(exchange, idToken, localId, budgetId);
                } else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                    updateBudget(exchange, idToken, localId, budgetId);
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    deleteBudget(exchange, idToken, localId, budgetId);
                } else {
                    exchange.sendResponseHeaders(405, -1); // Method not allowed
                }
            } 
            // Handle spending limits endpoints
            else if (path.equals("/api/limits")) {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    getLimits(exchange, idToken, localId);
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    createLimit(exchange, idToken, localId);
                } else {
                    exchange.sendResponseHeaders(405, -1); // Method not allowed
                }
            } else if (path.matches("/api/limits/[^/]+")) {
                String limitId = path.substring("/api/limits/".length());
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    getLimit(exchange, idToken, localId, limitId);
                } else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                    updateLimit(exchange, idToken, localId, limitId);
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    deleteLimit(exchange, idToken, localId, limitId);
                } else {
                    exchange.sendResponseHeaders(405, -1); // Method not allowed
                }
            }
            // Handle paychecks endpoints
            else if (path.equals("/api/paychecks")) {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    getPaychecks(exchange, idToken, localId);
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    createPaycheck(exchange, idToken, localId);
                } else {
                    exchange.sendResponseHeaders(405, -1); // Method not allowed
                }
            } else if (path.matches("/api/paychecks/[^/]+")) {
                String paycheckId = path.substring("/api/paychecks/".length());
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    getPaycheck(exchange, idToken, localId, paycheckId);
                } else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                    updatePaycheck(exchange, idToken, localId, paycheckId);
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    deletePaycheck(exchange, idToken, localId, paycheckId);
                } else {
                    exchange.sendResponseHeaders(405, -1); // Method not allowed
                }
            } else {
                exchange.sendResponseHeaders(404, -1); // Not found
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errorResponse = "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(500, errorResponse.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }
    
    // Helper method to extract cookie value
    private String extractCookieValue(String cookies, String name) {
        String[] parts = cookies.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring((name + "=").length());
            }
        }
        return null;
    }
    
    // Budget Handlers
    private void getBudgets(HttpExchange exchange, String idToken, String localId) throws IOException {
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/Budgets";
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
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else if (responseCode == 404) {
            // No budgets collection exists yet, return empty list
            String emptyResponse = "{\"documents\":[]}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, emptyResponse.length());
            OutputStream os = exchange.getResponseBody();
            os.write(emptyResponse.getBytes(StandardCharsets.UTF_8));
            os.close();
        } else {
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    private void getBudget(HttpExchange exchange, String idToken, String localId, String budgetId) throws IOException {
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/Budgets/" + budgetId;
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
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else {
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    private void createBudget(HttpExchange exchange, String idToken, String localId) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }
        
        JSONObject budgetData = new JSONObject(requestBody.toString());
        String budgetId = UUID.randomUUID().toString();
        
        // Required fields check
        if (!budgetData.has("amount") || !budgetData.has("startDate") || !budgetData.has("alertThreshold")) {
            exchange.sendResponseHeaders(400, -1); // Bad request - missing required fields
            return;
        }
        
        // Transform JSON into Firestore format
        JSONObject firestoreData = new JSONObject();
        JSONObject fields = new JSONObject();
        
        // Amount field
        JSONObject amountField = new JSONObject();
        amountField.put("doubleValue", budgetData.getDouble("amount"));
        fields.put("amount", amountField);
        
        // Start date field (convert to timestamp)
        JSONObject startDateField = new JSONObject();
        String startDate = budgetData.getString("startDate");
        LocalDate parsedDate = LocalDate.parse(startDate);
        Instant startDateInstant = parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        startDateField.put("timestampValue", startDateInstant.toString());
        fields.put("startDate", startDateField);
        
        // Alert threshold field
        JSONObject alertThresholdField = new JSONObject();
        alertThresholdField.put("integerValue", budgetData.getInt("alertThreshold"));
        fields.put("alertThreshold", alertThresholdField);
        
        // Notes field (optional)
        if (budgetData.has("notes") && !budgetData.isNull("notes")) {
            JSONObject notesField = new JSONObject();
            notesField.put("stringValue", budgetData.getString("notes"));
            fields.put("notes", notesField);
        }
        
        // Spent field (default: 0)
        JSONObject spentField = new JSONObject();
        spentField.put("doubleValue", 0.0);
        fields.put("spent", spentField);
        
        // Created timestamp field
        JSONObject createdField = new JSONObject();
        createdField.put("timestampValue", Instant.now().toString());
        fields.put("created", createdField);
        
        firestoreData.put("fields", fields);
        
        // Save to Firestore
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/Budgets/" + budgetId;
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");  // Use PUT with a specific document ID
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setDoOutput(true);
        
        OutputStream os = conn.getOutputStream();
        os.write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            // Create a budget alert
            createBudgetAlert(idToken, localId, budgetId, budgetData.getDouble("amount"), 
                    budgetData.getInt("alertThreshold"));
            
            // Return success response
            String successResponse = "{\"status\":\"success\",\"id\":\"" + budgetId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, successResponse.length());
            OutputStream respOs = exchange.getResponseBody();
            respOs.write(successResponse.getBytes(StandardCharsets.UTF_8));
            respOs.close();
        } else {
            // Handle error
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                BufferedReader errorReader = new BufferedReader(isrError);
                StringBuilder errorResponse = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
                System.out.println("Firestore error: " + errorResponse.toString());
            }
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    private void updateBudget(HttpExchange exchange, String idToken, String localId, String budgetId) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }
        
        JSONObject budgetData = new JSONObject(requestBody.toString());
        
        // Transform JSON into Firestore format
        JSONObject firestoreData = new JSONObject();
        JSONObject fields = new JSONObject();
        
        // Required fields check
        if (!budgetData.has("amount") || !budgetData.has("startDate") || !budgetData.has("alertThreshold")) {
            exchange.sendResponseHeaders(400, -1); // Bad request - missing required fields
            return;
        }
        
        // Amount field
        JSONObject amountField = new JSONObject();
        amountField.put("doubleValue", budgetData.getDouble("amount"));
        fields.put("amount", amountField);
        
        // Start date field (convert to timestamp)
        JSONObject startDateField = new JSONObject();
        String startDate = budgetData.getString("startDate");
        LocalDate parsedDate = LocalDate.parse(startDate);
        Instant startDateInstant = parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        startDateField.put("timestampValue", startDateInstant.toString());
        fields.put("startDate", startDateField);
        
        // Alert threshold field
        JSONObject alertThresholdField = new JSONObject();
        alertThresholdField.put("integerValue", budgetData.getInt("alertThreshold"));
        fields.put("alertThreshold", alertThresholdField);
        
        // Notes field (optional)
        if (budgetData.has("notes") && !budgetData.isNull("notes")) {
            JSONObject notesField = new JSONObject();
            notesField.put("stringValue", budgetData.getString("notes"));
            fields.put("notes", notesField);
        }
        
        // Spent field (if provided)
        if (budgetData.has("spent") && !budgetData.isNull("spent")) {
            JSONObject spentField = new JSONObject();
            spentField.put("doubleValue", budgetData.getDouble("spent"));
            fields.put("spent", spentField);
        }
        
        // Updated timestamp field
        JSONObject updatedField = new JSONObject();
        updatedField.put("timestampValue", Instant.now().toString());
        fields.put("updated", updatedField);
        
        firestoreData.put("fields", fields);
        
        // Save to Firestore
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/Budgets/" + budgetId;
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PATCH");  // Use PATCH to update
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setDoOutput(true);
        
        OutputStream os = conn.getOutputStream();
        os.write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            // Update the budget alert
            updateBudgetAlert(idToken, localId, budgetId, budgetData.getDouble("amount"), 
                    budgetData.getInt("alertThreshold"));
            
            // Return success response
            String successResponse = "{\"status\":\"success\",\"id\":\"" + budgetId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, successResponse.length());
            OutputStream respOs = exchange.getResponseBody();
            respOs.write(successResponse.getBytes(StandardCharsets.UTF_8));
            respOs.close();
        } else {
            // Handle error
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    private void deleteBudget(HttpExchange exchange, String idToken, String localId, String budgetId) throws IOException {
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/Budgets/" + budgetId;
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            // Delete any associated alerts
            deleteAlertsForBudget(idToken, localId, budgetId);
            
            // Return success response
            String successResponse = "{\"status\":\"success\",\"message\":\"Budget deleted successfully\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, successResponse.length());
            OutputStream os = exchange.getResponseBody();
            os.write(successResponse.getBytes(StandardCharsets.UTF_8));
            os.close();
        } else {
            // Handle error
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    // Spending Limit Handlers
    private void getLimits(HttpExchange exchange, String idToken, String localId) throws IOException {
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
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else if (responseCode == 404) {
            // No limits collection exists yet, return empty list
            String emptyResponse = "{\"documents\":[]}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, emptyResponse.length());
            OutputStream os = exchange.getResponseBody();
            os.write(emptyResponse.getBytes(StandardCharsets.UTF_8));
            os.close();
        } else {
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    private void getLimit(HttpExchange exchange, String idToken, String localId, String limitId) throws IOException {
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/SpendingLimits/" + limitId;
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
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else {
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }

    private void createLimit(HttpExchange exchange, String idToken, String localId) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        JSONObject limitData = new JSONObject(sb.toString());

        if (!limitData.has("category") || !limitData.has("amount") || !limitData.has("alertThreshold")) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        String limitId = UUID.randomUUID().toString();
        JSONObject firestoreData = new JSONObject();
        JSONObject fields = new JSONObject();

        JSONObject catField = new JSONObject();
        catField.put("stringValue", limitData.getString("category"));
        fields.put("category", catField);

        JSONObject amountField = new JSONObject();
        amountField.put("doubleValue", limitData.getDouble("amount"));
        fields.put("amount", amountField);

        JSONObject alertField = new JSONObject();
        alertField.put("integerValue", limitData.getInt("alertThreshold"));
        fields.put("alertThreshold", alertField);

        fields.put("created", new JSONObject().put("timestampValue", Instant.now().toString()));
        firestoreData.put("fields", fields);

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/SpendingLimits/" + limitId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        if (code == 200) {
            createLimitAlert(idToken, localId, limitId,
                    limitData.getString("category"),
                    limitData.getDouble("amount"),
                    limitData.getInt("alertThreshold"));

            String response = "{\"status\":\"success\",\"id\":\"" + limitId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private void updateLimit(HttpExchange exchange, String idToken, String localId, String limitId) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        JSONObject limitData = new JSONObject(sb.toString());

        JSONObject fields = new JSONObject();

        if (limitData.has("category")) {
            fields.put("category", new JSONObject().put("stringValue", limitData.getString("category")));
        }
        if (limitData.has("amount")) {
            fields.put("amount", new JSONObject().put("doubleValue", limitData.getDouble("amount")));
        }
        if (limitData.has("alertThreshold")) {
            fields.put("alertThreshold", new JSONObject().put("integerValue", limitData.getInt("alertThreshold")));
        }
        fields.put("updated", new JSONObject().put("timestampValue", Instant.now().toString()));

        JSONObject firestoreData = new JSONObject().put("fields", fields);

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/SpendingLimits/" + limitId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        if (code == 200) {
            String response = "{\"status\":\"success\",\"id\":\"" + limitId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }
    private void deleteLimit(HttpExchange exchange, String idToken, String localId, String limitId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/SpendingLimits/" + limitId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);

        int code = conn.getResponseCode();
        if (code == 200 || code == 204) {
            String success = "{\"status\":\"success\",\"message\":\"Limit deleted successfully\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, success.length());
            exchange.getResponseBody().write(success.getBytes(StandardCharsets.UTF_8));
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }

    // Paycheck Handlers
    private void getPaychecks(HttpExchange exchange, String idToken, String localId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Paychecks";
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);

        int code = conn.getResponseCode();
        if (code == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            in.close();

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] data = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
        } else if (code == 404) {
            String empty = "{\"documents\":[]}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, empty.length());
            exchange.getResponseBody().write(empty.getBytes(StandardCharsets.UTF_8));
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }


    private void getPaycheck(HttpExchange exchange, String idToken, String localId, String paycheckId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Paychecks/" + paycheckId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);

        int code = conn.getResponseCode();
        if (code == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            in.close();

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] data = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }


    private void createPaycheck(HttpExchange exchange, String idToken, String localId) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        JSONObject data = new JSONObject(sb.toString());

        if (!data.has("amount") || !data.has("date") || !data.has("employer")) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        String paycheckId = UUID.randomUUID().toString();
        JSONObject fields = new JSONObject();

        fields.put("amount", new JSONObject().put("doubleValue", data.getDouble("amount")));
        fields.put("employer", new JSONObject().put("stringValue", data.getString("employer")));

        LocalDate parsedDate = LocalDate.parse(data.getString("date"));
        Instant dateInstant = parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        fields.put("date", new JSONObject().put("timestampValue", dateInstant.toString()));

        fields.put("created", new JSONObject().put("timestampValue", Instant.now().toString()));

        JSONObject firestoreData = new JSONObject().put("fields", fields);

        String url = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Paychecks/" + paycheckId;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        if (code == 200) {
            String response = "{\"status\":\"success\",\"id\":\"" + paycheckId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }


    private void updatePaycheck(HttpExchange exchange, String idToken, String localId, String paycheckId) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        JSONObject data = new JSONObject(sb.toString());

        JSONObject fields = new JSONObject();

        if (data.has("amount")) {
            fields.put("amount", new JSONObject().put("doubleValue", data.getDouble("amount")));
        }
        if (data.has("employer")) {
            fields.put("employer", new JSONObject().put("stringValue", data.getString("employer")));
        }
        if (data.has("date")) {
            LocalDate parsedDate = LocalDate.parse(data.getString("date"));
            Instant dateInstant = parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            fields.put("date", new JSONObject().put("timestampValue", dateInstant.toString()));
        }
        fields.put("updated", new JSONObject().put("timestampValue", Instant.now().toString()));

        JSONObject firestoreData = new JSONObject().put("fields", fields);

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Paychecks/" + paycheckId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        if (code == 200) {
            String response = "{\"status\":\"success\",\"id\":\"" + paycheckId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }


    private void deletePaycheck(HttpExchange exchange, String idToken, String localId, String paycheckId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Paychecks/" + paycheckId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);

        int code = conn.getResponseCode();
        if (code == 200 || code == 204) {
            String response = "{\"status\":\"success\",\"message\":\"Paycheck deleted successfully\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private void createBudgetAlert(String idToken, String localId, String budgetId, double amount, int alertThreshold) throws IOException {
        String alertId = "budget_" + budgetId;
        JSONObject fields = new JSONObject();

        fields.put("type", new JSONObject().put("stringValue", "budget"));
        fields.put("targetId", new JSONObject().put("stringValue", budgetId));
        fields.put("threshold", new JSONObject().put("integerValue", alertThreshold));
        fields.put("targetAmount", new JSONObject().put("doubleValue", amount));
        fields.put("created", new JSONObject().put("timestampValue", Instant.now().toString()));

        JSONObject alertDoc = new JSONObject().put("fields", fields);

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Alerts/" + alertId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(alertDoc.toString().getBytes(StandardCharsets.UTF_8));
        conn.getResponseCode(); // force execution, discard result
    }


    private void updateBudgetAlert(String idToken, String localId, String budgetId, double amount, int alertThreshold) throws IOException {
        String alertId = "budget_" + budgetId;
        JSONObject fields = new JSONObject();

        fields.put("threshold", new JSONObject().put("integerValue", alertThreshold));
        fields.put("targetAmount", new JSONObject().put("doubleValue", amount));
        fields.put("updated", new JSONObject().put("timestampValue", Instant.now().toString()));

        JSONObject alertPatch = new JSONObject().put("fields", fields);

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Alerts/" + alertId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(alertPatch.toString().getBytes(StandardCharsets.UTF_8));
        conn.getResponseCode();
    }


    private void createLimitAlert(String idToken, String localId, String limitId, String category, double amount, int alertThreshold) throws IOException {
        String alertId = "limit_" + limitId;
        JSONObject fields = new JSONObject();

        fields.put("type", new JSONObject().put("stringValue", "limit"));
        fields.put("targetId", new JSONObject().put("stringValue", limitId));
        fields.put("category", new JSONObject().put("stringValue", category));
        fields.put("threshold", new JSONObject().put("integerValue", alertThreshold));
        fields.put("targetAmount", new JSONObject().put("doubleValue", amount));
        fields.put("created", new JSONObject().put("timestampValue", Instant.now().toString()));

        JSONObject alertDoc = new JSONObject().put("fields", fields);

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Alerts/" + alertId;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(alertDoc.toString().getBytes(StandardCharsets.UTF_8));
        conn.getResponseCode();
    }


    private void deleteAlertsForBudget(String idToken, String localId, String budgetId) throws IOException {
        String alertId = "budget_" + budgetId;
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Alerts/" + alertId;

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.getResponseCode();
    }

}
