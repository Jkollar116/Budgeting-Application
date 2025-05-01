package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.*;

public class ExpensesHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("ExpensesHandler invoked: " + exchange.getRequestMethod());
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        if (idToken == null || localId == null) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            handleGet(exchange, idToken, localId);
        } else if ("POST".equalsIgnoreCase(method)) {
            handlePost(exchange, idToken, localId);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            handleDelete(exchange, idToken, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleGet(HttpExchange exchange, String idToken, String localId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Expenses";
        HttpURLConnection conn = openFirestoreConnection(urlStr, "GET", idToken);
        int code = conn.getResponseCode();
        System.out.println("Firestore GET response code: " + code);
        if (code == 200) {
            byte[] resp = readAll(conn.getInputStream()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private void handlePost(HttpExchange exchange, String idToken, String localId) throws IOException {
        String body = readAll(exchange.getRequestBody());
        String date = getJsonValue(body, "date");
        String name = getJsonValue(body, "name");
        String category = getJsonValue(body, "category");
        String totalStr = getJsonValue(body, "total");
        double total;
        try {
            total = Double.parseDouble(totalStr);
        } catch (Exception e) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        
        // Parse date to extract year and month for easier querying
        LocalDate parsedDate = tryParseDate(date);
        String yearMonthField = "";
        String yearField = "";
        
        if (parsedDate != null) {
            int year = parsedDate.getYear();
            int month = parsedDate.getMonthValue();
            yearMonthField = year + "_" + (month < 10 ? "0" : "") + month;
            yearField = String.valueOf(year);
        }
        
        // Generate a unique transaction ID for better tracking
        String transactionId = "exp_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 1000);
        
        // Current timestamp for created/updated metadata
        long currentTimestamp = System.currentTimeMillis();
        
        String json = "{\"fields\":{"
                + "\"date\":{\"stringValue\":\"" + date + "\"},"
                + "\"name\":{\"stringValue\":\"" + name + "\"},"
                + "\"category\":{\"stringValue\":\"" + category + "\"},"
                + "\"total\":{\"doubleValue\":" + total + "},"
                + "\"yearMonth\":{\"stringValue\":\"" + yearMonthField + "\"},"
                + "\"year\":{\"stringValue\":\"" + yearField + "\"},"
                + "\"transactionId\":{\"stringValue\":\"" + transactionId + "\"},"
                + "\"createdAt\":{\"integerValue\":" + currentTimestamp + "},"
                + "\"updatedAt\":{\"integerValue\":" + currentTimestamp + "}"
                + "}}";

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Expenses";
        HttpURLConnection conn = openFirestoreConnection(urlStr, "POST", idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        System.out.println("Firestore POST response code: " + code);
        if (code == 200 || code == 201) {
            // After successfully adding the expense, update the monthly summary
            updateMonthlySummary(idToken, localId, parsedDate, total, category);
            
            byte[] msg = "Expense added successfully.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, msg.length);
            exchange.getResponseBody().write(msg);
        } else {
            logError(conn);
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private void handleDelete(HttpExchange exchange, String idToken, String localId) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String docId = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("docId=")) {
                    docId = URLDecoder.decode(param.substring(6), "UTF-8");
                    break;
                }
            }
        }
        if (docId == null || docId.isEmpty()) {
            System.out.println("DELETE missing docId parameter");
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Expenses/" + docId;
        System.out.println("Firestore DELETE URL: " + urlStr);
        HttpURLConnection conn = openFirestoreConnection(urlStr, "DELETE", idToken);
        int code = conn.getResponseCode();
        System.out.println("Firestore DELETE response code: " + code);
        if (code == 200 || code == 204) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            logError(conn);
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private HttpURLConnection openFirestoreConnection(String urlStr, String method, String idToken) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        return conn;
    }

    private String readAll(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private void logError(HttpURLConnection conn) throws IOException {
        InputStream err = conn.getErrorStream();
        if (err != null) {
            System.out.println("Firestore error: " + readAll(err));
        }
    }

    private static String extractCookieValue(String cookies, String name) {
        for (String part : cookies.split(";")) {
            String t = part.trim();
            if (t.startsWith(name + "=")) {
                return t.substring(name.length() + 1);
            }
        }
        return null;
    }

    /**
     * Updates the monthly summary document in Firestore for faster dashboard reads.
     * Creates or updates a document in the Summaries collection with pre-aggregated expense data.
     */
    private void updateMonthlySummary(String idToken, String localId, LocalDate date, double amount, String category) {
        if (date == null) {
            System.out.println("Cannot update monthly summary: Invalid date");
            return;
        }
        
        try {
            int year = date.getYear();
            int month = date.getMonthValue();
            String yearMonth = year + "_" + (month < 10 ? "0" : "") + month;
            
            // Firestore URL for the monthly summary document
            String summaryUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Summaries/expense_" + yearMonth;
            
            // First check if the document exists
            URL getUrl = new URL(summaryUrl);
            HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
            getConn.setRequestMethod("GET");
            getConn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            boolean documentExists = false;
            double existingTotal = 0.0;
            int existingCount = 0;
            JsonObject existingCategories = new JsonObject();
            
            int getResponseCode = getConn.getResponseCode();
            if (getResponseCode == 200) {
                // Document exists, parse the current data
                documentExists = true;
                String response = readAll(getConn.getInputStream());
                JsonObject root = JsonParser.parseString(response).getAsJsonObject();
                if (root.has("fields")) {
                    JsonObject fields = root.getAsJsonObject("fields");
                    
                    if (fields.has("totalExpense") && fields.getAsJsonObject("totalExpense").has("doubleValue")) {
                        existingTotal = fields.getAsJsonObject("totalExpense").get("doubleValue").getAsDouble();
                    }
                    
                    if (fields.has("expenseCount") && fields.getAsJsonObject("expenseCount").has("integerValue")) {
                        existingCount = fields.getAsJsonObject("expenseCount").get("integerValue").getAsInt();
                    }
                    
                    if (fields.has("categories") && fields.getAsJsonObject("categories").has("mapValue") && 
                        fields.getAsJsonObject("categories").getAsJsonObject("mapValue").has("fields")) {
                        existingCategories = fields.getAsJsonObject("categories")
                                                  .getAsJsonObject("mapValue")
                                                  .getAsJsonObject("fields");
                    }
                }
            }
            
            // Calculate new total and count
            double newTotal = existingTotal + amount;
            int newCount = existingCount + 1;
            
            // Update category totals
            double categoryTotal = 0.0;
            if (existingCategories.has(category) && 
                existingCategories.getAsJsonObject(category).has("doubleValue")) {
                categoryTotal = existingCategories.getAsJsonObject(category).get("doubleValue").getAsDouble();
            }
            categoryTotal += amount;
            
            // Build category map
            StringBuilder categoriesJson = new StringBuilder();
            categoriesJson.append("\"categories\":{\"mapValue\":{\"fields\":{");
            
            // Add all existing categories
            boolean firstCategory = true;
            for (String existingCategory : existingCategories.keySet()) {
                if (!firstCategory) {
                    categoriesJson.append(",");
                }
                firstCategory = false;
                
                double value = existingCategories.getAsJsonObject(existingCategory).get("doubleValue").getAsDouble();
                // Update current category if it's in the list
                if (existingCategory.equals(category)) {
                    value = categoryTotal;
                }
                categoriesJson.append("\"").append(existingCategory).append("\":{\"doubleValue\":").append(value).append("}");
            }
            
            // Add new category if it didn't exist
            if (!existingCategories.has(category)) {
                if (!firstCategory) {
                    categoriesJson.append(",");
                }
                categoriesJson.append("\"").append(category).append("\":{\"doubleValue\":").append(amount).append("}");
            }
            
            categoriesJson.append("}}},");
            
            // Current timestamp for metadata
            long currentTimestamp = System.currentTimeMillis();
            
            // Build JSON for update/create
            String jsonBody;
            if (documentExists) {
                // PATCH request for existing document
                jsonBody = "{\"fields\":{"
                        + "\"totalExpense\":{\"doubleValue\":" + newTotal + "},"
                        + "\"expenseCount\":{\"integerValue\":" + newCount + "},"
                        + categoriesJson.toString()
                        + "\"lastUpdated\":{\"integerValue\":" + currentTimestamp + "}"
                        + "}}";
            } else {
                // Create new document with all fields
                jsonBody = "{\"fields\":{"
                        + "\"totalExpense\":{\"doubleValue\":" + amount + "},"
                        + "\"expenseCount\":{\"integerValue\":1},"
                        + "\"yearMonth\":{\"stringValue\":\"" + yearMonth + "\"},"
                        + "\"year\":{\"stringValue\":\"" + year + "\"},"
                        + "\"month\":{\"integerValue\":" + month + "},"
                        + categoriesJson.toString()
                        + "\"createdAt\":{\"integerValue\":" + currentTimestamp + "},"
                        + "\"lastUpdated\":{\"integerValue\":" + currentTimestamp + "}"
                        + "}}";
            }
            
            // Send the request to update or create the summary document
            URL updateUrl = new URL(summaryUrl);
            HttpURLConnection updateConn = (HttpURLConnection) updateUrl.openConnection();
            updateConn.setRequestMethod(documentExists ? "PATCH" : "PUT");
            updateConn.setRequestProperty("Content-Type", "application/json");
            updateConn.setRequestProperty("Authorization", "Bearer " + idToken);
            updateConn.setDoOutput(true);
            
            try (OutputStream os = updateConn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            
            int updateResponseCode = updateConn.getResponseCode();
            if (updateResponseCode == 200 || updateResponseCode == 201) {
                System.out.println("Monthly expense summary updated successfully for " + yearMonth);
            } else {
                System.out.println("Failed to update monthly expense summary. Response code: " + updateResponseCode);
                // Log error if available
                logError(updateConn);
            }
            
        } catch (Exception e) {
            System.out.println("Exception in updateMonthlySummary: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Utility: Safely parse a date from a string with either "yyyy-MM-dd" or "MM/dd/yyyy" format.
     */
    private LocalDate tryParseDate(String ds) {
        if (ds == null || ds.trim().isEmpty())
            return null;
        try {
            return LocalDate.parse(ds, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            // Fallback to the alternate format
            try {
                return LocalDate.parse(ds, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } catch (Exception ex2) {
                System.out.println("tryParseDate failed for: " + ds);
                return null;
            }
        }
    }
    
    private static String getJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([\\d.-]+))");
        Matcher m = p.matcher(json);
        if (m.find()) {
            if (m.group(1) != null) return m.group(1);
            if (m.group(2) != null) return m.group(2);
        }
        return "";
    }
}
