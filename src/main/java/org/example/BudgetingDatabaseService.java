package org.example;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.cloud.Timestamp;
import com.google.firebase.cloud.FirestoreClient;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service for managing budgeting data in Firestore
 */
public class BudgetingDatabaseService {
    private static BudgetingDatabaseService instance;
    private static Firestore db;
    
    // Top-level collections
    private static final String USERS_COLLECTION = "users";
    private static final String ACTIVITIES_COLLECTION = "activities";
    private static final String GLOBAL_CATEGORIES_COLLECTION = "global_categories";
    
    // Subcollections under user
    private static final String ACCOUNTS_COLLECTION = "accounts";
    private static final String BUDGETS_COLLECTION = "budgets";
    private static final String CATEGORIES_COLLECTION = "categories";
    private static final String TRANSACTIONS_COLLECTION = "transactions";
    private static final String GOALS_COLLECTION = "goals";
    private static final String WALLETS_COLLECTION = "wallets";
    private static final String PORTFOLIOS_COLLECTION = "portfolios";
    private static final String RECURRING_TRANSACTIONS_COLLECTION = "recurring_transactions";
    private static final String REPORTS_COLLECTION = "reports";
    private static final String SETTINGS_COLLECTION = "settings";
    private static final String NOTIFICATIONS_COLLECTION = "notifications";
    
    /**
     * Private constructor to enforce singleton pattern
     */
    private BudgetingDatabaseService() {
        // Initialization happens through the FirestoreService
        db = FirestoreClient.getFirestore();
    }
    
    /**
     * Get the singleton instance
     * 
     * @return BudgetingDatabaseService instance
     */
    public static synchronized BudgetingDatabaseService getInstance() {
        if (instance == null) {
            instance = new BudgetingDatabaseService();
        }
        
        return instance;
    }
    
    /**
     * Check if database service is available
     * 
     * @return true if available, false otherwise
     */
    public boolean isAvailable() {
        return db != null && FirestoreService.getInstance().isAvailable();
    }
    
    /******************************************
     * ACCOUNT METHODS
     ******************************************/
    
    /**
     * Get all financial accounts for a user
     * 
     * @param userId User ID
     * @return List of account maps
     */
    public List<Map<String, Object>> getUserAccounts(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(ACCOUNTS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> accounts = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                accounts.add(document.getData());
            }
            
            return accounts;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user accounts: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Save or update a financial account
     * 
     * @param userId User ID
     * @param accountId Account ID
     * @param accountData Account data
     * @return true if successful, false otherwise
     */
    public boolean saveAccount(String userId, String accountId, Map<String, Object> accountData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Add timestamp if not already present
            if (!accountData.containsKey("lastUpdated")) {
                accountData.put("lastUpdated", FieldValue.serverTimestamp());
            }
            
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(ACCOUNTS_COLLECTION)
                    .document(accountId)
                    .set(accountData)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving account: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete a financial account
     * 
     * @param userId User ID
     * @param accountId Account ID
     * @return true if successful, false otherwise
     */
    public boolean deleteAccount(String userId, String accountId) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(ACCOUNTS_COLLECTION)
                    .document(accountId)
                    .delete()
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error deleting account: " + e.getMessage());
            return false;
        }
    }
    
    /******************************************
     * BUDGETS METHODS
     ******************************************/
    
