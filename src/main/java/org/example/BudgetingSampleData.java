package org.example;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import java.util.*;

/**
 * Utility class for creating sample budgeting data in Firebase
 * This demonstrates how to use the BudgetingDatabaseService
 */
public class BudgetingSampleData {
    private static Random random = new Random();
    
    /**
     * Initialize the database with sample categories and other shared data
     */
    public static void initializeGlobalData() {
        // Create standard categories that all users can use
        createDefaultCategories();
    }
    
    /**
     * Create sample data for a new user
     * 
     * @param userId The user ID
     */
    public static void createSampleUserData(String userId) {
        if (userId == null || userId.isEmpty()) {
            System.err.println("Cannot create sample data for empty user ID");
            return;
        }
        
        // Create some sample financial accounts
        createSampleAccounts(userId);
        
        // Create categories (custom to this user)
        createSampleCategories(userId);
        
        // Create a monthly budget
        createSampleBudget(userId);
        
        // Create some financial goals
        createSampleGoals(userId);
        
        // Create some transactions
        createSampleTransactions(userId);
        
        // Create recurring transactions
        createSampleRecurringTransactions(userId);
        
        // Create user preferences
        createSampleUserPreferences(userId);
        
        // Create notifications
        createSampleNotifications(userId);
        
        System.out.println("Sample data created for user: " + userId);
    }
    
    /**
     * Create default global categories
     */
    private static void createDefaultCategories() {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Income categories
        createGlobalCategory("income-salary", "Salary", "income", "💰", "#4CAF50");
        createGlobalCategory("income-freelance", "Freelance", "income", "💻", "#8BC34A");
        createGlobalCategory("income-investments", "Investments", "income", "📈", "#CDDC39");
        createGlobalCategory("income-gifts", "Gifts", "income", "🎁", "#FFC107");
        createGlobalCategory("income-other", "Other Income", "income", "💵", "#FF9800");
        
        // Expense categories
        createGlobalCategory("expense-housing", "Housing", "expense", "🏠", "#F44336");
        createGlobalCategory("expense-food", "Food", "expense", "🍔", "#E91E63");
        createGlobalCategory("expense-utilities", "Utilities", "expense", "💡", "#9C27B0");
        createGlobalCategory("expense-transportation", "Transportation", "expense", "🚗", "#673AB7");
        createGlobalCategory("expense-healthcare", "Healthcare", "expense", "🏥", "#3F51B5");
        createGlobalCategory("expense-entertainment", "Entertainment", "expense", "🎬", "#2196F3");
        createGlobalCategory("expense-shopping", "Shopping", "expense", "🛍️", "#03A9F4");
        createGlobalCategory("expense-education", "Education", "expense", "📚", "#00BCD4");
        createGlobalCategory("expense-subscriptions", "Subscriptions", "expense", "📱", "#009688");
        createGlobalCategory("expense-travel", "Travel", "expense", "✈️", "#FF5722");
        createGlobalCategory("expense-debt", "Debt Payments", "expense", "💳", "#795548");
        createGlobalCategory("expense-other", "Other Expenses", "expense", "🧾", "#9E9E9E");
        
        // Crypto categories
        createGlobalCategory("crypto-bitcoin", "Bitcoin", "crypto", "₿", "#F7931A");
        createGlobalCategory("crypto-ethereum", "Ethereum", "crypto", "Ξ", "#627EEA");
        createGlobalCategory("crypto-other", "Other Crypto", "crypto", "🪙", "#8C8C8C");
    }
    
    /**
     * Create a global category
     */
    private static void createGlobalCategory(String id, String name, String type, String icon, String color) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        Map<String, Object> categoryData = new HashMap<>();
        categoryData.put("id", id);
        categoryData.put("name", name);
        categoryData.put("type", type);
        categoryData.put("icon", icon);
        categoryData.put("color", color);
        categoryData.put("isDefault", true);
        categoryData.put("isSystem", true);
        
