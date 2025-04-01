package org.example;

import com.google.cloud.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test class for the enhanced Firebase/Firestore service
 * Demonstrates the improved capabilities and performance optimizations
 */
public class FirebaseEnhancedTest {
    private static final Logger LOGGER = Logger.getLogger(FirebaseEnhancedTest.class.getName());
    private static final String TEST_USER_ID = "test-user-" + System.currentTimeMillis();
    
    public static void main(String[] args) {
        LOGGER.info("Starting enhanced Firebase service test");
        
        try {
            // Get the enhanced Firestore service instance
            FirestoreServiceEnhanced service = FirestoreServiceEnhanced.getInstance();
            
            if (!service.isAvailable()) {
                LOGGER.severe("Firestore is not available, cannot proceed with tests");
                return;
            }
            
            LOGGER.info("Firestore service initialized successfully");
            
            // Run various tests
            testUserProfile(service);
            testWalletOperations(service);
            testBatchOperations(service);
            testTransactionOperations(service);
            testCachePerformance(service);
            testRealtimeListeners(service);
            
            // Clean up resources
            service.cleanup();
            
            LOGGER.info("All tests completed successfully");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Test failed with exception", e);
        }
    }
    
    private static void testUserProfile(FirestoreServiceEnhanced service) {
        LOGGER.info("Testing user profile operations");
        
        // Create a test user profile
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("name", "Test User");
        userProfile.put("email", "testuser@example.com");
        userProfile.put("created", System.currentTimeMillis());
        userProfile.put("preferences", Map.of(
            "theme", "dark",
            "notifications", true,
            "currency", "USD"
        ));
        
        // Save the user profile
        boolean saved = service.saveUserProfile(TEST_USER_ID, userProfile);
        LOGGER.info("User profile saved: " + saved);
        
        // Read the user profile back (should be from cache)
        Map<String, Object> retrievedProfile = service.getUserProfile(TEST_USER_ID);
        LOGGER.info("User profile retrieved: " + (retrievedProfile != null && !retrievedProfile.isEmpty()));
        LOGGER.info("Profile name: " + retrievedProfile.get("name"));
        
        // Update the profile
        userProfile.put("lastLogin", System.currentTimeMillis());
        saved = service.saveUserProfile(TEST_USER_ID, userProfile);
        LOGGER.info("User profile updated: " + saved);
    }
    
    private static void testWalletOperations(FirestoreServiceEnhanced service) {
        LOGGER.info("Testing wallet operations");
        
        // Create a test wallet
        Map<String, Object> walletData = new HashMap<>();
        String walletId = "wallet-" + System.currentTimeMillis();
        walletData.put("label", "Test Wallet");
        walletData.put("address", "0x" + walletId);
        walletData.put("cryptoType", "ETH");
        walletData.put("balance", 1.5);
        walletData.put("value", 2500.0);
        walletData.put("lastUpdated", System.currentTimeMillis());
        
        // Save the wallet
        boolean saved = service.saveWallet(TEST_USER_ID, walletId, walletData);
        LOGGER.info("Wallet saved: " + saved);
        
        // Get user wallets (should include our new wallet)
        List<Map<String, Object>> wallets = service.getUserWallets(TEST_USER_ID);
        LOGGER.info("Retrieved " + wallets.size() + " wallets");
        
        if (!wallets.isEmpty()) {
            Map<String, Object> retrievedWallet = wallets.get(0);
            LOGGER.info("Wallet label: " + retrievedWallet.get("label"));
            LOGGER.info("Wallet address: " + retrievedWallet.get("address"));
        }
        
        // Delete the wallet
        boolean deleted = service.deleteWallet(TEST_USER_ID, walletId);
        LOGGER.info("Wallet deleted: " + deleted);
    }
    
