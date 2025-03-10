package org.example;

import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.List;

/**
 * Utility class for debugging Firebase connections and operations
 */
public class FirebaseDebugger {
    // Test user ID - this is what we use for testing
    private static final String DEFAULT_USER_ID = "test_user";
    private static FirestoreService firestoreService;

    static {
        firestoreService = FirestoreService.getInstance();
    }

    /**
     * Run a debug test that attempts to save data to Firestore and then retrieve it.
     * This helps validate that the Firebase configuration is working correctly.
     */
    public static void runDebugTest() {
        System.out.println("\n\n==== Running Firebase Debug Test ====");
        
        try {
            // First ensure Firestore is available
            if (!firestoreService.isAvailable()) {
                System.out.println("ERROR: Firestore is not available!");
                return;
            }
            
            System.out.println("Firestore service is available");
            
            // Create user profile
            Map<String, Object> userData = new HashMap<>();
            userData.put("email", "test@example.com");
            userData.put("lastLogin", new Date().toString());
            userData.put("debugMessage", "Test data created at " + new Date());

            boolean userSaved = firestoreService.saveUserProfile(DEFAULT_USER_ID, userData);
            System.out.println("User profile saved: " + userSaved);
            
            // Create test wallet
            Map<String, Object> walletData = new HashMap<>();
            String walletId = "test-wallet-" + System.currentTimeMillis();
            walletData.put("id", walletId);
            walletData.put("label", "Debug Test Wallet");
            walletData.put("address", "0xDebugTest" + System.currentTimeMillis());
            walletData.put("cryptoType", "BTC");
            walletData.put("balance", 1.23456789);
            walletData.put("value", 45678.90);
            walletData.put("change24h", 2.34);
            walletData.put("lastUpdated", new Date().toString());
            
            boolean walletSaved = firestoreService.saveWallet(DEFAULT_USER_ID, walletId, walletData);
            System.out.println("Wallet saved: " + walletSaved + " with ID: " + walletId);
            
            // Add a debug transaction
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("type", "DEBUG");
            transactionData.put("amount", 0.12345);
            transactionData.put("timestamp", new Date().toString());
            transactionData.put("hash", "debug-" + System.currentTimeMillis());
            transactionData.put("status", "CONFIRMED");
            
            String transactionId = firestoreService.saveTransaction(DEFAULT_USER_ID, transactionData);
            System.out.println("Transaction saved with ID: " + transactionId);
            
            // Now retrieve and verify
            System.out.println("\nVerifying data...");
            
            // Check user
            Map<String, Object> retrievedUser = firestoreService.getUserProfile(DEFAULT_USER_ID);
            System.out.println("Retrieved user profile: " + (retrievedUser != null && !retrievedUser.isEmpty()));
            if (retrievedUser != null && !retrievedUser.isEmpty()) {
                System.out.println("User email: " + retrievedUser.get("email"));
                System.out.println("Debug message: " + retrievedUser.get("debugMessage"));
            }
            
            // Check wallets
            List<Map<String, Object>> wallets = firestoreService.getUserWallets(DEFAULT_USER_ID);
            System.out.println("Retrieved " + wallets.size() + " wallets");
            for (Map<String, Object> wallet : wallets) {
                System.out.println("Wallet: " + wallet.get("label") + " (" + wallet.get("id") + ")");
            }
            
            // Check transactions
            List<Map<String, Object>> transactions = firestoreService.getUserTransactions(DEFAULT_USER_ID, 10);
            System.out.println("Retrieved " + transactions.size() + " transactions");
            
            System.out.println("\nDebug test completed. Check your Firebase console at:");
            System.out.println("https://console.firebase.google.com/project/cashclimb-d162c/firestore/data/");
            System.out.println("You should see a 'users' collection with a 'test_user' document.\n");
            System.out.println("==== End Firebase Debug Test ====\n\n");
            
        } catch (Exception e) {
            System.err.println("ERROR in Firebase debug test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
