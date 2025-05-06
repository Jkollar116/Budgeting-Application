package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public class TaxHandler implements HttpHandler {
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

        String result = getJsonValue(body, "result");
        String income = getJsonValue(body, "income");
        String filingStatus = getJsonValue(body, "filingStatus");
        String state = getJsonValue(body, "state");
        String taxCredits = getJsonValue(body, "taxCredits");
        String taxesPaid = getJsonValue(body, "taxesPaid");

        if (result.isEmpty() || income.isEmpty() || filingStatus.isEmpty() || state.isEmpty()) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        String json = "{ \"fields\": {"
                + "\"result\": {\"stringValue\": \"" + escapeJson(result) + "\"},"
                + "\"income\": {\"stringValue\": \"" + escapeJson(income) + "\"},"
                + "\"filingStatus\": {\"stringValue\": \"" + escapeJson(filingStatus) + "\"},"
                + "\"state\": {\"stringValue\": \"" + escapeJson(state) + "\"},"
                + "\"taxCredits\": {\"stringValue\": \"" + escapeJson(taxCredits) + "\"},"
                + "\"taxesPaid\": {\"stringValue\": \"" + escapeJson(taxesPaid) + "\"}"
                + "} }";

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/TaxHistory/latest";

        HttpURLConnection conn = openFirestoreConnection(urlStr, "PATCH", idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        if (code == 200 || code == 201) {
            byte[] msg = "Tax record saved.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, msg.length);
            exchange.getResponseBody().write(msg);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
        exchange.getResponseBody().close();
    }

    private void handleGet(HttpExchange exchange, String idToken, String localId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/TaxHistory";

        HttpURLConnection conn = openFirestoreConnection(urlStr, "GET", idToken);
        conn.setRequestProperty("Content-Type", "application/json");

        int code = conn.getResponseCode();
        System.out.println("Firestore GET response code: " + code);

        InputStream inputStream;
        try {
            inputStream = conn.getInputStream();
        } catch (IOException e) {
            inputStream = conn.getErrorStream();
        }

        String responseText = readAll(inputStream);
        System.out.println("Firestore GET response body: " + responseText);

        if (responseText == null || responseText.trim().isEmpty()) {
            System.out.println("Firestore returned empty or invalid JSON.");
            exchange.sendResponseHeaders(204, -1); // No content
            return;
        }

        byte[] response = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
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
        return m.find() ? m.group(1) : "";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}