package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public class NetWorthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");

        if (idToken == null || localId == null) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readAll(exchange.getRequestBody());
            System.out.println("NetWorth POST Body: " + body);

            String netWorthStr = getJsonValue(body, "netWorth");
            String assetsStr = getJsonValue(body, "assetsLiabilities");
            String holdingsStr = getJsonValue(body, "stocksCrypto");

            if (netWorthStr.isEmpty() || assetsStr.isEmpty() || holdingsStr.isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            String json = "{ \"fields\": {"
                    + "\"netWorth\": {\"doubleValue\": " + netWorthStr + "},"
                    + "\"assetsLiabilities\": {\"doubleValue\": " + assetsStr + "},"
                    + "\"stocksCrypto\": {\"doubleValue\": " + holdingsStr + "}"
                    + "} }";

            String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/NetWorth/networth";

            HttpURLConnection conn = openFirestoreConnection(urlStr, "PATCH", idToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            System.out.println("Firestore NetWorth response code: " + code);

            if (code == 200 || code == 201) {
                byte[] msg = "Saved".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, msg.length);
                exchange.getResponseBody().write(msg);
            } else {
                System.out.println("Error: " + readAll(conn.getErrorStream()));
                exchange.sendResponseHeaders(code, -1);
            }
            exchange.getResponseBody().close();

        } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/NetWorth/networth";

            HttpURLConnection conn = openFirestoreConnection(urlStr, "GET", idToken);
            int code = conn.getResponseCode();
            if (code == 200) {
                byte[] response = readAll(conn.getInputStream()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } else {
                System.out.println("GET failed: " + readAll(conn.getErrorStream()));
                exchange.sendResponseHeaders(code, -1);
            }
            exchange.getResponseBody().close();
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }


    private static String extractCookieValue(String cookies, String name) {
        if (cookies == null) return null;
        for (String part : cookies.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        return null;
    }

    private static String getJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\"]+?)\"?[,}]");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private HttpURLConnection openFirestoreConnection(String urlStr, String method, String idToken) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        if ("PATCH".equalsIgnoreCase(method)) {
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        }
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        return conn;
    }
}

