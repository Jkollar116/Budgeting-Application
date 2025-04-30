package org.example;

import com.sun.net.httpserver.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.awt.Desktop;
import java.util.List;

public class Login {
    private static final String FIREBASE_API_KEY = "AIzaSyCMA1F8Xd4rCxGXssXIs8Da80qqP6jien8";
    public static void main(String[] args) throws Exception {
        int port = 8000;
        String portEnv = System.getenv("PORT");
        if (portEnv != null) {
            try { port = Integer.parseInt(portEnv); } catch (NumberFormatException e) { System.err.println("Invalid PORT env. Using default " + port); }
        } else if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException e) { System.err.println("Invalid port. Using default " + port); }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/dologin", new LoginHandler());
        server.createContext("/deleteAccount", new DeleteAccountHandler());
        server.createContext("/register", new RegisterHandler());
        server.createContext("/forgot", new ForgotPasswordHandler());
        HttpContext homeContext = server.createContext("/home.html", new StaticFileHandler());
        homeContext.getFilters().add(new AuthFilter());
        HttpContext apiDataContext = server.createContext("/api/getData", new HomeDataHandler());
        apiDataContext.getFilters().add(new AuthFilter());
        HttpContext apiChatContext = server.createContext("/api/chat", new ChatHandler());
        apiChatContext.getFilters().add(new AuthFilter());
        HttpContext apiWalletsContext = server.createContext("/api/wallets", new CryptoApiHandler());
        apiWalletsContext.getFilters().add(new AuthFilter());
        HttpContext apiWalletContext = server.createContext("/api/wallet", new CryptoApiHandler());
        apiWalletContext.getFilters().add(new AuthFilter());
        HttpContext apiExpensesContext = server.createContext("/api/expenses", new ExpensesHandler());
        apiExpensesContext.getFilters().add(new AuthFilter());
        HttpContext apiStockAccountContext = server.createContext("/api/stocks/account", new StockHandler());
        apiStockAccountContext.getFilters().add(new AuthFilter());
        HttpContext apiStockPortfolioContext = server.createContext("/api/stocks/portfolio", new StockHandler());
        apiStockPortfolioContext.getFilters().add(new AuthFilter());
        HttpContext apiStockOrdersContext = server.createContext("/api/stocks/orders", new StockHandler());
        apiStockOrdersContext.getFilters().add(new AuthFilter());
        HttpContext apiStockSymbolContext = server.createContext("/api/stocks/", new StockHandler());
        apiStockSymbolContext.getFilters().add(new AuthFilter());
        server.createContext("/logout", new LogoutHandler());
        HttpContext apiBudgetsContext = server.createContext("/api/budgets", new BudgetHandler());
        apiBudgetsContext.getFilters().add(new AuthFilter());
        HttpContext apiIncomeContext = server.createContext("/api/income", new IncomeHandler());
        apiIncomeContext.getFilters().add(new AuthFilter());
        HttpContext assetsLiabilitiesPage = server.createContext("/assetsLiabilities.html", new StaticFileHandler());
        assetsLiabilitiesPage.getFilters().add(new AuthFilter());
        HttpContext apiTipsContext = server.createContext("/api/tips", new TipsHandler());
        apiTipsContext.getFilters().add(new AuthFilter());

        HttpContext apiAssets = server.createContext("/api/assets", new AssetsLiabilitiesHandler("Assets"));
        apiAssets.getFilters().add(new AuthFilter());

        HttpContext apiLiabilities = server.createContext("/api/liabilities", new AssetsLiabilitiesHandler("Liabilities"));
        apiLiabilities.getFilters().add(new AuthFilter());

        HttpContext cryptoContext = server.createContext("/crypto.html", new StaticFileHandler());
        cryptoContext.getFilters().add(new AuthFilter());
        HttpContext stocksContext = server.createContext("/stocks.html", new StaticFileHandler());
        stocksContext.getFilters().add(new AuthFilter());
        HttpContext expensesContext = server.createContext("/expenses.html", new StaticFileHandler());
        expensesContext.getFilters().add(new AuthFilter());
        HttpContext incomeContext = server.createContext("/income.html", new StaticFileHandler());
        incomeContext.getFilters().add(new AuthFilter());
        HttpContext profileContext = server.createContext("/profile.html", new StaticFileHandler());
        profileContext.getFilters().add(new AuthFilter());
        HttpContext taxContext = server.createContext("/tax.html", new StaticFileHandler());
        taxContext.getFilters().add(new AuthFilter());
        HttpContext settingsContext = server.createContext("/settings.html", new StaticFileHandler());
        settingsContext.getFilters().add(new AuthFilter());
        HttpContext chatContext = server.createContext("/chat.html", new StaticFileHandler());
        chatContext.getFilters().add(new AuthFilter());
        HttpContext leaderboardContext = server.createContext("/leaderboard.html", new StaticFileHandler());
        leaderboardContext.getFilters().add(new AuthFilter());
        HttpContext billsContext = server.createContext("/bills.html", new StaticFileHandler());
        billsContext.getFilters().add(new AuthFilter());
        HttpContext tipsContext = server.createContext("/tips.html", new StaticFileHandler());
        tipsContext.getFilters().add(new AuthFilter());
        HttpContext budgetContext = server.createContext("/budget.html", new StaticFileHandler());
        budgetContext.getFilters().add(new AuthFilter());
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
            if (uriPath.endsWith(".html")) mime = "text/html";
            else if (uriPath.endsWith(".css")) mime = "text/css";
            else if (uriPath.endsWith(".js")) mime = "application/javascript";
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
                String email = "", password = "";
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        String key = URLDecoder.decode(parts[0], "UTF-8");
                        String value = URLDecoder.decode(parts[1], "UTF-8");
                        if (key.equals("email")) email = value;
                        else if (key.equals("password")) password = value;
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
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder firebaseResponse = new StringBuilder();
                    String ln;
                    while ((ln = in.readLine()) != null) {
                        firebaseResponse.append(ln);
                    }
                    in.close();
                    JSONObject jsonObject = new JSONObject(firebaseResponse.toString());
                    String idToken = jsonObject.getString("idToken");
                    String localId = jsonObject.getString("localId");
                    exchange.getResponseHeaders().add("Set-Cookie", "session=valid; Path=/");
                    exchange.getResponseHeaders().add("Set-Cookie", "idToken=" + idToken + "; Path=/; HttpOnly");
                    exchange.getResponseHeaders().add("Set-Cookie", "localId=" + localId + "; Path=/; HttpOnly");
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
                    String userMessage = "Invalid email or password. Please try again.";
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Login Error</title><link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\"><h2>Login Error</h2><p>" + userMessage + "</p><a href='/index.html'>Try Again</a></div></body></html>";
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

