package org.example;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.cloud.firestore.Query.Direction;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced service for interacting with Firebase Firestore
 * - Improved credential management
 * - Performance optimizations (batching, caching)
 * - Better error handling with retry logic
 * - Architectural improvements
 */
public class FirestoreServiceEnhanced {
    private static final Logger LOGGER = Logger.getLogger(FirestoreServiceEnhanced.class.getName());
    private static Firestore db;
    private static FirestoreServiceEnhanced instance;
    private static boolean initializationAttempted = false;
    
    // Collection names as constants
    private static final String USERS_COLLECTION = "users";
    private static final String WALLETS_COLLECTION = "wallets";
    private static final String TRANSACTIONS_COLLECTION = "transactions";
    private static final String PORTFOLIOS_COLLECTION = "portfolios";
    private static final String SETTINGS_COLLECTION = "settings";
    private static final String ACTIVITIES_COLLECTION = "activities";
    private static final String SYSTEM_COLLECTION = "system";
    
    // Project configuration
    private static final String PROJECT_ID = "cashclimb-d162c";
    private static final String DATABASE_URL = "https://" + PROJECT_ID + ".firebaseio.com";
    
    // Cache configuration
    private static final int CACHE_SIZE = 100; // Maximum number of entries
    private static final int CACHE_DURATION_MINUTES = 5; // Cache expiration time
    
    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;
    
    // In-memory cache for frequently accessed data
    private final Cache<String, Object> dataCache;
    
    // Registration of listeners for cleanup
    private final Map<String, ListenerRegistration> activeListeners = new HashMap<>();

