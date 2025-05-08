package org.example;

import com.sun.net.httpserver.*;
import org.json.JSONObject;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class Login {
    private static final String FIREBASE_API_KEY = "AIzaSyCMA1F8Xd4rCxGXssXIs8Da80qqP6jien8";

    public static void main(String[] args) throws Exception {
        int port = 8000;
        String portEnv = System.getenv("PORT");
        if (portEnv != null) {
            try { port = Integer.parseInt(portEnv); }
            catch (NumberFormatException e) { System.err.println("Invalid PORT env. Using default " + port); }
        } else if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { System.err.println("Invalid port. Using default " + port); }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/dologin", new LoginHandler());
        server.createContext("/deleteAccount", new DeleteAccountHandler());
        server.createContext("/register", new RegisterHandler());
        server.createContext("/forgot", new ForgotPasswordHandler());

        // Protect the home page
        HttpContext homeContext = server.createContext("/home.html", new StaticFileHandler());
        homeContext.getFilters().add(new AuthFilter());

        // Inline REST-based handler for /api/getData with proper response forwarding
        HttpContext apiDataContext = server.createContext("/api/getData", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
                String idToken = null, localId = null;
                if (cookieHeader != null) {
                    for (String c : cookieHeader.split(";")) {
                        String[] kv = c.trim().split("=", 2);
                        if (kv.length == 2) {
                            if ("idToken".equals(kv[0])) idToken = kv[1];
                            if ("localId".equals(kv[0])) localId = kv[1];
                        }
                    }
                }
                if (idToken == null || localId == null) {
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }

                String project = "cashclimb-d162c";
                String docPath = "Users/" + URLEncoder.encode(localId, "UTF-8");
                String restUrl = String.format(
                        "https://firestore.googleapis.com/v1/projects/%s/databases/(default)/documents/%s?key=%s",
                        project, docPath, FIREBASE_API_KEY
                );

                URL url = new URL(restUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + idToken);

                int code = conn.getResponseCode();
                InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] out = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(code, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
        });
        apiDataContext.getFilters().add(new AuthFilter());

        HttpContext apiProfileContext = server.createContext("/api/profile", new ProfileHandler());
        apiProfileContext.getFilters().add(new AuthFilter());

        server.createContext("/api/chat", new ChatHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/wallets", new CryptoApiHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/expenses", new ExpensesHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/stocks", new StockHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/budgets", new BudgetHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/income", new IncomeHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/tips", new TipsHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/tax", new TaxHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/alerts", new AlertsHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/assets", new AssetsLiabilitiesHandler("Assets")).getFilters().add(new AuthFilter());
        server.createContext("/api/liabilities", new AssetsLiabilitiesHandler("Liabilities")).getFilters().add(new AuthFilter());
        server.createContext("/api/networth", new NetWorthHandler()).getFilters().add(new AuthFilter());
        server.createContext("/api/bills", new BillsHandler()).getFilters().add(new AuthFilter());

        server.createContext("/logout", new LogoutHandler());

        server.setExecutor(null);
        server.start();

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:" + port + "/home.html"));
        }
    }

    static class StaticFileHandler implements HttpHandler {
        private final String basePath = "src/main/resources";
        public void handle(HttpExchange exchange) throws IOException {
            String uriPath = exchange.getRequestURI().getPath();
            if (uriPath.equals("/")) uriPath = "/index.html";
            File file = new File(basePath + uriPath).getCanonicalFile();
            if (!file.getPath().startsWith(new File(basePath).getCanonicalPath())) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            if (!file.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String mime = "application/octet-stream";
            if (uriPath.endsWith(".html")) mime = "text/html";
            else if (uriPath.endsWith(".css")) mime = "text/css";
            else if (uriPath.endsWith(".js")) mime = "application/javascript";
            else if (uriPath.endsWith(".png")) mime = "image/png";
            else if (uriPath.endsWith(".jpg") || uriPath.endsWith(".jpeg")) mime = "image/jpeg";
            else if (uriPath.endsWith(".svg")) mime = "image/svg+xml";
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
            StringBuilder buf = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) buf.append(line);
            String[] pairs = buf.toString().split("&");
            String email = "", password = "";
            for (String pair : pairs) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    String key = URLDecoder.decode(parts[0], "UTF-8");
                    String val = URLDecoder.decode(parts[1], "UTF-8");
                    if ("email".equals(key)) email = val;
                    if ("password".equals(key)) password = val;
                }
            }

            URL url = new URL("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            String payload = String.format("{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}", email, password);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder resp = new StringBuilder(); while ((line = in.readLine()) != null) resp.append(line);
                JSONObject json = new JSONObject(resp.toString());
                String idToken = json.getString("idToken"), localId = json.getString("localId");
                exchange.getResponseHeaders().add("Set-Cookie", "session=valid; Path=/");
                exchange.getResponseHeaders().add("Set-Cookie", "idToken=" + idToken + "; Path=/; HttpOnly");
                exchange.getResponseHeaders().add("Set-Cookie", "localId=" + localId + "; Path=/; HttpOnly");
                exchange.getResponseHeaders().set("Location", "/home.html");
                exchange.sendResponseHeaders(302, -1);
            } else {
                String html = "<!DOCTYPE html><html><body><h2>Login Failed</h2><p>Invalid credentials.</p><a href='/index.html'>Try Again</a></body></html>";
                byte[] out = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, out.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
            }
        }
    }

    static class DeleteAccountHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            List<String> cookies = exchange.getRequestHeaders().get("Cookie");
            String idToken = null;
            if (cookies != null) {
                for (String cookie : cookies) {
                    for (String pair : cookie.split(";")) {
                        String[] kv = pair.trim().split("=", 2);
                        if (kv.length == 2 && "idToken".equals(kv[0])) {
                            idToken = kv[1];
                        }
                    }
                }
            }
            if (idToken == null) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }

            URL url = new URL("https://identitytoolkit.googleapis.com/v1/accounts:delete?key=" + FIREBASE_API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            String payload = String.format("{\"idToken\":\"%s\"}", idToken);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                exchange.getResponseHeaders().add("Set-Cookie", "session=; Path=/; Max-Age=0");
                exchange.getResponseHeaders().add("Set-Cookie", "idToken=; Path=/; Max-Age=0");
                exchange.getResponseHeaders().add("Set-Cookie", "localId=; Path=/; Max-Age=0");
                exchange.getResponseHeaders().set("Location", "/index.html");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

    static class RegisterHandler implements HttpHandler {
        private final String basePath = "src/main/resources";
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                File file = new File(basePath + "/register.html").getCanonicalFile();
                if (!file.isFile()) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) buf.append(line);
                String email="", password="", confirm="";
                for (String pair : buf.toString().split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length==2) {
                        String k = URLDecoder.decode(parts[0], "UTF-8");
                        String v = URLDecoder.decode(parts[1], "UTF-8");
                        if ("email".equals(k)) email = v;
                        if ("password".equals(k)) password = v;
                        if ("confirm".equals(k)) confirm = v;
                    }
                }
                if (!password.equals(confirm)) {
                    String html = "<!DOCTYPE html><html><body><h2>Passwords do not match</h2><a href='/register.html'>Try Again</a></body></html>";
                    byte[] out = html.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, out.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
                    return;
                }
                URL url = new URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + FIREBASE_API_KEY);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String payload = String.format("{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}", email, password);
                try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes(StandardCharsets.UTF_8)); }
                if (conn.getResponseCode()==200) {
                    exchange.getResponseHeaders().set("Location", "/index.html");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    String html = "<!DOCTYPE html><html><body><h2>Registration failed</h2><a href='/register.html'>Try Again</a></body></html>";
                    byte[] out = html.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, out.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
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
                File file = new File(basePath + "/forgot.html").getCanonicalFile();
                if (!file.isFile()) { exchange.sendResponseHeaders(404, -1); return; }
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
                StringBuilder buf = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) buf.append(line);
                String email="";
                for (String pair : buf.toString().split("&")) {
                    String[] parts = pair.split("=",2);
                    if (parts.length==2 && "email".equals(URLDecoder.decode(parts[0],"UTF-8"))) {
                        email = URLDecoder.decode(parts[1],"UTF-8");
                    }
                }
                URL url = new URL("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=" + FIREBASE_API_KEY);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String payload = String.format("{\"requestType\":\"PASSWORD_RESET\",\"email\":\"%s\"}", email);
                try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes(StandardCharsets.UTF_8)); }
                if (conn.getResponseCode()==200) {
                    String html = "<!DOCTYPE html><html><body><h2>Password reset email sent</h2><a href='/index.html'>Back to Login</a></body></html>";
                    byte[] out = html.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200,out.length);
                    try (OutputStream os=exchange.getResponseBody()) { os.write(out); }
                } else {
                    exchange.sendResponseHeaders(500, -1);
                }
            } else {
                exchange.sendResponseHeaders(405,-1);
            }
        }
    }

    static class AuthFilter extends Filter {
        @Override public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
            String cookies = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookies == null || !cookies.contains("session=valid")) {
                exchange.getResponseHeaders().set("Location", "/invalidSession.html");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            chain.doFilter(exchange);
        }
        @Override public String description() { return "Checks for a valid session cookie"; }
    }

    static class LogoutHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Set-Cookie","session=; Path=/; Max-Age=0");
            exchange.getResponseHeaders().set("Location","/index.html");
            exchange.sendResponseHeaders(302,-1);
        }
    }

}
