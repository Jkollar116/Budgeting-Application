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
import java.time.format.DateTimeFormatter;
import com.google.cloud.Timestamp;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Handler for budgeting-related API endpoints
 */
public class BudgetingApiHandler implements HttpHandler {
    private BudgetingDatabaseService dbService;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    public BudgetingApiHandler() {
        dbService = BudgetingDatabaseService.getInstance();
        dateFormat.setLenient(false);
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Set CORS headers to allow all origins
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        // Enable pre-flight requests
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            exchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        // Get the user ID from cookies
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        String userId = extractUserIdFromCookies(cookies);
        
        if (userId == null || userId.trim().isEmpty()) {
            sendErrorResponse(exchange, "User not authenticated", 401);
            return;
        }
        
        // Parse the request URI to determine which API endpoint is being accessed
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> queryParams = parseQueryString(query);
        
        try {
            if (path.equals("/api/budgeting/dashboard")) {
                handleDashboardRequest(exchange, userId);
            } else if (path.equals("/api/budgeting/accounts")) {
                handleAccountsRequest(exchange, userId);
            } else if (path.equals("/api/budgeting/budgets")) {
                handleBudgetsRequest(exchange, userId);
            } else if (path.equals("/api/budgeting/transactions")) {
                handleTransactionsRequest(exchange, userId, queryParams);
            } else if (path.equals("/api/budgeting/goals")) {
                handleGoalsRequest(exchange, userId);
            } else {
                sendErrorResponse(exchange, "Endpoint not found", 404);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, "Internal server error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Handle requests for dashboard data
     */
    private void handleDashboardRequest(HttpExchange exchange, String userId) throws IOException {
        JSONObject response = new JSONObject();
        
        try {
            // Get accounts to calculate net worth
            List<Map<String, Object>> accounts = dbService.getUserAccounts(userId);
            double netWorth = calculateNetWorth(accounts);
            
            // Get the current budget
            List<Map<String, Object>> budgets = dbService.getUserBudgets(userId);
            double totalBudget = 0;
            double totalExpenses = 0;
            
            if (!budgets.isEmpty()) {
                Map<String, Object> currentBudget = budgets.get(0);
                if (currentBudget.containsKey("totalExpenses")) {
                    totalExpenses = ((Number) currentBudget.get("totalExpenses")).doubleValue();
                }
                
                // Calculate total budget from allocations
                if (currentBudget.containsKey("allocations")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> allocations = (Map<String, Object>) currentBudget.get("allocations");
                    for (Object value : allocations.values()) {
                        if (value instanceof Number) {
                            totalBudget += ((Number) value).doubleValue();
                        }
                    }
                }
            }
            
            // Get goals
            List<Map<String, Object>> goals = dbService.getFinancialGoals(userId);
            int activeGoals = 0;
            JSONArray topGoals = new JSONArray();
            
            for (Map<String, Object> goal : goals) {
                if ("in_progress".equals(goal.get("status"))) {
                    activeGoals++;
                    
                    // Add up to 3 top goals for the summary
                    if (topGoals.length() < 3) {
                        JSONObject topGoal = new JSONObject();
                        topGoal.put("name", goal.get("name"));
                        topGoal.put("currentAmount", ((Number) goal.get("currentAmount")).doubleValue());
                        topGoal.put("targetAmount", ((Number) goal.get("targetAmount")).doubleValue());
                        topGoals.put(topGoal);
                    }
                }
            }
            
            // Get upcoming bills
            List<Map<String, Object>> recurringTransactions = dbService.getRecurringTransactions(userId);
            int upcomingBills = countUpcomingBills(recurringTransactions);
            JSONArray nextBills = new JSONArray();
            
            // Get the next 3 bills for the summary
            Calendar now = Calendar.getInstance();
            List<Map<String, Object>> bills = new ArrayList<>();
            
            for (Map<String, Object> transaction : recurringTransactions) {
                if ("active".equals(transaction.get("status")) && "expense".equals(transaction.get("type"))) {
                    Object dayOfMonthObj = transaction.get("dayOfMonth");
                    if (dayOfMonthObj instanceof Number) {
                        int dayOfMonth = ((Number) dayOfMonthObj).intValue();
                        Calendar dueDate = Calendar.getInstance();
                        
                        // Set the due date
                        if (dayOfMonth > now.get(Calendar.DAY_OF_MONTH)) {
                            dueDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        } else {
                            dueDate.add(Calendar.MONTH, 1);
                            dueDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        }
                        
                        Map<String, Object> bill = new HashMap<>(transaction);
                        bill.put("dueDate", dateFormat.format(dueDate.getTime()));
                        bills.add(bill);
                    }
                }
            }
            
            // Sort bills by due date
            bills.sort((a, b) -> {
                String dateA = (String) a.get("dueDate");
                String dateB = (String) b.get("dueDate");
                return dateA.compareTo(dateB);
            });
            
            // Get the next 3 bills
            for (int i = 0; i < Math.min(3, bills.size()); i++) {
                Map<String, Object> bill = bills.get(i);
                JSONObject nextBill = new JSONObject();
                nextBill.put("name", bill.get("name"));
                nextBill.put("amount", ((Number) bill.get("amount")).doubleValue());
                nextBill.put("dueDate", bill.get("dueDate"));
                nextBills.put(nextBill);
            }
            
            // Build the response
            response.put("netWorth", netWorth);
            response.put("totalBudget", totalBudget);
            response.put("totalExpenses", totalExpenses);
            response.put("activeGoals", activeGoals);
            response.put("upcomingBills", upcomingBills);
            response.put("topGoals", topGoals);
            response.put("nextBills", nextBills);
            
            sendJsonResponse(exchange, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, "Error retrieving dashboard data: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Handle requests for account data
     */
    private void handleAccountsRequest(HttpExchange exchange, String userId) throws IOException {
        JSONObject response = new JSONObject();
        
        try {
            List<Map<String, Object>> accounts = dbService.getUserAccounts(userId);
            JSONArray accountsArray = new JSONArray();
            
            for (Map<String, Object> account : accounts) {
                // Make a copy of the account data with string IDs
                JSONObject jsonAccount = new JSONObject();
                for (Map.Entry<String, Object> entry : account.entrySet()) {
                    jsonAccount.put(entry.getKey(), entry.getValue());
                }
                accountsArray.put(jsonAccount);
            }
            
            response.put("accounts", accountsArray);
            sendJsonResponse(exchange, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, "Error retrieving accounts: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Handle requests for budget data
     */
    private void handleBudgetsRequest(HttpExchange exchange, String userId) throws IOException {
        JSONObject response = new JSONObject();
        
        try {
            List<Map<String, Object>> budgets = dbService.getUserBudgets(userId);
            JSONArray budgetsArray = new JSONArray();
            
            // Get current month's transactions for budget progress
            Calendar calendar = Calendar.getInstance();
            int currentYear = calendar.get(Calendar.YEAR);
            int currentMonth = calendar.get(Calendar.MONTH) + 1;
            
            String currentPeriod = String.format("%d-%02d", currentYear, currentMonth);
            
            for (Map<String, Object> budget : budgets) {
                // For now, we only process the budget for the current month
                if (budget.containsKey("year") && budget.containsKey("month")) {
                    int budgetYear = ((Number) budget.get("year")).intValue();
                    int budgetMonth = ((Number) budget.get("month")).intValue();
                    
                    // Skip budgets that are not for the current month
                    if (budgetYear != currentYear || budgetMonth != currentMonth) {
                        continue;
                    }
                }
                
                JSONObject jsonBudget = new JSONObject();
                for (Map.Entry<String, Object> entry : budget.entrySet()) {
                    jsonBudget.put(entry.getKey(), entry.getValue());
                }
                
                // Calculate actual spending by category for this budget
                Map<String, Object> allocations = (Map<String, Object>) budget.get("allocations");
                Map<String, Double> spent = calculateSpentByCategory(userId, allocations.keySet());
                
                jsonBudget.put("spent", new JSONObject(spent));
                budgetsArray.put(jsonBudget);
            }
            
            response.put("budgets", budgetsArray);
            sendJsonResponse(exchange, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, "Error retrieving budgets: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Handle requests for transaction data
     */
    private void handleTransactionsRequest(HttpExchange exchange, String userId, Map<String, String> queryParams) throws IOException {
        JSONObject response = new JSONObject();
        
        try {
            // Parse filters from query parameters
            Map<String, Object> filters = new HashMap<>();
            
            if (queryParams.containsKey("type")) {
                filters.put("type", queryParams.get("type"));
            }
            
            if (queryParams.containsKey("accountId")) {
                filters.put("accountId", queryParams.get("accountId"));
            }
            
            if (queryParams.containsKey("category")) {
                filters.put("category", queryParams.get("category"));
            }
            
            if (queryParams.containsKey("startDate")) {
                String startDateStr = queryParams.get("startDate");
                try {
                    Date startDate = dateFormat.parse(startDateStr);
                    filters.put("startDate", Timestamp.of(startDate));
                } catch (ParseException e) {
                    sendErrorResponse(exchange, "Invalid startDate format. Use YYYY-MM-DD.", 400);
                    return;
                }
            }
            
            if (queryParams.containsKey("endDate")) {
                String endDateStr = queryParams.get("endDate");
                try {
                    Date endDate = dateFormat.parse(endDateStr);
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(endDate);
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                    cal.add(Calendar.MILLISECOND, -1); // End of day
                    filters.put("endDate", Timestamp.of(cal.getTime()));
                } catch (ParseException e) {
                    sendErrorResponse(exchange, "Invalid endDate format. Use YYYY-MM-DD.", 400);
                    return;
                }
            }
            
            // Get transactions with filters
            List<Map<String, Object>> transactions = dbService.getUserTransactions(userId, filters.isEmpty() ? null : filters, 0);
            JSONArray transactionsArray = new JSONArray();
            
            // Get account names for readability
            List<Map<String, Object>> accounts = dbService.getUserAccounts(userId);
            Map<String, String> accountNames = new HashMap<>();
            for (Map<String, Object> account : accounts) {
                accountNames.put((String) account.get("id"), (String) account.get("name"));
            }
            
            for (Map<String, Object> transaction : transactions) {
                JSONObject jsonTransaction = new JSONObject();
                for (Map.Entry<String, Object> entry : transaction.entrySet()) {
                    jsonTransaction.put(entry.getKey(), entry.getValue());
                }
                
                // Add account name for readability
                if (transaction.containsKey("accountId")) {
                    String accountId = (String) transaction.get("accountId");
                    jsonTransaction.put("accountName", accountNames.getOrDefault(accountId, "Unknown Account"));
                }
                
                transactionsArray.put(jsonTransaction);
            }
            
            // Get categories for the filter
            List<Map<String, Object>> categories = dbService.getUserCategories(userId);
            
            // If the user doesn't have custom categories, get the global ones
            if (categories.isEmpty()) {
                categories = dbService.getGlobalCategories();
            }
            
            JSONArray categoriesArray = new JSONArray();
            for (Map<String, Object> category : categories) {
                JSONObject jsonCategory = new JSONObject();
                jsonCategory.put("id", category.get("id"));
                jsonCategory.put("name", category.get("name"));
                jsonCategory.put("type", category.get("type"));
                categoriesArray.put(jsonCategory);
            }
            
            response.put("transactions", transactionsArray);
            response.put("categories", categoriesArray);
            sendJsonResponse(exchange, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, "Error retrieving transactions: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Handle requests for goal data
     */
    private void handleGoalsRequest(HttpExchange exchange, String userId) throws IOException {
        JSONObject response = new JSONObject();
        
        try {
            List<Map<String, Object>> goals = dbService.getFinancialGoals(userId);
            JSONArray goalsArray = new JSONArray();
            
            for (Map<String, Object> goal : goals) {
                JSONObject jsonGoal = new JSONObject();
                for (Map.Entry<String, Object> entry : goal.entrySet()) {
                    jsonGoal.put(entry.getKey(), entry.getValue());
                }
                goalsArray.put(jsonGoal);
            }
            
            response.put("goals", goalsArray);
            sendJsonResponse(exchange, response.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, "Error retrieving goals: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Calculate a user's net worth from their accounts
     */
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
    
    /**
     * Calculate spent amount by category from transactions
     */
    private Map<String, Double> calculateSpentByCategory(String userId, Set<String> categories) {
        Map<String, Double> spent = new HashMap<>();
        
        // Initialize with zero for all categories
        for (String category : categories) {
            spent.put(category, 0.0);
        }
        
        // Get current month's transactions
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1); // Start of month
        Date startDate = calendar.getTime();
        
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1); // End of month
        Date endDate = calendar.getTime();
        
        Map<String, Object> filters = new HashMap<>();
        filters.put("startDate", Timestamp.of(startDate));
        filters.put("endDate", Timestamp.of(endDate));
        filters.put("type", "expense");
        
        List<Map<String, Object>> transactions = dbService.getUserTransactions(userId, filters, 0);
        
        // Sum up expenses by category
        for (Map<String, Object> transaction : transactions) {
            String category = (String) transaction.get("category");
            Object amountObj = transaction.get("amount");
            
            if (category != null && spent.containsKey(category) && amountObj instanceof Number) {
                double amount = ((Number) amountObj).doubleValue();
                spent.put(category, spent.get(category) + amount);
            }
        }
        
        return spent;
    }
    
    /**
     * Count upcoming bills due in the next 7 days
     */
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
    
    /**
     * Parse query parameters from a query string
     */
    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String key = idx > 0 ? pair.substring(0, idx) : pair;
            String value = idx > 0 && pair.length() > idx + 1 ? pair.substring(idx + 1) : "";
            params.put(key, value);
        }
        
        return params;
    }
    
    /**
     * Extract the user ID from the cookies
     */
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
    
    /**
     * Send a JSON response
     */
    private void sendJsonResponse(HttpExchange exchange, String jsonString) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] responseBytes = jsonString.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
    
    /**
     * Send an error response
     */
    private void sendErrorResponse(HttpExchange exchange, String errorMessage, int statusCode) throws IOException {
        JSONObject error = new JSONObject();
        error.put("error", errorMessage);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] responseBytes = error.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
}
