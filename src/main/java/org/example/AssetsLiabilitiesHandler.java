// src/main/java/org/example/AssetsLiabilitiesHandler.java
package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.cloud.firestore.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Handles assets and liabilities API endpoints with improved Firebase integration
 */
public class AssetsLiabilitiesHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(AssetsLiabilitiesHandler.class.getName());
    private final String collectionName;
    private final FirestoreService firestoreService;

    public AssetsLiabilitiesHandler(String collectionName) {
        this.collectionName = collectionName;
        this.firestoreService = FirestoreService.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            LOGGER.warning("Unauthorized " + collectionName + " attempt: missing cookies");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        
        LOGGER.info("=== AssetsLiabilitiesHandler(" + collectionName + ") " + method + " ===");

        if (!firestoreService.isInitialized()) {
            LOGGER.severe("Firestore service not initialized");
            exchange.sendResponseHeaders(500, -1);
            return;
        }

        if ("GET".equalsIgnoreCase(method)) {
            handleGet(exchange, localId);
        } else if ("POST".equalsIgnoreCase(method)) {
            handlePost(exchange, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleGet(HttpExchange exchange, String localId) throws IOException {
        try {
            LOGGER.info("Fetching " + collectionName + " for user: " + localId);
            
            // Get collection from Firestore
            CollectionReference collectionRef = firestoreService.getSubcollection(localId, collectionName);
            
            if (collectionRef == null) {
                LOGGER.severe("Failed to get collection reference");
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            
            // Query all documents in the collection
            QuerySnapshot querySnapshot = collectionRef.get().get();
            
            // Convert to JSON response
            JSONObject response = new JSONObject();
            JSONArray documents = new JSONArray();
            
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Map<String, Object> data = doc.getData();
                
                if (data != null) {
                    // Convert to Firestore API format (compatible with original implementation)
                    JSONObject item = new JSONObject();
                    item.put("name", doc.getId());
                    
                    JSONObject fields = new JSONObject();
                    
                    // Convert each field to Firestore API format
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        
                        if (value instanceof String) {
                            fields.put(key, new JSONObject().put("stringValue", value));
                        } else if (value instanceof Double || value instanceof Float) {
                            fields.put(key, new JSONObject().put("doubleValue", value));
                        } else if (value instanceof Integer || value instanceof Long) {
                            fields.put(key, new JSONObject().put("integerValue", value));
                        } else if (value instanceof Boolean) {
                            fields.put(key, new JSONObject().put("booleanValue", value));
                        }
                    }
                    
                    item.put("fields", fields);
                    documents.put(item);
                }
            }
            
            response.put("documents", documents);
            
            // Send response
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.getResponseBody().close();
            
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Error fetching " + collectionName, e);
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private void handlePost(HttpExchange exchange, String localId) throws IOException {
        try {
            // Read request body
            String requestBody = readAll(exchange.getRequestBody());
            LOGGER.info("Received POST payload for " + collectionName + ": " + requestBody);
            
            JSONObject jsonRequest = new JSONObject(requestBody);
            String name = jsonRequest.optString("name", "");
            double amount = jsonRequest.optDouble("amount", Double.NaN);
            
            if (name.isEmpty() || Double.isNaN(amount)) {
                LOGGER.warning("Invalid payload for " + collectionName + ": missing name or amount");
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            
            // Prepare data for Firestore
            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("amount", amount);
            data.put("createdAt", new Date());
            
            // Generate a document ID based on the name or use a UUID
            String docId = name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
            if (docId.isEmpty()) {
                docId = UUID.randomUUID().toString();
            }
            
            // Save to Firestore
            DocumentReference docRef = firestoreService.getSubcollectionDocument(localId, collectionName, docId);
            
            if (docRef == null) {
                LOGGER.severe("Failed to get document reference");
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            
            docRef.set(data).get();
            
            // Prepare success response that matches the expected format
            JSONObject responseData = new JSONObject();
            JSONObject fields = new JSONObject();
            fields.put("name", new JSONObject().put("stringValue", name));
            fields.put("amount", new JSONObject().put("doubleValue", amount));
            responseData.put("fields", fields);
            
            // Send response
            byte[] responseBytes = responseData.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.getResponseBody().close();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving " + collectionName, e);
            exchange.sendResponseHeaders(500, -1);
        }
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
