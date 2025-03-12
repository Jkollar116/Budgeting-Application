package org.example;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for initializing the budgeting database with sample data
 */
public class BudgetingDatabaseInitializer {
    
    private static boolean isInitialized = false;
    
    /**
     * Initialize empty user data structure without sample data
     * This creates the minimum required data structure for a new user account
     * while ensuring all necessary collections are initialized
     * 
     * @param userId Firebase user ID
     */
    public static void initializeEmptyUserData(String userId) {
        if (!FirestoreService.getInstance().isAvailable()) {
            System.err.println("Firebase is not available, cannot initialize user data");
            return;
        }
        
        // Initialize user preferences with default settings
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Set up minimal user preferences/settings
        Map<String, Object> settings = new HashMap<>();
        
        // Display preferences
        Map<String, Object> display = new HashMap<>();
        display.put("theme", "auto");  // light, dark, auto
        display.put("dateFormat", "MM/DD/YYYY");
        display.put("currencyFormat", "en-US");
        display.put("defaultCurrency", "USD");
        display.put("startDayOfWeek", 0);  // 0 = Sunday
        
        // Notification preferences
        Map<String, Object> notifications = new HashMap<>();
        notifications.put("enableEmailNotifications", true);
        notifications.put("enablePushNotifications", true);
        notifications.put("billReminders", true);
        
        // Privacy settings
        Map<String, Object> privacy = new HashMap<>();
        privacy.put("requireAuthentication", true);
        
        // Feature preferences
        Map<String, Object> features = new HashMap<>();
        features.put("enableCrypto", true);
        features.put("enableStocks", true);
        
        settings.put("display", display);
        settings.put("notifications", notifications);
        settings.put("privacy", privacy);
        settings.put("features", features);
        
        // Save the user settings
        dbService.saveUserSettings(userId, settings);
        
        // Create at least one default account (checking)
        Map<String, Object> checkingAccount = new HashMap<>();
        checkingAccount.put("name", "Primary Checking");
        checkingAccount.put("type", "checking");
        checkingAccount.put("balance", 0.0);
        checkingAccount.put("currency", "USD");
        checkingAccount.put("includeInTotals", true);
        checkingAccount.put("icon", "bank");
        checkingAccount.put("color", "#2196F3");
        checkingAccount.put("isLinked", false);
        dbService.saveAccount(userId, "checking-primary", checkingAccount);
        
        // Initialize an empty monthly budget structure
        initializeEmptyBudget(userId);
        
        // Initialize an empty financial goal
        initializeEmptyGoal(userId);
        
        // Add at least one empty transaction to initialize the transactions collection
        initializeEmptyTransaction(userId);
        
        System.out.println("Empty user data structure initialized for user: " + userId);
    }
    
    /**
     * Initialize an empty budget structure
     * 
     * @param userId Firebase user ID
     */
    private static void initializeEmptyBudget(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Get current month and year
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;  // Calendar months are 0-based
        
        // Create an empty budget
        String budgetId = year + "-" + month;
        Map<String, Object> budget = new HashMap<>();
        budget.put("name", "Monthly Budget: " + month + "/" + year);
        budget.put("period", "monthly");
        budget.put("year", year);
        budget.put("month", month);
        budget.put("totalIncome", 0.0);
        budget.put("totalExpenses", 0.0);
        budget.put("status", "active");
        
        // Create empty budget allocations (this ensures the structure exists)
        Map<String, Object> allocations = new HashMap<>();
        budget.put("allocations", allocations);
        
        // Save the budget
        dbService.saveBudget(userId, budgetId, budget);
    }
    
    /**
     * Initialize an empty financial goal
     * 
     * @param userId Firebase user ID
     */
    private static void initializeEmptyGoal(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Create an example savings goal
        Map<String, Object> savingsGoal = new HashMap<>();
        savingsGoal.put("name", "New Savings Goal");
        savingsGoal.put("description", "Start setting savings goals");
        savingsGoal.put("targetAmount", 0.0);
        savingsGoal.put("currentAmount", 0.0);
        savingsGoal.put("currency", "USD");
        savingsGoal.put("targetDate", getDateInFuture(365));  // 1 year from now
        savingsGoal.put("priority", "medium");
        savingsGoal.put("status", "not_started");
        savingsGoal.put("icon", "piggy-bank");
        savingsGoal.put("color", "#4CAF50");
        
        // Save the goal
        dbService.saveFinancialGoal(userId, "goal-savings", savingsGoal);
    }
    
