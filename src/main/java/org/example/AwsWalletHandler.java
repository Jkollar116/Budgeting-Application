package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Handler for AWS wallet operations
 */
public class AwsWalletHandler implements HttpHandler {
    private final AwsS3Service s3Service;
    private final WalletService walletService;

    public AwsWalletHandler() {
        this.s3Service = new AwsS3Service();
        this.walletService = new WalletService();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Add CORS headers for all requests
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // Handle preflight OPTIONS request
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            // Get userId from request parameter (normally would come from authentication)
            String userId = exchange.getRequestURI().getQuery();
            if (userId == null || !userId.startsWith("userId=")) {
                sendErrorResponse(exchange, 400, "Missing userId parameter");
                return;
            }
            userId = userId.substring(7); // Remove "userId=" prefix

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/aws-wallets") && method.equals("GET")) {
                handleListWallets(exchange, userId);
            } else if (path.equals("/aws-wallets") && method.equals("POST")) {
                handleAddWallet(exchange, userId);
            } else if (path.startsWith("/aws-wallets/") && method.equals("GET")) {
                String walletId = path.substring("/aws-wallets/".length());
                handleGetWallet(exchange, userId, walletId);
            } else if (path.startsWith("/aws-wallets/") && method.equals("POST")) {
                String walletId = path.substring("/aws-wallets/".length());
                if (path.endsWith("/refresh")) {
                    handleRefreshWallet(exchange, userId, walletId);
                } else {
                    sendErrorResponse(exchange, 400, "Invalid operation");
                }
            } else if (path.startsWith("/aws-wallets/") && method.equals("DELETE")) {
                String walletId = path.substring("/aws-wallets/".length());
                handleDeleteWallet(exchange, userId, walletId);
            } else {
                sendErrorResponse(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleListWallets(HttpExchange exchange, String userId) throws IOException {
        try {
            JSONObject walletList = s3Service.listObjects(userId, "wallets");
            JSONArray walletsArray = new JSONArray();

            for (String walletId : walletList.keySet()) {
                JSONObject wallet = s3Service.getObject(userId, "wallets", walletId);
                wallet.put("id", walletId);
                walletsArray.put(wallet);
            }

            sendSuccessResponse(exchange, walletsArray.toString());
        } catch (Exception e) {
            sendErrorResponse(exchange, 500, "Failed to list wallets: " + e.getMessage());
        }
    }

    private void handleAddWallet(HttpExchange exchange, String userId) throws IOException {
        try {
            // Read request body
            String requestBody = readRequestBody(exchange);
            JSONObject json = new JSONObject(requestBody);

            // Create wallet object
            Wallet wallet = new Wallet(
                    json.getString("label"),
                    json.getString("address"),
                    json.getString("cryptoType")
            );

            // Get wallet info from blockchain
            WalletInfo info = walletService.getWalletInfo(wallet.getAddress(), wallet.getCryptoType());
            wallet.updateInfo(info);

            // Convert to JSON and store in S3
            JSONObject walletJson = wallet.toJSON();
            String walletId = s3Service.storeObject(userId, "wallets", walletJson);

            // Add wallet ID to response
            walletJson.put("id", walletId);

            sendSuccessResponse(exchange, walletJson.toString());
        } catch (Exception e) {
            sendErrorResponse(exchange, 500, "Failed to add wallet: " + e.getMessage());
        }
    }

    private void handleGetWallet(HttpExchange exchange, String userId, String walletId) throws IOException {
        try {
            JSONObject walletJson = s3Service.getObject(userId, "wallets", walletId);
            walletJson.put("id", walletId);
            sendSuccessResponse(exchange, walletJson.toString());
        } catch (Exception e) {
            sendErrorResponse(exchange, 500, "Failed to get wallet: " + e.getMessage());
        }
    }

    private void handleRefreshWallet(HttpExchange exchange, String userId, String walletId) throws IOException {
        try {
            // Get existing wallet from S3
            JSONObject walletJson = s3Service.getObject(userId, "wallets", walletId);

            // Create wallet object
            Wallet wallet = new Wallet(
                    walletJson.getString("label"),
                    walletJson.getString("address"),
                    walletJson.getString("cryptoType")
            );

            // Get updated wallet info from blockchain
            WalletInfo info = walletService.getWalletInfo(wallet.getAddress(), wallet.getCryptoType());
            wallet.updateInfo(info);

            // Update wallet in S3
            JSONObject updatedWalletJson = wallet.toJSON();
            s3Service.updateObject(userId, "wallets", walletId, updatedWalletJson);

            // Add wallet ID to response
            updatedWalletJson.put("id", walletId);

            sendSuccessResponse(exchange, updatedWalletJson.toString());
        } catch (Exception e) {
            sendErrorResponse(exchange, 500, "Failed to refresh wallet: " + e.getMessage());
        }
    }

    private void handleDeleteWallet(HttpExchange exchange, String userId, String walletId) throws IOException {
        try {
            s3Service.deleteObject(userId, "wallets", walletId);
            sendSuccessResponse(exchange, new JSONObject().put("success", true).toString());
        } catch (Exception e) {
            sendErrorResponse(exchange, 500, "Failed to delete wallet: " + e.getMessage());
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            return requestBody.toString();
        }
    }

    private void sendSuccessResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
        JSONObject error = new JSONObject();
        error.put("error", errorMessage);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = error.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
