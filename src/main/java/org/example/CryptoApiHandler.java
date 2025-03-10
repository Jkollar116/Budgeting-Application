package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CryptoApiHandler implements HttpHandler {
    // Keep in-memory wallet list for backward compatibility
    private static final List<Wallet> wallets = new ArrayList<>();
    
    // Services
    private final WalletService walletService;
    private final FirestoreService firestoreService;
    
    // Default user ID for testing when actual user ID is not available
    private static final String DEFAULT_USER_ID = "test_user";
    
    // Cookie name for user ID
    private static final String USER_ID_COOKIE = "userId";

    public CryptoApiHandler() {
        this.walletService = new WalletService();
        this.firestoreService = FirestoreService.getInstance();
        
        // Load any wallets from Firestore if available
        loadWalletsFromFirestore();
    }
    
    /**
     * Get the user ID from cookies or use default if not found
     */
    private String getUserId(HttpExchange exchange) {
        // Get cookies from request headers
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies != null) {
            for (String cookie : cookies.split(";")) {
                cookie = cookie.trim();
                if (cookie.startsWith(USER_ID_COOKIE + "=")) {
                    return cookie.substring(USER_ID_COOKIE.length() + 1);
                }
            }
        }
        
        // Fall back to default user ID if not found
        System.out.println("User ID not found in cookies, using default: " + DEFAULT_USER_ID);
        return DEFAULT_USER_ID;
    }
    
    /**
     * Loads wallets from Firestore database if available
     */
    private void loadWalletsFromFirestore() {
        if (firestoreService.isAvailable()) {
            try {
                // For initial loading we'll use the default user ID
                List<Map<String, Object>> storedWallets = firestoreService.getUserWallets(DEFAULT_USER_ID);
                
                if (!storedWallets.isEmpty()) {
                    // Clear in-memory wallets and reload from Firestore
                    wallets.clear();
                    
                    for (Map<String, Object> walletData : storedWallets) {
                        Wallet wallet = FirestoreService.mapToWallet(walletData);
                        wallets.add(wallet);
                    }
                    System.out.println("Loaded " + storedWallets.size() + " wallets from Firestore");
                }
            } catch (Exception e) {
                System.err.println("Error loading wallets from Firestore: " + e.getMessage());
                // Continue with in-memory wallets
            }
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String method = exchange.getRequestMethod();

            switch (method) {
                case "GET":
                    if (path.equals("/api/wallet") && query != null) {
                        handleGetWalletInfo(exchange);
                    } else {
                        handleGetWallets(exchange);
                    }
                    break;
                case "POST":
                    if (path.contains("/refresh")) {
                        handleRefreshWallet(exchange);
                    } else {
                        handleAddWallet(exchange);
                    }
                    break;
                default:
                    sendResponse(exchange, new JSONObject().put("error", "Method not allowed").toString(), 405);
            }
        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getMessage());
            sendResponse(exchange, new JSONObject().put("error", e.getMessage()).toString(), 500);
        }
    }

    /**
     * Handles requests to get real-time wallet information by address and crypto type
     * This endpoint serves the frontend's refreshWallet API call
     */
    private void handleGetWalletInfo(HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            String address = null;
            String type = null;
            
            // Parse query parameters
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        String key = keyValue[0];
                        String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        
                        if (key.equals("address")) {
                            address = value;
                        } else if (key.equals("type")) {
                            type = value;
                        }
                    }
                }
            }
            
            if (type == null) {
                sendResponse(exchange, new JSONObject()
                    .put("error", "Missing type parameter")
                    .toString(), 400);
                return;
            }
            
            WalletInfo info;
            
            // If address is not provided, just get price info
            if (address == null || address.isEmpty()) {
                // If only type is provided, get just the price information
                // This is useful for market data display without a specific wallet
                if (type.equals("BTC")) {
                    // For BTC, get a minimal wallet info with just price data
                    info = walletService.getBitcoinPriceInfo();
                } else if (type.equals("ETH")) {
                    // For ETH, get a minimal wallet info with just price data
                    info = walletService.getEthereumPriceInfo();
                } else {
                    sendResponse(exchange, new JSONObject()
                        .put("error", "Unsupported cryptocurrency type")
                        .toString(), 400);
                    return;
                }
                
                // Create a simplified response with just price data
                JSONObject response = new JSONObject();
                response.put("currentPrice", info.currentPrice());
                response.put("priceChange24h", info.priceChange24h());
                sendResponse(exchange, response.toString(), 200);
                return;
            }
            
            // If we have an address, get the full wallet info
            info = walletService.getWalletInfo(address, type);
            
            // Create response JSON with all wallet fields
            JSONObject response = new JSONObject();
            response.put("balance", info.balance());
            response.put("value", info.balance() * info.currentPrice());
            response.put("change24h", info.priceChange24h());
            response.put("currentPrice", info.currentPrice());
            
            JSONArray txArray = new JSONArray();
            for (Transaction tx : info.transactions()) {
                // Use the Transaction's built-in toJSON method
                txArray.put(tx.toJSON());
            }
            response.put("transactions", txArray);
            
            // Optionally, update the wallet in our in-memory list if it exists
            for (Wallet wallet : wallets) {
                if (wallet.getAddress().equals(address) && wallet.getCryptoType().equals(type)) {
                    wallet.updateInfo(info);
                    break;
                }
            }
            
            sendResponse(exchange, response.toString(), 200);
        } catch (Exception e) {
            System.err.println("Error getting wallet info: " + e.getMessage());
            e.printStackTrace();
            sendResponse(exchange, new JSONObject()
                .put("error", "Failed to get wallet info: " + e.getMessage())
                .toString(), 500);
        }
    }
    
    private void handleGetWallets(HttpExchange exchange) throws IOException {
        String userId = getUserId(exchange);
        
        // Try to get wallets from Firestore for this specific user
        if (firestoreService.isAvailable()) {
            try {
                List<Map<String, Object>> storedWallets = firestoreService.getUserWallets(userId);
                
                if (!storedWallets.isEmpty()) {
                    JSONArray walletsArray = new JSONArray();
                    
                    for (Map<String, Object> walletData : storedWallets) {
                        Wallet wallet = FirestoreService.mapToWallet(walletData);
                        // Convert to JSON
                        walletsArray.put(wallet.toJSON());
                    }
                    
                    sendResponse(exchange, walletsArray.toString(), 200);
                    return;
                }
            } catch (Exception e) {
                System.err.println("Error getting wallets from Firestore: " + e.getMessage());
                // Continue with in-memory wallets
            }
        }
        
        // Fall back to in-memory wallets if Firestore fails or is empty
        JSONArray walletsArray = new JSONArray();
        for (Wallet wallet : wallets) {
            walletsArray.put(wallet.toJSON());
        }
        sendResponse(exchange, walletsArray.toString(), 200);
    }

    private void handleAddWallet(HttpExchange exchange) throws IOException {
        String userId = getUserId(exchange);
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(requestBody);

        Wallet wallet = new Wallet(
                json.getString("label"),
                json.getString("address"),
                json.getString("cryptoType")
        );

        try {
            // Get real-time wallet info from blockchain API
            WalletInfo info = walletService.getWalletInfo(wallet.getAddress(), wallet.getCryptoType());
            wallet.updateInfo(info);
            
            // Add to in-memory list
            wallets.add(wallet);
            
            // Save to Firestore with the user's actual ID
            if (firestoreService.isAvailable()) {
                // Generate a wallet document ID
                if (wallet.getId() == null || wallet.getId().isEmpty()) {
                    wallet.setId(java.util.UUID.randomUUID().toString());
                }
                
                // Convert to map and save
                Map<String, Object> walletData = FirestoreService.walletToMap(wallet);
                boolean saveSuccess = firestoreService.saveWallet(userId, wallet.getId(), walletData);
                System.out.println("Wallet saved to Firestore for user " + userId + ": " + wallet.getId() + ", success: " + saveSuccess);
                System.out.println("Wallet data: " + walletData);
            }
            
            sendResponse(exchange, wallet.toJSON().toString(), 200);
        } catch (Exception e) {
            System.err.println("Error adding wallet: " + e.getMessage());
            sendResponse(exchange, new JSONObject()
                .put("error", "Failed to add wallet: " + e.getMessage())
                .toString(), 500);
        }
    }

    private void handleRefreshWallet(HttpExchange exchange) throws IOException {
        String userId = getUserId(exchange);
        String address = exchange.getRequestURI().getPath().split("/")[3];
        
        try {
            Wallet wallet = wallets.stream()
                    .filter(w -> w.getAddress().equals(address))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Wallet not found"));
    
            // Get updated info from blockchain API
            WalletInfo info = walletService.getWalletInfo(wallet.getAddress(), wallet.getCryptoType());
            wallet.updateInfo(info);
            
            // Also update in Firestore if available
            if (firestoreService.isAvailable()) {
                Map<String, Object> walletData = FirestoreService.walletToMap(wallet);
                firestoreService.saveWallet(userId, wallet.getId(), walletData);
                System.out.println("Wallet updated in Firestore for user " + userId + ": " + wallet.getId());
            }
    
            sendResponse(exchange, wallet.toJSON().toString(), 200);
        } catch (Exception e) {
            System.err.println("Error refreshing wallet: " + e.getMessage());
            sendResponse(exchange, new JSONObject()
                .put("error", "Failed to refresh wallet: " + e.getMessage())
                .toString(), 500);
        }
    }

    private void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
