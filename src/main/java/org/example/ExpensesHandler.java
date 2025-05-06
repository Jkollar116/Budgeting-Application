package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpensesHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("ExpensesHandler invoked: " + exchange.getRequestMethod());
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

        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            handleGet(exchange, idToken, localId);
        } else if ("POST".equalsIgnoreCase(method)) {
            handlePost(exchange, idToken, localId);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            handleDelete(exchange, idToken, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleGet(HttpExchange exchange, String idToken, String localId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Expenses";
        HttpURLConnection conn = openFirestoreConnection(urlStr, "GET", idToken);
        int code = conn.getResponseCode();
        System.out.println("Firestore GET response code: " + code);
        if (code == 200) {
            byte[] resp = readAll(conn.getInputStream()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private void handlePost(HttpExchange exchange, String idToken, String localId) throws IOException {
        String body = readAll(exchange.getRequestBody());
        String date = getJsonValue(body, "date");
        String name = getJsonValue(body, "name");
        String category = getJsonValue(body, "category");
        String totalStr = getJsonValue(body, "total");
        double total;
        try {
            total = Double.parseDouble(totalStr);
        } catch (Exception e) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        String json = "{\"fields\":{"
                + "\"date\":{\"stringValue\":\"" + date + "\"},"
                + "\"name\":{\"stringValue\":\"" + name + "\"},"
                + "\"category\":{\"stringValue\":\"" + category + "\"},"
                + "\"total\":{\"doubleValue\":" + total + "}"
                + "}}";

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Expenses";
        HttpURLConnection conn = openFirestoreConnection(urlStr, "POST", idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        System.out.println("Firestore POST response code: " + code);
        if (code == 200 || code == 201) {
            byte[] msg = "Expense added successfully.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, msg.length);
            exchange.getResponseBody().write(msg);
        } else {
            logError(conn);
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private void handleDelete(HttpExchange exchange, String idToken, String localId) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String docId = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("docId=")) {
                    docId = URLDecoder.decode(param.substring(6), "UTF-8");
                    break;
                }
            }
        }
        if (docId == null || docId.isEmpty()) {
            System.out.println("DELETE missing docId parameter");
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Expenses/" + docId;
        System.out.println("Firestore DELETE URL: " + urlStr);
        HttpURLConnection conn = openFirestoreConnection(urlStr, "DELETE", idToken);
        int code = conn.getResponseCode();
        System.out.println("Firestore DELETE response code: " + code);
        if (code == 200 || code == 204) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            logError(conn);
            exchange.sendResponseHeaders(code, -1);
        }
    }

    private HttpURLConnection openFirestoreConnection(String urlStr, String method, String idToken) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
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

    private void logError(HttpURLConnection conn) throws IOException {
        InputStream err = conn.getErrorStream();
        if (err != null) {
            System.out.println("Firestore error: " + readAll(err));
        }
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
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([\\d.-]+))");
        Matcher m = p.matcher(json);
        if (m.find()) {
            if (m.group(1) != null) return m.group(1);
            if (m.group(2) != null) return m.group(2);
        }
        return "";
    }
}
