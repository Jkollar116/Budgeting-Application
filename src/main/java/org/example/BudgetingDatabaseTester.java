package org.example;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class to test database operations for the budgeting system
 * This can be used to verify if transactions and wallets are being saved correctly
 */
public class BudgetingDatabaseTester {

    public static void main(String[] args) {
        // Initialize Firebase - this is required before any database operations
        try {
            FirestoreService.initialize();
            System.out.println("Firebase initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Initialize the database service
        BudgetingDatabaseService dbService = BudgetingDatabaseService.getInstance();
        
        // Check if the service is available
        if (!dbService.isAvailable()) {
            System.err.println("Database service is not available. Check your Firebase configuration.");
            return;
        }
        
        // Get a test user ID
        String testUserId = "test-user-" + System.currentTimeMillis();
        System.out.println("Using test user ID: " + testUserId);
        
        // For testing purposes, we can still use sample data
        // In production, actual users will get empty structures
        if (Boolean.getBoolean("use.sample.data")) {
            // Initialize sample data for the test user (only for testing)
            BudgetingDatabaseInitializer.initializeUserData(testUserId);
            System.out.println("Sample data initialized for test user");
        } else {
            // Initialize empty user data structure (like real users will get)
            BudgetingDatabaseInitializer.initializeEmptyUserData(testUserId);
            System.out.println("Empty user data structure initialized for test user");
        }
        
        // Test adding a transaction
        testSaveTransaction(dbService, testUserId);
        
        // Test fetching transactions
        testFetchTransactions(dbService, testUserId);
        
        // Test wallet operations (if you have wallet functionality)
        testWalletOperations(dbService, testUserId);
        
        System.out.println("\nDatabase test completed!");
        System.out.println("Check your Firebase console to verify the data was saved.");
        System.out.println("The test data was saved under user ID: " + testUserId);
    }
    
    private static void testSaveTransaction(BudgetingDatabaseService dbService, String userId) {
        System.out.println("\n----- Testing Transaction Saving -----");
        
        // Create a test transaction
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("description", "Test Transaction " + new Date());
        transaction.put("amount", 123.45);
        transaction.put("type", "expense");
        transaction.put("category", "expense-food");
        transaction.put("accountId", "checking-primary"); // This should match an account ID created in the sample data
        transaction.put("notes", "This is a test transaction created by BudgetingDatabaseTester");
        transaction.put("isReconciled", true);
        
        // Save the transaction
        String transactionId = dbService.saveTransaction(userId, transaction);
        
        if (transactionId != null && !transactionId.isEmpty()) {
            System.out.println("✅ Transaction was successfully saved with ID: " + transactionId);
        } else {
            System.err.println("❌ Failed to save transaction!");
        }
    }
    
    private static void testFetchTransactions(BudgetingDatabaseService dbService, String userId) {
        System.out.println("\n----- Testing Transaction Fetching -----");
        
        // Fetch all transactions for the user
        List<Map<String, Object>> transactions = dbService.getUserTransactions(userId, null, 0);
        
        System.out.println("Found " + transactions.size() + " transactions for user " + userId);
        
        // Print details of each transaction
        for (int i = 0; i < transactions.size(); i++) {
            Map<String, Object> transaction = transactions.get(i);
            System.out.println("\nTransaction " + (i+1) + ":");
            System.out.println("  ID: " + transaction.get("id"));
            System.out.println("  Description: " + transaction.get("description"));
            System.out.println("  Amount: " + transaction.get("amount"));
            System.out.println("  Type: " + transaction.get("type"));
            System.out.println("  Category: " + transaction.get("category"));
            System.out.println("  Account: " + transaction.get("accountId"));
            System.out.println("  Timestamp: " + transaction.get("timestamp"));
        }
    }
    
    private static void testWalletOperations(BudgetingDatabaseService dbService, String userId) {
        System.out.println("\n----- Testing Wallet Operations -----");
        
        // Get wallet information
        List<Map<String, Object>> accounts = dbService.getUserAccounts(userId);
        
        // Find crypto wallets specifically (assuming they have a type indicator)
        boolean foundCryptoWallets = false;
        for (Map<String, Object> account : accounts) {
            if (account.get("type") != null && account.get("type").toString().contains("crypto")) {
                foundCryptoWallets = true;
                System.out.println("Found crypto wallet:");
                System.out.println("  Name: " + account.get("name"));
                System.out.println("  Balance: " + account.get("balance"));
                System.out.println("  Type: " + account.get("type"));
            }
        }
        
        if (!foundCryptoWallets) {
            System.out.println("No crypto wallets found. Testing wallet creation...");
            
            // Create a test crypto wallet
            Map<String, Object> wallet = new HashMap<>();
            wallet.put("name", "Test Bitcoin Wallet");
            wallet.put("type", "crypto-wallet");
            wallet.put("balance", 0.25);
            wallet.put("currency", "BTC");
            wallet.put("institution", "Test Exchange");
            wallet.put("includeInTotals", true);
            wallet.put("icon", "bitcoin");
            wallet.put("color", "#F7931A");
            wallet.put("address", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"); // Example Bitcoin address
            
            // Create a unique ID for the wallet
            String walletId = "crypto-bitcoin-" + UUID.randomUUID().toString().substring(0, 8);
            
            // Save the wallet
            boolean success = dbService.saveAccount(userId, walletId, wallet);
            
            if (success) {
                System.out.println("✅ Crypto wallet was successfully created with ID: " + walletId);
            } else {
                System.err.println("❌ Failed to create crypto wallet!");
            }
        }
    }
}
