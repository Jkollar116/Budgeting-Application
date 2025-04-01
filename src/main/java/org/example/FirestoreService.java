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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for interacting with Firebase Firestore
 */
public class FirestoreService {
    private static final Logger LOGGER = Logger.getLogger(FirestoreService.class.getName());
    private static Firestore db;
    private static FirestoreService instance;
    private static final String USERS_COLLECTION = "users";
    private static final String WALLETS_COLLECTION = "wallets";
    private static final String TRANSACTIONS_COLLECTION = "transactions";
    private static final String PORTFOLIOS_COLLECTION = "portfolios";
    private static final String SETTINGS_COLLECTION = "settings";
    private static final String ACTIVITIES_COLLECTION = "activities";
    private static boolean initializationAttempted = false;
    private static final String PROJECT_ID = "cashclimb-d162c";

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
        if (initializationAttempted) {
            LOGGER.info("Firebase initialization already attempted, not retrying");
            return;
        }
        
        initializationAttempted = true;
        
        // Run diagnostic checks before initialization
        System.out.println("\n============ FIREBASE DIAGNOSTIC ============");
        diagnoseProblem();
        System.out.println("============================================\n");
        
        try {
            // Only initialize if not already initialized
            if (FirebaseApp.getApps().isEmpty()) {
                System.out.println("Initializing Firebase with service account key");
                
                // Try to load credentials using multiple methods
                GoogleCredentials credentials = loadCredentials();
                if (credentials == null) {
                    System.out.println("Failed to load Firebase credentials from any source");
                    return;
                }
                
                String databaseUrl = "https://" + PROJECT_ID + ".firebaseio.com";
                
                // Create FirebaseOptions with explicit project ID and credentials
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setDatabaseUrl(databaseUrl)
                        .setProjectId(PROJECT_ID)
                        .build();
                
                // Initialize the Firebase app
                FirebaseApp app = FirebaseApp.initializeApp(options);
                LOGGER.info("Firebase app name: " + app.getName());
            } else {
                LOGGER.info("Firebase already initialized with " + FirebaseApp.getApps().size() + " apps");
            }
            
            // Get Firestore instance
            db = FirestoreClient.getFirestore();
            LOGGER.info("Firebase Firestore initialized successfully!");
            
            // Try a simple write/read to validate the connection
            try {
                validateFirestoreConnection();
            } catch (Exception e) {
                LOGGER.warning("Warning: Test write to Firestore failed: " + e.getMessage());
                // Don't set db to null - the connection might still work for other operations
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Validates Firestore connection by writing to a test document
     */
    private static void validateFirestoreConnection() throws ExecutionException, InterruptedException {
        DocumentReference docRef = db.collection("system").document("test");
        Map<String, Object> testData = new HashMap<>();
        testData.put("timestamp", FieldValue.serverTimestamp());
        testData.put("message", "Initialization test at " + new Date().toString());
        
        // Execute write and wait for result
        LOGGER.info("Testing Firestore connection with write operation...");
        WriteResult writeResult = docRef.set(testData).get();
        LOGGER.info("Test document written at: " + writeResult.getUpdateTime());
        
        // Try to read back the document
        LOGGER.info("Verifying read capability...");
        DocumentSnapshot snapshot = docRef.get().get();
        if (snapshot.exists()) {
            LOGGER.info("Successfully read test document");
        } else {
            LOGGER.warning("Test document not found after writing");
        }
    }
    
    /**
     * Load Google credentials using multiple methods, trying each until one succeeds
     */
    private static void diagnoseProblem() {
        System.out.println("DIAGNOSIS: Checking Firebase Authentication Issue");
        
        // Check if we have the credentials file
        System.out.println("Step 1: Checking credential files...");
        
        String[] possiblePaths = {
            "firebase-credentials.json", 
            "serviceAccountKey.json",
            "src/main/resources/serviceAccountKey.json",
            "./serviceAccountKey.json"
        };
        
        boolean found = false;
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                System.out.println("  ✓ Found credential file at: " + file.getAbsolutePath());
                System.out.println("    File size: " + file.length() + " bytes");
                System.out.println("    Last modified: " + new java.util.Date(file.lastModified()));
                found = true;
                
                // Try to peek into file content
                try {
                    String content = new String(Files.readAllBytes(file.toPath()));
                    if (content.contains("\"type\": \"service_account\"")) {
                        System.out.println("  ✓ File contains service account configuration");
                    } else {
                        System.out.println("  ✗ File does NOT contain service account configuration");
                    }
                } catch (Exception e) {
                    System.out.println("  ✗ Could not read file contents: " + e.getMessage());
                }
            } else {
                System.out.println("  ✗ No credential file at: " + path);
            }
        }
        
        if (!found) {
            System.out.println("PROBLEM: No credential files found!");
        }
        
        // Check environment variable
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null) {
            System.out.println("  GOOGLE_APPLICATION_CREDENTIALS environment variable: " + envPath);
            File envFile = new File(envPath);
            if (envFile.exists()) {
                System.out.println("  ✓ File exists at path specified by environment variable");
            } else {
                System.out.println("  ✗ File DOES NOT exist at path specified by environment variable");
                System.out.println("PROBLEM: Environment variable points to missing file");
            }
        } else {
            System.out.println("  ✗ GOOGLE_APPLICATION_CREDENTIALS environment variable not set");
        }
        
