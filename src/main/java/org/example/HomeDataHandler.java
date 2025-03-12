package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.json.JSONObject;

public class HomeDataHandler implements HttpHandler {
    private BudgetingDatabaseService dbService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM");
    
    public HomeDataHandler() {
        dbService = BudgetingDatabaseService.getInstance();
    }
    
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        // Get the user ID from cookies
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        String userId = extractUserIdFromCookies(cookies);
        
        if (userId == null || userId.trim().isEmpty()) {
            sendErrorResponse(exchange, "User not authenticated");
            return;
        }
        
        // Get data from Firebase using the BudgetingDatabaseService
        Map<String, Object> responseData = new HashMap<>();
        
        try {
            // Get user accounts to calculate net worth
            List<Map<String, Object>> accounts = dbService.getUserAccounts(userId);
            double netWorth = calculateNetWorth(accounts);
            Map<String, Double> netWorthBreakdown = calculateNetWorthBreakdown(accounts);
            
            // Get user's transactions to calculate income and expenses
            double totalIncome = 0;
            double totalExpenses = 0;
            Map<String, Double> incomeBreakdown = new HashMap<>();
            Map<String, Double> expenseBreakdown = new HashMap<>();
            List<Double> monthlyExpenses = new ArrayList<>();
            
            // Get all transactions for the current year
            List<Map<String, Object>> transactions = getUserTransactionsForCurrentYear(userId);
            processTransactions(transactions, incomeBreakdown, expenseBreakdown, monthlyExpenses);
            
            // Calculate totals
            for (double amount : incomeBreakdown.values()) {
                totalIncome += amount;
            }
            
            for (double amount : expenseBreakdown.values()) {
                totalExpenses += amount;
            }
            
            // Get upcoming bills (recurring transactions due within the next 7 days)
            List<Map<String, Object>> recurringTransactions = dbService.getRecurringTransactions(userId);
            int billsDue = countUpcomingBills(recurringTransactions);
            
            // Build the response object
            responseData.put("netWorth", roundToTwoDecimals(netWorth));
            responseData.put("netWorthBreakdown", netWorthBreakdown);
            responseData.put("totalIncome", roundToTwoDecimals(totalIncome));
            responseData.put("totalIncomeBreakdown", incomeBreakdown);
            responseData.put("totalExpenses", roundToTwoDecimals(totalExpenses));
            responseData.put("billsDue", billsDue);
            responseData.put("monthlyExpenses", monthlyExpenses);
            
            // Convert Map to JSON
            JSONObject jsonResponse = new JSONObject(responseData);
            String jsonString = jsonResponse.toString();
            
            // Send the response
            byte[] responseBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, "Error retrieving data: " + e.getMessage());
        }
    }
    
    private String extractUserIdFromCookies(String cookies) {
        if (cookies == null) return null;
        
        String[] cookiePairs = cookies.split(";");
        for (String cookiePair : cookiePairs) {
            String[] cookieNameValue = cookiePair.trim().split("=");
            if (cookieNameValue.length == 2 && cookieNameValue[0].equals("userId")) {
                return cookieNameValue[1];
            }
        }
        
        return null;
    }
    
    private double calculateNetWorth(List<Map<String, Object>> accounts) {
        double netWorth = 0;
        
        for (Map<String, Object> account : accounts) {
            Object balanceObj = account.get("balance");
            boolean includeInTotals = account.containsKey("includeInTotals") ? 
                                     (boolean) account.get("includeInTotals") : true;
            
            if (balanceObj instanceof Number && includeInTotals) {
                netWorth += ((Number) balanceObj).doubleValue();
            }
        }
        
        return netWorth;
    }
    
    private Map<String, Double> calculateNetWorthBreakdown(List<Map<String, Object>> accounts) {
        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("cash", 0.0);
        breakdown.put("equity", 0.0);
        breakdown.put("investments", 0.0);
        
        for (Map<String, Object> account : accounts) {
            Object balanceObj = account.get("balance");
            String type = (String) account.get("type");
            boolean includeInTotals = account.containsKey("includeInTotals") ? 
                                     (boolean) account.get("includeInTotals") : true;
            
            if (balanceObj instanceof Number && includeInTotals) {
                double balance = ((Number) balanceObj).doubleValue();
                
                if ("checking".equals(type) || "savings".equals(type)) {
                    breakdown.put("cash", breakdown.get("cash") + balance);
                } else if ("credit".equals(type)) {
                    // Credit card balances are typically negative (money owed)
                    breakdown.put("cash", breakdown.get("cash") + balance);
                } else if ("investment".equals(type) || "brokerage".equals(type)) {
                    breakdown.put("investments", breakdown.get("investments") + balance);
                } else if ("property".equals(type) || "real-estate".equals(type)) {
                    breakdown.put("equity", breakdown.get("equity") + balance);
                }
            }
        }
        
        // Round values to two decimal places
        for (String key : breakdown.keySet()) {
            breakdown.put(key, roundToTwoDecimals(breakdown.get(key)));
        }
        
        return breakdown;
    }
    
    private List<Map<String, Object>> getUserTransactionsForCurrentYear(String userId) {
        // Get current year's transactions
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        Map<String, Object> filters = new HashMap<>();
        
        // Get all transactions, we'll filter by date in code
        return dbService.getUserTransactions(userId, null, 0);
    }
    
    private void processTransactions(List<Map<String, Object>> transactions, 
                                    Map<String, Double> incomeBreakdown,
                                    Map<String, Double> expenseBreakdown,
                                    List<Double> monthlyExpenses) {
        // Initialize monthly expenses for 12 months
        for (int i = 0; i < 12; i++) {
            monthlyExpenses.add(0.0);
        }
        
        // Process each transaction
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        
        for (Map<String, Object> transaction : transactions) {
            String type = (String) transaction.get("type");
            Object amountObj = transaction.get("amount");
            String category = (String) transaction.get("category");
            Object timestampObj = transaction.get("timestamp");
            
            if (amountObj instanceof Number) {
                double amount = ((Number) amountObj).doubleValue();
                
                // Skip transfers - they're not income or expenses
                if ("transfer".equals(type)) {
                    continue;
                }
                
                // Get the transaction date
                Date transactionDate = null;
                if (timestampObj != null) {
                    if (timestampObj instanceof com.google.cloud.Timestamp) {
                        transactionDate = ((com.google.cloud.Timestamp) timestampObj).toDate();
                    } else if (timestampObj instanceof Date) {
                        transactionDate = (Date) timestampObj;
                    } else if (timestampObj instanceof String) {
                        try {
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                            transactionDate = formatter.parse((String) timestampObj);
                        } catch (ParseException e) {
                            // If parsing fails, skip this transaction for monthly expenses
                        }
                    }
                }
                
                // Skip transactions from other years
                if (transactionDate != null) {
                    calendar.setTime(transactionDate);
                    int transactionYear = calendar.get(Calendar.YEAR);
                    
                    if (transactionYear != currentYear) {
                        continue;
                    }
                    
                    // Add to monthly expenses array (zero-based index)
                    int month = calendar.get(Calendar.MONTH);
                    if (month >= 0 && month < 12 && "expense".equals(type)) {
                        monthlyExpenses.set(month, monthlyExpenses.get(month) + amount);
                    }
                }
                
                // Process by transaction type
                if ("income".equals(type)) {
                    // Check for specific income categories
                    String incomeCategory = "other";
                    if (category != null) {
                        if (category.contains("salary")) {
                            incomeCategory = "salary";
                        } else if (category.contains("bonus")) {
                            incomeCategory = "bonus";
                        } else if (category.contains("investment")) {
                            incomeCategory = "investments";
                        }
                    }
                    
                    // Update income breakdown
                    incomeBreakdown.put(incomeCategory, 
                        incomeBreakdown.getOrDefault(incomeCategory, 0.0) + amount);
                    
                } else if ("expense".equals(type)) {
                    // Group expense categories
                    String expenseCategory = "other";
                    if (category != null) {
                        if (category.contains("housing") || category.contains("rent") || category.contains("mortgage")) {
                            expenseCategory = "housing";
                        } else if (category.contains("food") || category.contains("groceries") || category.contains("dining")) {
                            expenseCategory = "food";
                        } else if (category.contains("utilities")) {
                            expenseCategory = "utilities";
                        } else if (category.contains("transportation")) {
                            expenseCategory = "transportation";
                        }
                    }
                    
                    // Update expense breakdown
                    expenseBreakdown.put(expenseCategory, 
                        expenseBreakdown.getOrDefault(expenseCategory, 0.0) + amount);
                }
            }
        }
        
        // Ensure we have some default categories even if no transactions
        if (incomeBreakdown.isEmpty()) {
            incomeBreakdown.put("salary", 0.0);
            incomeBreakdown.put("bonus", 0.0);
            incomeBreakdown.put("other", 0.0);
        }
        
        if (expenseBreakdown.isEmpty()) {
            expenseBreakdown.put("housing", 0.0);
            expenseBreakdown.put("food", 0.0);
            expenseBreakdown.put("utilities", 0.0);
            expenseBreakdown.put("transportation", 0.0);
            expenseBreakdown.put("other", 0.0);
        }
        
        // Round all values
        for (String key : incomeBreakdown.keySet()) {
            incomeBreakdown.put(key, roundToTwoDecimals(incomeBreakdown.get(key)));
        }
        
        for (String key : expenseBreakdown.keySet()) {
            expenseBreakdown.put(key, roundToTwoDecimals(expenseBreakdown.get(key)));
        }
        
        for (int i = 0; i < monthlyExpenses.size(); i++) {
            monthlyExpenses.set(i, roundToTwoDecimals(monthlyExpenses.get(i)));
        }
    }
    
    private int countUpcomingBills(List<Map<String, Object>> recurringTransactions) {
        int count = 0;
        Calendar now = Calendar.getInstance();
        Calendar nextWeek = Calendar.getInstance();
        nextWeek.add(Calendar.DAY_OF_MONTH, 7);
        
        for (Map<String, Object> transaction : recurringTransactions) {
            String status = (String) transaction.get("status");
            if (!"active".equals(status)) {
                continue;
            }
            
            // Only count expenses as bills
            String type = (String) transaction.get("type");
            if (!"expense".equals(type)) {
                continue;
            }
            
            // Check if this bill is due in the next week
            Object dayOfMonthObj = transaction.get("dayOfMonth");
            String frequency = (String) transaction.get("frequency");
            
            if (dayOfMonthObj instanceof Number && "monthly".equals(frequency)) {
                int dayOfMonth = ((Number) dayOfMonthObj).intValue();
                Calendar nextDueDate = Calendar.getInstance();
                
                // Set the day of the month for the due date
                if (dayOfMonth > now.get(Calendar.DAY_OF_MONTH)) {
                    // Due date is this month
                    nextDueDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                } else {
                    // Due date is next month
                    nextDueDate.add(Calendar.MONTH, 1);
                    nextDueDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                }
                
                // Check if due date is within the next week
                if (nextDueDate.after(now) && nextDueDate.before(nextWeek)) {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
    
    private void sendErrorResponse(HttpExchange exchange, String errorMessage) throws IOException {
        String jsonError = "{\"error\":\"" + errorMessage + "\"}";
        byte[] responseBytes = jsonError.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(400, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
}
