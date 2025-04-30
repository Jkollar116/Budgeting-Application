package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public class TipsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        if (idToken == null || localId == null) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handlePost(exchange, idToken, localId);
        } else if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleGet(exchange, idToken, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
    private void handlePost(HttpExchange exchange, String idToken, String localId) throws IOException {
        String body = readAll(exchange.getRequestBody());
        String id = getJsonValue(body, "id");
        String category = getJsonValue(body, "category");
        String text = getJsonValue(body, "text");

        if (id.isEmpty() || category.isEmpty() || text.isEmpty()) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        String json = "{ \"fields\": {"
                + "\"id\": {\"stringValue\": \"" + id + "\"},"
                + "\"category\": {\"stringValue\": \"" + category + "\"},"
                + "\"text\": {\"stringValue\": \"" + text + "\"}"
                + "} }";

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/SavedTips/" + URLEncoder.encode(id, "UTF-8");

        HttpURLConnection conn = openFirestoreConnection(urlStr, "PATCH", idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        if (code == 200 || code == 201) {
            byte[] msg = "Tip saved.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, msg.length);
            exchange.getResponseBody().write(msg);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
        exchange.getResponseBody().close();
    }
    private void handleGet(HttpExchange exchange, String idToken, String localId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/SavedTips";

        HttpURLConnection conn = openFirestoreConnection(urlStr, "GET", idToken);
        int code = conn.getResponseCode();
        if (code == 200) {
            byte[] response = readAll(conn.getInputStream()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
        exchange.getResponseBody().close();
    }
    private HttpURLConnection openFirestoreConnection(String urlStr, String method, String idToken) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        if ("PATCH".equalsIgnoreCase(method)) {
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        } else if ("GET".equalsIgnoreCase(method)) {
            conn.setRequestMethod("GET");
        }
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        return conn;
    }
    private String readAll(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
    private static String extractCookieValue(String cookies, String name) {
        for (String part : cookies.split(";")) {
            String t = part.trim();
            if (t.startsWith(name + "=")) {
                return t.substring(name.length() + 1);
            }
        }
        return null;
    }
    private static String getJsonValue(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return "";
    }
}



