package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.Filter;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.awt.Desktop;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Login {
    private static final String FIREBASE_API_KEY = "AIzaSyCMA1F8Xd4rCxGXssXIs8Da80qqP6jien8";

    public static void main(String[] args) throws Exception {
        System.out.println("Starting main method...");
        int port = 8000;
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

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        System.out.println("Server created on port: " + port);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/doLogin", new LoginHandler());
        server.createContext("/register", new RegisterHandler());
        server.createContext("/forgot", new ForgotPasswordHandler());

        HttpContext homeContext = server.createContext("/home.html", new StaticFileHandler());
        homeContext.getFilters().add(new AuthFilter());

        HttpContext apiDataContext = server.createContext("/api/getData", new HomeDataHandler());
        apiDataContext.getFilters().add(new AuthFilter());

        HttpContext apiChatContext = server.createContext("/api/chat", new ChatHandler());
        apiChatContext.getFilters().add(new AuthFilter());

        HttpContext apiWalletContext = server.createContext("/api/wallets", new CryptoApiHandler());
        apiWalletContext.getFilters().add(new AuthFilter());

        HttpContext apiExpensesContext = server.createContext("/api/expenses", new ExpensesHandler());
        apiExpensesContext.getFilters().add(new AuthFilter());

        server.createContext("/logout", new LogoutHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server started. Listening on port: " + port);

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:" + port));
        }
    }

    static class StaticFileHandler implements HttpHandler {
        private final String basePath = "src/main/resources";

        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("StaticFileHandler invoked with method: " + exchange.getRequestMethod());
            String uriPath = exchange.getRequestURI().getPath();
            System.out.println("Requested path: " + uriPath);

            if (uriPath.equals("/")) {
                uriPath = "/index.html";
            }

            File file = new File(basePath + uriPath).getCanonicalFile();
            if (!file.getPath().startsWith(new File(basePath).getCanonicalPath())) {
                exchange.sendResponseHeaders(403, 0);
                exchange.getResponseBody().close();
                System.out.println("403 Forbidden (path outside base directory)");
                return;
            }

            if (!file.isFile()) {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
                System.out.println("404 Not Found: " + file.getAbsolutePath());
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
            System.out.println("Served file: " + file.getAbsolutePath() + " with mime: " + mime);
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("LoginHandler invoked with method: " + exchange.getRequestMethod());

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                System.out.println("Login form data: " + formData);

                String email = "", password = "";
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
                System.out.println("Parsed email: " + email);

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
                System.out.println("Firebase signInWithPassword response code: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder firebaseResponse = new StringBuilder();
                    String respLine;
                    while ((respLine = in.readLine()) != null) {
                        firebaseResponse.append(respLine);
                    }
                    in.close();
                    String responseStr = firebaseResponse.toString();

                    String idToken = "";
                    String localId = "";

                    Pattern idPattern = Pattern.compile("\"idToken\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher idMatcher = idPattern.matcher(responseStr);
                    if (idMatcher.find()) {
                        idToken = idMatcher.group(1);
                    }

                    Pattern localIdPattern = Pattern.compile("\"localId\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher localIdMatcher = localIdPattern.matcher(responseStr);
                    if (localIdMatcher.find()) {
                        localId = localIdMatcher.group(1);
                    }

                    exchange.getResponseHeaders().add("Set-Cookie", "session=valid; Path=/");
                    exchange.getResponseHeaders().add("Set-Cookie", "idToken=" + idToken + "; Path=/");
                    exchange.getResponseHeaders().add("Set-Cookie", "localId=" + localId + "; Path=/");
                    exchange.getResponseHeaders().set("Location", "/home.html");
                    exchange.sendResponseHeaders(302, -1);
                    System.out.println("Login success. Set session, idToken and localId cookies and redirecting to /home.html");

                } else {
                    InputStream errorStream = conn.getErrorStream();
                    InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    BufferedReader in = new BufferedReader(isrError);
                    StringBuilder response = new StringBuilder();
                    String line2;
                    while ((line2 = in.readLine()) != null) {
                        response.append(line2);
                    }
                    in.close();
                    System.out.println("Firebase login error response: " + response);

                    String userMessage = "Invalid email or password. Please try again.";
                    String errorHtml =
                            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Login Error</title>"
                                    + "<link rel=\"stylesheet\" href=\"style.css\"></head>"
                                    + "<body><div class=\"login-container\"><h2>Login Error</h2><p>"
                                    + userMessage + "</p><a href='/index.html'>Try Again</a></div></body></html>";

                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }

            } else {
                // All non-POST requests to /login => 405
                exchange.sendResponseHeaders(405, -1);
                System.out.println("LoginHandler: GET or other method => 405");
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        private final String basePath = "src/main/resources";

        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("RegisterHandler invoked with method: " + exchange.getRequestMethod());

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String fileName = "/register.html";
                File file = new File(basePath + fileName).getCanonicalFile();
                if (!file.isFile()) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    System.out.println("RegisterHandler: file not found");
                    return;
                }

                String mime = "text/html";
                exchange.getResponseHeaders().set("Content-Type", mime);
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                System.out.println("Served register.html");

            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                System.out.println("Register form data: " + formData);

                String email = "", password = "", confirm = "";
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
                System.out.println("Parsed registration email: " + email);

                if (!password.equals(confirm)) {
                    String errorHtml =
                            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Registration Error</title>"
                                    + "<link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\">"
                                    + "<h2>Registration Error</h2><p>Passwords do not match. Please try again.</p>"
                                    + "<a href='/register.html'>Try Again</a></div></body></html>";

                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                    System.out.println("Passwords did not match");
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
                System.out.println("Firebase signUp response code: " + responseCode);

                if (responseCode == 200) {
                    exchange.getResponseHeaders().set("Location", "/index.html");
                    exchange.sendResponseHeaders(302, -1);
                    System.out.println("Registration success. Redirecting to /index.html");
                } else {
                    InputStream errorStream = conn.getErrorStream();
                    InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    BufferedReader in = new BufferedReader(isrError);
                    StringBuilder response = new StringBuilder();
                    String line2;
                    while ((line2 = in.readLine()) != null) {
                        response.append(line2);
                    }
                    in.close();
                    String errorResponse = response.toString();
                    System.out.println("Firebase registration error: " + errorResponse);

                    String userMessage = "Registration failed. Please try again.";
                    if (errorResponse.contains("EMAIL_EXISTS")) {
                        userMessage = "This email is already registered. Please use a different email.";
                    }
                    String errorHtml =
                            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Registration Error</title>"
                                    + "<link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\">"
                                    + "<h2>Registration Error</h2><p>" + userMessage + "</p><a href='/register.html'>Try Again</a></div></body></html>";

                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }

            } else {
                exchange.sendResponseHeaders(405, -1);
                System.out.println("RegisterHandler received a non-POST method");
            }
        }
    }

    static class ForgotPasswordHandler implements HttpHandler {
        private final String basePath = "src/main/resources";

        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("ForgotPasswordHandler invoked with method: " + exchange.getRequestMethod());

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String fileName = "/forgot.html";
                File file = new File(basePath + fileName).getCanonicalFile();
                if (!file.isFile()) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    System.out.println("forgot.html not found");
                    return;
                }
                String mime = "text/html";
                exchange.getResponseHeaders().set("Content-Type", mime);
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                System.out.println("Served forgot.html");

            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                System.out.println("ForgotPassword form data: " + formData);

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
                System.out.println("ForgotPassword email: " + email);

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
                System.out.println("Firebase password reset response code: " + responseCode);

                if (responseCode == 200) {
                    String successHtml =
                            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Password Reset</title>"
                                    + "<link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\">"
                                    + "<h2>Password Reset</h2><p>A password reset email has been sent. Please check your inbox.</p>"
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
                    String line2;
                    while ((line2 = in.readLine()) != null) {
                        response.append(line2);
                    }
                    in.close();
                    System.out.println("Firebase password reset error: " + response);

                    String userMessage = "Failed to send password reset email. Please try again.";
                    String errorHtml =
                            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Forgot Password Error</title>"
                                    + "<link rel=\"stylesheet\" href=\"style.css\"></head><body><div class=\"login-container\">"
                                    + "<h2>Forgot Password Error</h2><p>" + userMessage + "</p><a href='/forgot.html'>Try Again</a>"
                                    + "</div></body></html>";

                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }

            } else {
                exchange.sendResponseHeaders(405, -1);
                System.out.println("ForgotPasswordHandler received a non-POST method");
            }
        }
    }

    static class AuthFilter extends Filter {
        @Override
        public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
            System.out.println("AuthFilter triggered: checking cookies");
            String cookies = exchange.getRequestHeaders().getFirst("Cookie");
            System.out.println("Cookies: " + cookies);

            if (cookies == null || !cookies.contains("session=valid")) {
                System.out.println("No valid session cookie found, redirecting to /index.html");
                exchange.getResponseHeaders().set("Location", "/index.html");
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
            System.out.println("LogoutHandler invoked");
            exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Max-Age=0");
            exchange.getResponseHeaders().set("Location", "/index.html");
            exchange.sendResponseHeaders(302, -1);
            System.out.println("Session cleared. Redirected to /index.html");
        }
    }

    static class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("ChatHandler invoked: Protected route");
            String response = "Protected chat data";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class CryptoApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("CryptoApiHandler invoked: Protected route");
            String response = "Protected crypto data";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
