// src/main/java/org/example/AssetsLiabilitiesHandler.java
package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class AssetsLiabilitiesHandler implements HttpHandler {
    private final String collectionName;

    public AssetsLiabilitiesHandler(String collectionName) {
        this.collectionName = collectionName;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            System.out.println(" Unauthorized " + collectionName + " attempt: missing cookies");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        String idToken  = extractCookieValue(cookies, "idToken");
        String localId  = extractCookieValue(cookies, "localId");
        System.out.println("=== AssetsLiabilitiesHandler(" + collectionName + ") " + method + " ===");

        if ("GET".equalsIgnoreCase(method)) {
            handleGet(exchange, idToken, localId);
        } else if ("POST".equalsIgnoreCase(method)) {
            handlePost(exchange, idToken, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleGet(HttpExchange exchange, String idToken, String localId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/"
                + "databases/(default)/documents/Users/" + localId + "/" + collectionName;
        System.out.println("→ FETCH GET " + urlStr);
        HttpURLConnection conn = openConnection(urlStr, "GET", idToken);
        int code = conn.getResponseCode();
        System.out.println("← Firestore GET response code: " + code);
        if (code == 200) {
            String body = readAll(conn.getInputStream());
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private void handlePost(HttpExchange exchange, String idToken, String localId) throws IOException {
        String reqBody = readAll(exchange.getRequestBody());
        System.out.println("→ RECEIVED POST payload for " + collectionName + ": " + reqBody);
        JSONObject req = new JSONObject(reqBody);
        String name = req.optString("name", "");
        double amount = req.has("amount") ? req.optDouble("amount", Double.NaN) : Double.NaN;
        if (name.isEmpty() || Double.isNaN(amount)) {
            System.out.println(" Invalid payload for " + collectionName);
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        JSONObject fields = new JSONObject();
        fields.put("name",   new JSONObject().put("stringValue", name));
        fields.put("amount", new JSONObject().put("doubleValue", amount));
        JSONObject payload = new JSONObject().put("fields", fields);
        byte[] out = payload.toString().getBytes(StandardCharsets.UTF_8);

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/"
                + "databases/(default)/documents/Users/" + localId + "/" + collectionName;
        System.out.println("→ FETCH POST " + urlStr);
        HttpURLConnection conn = openConnection(urlStr, "POST", idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(out);

        int code = conn.getResponseCode();
        System.out.println("← Firestore POST response code: " + code);
        if (code >= 200 && code < 300) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private HttpURLConnection openConnection(String urlStr, String method, String idToken) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        return conn;
    }

    private String readAll(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private String extractCookieValue(String cookies, String name) {
        for (String c : cookies.split(";")) {
            String[] kv = c.trim().split("=", 2);
            if (kv[0].equals(name) && kv.length == 2) return kv[1];
        }
        return null;
    }
}
