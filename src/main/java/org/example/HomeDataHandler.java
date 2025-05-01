package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.json.JSONObject;
import org.json.JSONArray;

public class HomeDataHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("HomeDataHandler: handle() invoked.");
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        System.out.println("Cookies: " + cookies);
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            System.out.println("No idToken or localId in cookies; sending 401");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        System.out.println("Extracted idToken: " + idToken);
        System.out.println("Extracted localId: " + localId);
        if (idToken == null || localId == null) {
            System.out.println("Either idToken or localId is null; sending 401");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        String responseJson = buildResponseJson(localId, idToken);
        byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
        System.out.println("HomeDataHandler: Response sent successfully.");
    }
    private String buildResponseJson(String localId, String idToken) {
        try {
            JSONObject doc = new JSONObject();
            doc.put("fields", new JSONObject());
            JSONObject fieldsObj = doc.getJSONObject("fields");
            
            // Fetch all financial data from Firebase
            double stockValue = fetchStockPortfolioValue(localId, idToken);
            double cryptoValue = fetchCryptoWalletValue(localId, idToken);
            double cash = fetchCashBalance(localId, idToken);
            
            // Calculate net worth including investments
            double netWorth = cash + stockValue + cryptoValue;
            fieldsObj.put("netWorth", new JSONObject().put("integerValue", String.valueOf((int)Math.round(netWorth))));
            
            // Create detailed breakdown
            JSONObject netWorthBreakdown = new JSONObject();
            netWorthBreakdown.put("cash", cash);
            netWorthBreakdown.put("stocks", stockValue);
            netWorthBreakdown.put("crypto", cryptoValue);
            fieldsObj.put("netWorthBreakdown", new JSONObject().put("stringValue", netWorthBreakdown.toString()));
            
            // Income data
            int totalIncome = 0;
            fieldsObj.put("totalIncome", new JSONObject().put("integerValue", String.valueOf(totalIncome)));
            fieldsObj.put("totalIncomeBreakdown", new JSONObject().put("stringValue", "{\"salary\":0,\"bonus\":0,\"other\":0}"));
            
            // Bills data
            int billsDue = 0;
            fieldsObj.put("billsDue", new JSONObject().put("integerValue", String.valueOf(billsDue)));
            
            // Monthly expenses and incomes
            double currentMonthTotalExpenses = 0.0;
            double currentMonthTotalIncomes = 0.0;
            double[] monthlyExpenses = fetchExpensesMonthly(localId, idToken);
            double[] monthlyIncomes = fetchIncomesMonthly(localId, idToken);
            
            // Update current month totals
            LocalDate today = LocalDate.now();
            int thisMonthIndex = today.getMonthValue() - 1;
            if (thisMonthIndex >= 0 && thisMonthIndex < 12) {
                currentMonthTotalExpenses = monthlyExpenses[thisMonthIndex];
                currentMonthTotalIncomes = monthlyIncomes[thisMonthIndex];
            }
            
            // Set monthly expenses array
            JSONArray expenseArr = new JSONArray();
            for(int i=0;i<12;i++){
                expenseArr.put(new JSONObject().put("integerValue", String.valueOf((int) monthlyExpenses[i])));
            }
            fieldsObj.put("monthlyExpenses", new JSONObject().put("arrayValue", new JSONObject().put("values", expenseArr)));
            
            // Set monthly incomes array
            JSONArray incomeArr = new JSONArray();
            for(int i=0;i<12;i++){
                incomeArr.put(new JSONObject().put("integerValue", String.valueOf((int) monthlyIncomes[i])));
            }
            fieldsObj.put("monthlyIncomes", new JSONObject().put("arrayValue", new JSONObject().put("values", incomeArr)));
            
            // Set totals for current month
            int totalExpThisMonth = (int) Math.round(currentMonthTotalExpenses);
            fieldsObj.put("totalExpenses", new JSONObject().put("integerValue", String.valueOf(totalExpThisMonth)));
            fieldsObj.put("totalIncomes", new JSONObject().put("integerValue", String.valueOf((int)Math.round(currentMonthTotalIncomes))));
            
            // Add investments data
            fieldsObj.put("totalInvestments", new JSONObject().put("integerValue", String.valueOf((int)Math.round(stockValue + cryptoValue))));
            
            // Stock portfolio performance
            double[] monthlyStockValues = getMonthlyStockValues(localId, idToken);
            JSONArray stockValueArr = new JSONArray();
            for(int i=0;i<12;i++){
                stockValueArr.put(new JSONObject().put("integerValue", String.valueOf((int) monthlyStockValues[i])));
            }
            fieldsObj.put("monthlyStockValues", new JSONObject().put("arrayValue", new JSONObject().put("values", stockValueArr)));
            
            return doc.toString();
        } catch(Exception e) {
            System.out.println("Error building HomeData JSON: " + e);
            return "{\"fields\":{}}";
        }
    }
    
    /**
     * Fetches the current stock portfolio value from Firebase
     * @return Total value of stock portfolio
     */
    private double fetchStockPortfolioValue(String localId, String idToken) {
        try {
            StockApiService apiService = new StockApiService();
            double totalValue = 0.0;
            
            // Fetch user's stock positions from Firebase
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/StockPositions";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse the positions data
                JSONObject root = new JSONObject(response.toString());
                if (root.has("documents")) {
                    JSONArray documents = root.getJSONArray("documents");
                    
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject doc = documents.getJSONObject(i);
                        if (doc.has("fields")) {
                            JSONObject fields = doc.getJSONObject("fields");
                            
                            // Extract position data
                            String symbol = "";
                            int quantity = 0;
                            
                            if (fields.has("symbol") && fields.getJSONObject("symbol").has("stringValue")) {
                                symbol = fields.getJSONObject("symbol").getString("stringValue");
                            }
                            
                            if (fields.has("quantity") && fields.getJSONObject("quantity").has("integerValue")) {
                                quantity = Integer.parseInt(fields.getJSONObject("quantity").getString("integerValue"));
                            }
                            
                            if (!symbol.isEmpty() && quantity > 0) {
                                try {
                                    // Get current price from Alpha Vantage API
                                    Stock stock = apiService.getStockQuote(symbol);
                                    totalValue += stock.getPrice() * quantity;
                                } catch (Exception e) {
                                    System.out.println("Error fetching price for " + symbol + ": " + e.getMessage());
                                    // If we can't get a live price, try to use the last known price
                                    if (fields.has("lastPrice") && fields.getJSONObject("lastPrice").has("doubleValue")) {
                                        double lastPrice = fields.getJSONObject("lastPrice").getDouble("doubleValue");
                                        totalValue += lastPrice * quantity;
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                System.out.println("Failed to fetch stock positions: HTTP " + responseCode);
            }
            
            return totalValue;
        } catch (Exception e) {
            System.out.println("Error calculating stock portfolio value: " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Fetches the current crypto wallet value from Firebase
     * @return Total value of crypto wallets
     */
    private double fetchCryptoWalletValue(String localId, String idToken) {
        try {
            WalletService walletService = new WalletService();
            double totalValue = 0.0;
            
            // Fetch user's crypto wallets from Firebase
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Wallets";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse the wallets data
                JSONObject root = new JSONObject(response.toString());
                if (root.has("documents")) {
                    JSONArray documents = root.getJSONArray("documents");
                    
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject doc = documents.getJSONObject(i);
                        if (doc.has("fields")) {
                            JSONObject fields = doc.getJSONObject("fields");
                            
                            // Extract wallet data
                            String address = "";
                            String type = "";
                            double balance = 0.0;
                            
                            if (fields.has("address") && fields.getJSONObject("address").has("stringValue")) {
                                address = fields.getJSONObject("address").getString("stringValue");
                            }
                            
                            if (fields.has("type") && fields.getJSONObject("type").has("stringValue")) {
                                type = fields.getJSONObject("type").getString("stringValue");
                            }
                            
                            if (fields.has("balance") && fields.getJSONObject("balance").has("doubleValue")) {
                                balance = fields.getJSONObject("balance").getDouble("doubleValue");
                            }
                            
                            if (!address.isEmpty() && !type.isEmpty() && balance > 0) {
                                try {
                                    WalletInfo info;
                                    if ("BTC".equals(type)) {
                                        info = walletService.getBitcoinPriceInfo();
                                    } else if ("ETH".equals(type)) {
                                        info = walletService.getEthereumPriceInfo();
                                    } else {
                                        continue;
                                    }
                                    
                                    totalValue += balance * info.currentPrice();
                                } catch (Exception e) {
                                    System.out.println("Error fetching price for " + type + ": " + e.getMessage());
                                    
                                    // If we can't get a live price, try to use the last known price
                                    if (fields.has("lastPrice") && fields.getJSONObject("lastPrice").has("doubleValue")) {
                                        double lastPrice = fields.getJSONObject("lastPrice").getDouble("doubleValue");
                                        totalValue += balance * lastPrice;
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                System.out.println("Failed to fetch crypto wallets: HTTP " + responseCode);
            }
            
            return totalValue;
        } catch (Exception e) {
            System.out.println("Error calculating crypto wallet value: " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Get monthly stock values for the past 12 months from Firebase
     */
    private double[] getMonthlyStockValues(String localId, String idToken) {
        double[] monthlyValues = new double[12];
        
        try {
            // Fetch historical portfolio values from Firebase summaries
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Summaries";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse the summaries data to find portfolio value history
                JSONObject root = new JSONObject(response.toString());
                if (root.has("documents")) {
                    JSONArray documents = root.getJSONArray("documents");
                    
                    LocalDate today = LocalDate.now();
                    int currentYear = today.getYear();
                    
                    // Get the summaries for each month
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject doc = documents.getJSONObject(i);
                        String name = doc.getString("name");
                        
                        // Check if it's a portfolio summary
                        if (name.contains("portfolio_") && doc.has("fields")) {
                            JSONObject fields = doc.getJSONObject("fields");
                            
                            // Try to parse the year_month from the document name
                            String[] parts = name.split("portfolio_");
                            if (parts.length == 2) {
                                String yearMonth = parts[1];
                                String[] dateParts = yearMonth.split("_");
                                if (dateParts.length == 2) {
                                    int year = Integer.parseInt(dateParts[0]);
                                    int month = Integer.parseInt(dateParts[1]);
                                    
                                    // Only use data from the current year
                                    if (year == currentYear && month >= 1 && month <= 12) {
                                        if (fields.has("totalValue") && 
                                            fields.getJSONObject("totalValue").has("doubleValue")) {
                                            double value = fields.getJSONObject("totalValue").getDouble("doubleValue");
                                            monthlyValues[month - 1] = value;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                System.out.println("Failed to fetch portfolio history: HTTP " + responseCode);
            }
            
            // For any missing months, use the most recent non-zero value
            double lastValue = fetchStockPortfolioValue(localId, idToken); // Current value
            for (int i = 11; i >= 0; i--) {
                if (monthlyValues[i] == 0) {
                    monthlyValues[i] = lastValue;
                } else {
                    lastValue = monthlyValues[i];
                }
            }
            
        } catch (Exception e) {
            System.out.println("Error fetching monthly stock values: " + e.getMessage());
            
            // Fallback: Use current value for all months
            double currentValue = fetchStockPortfolioValue(localId, idToken);
            for (int i = 0; i < 12; i++) {
                monthlyValues[i] = currentValue;
            }
        }
        
        return monthlyValues;
    }
    private double[] fetchExpensesMonthly(String localId, String idToken) {
        System.out.println("Fetching expenses for localId: " + localId);
        double[] monthly = new double[12];
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        
        // First try to fetch from the optimized summary collection
        boolean summarySuccess = fetchFromExpenseSummaries(localId, idToken, monthly, currentYear);
        
        // If summary data isn't available or incomplete, fall back to parsing all expense documents
        if (!summarySuccess) {
            try {
                String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                        + localId + "/Expenses";
                URL url = new URL(firestoreUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + idToken);
                int code = conn.getResponseCode();
                System.out.println("Expenses GET response code: " + code);
                if (code != 200) {
                    return monthly;
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String ln;
                while ((ln = in.readLine()) != null) sb.append(ln);
                in.close();
                String resp = sb.toString();
                System.out.println("Expenses raw GET response:\n" + resp);
                monthly = parseExpenseDocs(resp);
            } catch(Exception e){
                System.out.println("Error fetching expenses: " + e);
            }
        }
        return monthly;
    }
    
    /**
     * Attempts to fetch expense data from the optimized Summaries collection.
     * This is more efficient than processing all expense documents.
     * 
     * @return true if successful, false if data was unavailable or incomplete
     */
    private boolean fetchFromExpenseSummaries(String localId, String idToken, double[] monthly, int year) {
        try {
            // Track how many months we successfully loaded from summaries
            int monthsLoaded = 0;
            
            // Try to fetch summary for each month of the current year
            for (int month = 1; month <= 12; month++) {
                String monthStr = (month < 10) ? "0" + month : String.valueOf(month);
                String yearMonth = year + "_" + monthStr;
                
                String summaryUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                        + localId + "/Summaries/expense_" + yearMonth;
                
                URL url = new URL(summaryUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + idToken);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    // Found a summary document for this month
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Parse the summary data
                    JSONObject root = new JSONObject(response.toString());
                    if (root.has("fields")) {
                        JSONObject fields = root.getJSONObject("fields");
                        
                        // Extract the total expense for this month
                        if (fields.has("totalExpense")) {
                            JSONObject totalField = fields.getJSONObject("totalExpense");
                            if (totalField.has("doubleValue")) {
                                double totalExpense = totalField.getDouble("doubleValue");
                                monthly[month - 1] = totalExpense;
                                monthsLoaded++;
                            }
                        }
                    }
                }
            }
            
            // Consider the summary data fetch successful if we got data for at least half the months
            return monthsLoaded >= 6;
            
        } catch (Exception e) {
            System.out.println("Error fetching from expense summaries: " + e.getMessage());
            return false;
        }
    }
    private double[] parseExpenseDocs(String body) {
        double[] monthly = new double[12];
        try {
            JSONObject root = new JSONObject(body);
            if (!root.has("documents")) {
                return monthly;
            }
            JSONArray docs = root.getJSONArray("documents");
            for (int i=0; i<docs.length(); i++){
                JSONObject doc = docs.getJSONObject(i);
                JSONObject fields = doc.optJSONObject("fields");
                if (fields == null) continue;
                String dateStr = getStringValue(fields, "date");
                double total = getDoubleValue(fields, "total");
                LocalDate parsed = parseAnyDate(dateStr);
                if(parsed!=null){
                    int mo = parsed.getMonthValue()-1;
                    if(mo>=0 && mo<12){
                        monthly[mo]+= total;
                    }
                }
            }
        } catch(Exception e){
            System.out.println("parseExpenseDocs error: " + e);
        }
        return monthly;
    }
    private double[] fetchIncomesMonthly(String localId, String idToken) {
        System.out.println("Fetching incomes for localId: " + localId);
        double[] monthly = new double[12];
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        
        // First try to fetch from the optimized summary collection
        boolean summarySuccess = fetchFromIncomeSummaries(localId, idToken, monthly, currentYear);
        
        // If summary data isn't available or incomplete, fall back to parsing all income documents
        if (!summarySuccess) {
            try {
                String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                        + localId + "/Income";
                URL url = new URL(firestoreUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + idToken);
                int code = conn.getResponseCode();
                System.out.println("Incomes GET response code: " + code);
                if (code != 200) {
                    return monthly;
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String ln;
                while ((ln = in.readLine()) != null) sb.append(ln);
                in.close();
                String resp = sb.toString();
                System.out.println("Incomes raw GET response:\n" + resp);
                monthly = parseIncomeDocs(resp);
            } catch(Exception e){
                System.out.println("Error fetching incomes: " + e);
            }
        }
        return monthly;
    }
    
    /**
     * Attempts to fetch income data from the optimized Summaries collection.
     * This is more efficient than processing all income documents.
     * 
     * @return true if successful, false if data was unavailable or incomplete
     */
    /**
     * Fetches the user's cash balance from Firebase
     * @return User's cash balance
     */
    private double fetchCashBalance(String localId, String idToken) {
        try {
            // Fetch user's cash balance from Firebase
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Profile/finances";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse the finance data
                JSONObject root = new JSONObject(response.toString());
                if (root.has("fields")) {
                    JSONObject fields = root.getJSONObject("fields");
                    
                    // Look for cash balance field
                    if (fields.has("cashBalance") && fields.getJSONObject("cashBalance").has("doubleValue")) {
                        return fields.getJSONObject("cashBalance").getDouble("doubleValue");
                    }
                }
            } else {
                System.out.println("Failed to fetch cash balance: HTTP " + responseCode);
            }
            
            // Fallback value if we couldn't get the data
            return 0.0;
        } catch (Exception e) {
            System.out.println("Error fetching cash balance: " + e.getMessage());
            return 0.0;
        }
    }
    
    private boolean fetchFromIncomeSummaries(String localId, String idToken, double[] monthly, int year) {
        try {
            // Track how many months we successfully loaded from summaries
            int monthsLoaded = 0;
            
            // Try to fetch summary for each month of the current year
            for (int month = 1; month <= 12; month++) {
                String monthStr = (month < 10) ? "0" + month : String.valueOf(month);
                String yearMonth = year + "_" + monthStr;
                
                String summaryUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                        + localId + "/Summaries/income_" + yearMonth;
                
                URL url = new URL(summaryUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + idToken);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    // Found a summary document for this month
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Parse the summary data
                    JSONObject root = new JSONObject(response.toString());
                    if (root.has("fields")) {
                        JSONObject fields = root.getJSONObject("fields");
                        
                        // Extract the total income for this month
                        if (fields.has("totalIncome")) {
                            JSONObject totalField = fields.getJSONObject("totalIncome");
                            if (totalField.has("doubleValue")) {
                                double totalIncome = totalField.getDouble("doubleValue");
                                monthly[month - 1] = totalIncome;
                                monthsLoaded++;
                            }
                        }
                    }
                }
            }
            
            // Consider the summary data fetch successful if we got data for at least half the months
            return monthsLoaded >= 6;
            
        } catch (Exception e) {
            System.out.println("Error fetching from income summaries: " + e.getMessage());
            return false;
        }
    }
    private double[] parseIncomeDocs(String body) {
        double[] monthly = new double[12];
        try {
            JSONObject root = new JSONObject(body);
            if (!root.has("documents")) {
                return monthly;
            }
            JSONArray docs = root.getJSONArray("documents");
            for (int i=0; i<docs.length(); i++){
                JSONObject doc = docs.getJSONObject(i);
                JSONObject fields = doc.optJSONObject("fields");
                if (fields == null) continue;
                String dateStr = getStringValue(fields, "date");
                double total = getDoubleValue(fields, "total");
                boolean isRecurring = "true".equalsIgnoreCase(getStringValue(fields,"recurring"));
                String freq = getStringValue(fields,"frequency");
                if (isRecurring) {
                    double annualValue=0.0;
                    switch(freq.toLowerCase()){
                        case "weekly":
                            annualValue = total * 52.0;
                            break;
                        case "biweekly":
                            annualValue = total * 26.0;
                            break;
                        case "monthly":
                            annualValue = total * 12.0;
                            break;
                        case "yearly":
                            annualValue = total;
                            break;
                        default:
                            LocalDate singleDate = parseAnyDate(dateStr);
                            if(singleDate!=null){
                                int m=singleDate.getMonthValue()-1;
                                if(m>=0 && m<12){
                                    monthly[m]+= total;
                                }
                            }
                            continue;
                    }
                    double monthlyVal = annualValue/12.0;
                    for(int j=0;j<12;j++){
                        monthly[j]+= monthlyVal;
                    }
                } else {
                    LocalDate parsed = parseAnyDate(dateStr);
                    if(parsed!=null){
                        int mo = parsed.getMonthValue()-1;
                        if(mo>=0 && mo<12){
                            monthly[mo]+= total;
                        }
                    }
                }
            }
        } catch(Exception e){
            System.out.println("parseIncomeDocs error: " + e);
        }
        return monthly;
    }
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
    private LocalDate parseAnyDate(String ds){
        if(ds==null||ds.isEmpty()) return null;
        try{
            return LocalDate.parse(ds, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch(Exception e){
            try{
                return LocalDate.parse(ds, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } catch(Exception e2){
                return null;
            }
        }
    }
    private String getStringValue(JSONObject fields, String fieldName){
        if(!fields.has(fieldName)) return "";
        JSONObject node = fields.getJSONObject(fieldName);
        if(node.has("stringValue")){
            return node.getString("stringValue");
        }
        return "";
    }
    private double getDoubleValue(JSONObject fields, String fieldName){
        if(!fields.has(fieldName)) return 0.0;
        JSONObject node = fields.getJSONObject(fieldName);
        if(node.has("doubleValue")){
            return node.getDouble("doubleValue");
        }
        return 0.0;
    }
}
