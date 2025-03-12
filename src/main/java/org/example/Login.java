package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.cloud.firestore.FieldValue;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.awt.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import com.sun.net.httpserver.*;

public class Login {
    // API keys - first try environment variables, then fallback to hardcoded values for local development 
    private static final String FIREBASE_API_KEY = System.getenv("FIREBASE_API_KEY") != null ? 
                                                  System.getenv("FIREBASE_API_KEY") : 
                                                  "AIzaSyCMA1F8Xd4rCxGXssXIs8Da80qqP6jien8";
    
    private static final String OPENAI_API_KEY = System.getenv("OPENAI_APIKEY") != null ?
                                                System.getenv("OPENAI_APIKEY") :
                                                "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";
    
    public static void main(String[] args) throws Exception {
        // Initialize Firebase Firestore
        try {
            // Initialize Firebase
            FirestoreService.initialize();
            System.out.println("Firebase initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
            // Continue anyway, as we can fall back to local storage
        }
        // Try to find an available port, starting with 8000 as default
        int port = 8000;
        int maxPortAttempts = 10; // Try up to 10 ports
        boolean serverStarted = false;
        HttpServer server = null;
        
        // Check environment variable for port
        String portEnv = System.getenv("PORT");
        if (portEnv != null) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT environment variable. Using default port " + port);
            }
        } else if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port specified. Using default port " + port);
            }
        }
        
        // Try to create server on available port
        for (int attempt = 0; attempt < maxPortAttempts; attempt++) {
            try {
                int currentPort = port + attempt;
                server = HttpServer.create(new InetSocketAddress("0.0.0.0", currentPort), 0);
                System.out.println("Server started on port " + currentPort);
                port = currentPort;
                serverStarted = true;
                break;
            } catch (java.net.BindException e) {
                System.out.println("Port " + (port + attempt) + " is in use, trying next port...");
            }
        }
        
        if (!serverStarted) {
            throw new IOException("Could not find an available port after " + maxPortAttempts + " attempts");
        }
        server.createContext("/", new StaticFileHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/register", new RegisterHandler());
        server.createContext("/forgot", new ForgotPasswordHandler());
        server.createContext("/api/getData", new HomeDataHandler());
        server.createContext("/api/chat", new ChatHandler());
        server.createContext("/api/wallets", new CryptoApiHandler());
        server.createContext("/api/wallet", new CryptoApiHandler()); // Add this for the real-time wallet endpoint
        server.createContext("/api/stocks", new StockApiHandler()); // Stock trading endpoint
        server.createContext("/api/portfolio", new StockApiHandler()); // Portfolio endpoint
        server.createContext("/api/orders", new StockApiHandler()); // Orders endpoint
        server.createContext("/api/account", new StockApiHandler()); // Account endpoint
        
        // Initialize budgeting features
        BudgetingDatabaseInitializer.initializeDatabase();
        
        // Register budgeting API endpoints
        server.createContext("/api/budgeting/dashboard", new BudgetingApiHandler());
        server.createContext("/api/budgeting/accounts", new BudgetingApiHandler());
        server.createContext("/api/budgeting/budgets", new BudgetingApiHandler());
        server.createContext("/api/budgeting/transactions", new BudgetingApiHandler());
        server.createContext("/api/budgeting/goals", new BudgetingApiHandler());
        server.setExecutor(null);
        server.start();

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
                    // Read the response to get user ID and other details
                    StringBuilder response = new StringBuilder();
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    String responseLine;
                    while ((responseLine = in.readLine()) != null) {
                        response.append(responseLine);
                    }
                    in.close();
                    
                    // Parse the response to get the user ID
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String userId = jsonResponse.getString("localId"); // Firebase UID
                    
                    // Create or update user in Firestore
                    if (FirestoreService.getInstance().isAvailable()) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("email", email);
                        userData.put("lastLogin", new Date().toString());
                        userData.put("loginCount", FieldValue.increment(1)); // Track login count
                        userData.put("lastIp", exchange.getRemoteAddress().getAddress().getHostAddress());
                        userData.put("active", true);
                        
                        // Get existing user data to preserve other fields
                        Map<String, Object> existingData = FirestoreService.getInstance().getUserProfile(userId);
                        if (!existingData.isEmpty()) {
                            // Preserve existing fields that we're not updating
                            for (Map.Entry<String, Object> entry : existingData.entrySet()) {
                                if (!userData.containsKey(entry.getKey())) {
                                    userData.put(entry.getKey(), entry.getValue());
                                }
                            }
                        }
                        
                        boolean success = FirestoreService.getInstance().saveUserProfile(userId, userData);
                        System.out.println("User profile "+(success ? "saved to" : "failed to save to")+" Firestore with ID: " + userId);
                        
                        // Also track login activity in a separate collection
                        Map<String, Object> loginActivity = new HashMap<>();
                        loginActivity.put("userId", userId);
                        loginActivity.put("email", email);
                        loginActivity.put("timestamp", new Date().toString());
                        loginActivity.put("ip", exchange.getRemoteAddress().getAddress().getHostAddress());
                        loginActivity.put("userAgent", exchange.getRequestHeaders().getFirst("User-Agent"));
                        loginActivity.put("type", "login");
                        
                        FirestoreService.getInstance().saveActivity(loginActivity);
                    }
                    
                    // Store user ID in a cookie or session
                    exchange.getResponseHeaders().set("Set-Cookie", "userId=" + userId + "; Path=/; HttpOnly");
                    exchange.getResponseHeaders().set("Location", "/home.html");
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
                    
                    // Track failed login attempt
                    if (FirestoreService.getInstance().isAvailable()) {
                        Map<String, Object> failedLoginActivity = new HashMap<>();
                        failedLoginActivity.put("email", email);
                        failedLoginActivity.put("timestamp", new Date().toString());
                        failedLoginActivity.put("ip", exchange.getRemoteAddress().getAddress().getHostAddress());
                        failedLoginActivity.put("userAgent", exchange.getRequestHeaders().getFirst("User-Agent"));
                        failedLoginActivity.put("type", "failed_login");
                        failedLoginActivity.put("reason", "Invalid credentials");
                        
                        FirestoreService.getInstance().saveActivity(failedLoginActivity);
                    }
                    
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
                    // Read the response to get user ID and other details
                    StringBuilder response = new StringBuilder();
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    String responseLine;
                    while ((responseLine = in.readLine()) != null) {
                        response.append(responseLine);
                    }
                    in.close();
                    
                    // Parse the response to get the user ID
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String userId = jsonResponse.getString("localId"); // Firebase UID
                    
                    // Create initial user profile in Firestore
                    if (FirestoreService.getInstance().isAvailable()) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("email", email);
                        userData.put("createdAt", new Date().toString());
                        userData.put("lastLogin", new Date().toString());
                        userData.put("loginCount", 1);
                        userData.put("lastIp", exchange.getRemoteAddress().getAddress().getHostAddress());
                        userData.put("active", true);
                        userData.put("userAgent", exchange.getRequestHeaders().getFirst("User-Agent"));

                        boolean success = FirestoreService.getInstance().saveUserProfile(userId, userData);
                        System.out.println("New user profile " + (success ? "created in" : "failed to save to") + " Firestore with ID: " + userId);

                        // Also track registration activity
                        Map<String, Object> registrationActivity = new HashMap<>();
                        registrationActivity.put("userId", userId);
                        registrationActivity.put("email", email);
                        registrationActivity.put("timestamp", new Date().toString());
                        registrationActivity.put("ip", exchange.getRemoteAddress().getAddress().getHostAddress());
                        registrationActivity.put("userAgent", exchange.getRequestHeaders().getFirst("User-Agent"));
                        registrationActivity.put("type", "registration");

                        FirestoreService.getInstance().saveActivity(registrationActivity);
                        
                        // Initialize budgeting data for the new user
                        try {
                            // Make sure global data is initialized first
                            BudgetingDatabaseInitializer.initializeDatabase();
                            
                            // Initialize empty user data structure without sample data
                            BudgetingDatabaseInitializer.initializeEmptyUserData(userId);
                            
                            System.out.println("Empty user data structure initialized for new user: " + userId);
                        } catch (Exception e) {
                            System.err.println("Failed to initialize budgeting data for user: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    
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
                    // Track password reset activity
                    if (FirestoreService.getInstance().isAvailable()) {
                        Map<String, Object> resetActivity = new HashMap<>();
                        resetActivity.put("email", email);
                        resetActivity.put("timestamp", new Date().toString());
                        resetActivity.put("ip", exchange.getRemoteAddress().getAddress().getHostAddress());
                        resetActivity.put("userAgent", exchange.getRequestHeaders().getFirst("User-Agent"));
                        resetActivity.put("type", "password_reset_request");
                        resetActivity.put("success", true);
                        
                        FirestoreService.getInstance().saveActivity(resetActivity);
                    }
                    
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
}