    /**
     * Add an empty transaction to initialize the transaction collection
     * 
     * @param userId Firebase user ID
     */
    private static void initializeEmptyTransaction(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Create a placeholder transaction (will be hidden in UI)
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("description", "Account Created");
        transaction.put("amount", 0.0);
        transaction.put("type", "system");
        transaction.put("category", "system");
        transaction.put("isHidden", true);
        transaction.put("notes", "System generated transaction to initialize account structure");
        
        // Save the transaction
        dbService.saveTransaction(userId, transaction);
    }
    
    /**
     * Helper method to get a date in the future
     * 
     * @param daysInFuture Number of days in the future
     * @return String representation of the date (ISO format)
     */
    private static String getDateInFuture(int daysInFuture) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysInFuture);
        return cal.getTime().toInstant().toString();
    }
    
    /**
     * Initialize the database with default categories and other global data
     * This should be called when the application starts
     */
    public static void initializeDatabase() {
        if (isInitialized) {
            return;
        }
        
        // Make sure Firebase is initialized
        if (!FirestoreService.getInstance().isAvailable()) {
            System.err.println("Firebase is not available, cannot initialize database");
            return;
        }
        
        // Initialize global data (categories, etc.)
        BudgetingSampleData.initializeGlobalData();
        
        isInitialized = true;
        System.out.println("Budgeting database initialized with global data");
    }
    
    /**
     * Initialize user data for a new user with sample data
     * This should be called when a new user registers
     * 
     * @param userId Firebase user ID
     */
    public static void initializeUserData(String userId) {
        if (!FirestoreService.getInstance().isAvailable()) {
            System.err.println("Firebase is not available, cannot initialize user data");
            return;
        }
        
        // Create sample data for the user
        BudgetingSampleData.createSampleUserData(userId);
        
        System.out.println("User data initialized for: " + userId);
    }
    
    /**
     * Check if a user has been initialized with budgeting data
     * 
     * @param userId Firebase user ID
     * @return true if user has budgeting data, false otherwise
     */
    public static boolean isUserInitialized(String userId) {
        if (!FirestoreService.getInstance().isAvailable()) {
            return false;
        }
        
        // Check if the user has any accounts
        return !BudgetingDatabaseService.getInstance().getUserAccounts(userId).isEmpty();
    }
    
    /**
     * Demo method to show how to use the database
     */
    public static void main(String[] args) {
        // Initialize Firebase
        FirestoreService.initialize();
        
        // Initialize the database with global data
        initializeDatabase();
        
        // Create sample data for a test user
        String testUserId = "test-user-" + System.currentTimeMillis();
        initializeUserData(testUserId);
        
        // Demonstrate how to retrieve data
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Get user accounts
        System.out.println("\nUser Accounts:");
        dbService.getUserAccounts(testUserId).forEach(account -> {
            System.out.println(" - " + account.get("name") + ": $" + account.get("balance"));
        });
        
        // Get user budgets
        System.out.println("\nUser Budgets:");
        dbService.getUserBudgets(testUserId).forEach(budget -> {
            System.out.println(" - " + budget.get("name"));
            System.out.println("   Income: $" + budget.get("totalIncome"));
            System.out.println("   Expenses: $" + budget.get("totalExpenses"));
        });
        
        // Get financial goals
        System.out.println("\nFinancial Goals:");
        dbService.getFinancialGoals(testUserId).forEach(goal -> {
            double current = ((Number) goal.get("currentAmount")).doubleValue();
            double target = ((Number) goal.get("targetAmount")).doubleValue();
            double percentage = Math.round(current / target * 100.0);
            
            System.out.println(" - " + goal.get("name"));
            System.out.println("   Progress: $" + current + " / $" + target + " (" + percentage + "%)");
        });
        
        // Get transactions (last 5)
        System.out.println("\nRecent Transactions:");
        dbService.getUserTransactions(testUserId, null, 5).forEach(transaction -> {
            String type = (String) transaction.get("type");
            String prefix = type.equals("income") ? "+" : type.equals("expense") ? "-" : "";
            
            System.out.println(" - " + transaction.get("description"));
            System.out.println("   " + prefix + "$" + transaction.get("amount") + " (" + transaction.get("category") + ")");
        });
        
        System.out.println("\nBudgeting database demo completed successfully!");
    }
}