    /**
     * Get all budgets for a user
     * 
     * @param userId User ID
     * @return List of budget maps
     */
    public List<Map<String, Object>> getUserBudgets(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(BUDGETS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> budgets = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                budgets.add(document.getData());
            }
            
            return budgets;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user budgets: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Save or update a budget
     * 
     * @param userId User ID
     * @param budgetId Budget ID
     * @param budgetData Budget data
     * @return true if successful, false otherwise
     */
    public boolean saveBudget(String userId, String budgetId, Map<String, Object> budgetData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Add timestamp if not already present
            if (!budgetData.containsKey("lastUpdated")) {
                budgetData.put("lastUpdated", FieldValue.serverTimestamp());
            }
            
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(BUDGETS_COLLECTION)
                    .document(budgetId)
                    .set(budgetData)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving budget: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get budget category allocations
     * 
     * @param userId User ID
     * @param budgetId Budget ID
     * @return Map of category allocations
     */
    public Map<String, Object> getBudgetAllocations(String userId, String budgetId) {
        if (!isAvailable()) {
            return new HashMap<>();
        }
        
        try {
            DocumentSnapshot document = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(BUDGETS_COLLECTION)
                    .document(budgetId)
                    .get()
                    .get();
            
            if (document.exists() && document.contains("allocations")) {
                return (Map<String, Object>) document.get("allocations");
            } else {
                return new HashMap<>();
            }
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting budget allocations: " + e.getMessage());
            return new HashMap<>();
        }
    }
    
    /******************************************
     * CATEGORIES METHODS
     ******************************************/
    
    /**
     * Get all spending/income categories for a user
     * 
     * @param userId User ID
     * @return List of category maps
     */
    public List<Map<String, Object>> getUserCategories(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CATEGORIES_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> categories = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                categories.add(document.getData());
            }
            
            return categories;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user categories: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Save or update a category
     * 
     * @param userId User ID
     * @param categoryId Category ID
     * @param categoryData Category data
     * @return true if successful, false otherwise
     */
    public boolean saveCategory(String userId, String categoryId, Map<String, Object> categoryData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(CATEGORIES_COLLECTION)
                    .document(categoryId)
                    .set(categoryData)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving category: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get global/default categories
     * 
     * @return List of default category maps
     */
    public List<Map<String, Object>> getGlobalCategories() {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(GLOBAL_CATEGORIES_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> categories = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                categories.add(document.getData());
            }
            
            return categories;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting global categories: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /******************************************
     * TRANSACTION METHODS
     ******************************************/
    
    /**
     * Get transactions for a user with filtering options
     * 
     * @param userId User ID
     * @param filters Map of filter criteria (optional)
     * @param limit Maximum number of transactions to return (optional)
     * @return List of transaction maps
     */
    public List<Map<String, Object>> getUserTransactions(String userId, Map<String, Object> filters, int limit) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            // Start with base query
            Query query = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(TRANSACTIONS_COLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING);
            
            // Apply filters if provided
            if (filters != null && !filters.isEmpty()) {
                // Date range filter
                if (filters.containsKey("startDate") && filters.containsKey("endDate")) {
                    Timestamp startDate = (Timestamp) filters.get("startDate");
                    Timestamp endDate = (Timestamp) filters.get("endDate");
                    query = query.whereGreaterThanOrEqualTo("timestamp", startDate)
                                .whereLessThanOrEqualTo("timestamp", endDate);
                }
                
                // Category filter
                if (filters.containsKey("category")) {
                    query = query.whereEqualTo("category", filters.get("category"));
                }
                
                // Account filter
                if (filters.containsKey("accountId")) {
                    query = query.whereEqualTo("accountId", filters.get("accountId"));
                }
                
                // Type filter (income/expense)
                if (filters.containsKey("type")) {
                    query = query.whereEqualTo("type", filters.get("type"));
                }
            }
            
            // Apply limit if provided
            if (limit > 0) {
                query = query.limit(limit);
            }
            
            QuerySnapshot querySnapshot = query.get().get();
            
            List<Map<String, Object>> transactions = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                transactions.add(document.getData());
            }
            
            return transactions;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user transactions: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Save a transaction
     * 
     * @param userId User ID
     * @param transactionData Transaction data
     * @return transaction ID if successful, empty string otherwise
     */
    public String saveTransaction(String userId, Map<String, Object> transactionData) {
        if (!isAvailable()) {
            return "";
        }
        
        try {
            // Generate a unique ID for the transaction
            DocumentReference docRef = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(TRANSACTIONS_COLLECTION)
                    .document();
            
            // Add the transaction ID to the data
            transactionData.put("id", docRef.getId());
            
            // Add timestamp if not already present
            if (!transactionData.containsKey("timestamp")) {
                transactionData.put("timestamp", FieldValue.serverTimestamp());
            }
            
            // Save the transaction
            docRef.set(transactionData).get();
            
            // Update account balance if this is linked to an account
            if (transactionData.containsKey("accountId")) {
                String accountId = (String) transactionData.get("accountId");
                updateAccountBalance(userId, accountId);
            }
            
            return docRef.getId();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving transaction: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Update an account balance based on its transactions
     * 
     * @param userId User ID
     * @param accountId Account ID
     */
    private void updateAccountBalance(String userId, String accountId) {
        try {
            // Get all transactions for this account
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(TRANSACTIONS_COLLECTION)
                    .whereEqualTo("accountId", accountId)
                    .get()
                    .get();
            
            double balance = 0.0;
            
            // Calculate balance from transactions
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                Map<String, Object> transaction = document.getData();
                double amount = 0.0;
                
                if (transaction.get("amount") instanceof Number) {
                    amount = ((Number) transaction.get("amount")).doubleValue();
                }
                
                // Add income, subtract expenses
                if ("income".equals(transaction.get("type"))) {
                    balance += amount;
                } else if ("expense".equals(transaction.get("type"))) {
                    balance -= amount;
                } else if ("transfer".equals(transaction.get("type"))) {
                    // For transfers, check if this account is source or destination
                    if (accountId.equals(transaction.get("sourceAccountId"))) {
                        balance -= amount;
                    } else if (accountId.equals(transaction.get("destinationAccountId"))) {
                        balance += amount;
                    }
                }
            }
            
            // Update the account balance
            Map<String, Object> updates = new HashMap<>();
            updates.put("balance", balance);
            updates.put("lastUpdated", FieldValue.serverTimestamp());
            
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(ACCOUNTS_COLLECTION)
                    .document(accountId)
                    .update(updates)
                    .get();
                    
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error updating account balance: " + e.getMessage());
        }
    }
    
    /******************************************
     * RECURRING TRANSACTIONS METHODS
     ******************************************/
    
    /**
     * Get recurring transactions for a user
     * 
     * @param userId User ID
     * @return List of recurring transaction maps
     */
    public List<Map<String, Object>> getRecurringTransactions(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(RECURRING_TRANSACTIONS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> transactions = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                transactions.add(document.getData());
            }
            
            return transactions;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting recurring transactions: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Save a recurring transaction
     * 
     * @param userId User ID
     * @param recurringId Recurring transaction ID
     * @param transactionData Transaction data
     * @return true if successful, false otherwise
     */
    public boolean saveRecurringTransaction(String userId, String recurringId, Map<String, Object> transactionData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Add timestamp if not already present
            if (!transactionData.containsKey("createdAt")) {
                transactionData.put("createdAt", FieldValue.serverTimestamp());
            }
            
            transactionData.put("lastUpdated", FieldValue.serverTimestamp());
            
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(RECURRING_TRANSACTIONS_COLLECTION)
                    .document(recurringId)
                    .set(transactionData)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving recurring transaction: " + e.getMessage());
            return false;
        }
    }
    
    /******************************************
     * FINANCIAL GOALS METHODS
     ******************************************/
    
    /**
     * Get financial goals for a user
     * 
     * @param userId User ID
     * @return List of goal maps
     */
    public List<Map<String, Object>> getFinancialGoals(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(GOALS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> goals = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                goals.add(document.getData());
            }
            
            return goals;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting financial goals: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Save a financial goal
     * 
     * @param userId User ID
     * @param goalId Goal ID
     * @param goalData Goal data
     * @return true if successful, false otherwise
     */
    public boolean saveFinancialGoal(String userId, String goalId, Map<String, Object> goalData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Add timestamps if not already present
            if (!goalData.containsKey("createdAt")) {
                goalData.put("createdAt", FieldValue.serverTimestamp());
            }
            
            goalData.put("lastUpdated", FieldValue.serverTimestamp());
            
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(GOALS_COLLECTION)
                    .document(goalId)
                    .set(goalData)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving financial goal: " + e.getMessage());
            return false;
        }
    }
    
    /******************************************
     * REPORTS METHODS
     ******************************************/
    
    /**
     * Save a user report
     * 
     * @param userId User ID
     * @param reportId Report ID
     * @param reportData Report data
     * @return true if successful, false otherwise
     */
    public boolean saveReport(String userId, String reportId, Map<String, Object> reportData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Add timestamp if not already present
            if (!reportData.containsKey("createdAt")) {
                reportData.put("createdAt", FieldValue.serverTimestamp());
            }
            
            reportData.put("lastUpdated", FieldValue.serverTimestamp());
            
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(REPORTS_COLLECTION)
                    .document(reportId)
                    .set(reportData)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving report: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get saved reports for a user
     * 
     * @param userId User ID
     * @return List of report maps
     */
    public List<Map<String, Object>> getUserReports(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(REPORTS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> reports = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                reports.add(document.getData());
            }
            
            return reports;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user reports: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /******************************************
     * NOTIFICATIONS METHODS
     ******************************************/
    
    /**
     * Save a notification for a user
     * 
     * @param userId User ID
     * @param notificationData Notification data
     * @return notification ID if successful, empty string otherwise
     */
    public String saveNotification(String userId, Map<String, Object> notificationData) {
        if (!isAvailable()) {
            return "";
        }
        
        try {
            // Generate a unique ID for the notification
            DocumentReference docRef = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(NOTIFICATIONS_COLLECTION)
                    .document();
            
            // Add the notification ID to the data
            notificationData.put("id", docRef.getId());
            
            // Add timestamp if not already present
            if (!notificationData.containsKey("timestamp")) {
                notificationData.put("timestamp", FieldValue.serverTimestamp());
            }
            
            // Default to unread if not specified
            if (!notificationData.containsKey("read")) {
                notificationData.put("read", false);
            }
            
            // Save the notification
            docRef.set(notificationData).get();
            
            return docRef.getId();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving notification: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Get unread notifications for a user
     * 
     * @param userId User ID
     * @return List of notification maps
     */
    public List<Map<String, Object>> getUnreadNotifications(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(NOTIFICATIONS_COLLECTION)
                    .whereEqualTo("read", false)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .get();
            
            List<Map<String, Object>> notifications = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                notifications.add(document.getData());
            }
            
            return notifications;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting unread notifications: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Mark a notification as read
     * 
     * @param userId User ID
     * @param notificationId Notification ID
     * @return true if successful, false otherwise
     */
    public boolean markNotificationAsRead(String userId, String notificationId) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(NOTIFICATIONS_COLLECTION)
                    .document(notificationId)
                    .update("read", true)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error marking notification as read: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Save user settings
     * 
     * @param userId User ID
     * @param settings Settings data
     * @return true if successful, false otherwise
     */
    public boolean saveUserSettings(String userId, Map<String, Object> settings) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(SETTINGS_COLLECTION)
                    .document("preferences")
                    .set(settings)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving user settings: " + e.getMessage());
            return false;
        }
    }
}
