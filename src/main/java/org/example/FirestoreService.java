package org.example;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.api.core.ApiFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Service for interacting with Firebase Firestore
 */
public class FirestoreService {
    private static Firestore db;
    private static FirestoreService instance;
    private static final String USERS_COLLECTION = "users";
    private static final String WALLETS_COLLECTION = "wallets";
    private static final String TRANSACTIONS_COLLECTION = "transactions";
    private static final String PORTFOLIOS_COLLECTION = "portfolios";
    private static final String SETTINGS_COLLECTION = "settings";
    private static final String ACTIVITIES_COLLECTION = "activities";

    /**
     * Private constructor to enforce singleton pattern
     */
    private FirestoreService() {
        // Initialization is done in the initialize method
    }

    /**
     * Initialize Firebase and Firestore
     * This method must be called before any other method
     */
    public static void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                System.out.println("Initializing Firebase with service account key");
                FileInputStream serviceAccount = new FileInputStream("src/main/resources/serviceAccountKey.json");
                
                String projectId = "cashclimb-d162c";
                String databaseUrl = "https://" + projectId + ".firebaseio.com";
                
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setDatabaseUrl(databaseUrl)
                        .setProjectId(projectId)
                        .build();
                
                FirebaseApp app = FirebaseApp.initializeApp(options);
                System.out.println("Firebase app name: " + app.getName());
            } else {
                System.out.println("Firebase already initialized with " + FirebaseApp.getApps().size() + " apps");
            }
            
            db = FirestoreClient.getFirestore();
            System.out.println("Firebase Firestore initialized successfully!");
            
            // Try a simple write/read to validate the connection
            try {
                DocumentReference docRef = db.collection("system").document("test");
                Map<String, Object> testData = new HashMap<>();
                testData.put("timestamp", FieldValue.serverTimestamp());
                testData.put("message", "Initialization test at " + new java.util.Date().toString());
                ApiFuture<WriteResult> result = docRef.set(testData);
                WriteResult writeResult = result.get();
                System.out.println("Test document written at: " + writeResult.getUpdateTime());
            } catch (Exception e) {
                System.err.println("Warning: Test write to Firestore failed: " + e.getMessage());
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }

    /**
     * Get the singleton instance of FirestoreService
     * 
     * @return FirestoreService instance
     */
    public static synchronized FirestoreService getInstance() {
        if (instance == null) {
            instance = new FirestoreService();
        }
        
        // Initialize Firestore if not already done
        if (db == null) {
            initialize();
        }
        
        return instance;
    }

    /**
     * Check if Firestore is available
     * 
     * @return true if Firestore is available, false otherwise
     */
    public boolean isAvailable() {
        return db != null;
    }

    /**
     * Get a user's profile data
     * 
     * @param userId The user ID
     * @return Map containing user data
     */
    public Map<String, Object> getUserProfile(String userId) {
        if (!isAvailable()) {
            return new HashMap<>();
        }
        
        try {
            DocumentSnapshot document = db.collection(USERS_COLLECTION).document(userId).get().get();
            if (document.exists()) {
                return document.getData();
            } else {
                return new HashMap<>();
            }
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user profile: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Save or update a user's profile data
     * 
     * @param userId The user ID
     * @param data The data to save
     * @return true if successful, false otherwise
     */
    public boolean saveUserProfile(String userId, Map<String, Object> data) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            db.collection(USERS_COLLECTION).document(userId).set(data).get();
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving user profile: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get a user's wallets
     * 
     * @param userId The user ID
     * @return List of wallet data maps
     */
    public List<Map<String, Object>> getUserWallets(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> wallets = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                wallets.add(document.getData());
            }
            
            return wallets;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user wallets: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Save a wallet for a user
     * 
     * @param userId The user ID
     * @param walletId The wallet ID (address)
     * @param walletData The wallet data
     * @return true if successful, false otherwise
     */
    public boolean saveWallet(String userId, String walletId, Map<String, Object> walletData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .document(walletId)
                    .set(walletData)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving wallet: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a wallet for a user
     * 
     * @param userId The user ID
     * @param walletId The wallet ID (address)
     * @return true if successful, false otherwise
     */
    public boolean deleteWallet(String userId, String walletId) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .document(walletId)
                    .delete()
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error deleting wallet: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get a user's stock portfolio
     * 
     * @param userId The user ID
     * @return List of stock position data maps
     */
    public List<Map<String, Object>> getUserPortfolio(String userId) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(PORTFOLIOS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> positions = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                positions.add(document.getData());
            }
            
            return positions;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user portfolio: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Save a stock position for a user
     * 
     * @param userId The user ID
     * @param symbol The stock symbol
     * @param positionData The stock position data
     * @return true if successful, false otherwise
     */
    public boolean saveStockPosition(String userId, String symbol, Map<String, Object> positionData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(PORTFOLIOS_COLLECTION)
                    .document(symbol)
                    .set(positionData)
                    .get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving stock position: " + e.getMessage());
            return false;
        }
    }

    /**
     * Save a transaction for a user
     * 
     * @param userId The user ID
     * @param transactionData The transaction data
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
            
            return docRef.getId();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving transaction: " + e.getMessage());
            return "";
        }
    }

    /**
     * Get a user's transactions
     * 
     * @param userId The user ID
     * @param limit Maximum number of transactions to return
     * @return List of transaction data maps
     */
    public List<Map<String, Object>> getUserTransactions(String userId, int limit) {
        if (!isAvailable()) {
            return new ArrayList<>();
        }
        
        try {
            // Order by timestamp descending (most recent first) and limit
            Query query = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(TRANSACTIONS_COLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING);
            
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
     * Get user settings
     * 
     * @param userId The user ID
     * @return Map of settings
     */
    public Map<String, Object> getUserSettings(String userId) {
        if (!isAvailable()) {
            return new HashMap<>();
        }
        
        try {
            DocumentSnapshot document = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(SETTINGS_COLLECTION)
                    .document("preferences")
                    .get()
                    .get();
            
            if (document.exists()) {
                return document.getData();
            } else {
                return new HashMap<>();
            }
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error getting user settings: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Save user settings
     * 
     * @param userId The user ID
     * @param settings The settings map
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
    
    /**
     * Save user activity for analytics and audit purposes
     * 
     * @param activityData The activity data to save
     * @return true if successful, false otherwise
     */
    public boolean saveActivity(Map<String, Object> activityData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Generate a unique ID for the activity
            DocumentReference docRef = db.collection(ACTIVITIES_COLLECTION).document();
            
            // Add the activity ID to the data
            activityData.put("id", docRef.getId());
            
            // Add timestamp if not already present
            if (!activityData.containsKey("timestamp")) {
                activityData.put("timestamp", FieldValue.serverTimestamp());
            }
            
            // Save the activity
            docRef.set(activityData).get();
            
            return true;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error saving activity: " + e.getMessage());
            return false;
        }
    }

    /**
     * Convert a Wallet object to a Map
     * 
     * @param wallet The Wallet object
     * @return Map representation of the wallet
     */
    public static Map<String, Object> walletToMap(Wallet wallet) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", wallet.getId());
        map.put("label", wallet.getLabel());
        map.put("address", wallet.getAddress());
        map.put("cryptoType", wallet.getCryptoType());
        map.put("balance", wallet.getBalance());
        map.put("value", wallet.getValue());
        map.put("change24h", wallet.getChange24h());
        map.put("lastUpdated", wallet.getLastUpdated());
        
        return map;
    }

    /**
     * Convert a map to a Wallet object
     * 
     * @param map The map representation of the wallet
     * @return Wallet object
     */
    public static Wallet mapToWallet(Map<String, Object> map) {
        Wallet wallet = new Wallet(
                (String) map.get("label"),
                (String) map.get("address"),
                (String) map.get("cryptoType")
        );
        
        // Set additional properties
        wallet.setId((String) map.get("id"));
        
        // Convert properly from firestore types
        if (map.get("balance") instanceof Number) {
            wallet.setBalance(((Number) map.get("balance")).doubleValue());
        }
        
        if (map.get("value") instanceof Number) {
            wallet.setValue(((Number) map.get("value")).doubleValue());
        }
        
        if (map.get("change24h") instanceof Number) {
            wallet.setChange24h(((Number) map.get("change24h")).doubleValue());
        }
        
        wallet.setLastUpdated((String) map.get("lastUpdated"));
        
        return wallet;
    }

    /**
     * Convert a StockPosition object to a Map
     * 
     * @param position The StockPosition object
     * @return Map representation of the position
     */
//    public static Map<String, Object> stockPositionToMap(StockPosition position) {
//        Map<String, Object> map = new HashMap<>();
//        map.put("symbol", position.getSymbol());
//        map.put("qty", position.getQuantity());
//        map.put("avgPrice", position.getAverageEntryPrice());
//        map.put("marketValue", position.getMarketValue());
//        map.put("costBasis", position.getQuantity() * position.getAverageEntryPrice()); // Calculate cost basis
//        map.put("unrealizedPL", position.getUnrealizedProfitLoss());
//        map.put("unrealizedPLPercent", position.getUnrealizedProfitLossPercent());
//        map.put("assetId", position.getAssetId());
//        map.put("lastUpdated", position.getLastUpdated());
//        // Note: Exchange information not available in StockPosition class
//
//        return map;
//    }

    /**
     * Convert a map to a StockPosition object
     * 
     * @param map The map representation of the position
     * @return StockPosition object
     */
//    public static StockPosition mapToStockPosition(Map<String, Object> map) {
//        // Create a JSON representation for the constructor
//        JSONObject json = new JSONObject(map);
//        return new StockPosition(json);
//    }
}
