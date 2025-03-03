package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.awt.*;
import com.sun.net.httpserver.*;
import org.json.JSONObject;

public class Login {
    private static final String FIREBASE_API_KEY = "AIzaSyCMA1F8Xd4rCxGXssXIs8Da80qqP6jien8";

    public static void main(String[] args) throws Exception {
            int port = 8000;
            if (args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port specified. Using default port " + port);
                }
            }

            // Print environment variables for debugging (redacted to avoid showing sensitive info)
            String awsRegion = System.getenv("AWS_REGION");
            System.out.println("AWS Region: " + (awsRegion != null ? awsRegion : "Not set"));
            System.out.println("AWS S3 Bucket: " + (System.getenv("AWS_S3_BUCKET") != null ? System.getenv("AWS_S3_BUCKET") : "Not set"));
            System.out.println("AWS Access Key: " + (System.getenv("AWS_ACCESS_KEY_ID") != null ? "Set" : "Not set"));
            System.out.println("AWS Secret Key: " + (System.getenv("AWS_SECRET_ACCESS_KEY") != null ? "Set" : "Not set"));

            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

            // Static file and basic authentication handlers
            server.createContext("/", new StaticFileHandler());
            server.createContext("/login", new LoginHandler());
            server.createContext("/register", new RegisterHandler());
            server.createContext("/forgot", new ForgotPasswordHandler());

            // API handlers
            server.createContext("/api/getData", new HomeDataHandler());
            server.createContext("/api/chat", new ChatHandler());
            server.createContext("/api/wallets", new CryptoApiHandler());
            server.createContext("/api/crypto/prices", new CryptoPriceHandler());

            // AWS configuration endpoint - make sure this path is unique
            server.createContext("/api/aws-config", new AwsConfigHandler());

            // AWS wallet handler - make sure this path doesn't conflict with any other path
            // The context path must be unique, so we're using /api/aws-wallets to avoid conflicts
            server.createContext("/api/aws-wallets", new AwsWalletHandler());

            server.setExecutor(null);
            server.start();