    static class DeleteAccountHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Headers requestHeaders = exchange.getRequestHeaders();
                List<String> cookies = requestHeaders.get("Cookie");
                String idToken = null;

                // Extract idToken from cookie
                if (cookies != null) {
                    for (String cookie : cookies) {
                        String[] cookiePairs = cookie.split(";");
                        for (String pair : cookiePairs) {
                            String[] kv = pair.trim().split("=");
                            if (kv.length == 2 && kv[0].equals("idToken")) {
                                idToken = kv[1];
                            }
                        }
                    }
                }

                if (idToken == null) {
                    exchange.sendResponseHeaders(401, -1); // Unauthorized
                    return;
                }

                // Call Firebase REST API to delete the user
                String deleteUrl = "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=" + FIREBASE_API_KEY;
                URL url = new URL(deleteUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonPayload = "{\"idToken\":\"" + idToken + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    // Success - clear cookies and redirect
                    exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Max-Age=0");
                    exchange.getResponseHeaders().add("Set-Cookie", "idToken=; Path=/; Max-Age=0");
                    exchange.getResponseHeaders().add("Set-Cookie", "localId=; Path=/; Max-Age=0");
                    exchange.getResponseHeaders().set("Location", "/index.html");
                    exchange.sendResponseHeaders(302, -1); // Redirect to login
                } else {
                    InputStream errorStream = conn.getErrorStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    br.close();
                    System.err.println("Firebase delete failed: " + errorResponse.toString());
                    exchange.sendResponseHeaders(500, -1); // Internal Server Error
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
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
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                String email = "", password = "", confirm = "";
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        String key = URLDecoder.decode(parts[0], "UTF-8");
                        String value = URLDecoder.decode(parts[1], "UTF-8");
                        if (key.equals("email")) email = value;
                        else if (key.equals("password")) password = value;
                        else if (key.equals("confirm")) confirm = value;
                    }
                }
                if (!password.equals(confirm)) {
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Registration Error</title><link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\"><h2>Registration Error</h2><p>Passwords do not match. Please try again.</p><a href='/register.html'>Try Again</a></div></body></html>";
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
                } else {
                    InputStream errorStream = conn.getErrorStream();
                    if (errorStream != null) {
                        InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                        BufferedReader in = new BufferedReader(isrError);
                        StringBuilder response = new StringBuilder();
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();
                    }
                    String userMessage = "Registration failed. Please try again.";
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Registration Error</title><link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\"><h2>Registration Error</h2><p>" + userMessage + "</p><a href='/register.html'>Try Again</a></div></body></html>";
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
                        if (key.equals("email")) email = value;
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
                    String successHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Password Reset</title><link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\"><h2>Password Reset</h2><p>A password reset email has been sent. Please check your inbox.</p><a href='/index.html'>Back to Login</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] successBytes = successHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, successBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(successBytes);
                    osResp.close();
                } else {
                    InputStream errorStream = conn.getErrorStream();
                    if (errorStream != null) {
                        InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                        BufferedReader in = new BufferedReader(isrError);
                        StringBuilder response = new StringBuilder();
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();
                        System.out.println("Firestore password reset error: " + response);
                    }
                    String userMessage = "Failed to send password reset email. Please try again.";
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Forgot Password Error</title><link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\"><h2>Forgot Password Error</h2><p>" + userMessage + "</p><a href='/forgot.html'>Try Again</a></div></body></html>";
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
    static class AuthFilter extends Filter {
        @Override
        public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
            String cookies = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookies == null || !cookies.contains("session=valid")) {
                exchange.getResponseHeaders().set("Location", "/invalidSession.html");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            chain.doFilter(exchange);
        }
        @Override
        public String description() {
            return "AuthFilter checks for a valid session cookie";
        }
    }
    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Max-Age=0");
            exchange.getResponseHeaders().set("Location", "/index.html");
            exchange.sendResponseHeaders(302, -1);
        }
    }

    // Using the full CryptoApiHandler implementation from org.example.CryptoApiHandler
}
