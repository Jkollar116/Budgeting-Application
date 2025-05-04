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
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for net worth related endpoints.
 * Provides functionality to calculate and retrieve net worth data.
 */
public class NetWorthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("NetWorthHandler invoked: " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        
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
        
        if (path.equals("/api/networth/calculate") && "GET".equalsIgnoreCase(method)) {
            handleCalculateNetWorth(exchange, idToken, localId);
        } else if (path.equals("/api/networth/history") && "GET".equalsIgnoreCase(method)) {
            handleGetNetWorthHistory(exchange, idToken, localId);
        } else if (path.equals("/api/networth/breakdown") && "GET".equalsIgnoreCase(method)) {
            handleGetNetWorthBreakdown(exchange, idToken, localId);
        } else {
            System.out.println("404 - Unsupported path/method: " + path + " " + method);
            exchange.sendResponseHeaders(404, -1);
        }
    }
    
    /**
     * Handle GET request to calculate and return the current net worth
     */
    private void handleCalculateNetWorth(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Calculate net worth
            double netWorth = NetWorthCalculator.calculateNetWorth(idToken, localId);
            
            // Save the current net worth to history
            NetWorthCalculator.saveNetWorthHistory(idToken, localId, netWorth);
            
            // Update the user's net worth field
            updateUserNetWorth(idToken, localId, netWorth);
            
            // Create response
            JSONObject response = new JSONObject();
            response.put("netWorth", netWorth);
            response.put("timestamp", Instant.now().toString());
            
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
     * Handle GET request to get historical net worth data
     */
    private void handleGetNetWorthHistory(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Get query parameters
            String query = exchange.getRequestURI().getQuery();
            int months = 12; // Default to 12 months
            
            if (query != null && query.contains("months=")) {
                String monthsStr = query.substring(query.indexOf("months=") + 7);
                if (monthsStr.contains("&")) {
                    monthsStr = monthsStr.substring(0, monthsStr.indexOf("&"));
                }
                months = Integer.parseInt(monthsStr);
            }
            
            // Convert months to period
            String period;
            if (months <= 1) {
                period = "month";
            } else if (months <= 3) {
                period = "quarter";
            } else if (months <= 12) {
                period = "year";
            } else {
                period = "all";
            }
            
            // Get historical data
            JSONArray history = NetWorthCalculator.getNetWorthHistory(idToken, localId, period);
            
            // Create response
            JSONObject response = new JSONObject();
            response.put("history", history);
            
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
     * Handle GET request to get detailed breakdown of net worth components
     */
    private void handleGetNetWorthBreakdown(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Calculate total assets
            double bankAccounts = getBankAccountsTotal(idToken, localId);
            double investments = getInvestmentsTotal(idToken, localId);
            double crypto = getCryptoTotal(idToken, localId);
            double otherAssets = getOtherAssetsTotal(idToken, localId);
            double totalAssets = bankAccounts + investments + crypto + otherAssets;
            
            // Calculate total liabilities
            double loans = getLoansTotal(idToken, localId);
            double creditCards = getCreditCardDebtTotal(idToken, localId);
            double mortgages = getMortgagesTotal(idToken, localId);
            double otherLiabilities = getOtherLiabilitiesTotal(idToken, localId);
            double totalLiabilities = loans + creditCards + mortgages + otherLiabilities;
            
            // Calculate net worth
            double netWorth = totalAssets - totalLiabilities;
            
            // Create response
            JSONObject response = new JSONObject();
            
            // Assets breakdown
            JSONObject assets = new JSONObject();
            assets.put("bankAccounts", bankAccounts);
            assets.put("investments", investments);
            assets.put("crypto", crypto);
            assets.put("otherAssets", otherAssets);
            assets.put("total", totalAssets);
            
            // Liabilities breakdown
            JSONObject liabilities = new JSONObject();
            liabilities.put("loans", loans);
            liabilities.put("creditCards", creditCards);
            liabilities.put("mortgages", mortgages);
            liabilities.put("otherLiabilities", otherLiabilities);
            liabilities.put("total", totalLiabilities);
            
            response.put("assets", assets);
            response.put("liabilities", liabilities);
            response.put("netWorth", netWorth);
            response.put("timestamp", Instant.now().toString());
            
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
     * Update the user's net worth field in Firestore
     */
    private void updateUserNetWorth(String idToken, String localId, double netWorth) {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + localId;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            JSONObject updateData = new JSONObject();
            JSONObject fields = new JSONObject();
            fields.put("netWorth", new JSONObject().put("doubleValue", netWorth));
            updateData.put("fields", fields);
            
            OutputStream os = conn.getOutputStream();
            os.write(updateData.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.out.println("Error updating user net worth: " + responseCode);
            }
        } catch (Exception e) {
            System.out.println("Error updating user net worth: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // These methods are similar to those in NetWorthCalculator but can be optimized for this specific use case
    
    private double getBankAccountsTotal(String idToken, String localId) throws Exception {
        return getCollectionTotal(idToken, localId, "BankAccounts", "balance");
    }
    
    private double getInvestmentsTotal(String idToken, String localId) throws Exception {
        double stocks = getCollectionTotal(idToken, localId, "Stocks", "currentValue");
        double investments = getCollectionTotal(idToken, localId, "Investments", "balance");
        return stocks + investments;
    }
    
    private double getCryptoTotal(String idToken, String localId) throws Exception {
        return getCollectionTotal(idToken, localId, "Crypto", "currentValue");
    }
    
    private double getOtherAssetsTotal(String idToken, String localId) throws Exception {
        return getCollectionTotal(idToken, localId, "Assets", "value");
    }
    
    private double getLoansTotal(String idToken, String localId) throws Exception {
        return getCollectionTotal(idToken, localId, "Loans", "remainingBalance");
    }
    
    private double getCreditCardDebtTotal(String idToken, String localId) throws Exception {
        return getCollectionTotal(idToken, localId, "CreditCards", "balance");
    }
    
    private double getMortgagesTotal(String idToken, String localId) throws Exception {
        return getCollectionTotal(idToken, localId, "Mortgages", "remainingBalance");
    }
    
    private double getOtherLiabilitiesTotal(String idToken, String localId) throws Exception {
        return getCollectionTotal(idToken, localId, "Liabilities", "amount");
    }
    
    /**
     * Generic method to get the total value from a collection
     */
    private double getCollectionTotal(String idToken, String localId, String collection, String valueField) throws Exception {
        double total = 0.0;
        
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" 
                + localId + "/" + collection;
        
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
                
                for (int i = 0; i < documents.length(); i++) {
                    JSONObject document = documents.getJSONObject(i);
                    JSONObject fields = document.getJSONObject("fields");
                    
                    // Try different field names that might contain the value
                    String[] possibleFields = {valueField, "value", "balance", "amount", "currentValue"};
                    
                    for (String field : possibleFields) {
                        if (fields.has(field)) {
                            JSONObject valueObj = fields.getJSONObject(field);
                            
                            if (valueObj.has("doubleValue")) {
                                total += valueObj.getDouble("doubleValue");
                                break;
                            } else if (valueObj.has("integerValue")) {
                                total += valueObj.getInt("integerValue");
                                break;
                            }
                        }
                    }
                    
                    // Special handling for calculated values (e.g., shares * price)
                    if (fields.has("shares") && fields.has("currentPrice")) {
                        double shares = 0;
                        double price = 0;
                        
                        if (fields.getJSONObject("shares").has("doubleValue")) {
                            shares = fields.getJSONObject("shares").getDouble("doubleValue");
                        } else if (fields.getJSONObject("shares").has("integerValue")) {
                            shares = fields.getJSONObject("shares").getInt("integerValue");
                        }
                        
                        if (fields.getJSONObject("currentPrice").has("doubleValue")) {
                            price = fields.getJSONObject("currentPrice").getDouble("doubleValue");
                        } else if (fields.getJSONObject("currentPrice").has("integerValue")) {
                            price = fields.getJSONObject("currentPrice").getInt("integerValue");
                        }
                        
                        total += shares * price;
                    }
                    
                    if (fields.has("amount") && fields.has("currentPrice")) {
                        double amount = 0;
                        double price = 0;
                        
                        if (fields.getJSONObject("amount").has("doubleValue")) {
                            amount = fields.getJSONObject("amount").getDouble("doubleValue");
                        } else if (fields.getJSONObject("amount").has("integerValue")) {
                            amount = fields.getJSONObject("amount").getInt("integerValue");
                        }
                        
                        if (fields.getJSONObject("currentPrice").has("doubleValue")) {
                            price = fields.getJSONObject("currentPrice").getDouble("doubleValue");
                        } else if (fields.getJSONObject("currentPrice").has("integerValue")) {
                            price = fields.getJSONObject("currentPrice").getInt("integerValue");
                        }
                        
                        total += amount * price;
                    }
                }
            }
        } else if (responseCode != 404) {
            throw new Exception("Failed to get " + collection + ": " + responseCode);
        }
        
        return total;
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