    /**
     * Private constructor to enforce singleton pattern
     */
    private FirestoreServiceEnhanced() {
        // Initialize cache with size limit and expiration
        dataCache = CacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .expireAfterWrite(CACHE_DURATION_MINUTES, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Get the singleton instance of FirestoreServiceEnhanced
     * 
     * @return FirestoreServiceEnhanced instance
     */
    public static synchronized FirestoreServiceEnhanced getInstance() {
        if (instance == null) {
            instance = new FirestoreServiceEnhanced();
        }
        
        // Initialize Firestore if not already done
        if (db == null) {
            initialize();
        }
        
        return instance;
    }

    /**
     * Initialize Firebase and Firestore with improved credential management
     * This method must be called before any other method
     */
    public static void initialize() {
        if (initializationAttempted) {
            LOGGER.info("Firebase initialization already attempted, not retrying");
            return;
        }
        
        initializationAttempted = true;
        
        try {
            // Only initialize if not already initialized
            if (FirebaseApp.getApps().isEmpty()) {
                LOGGER.info("Starting Firebase initialization");
                
                // Load credentials with more secure approach
                GoogleCredentials credentials = loadCredentialsSecurely();
                if (credentials == null) {
                    LOGGER.severe("Failed to load Firebase credentials from any source");
                    return;
                }
                
                // Create FirebaseOptions with explicit project ID and credentials
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setDatabaseUrl(DATABASE_URL)
                        .setProjectId(PROJECT_ID)
                        .build();
                
                // Initialize the Firebase app
                FirebaseApp app = FirebaseApp.initializeApp(options);
                LOGGER.info("Firebase app initialized with name: " + app.getName());
            } else {
                LOGGER.info("Firebase already initialized with " + FirebaseApp.getApps().size() + " apps");
            }
            
            // Get Firestore instance
            db = FirestoreClient.getFirestore();
            LOGGER.info("Firebase Firestore initialized successfully!");
            
            // Validate connection - with retry logic
            validateConnection();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Firebase", e);
        }
    }
    
    /**
     * Validates Firestore connection with retry logic
     */
    private static void validateConnection() {
        final int maxAttempts = 3;
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxAttempts) {
            attempt++;
            try {
                DocumentReference docRef = db.collection(SYSTEM_COLLECTION).document("connection_test");
                Map<String, Object> testData = new HashMap<>();
                testData.put("timestamp", FieldValue.serverTimestamp());
                testData.put("message", "Connection test at " + new Date());
                
                LOGGER.info("Testing Firestore connection (attempt " + attempt + " of " + maxAttempts + ")");
                WriteResult writeResult = docRef.set(testData).get(10, TimeUnit.SECONDS); // Add timeout
                
                // Try to read back
                DocumentSnapshot snapshot = docRef.get().get(10, TimeUnit.SECONDS);
                if (snapshot.exists()) {
                    LOGGER.info("Connection test successful at: " + writeResult.getUpdateTime());
                    return; // Success, exit the retry loop
                }
            } catch (Exception e) {
                lastException = e;
                LOGGER.warning("Connection test failed (attempt " + attempt + "): " + e.getMessage());
                
                // Exponential backoff
                try {
                    Thread.sleep(INITIAL_RETRY_DELAY_MS * (1 << (attempt - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break; // Exit if thread is interrupted
                }
            }
        }
        
        // If we get here, all attempts failed
        LOGGER.severe("All connection test attempts failed. Last error: " + 
                      (lastException != null ? lastException.getMessage() : "Unknown"));
    }
    
    /**
     * Load Google credentials using a more secure, prioritized approach
     */
    private static GoogleCredentials loadCredentialsSecurely() {
        LOGGER.info("Loading Firebase credentials");
        GoogleCredentials credentials = null;
        List<Exception> errors = new ArrayList<>();
        
        // Priority 1: Environment variable - Most secure, recommended approach
        try {
            String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credPath != null && !credPath.isEmpty()) {
                File file = new File(credPath);
                if (file.exists()) {
                    try (FileInputStream serviceAccount = new FileInputStream(file)) {
                        credentials = GoogleCredentials.fromStream(serviceAccount);
                        LOGGER.info("Credentials loaded from environment variable: " + credPath);
                        return credentials;
                    } catch (IOException e) {
                        errors.add(new IOException("Failed to load from env variable: " + e.getMessage(), e));
                    }
                } else {
                    errors.add(new IOException("Env variable file doesn't exist: " + credPath));
                }
            }
        } catch (Exception e) {
            errors.add(e);
        }
        
        // Priority 2: Project-specific credential file
        String[] possiblePaths = {
            "firebase-credentials.json", 
            "serviceAccountKey.json",
            "src/main/resources/serviceAccountKey.json"
        };
        
        for (String path : possiblePaths) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    try (FileInputStream serviceAccount = new FileInputStream(file)) {
                        credentials = GoogleCredentials.fromStream(serviceAccount);
                        LOGGER.info("Credentials loaded from file: " + file.getAbsolutePath());
                        return credentials;
                    } catch (IOException e) {
                        errors.add(new IOException("Failed to load from " + path + ": " + e.getMessage(), e));
                    }
                }
            } catch (Exception e) {
                errors.add(e);
            }
        }
        
        // Priority 3: Application Default Credentials
        try {
            credentials = GoogleCredentials.getApplicationDefault();
            LOGGER.info("Credentials loaded from Application Default Credentials");
            return credentials;
        } catch (IOException e) {
            errors.add(new IOException("Failed to load application default credentials: " + e.getMessage(), e));
        }
        
        // Log all errors encountered during credential loading
        LOGGER.severe("Failed to load credentials. Encountered " + errors.size() + " errors:");
        for (int i = 0; i < errors.size(); i++) {
            LOGGER.severe("Error " + (i+1) + ": " + errors.get(i).getMessage());
        }
        
        return null;
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
     * Safely execute a Firestore operation with retry logic
     * 
     * @param <T> The return type of the operation
     * @param operation The operation to execute
     * @param errorMessage Error message prefix for logging
     * @param defaultValue Default value to return on failure
     * @return The result of the operation or defaultValue on failure
     */
    private <T> T executeWithRetry(FirestoreOperation<T> operation, String errorMessage, T defaultValue) {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < MAX_RETRIES) {
            try {
                if (!isAvailable()) {
                    LOGGER.warning("Firestore not available, returning default value");
                    return defaultValue;
                }
                
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (attempts < MAX_RETRIES) {
                    LOGGER.warning(errorMessage + " (attempt " + attempts + "/" + MAX_RETRIES + "): " + e.getMessage());
                    
                    // Exponential backoff
                    try {
                        Thread.sleep(INITIAL_RETRY_DELAY_MS * (1 << (attempts - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // All retries failed
        LOGGER.severe(errorMessage + " - all " + MAX_RETRIES + " attempts failed. Last error: " + 
                     (lastException != null ? lastException.getMessage() : "Unknown"));
        
        if (lastException != null) {
            LOGGER.log(Level.SEVERE, "Stack trace:", lastException);
        }
        
        return defaultValue;
    }
    
    /**
     * Functional interface for Firestore operations
     */
    @FunctionalInterface
    private interface FirestoreOperation<T> {
        T execute() throws Exception;
    }
    
    /**
     * Create a deep copy of a list of maps to prevent cache corruption
     * 
     * @param <K> Key type
     * @param <V> Value type
     * @param originalList The original list to copy
     * @return A deep copy of the original list
     */
    private <K, V> List<Map<K, V>> deepCopyList(List<Map<K, V>> originalList) {
        if (originalList == null) {
            return null;
        }
        
        List<Map<K, V>> copiedList = new ArrayList<>(originalList.size());
        
        for (Map<K, V> originalMap : originalList) {
            if (originalMap != null) {
                copiedList.add(new HashMap<>(originalMap));
            } else {
                copiedList.add(null);
            }
        }
        
        return copiedList;
    }
    
    /**
     * Invalidate the cache entry for a user's wallets
     * 
     * @param userId The user ID
     */
    private void invalidateUserWalletsCache(String userId) {
        final String cacheKey = "user_wallets_" + userId;
        dataCache.invalidate(cacheKey);
        LOGGER.fine("Invalidated wallets cache for user: " + userId);
    }
    
    /**
     * Invalidate the cache entry for a user's portfolio
     * 
     * @param userId The user ID
     */
    private void invalidateUserPortfolioCache(String userId) {
        final String cacheKey = "user_portfolio_" + userId;
        dataCache.invalidate(cacheKey);
        LOGGER.fine("Invalidated portfolio cache for user: " + userId);
    }
    
    /**
     * Invalidate the cache entry for a user's transactions
     * 
     * @param userId The user ID
     */
    private void invalidateUserTransactionsCache(String userId) {
        // Invalidate all transaction caches for this user by pattern
        // Since we can have different limits, we can't predict all keys
        // Just remove any transaction cache keys containing this user's ID
        Set<String> keysToInvalidate = new HashSet<>();
        for (Object key : dataCache.asMap().keySet()) {
            String keyStr = (String) key;
            if (keyStr.startsWith("user_transactions_" + userId)) {
                keysToInvalidate.add(keyStr);
            }
        }
        
        // Invalidate all matching keys
        keysToInvalidate.forEach(dataCache::invalidate);
        LOGGER.fine("Invalidated " + keysToInvalidate.size() + " transaction cache entries for user: " + userId);
    }
    
    /**
     * Get a user's profile data with cache support
     * 
     * @param userId The user ID
     * @return Map containing user data
     */
    public Map<String, Object> getUserProfile(String userId) {
        final String cacheKey = "user_profile_" + userId;
        
        // Try to get from cache first
        @SuppressWarnings("unchecked")
        Map<String, Object> cachedProfile = (Map<String, Object>) dataCache.getIfPresent(cacheKey);
        if (cachedProfile != null) {
            LOGGER.fine("User profile for " + userId + " found in cache");
            return new HashMap<>(cachedProfile); // Return a copy to prevent cache corruption
        }
        
        // Not in cache, get from Firestore with retry
        Map<String, Object> profile = executeWithRetry(() -> {
            DocumentSnapshot document = db.collection(USERS_COLLECTION).document(userId).get().get();
            if (document.exists()) {
                return document.getData();
            } else {
                return new HashMap<>();
            }
        }, "Error getting user profile for " + userId, new HashMap<>());
        
        // Cache the result if not empty
        if (!profile.isEmpty()) {
            dataCache.put(cacheKey, new HashMap<>(profile)); // Store a copy in cache
        }
        
        return profile;
    }

    /**
     * Save or update a user's profile data
     * 
     * @param userId The user ID
     * @param data The data to save
     * @return true if successful, false otherwise
     */
    public boolean saveUserProfile(String userId, Map<String, Object> data) {
        boolean success = executeWithRetry(() -> {
            db.collection(USERS_COLLECTION).document(userId).set(data).get();
            return true;
        }, "Error saving user profile for " + userId, false);
        
        // Update cache if successful
        if (success) {
            final String cacheKey = "user_profile_" + userId;
            dataCache.put(cacheKey, new HashMap<>(data)); // Store a copy in cache
        }
        
        return success;
    }

    /**
     * Get a user's wallets with cache support
     * 
     * @param userId The user ID
     * @return List of wallet data maps
     */
    public List<Map<String, Object>> getUserWallets(String userId) {
        final String cacheKey = "user_wallets_" + userId;
        
        // Try to get from cache first
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cachedWallets = (List<Map<String, Object>>) dataCache.getIfPresent(cacheKey);
        if (cachedWallets != null) {
            LOGGER.fine("Wallets for " + userId + " found in cache, count: " + cachedWallets.size());
            return deepCopyList(cachedWallets); // Return a copy to prevent cache corruption
        }
        
        // Not in cache, get from Firestore with retry
        List<Map<String, Object>> wallets = executeWithRetry(() -> {
            QuerySnapshot querySnapshot = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .get()
                    .get();
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                result.add(document.getData());
            }
            
            return result;
        }, "Error getting wallets for " + userId, new ArrayList<>());
        
        // Cache the result if not empty
        if (!wallets.isEmpty()) {
            dataCache.put(cacheKey, deepCopyList(wallets)); // Store a copy in cache
        }
        
        return wallets;
    }

    /**
     * Save multiple wallets in a batch operation
     * 
     * @param userId The user ID
     * @param wallets Map of wallet IDs to wallet data
     * @return true if all wallets were saved successfully, false otherwise
     */
    public boolean saveWalletsBatch(String userId, Map<String, Map<String, Object>> wallets) {
        if (wallets == null || wallets.isEmpty()) {
            return true; // Nothing to save
        }
        
        return executeWithRetry(() -> {
            WriteBatch batch = db.batch();
            
            for (Map.Entry<String, Map<String, Object>> entry : wallets.entrySet()) {
                DocumentReference docRef = db.collection(USERS_COLLECTION)
                        .document(userId)
                        .collection(WALLETS_COLLECTION)
                        .document(entry.getKey());
                
                batch.set(docRef, entry.getValue());
            }
            
            // Commit the batch
            batch.commit().get();
            
            // Update cache
            invalidateUserWalletsCache(userId);
            
            return true;
        }, "Error saving wallets batch for " + userId, false);
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
        boolean success = executeWithRetry(() -> {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .document(walletId)
                    .set(walletData)
                    .get();
            
            return true;
        }, "Error saving wallet " + walletId + " for " + userId, false);
        
        // Invalidate cache if successful
        if (success) {
            invalidateUserWalletsCache(userId);
        }
        
        return success;
    }

    /**
     * Delete a wallet for a user
     * 
     * @param userId The user ID
     * @param walletId The wallet ID (address)
     * @return true if successful, false otherwise
     */
    public boolean deleteWallet(String userId, String walletId) {
        boolean success = executeWithRetry(() -> {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(WALLETS_COLLECTION)
                    .document(walletId)
                    .delete()
                    .get();
            
            return true;
        }, "Error deleting wallet " + walletId + " for " + userId, false);
        
        // Invalidate cache if successful
        if (success) {
            invalidateUserWalletsCache(userId);
        }
        
        return success;
    }

    /**
     * Get a user's stock portfolio with cache support
     * 
     * @param userId The user ID
     * @return List of stock position data maps
     */
    public List<Map<String, Object>> getUserPortfolio(String userId) {
        final String cacheKey = "user_portfolio_" + userId;
        
        // Try to get from cache first
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cachedPortfolio = (List<Map<String, Object>>) dataCache.getIfPresent(cacheKey);
        if (cachedPortfolio != null) {
            LOGGER.fine("Portfolio for " + userId + " found in cache, positions: " + cachedPortfolio.size());
            return deepCopyList(cachedPortfolio); // Return a copy to prevent cache corruption
        }
        
        // Not in cache, get from Firestore with retry
        List<Map<String, Object>> portfolio = executeWithRetry(() -> {
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
        }, "Error getting portfolio for " + userId, new ArrayList<>());
        
        // Cache the result if not empty
        if (!portfolio.isEmpty()) {
            dataCache.put(cacheKey, deepCopyList(portfolio)); // Store a copy in cache
        }
        
        return portfolio;
    }

    /**
     * Save stock positions in a batch operation
     * 
     * @param userId The user ID
     * @param positions Map of stock symbols to position data
     * @return true if all positions were saved successfully, false otherwise
     */
    public boolean saveStockPositionsBatch(String userId, Map<String, Map<String, Object>> positions) {
        if (positions == null || positions.isEmpty()) {
            return true; // Nothing to save
        }
        
        return executeWithRetry(() -> {
            WriteBatch batch = db.batch();
            
            for (Map.Entry<String, Map<String, Object>> entry : positions.entrySet()) {
                DocumentReference docRef = db.collection(USERS_COLLECTION)
                        .document(userId)
                        .collection(PORTFOLIOS_COLLECTION)
                        .document(entry.getKey());
                
                batch.set(docRef, entry.getValue());
            }
            
            // Commit the batch
            batch.commit().get();
            
            // Update cache
            invalidateUserPortfolioCache(userId);
            
            return true;
        }, "Error saving stock positions batch for " + userId, false);
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
        boolean success = executeWithRetry(() -> {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(PORTFOLIOS_COLLECTION)
                    .document(symbol)
                    .set(positionData)
                    .get();
            
            return true;
        }, "Error saving stock position " + symbol + " for " + userId, false);
        
        // Invalidate cache if successful
        if (success) {
            invalidateUserPortfolioCache(userId);
        }
        
        return success;
    }

    /**
     * Save multiple transactions in a batch operation
     * 
     * @param userId The user ID
     * @param transactions List of transaction data maps
     * @return List of transaction IDs that were successfully saved
     */
    public List<String> saveTransactionsBatch(String userId, List<Map<String, Object>> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return new ArrayList<>(); // Nothing to save
        }
        
        return executeWithRetry(() -> {
            List<String> transactionIds = new ArrayList<>();
            WriteBatch batch = db.batch();
            
            for (Map<String, Object> transaction : transactions) {
                // Generate a unique ID for the transaction
                DocumentReference docRef = db.collection(USERS_COLLECTION)
                        .document(userId)
                        .collection(TRANSACTIONS_COLLECTION)
                        .document();
                
                String transactionId = docRef.getId();
                transactionIds.add(transactionId);
                
                // Add the transaction ID to the data
                transaction.put("id", transactionId);
                
                // Add timestamp if not already present
                if (!transaction.containsKey("timestamp")) {
                    transaction.put("timestamp", FieldValue.serverTimestamp());
                }
                
                batch.set(docRef, transaction);
            }
            
            // Commit the batch
            batch.commit().get();
            
            // Invalidate transactions cache
            invalidateUserTransactionsCache(userId);
            
            return transactionIds;
        }, "Error saving transactions batch for " + userId, new ArrayList<>());
    }

    /**
     * Save a transaction for a user
     * 
     * @param userId The user ID
     * @param transactionData The transaction data
     * @return transaction ID if successful, empty string otherwise
     */
    public String saveTransaction(String userId, Map<String, Object> transactionData) {
        return executeWithRetry(() -> {
            // Generate a unique ID for the transaction
            DocumentReference docRef = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(TRANSACTIONS_COLLECTION)
                    .document();
            
            // Add the transaction ID to the data
            String transactionId = docRef.getId();
            transactionData.put("id", transactionId);
            
            // Add timestamp if not already present
            if (!transactionData.containsKey("timestamp")) {
                transactionData.put("timestamp", FieldValue.serverTimestamp());
            }
            
            // Save the transaction
            docRef.set(transactionData).get();
            
            // Invalidate cache
            invalidateUserTransactionsCache(userId);
            
            return transactionId;
        }, "Error saving transaction for " + userId, "");
    }

    /**
     * Get a user's transactions with pagination support
     * 
     * @param userId The user ID
     * @param limit Maximum number of transactions to return
     * @param startAfter Document snapshot to start after (for pagination)
     * @param filters Optional filters to apply to the query
     * @return List of transaction data maps
     */
    public List<Map<String, Object>> getUserTransactions(String userId, int limit, 
                                                        DocumentSnapshot startAfter,
                                                        Map<String, Object> filters) {
        return executeWithRetry(() -> {
            // Start with base query
            Query query = db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(TRANSACTIONS_COLLECTION)
                    .orderBy("timestamp", Direction.DESCENDING);
            
            // Apply pagination if specified
            if (startAfter != null) {
                query = query.startAfter(startAfter);
            }
            
            // Apply filters if specified
            if (filters != null) {
                for (Map.Entry<String, Object> filter : filters.entrySet()) {
                    query = query.whereEqualTo(filter.getKey(), filter.getValue());
                }
            }
            
            // Apply limit if specified
            if (limit > 0) {
                query = query.limit(limit);
            }
            
            // Execute query
            QuerySnapshot querySnapshot = query.get().get();
            
            // Process results
            List<Map<String, Object>> transactions = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                transactions.add(document.getData());
            }
            
            return transactions;
        }, "Error getting transactions for " + userId, new ArrayList<>());
    }
    
    /**
     * Get a user's transactions with cache support
     * 
     * @param userId The user ID
     * @param limit Maximum number of transactions to return
     * @return List of transaction data maps
     */
    public List<Map<String, Object>> getUserTransactions(String userId, int limit) {
        final String cacheKey = "user_transactions_" + userId + "_" + limit;
        
        // Only use cache for small result sets to avoid memory issues
        if (limit <= 20) {
            // Try to get from cache first
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cachedTransactions = 
                (List<Map<String, Object>>) dataCache.getIfPresent(cacheKey);
            
            if (cachedTransactions != null) {
                LOGGER.fine("Transactions for " + userId + " found in cache, count: " + cachedTransactions.size());
                return deepCopyList(cachedTransactions); // Return a copy to prevent cache corruption
            }
        }
        
        // Not in cache or limit too large, get from Firestore
        List<Map<String, Object>> transactions = getUserTransactions(userId, limit, null, null);
        
        // Cache the result if appropriate
        if (limit <= 20 && !transactions.isEmpty()) {
            dataCache.put(cacheKey, deepCopyList(transactions)); // Store a copy in cache
        }
        
        return transactions;
    }

    /**
     * Get user settings with cache support
     * 
     * @param userId The user ID
     * @return Map of settings
     */
    public Map<String, Object> getUserSettings(String userId) {
        final String cacheKey = "user_settings_" + userId;
        
        // Try to get from cache first
        @SuppressWarnings("unchecked")
        Map<String, Object> cachedSettings = (Map<String, Object>) dataCache.getIfPresent(cacheKey);
        if (cachedSettings != null) {
            LOGGER.fine("Settings for " + userId + " found in cache");
            return new HashMap<>(cachedSettings); // Return a copy to prevent cache corruption
        }
        
        // Not in cache, get from Firestore with retry
        Map<String, Object> settings = executeWithRetry(() -> {
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
        }, "Error getting settings for " + userId, new HashMap<>());
        
        // Cache the result if not empty
        if (!settings.isEmpty()) {
            dataCache.put(cacheKey, new HashMap<>(settings)); // Store a copy in cache
        }
        
        return settings;
    }

    /**
     * Save user settings
     * 
     * @param userId The user ID
     * @param settings The settings map
     * @return true if successful, false otherwise
     */
    public boolean saveUserSettings(String userId, Map<String, Object> settings) {
        boolean success = executeWithRetry(() -> {
            db.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(SETTINGS_COLLECTION)
                    .document("preferences")
                    .set(settings)
                    .get();
            
            return true;
        }, "Error saving settings for " + userId, false);
        
        // Update cache if successful
        if (success) {
            final String cacheKey = "user_settings_" + userId;
            dataCache.put(cacheKey, new HashMap<>(settings)); // Store a copy in cache
        }
        
        return success;
    }
    
    /**
     * Save multiple activities in a batch operation
     * 
     * @param activities List of activity data maps
     * @return true if all activities were saved successfully, false otherwise
     */
    public boolean saveActivitiesBatch(List<Map<String, Object>> activities) {
        if (activities == null || activities.isEmpty()) {
            return true; // Nothing to save
        }
        
        return executeWithRetry(() -> {
            WriteBatch batch = db.batch();
            
            for (Map<String, Object> activity : activities) {
                // Generate a unique ID for the activity
                DocumentReference docRef = db.collection(ACTIVITIES_COLLECTION).document();
                
                // Add the activity ID to the data
                String activityId = docRef.getId();
                activity.put("id", activityId);
                
                // Add timestamp if not already present
                if (!activity.containsKey("timestamp")) {
                    activity.put("timestamp", FieldValue.serverTimestamp());
                }
                
                batch.set(docRef, activity);
            }
            
            // Commit the batch
            batch.commit().get();
            
            return true;
        }, "Error saving activities batch", false);
    }
    
    /**
     * Save a user activity
     * 
     * @param activityData The activity data
     * @return activity ID if successful, empty string otherwise
     */
    public String saveActivity(Map<String, Object> activityData) {
        return executeWithRetry(() -> {
            // Generate a unique ID for the activity
            DocumentReference docRef = db.collection(ACTIVITIES_COLLECTION).document();
            
            // Add the activity ID to the data
            String activityId = docRef.getId();
            activityData.put("id", activityId);
            
            // Add timestamp if not already present
            if (!activityData.containsKey("timestamp")) {
                activityData.put("timestamp", FieldValue.serverTimestamp());
            }
            
            // Save the activity
            docRef.set(activityData).get();
            
            return activityId;
        }, "Error saving activity", "");
    }
    
    /**
     * Set up a real-time listener for a document
     * 
     * @param collection The collection name
     * @param documentId The document ID
     * @param listener The event listener to call when data changes
     * @return The key to use for removing the listener
     */
    public String addRealtimeListener(String collection, String documentId, 
                                     Consumer<DocumentSnapshot> listener) {
        if (!isAvailable()) {
            LOGGER.warning("Firestore not available, cannot add listener");
            return null;
        }
        
        try {
            // Create a unique key for this listener
            String listenerKey = collection + "_" + documentId + "_" + UUID.randomUUID().toString();
            
            // Register the listener
            DocumentReference docRef = db.collection(collection).document(documentId);
            ListenerRegistration registration = docRef.addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    LOGGER.warning("Error in real-time listener: " + error.getMessage());
                    return;
                }
                
                if (snapshot != null && snapshot.exists()) {
                    listener.accept(snapshot);
                }
            });
            
            // Store the registration for later cleanup
            activeListeners.put(listenerKey, registration);
            
            return listenerKey;
        } catch (Exception e) {
            LOGGER.severe("Failed to add real-time listener: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Remove a previously registered real-time listener
     * 
     * @param listenerKey The key returned when the listener was added
     */
    public void removeRealtimeListener(String listenerKey) {
        if (listenerKey == null) {
            return;
        }
        
        ListenerRegistration registration = activeListeners.remove(listenerKey);
        if (registration != null) {
            registration.remove();
            LOGGER.fine("Removed real-time listener: " + listenerKey);
        }
    }
    
    /**
     * Clean up all listeners and resources
     * Call this when the application is shutting down
     */
    public void cleanup() {
        // Remove all active listeners
        for (ListenerRegistration registration : activeListeners.values()) {
            registration.remove();
        }
        activeListeners.clear();
        
        // Clear cache
        dataCache.invalidateAll();
        
        LOGGER.info("Firestore service cleaned up");
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
    public static Map<String, Object> stockPositionToMap(StockPosition position) {
        Map<String, Object> map = new HashMap<>();
        map.put("symbol", position.getSymbol());
        map.put("qty", position.getQuantity());
        map.put("avgPrice", position.getAverageEntryPrice());
        map.put("marketValue", position.getMarketValue());
        map.put("costBasis", position.getQuantity() * position.getAverageEntryPrice()); // Calculate cost basis
        map.put("unrealizedPL", position.getUnrealizedProfitLoss());
        map.put("unrealizedPLPercent", position.getUnrealizedProfitLossPercent());
        map.put("assetId", position.getAssetId());
        map.put("lastUpdated", position.getLastUpdated());
        
        return map;
    }

    /**
     * Convert a map to a StockPosition object
     * 
     * @param map The map representation of the position
     * @return StockPosition object
     */
    public static StockPosition mapToStockPosition(Map<String, Object> map) {
        // Create a JSON representation for the constructor
        JSONObject json = new JSONObject(map);
        return new StockPosition(json);
    }
}