    private static void testBatchOperations(FirestoreServiceEnhanced service) {
        LOGGER.info("Testing batch operations");
        
        // Create multiple wallets for batch operation
        Map<String, Map<String, Object>> wallets = new HashMap<>();
        
        for (int i = 1; i <= 5; i++) {
            String walletId = "batch-wallet-" + i;
            Map<String, Object> walletData = new HashMap<>();
            walletData.put("label", "Batch Wallet " + i);
            walletData.put("address", "0x" + walletId);
            walletData.put("cryptoType", i % 2 == 0 ? "BTC" : "ETH");
            walletData.put("balance", i * 0.5);
            walletData.put("value", i * 1000.0);
            walletData.put("lastUpdated", System.currentTimeMillis());
            
            wallets.put(walletId, walletData);
        }
        
        // Save all wallets in one batch operation
        boolean saved = service.saveWalletsBatch(TEST_USER_ID, wallets);
        LOGGER.info("Batch wallet save: " + saved);
        
        // Get user wallets (should include our new wallets)
        List<Map<String, Object>> retrievedWallets = service.getUserWallets(TEST_USER_ID);
        LOGGER.info("Retrieved " + retrievedWallets.size() + " wallets after batch operation");
        
        // Create multiple transactions for batch operation
        List<Map<String, Object>> transactions = new ArrayList<>();
        
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> transaction = new HashMap<>();
            transaction.put("type", "TRANSFER");
            transaction.put("amount", i * 100.0);
            transaction.put("description", "Batch Transaction " + i);
            transaction.put("date", System.currentTimeMillis());
            
            transactions.add(transaction);
        }
        
        // Save all transactions in one batch operation
        List<String> transactionIds = service.saveTransactionsBatch(TEST_USER_ID, transactions);
        LOGGER.info("Batch transaction save: " + transactionIds.size() + " transactions saved");
    }
    
    private static void testTransactionOperations(FirestoreServiceEnhanced service) {
        LOGGER.info("Testing transaction operations");
        
        // Create a single transaction
        Map<String, Object> transactionData = new HashMap<>();
        transactionData.put("type", "PURCHASE");
        transactionData.put("amount", 500.0);
        transactionData.put("description", "Test Transaction");
        transactionData.put("date", System.currentTimeMillis());
        
        // Save the transaction
        String transactionId = service.saveTransaction(TEST_USER_ID, transactionData);
        LOGGER.info("Transaction saved with ID: " + transactionId);
        
        // Get transactions (with limit)
        List<Map<String, Object>> transactions = service.getUserTransactions(TEST_USER_ID, 10);
        LOGGER.info("Retrieved " + transactions.size() + " transactions");
        
        if (!transactions.isEmpty()) {
            Map<String, Object> transaction = transactions.get(0);
            LOGGER.info("Transaction type: " + transaction.get("type"));
            LOGGER.info("Transaction amount: " + transaction.get("amount"));
        }
    }
    
    private static void testCachePerformance(FirestoreServiceEnhanced service) {
        LOGGER.info("Testing cache performance");
        
        // First call will hit Firestore
        long startTime = System.currentTimeMillis();
        Map<String, Object> profile = service.getUserProfile(TEST_USER_ID);
        long firstCallTime = System.currentTimeMillis() - startTime;
        LOGGER.info("First call time (Firestore): " + firstCallTime + "ms");
        
        // Second call should hit cache
        startTime = System.currentTimeMillis();
        profile = service.getUserProfile(TEST_USER_ID);
        long secondCallTime = System.currentTimeMillis() - startTime;
        LOGGER.info("Second call time (cached): " + secondCallTime + "ms");
        LOGGER.info("Performance improvement: " + 
                    (firstCallTime > 0 ? (100 - (secondCallTime * 100 / firstCallTime)) : "N/A") + "%");
    }
    
    private static void testRealtimeListeners(FirestoreServiceEnhanced service) {
        LOGGER.info("Testing real-time listeners");
        
        try {
            // Create a CountDownLatch to wait for the real-time update
            CountDownLatch updateLatch = new CountDownLatch(1);
            
            // Add a real-time listener for the user profile
            String listenerKey = service.addRealtimeListener(
                "users", 
                TEST_USER_ID,
                snapshot -> {
                    LOGGER.info("Real-time update received!");
                    Map<String, Object> data = snapshot.getData();
                    if (data != null) {
                        LOGGER.info("Updated profile name: " + data.get("name"));
                        LOGGER.info("Updated timestamp: " + data.get("updatedTimestamp"));
                    }
                    updateLatch.countDown();
                }
            );
            
            // Update the user profile to trigger the listener
            Map<String, Object> userProfile = service.getUserProfile(TEST_USER_ID);
            userProfile.put("name", "Updated User");
            userProfile.put("updatedTimestamp", System.currentTimeMillis());
            service.saveUserProfile(TEST_USER_ID, userProfile);
            
            // Wait for the update to be received
            boolean notified = updateLatch.await(5, TimeUnit.SECONDS);
            LOGGER.info("Received real-time update: " + notified);
            
            // Remove the listener
            service.removeRealtimeListener(listenerKey);
            LOGGER.info("Real-time listener removed");
            
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Interrupted while waiting for real-time update", e);
            Thread.currentThread().interrupt();
        }
    }
}