            System.out.println("Server started on port " + port);
            System.out.println("Open http://localhost:" + port + " in your browser");

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI("http://localhost:" + port));
            }
        }



        static class StaticFileHandler implements HttpHandler {
        private final String basePath = "src/main/resources";

        public void handle(HttpExchange exchange) throws IOException {
            String uriPath = exchange.getRequestURI().getPath();
            if (uriPath.equals("/")) {
                uriPath = "/index.html";
            }
            File file = new File(basePath + uriPath).getCanonicalFile();
            if (!file.getPath().startsWith(new File(basePath).getCanonicalPath())) {
                exchange.sendResponseHeaders(403, 0);
                exchange.getResponseBody().close();
                return;
            }
            if (!file.isFile()) {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
                return;
            }
            String mime = "text/plain";
            if (uriPath.endsWith(".html")) {
                mime = "text/html";
            } else if (uriPath.endsWith(".css")) {
                mime = "text/css";
            } else if (uriPath.endsWith(".js")) {
                mime = "application/javascript";
            }
            exchange.getResponseHeaders().set("Content-Type", mime);
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                String email = "";
                String password = "";
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        String key = URLDecoder.decode(parts[0], "UTF-8");
                        String value = URLDecoder.decode(parts[1], "UTF-8");
                        if (key.equals("email")) {
                            email = value;
                        } else if (key.equals("password")) {
                            password = value;
                        }
                    }
                }
                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_API_KEY;
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"returnSecureToken\":true}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    // Parse the response to get the localId (the Firebase user ID)
                    InputStream inputStream = conn.getInputStream();
                    InputStreamReader isrSuc = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                    BufferedReader brSuc = new BufferedReader(isrSuc);
                    StringBuilder responseSuc = new StringBuilder();
                    String lineSuc;
                    while ((lineSuc = brSuc.readLine()) != null) {
                        responseSuc.append(lineSuc);
                    }
                    brSuc.close();

                    JSONObject responseJson = new JSONObject(responseSuc.toString());
                    String userId = responseJson.getString("localId");
                    String userEmail = responseJson.getString("email");

                    // Create an HTML response with JavaScript that stores the user ID
                    String successHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                            + "<title>Login Success</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\">"
                            + "<script src=\"profile.js\"></script>"
                            + "</head><body>"
                            + "<div class=\"login-container\"><h2>Login Successful</h2>"
                            + "<p>Redirecting you to the dashboard...</p></div>"
                            + "<script>"
                            + "document.addEventListener('DOMContentLoaded', function() {"
                            + "  storeUserSession('" + userId + "', '" + userEmail + "');"
                            + "  setTimeout(function() { window.location.href = '/home.html'; }, 1000);"
                            + "});"
                            + "</script>"
                            + "</body></html>";

                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] successBytes = successHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, successBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(successBytes);
                    osResp.close();
                    return;
                }
                else {
                    InputStream errorStream = conn.getErrorStream();
                    InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    BufferedReader in = new BufferedReader(isrError);
                    StringBuilder response = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    String errorResponse = response.toString();
                    String userMessage = "Invalid email or password. Please try again.";
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Login Error</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Login Error</h2><p>" + userMessage + "</p>"
                            + "<a href='/index.html'>Try Again</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        private final String basePath = "src/main/resources";

        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String fileName = "/register.html";
                File file = new File(basePath + fileName).getCanonicalFile();
                if (!file.isFile()) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                String mime = "text/html";
                exchange.getResponseHeaders().set("Content-Type", mime);
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                String email = "";
                String password = "";
                String confirm = "";
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        String key = URLDecoder.decode(parts[0], "UTF-8");
                        String value = URLDecoder.decode(parts[1], "UTF-8");
                        if (key.equals("email")) {
                            email = value;
                        } else if (key.equals("password")) {
                            password = value;
                        } else if (key.equals("confirm")) {
                            confirm = value;
                        }
                    }
                }
                if (!password.equals(confirm)) {
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Registration Error</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Registration Error</h2><p>Passwords do not match. Please try again.</p>"
                            + "<a href='/register.html'>Try Again</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                    return;
                }
                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + FIREBASE_API_KEY;
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"returnSecureToken\":true}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    exchange.getResponseHeaders().set("Location", "/index.html");
                    exchange.sendResponseHeaders(302, -1);
                    return;
                } else {
                    InputStream errorStream = conn.getErrorStream();
                    InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    BufferedReader in = new BufferedReader(isrError);
                    StringBuilder response = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    String errorResponse = response.toString();
                    String userMessage = "Registration failed. Please try again.";
                    if (errorResponse.contains("EMAIL_EXISTS")) {
                        userMessage = "This email is already registered. Please use a different email.";
                    }
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Registration Error</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Registration Error</h2><p>" + userMessage + "</p>"
                            + "<a href='/register.html'>Try Again</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class ForgotPasswordHandler implements HttpHandler {
        private final String basePath = "src/main/resources";

        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String fileName = "/forgot.html";
                File file = new File(basePath + fileName).getCanonicalFile();
                if (!file.isFile()) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                String mime = "text/html";
                exchange.getResponseHeaders().set("Content-Type", mime);
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                String email = "";
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        String key = URLDecoder.decode(parts[0], "UTF-8");
                        String value = URLDecoder.decode(parts[1], "UTF-8");
                        if (key.equals("email")) {
                            email = value;
                        }
                    }
                }
                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=" + FIREBASE_API_KEY;
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"" + email + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String successHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Password Reset</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Password Reset</h2><p>A password reset email has been sent. Please check your inbox.</p>"
                            + "<a href='/index.html'>Back to Login</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] successBytes = successHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, successBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(successBytes);
                    osResp.close();
                } else {
                    InputStream errorStream = conn.getErrorStream();
                    InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    BufferedReader in = new BufferedReader(isrError);
                    StringBuilder response = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    String userMessage = "Failed to send password reset email. Please try again.";
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Forgot Password Error</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Forgot Password Error</h2><p>" + userMessage + "</p>"
                            + "<a href='/forgot.html'>Try Again</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class CryptoPriceHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            try {
                JSONObject response = new JSONObject();

                CoinMarketCapService cmcService = new CoinMarketCapService();

                String[] coins = {"BTC", "ETH"};

                for (String coin : coins) {
                    try {
                        CoinPrice price = cmcService.getPrice(coin);
                        JSONObject coinData = new JSONObject();
                        coinData.put("price", price.currentPrice());
                        coinData.put("change24h", price.priceChangePercentage24h());

                        if (coin.equals("BTC")) {
                            double marketCap = price.currentPrice() * 19000000; // ~19M circulating supply
                            coinData.put("marketCap", marketCap);
                            coinData.put("volume24h", marketCap * 0.03); // Typical BTC 24h volume is ~3% of market cap
                        } else if (coin.equals("ETH")) {
                            double marketCap = price.currentPrice() * 120000000; // ~120M circulating supply
                            coinData.put("marketCap", marketCap);
                            coinData.put("volume24h", marketCap * 0.04); // Typical ETH 24h volume is ~4% of market cap
                        }

                        response.put(coin, coinData);
                    } catch (Exception e) {
                        System.err.println("Error fetching price for " + coin + ": " + e.getMessage());
                    }
                }

                byte[] responseBytes = response.toString().getBytes("UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } catch (Exception e) {
                String errorResponse = new JSONObject()
                        .put("error", "Failed to fetch crypto prices: " + e.getMessage())
                        .toString();

                byte[] responseBytes = errorResponse.getBytes("UTF-8");
                exchange.sendResponseHeaders(500, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            }
        }
    }
    public static class UserProfileHandler implements HttpHandler {
        private final String profilesDirectory = "profiles";

        public UserProfileHandler() {
            // Create profiles directory if it doesn't exist
            File directory = new File(profilesDirectory);
            if (!directory.exists()) {
                directory.mkdirs();
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            // Handle preflight OPTIONS request
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                String userId = null;
                if (query != null && query.startsWith("userId=")) {
                    userId = query.substring(7); // Extract userId from query string
                }

                if (userId == null || userId.isEmpty()) {
                    sendResponse(exchange, 400, createErrorJson("User ID is required"));
                    return;
                }

                String method = exchange.getRequestMethod();
                if (method.equals("GET")) {
                    handleGetProfile(exchange, userId);
                } else if (method.equals("POST")) {
                    handleSaveProfile(exchange, userId);
                } else if (method.equals("DELETE")) {
                    handleDeleteProfile(exchange, userId);
                } else {
                    sendResponse(exchange, 405, createErrorJson("Method not allowed"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, createErrorJson("Server error: " + e.getMessage()));
            }
        }

        private void handleGetProfile(HttpExchange exchange, String userId) throws IOException {
            File profileFile = new File(profilesDirectory + "/" + userId + ".json");
            if (!profileFile.exists()) {
                // Return empty profile if file doesn't exist
                sendResponse(exchange, 200, "{}");
                return;
            }

            String profileJson = new String(Files.readAllBytes(profileFile.toPath()), StandardCharsets.UTF_8);
            sendResponse(exchange, 200, profileJson);
        }

        private void handleSaveProfile(HttpExchange exchange, String userId) throws IOException {
            StringBuilder requestBody = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    requestBody.append(line);
                }
            }

            // Validate JSON
            String profileJson = requestBody.toString();
            try {
                new JSONObject(profileJson);
            } catch (Exception e) {
                sendResponse(exchange, 400, createErrorJson("Invalid JSON"));
                return;
            }

            // Save profile to file
            Files.write(Paths.get(profilesDirectory + "/" + userId + ".json"), profileJson.getBytes(StandardCharsets.UTF_8));
            sendResponse(exchange, 200, "{\"success\": true}");
        }

        private void handleDeleteProfile(HttpExchange exchange, String userId) throws IOException {
            File profileFile = new File(profilesDirectory + "/" + userId + ".json");
            if (profileFile.exists()) {
                profileFile.delete();
            }
            sendResponse(exchange, 200, "{\"success\": true}");
        }

        private String createErrorJson(String message) {
            return "{\"error\": \"" + message.replace("\"", "\\\"") + "\"}";
        }

        private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }



    private static void initializeFirebase() {
        String apiKey = System.getenv("FIREBASE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = FIREBASE_API_KEY; // Fall back to your constant
        }
        System.out.println("Firebase initialized with API key: " + (apiKey.length() > 4 ?
                apiKey.substring(0, 4) + "..." : "not set"));
    }

    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"UP\",\"firebase\":\"connected\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
