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
            String method = exchange.getRequestMethod();

            switch (method) {
                case "GET":
                    handleGetWallets(exchange);
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
