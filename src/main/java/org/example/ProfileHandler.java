package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

public class ProfileHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");

        if (idToken == null || localId == null) {
            System.out.println("401 - Missing idToken or localId");
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
        System.out.println("ProfileHandler POST invoked");
        System.out.println("Body: " + body);

        String fullName = getJsonValue(body, "fullName");
        String careerDescription = getJsonValue(body, "careerDescription");
        String profileImage = getJsonValue(body, "profileImage");
        String netWorth = getJsonValue(body, "netWorth");
        String optInLeaderboardRaw = getJsonValue(body, "optInLeaderboard");

        if (fullName.isEmpty() && careerDescription.isEmpty() && profileImage.isEmpty()
                && netWorth.isEmpty() && optInLeaderboardRaw.isEmpty()) {
            System.out.println("400 - No valid fields provided");
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        StringBuilder json = new StringBuilder("{ \"fields\": {");

        if (!fullName.isEmpty()) {
            json.append("\"fullName\": {\"stringValue\": \"").append(escapeJson(fullName)).append("\"},");
        }
        if (!careerDescription.isEmpty()) {
            json.append("\"careerDescription\": {\"stringValue\": \"").append(escapeJson(careerDescription)).append("\"},");
        }
        if (!profileImage.isEmpty()) {
            json.append("\"profileImage\": {\"stringValue\": \"").append(escapeJson(profileImage)).append("\"},");
        }
        if (!netWorth.isEmpty()) {
            json.append("\"netWorth\": {\"doubleValue\": ").append(netWorth).append("},");
        }
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }

        json.append("} }");

        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Profile/profile";

        HttpURLConnection conn = openFirestoreConnection(urlStr, "PATCH", idToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(json.toString().getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        System.out.println("Firestore response code: " + code);

        if (code == 200 || code == 201) {
            byte[] msg = "Profile saved.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, msg.length);
            exchange.getResponseBody().write(msg);
        } else {
            String error = readAll(conn.getErrorStream());
            System.out.println("Firestore error body: " + error);
            exchange.sendResponseHeaders(code, -1);
        }

        exchange.getResponseBody().close();
    }

    private void handleGet(HttpExchange exchange, String idToken, String localId) throws IOException {
        String urlStr = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Profile/profile";

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

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
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

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}