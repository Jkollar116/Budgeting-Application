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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BillsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("BillsHandler invoked: " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        
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
        
        if (path.equals("/api/bills") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleGetBills(exchange, idToken, localId);
        } else if (path.equals("/api/bills") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleCreateBill(exchange, idToken, localId);
        } else if (path.matches("/api/bills/[^/]+") && "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            String billId = path.substring(path.lastIndexOf('/') + 1);
            handleUpdateBill(exchange, idToken, localId, billId);
        } else if (path.matches("/api/bills/[^/]+") && "DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            String billId = path.substring(path.lastIndexOf('/') + 1);
            handleDeleteBill(exchange, idToken, localId, billId);
        } else {
            System.out.println("404 - Unsupported path/method: " + path + " " + exchange.getRequestMethod());
            exchange.sendResponseHeaders(404, -1);
        }
    }
    
    private void handleGetBills(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Bills";
            
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
                
                // Format response for our app
                JSONObject firebaseResponse = new JSONObject(response.toString());
                JSONArray bills = new JSONArray();
                
                if (firebaseResponse.has("documents")) {
                    JSONArray documents = firebaseResponse.getJSONArray("documents");
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        JSONObject bill = new JSONObject();
                        // Extract document ID from the full path
                        String name = document.getString("name");
                        String id = name.substring(name.lastIndexOf('/') + 1);
                        bill.put("id", id);
                        
                        if (fields.has("name")) {
                            bill.put("name", fields.getJSONObject("name").getString("stringValue"));
                        }
                        
                        if (fields.has("amount")) {
                            if (fields.getJSONObject("amount").has("doubleValue")) {
                                bill.put("amount", fields.getJSONObject("amount").getDouble("doubleValue"));
                            } else if (fields.getJSONObject("amount").has("integerValue")) {
                                bill.put("amount", fields.getJSONObject("amount").getInt("integerValue"));
                            }
                        }
                        
                        if (fields.has("category")) {
                            bill.put("category", fields.getJSONObject("category").getString("stringValue"));
                        }
                        
                        if (fields.has("frequency")) {
                            bill.put("frequency", fields.getJSONObject("frequency").getString("stringValue"));
                        }
                        
                        if (fields.has("dueDate")) {
                            bill.put("dueDate", fields.getJSONObject("dueDate").getString("stringValue"));
                        }
                        
                        if (fields.has("alertDays")) {
                            bill.put("alertDays", fields.getJSONObject("alertDays").getInt("integerValue"));
                        }
                        
                        if (fields.has("autoPay")) {
                            bill.put("autoPay", fields.getJSONObject("autoPay").getBoolean("booleanValue"));
                        }
                        
                        if (fields.has("notes")) {
                            bill.put("notes", fields.getJSONObject("notes").getString("stringValue"));
                        }
                        
                        if (fields.has("paid")) {
                            bill.put("paid", fields.getJSONObject("paid").getBoolean("booleanValue"));
                        } else {
                            bill.put("paid", false);
                        }
                        
                        bills.put(bill);
                    }
                }
                
                JSONObject appResponse = new JSONObject();
                appResponse.put("bills", bills);
                
                byte[] responseBytes = appResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                // If there are no bills, return an empty array
                if (responseCode == 404) {
                    JSONObject emptyResponse = new JSONObject();
                    emptyResponse.put("bills", new JSONArray());
                    
                    byte[] responseBytes = emptyResponse.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                } else {
                    System.out.println("Error fetching bills: " + responseCode);
                    exchange.sendResponseHeaders(responseCode, -1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    private void handleCreateBill(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Read the request body
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            
            // Parse the bill data
            JSONObject billData = new JSONObject(requestBody.toString());
            
            // Generate a unique ID for the bill
            String billId = UUID.randomUUID().toString();
            
            // Create the Firestore document
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("name", new JSONObject().put("stringValue", billData.getString("name")));
            fields.put("amount", new JSONObject().put("doubleValue", billData.getDouble("amount")));
            fields.put("category", new JSONObject().put("stringValue", billData.getString("category")));
            fields.put("frequency", new JSONObject().put("stringValue", billData.getString("frequency")));
            fields.put("dueDate", new JSONObject().put("stringValue", billData.getString("dueDate")));
            fields.put("alertDays", new JSONObject().put("integerValue", billData.getInt("alertDays")));
            fields.put("autoPay", new JSONObject().put("booleanValue", billData.getBoolean("autoPay")));
            fields.put("notes", new JSONObject().put("stringValue", billData.optString("notes", "")));
            fields.put("paid", new JSONObject().put("booleanValue", billData.optBoolean("paid", false)));
            fields.put("created", new JSONObject().put("timestampValue", Instant.now().toString()));
            
            document.put("fields", fields);
            
            // Save to Firestore
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Bills/" + billId;
            
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
                successResponse.put("id", billId);
                
                byte[] responseBytes = successResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream responseOs = exchange.getResponseBody();
                responseOs.write(responseBytes);
                responseOs.close();
                
                // Create an alert for the bill if due soon
                createBillAlert(idToken, localId, billData);
            } else {
                System.out.println("Error creating bill: " + responseCode);
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
    
    private void handleUpdateBill(HttpExchange exchange, String idToken, String localId, String billId) throws IOException {
        try {
            // Read the request body
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            
            // Parse the bill data
            JSONObject billData = new JSONObject(requestBody.toString());
            
            // Create the update data
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            
            // Only update the fields that are provided
            if (billData.has("name")) {
                fields.put("name", new JSONObject().put("stringValue", billData.getString("name")));
            }
            
            if (billData.has("amount")) {
                fields.put("amount", new JSONObject().put("doubleValue", billData.getDouble("amount")));
            }
            
            if (billData.has("category")) {
                fields.put("category", new JSONObject().put("stringValue", billData.getString("category")));
            }
            
            if (billData.has("frequency")) {
                fields.put("frequency", new JSONObject().put("stringValue", billData.getString("frequency")));
            }
            
            if (billData.has("dueDate")) {
                fields.put("dueDate", new JSONObject().put("stringValue", billData.getString("dueDate")));
            }
            
            if (billData.has("alertDays")) {
                fields.put("alertDays", new JSONObject().put("integerValue", billData.getInt("alertDays")));
            }
            
            if (billData.has("autoPay")) {
                fields.put("autoPay", new JSONObject().put("booleanValue", billData.getBoolean("autoPay")));
            }
            
            if (billData.has("notes")) {
                fields.put("notes", new JSONObject().put("stringValue", billData.getString("notes")));
            }
            
            if (billData.has("paid")) {
                fields.put("paid", new JSONObject().put("booleanValue", billData.getBoolean("paid")));
            }
            
            // Add the updated timestamp
            fields.put("updated", new JSONObject().put("timestampValue", Instant.now().toString()));
            
            document.put("fields", fields);
            
            // Update in Firestore
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Bills/" + billId;
            
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
                System.out.println("Error updating bill: " + responseCode);
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
    
    private void handleDeleteBill(HttpExchange exchange, String idToken, String localId, String billId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Bills/" + billId;
            
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
                System.out.println("Error deleting bill: " + responseCode);
                exchange.sendResponseHeaders(responseCode, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    /**
     * Get upcoming bills that are due within the specified number of days
     * 
     * @param idToken Firebase ID token
     * @param localId Firebase user ID
     * @param daysAhead Number of days to look ahead
     * @return List of bills due within the specified days
     */
    public List<Map<String, Object>> getUpcomingBills(String idToken, String localId, int daysAhead) throws Exception {
        List<Map<String, Object>> upcomingBills = new ArrayList<>();
        
        // Get all bills
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/Bills";
        
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
            
            if (firebaseResponse.has("documents")) {
                JSONArray documents = firebaseResponse.getJSONArray("documents");
                
                // Current date for comparing
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDate cutoffDate = today.plusDays(daysAhead);
                
                for (int i = 0; i < documents.length(); i++) {
                    JSONObject document = documents.getJSONObject(i);
                    JSONObject fields = document.getJSONObject("fields");
                    
                    // Skip if already paid
                    if (fields.has("paid") && fields.getJSONObject("paid").getBoolean("booleanValue")) {
                        continue;
                    }
                    
                    // Check due date
                    if (fields.has("dueDate")) {
                        String dueDateStr = fields.getJSONObject("dueDate").getString("stringValue");
                        java.time.LocalDate dueDate = java.time.LocalDate.parse(dueDateStr);
                        
                        // Check if due date is within range and not in the past
                        if (!dueDate.isBefore(today) && !dueDate.isAfter(cutoffDate)) {
                            // Extract bill data
                            Map<String, Object> bill = new HashMap<>();
                            
                            // Extract document ID
                            String name = document.getString("name");
                            String id = name.substring(name.lastIndexOf('/') + 1);
                            bill.put("id", id);
                            
                            // Extract other fields
                            if (fields.has("name")) {
                                bill.put("name", fields.getJSONObject("name").getString("stringValue"));
                            }
                            
                            if (fields.has("amount")) {
                                if (fields.getJSONObject("amount").has("doubleValue")) {
                                    bill.put("amount", fields.getJSONObject("amount").getDouble("doubleValue"));
                                } else if (fields.getJSONObject("amount").has("integerValue")) {
                                    bill.put("amount", fields.getJSONObject("amount").getInt("integerValue"));
                                }
                            }
                            
                            bill.put("dueDate", dueDateStr);
                            
                            // Calculate days until due
                            long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
                            bill.put("daysUntilDue", daysUntilDue);
                            
                            if (fields.has("category")) {
                                bill.put("category", fields.getJSONObject("category").getString("stringValue"));
                            }
                            
                            if (fields.has("frequency")) {
                                bill.put("frequency", fields.getJSONObject("frequency").getString("stringValue"));
                            }
                            
                            if (fields.has("autoPay")) {
                                bill.put("autoPay", fields.getJSONObject("autoPay").getBoolean("booleanValue"));
                            }
                            
                            upcomingBills.add(bill);
                        }
                    }
                }
            }
        }
        
        return upcomingBills;
    }
    
    private void createBillAlert(String idToken, String localId, JSONObject billData) {
        try {
            // Check if the bill is due within the alert days
            String dueDateStr = billData.getString("dueDate");
            java.time.LocalDate dueDate = java.time.LocalDate.parse(dueDateStr);
            java.time.LocalDate now = java.time.LocalDate.now();
            int alertDays = billData.getInt("alertDays");
            
            long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(now, dueDate);
            
            if (daysUntilDue <= alertDays && daysUntilDue >= 0) {
                // Create an alert
                String billName = billData.getString("name");
                double amount = billData.getDouble("amount");
                
                JSONObject alertData = new JSONObject();
                alertData.put("title", "Upcoming Bill: " + billName);
                alertData.put("message", String.format(
                        "Your %s bill of $%.2f is due in %d day%s (on %s).", 
                        billName, 
                        amount, 
                        daysUntilDue,
                        daysUntilDue == 1 ? "" : "s",
                        dueDate.toString()
                ));
                alertData.put("type", "bill");
                
                // Use AlertsHandler to create the alert
                String alertId = UUID.randomUUID().toString();
                JSONObject document = new JSONObject();
                JSONObject fields = new JSONObject();
                
                fields.put("title", new JSONObject().put("stringValue", alertData.getString("title")));
                fields.put("message", new JSONObject().put("stringValue", alertData.getString("message")));
                fields.put("type", new JSONObject().put("stringValue", alertData.getString("type")));
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
                    System.out.println("Error creating bill alert: " + responseCode);
                }
            }
        } catch (Exception e) {
            System.out.println("Error creating bill alert: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
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