        // Check for API enablement
        System.out.println("\nStep 2: Checking API enablement...");
        System.out.println("  • Make sure the Firebase Admin SDK is enabled for project ID: cashclimb-d162c");
        System.out.println("  • Make sure Firestore API is enabled in Google Cloud Console");
        System.out.println("  • Make sure the service account has Firebase Admin SDK Administrator role");
        
        // Check network connectivity
        System.out.println("\nStep 3: Checking network connectivity...");
        try {
            boolean canReachFirebase = java.net.InetAddress.getByName("firestore.googleapis.com").isReachable(5000);
            if (canReachFirebase) {
                System.out.println("  ✓ Can reach Firestore servers");
            } else {
                System.out.println("  ✗ Cannot reach Firestore servers");
                System.out.println("PROBLEM: Network connectivity issues");
            }
        } catch (Exception e) {
            System.out.println("  ✗ Network test failed: " + e.getMessage());
        }
        
        System.out.println("\nStep 4: Summary of potential issues");
        System.out.println("  1. Service account may not have necessary permissions");
        System.out.println("  2. Firestore API may not be enabled for the project");
        System.out.println("  3. Credential file may be corrupted or invalid");
        System.out.println("  4. Network connectivity issues preventing API access");
    }
    
    private static GoogleCredentials loadCredentials() {
        GoogleCredentials credentials = null;
        
        System.out.println("Attempting to load credentials from various sources...");
        
        // Method 1: Try environment variable GOOGLE_APPLICATION_CREDENTIALS
        try {
            String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credPath != null && !credPath.isEmpty()) {
                LOGGER.info("Trying to load credentials from environment variable: " + credPath);
                File file = new File(credPath);
                if (file.exists()) {
                    try (FileInputStream serviceAccount = new FileInputStream(file)) {
                        credentials = GoogleCredentials.fromStream(serviceAccount);
                        LOGGER.info("Successfully loaded credentials from environment variable");
                        return credentials;
                    } catch (IOException e) {
                        LOGGER.warning("Failed to load credentials from environment variable: " + e.getMessage());
                    }
                } else {
                    LOGGER.warning("Credentials file specified in environment variable does not exist: " + credPath);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error checking environment variable: " + e.getMessage());
        }
        
        // Method 2: Try explicit file paths
        String[] possiblePaths = {
            "firebase-credentials.json", 
            "serviceAccountKey.json",
            "src/main/resources/serviceAccountKey.json",
            "./serviceAccountKey.json",
            "../serviceAccountKey.json"
        };
        
        for (String path : possiblePaths) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    LOGGER.info("Found credentials file at: " + file.getAbsolutePath());
                    try (FileInputStream serviceAccount = new FileInputStream(file)) {
                        credentials = GoogleCredentials.fromStream(serviceAccount);
                        LOGGER.info("Successfully loaded credentials from: " + path);
                        return credentials;
                    } catch (IOException e) {
                        LOGGER.warning("Failed to load credentials from " + path + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Error checking path " + path + ": " + e.getMessage());
            }
        }
        
        // Method 3: Try application default credentials
        try {
            LOGGER.info("Trying application default credentials");
            credentials = GoogleCredentials.getApplicationDefault();
            LOGGER.info("Successfully loaded application default credentials");
            return credentials;
        } catch (IOException e) {
            LOGGER.warning("Failed to load application default credentials: " + e.getMessage());
        }
        
        // Method 4: Create new service account file from project resources if needed
        try {
            LOGGER.info("Trying to create a new service account file from resources");
            credentials = createAndLoadCredentialsFile();
            if (credentials != null) {
                LOGGER.info("Successfully created and loaded credentials from new file");
                return credentials;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to create and load new credentials file: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Create a new credentials file from resources and load it
     */
    private static GoogleCredentials createAndLoadCredentialsFile() {
        try {
            // Check if we have a template or existing service account key
            File templateFile = new File("src/main/resources/serviceAccountKey.json");
            File outputFile = new File("serviceAccountKey.json");
            
            if (templateFile.exists() && !outputFile.exists()) {
                // Copy the template to the output location
                String content = new String(Files.readAllBytes(templateFile.toPath()));
                Files.write(outputFile.toPath(), content.getBytes());
                LOGGER.info("Created new serviceAccountKey.json from template");
                
                // Try to load the new file
                try (FileInputStream serviceAccount = new FileInputStream(outputFile)) {
                    return GoogleCredentials.fromStream(serviceAccount);
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Error creating credentials file: " + e.getMessage());
        }
        
        return null;
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
            LOGGER.info("Getting user profile for ID: " + userId);
            DocumentSnapshot document = db.collection(USERS_COLLECTION).document(userId).get().get();
            if (document.exists()) {
                LOGGER.info("Found user profile");
                return document.getData();
            } else {
                LOGGER.info("User profile not found, returning empty map");
                return new HashMap<>();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error getting user profile: " + e.getMessage());
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
            LOGGER.info("Saving user profile for ID: " + userId);
            db.collection(USERS_COLLECTION).document(userId).set(data).get();
            LOGGER.info("User profile saved to Firestore with ID: " + userId);
            return true;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error saving user profile: " + e.getMessage());
            LOGGER.severe("User profile failed to save to Firestore with ID: " + userId);
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
            LOGGER.info("Getting wallets for user ID: " + userId);
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> wallets = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                wallets.add(document.getData());
            }
            
            LOGGER.info("Found " + wallets.size() + " wallets for user: " + userId);
            return wallets;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error getting user wallets: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Save a wallet for a user
     * 
     * @param userId The user ID
     * @param walletId The wallet ID
     * @param walletData The wallet data
     * @return true if successful, false otherwise
     */
    public boolean saveWallet(String userId, String walletId, Map<String, Object> walletData) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            LOGGER.info("Saving wallet " + walletId + " for user: " + userId);
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .document(walletId)
                    .set(walletData)
                    .get();
            
            LOGGER.info("Wallet saved successfully");
            return true;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error saving wallet: " + e.getMessage());
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
            LOGGER.info("Deleting wallet " + walletId + " for user: " + userId);
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .document(walletId)
                    .delete()
                    .get();
            
            LOGGER.info("Wallet deleted successfully");
            return true;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error deleting wallet: " + e.getMessage());
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
            LOGGER.info("Getting portfolio for user ID: " + userId);
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(PORTFOLIOS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> positions = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                positions.add(document.getData());
            }
            
            LOGGER.info("Found " + positions.size() + " stock positions for user: " + userId);
            return positions;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error getting user portfolio: " + e.getMessage());
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
            LOGGER.info("Saving stock position " + symbol + " for user: " + userId);
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(PORTFOLIOS_COLLECTION)
                    .document(symbol)
                    .set(positionData)
                    .get();
            
            LOGGER.info("Stock position saved successfully");
            return true;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error saving stock position: " + e.getMessage());
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
            LOGGER.info("Saving transaction for user: " + userId);
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
            
            LOGGER.info("Transaction saved with ID: " + docRef.getId());
            return docRef.getId();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error saving transaction: " + e.getMessage());
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
            LOGGER.info("Getting transactions for user ID: " + userId + " with limit: " + limit);
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
            
            LOGGER.info("Found " + transactions.size() + " transactions for user: " + userId);
            return transactions;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error getting user transactions: " + e.getMessage());
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
            LOGGER.info("Getting settings for user ID: " + userId);
            DocumentSnapshot document = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(SETTINGS_COLLECTION)
                    .document("preferences")
                    .get()
                    .get();
            
            if (document.exists()) {
                LOGGER.info("Found user settings");
                return document.getData();
            } else {
                LOGGER.info("User settings not found, returning empty map");
                return new HashMap<>();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error getting user settings: " + e.getMessage());
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
            LOGGER.info("Saving settings for user ID: " + userId);
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(SETTINGS_COLLECTION)
                    .document("preferences")
                    .set(settings)
                    .get();
            
            LOGGER.info("User settings saved successfully");
            return true;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error saving user settings: " + e.getMessage());
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
            LOGGER.info("Saving user activity");
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
            
            LOGGER.info("Activity saved with ID: " + docRef.getId());
            return true;
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.severe("Error saving activity: " + e.getMessage());
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
