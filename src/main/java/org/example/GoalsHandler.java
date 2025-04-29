package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class GoalsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("GoalsHandler invoked: " + exchange.getRequestMethod());
        
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
        
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleGetRequest(exchange, idToken, localId);
        } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handlePostRequest(exchange, idToken, localId);
        } else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            handlePutRequest(exchange, idToken, localId);
        } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleDeleteRequest(exchange, idToken, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
    
    private void handleGetRequest(HttpExchange exchange, String idToken, String localId) throws IOException {
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Goals";
                
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else {
            // If no goals exist yet, return an empty array
            if (responseCode == 404) {
                String emptyResponse = "{\"goals\":[]}";
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] responseBytes = emptyResponse.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(responseCode, -1);
            }
        }
    }
    
    private void handlePostRequest(HttpExchange exchange, String idToken, String localId) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
        }
        
        String requestBody = body.toString();
        JSONObject goalData = new JSONObject(requestBody);
        
        // Generate a unique ID for the goal if not provided
        String goalId = goalData.optString("id", UUID.randomUUID().toString());
        
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Goals/" + goalId;
                
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setDoOutput(true);
        
        // Convert our goal data to Firestore format
        JSONObject firestoreData = new JSONObject();
        JSONObject fields = new JSONObject();
        
        fields.put("name", new JSONObject().put("stringValue", goalData.getString("name")));
        fields.put("targetAmount", new JSONObject().put("doubleValue", goalData.getDouble("targetAmount")));
        fields.put("currentAmount", new JSONObject().put("doubleValue", goalData.getDouble("currentAmount")));
        fields.put("deadline", new JSONObject().put("stringValue", goalData.optString("deadline", "")));
        fields.put("category", new JSONObject().put("stringValue", goalData.optString("category", "Other")));
        
        firestoreData.put("fields", fields);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200 || responseCode == 201) {
            String successResponse = "{\"success\":true,\"id\":\"" + goalId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = successResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else {
            StringBuilder errorResponse = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
            }
            
            String error = "{\"success\":false,\"error\":\"" + errorResponse.toString().replace("\"", "\\\"") + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = error.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseCode, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
    
    private void handlePutRequest(HttpExchange exchange, String idToken, String localId) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        String goalId = parts[parts.length - 1]; // Assuming URL is /api/goals/{goalId}
        
        StringBuilder body = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
        }
        
        String requestBody = body.toString();
        JSONObject goalData = new JSONObject(requestBody);
        
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Goals/" + goalId;
                
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setDoOutput(true);
        
        // Convert our goal data to Firestore format
        JSONObject firestoreData = new JSONObject();
        JSONObject fields = new JSONObject();
        
        if (goalData.has("name")) {
            fields.put("name", new JSONObject().put("stringValue", goalData.getString("name")));
        }
        if (goalData.has("targetAmount")) {
            fields.put("targetAmount", new JSONObject().put("doubleValue", goalData.getDouble("targetAmount")));
        }
        if (goalData.has("currentAmount")) {
            fields.put("currentAmount", new JSONObject().put("doubleValue", goalData.getDouble("currentAmount")));
        }
        if (goalData.has("deadline")) {
            fields.put("deadline", new JSONObject().put("stringValue", goalData.getString("deadline")));
        }
        if (goalData.has("category")) {
            fields.put("category", new JSONObject().put("stringValue", goalData.getString("category")));
        }
        
        firestoreData.put("fields", fields);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String successResponse = "{\"success\":true,\"id\":\"" + goalId + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = successResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else {
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    private void handleDeleteRequest(HttpExchange exchange, String idToken, String localId) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        String goalId = parts[parts.length - 1]; // Assuming URL is /api/goals/{goalId}
        
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Goals/" + goalId;
                
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String successResponse = "{\"success\":true}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = successResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } else {
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    private static String extractCookieValue(String cookies, String name) {
        String[] parts = cookies.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring((name + "=").length());
            }
        }
        return null;
    }
}