        try {
            dbService.saveCategory("global", id, categoryData);
        } catch (Exception e) {
            System.err.println("Error creating global category: " + e.getMessage());
        }
    }
    
    /**
     * Create sample accounts for a user
     */
    private static void createSampleAccounts(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Checking account
        Map<String, Object> checkingAccount = new HashMap<>();
        checkingAccount.put("name", "Primary Checking");
        checkingAccount.put("type", "checking");
        checkingAccount.put("balance", 2500.0);
        checkingAccount.put("currency", "USD");
        checkingAccount.put("institution", "Example Bank");
        checkingAccount.put("includeInTotals", true);
        checkingAccount.put("icon", "bank");
        checkingAccount.put("color", "#2196F3");
        checkingAccount.put("isLinked", false);
        dbService.saveAccount(userId, "checking-primary", checkingAccount);
        
        // Savings account
        Map<String, Object> savingsAccount = new HashMap<>();
        savingsAccount.put("name", "Emergency Fund");
        savingsAccount.put("type", "savings");
        savingsAccount.put("balance", 10000.0);
        savingsAccount.put("currency", "USD");
        savingsAccount.put("institution", "Example Bank");
        savingsAccount.put("includeInTotals", true);
        savingsAccount.put("icon", "piggy-bank");
        savingsAccount.put("color", "#4CAF50");
        savingsAccount.put("isLinked", false);
        dbService.saveAccount(userId, "savings-emergency", savingsAccount);
        
        // Credit card
        Map<String, Object> creditCard = new HashMap<>();
        creditCard.put("name", "Primary Credit Card");
        creditCard.put("type", "credit");
        creditCard.put("balance", -750.0);  // Credit cards typically have negative balances (money owed)
        creditCard.put("currency", "USD");
        creditCard.put("institution", "Example Credit Union");
        creditCard.put("includeInTotals", true);
        creditCard.put("icon", "credit-card");
        creditCard.put("color", "#F44336");
        creditCard.put("creditLimit", 5000.0);
        creditCard.put("isLinked", false);
        dbService.saveAccount(userId, "credit-primary", creditCard);
        
        // Investment account
        Map<String, Object> investmentAccount = new HashMap<>();
        investmentAccount.put("name", "Brokerage Account");
        investmentAccount.put("type", "investment");
        investmentAccount.put("balance", 15000.0);
        investmentAccount.put("currency", "USD");
        investmentAccount.put("institution", "Example Investments");
        investmentAccount.put("includeInTotals", true);
        investmentAccount.put("icon", "chart-line");
        investmentAccount.put("color", "#673AB7");
        investmentAccount.put("isLinked", false);
        dbService.saveAccount(userId, "investment-brokerage", investmentAccount);
    }
    
    /**
     * Create sample categories for a user
     */
    private static void createSampleCategories(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Custom income categories
        Map<String, Object> bonusCategory = new HashMap<>();
        bonusCategory.put("name", "Performance Bonus");
        bonusCategory.put("type", "income");
        bonusCategory.put("icon", "🎯");
        bonusCategory.put("color", "#9C27B0");
        bonusCategory.put("isDefault", false);
        bonusCategory.put("parentCategory", "income-salary");
        dbService.saveCategory(userId, "income-bonus", bonusCategory);
        
        // Custom expense categories - more specific subcategories
        Map<String, Object> groceryCategory = new HashMap<>();
        groceryCategory.put("name", "Groceries");
        groceryCategory.put("type", "expense");
        groceryCategory.put("icon", "🛒");
        groceryCategory.put("color", "#8BC34A");
        groceryCategory.put("isDefault", false);
        groceryCategory.put("parentCategory", "expense-food");
        dbService.saveCategory(userId, "expense-groceries", groceryCategory);
        
        Map<String, Object> diningCategory = new HashMap<>();
        diningCategory.put("name", "Dining Out");
        diningCategory.put("type", "expense");
        diningCategory.put("icon", "🍽️");
        diningCategory.put("color", "#FF9800");
        diningCategory.put("isDefault", false);
        diningCategory.put("parentCategory", "expense-food");
        dbService.saveCategory(userId, "expense-dining", diningCategory);
        
        Map<String, Object> coffeeCategory = new HashMap<>();
        coffeeCategory.put("name", "Coffee Shops");
        coffeeCategory.put("type", "expense");
        coffeeCategory.put("icon", "☕");
        coffeeCategory.put("color", "#795548");
        coffeeCategory.put("isDefault", false);
        coffeeCategory.put("parentCategory", "expense-food");
        dbService.saveCategory(userId, "expense-coffee", coffeeCategory);
    }
    
    /**
     * Create a sample monthly budget
     */
    private static void createSampleBudget(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Get current month and year
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;  // Calendar months are 0-based
        
        // Create budget
        String budgetId = year + "-" + month;
        Map<String, Object> budget = new HashMap<>();
        budget.put("name", "Monthly Budget: " + month + "/" + year);
        budget.put("period", "monthly");
        budget.put("year", year);
        budget.put("month", month);
        budget.put("totalIncome", 5000.0);
        budget.put("totalExpenses", 3500.0);
        budget.put("status", "active");
        
        // Create budget allocations by category
        Map<String, Object> allocations = new HashMap<>();
        
        // Income allocations
        allocations.put("income-salary", 4500.0);
        allocations.put("income-freelance", 500.0);
        
        // Expense allocations
        allocations.put("expense-housing", 1200.0);
        allocations.put("expense-groceries", 400.0);
        allocations.put("expense-dining", 300.0);
        allocations.put("expense-coffee", 50.0);
        allocations.put("expense-utilities", 200.0);
        allocations.put("expense-transportation", 250.0);
        allocations.put("expense-healthcare", 100.0);
        allocations.put("expense-entertainment", 150.0);
        allocations.put("expense-shopping", 200.0);
        allocations.put("expense-subscriptions", 50.0);
        allocations.put("expense-debt", 300.0);
        allocations.put("expense-other", 300.0);
        
        budget.put("allocations", allocations);
        
        // Save the budget
        dbService.saveBudget(userId, budgetId, budget);
    }
    
    /**
     * Create sample financial goals
     */
    private static void createSampleGoals(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Emergency fund goal
        Map<String, Object> emergencyFundGoal = new HashMap<>();
        emergencyFundGoal.put("name", "Emergency Fund");
        emergencyFundGoal.put("description", "Save 6 months of living expenses");
        emergencyFundGoal.put("targetAmount", 15000.0);
        emergencyFundGoal.put("currentAmount", 10000.0);
        emergencyFundGoal.put("currency", "USD");
        emergencyFundGoal.put("targetDate", getDateInFuture(180));  // 6 months from now
        emergencyFundGoal.put("accountId", "savings-emergency");
        emergencyFundGoal.put("priority", "high");
        emergencyFundGoal.put("status", "in_progress");
        emergencyFundGoal.put("icon", "shield");
        emergencyFundGoal.put("color", "#4CAF50");
        dbService.saveFinancialGoal(userId, "goal-emergency-fund", emergencyFundGoal);
        
        // Vacation goal
        Map<String, Object> vacationGoal = new HashMap<>();
        vacationGoal.put("name", "Summer Vacation");
        vacationGoal.put("description", "Trip to Hawaii");
        vacationGoal.put("targetAmount", 3000.0);
        vacationGoal.put("currentAmount", 800.0);
        vacationGoal.put("currency", "USD");
        vacationGoal.put("targetDate", getDateInFuture(120));  // 4 months from now
        vacationGoal.put("priority", "medium");
        vacationGoal.put("status", "in_progress");
        vacationGoal.put("icon", "umbrella-beach");
        vacationGoal.put("color", "#FF9800");
        dbService.saveFinancialGoal(userId, "goal-vacation", vacationGoal);
        
        // Down payment goal
        Map<String, Object> homeDownPaymentGoal = new HashMap<>();
        homeDownPaymentGoal.put("name", "Home Down Payment");
        homeDownPaymentGoal.put("description", "20% down payment for a house");
        homeDownPaymentGoal.put("targetAmount", 60000.0);
        homeDownPaymentGoal.put("currentAmount", 15000.0);
        homeDownPaymentGoal.put("currency", "USD");
        homeDownPaymentGoal.put("targetDate", getDateInFuture(730));  // About 2 years
        homeDownPaymentGoal.put("priority", "medium");
        homeDownPaymentGoal.put("status", "in_progress");
        homeDownPaymentGoal.put("icon", "home");
        homeDownPaymentGoal.put("color", "#3F51B5");
        dbService.saveFinancialGoal(userId, "goal-home-down-payment", homeDownPaymentGoal);
    }
    
    /**
     * Create sample transactions
     */
    private static void createSampleTransactions(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Last month's salary
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        cal.set(Calendar.DATE, 15); // Pay day
        
        Map<String, Object> salaryTransaction = new HashMap<>();
        salaryTransaction.put("description", "Monthly Salary");
        salaryTransaction.put("amount", 4500.0);
        salaryTransaction.put("type", "income");
        salaryTransaction.put("category", "income-salary");
        salaryTransaction.put("accountId", "checking-primary");
        salaryTransaction.put("timestamp", Timestamp.of(cal.getTime()));
        salaryTransaction.put("notes", "Regular monthly payment");
        salaryTransaction.put("isReconciled", true);
        dbService.saveTransaction(userId, salaryTransaction);
        
        // This month's salary
        cal = Calendar.getInstance();
        cal.set(Calendar.DATE, 15); // Pay day
        
        salaryTransaction = new HashMap<>();
        salaryTransaction.put("description", "Monthly Salary");
        salaryTransaction.put("amount", 4500.0);
        salaryTransaction.put("type", "income");
        salaryTransaction.put("category", "income-salary");
        salaryTransaction.put("accountId", "checking-primary");
        salaryTransaction.put("timestamp", Timestamp.of(cal.getTime()));
        salaryTransaction.put("notes", "Regular monthly payment");
        salaryTransaction.put("isReconciled", true);
        dbService.saveTransaction(userId, salaryTransaction);
        
        // Freelance income
        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        
        Map<String, Object> freelanceTransaction = new HashMap<>();
        freelanceTransaction.put("description", "Website Design Project");
        freelanceTransaction.put("amount", 500.0);
        freelanceTransaction.put("type", "income");
        freelanceTransaction.put("category", "income-freelance");
        freelanceTransaction.put("accountId", "checking-primary");
        freelanceTransaction.put("timestamp", Timestamp.of(cal.getTime()));
        freelanceTransaction.put("notes", "Logo design for XYZ Company");
        freelanceTransaction.put("isReconciled", true);
        dbService.saveTransaction(userId, freelanceTransaction);
        
        // Rent payment
        cal = Calendar.getInstance();
        cal.set(Calendar.DATE, 1); // First of the month
        
        Map<String, Object> rentTransaction = new HashMap<>();
        rentTransaction.put("description", "Monthly Rent");
        rentTransaction.put("amount", 1200.0);
        rentTransaction.put("type", "expense");
        rentTransaction.put("category", "expense-housing");
        rentTransaction.put("accountId", "checking-primary");
        rentTransaction.put("timestamp", Timestamp.of(cal.getTime()));
        rentTransaction.put("notes", "Monthly rent payment");
        rentTransaction.put("isReconciled", true);
        dbService.saveTransaction(userId, rentTransaction);
        
        // Recent grocery trips
        createRandomTransactions(userId, "Grocery Shopping", "expense-groceries", "expense", 
                                 50.0, 150.0, "checking-primary", 5, 20);
        
        // Recent dining out
        createRandomTransactions(userId, "Restaurant", "expense-dining", "expense", 
                                 15.0, 75.0, "credit-primary", 3, 14);
        
        // Coffee shop visits
        createRandomTransactions(userId, "Coffee Shop", "expense-coffee", "expense", 
                                 4.0, 15.0, "credit-primary", 8, 21);
        
        // Utility bills
        createUtilityBills(userId);
        
        // Transfer to savings
        cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -5);
        
        Map<String, Object> savingsTransfer = new HashMap<>();
        savingsTransfer.put("description", "Transfer to Savings");
        savingsTransfer.put("amount", 500.0);
        savingsTransfer.put("type", "transfer");
        savingsTransfer.put("sourceAccountId", "checking-primary");
        savingsTransfer.put("destinationAccountId", "savings-emergency");
        savingsTransfer.put("timestamp", Timestamp.of(cal.getTime()));
        savingsTransfer.put("notes", "Monthly savings contribution");
        savingsTransfer.put("isReconciled", true);
        dbService.saveTransaction(userId, savingsTransfer);
    }
    
    /**
     * Create random transactions for a category
     */
    private static void createRandomTransactions(String userId, String descriptionPrefix, 
                                                String category, String type, 
                                                double minAmount, double maxAmount, 
                                                String accountId, int count, int maxDaysAgo) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        for (int i = 0; i < count; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -random.nextInt(maxDaysAgo));
            
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("description", descriptionPrefix + " " + (i + 1));
            
            // Random amount between min and max
            double amount = minAmount + (random.nextDouble() * (maxAmount - minAmount));
            amount = Math.round(amount * 100.0) / 100.0; // Round to 2 decimal places
            
            transaction.put("amount", amount);
            transaction.put("type", type);
            transaction.put("category", category);
            transaction.put("accountId", accountId);
            transaction.put("timestamp", Timestamp.of(cal.getTime()));
            transaction.put("isReconciled", true);
            
            dbService.saveTransaction(userId, transaction);
        }
    }
    
    /**
     * Create utility bill transactions
     */
    private static void createUtilityBills(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DATE, 5); // Electricity on the 5th
        
        Map<String, Object> electricBill = new HashMap<>();
        electricBill.put("description", "Electric Bill");
        electricBill.put("amount", 85.0);
        electricBill.put("type", "expense");
        electricBill.put("category", "expense-utilities");
        electricBill.put("accountId", "checking-primary");
        electricBill.put("timestamp", Timestamp.of(cal.getTime()));
        electricBill.put("isReconciled", true);
        dbService.saveTransaction(userId, electricBill);
        
        cal.set(Calendar.DATE, 10); // Water on the 10th
        
        Map<String, Object> waterBill = new HashMap<>();
        waterBill.put("description", "Water Bill");
        waterBill.put("amount", 45.0);
        waterBill.put("type", "expense");
        waterBill.put("category", "expense-utilities");
        waterBill.put("accountId", "checking-primary");
        waterBill.put("timestamp", Timestamp.of(cal.getTime()));
        waterBill.put("isReconciled", true);
        dbService.saveTransaction(userId, waterBill);
        
        cal.set(Calendar.DATE, 15); // Internet on the 15th
        
        Map<String, Object> internetBill = new HashMap<>();
        internetBill.put("description", "Internet Bill");
        internetBill.put("amount", 70.0);
        internetBill.put("type", "expense");
        internetBill.put("category", "expense-utilities");
        internetBill.put("accountId", "credit-primary");
        internetBill.put("timestamp", Timestamp.of(cal.getTime()));
        internetBill.put("isReconciled", true);
        dbService.saveTransaction(userId, internetBill);
    }
    
    /**
     * Create sample recurring transactions
     */
    private static void createSampleRecurringTransactions(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Monthly salary
        Map<String, Object> salarySetting = new HashMap<>();
        salarySetting.put("name", "Monthly Salary");
        salarySetting.put("amount", 4500.0);
        salarySetting.put("type", "income");
        salarySetting.put("category", "income-salary");
        salarySetting.put("accountId", "checking-primary");
        salarySetting.put("frequency", "monthly");
        salarySetting.put("dayOfMonth", 15);
        salarySetting.put("status", "active");
        salarySetting.put("notes", "Regular monthly payment");
        dbService.saveRecurringTransaction(userId, "recurring-salary", salarySetting);
        
        // Monthly rent
        Map<String, Object> rentSetting = new HashMap<>();
        rentSetting.put("name", "Monthly Rent");
        rentSetting.put("amount", 1200.0);
        rentSetting.put("type", "expense");
        rentSetting.put("category", "expense-housing");
        rentSetting.put("accountId", "checking-primary");
        rentSetting.put("frequency", "monthly");
        rentSetting.put("dayOfMonth", 1);
        rentSetting.put("status", "active");
        rentSetting.put("notes", "Apartment rent");
        dbService.saveRecurringTransaction(userId, "recurring-rent", rentSetting);
        
        // Monthly savings
        Map<String, Object> savingsSetting = new HashMap<>();
        savingsSetting.put("name", "Monthly Savings");
        savingsSetting.put("amount", 500.0);
        savingsSetting.put("type", "transfer");
        savingsSetting.put("sourceAccountId", "checking-primary");
        savingsSetting.put("destinationAccountId", "savings-emergency");
        savingsSetting.put("frequency", "monthly");
        savingsSetting.put("dayOfMonth", 15);
        savingsSetting.put("status", "active");
        savingsSetting.put("notes", "Automatic transfer to emergency fund");
        dbService.saveRecurringTransaction(userId, "recurring-savings", savingsSetting);
        
        // Subscriptions
        createSubscription(userId, "Netflix", 15.99, 10);
        createSubscription(userId, "Spotify", 9.99, 15);
        createSubscription(userId, "Gym Membership", 45.0, 5);
    }
    
    /**
     * Create a subscription recurring transaction
     */
    private static void createSubscription(String userId, String name, double amount, int dayOfMonth) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        Map<String, Object> subscription = new HashMap<>();
        subscription.put("name", name);
        subscription.put("amount", amount);
        subscription.put("type", "expense");
        subscription.put("category", "expense-subscriptions");
        subscription.put("accountId", "credit-primary");
        subscription.put("frequency", "monthly");
        subscription.put("dayOfMonth", dayOfMonth);
        subscription.put("status", "active");
        
        String id = "recurring-" + name.toLowerCase().replace(" ", "-");
        dbService.saveRecurringTransaction(userId, id, subscription);
    }
    
    /**
     * Create sample user preferences
     */
    private static void createSampleUserPreferences(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        Map<String, Object> settings = new HashMap<>();
        
        // Display preferences
        Map<String, Object> display = new HashMap<>();
        display.put("theme", "auto");  // light, dark, auto
        display.put("colorMode", "default");
        display.put("dateFormat", "MM/DD/YYYY");
        display.put("currencyFormat", "en-US");
        display.put("defaultCurrency", "USD");
        display.put("startDayOfWeek", 0);  // 0 = Sunday
        
        // Notification preferences
        Map<String, Object> notifications = new HashMap<>();
        notifications.put("enableEmailNotifications", true);
        notifications.put("enablePushNotifications", true);
        notifications.put("billReminders", true);
        notifications.put("budgetAlerts", true);
        notifications.put("goalUpdates", true);
        notifications.put("unusualActivity", true);
        
        // Privacy settings
        Map<String, Object> privacy = new HashMap<>();
        privacy.put("shareUsageData", false);
        privacy.put("enableAnalytics", true);
        privacy.put("requireAuthentication", true);
        
        // Feature preferences
        Map<String, Object> features = new HashMap<>();
        features.put("enableCrypto", true);
        features.put("enableStocks", true);
        features.put("enableAutomation", true);
        
        settings.put("display", display);
        settings.put("notifications", notifications);
        settings.put("privacy", privacy);
        settings.put("features", features);
        
        dbService.saveUserSettings(userId, settings);
    }
    
    /**
     * Create sample notifications
     */
    private static void createSampleNotifications(String userId) {
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Budget alert
        Map<String, Object> budgetAlert = new HashMap<>();
        budgetAlert.put("title", "Budget Alert");
        budgetAlert.put("message", "You've spent 80% of your Dining Out budget this month");
        budgetAlert.put("type", "budget_alert");
        budgetAlert.put("priority", "medium");
        budgetAlert.put("category", "expense-dining");
        budgetAlert.put("read", false);
        dbService.saveNotification(userId, budgetAlert);
        
        // Goal milestone
        Map<String, Object> goalMilestone = new HashMap<>();
        goalMilestone.put("title", "Goal Progress");
        goalMilestone.put("message", "You've reached 65% of your Emergency Fund goal!");
        goalMilestone.put("type", "goal_milestone");
        goalMilestone.put("priority", "low");
        goalMilestone.put("goalId", "goal-emergency-fund");
        goalMilestone.put("read", false);
        dbService.saveNotification(userId, goalMilestone);
        
        // Upcoming bill
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 3);
        
        Map<String, Object> upcomingBill = new HashMap<>();
        upcomingBill.put("title", "Upcoming Bill");
        upcomingBill.put("message", "Your monthly rent payment of $1,200 is due in 3 days");
        upcomingBill.put("type", "bill_reminder");
        upcomingBill.put("priority", "high");
        upcomingBill.put("dueDate", Timestamp.of(cal.getTime()));
        upcomingBill.put("amount", 1200.0);
        upcomingBill.put("recurringId", "recurring-rent");
        upcomingBill.put("read", false);
        dbService.saveNotification(userId, upcomingBill);
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
}
