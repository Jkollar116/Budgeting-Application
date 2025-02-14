package org.example;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.awt.*;
import com.sun.net.httpserver.*;

public class Login {
    private static final String FIREBASE_API_KEY = "AIzaSyAuSN96Py8Sr0xONWOG_LpOPSZ6EgerX98";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/register", new RegisterHandler());
        server.createContext("/forgot", new ForgotPasswordHandler());
        server.setExecutor(null);
        System.out.println("Server started at http://localhost:8000");
        server.start();

        // Automatically open the default browser to the server URL.
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:8000"));
        }
    }

    static class StaticFileHandler implements HttpHandler {
        // Serve files from "src/main/resources"
        private final String basePath = "src/main/resources";

        public void handle(HttpExchange exchange) throws IOException {
            String uriPath = exchange.getRequestURI().getPath();
            System.out.println("StaticFileHandler: Request URI: " + uriPath);
            if (uriPath.equals("/")) {
                uriPath = "/index.html";
            }
            File file = new File(basePath + uriPath).getCanonicalFile();
            System.out.println("StaticFileHandler: Looking for file: " + file.getAbsolutePath());
            if (!file.getPath().startsWith(new File(basePath).getCanonicalPath())) {
                System.out.println("StaticFileHandler: Forbidden file access attempted.");
                exchange.sendResponseHeaders(403, 0);
                exchange.getResponseBody().close();
                return;
            }
            if (!file.isFile()) {
                System.out.println("StaticFileHandler: File not found: " + file.getAbsolutePath());
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
            System.out.println("StaticFileHandler: Served file: " + file.getAbsolutePath());
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("LoginHandler: Received request: " + exchange.getRequestMethod());
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Read form data
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                System.out.println("LoginHandler: Received form data: " + formData);

                // Parse the form data manually
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
                System.out.println("LoginHandler: Parsed email: " + email);
                System.out.println("LoginHandler: Parsed password: " + password);

                // Call Firebase signInWithPassword endpoint
                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_API_KEY;
                System.out.println("LoginHandler: Firebase URL: " + firebaseUrl);
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"returnSecureToken\":true}";
                System.out.println("LoginHandler: JSON payload: " + jsonPayload);
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();

                int responseCode = conn.getResponseCode();
                System.out.println("LoginHandler: Firebase response code: " + responseCode);

                if (responseCode == 200) {
                    System.out.println("LoginHandler: Login successful. Redirecting to home.html.");
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
                    System.out.println("LoginHandler: Firebase error response: " + response.toString());
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
                System.out.println("LoginHandler: Invalid request method: " + exchange.getRequestMethod());
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        private final String basePath = "src/main/resources";

        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("RegisterHandler: Received request: " + exchange.getRequestMethod());
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Serve the registration page.
                String fileName = "/register.html";
                File file = new File(basePath + fileName).getCanonicalFile();
                System.out.println("RegisterHandler: Looking for file: " + file.getAbsolutePath());
                if (!file.isFile()) {
                    System.out.println("RegisterHandler: File not found: " + file.getAbsolutePath());
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
                System.out.println("RegisterHandler: Served file: " + file.getAbsolutePath());
                return;
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Process registration data.
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                System.out.println("RegisterHandler: Received form data: " + formData);

                // Parse form data manually.
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
                System.out.println("RegisterHandler: Parsed email: " + email);
                System.out.println("RegisterHandler: Parsed password: " + password);
                System.out.println("RegisterHandler: Parsed confirm: " + confirm);

                // Check if password and confirm match.
                if (!password.equals(confirm)) {
                    System.out.println("RegisterHandler: Passwords do not match.");
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

                // Call Firebase signUp endpoint.
                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + FIREBASE_API_KEY;
                System.out.println("RegisterHandler: Firebase URL: " + firebaseUrl);
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"returnSecureToken\":true}";
                System.out.println("RegisterHandler: JSON payload: " + jsonPayload);
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();

                int responseCode = conn.getResponseCode();
                System.out.println("RegisterHandler: Firebase response code: " + responseCode);

                if (responseCode == 200) {
                    System.out.println("RegisterHandler: Registration successful. Redirecting to login page.");
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
                    System.out.println("RegisterHandler: Firebase error response: " + errorResponse);
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
                System.out.println("RegisterHandler: Invalid request method: " + exchange.getRequestMethod());
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class ForgotPasswordHandler implements HttpHandler {
        private final String basePath = "src/main/resources";
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("ForgotPasswordHandler: Received request: " + exchange.getRequestMethod());
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Serve the forgot password page.
                String fileName = "/forgot.html";
                File file = new File(basePath + fileName).getCanonicalFile();
                System.out.println("ForgotPasswordHandler: Looking for file: " + file.getAbsolutePath());
                if (!file.isFile()) {
                    System.out.println("ForgotPasswordHandler: File not found: " + file.getAbsolutePath());
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
                System.out.println("ForgotPasswordHandler: Served file: " + file.getAbsolutePath());
                return;
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Process forgot password form: send password reset email.
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                System.out.println("ForgotPasswordHandler: Received form data: " + formData);

                // Parse form data manually.
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
                System.out.println("ForgotPasswordHandler: Parsed email: " + email);

                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=" + FIREBASE_API_KEY;
                System.out.println("ForgotPasswordHandler: Firebase URL: " + firebaseUrl);
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"" + email + "\"}";
                System.out.println("ForgotPasswordHandler: JSON payload: " + jsonPayload);
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();

                int responseCode = conn.getResponseCode();
                System.out.println("ForgotPasswordHandler: Firebase response code: " + responseCode);

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
                    System.out.println("ForgotPasswordHandler: Firebase error response: " + response.toString());
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
                System.out.println("ForgotPasswordHandler: Invalid request method: " + exchange.getRequestMethod());
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}
