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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles all alert-related operations for the application.
 * This includes fetching alerts, marking them as read, and generating new alerts based on various triggers.
 */
public class AlertsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("AlertsHandler invoked: " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        
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
        
        if (path.equals("/api/alerts") && "GET".equalsIgnoreCase(method)) {
            handleGetAlerts(exchange, idToken, localId);
        } else if (path.equals("/api/alerts/read") && "POST".equalsIgnoreCase(method)) {
            handleMarkAllRead(exchange, idToken, localId);
        } else if (path.equals("/api/alerts/trigger/check") && "POST".equalsIgnoreCase(method)) {
            handleCheckTriggers(exchange, idToken, localId);
        } else if (path.matches("/api/alerts/[^/]+") && "DELETE".equalsIgnoreCase(method)) {
            String alertId = path.substring(path.lastIndexOf('/') + 1);
            handleDeleteAlert(exchange, idToken, localId, alertId);
        } else {
            System.out.println("404 - Unsupported path/method: " + path + " " + method);
            exchange.sendResponseHeaders(404, -1);
        }
    }
    
    /**
     * Handle GET request to fetch all alerts for a user
     */
    private void handleGetAlerts(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Alerts";
            
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
                JSONArray alerts = new JSONArray();
                int unreadCount = 0;
                
                if (firebaseResponse.has("documents")) {
                    JSONArray documents = firebaseResponse.getJSONArray("documents");
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        JSONObject alert = new JSONObject();
                        // Extract document ID from the full path
                        String name = document.getString("name");
                        String id = name.substring(name.lastIndexOf('/') + 1);
                        alert.put("id", id);
                        
                        if (fields.has("title")) {
                            alert.put("title", fields.getJSONObject("title").getString("stringValue"));
                        }
                        
                        if (fields.has("message")) {
                            alert.put("message", fields.getJSONObject("message").getString("stringValue"));
                        }
                        
                        if (fields.has("type")) {
                            alert.put("type", fields.getJSONObject("type").getString("stringValue"));
                        } else {
                            alert.put("type", "general");
                        }
                        
                        if (fields.has("created")) {
                            alert.put("timestamp", fields.getJSONObject("created").getString("timestampValue"));
                        } else {
                            alert.put("timestamp", Instant.now().toString());
                        }
                        
                        if (fields.has("read")) {
                            boolean read = fields.getJSONObject("read").getBoolean("booleanValue");
                            alert.put("read", read);
                            if (!read) {
                                unreadCount++;
                            }
                        } else {
                            alert.put("read", false);
                            unreadCount++;
                        }
                        
                        if (fields.has("relatedId")) {
                            alert.put("relatedId", fields.getJSONObject("relatedId").getString("stringValue"));
                        }
                        
                        alerts.put(alert);
                    }
                }
                
                JSONObject appResponse = new JSONObject();
                appResponse.put("alerts", alerts);
                appResponse.put("unreadCount", unreadCount);
                
                byte[] responseBytes = appResponse.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                // If there are no alerts, return an empty array
                if (responseCode == 404) {
                    JSONObject emptyResponse = new JSONObject();
                    emptyResponse.put("alerts", new JSONArray());
                    emptyResponse.put("unreadCount", 0);
                    
                    byte[] responseBytes = emptyResponse.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                } else {
                    System.out.println("Error fetching alerts: " + responseCode);
                    exchange.sendResponseHeaders(responseCode, -1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    /**
     * Handle POST request to mark all alerts as read
     */
    private void handleMarkAllRead(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Get all alerts first
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Alerts";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            List<String> alertIds = new ArrayList<>();
            
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
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        
                        // Get alert ID
                        String name = document.getString("name");
                        String id = name.substring(name.lastIndexOf('/') + 1);
                        
                        // Add to list if unread
                        JSONObject fields = document.getJSONObject("fields");
                        if (!fields.has("read") || !fields.getJSONObject("read").getBoolean("booleanValue")) {
                            alertIds.add(id);
                        }
                    }
                }
            }
            
            // Mark each unread alert as read
            int markedCount = 0;
            
            for (String alertId : alertIds) {
                String updateUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                        + localId + "/Alerts/" + alertId;
                
                URL updateUrlObj = new URL(updateUrl);
                HttpURLConnection updateConn = (HttpURLConnection) updateUrlObj.openConnection();
                updateConn.setRequestMethod("PATCH");
                updateConn.setRequestProperty("Content-Type", "application/json");
                updateConn.setRequestProperty("Authorization", "Bearer " + idToken);
                updateConn.setDoOutput(true);
                
                JSONObject updateData = new JSONObject();
                JSONObject fields = new JSONObject();
                fields.put("read", new JSONObject().put("booleanValue", true));
                updateData.put("fields", fields);
                
                OutputStream os = updateConn.getOutputStream();
                os.write(updateData.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                
                int updateResponseCode = updateConn.getResponseCode();
                if (updateResponseCode == 200) {
                    markedCount++;
                }
            }
            
            // Send response
            JSONObject successResponse = new JSONObject();
            successResponse.put("success", true);
            successResponse.put("markedCount", markedCount);
            
            byte[] responseBytes = successResponse.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    /**
     * Handle DELETE request to delete a specific alert
     */
    private void handleDeleteAlert(HttpExchange exchange, String idToken, String localId, String alertId) throws IOException {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Alerts/" + alertId;
            
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
                System.out.println("Error deleting alert: " + responseCode);
                exchange.sendResponseHeaders(responseCode, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    /**
     * Handle POST request to check for new alerts based on triggers
     * This method checks various conditions (bills due, spending limits, etc.) and creates alerts as needed
     */
    private void handleCheckTriggers(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            int alertsCreated = 0;
            
            // Check for upcoming bills
            alertsCreated += checkBillAlerts(idToken, localId);
            
            // Check budget limits
            alertsCreated += checkBudgetAlerts(idToken, localId);
            
            // Check spending limits
            alertsCreated += checkSpendingLimitAlerts(idToken, localId);
            
            // Check paycheck alerts
            alertsCreated += checkPaycheckAlerts(idToken, localId);
            
            // Check goal progress 
            alertsCreated += checkGoalAlerts(idToken, localId);
            
            // Send response
            JSONObject successResponse = new JSONObject();
            successResponse.put("success", true);
            successResponse.put("count", alertsCreated);
            
            byte[] responseBytes = successResponse.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    /**
     * Check for upcoming bills and create alerts
     */
    private int checkBillAlerts(String idToken, String localId) {
        int alertsCreated = 0;
        try {
            // Get user settings to determine alert days preference
            int alertDays = getUserAlertDays(idToken, localId);
            
            // Use BillsHandler to get upcoming bills
            BillsHandler billsHandler = new BillsHandler();
            List<java.util.Map<String, Object>> upcomingBills = billsHandler.getUpcomingBills(idToken, localId, alertDays);
            
            for (java.util.Map<String, Object> bill : upcomingBills) {
                // Create alert for bill
                String billName = (String) bill.get("name");
                double amount = bill.get("amount") instanceof Double ? (Double) bill.get("amount") : ((Integer) bill.get("amount")).doubleValue();
                String dueDate = (String) bill.get("dueDate");
                long daysUntilDue = (Long) bill.get("daysUntilDue");
                
                // Check if an alert for this bill already exists
                if (!alertExists(idToken, localId, "bill_" + (daysUntilDue == 0 ? "due" : "upcoming"), billName)) {
                    // Create alert
                    String title = daysUntilDue == 0 ? "Bill Due Today" : "Upcoming Bill";
                    String message = daysUntilDue == 0 ?
                            String.format("Your %s bill of $%.2f is due today!", billName, amount) :
                            String.format("Your %s bill of $%.2f is due in %d day%s (on %s).", 
                                    billName, amount, daysUntilDue, daysUntilDue == 1 ? "" : "s", dueDate);
                    
                    String alertType = daysUntilDue == 0 ? "bill_due" : "bill_upcoming";
                    
                    createAlert(idToken, localId, title, message, alertType, (String) bill.get("id"));
                    alertsCreated++;
                }
            }
        } catch (Exception e) {
            System.out.println("Error checking bill alerts: " + e.getMessage());
            e.printStackTrace();
        }
        return alertsCreated;
    }
    
    /**
     * Check for budget alerts
     */
    private int checkBudgetAlerts(String idToken, String localId) {
        int alertsCreated = 0;
        try {
            // Get user's budget
            String budgetUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Budget";
            
            URL url = new URL(budgetUrl);
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
                    
                    // Get current month expenses
                    double totalExpenses = getCurrentMonthExpenses(idToken, localId);
                    
                    // Check each budget category
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        if (fields.has("amount") && fields.has("category")) {
                            double budgetAmount = 0;
                            if (fields.getJSONObject("amount").has("doubleValue")) {
                                budgetAmount = fields.getJSONObject("amount").getDouble("doubleValue");
                            } else if (fields.getJSONObject("amount").has("integerValue")) {
                                budgetAmount = fields.getJSONObject("amount").getInt("integerValue");
                            }
                            
                            String category = fields.getJSONObject("category").getString("stringValue");
                            
                            // Get expenses for this category
                            double categoryExpenses = getCategoryExpenses(idToken, localId, category);
                            
                            // Create alert if over budget
                            if (categoryExpenses > budgetAmount) {
                                // Check if alert already exists
                                if (!alertExists(idToken, localId, "budget_exceeded", category)) {
                                    String title = "Budget Exceeded";
                                    String message = String.format("You've exceeded your %s budget of $%.2f. Current spending: $%.2f", 
                                            category, budgetAmount, categoryExpenses);
                                    
                                    // Get document ID
                                    String name = document.getString("name");
                                    String id = name.substring(name.lastIndexOf('/') + 1);
                                    
                                    createAlert(idToken, localId, title, message, "budget_exceeded", id);
                                    alertsCreated++;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error checking budget alerts: " + e.getMessage());
            e.printStackTrace();
        }
        return alertsCreated;
    }
    
    /**
     * Check for spending limit alerts
     */
    private int checkSpendingLimitAlerts(String idToken, String localId) {
        int alertsCreated = 0;
        try {
            // Get user's spending limits
            String limitsUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/SpendingLimits";
            
            URL url = new URL(limitsUrl);
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
                    
                    // Check each spending limit
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        if (fields.has("amount") && fields.has("category")) {
                            double limitAmount = 0;
                            if (fields.getJSONObject("amount").has("doubleValue")) {
                                limitAmount = fields.getJSONObject("amount").getDouble("doubleValue");
                            } else if (fields.getJSONObject("amount").has("integerValue")) {
                                limitAmount = fields.getJSONObject("amount").getInt("integerValue");
                            }
                            
                            String category = fields.getJSONObject("category").getString("stringValue");
                            
                            // Get expenses for this category
                            double categoryExpenses = getCategoryExpenses(idToken, localId, category);
                            
                            // Create alert if over limit
                            if (categoryExpenses > limitAmount) {
                                // Check if alert already exists
                                if (!alertExists(idToken, localId, "spending_limit", category)) {
                                    String title = "Spending Limit Exceeded";
                                    String message = String.format("You've exceeded your %s spending limit of $%.2f. Current spending: $%.2f", 
                                            category, limitAmount, categoryExpenses);
                                    
                                    // Get document ID
                                    String name = document.getString("name");
                                    String id = name.substring(name.lastIndexOf('/') + 1);
                                    
                                    createAlert(idToken, localId, title, message, "spending_limit", id);
                                    alertsCreated++;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error checking spending limit alerts: " + e.getMessage());
            e.printStackTrace();
        }
        return alertsCreated;
    }
    
    /**
     * Check for paycheck alerts
     */
    private int checkPaycheckAlerts(String idToken, String localId) {
        int alertsCreated = 0;
        try {
            // Get user's income sources
            String incomeUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Income";
            
            URL url = new URL(incomeUrl);
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
                    LocalDate today = LocalDate.now();
                    
                    // Check each income source
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        if (fields.has("type") && fields.getJSONObject("type").getString("stringValue").equals("recurring") &&
                            fields.has("nextPaymentDate")) {
                            
                            String nextPaymentDateStr = fields.getJSONObject("nextPaymentDate").getString("stringValue");
                            LocalDate nextPaymentDate = LocalDate.parse(nextPaymentDateStr);
                            
                            // Check if payment is today
                            if (nextPaymentDate.equals(today)) {
                                String incomeSource = fields.getJSONObject("source").getString("stringValue");
                                double amount = 0;
                                if (fields.getJSONObject("amount").has("doubleValue")) {
                                    amount = fields.getJSONObject("amount").getDouble("doubleValue");
                                } else if (fields.getJSONObject("amount").has("integerValue")) {
                                    amount = fields.getJSONObject("amount").getInt("integerValue");
                                }
                                
                                // Check if alert already exists
                                if (!alertExists(idToken, localId, "paycheck", incomeSource)) {
                                    String title = "Paycheck Due Today";
                                    String message = String.format("Your paycheck of $%.2f from %s is due today!", amount, incomeSource);
                                    
                                    // Get document ID
                                    String name = document.getString("name");
                                    String id = name.substring(name.lastIndexOf('/') + 1);
                                    
                                    createAlert(idToken, localId, title, message, "paycheck", id);
                                    alertsCreated++;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error checking paycheck alerts: " + e.getMessage());
            e.printStackTrace();
        }
        return alertsCreated;
    }
    
    /**
     * Check for goal achievement alerts
     */
    private int checkGoalAlerts(String idToken, String localId) {
        int alertsCreated = 0;
        try {
            // Get user's financial goals
            String goalsUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Goals";

            URL url = new URL(goalsUrl);
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

                    // Get user's current net worth
                    //double netWorth = getUserNetWorth(idToken, localId);

                    // Check each goal
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");

                        if (fields.has("targetAmount") && fields.has("type")) {
                            double targetAmount = 0;
                            if (fields.getJSONObject("targetAmount").has("doubleValue")) {
                                targetAmount = fields.getJSONObject("targetAmount").getDouble("doubleValue");
                            } else if (fields.getJSONObject("targetAmount").has("integerValue")) {
                                targetAmount = fields.getJSONObject("targetAmount").getInt("integerValue");
                            }

                            String goalType = fields.getJSONObject("type").getString("stringValue");

//                            if (goalType.equals("netWorth") && netWorth >= targetAmount) {
//                                // Goal achieved
//                                boolean achieved = fields.has("achieved") && fields.getJSONObject("achieved").getBoolean("booleanValue");
//
//                                if (!achieved) {
//                                    // Get document ID
//                                    String name = document.getString("name");
//                                    String id = name.substring(name.lastIndexOf('/') + 1);
//
//                                    // Update goal to mark as achieved
//                                    String updateUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
//                                            + localId + "/Goals/" + id;
//
//                                    URL updateUrlObj = new URL(updateUrl);
//                                    HttpURLConnection updateConn = (HttpURLConnection) updateUrlObj.openConnection();
//                                    updateConn.setRequestMethod("PATCH");
//                                    updateConn.setRequestProperty("Content-Type", "application/json");
//                                    updateConn.setRequestProperty("Authorization", "Bearer " + idToken);
//                                    updateConn.setDoOutput(true);
//
//                                    JSONObject updateData = new JSONObject();
//                                    JSONObject updateFields = new JSONObject();
//                                    updateFields.put("achieved", new JSONObject().put("booleanValue", true));
//                                    updateFields.put("achievedDate", new JSONObject().put("timestampValue", Instant.now().toString()));
//                                    updateData.put("fields", updateFields);
//
//                                    OutputStream os = updateConn.getOutputStream();
//                                    os.write(updateData.toString().getBytes(StandardCharsets.UTF_8));
//                                    os.close();
//
//                                    int updateResponseCode = updateConn.getResponseCode();
//
//                                    if (updateResponseCode == 200) {
//                                        // Create alert for achieved goal
//                                        String goalName = fields.has("name") ? fields.getJSONObject("name").getString("stringValue") : "Financial Goal";
//                                        String title = "Goal Achieved!";
//                                        String message = String.format("Congratulations! You've achieved your goal of reaching $%.2f net worth.", targetAmount);
//
//                                        createAlert(idToken, localId, title, message, "goal_achieved", id);
//                                        alertsCreated++;
//                                    }
//                                }
//                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error checking goal alerts: " + e.getMessage());
            e.printStackTrace();
        }
        return alertsCreated;
    }
    
    /**
     * Get user's alert preferences
     */
    private int getUserAlertDays(String idToken, String localId) {
        int defaultAlertDays = 3; // Default value
        
        try {
            String userUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + localId;
            
            URL url = new URL(userUrl);
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
                
                JSONObject userDoc = new JSONObject(response.toString());
                
                if (userDoc.has("fields") && userDoc.getJSONObject("fields").has("settings")) {
                    JSONObject settings = userDoc.getJSONObject("fields").getJSONObject("settings");
                    
                    if (settings.has("mapValue") && settings.getJSONObject("mapValue").has("fields")) {
                        JSONObject settingsFields = settings.getJSONObject("mapValue").getJSONObject("fields");
                        
                        if (settingsFields.has("billAlertDays") && 
                            settingsFields.getJSONObject("billAlertDays").has("integerValue")) {
                            return settingsFields.getJSONObject("billAlertDays").getInt("integerValue");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error getting user alert days: " + e.getMessage());
            e.printStackTrace();
        }
        
        return defaultAlertDays;
    }
    
    /**
     * Check if an alert already exists for a specific type and related item
     */
    private boolean alertExists(String idToken, String localId, String alertType, String relatedItem) {
        try {
            String alertsUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Alerts";
            
            URL url = new URL(alertsUrl);
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
                    
                    // Check each alert
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        if (fields.has("type") && fields.getJSONObject("type").getString("stringValue").equals(alertType)) {
                            // For bills and other specific items, check the message contains the item name
                            if (fields.has("message")) {
                                String message = fields.getJSONObject("message").getString("stringValue");
                                if (message.contains(relatedItem)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error checking if alert exists: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Create a new alert
     */
    private void createAlert(String idToken, String localId, String title, String message, String alertType, String relatedId) {
        try {
            // Generate a unique ID for the alert
            String alertId = UUID.randomUUID().toString();
            
            // Create the alert document
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("title", new JSONObject().put("stringValue", title));
            fields.put("message", new JSONObject().put("stringValue", message));
            fields.put("type", new JSONObject().put("stringValue", alertType));
            fields.put("created", new JSONObject().put("timestampValue", Instant.now().toString()));
            fields.put("read", new JSONObject().put("booleanValue", false));
            
            if (relatedId != null) {
                fields.put("relatedId", new JSONObject().put("stringValue", relatedId));
            }
            
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
                System.out.println("Error creating alert: " + responseCode);
            }
        } catch (Exception e) {
            System.out.println("Error creating alert: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the total expenses for the current month
     */
    private double getCurrentMonthExpenses(String idToken, String localId) {
        double total = 0.0;
        
        try {
            // Get the current month and year
            LocalDate now = LocalDate.now();
            int currentMonth = now.getMonthValue();
            int currentYear = now.getYear();
            
            // Get all expenses
            String expensesUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Expenses";
            
            URL url = new URL(expensesUrl);
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
                    
                    // Check each expense
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        if (fields.has("date") && fields.has("amount")) {
                            String dateStr = fields.getJSONObject("date").getString("stringValue");
                            LocalDate expenseDate = LocalDate.parse(dateStr);
                            
                            // Check if this expense is from the current month and year
                            if (expenseDate.getMonthValue() == currentMonth && expenseDate.getYear() == currentYear) {
                                // Add to total
                                if (fields.getJSONObject("amount").has("doubleValue")) {
                                    total += fields.getJSONObject("amount").getDouble("doubleValue");
                                } else if (fields.getJSONObject("amount").has("integerValue")) {
                                    total += fields.getJSONObject("amount").getInt("integerValue");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error getting current month expenses: " + e.getMessage());
            e.printStackTrace();
        }
        
        return total;
    }
    
    /**
     * Get expenses for a specific category in the current month
     */
    private double getCategoryExpenses(String idToken, String localId, String category) {
        double total = 0.0;
        
        try {
            // Get the current month and year
            LocalDate now = LocalDate.now();
            int currentMonth = now.getMonthValue();
            int currentYear = now.getYear();
            
            // Get all expenses
            String expensesUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                    + localId + "/Expenses";
            
            URL url = new URL(expensesUrl);
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
                    
                    // Check each expense
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject document = documents.getJSONObject(i);
                        JSONObject fields = document.getJSONObject("fields");
                        
                        if (fields.has("date") && fields.has("amount") && fields.has("category")) {
                            String dateStr = fields.getJSONObject("date").getString("stringValue");
                            LocalDate expenseDate = LocalDate.parse(dateStr);
                            String expenseCategory = fields.getJSONObject("category").getString("stringValue");
                            
                            // Check if this expense is from the current month and year, and matches the category
                            if (expenseDate.getMonthValue() == currentMonth && 
                                expenseDate.getYear() == currentYear && 
                                expenseCategory.equals(category)) {
                                
                                // Add to total
                                if (fields.getJSONObject("amount").has("doubleValue")) {
                                    total += fields.getJSONObject("amount").getDouble("doubleValue");
                                } else if (fields.getJSONObject("amount").has("integerValue")) {
                                    total += fields.getJSONObject("amount").getInt("integerValue");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error getting category expenses: " + e.getMessage());
            e.printStackTrace();
        }
        
        return total;
    }
    
    /**
     * Get the user's current net worth
     */
//    private double getUserNetWorth(String idToken, String localId) {
//        double netWorth = 0.0;
//
//        try {
//            String userUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + localId;
//
//            URL url = new URL(userUrl);
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestMethod("GET");
//            conn.setRequestProperty("Authorization", "Bearer " + idToken);
//
//            int responseCode = conn.getResponseCode();
//
//            if (responseCode == 200) {
//                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
//                StringBuilder response = new StringBuilder();
//                String line;
//                while ((line = in.readLine()) != null) {
//                    response.append(line);
//                }
//                in.close();
//
//                JSONObject userDoc = new JSONObject(response.toString());
//
//                if (userDoc.has("fields") && userDoc.getJSONObject("fields").has("netWorth")) {
//                    JSONObject netWorthField = userDoc.getJSONObject("fields").getJSONObject("netWorth");
//
//                    if (netWorthField.has("doubleValue")) {
//                        netWorth = netWorthField.getDouble("doubleValue");
//                    } else if (netWorthField.has("integerValue")) {
//                        netWorth = netWorthField.getInt("integerValue");
//                    }
//                }
//            }
//        } catch (Exception e) {
//            System.out.println("Error getting user net worth: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        return netWorth;
//    }
    
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
