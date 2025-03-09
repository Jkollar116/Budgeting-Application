package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public class CryptoApiHandler implements HttpHandler {
    private static final List<Wallet> wallets = new ArrayList<>();
    private final WalletService walletService;

    public CryptoApiHandler() {
        this.walletService = new WalletService();
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
            
            if (address == null || type == null) {
                sendResponse(exchange, new JSONObject()
                    .put("error", "Missing address or type parameter")
                    .toString(), 400);
                return;
            }
            
            // Get real-time wallet info from blockchain API
            WalletInfo info = walletService.getWalletInfo(address, type);
            
            // Create response JSON with all required fields
            JSONObject response = new JSONObject();
            response.put("balance", info.balance());
            response.put("value", info.balance() * info.currentPrice());
            response.put("change24h", info.priceChange24h());
            
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
        JSONArray walletsArray = new JSONArray();
        for (Wallet wallet : wallets) {
            walletsArray.put(wallet.toJSON());
        }
        sendResponse(exchange, walletsArray.toString(), 200);
    }

    private void handleAddWallet(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(requestBody);

        Wallet wallet = new Wallet(
                json.getString("label"),
                json.getString("address"),
                json.getString("cryptoType")
        );

        WalletInfo info = walletService.getWalletInfo(wallet.getAddress(), wallet.getCryptoType());
        wallet.updateInfo(info);
        wallets.add(wallet);

        sendResponse(exchange, wallet.toJSON().toString(), 200);
    }

    private void handleRefreshWallet(HttpExchange exchange) throws IOException {
        String address = exchange.getRequestURI().getPath().split("/")[3];
        Wallet wallet = wallets.stream()
                .filter(w -> w.getAddress().equals(address))
                .findFirst()
                .orElseThrow(() -> new IOException("Wallet not found"));

        WalletInfo info = walletService.getWalletInfo(wallet.getAddress(), wallet.getCryptoType());
        wallet.updateInfo(info);

        sendResponse(exchange, wallet.toJSON().toString(), 200);
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
