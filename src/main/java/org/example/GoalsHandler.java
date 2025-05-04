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
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Handler for financial goals related endpoints.
 * Provides functionality to create, retrieve, update, and delete financial goals.
 */
public class GoalsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("GoalsHandler invoked: " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        
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
        
        if (path.equals("/api/goals") && "GET".equalsIgnoreCase(method)) {
            handleGetGoals(exchange, idToken, localId);
        } else if (path.equals("/api/goals") && "POST".equalsIgnoreCase(method)) {
            handleCreateGoal(exchange, idToken, localId);
        } else if (path.matches("/api/goals/[^/]+") && "PUT".equalsIgnoreCase(method)) {
            String goalId = path.substring(path.lastIndexOf('/') + 1);
            handleUpdateGoal(exchange, idToken, localId, goalId);
        } else if (path.matches("/api/goals/[^/]+") && "DELETE".equalsIgnoreCase(method)) {
            String goalId = path.substring(path.lastIndexOf('/') + 1);
            handleDeleteGoal(exchange, idToken, localId, goalId);
        } else if (path.equals("/api/goals/progress") && "GET".equalsIgnoreCase(method)) {
            handleGetProgress(exchange, idToken, localId);
        } else {
            System.out.println("404 - Unsupported path/method: " + path + " " + method);
            exchange.sendResponseHeaders(404, -1);
        }
    }
    
    /**
     * Handle GET request to retrieve all financial goals
     */
    private void handleGetGoals(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Goals";
            
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
                JSONArray goals = new JSONArray();
                
                if (firebaseResponse.has("documents")) {
                    JSONArray documents = firebaseResponse.getJSONArray("documents");
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        JSONObject goal = new JSONObject();
                        // Extract document ID from the full path
                        String name = document.getString("name");
                        String id = name.substring(name.lastIndexOf('/') + 1);
                        goal.put("id", id);
                        
                        if (fields.has("name")) {
                            goal.put("name", fields.getJSONObject("name").getString("stringValue"));
                        }
                        
                        if (fields.has("description")) {
                            goal.put("description", fields.getJSONObject("description").getString("stringValue"));
                        }
                        
                        if (fields.has("targetAmount")) {
                            if (fields.getJSONObject("targetAmount").has("doubleValue")) {
                                goal.put("targetAmount", fields.getJSONObject("targetAmount").getDouble("doubleValue"));
                            } else if (fields.getJSONObject("targetAmount").has("integerValue")) {
                                goal.put("targetAmount", fields.getJSONObject("targetAmount").getInt("integerValue"));
                            }
                        }
                        
                        if (fields.has("currentAmount")) {
                            if (fields.getJSONObject("currentAmount").has("doubleValue")) {
                                goal.put("currentAmount", fields.getJSONObject("currentAmount").getDouble("doubleValue"));
                            } else if (fields.getJSONObject("currentAmount").has("integerValue")) {
                                goal.put("currentAmount", fields.getJSONObject("currentAmount").getInt("integerValue"));
                            }
                        } else {
                            goal.put("currentAmount", 0);
                        }
                        
                        if (fields.has("targetDate")) {
                            goal.put("targetDate", fields.getJSONObject("targetDate").getString("stringValue"));
                        }
                        
                        if (fields.has("type")) {
                            goal.put("type", fields.getJSONObject("type").getString("stringValue"));
                        } else {
                            goal.put("type", "saving");
                        }
                        
                        if (fields.has("achieved")) {
                            goal.put("achieved", fields.getJSONObject("achieved").getBoolean("booleanValue"));
                        } else {
                            goal.put("achieved", false);
                        }
                        
                        if (fields.has("achievedDate")) {
                            goal.put("achievedDate", fields.getJSONObject("achievedDate").getString("timestampValue"));
                        }
                        
                        if (fields.has("createdDate")) {
                            goal.put("createdDate", fields.getJSONObject("createdDate").getString("timestampValue"));
                        }
                        
                        // Calculate progress percentage
                        double targetAmount = goal.getDouble("targetAmount");
                        double currentAmount = goal.has("currentAmount") ? goal.getDouble("currentAmount") : 0;
                        double progress = targetAmount > 0 ? (currentAmount / targetAmount) * 100 : 0;
                        
                        // Handle net worth goals
                        if (goal.getString("type").equals("netWorth")) {
                            double netWorth = getUserNetWorth(idToken, localId);
                            progress = targetAmount > 0 ? (netWorth / targetAmount) * 100 : 0;
                            goal.put("currentAmount", netWorth);
                        }
                        
                        goal.put("progress", Math.min(progress, 100)); // Cap at 100%
                        
                        goals.put(goal);
                    }
                }
                
                JSONObject appResponse = new JSONObject();
                appResponse.put("goals", goals);
                
                byte[] responseBytes = appResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                // If no goals, return an empty array
                if (responseCode == 404) {
                    JSONObject emptyResponse = new JSONObject();
                    emptyResponse.put("goals", new JSONArray());
                    
                    byte[] responseBytes = emptyResponse.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                } else {
                    System.out.println("Error fetching goals: " + responseCode);
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
     * Handle POST request to create a new financial goal
     */
    private void handleCreateGoal(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Read request body
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            
            // Parse the goal data
            JSONObject goalData = new JSONObject(requestBody.toString());
            
            // Generate a unique ID
            String goalId = UUID.randomUUID().toString();
            
            // Create the Firestore document
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("name", new JSONObject().put("stringValue", goalData.getString("name")));
            fields.put("targetAmount", new JSONObject().put("doubleValue", goalData.getDouble("targetAmount")));
            
            if (goalData.has("description")) {
                fields.put("description", new JSONObject().put("stringValue", goalData.getString("description")));
            }
            
            if (goalData.has("targetDate")) {
                fields.put("targetDate", new JSONObject().put("stringValue", goalData.getString("targetDate")));
            }
            
            if (goalData.has("type")) {
                fields.put("type", new JSONObject().put("stringValue", goalData.getString("type")));
            } else {
                fields.put("type", new JSONObject().put("stringValue", "saving"));
            }
            
            if (goalData.has("currentAmount")) {
                fields.put("currentAmount", new JSONObject().put("doubleValue", goalData.getDouble("currentAmount")));
            } else {
                fields.put("currentAmount", new JSONObject().put("doubleValue", 0.0));
            }
            
            // Default values
            fields.put("achieved", new JSONObject().put("booleanValue", false));
            fields.put("createdDate", new JSONObject().put("timestampValue", Instant.now().toString()));
            
            document.put("fields", fields);
            
            // Save to Firestore
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Goals/" + goalId;
            
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
                successResponse.put("id", goalId);
                
                byte[] responseBytes = successResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream responseOs = exchange.getResponseBody();
                responseOs.write(responseBytes);
                responseOs.close();
            } else {
                System.out.println("Error creating goal: " + responseCode);
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
     * Handle PUT request to update an existing financial goal
     */
    private void handleUpdateGoal(HttpExchange exchange, String idToken, String localId, String goalId) throws IOException {
        try {
            // Read request body
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            
            // Parse the goal data
            JSONObject goalData = new JSONObject(requestBody.toString());
            
            // Create the update data
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            
            // Only update the fields that are provided
            if (goalData.has("name")) {
                fields.put("name", new JSONObject().put("stringValue", goalData.getString("name")));
            }
            
            if (goalData.has("description")) {
                fields.put("description", new JSONObject().put("stringValue", goalData.getString("description")));
            }
            
            if (goalData.has("targetAmount")) {
                fields.put("targetAmount", new JSONObject().put("doubleValue", goalData.getDouble("targetAmount")));
            }
            
            if (goalData.has("currentAmount")) {
                fields.put("currentAmount", new JSONObject().put("doubleValue", goalData.getDouble("currentAmount")));
            }
            
            if (goalData.has("targetDate")) {
                fields.put("targetDate", new JSONObject().put("stringValue", goalData.getString("targetDate")));
            }
            
            if (goalData.has("type")) {
                fields.put("type", new JSONObject().put("stringValue", goalData.getString("type")));
            }
            
            if (goalData.has("achieved")) {
                fields.put("achieved", new JSONObject().put("booleanValue", goalData.getBoolean("achieved")));
                
                // If marked as achieved, add achieved date
                if (goalData.getBoolean("achieved")) {
                    fields.put("achievedDate", new JSONObject().put("timestampValue", Instant.now().toString()));
                }
            }
            
            document.put("fields", fields);
            
            // Update in Firestore
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Goals/" + goalId;
            
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
                System.out.println("Error updating goal: " + responseCode);
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
     * Handle DELETE request to delete a financial goal
     */
    private void handleDeleteGoal(HttpExchange exchange, String idToken, String localId, String goalId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Goals/" + goalId;
            
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
                System.out.println("Error deleting goal: " + responseCode);
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
     * Handle GET request to get progress on financial goals
     */
    private void handleGetProgress(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // First get user's current net worth
            double netWorth = getUserNetWorth(idToken, localId);
            
            // Then get all goals
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Goals";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            
            JSONObject response = new JSONObject();
            response.put("netWorth", netWorth);
            
            int totalGoals = 0;
            int achievedGoals = 0;
            double totalProgress = 0;
            JSONArray goals = new JSONArray();
            
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder responseStr = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    responseStr.append(line);
                }
                in.close();
                
                JSONObject firebaseResponse = new JSONObject(responseStr.toString());
                
                if (firebaseResponse.has("documents")) {
                    JSONArray documents = firebaseResponse.getJSONArray("documents");
                    
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        totalGoals++;
                        
                        // Check if goal is achieved
                        boolean achieved = fields.has("achieved") && fields.getJSONObject("achieved").getBoolean("booleanValue");
                        if (achieved) {
                            achievedGoals++;
                        }
                        
                        // Get goal details
                        JSONObject goal = new JSONObject();
                        String name = document.getString("name");
                        String id = name.substring(name.lastIndexOf('/') + 1);
                        goal.put("id", id);
                        
                        if (fields.has("name")) {
                            goal.put("name", fields.getJSONObject("name").getString("stringValue"));
                        }
                        
                        double targetAmount = 0;
                        if (fields.has("targetAmount")) {
                            if (fields.getJSONObject("targetAmount").has("doubleValue")) {
                                targetAmount = fields.getJSONObject("targetAmount").getDouble("doubleValue");
                            } else if (fields.getJSONObject("targetAmount").has("integerValue")) {
                                targetAmount = fields.getJSONObject("targetAmount").getInt("integerValue");
                            }
                        }
                        goal.put("targetAmount", targetAmount);
                        
                        double currentAmount = 0;
                        if (fields.has("currentAmount")) {
                            if (fields.getJSONObject("currentAmount").has("doubleValue")) {
                                currentAmount = fields.getJSONObject("currentAmount").getDouble("doubleValue");
                            } else if (fields.getJSONObject("currentAmount").has("integerValue")) {
                                currentAmount = fields.getJSONObject("currentAmount").getInt("integerValue");
                            }
                        }
                        
                        String type = "saving";
                        if (fields.has("type")) {
                            type = fields.getJSONObject("type").getString("stringValue");
                        }
                        goal.put("type", type);
                        
                        // For netWorth goals, use current net worth as the current amount
                        if (type.equals("netWorth")) {
                            currentAmount = netWorth;
                        }
                        
                        goal.put("currentAmount", currentAmount);
                        
                        // Calculate progress
                        double progress = 0;
                        if (targetAmount > 0) {
                            progress = (currentAmount / targetAmount) * 100;
                            progress = Math.min(progress, 100); // Cap at 100%
                        }
                        goal.put("progress", progress);
                        
                        totalProgress += progress;
                        
                        goals.put(goal);
                    }
                }
            }
            
            // Calculate average progress
            double averageProgress = totalGoals > 0 ? totalProgress / totalGoals : 0;
            
            response.put("totalGoals", totalGoals);
            response.put("achievedGoals", achievedGoals);
            response.put("averageProgress", averageProgress);
            response.put("goals", goals);
            
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
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
     * Get user's current net worth
     */
    private double getUserNetWorth(String idToken, String localId) {
        try {
            return NetWorthCalculator.calculateNetWorth(idToken, localId);
        } catch (Exception e) {
            System.out.println("Error calculating net worth: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
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
