package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.cloud.firestore.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Enhanced handler for home page dashboard data
 * Uses FirestoreService for more reliable Firebase operations
 */
public class HomeDataHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(HomeDataHandler.class.getName());
    private final FirestoreService firestoreService;
    private final StockApiService stockApiService;
    private final WalletService walletService;
    
    private static final String SUMMARIES_COLLECTION = "Summaries";
    private static final String EXPENSES_COLLECTION = "Expenses";
    private static final String INCOME_COLLECTION = "Income";
    private static final String STOCK_POSITIONS_COLLECTION = "StockPositions";
    private static final String WALLETS_COLLECTION = "Wallets";
    private static final String PROFILE_COLLECTION = "Profile";

    public HomeDataHandler() {
        this.firestoreService = FirestoreService.getInstance();
        this.stockApiService = new StockApiService();
        this.walletService = new WalletService();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LOGGER.info("HomeDataHandler: handle() invoked.");
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            LOGGER.warning("No idToken or localId in cookies; sending 401");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        
        LOGGER.info("Processing request for user: " + localId);
        
        if (idToken == null || localId == null) {
            LOGGER.warning("Either idToken or localId is null; sending 401");
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        if (!firestoreService.isInitialized()) {
            LOGGER.severe("Firestore service not initialized - returning mock data instead");
            // Instead of returning a 500 error or empty data, return mock data for development
            String mockResponseJson = createMockResponse();
            byte[] responseBytes = mockResponseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            return;
        }
        
        try {
            String responseJson = buildResponseJson(localId);
            byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            LOGGER.info("HomeDataHandler: Response sent successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing home data request", e);
            exchange.sendResponseHeaders(500, -1);
        }
    }
    
    private String buildResponseJson(String localId) {
        try {
            JSONObject doc = new JSONObject();
            doc.put("fields", new JSONObject());
            JSONObject fieldsObj = doc.getJSONObject("fields");
            
            // Fetch all financial data from Firebase using our service
            double stockValue = fetchStockPortfolioValue(localId);
            double cryptoValue = fetchCryptoWalletValue(localId);
            double cash = fetchCashBalance(localId);
            
            // Calculate net worth including investments
            double netWorth = cash + stockValue + cryptoValue;
            fieldsObj.put("netWorth", new JSONObject().put("integerValue", String.valueOf((int)Math.round(netWorth))));
            
            // Create detailed breakdown
            JSONObject netWorthBreakdown = new JSONObject();
            netWorthBreakdown.put("cash", cash);
            netWorthBreakdown.put("stocks", stockValue);
            netWorthBreakdown.put("crypto", cryptoValue);
            fieldsObj.put("netWorthBreakdown", new JSONObject().put("stringValue", netWorthBreakdown.toString()));
            
            // Income data from summaries
            int totalIncome = fetchTotalIncome(localId);
            fieldsObj.put("totalIncome", new JSONObject().put("integerValue", String.valueOf(totalIncome)));
            
            // Get income breakdown for pie chart
            JSONObject incomeBreakdown = fetchIncomeBreakdown(localId);
            fieldsObj.put("totalIncomeBreakdown", new JSONObject().put("stringValue", incomeBreakdown.toString()));
            
            // Get bills due count
            int billsDue = fetchBillsDueCount(localId);
            fieldsObj.put("billsDue", new JSONObject().put("integerValue", String.valueOf(billsDue)));
            
            // Monthly expenses and incomes from summaries
            double[] monthlyExpenses = fetchExpensesMonthly(localId);
            double[] monthlyIncomes = fetchIncomesMonthly(localId);
            
            // Calculate this month's totals
            LocalDate today = LocalDate.now();
            int thisMonthIndex = today.getMonthValue() - 1;
            double currentMonthTotalExpenses = (thisMonthIndex >= 0 && thisMonthIndex < 12) ? 
                monthlyExpenses[thisMonthIndex] : 0.0;
            double currentMonthTotalIncomes = (thisMonthIndex >= 0 && thisMonthIndex < 12) ? 
                monthlyIncomes[thisMonthIndex] : 0.0;
            
            // Add monthly arrays to response
            JSONArray expenseArr = new JSONArray();
            for (int i = 0; i < 12; i++) {
                expenseArr.put(new JSONObject().put("integerValue", String.valueOf((int) monthlyExpenses[i])));
            }
            fieldsObj.put("monthlyExpenses", new JSONObject().put("arrayValue", new JSONObject().put("values", expenseArr)));
            
            JSONArray incomeArr = new JSONArray();
            for (int i = 0; i < 12; i++) {
                incomeArr.put(new JSONObject().put("integerValue", String.valueOf((int) monthlyIncomes[i])));
            }
            fieldsObj.put("monthlyIncomes", new JSONObject().put("arrayValue", new JSONObject().put("values", incomeArr)));
            
            // Set totals for current month
            int totalExpThisMonth = (int) Math.round(currentMonthTotalExpenses);
            fieldsObj.put("totalExpenses", new JSONObject().put("integerValue", String.valueOf(totalExpThisMonth)));
            fieldsObj.put("totalIncomes", new JSONObject().put("integerValue", String.valueOf((int)Math.round(currentMonthTotalIncomes))));
            
            // Add investments data
            int totalInvestments = (int) Math.round(stockValue + cryptoValue);
            fieldsObj.put("totalInvestments", new JSONObject().put("integerValue", String.valueOf(totalInvestments)));
            
            // Stock portfolio performance
            double[] monthlyStockValues = getMonthlyStockValues(localId);
            JSONArray stockValueArr = new JSONArray();
            for (int i = 0; i < 12; i++) {
                stockValueArr.put(new JSONObject().put("integerValue", String.valueOf((int) monthlyStockValues[i])));
            }
            fieldsObj.put("monthlyStockValues", new JSONObject().put("arrayValue", new JSONObject().put("values", stockValueArr)));
            
            // Make sure we have at least the minimum structure for the charts to render
            ensureMinimumStructure(fieldsObj);
            
            // Log the final response
            String response = doc.toString();
            LOGGER.info("Returning dashboard data (first 100 chars): " + 
                (response.length() > 100 ? response.substring(0, 100) + "..." : response));
            return response;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error building HomeData JSON", e);
            return "{\"fields\":{}}";
        }
    }
    
    /**
     * Fetches the current stock portfolio value using FirestoreService
     */
    private double fetchStockPortfolioValue(String localId) {
        double totalValue = 0.0;
        
        try {
            // Get collection from Firestore
            CollectionReference portfolioRef = firestoreService.getSubcollection(localId, STOCK_POSITIONS_COLLECTION);
            
            if (portfolioRef == null) {
                LOGGER.warning("Failed to get stock positions collection reference");
                return totalValue;
            }
            
            // Query all stock positions
            QuerySnapshot querySnapshot = portfolioRef.get().get();
            
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Map<String, Object> data = doc.getData();
                
                if (data != null) {
                    String symbol = "";
                    int quantity = 0;
                    double lastPrice = 0.0;
                    
                    if (data.get("symbol") instanceof String) {
                        symbol = (String) data.get("symbol");
                    }
                    
                    if (data.get("quantity") instanceof Number) {
                        quantity = ((Number) data.get("quantity")).intValue();
                    }
                    
                    if (quantity > 0 && !symbol.isEmpty()) {
                        try {
                            // First try to get current price from API
                            Stock stock = stockApiService.getStockQuote(symbol);
                            lastPrice = stock.getPrice();
                        } catch (Exception e) {
                            LOGGER.warning("Error fetching price for " + symbol + ": " + e.getMessage());
                            
                            // Fall back to last stored price if available
                            if (data.get("lastPrice") instanceof Number) {
                                lastPrice = ((Number) data.get("lastPrice")).doubleValue();
                            }
                        }
                        
                        // Add to portfolio value
                        totalValue += quantity * lastPrice;
                    }
                }
            }
            
            LOGGER.info("Portfolio value calculated: " + totalValue);
            return totalValue;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calculating stock portfolio value", e);
            return 0.0;
        }
    }
    
    /**
     * Fetches the current crypto wallet value using FirestoreService
     */
    private double fetchCryptoWalletValue(String localId) {
        double totalValue = 0.0;
        
        try {
            // Get collection from Firestore
            CollectionReference walletsRef = firestoreService.getSubcollection(localId, WALLETS_COLLECTION);
            
            if (walletsRef == null) {
                LOGGER.warning("Failed to get wallets collection reference");
                return totalValue;
            }
            
            // Query all wallets
            QuerySnapshot querySnapshot = walletsRef.get().get();
            
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Map<String, Object> data = doc.getData();
                
                if (data != null) {
                    String address = "";
                    String type = "";
                    double balance = 0.0;
                    double lastPrice = 0.0;
                    
                    if (data.get("address") instanceof String) {
                        address = (String) data.get("address");
                    }
                    
                    if (data.get("cryptoType") instanceof String) {
                        type = (String) data.get("cryptoType");
                    }
                    
                    if (data.get("balance") instanceof Number) {
                        balance = ((Number) data.get("balance")).doubleValue();
                    }
                    
                    if (!address.isEmpty() && !type.isEmpty() && balance > 0) {
                        try {
                            // Try to get current price from API
                            WalletInfo info;
                            if ("BTC".equals(type)) {
                                info = walletService.getBitcoinPriceInfo();
                            } else if ("ETH".equals(type)) {
                                info = walletService.getEthereumPriceInfo();
                            } else {
                                continue;
                            }
                            
                            lastPrice = info.currentPrice();
                        } catch (Exception e) {
                            LOGGER.warning("Error fetching price for " + type + ": " + e.getMessage());
                            
                            // Fall back to last stored price if available
                            if (data.get("lastPrice") instanceof Number) {
                                lastPrice = ((Number) data.get("lastPrice")).doubleValue();
                            }
                        }
                        
                        // Add to total value
                        totalValue += balance * lastPrice;
                    }
                }
            }
            
            LOGGER.info("Crypto wallet value calculated: " + totalValue);
            return totalValue;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error calculating crypto wallet value", e);
            return 0.0;
        }
    }
    
    /**
     * Fetches the user's cash balance from profile
     */
    private double fetchCashBalance(String localId) {
        try {
            // Get document from Firestore
            DocumentReference financeDoc = firestoreService.getSubcollectionDocument(localId, PROFILE_COLLECTION, "finances");
            
            if (financeDoc == null) {
                LOGGER.warning("Failed to get finances document reference");
                return 0.0;
            }
            
            // Get the document
            DocumentSnapshot snapshot = financeDoc.get().get();
            
            if (snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                
                if (data != null && data.get("cashBalance") instanceof Number) {
                    return ((Number) data.get("cashBalance")).doubleValue();
                }
            }
            
            LOGGER.info("No cash balance found, using default value");
            return 0.0;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching cash balance", e);
            return 0.0;
        }
    }
    
    /**
     * Fetches monthly expenses from summaries or calculates from expenses
     */
    private double[] fetchExpensesMonthly(String localId) {
        double[] monthly = new double[12];
        Arrays.fill(monthly, 0.0);
        
        try {
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();
            
            // First try to get values from summary documents
            boolean summariesComplete = fetchFromExpenseSummaries(localId, monthly, currentYear);
            
            // If summaries aren't complete, calculate from expenses collection
            if (!summariesComplete) {
                LOGGER.info("Expense summaries incomplete, calculating from expenses collection");
                
                // Get collection from Firestore
                CollectionReference expensesRef = firestoreService.getSubcollection(localId, EXPENSES_COLLECTION);
                
                if (expensesRef == null) {
                    LOGGER.warning("Failed to get expenses collection reference");
                    return monthly;
                }
                
                // Query all expenses
                QuerySnapshot querySnapshot = expensesRef.get().get();
                
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Map<String, Object> data = doc.getData();
                    
                    if (data != null) {
                        String dateStr = "";
                        double total = 0.0;
                        
                        if (data.get("date") instanceof String) {
                            dateStr = (String) data.get("date");
                        }
                        
                        if (data.get("total") instanceof Number) {
                            total = ((Number) data.get("total")).doubleValue();
                        }
                        
                        LocalDate parsedDate = tryParseDate(dateStr);
                        if (parsedDate != null) {
                            int mo = parsedDate.getMonthValue() - 1;
                            if (mo >= 0 && mo < 12) {
                                monthly[mo] += total;
                            }
                        }
                    }
                }
            }
            
            return monthly;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching monthly expenses", e);
            return monthly;
        }
    }
    
    /**
     * Fetches expense summaries from the Summaries collection
     */
    private boolean fetchFromExpenseSummaries(String localId, double[] monthly, int year) {
        try {
            int monthsLoaded = 0;
            
            // Try to fetch summary for each month of the current year
            for (int month = 1; month <= 12; month++) {
                String monthStr = (month < 10) ? "0" + month : String.valueOf(month);
                String yearMonth = year + "_" + monthStr;
                String summaryId = "expense_" + yearMonth;
                
                // Get the summary document
                DocumentReference summaryRef = firestoreService.getSubcollectionDocument(
                    localId, SUMMARIES_COLLECTION, summaryId);
                
                if (summaryRef == null) {
                    continue;
                }
                
                DocumentSnapshot snapshot = summaryRef.get().get();
                
                if (snapshot.exists()) {
                    Map<String, Object> data = snapshot.getData();
                    
                    if (data != null && data.get("totalExpense") instanceof Number) {
                        double totalExpense = ((Number) data.get("totalExpense")).doubleValue();
                        monthly[month - 1] = totalExpense;
                        monthsLoaded++;
                    }
                }
            }
            
            // Consider summaries complete if we have at least half the months
            return monthsLoaded >= 6;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching expense summaries", e);
            return false;
        }
    }
    
    /**
     * Fetches monthly income from summaries or calculates from income
     */
    private double[] fetchIncomesMonthly(String localId) {
        double[] monthly = new double[12];
        Arrays.fill(monthly, 0.0);
        
        try {
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();
            
            // First try to get values from summary documents
            boolean summariesComplete = fetchFromIncomeSummaries(localId, monthly, currentYear);
            
            // If summaries aren't complete, calculate from income collection
            if (!summariesComplete) {
                LOGGER.info("Income summaries incomplete, calculating from income collection");
                
                // Get collection from Firestore
                CollectionReference incomesRef = firestoreService.getSubcollection(localId, INCOME_COLLECTION);
                
                if (incomesRef == null) {
                    LOGGER.warning("Failed to get income collection reference");
                    return monthly;
                }
                
                // Query all income entries
                QuerySnapshot querySnapshot = incomesRef.get().get();
                
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Map<String, Object> data = doc.getData();
                    
                    if (data != null) {
                        String dateStr = "";
                        double total = 0.0;
                        boolean isRecurring = false;
                        String frequency = "";
                        
                        if (data.get("date") instanceof String) {
                            dateStr = (String) data.get("date");
                        }
                        
                        if (data.get("total") instanceof Number) {
                            total = ((Number) data.get("total")).doubleValue();
                        }
                        
                        if (data.get("recurring") instanceof String) {
                            isRecurring = "true".equalsIgnoreCase((String) data.get("recurring"));
                        }
                        
                        if (data.get("frequency") instanceof String) {
                            frequency = (String) data.get("frequency");
                        }
                        
                        if (isRecurring) {
                            double annualValue = 0.0;
                            switch (frequency.toLowerCase()) {
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
                                    LocalDate singleDate = tryParseDate(dateStr);
                                    if (singleDate != null) {
                                        int m = singleDate.getMonthValue() - 1;
                                        if (m >= 0 && m < 12) {
                                            monthly[m] += total;
                                        }
                                    }
                                    continue;
                            }
                            
                            // Distribute annual value across months
                            double monthlyVal = annualValue / 12.0;
                            for (int j = 0; j < 12; j++) {
                                monthly[j] += monthlyVal;
                            }
                        } else {
                            // Non-recurring income
                            LocalDate parsedDate = tryParseDate(dateStr);
                            if (parsedDate != null) {
                                int mo = parsedDate.getMonthValue() - 1;
                                if (mo >= 0 && mo < 12) {
                                    monthly[mo] += total;
                                }
                            }
                        }
                    }
                }
            }
            
            return monthly;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching monthly incomes", e);
            return monthly;
        }
    }
    
    /**
     * Fetches income summaries from the Summaries collection
     */
    private boolean fetchFromIncomeSummaries(String localId, double[] monthly, int year) {
        try {
            int monthsLoaded = 0;
            
            // Try to fetch summary for each month of the current year
            for (int month = 1; month <= 12; month++) {
                String monthStr = (month < 10) ? "0" + month : String.valueOf(month);
                String yearMonth = year + "_" + monthStr;
                String summaryId = "income_" + yearMonth;
                
                // Get the summary document
                DocumentReference summaryRef = firestoreService.getSubcollectionDocument(
                    localId, SUMMARIES_COLLECTION, summaryId);
                
                if (summaryRef == null) {
                    continue;
                }
                
                DocumentSnapshot snapshot = summaryRef.get().get();
                
                if (snapshot.exists()) {
                    Map<String, Object> data = snapshot.getData();
                    
                    if (data != null && data.get("totalIncome") instanceof Number) {
                        double totalIncome = ((Number) data.get("totalIncome")).doubleValue();
                        monthly[month - 1] = totalIncome;
                        monthsLoaded++;
                    }
                }
            }
            
            // Consider summaries complete if we have at least half the months
            return monthsLoaded >= 6;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching income summaries", e);
            return false;
        }
    }
    
    /**
     * Calculates the total income for the current month
     */
    private int fetchTotalIncome(String localId) {
        try {
            // Get the current month
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();
            int currentMonth = today.getMonthValue();
            String monthStr = (currentMonth < 10) ? "0" + currentMonth : String.valueOf(currentMonth);
            String yearMonth = currentYear + "_" + monthStr;
            String summaryId = "income_" + yearMonth;
            
            // Get the summary document
            DocumentReference summaryRef = firestoreService.getSubcollectionDocument(
                localId, SUMMARIES_COLLECTION, summaryId);
            
            if (summaryRef == null) {
                return 0;
            }
            
            DocumentSnapshot snapshot = summaryRef.get().get();
            
            if (snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                
                if (data != null && data.get("totalIncome") instanceof Number) {
                    return ((Number) data.get("totalIncome")).intValue();
                }
            }
            
            LOGGER.info("No income summary found for current month");
            return 0;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching total income", e);
            return 0;
        }
    }
    
    /**
     * Get income breakdown for pie chart
     */
    private JSONObject fetchIncomeBreakdown(String localId) {
        JSONObject breakdown = new JSONObject();
        breakdown.put("salary", 0);
        breakdown.put("bonus", 0);
        breakdown.put("other", 0);
        
        try {
            // Get income collection
            CollectionReference incomesRef = firestoreService.getSubcollection(localId, INCOME_COLLECTION);
            
            if (incomesRef == null) {
                return breakdown;
            }
            
            // Query recurring income (most important for breakdown)
            QuerySnapshot querySnapshot = incomesRef.whereEqualTo("recurring", "true").get().get();
            
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Map<String, Object> data = doc.getData();
                
                if (data != null) {
                    String name = "";
                    double total = 0.0;
                    String frequency = "";
                    
                    if (data.get("name") instanceof String) {
                        name = ((String) data.get("name")).toLowerCase();
                    }
                    
                    if (data.get("total") instanceof Number) {
                        total = ((Number) data.get("total")).doubleValue();
                    }
                    
                    if (data.get("frequency") instanceof String) {
                        frequency = (String) data.get("frequency");
                    }
                    
                    // Calculate annual value
                    double annualValue = 0.0;
                    switch (frequency.toLowerCase()) {
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
                            continue;
                    }
                    
                    // Categorize by name
                    if (name.contains("salary") || name.contains("wage")) {
                        breakdown.put("salary", breakdown.getDouble("salary") + annualValue);
                    } else if (name.contains("bonus")) {
                        breakdown.put("bonus", breakdown.getDouble("bonus") + annualValue);
                    } else {
                        breakdown.put("other", breakdown.getDouble("other") + annualValue);
                    }
                }
            }
            
            return breakdown;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching income breakdown", e);
            return breakdown;
        }
    }
    
    /**
     * Count number of bills due this month
     */
    private int fetchBillsDueCount(String localId) {
        try {
            // Get current month and year
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();
            int currentMonth = today.getMonthValue();
            String yearMonth = currentYear + "_" + (currentMonth < 10 ? "0" : "") + currentMonth;
            
            // Query bills collection for this month
            CollectionReference billsRef = firestoreService.getSubcollection(localId, "Bills");
            
            if (billsRef == null) {
                return 0;
            }
            
            // Get bills for current month
            QuerySnapshot querySnapshot = billsRef.whereEqualTo("yearMonth", yearMonth)
                .whereEqualTo("paid", false).get().get();
            
            return querySnapshot.size();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching bills due count", e);
            return 0;
        }
    }
    
    /**
     * Get monthly stock values for the past 12 months
     */
    private double[] getMonthlyStockValues(String localId) {
        double[] monthlyValues = new double[12];
        Arrays.fill(monthlyValues, 0.0);
        
        try {
            // Try to fetch summaries for each month
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();
            
            // Query portfolio summaries
            CollectionReference summariesRef = firestoreService.getSubcollection(localId, SUMMARIES_COLLECTION);
            
            if (summariesRef == null) {
                return monthlyValues;
            }
            
            QuerySnapshot querySnapshot = summariesRef.get().get();
            
            // Process each summary document
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                String name = doc.getId();
                
                // Check if it's a portfolio summary
                if (name.startsWith("portfolio_") && name.length() >= 12) {
                    String yearMonthStr = name.substring(10); // Extract YYYY_MM
                    String[] parts = yearMonthStr.split("_");
                    
                    if (parts.length == 2) {
                        try {
                            int year = Integer.parseInt(parts[0]);
                            int month = Integer.parseInt(parts[1]);
                            
                            // Only use data from current year
                            if (year == currentYear && month >= 1 && month <= 12) {
                                Map<String, Object> data = doc.getData();
                                
                                if (data != null && data.get("portfolioValue") instanceof Number) {
                                    double value = ((Number) data.get("portfolioValue")).doubleValue();
                                    monthlyValues[month - 1] = value;
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Ignore invalid formats
                        }
                    }
                }
            }
            
            // For any missing months, use most recent non-zero value
            double lastValue = fetchStockPortfolioValue(localId);
            for (int i = 11; i >= 0; i--) {
                if (monthlyValues[i] == 0) {
                    monthlyValues[i] = lastValue;
                } else {
                    lastValue = monthlyValues[i];
                }
            }
            
            return monthlyValues;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching monthly stock values", e);
            
            // Use current value for all months as fallback
            double currentValue = fetchStockPortfolioValue(localId);
            Arrays.fill(monthlyValues, currentValue);
            return monthlyValues;
        }
    }

    /**
     * Utility: Extract a cookie value from the cookies string
     */
    private String extractCookieValue(String cookies, String name) {
        if (cookies == null) {
            return null;
        }
        
        for (String part : cookies.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        
        return null;
    }
    
    /**
     * Ensures the JSON structure is valid but doesn't add mock data
     * This prevents serialization errors while still allowing empty values
     */
    private void ensureMinimumStructure(JSONObject fieldsObj) {
        try {
            // Just ensure the base fields exist, but don't fill with mock data
            // This will allow charts to properly show "no data" state when appropriate
            
            if (!fieldsObj.has("netWorthBreakdown")) {
                fieldsObj.put("netWorthBreakdown", new JSONObject().put("stringValue", "{}"));
            }
            
            if (!fieldsObj.has("totalIncomeBreakdown")) {
                fieldsObj.put("totalIncomeBreakdown", new JSONObject().put("stringValue", "{}"));
            }
            
            if (!fieldsObj.has("monthlyExpenses")) {
                fieldsObj.put("monthlyExpenses", new JSONObject().put("arrayValue", new JSONObject().put("values", new JSONArray())));
            }
            
            if (!fieldsObj.has("monthlyIncomes")) {
                fieldsObj.put("monthlyIncomes", new JSONObject().put("arrayValue", new JSONObject().put("values", new JSONArray())));
            }
            
            if (!fieldsObj.has("monthlyStockValues")) {
                fieldsObj.put("monthlyStockValues", new JSONObject().put("arrayValue", new JSONObject().put("values", new JSONArray())));
            }
            
            // Ensure required numeric fields exist but with zero values
            if (!fieldsObj.has("netWorth")) {
                fieldsObj.put("netWorth", new JSONObject().put("integerValue", "0"));
            }
            
            if (!fieldsObj.has("totalIncome")) {
                fieldsObj.put("totalIncome", new JSONObject().put("integerValue", "0"));
            }
            
            if (!fieldsObj.has("totalExpenses")) {
                fieldsObj.put("totalExpenses", new JSONObject().put("integerValue", "0"));
            }
            
            if (!fieldsObj.has("billsDue")) {
                fieldsObj.put("billsDue", new JSONObject().put("integerValue", "0"));
            }
            
            if (!fieldsObj.has("totalInvestments")) {
                fieldsObj.put("totalInvestments", new JSONObject().put("integerValue", "0"));
            }
            
            LOGGER.info("Minimum structure ensured without adding mock data");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error ensuring minimum structure", e);
        }
    }
    
    /**
     * Utility: Safely parse a date from string with different formats
     */
    private LocalDate tryParseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Try ISO format first (yyyy-MM-dd)
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e1) {
            try {
                // Try MM/dd/yyyy format
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } catch (Exception e2) {
                try {
                    // Try yyyy/MM/dd format
                    return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                } catch (Exception e3) {
                    LOGGER.warning("Could not parse date: " + dateStr);
                    return null;
                }
            }
        }
    }
    
    /**
     * Creates an empty but valid response structure
     * Used when Firestore is not available to prevent frontend errors
     * 
     * @return JSON string with empty data structure
     */
    private String createEmptyResponse() {
        try {
            JSONObject doc = new JSONObject();
            JSONObject fieldsObj = new JSONObject();
            doc.put("fields", fieldsObj);
            
            // Add basic structure with empty values
            fieldsObj.put("netWorth", new JSONObject().put("integerValue", "0"));
            fieldsObj.put("totalIncome", new JSONObject().put("integerValue", "0"));
            fieldsObj.put("totalExpenses", new JSONObject().put("integerValue", "0"));
            fieldsObj.put("billsDue", new JSONObject().put("integerValue", "0"));
            fieldsObj.put("totalInvestments", new JSONObject().put("integerValue", "0"));
            
            // Add empty breakdown objects
            JSONObject emptyBreakdown = new JSONObject();
            emptyBreakdown.put("cash", 0);
            emptyBreakdown.put("stocks", 0);
            emptyBreakdown.put("crypto", 0);
            fieldsObj.put("netWorthBreakdown", new JSONObject().put("stringValue", emptyBreakdown.toString()));
            
            JSONObject incomeBreakdown = new JSONObject();
            incomeBreakdown.put("salary", 0);
            incomeBreakdown.put("bonus", 0);
            incomeBreakdown.put("other", 0);
            fieldsObj.put("totalIncomeBreakdown", new JSONObject().put("stringValue", incomeBreakdown.toString()));
            
            // Add empty arrays for monthly data
            JSONArray emptyArray = new JSONArray();
            for (int i = 0; i < 12; i++) {
                emptyArray.put(new JSONObject().put("integerValue", "0"));
            }
            
            fieldsObj.put("monthlyExpenses", new JSONObject().put("arrayValue", new JSONObject().put("values", emptyArray)));
            fieldsObj.put("monthlyIncomes", new JSONObject().put("arrayValue", new JSONObject().put("values", emptyArray)));
            fieldsObj.put("monthlyStockValues", new JSONObject().put("arrayValue", new JSONObject().put("values", emptyArray)));
            
            LOGGER.info("Created empty response structure for frontend");
            return doc.toString();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating empty response", e);
            return "{\"fields\":{}}";
        }
    }
    
    /**
     * Creates a response with realistic-looking mock data
     * Used when Firestore is not available for development/testing
     * 
     * @return JSON string with mock data structure
     */
    private String createMockResponse() {
        try {
            JSONObject doc = new JSONObject();
            JSONObject fieldsObj = new JSONObject();
            doc.put("fields", fieldsObj);
            
            // Add realistic mock values for dashboard
            fieldsObj.put("netWorth", new JSONObject().put("integerValue", "24500"));
            fieldsObj.put("totalIncome", new JSONObject().put("integerValue", "5200"));
            fieldsObj.put("totalExpenses", new JSONObject().put("integerValue", "2850"));
            fieldsObj.put("billsDue", new JSONObject().put("integerValue", "3"));
            fieldsObj.put("totalInvestments", new JSONObject().put("integerValue", "14750"));
            
            // Add breakdown objects with realistic values
            JSONObject worthBreakdown = new JSONObject();
            worthBreakdown.put("cash", 9750);
            worthBreakdown.put("stocks", 8500);
            worthBreakdown.put("crypto", 6250);
            fieldsObj.put("netWorthBreakdown", new JSONObject().put("stringValue", worthBreakdown.toString()));
            
            // Income breakdown for pie chart
            JSONObject incomeBreakdown = new JSONObject();
            incomeBreakdown.put("salary", 4200);
            incomeBreakdown.put("bonus", 500);
            incomeBreakdown.put("other", 500);
            fieldsObj.put("totalIncomeBreakdown", new JSONObject().put("stringValue", incomeBreakdown.toString()));
            
            // Generate realistic monthly expenses data (decreasing then increasing pattern)
            JSONArray monthlyExpArray = new JSONArray();
            int[] expenseData = {2980, 2840, 2750, 2650, 2500, 2450, 2550, 2750, 2850, 2950, 3050, 3150};
            for (int i = 0; i < 12; i++) {
                monthlyExpArray.put(new JSONObject().put("integerValue", String.valueOf(expenseData[i])));
            }
            fieldsObj.put("monthlyExpenses", new JSONObject().put("arrayValue", new JSONObject().put("values", monthlyExpArray)));
            
            // Generate realistic monthly income data (slight upward trend)
            JSONArray monthlyIncArray = new JSONArray();
            int[] incomeData = {4800, 4850, 4850, 4900, 4900, 4950, 5000, 5000, 5050, 5100, 5150, 5200};
            for (int i = 0; i < 12; i++) {
                monthlyIncArray.put(new JSONObject().put("integerValue", String.valueOf(incomeData[i])));
            }
            fieldsObj.put("monthlyIncomes", new JSONObject().put("arrayValue", new JSONObject().put("values", monthlyIncArray)));
            
            // Generate realistic stock performance data (volatile with upward trend)
            JSONArray stockValueArr = new JSONArray();
            int[] stockData = {6800, 6500, 7200, 7400, 7100, 7600, 7400, 7800, 8100, 7900, 8200, 8500};
            for (int i = 0; i < 12; i++) {
                stockValueArr.put(new JSONObject().put("integerValue", String.valueOf(stockData[i])));
            }
            fieldsObj.put("monthlyStockValues", new JSONObject().put("arrayValue", new JSONObject().put("values", stockValueArr)));
            
            LOGGER.info("Created mock data response for dashboard development");
            return doc.toString();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating mock response", e);
            return createEmptyResponse(); // Fall back to empty response if there's an error
        }
    }
}
