package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ProfileHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("ProfileHandler invoked: " + exchange.getRequestMethod());
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            System.out.println("401 - Missing idToken/localId in cookies");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        
        if (idToken == null || localId == null) {
            System.out.println("401 - null tokens");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleGetProfile(exchange, idToken, localId);
        } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleSaveProfile(exchange, idToken, localId);
        } else {
            exchange.sendResponseHeaders(405, -1); // Method not allowed
        }
    }
    
    private void handleGetProfile(HttpExchange exchange, String idToken, String localId) throws IOException {
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + localId;
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
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    private void handleSaveProfile(HttpExchange exchange, String idToken, String localId) throws IOException {
        // Read request body
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }
        
        JSONObject profileData = new JSONObject(requestBody.toString());
        
        // Transform JSON into Firestore format
        JSONObject firestoreData = new JSONObject();
        JSONObject fields = new JSONObject();
        
        // Add name field if present
        if (profileData.has("name") && !profileData.isNull("name")) {
            JSONObject nameField = new JSONObject();
            nameField.put("stringValue", profileData.getString("name"));
            fields.put("name", nameField);
        }
        
        // Add username field if present
        if (profileData.has("username") && !profileData.isNull("username")) {
            // First verify username is unique
            String newUsername = profileData.getString("username");
            if (isUsernameUnique(newUsername, idToken, localId)) {
                JSONObject usernameField = new JSONObject();
                usernameField.put("stringValue", newUsername);
                fields.put("username", usernameField);
                
                // Also update the Usernames collection to ensure uniqueness
                updateUsernameRecord(newUsername, idToken, localId);
            } else {
                // Username is not unique, return an error
                String errorResponse = "{\"status\":\"error\",\"message\":\"Username already taken\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(400, errorResponse.length());
                OutputStream respOs = exchange.getResponseBody();
                respOs.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                respOs.close();
                return;
            }
        }
        
        // Add age field if present
        if (profileData.has("age") && !profileData.isNull("age")) {
            JSONObject ageField = new JSONObject();
            ageField.put("integerValue", profileData.getInt("age"));
            fields.put("age", ageField);
        }
        
        // Add career field if present
        if (profileData.has("career") && !profileData.isNull("career")) {
            JSONObject careerField = new JSONObject();
            careerField.put("stringValue", profileData.getString("career"));
            fields.put("career", careerField);
        }
        
        // Add careerDescription field if present
        if (profileData.has("careerDescription") && !profileData.isNull("careerDescription")) {
            JSONObject descField = new JSONObject();
            descField.put("stringValue", profileData.getString("careerDescription"));
            fields.put("careerDescription", descField);
        }
        
        firestoreData.put("fields", fields);
        
        // Save to Firestore
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + localId;
        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setDoOutput(true);
        
        OutputStream os = conn.getOutputStream();
        os.write(firestoreData.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String successResponse = "{\"status\":\"success\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, successResponse.length());
            OutputStream respOs = exchange.getResponseBody();
            respOs.write(successResponse.getBytes(StandardCharsets.UTF_8));
            respOs.close();
        } else {
            // Handle error
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                BufferedReader errorReader = new BufferedReader(isrError);
                StringBuilder errorResponse = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
                System.out.println("Firestore error: " + errorResponse.toString());
            }
            exchange.sendResponseHeaders(responseCode, -1);
        }
    }
    
    /**
     * Check if a username is unique
     */
    private boolean isUsernameUnique(String username, String idToken, String localId) {
        try {
            // First check if the user already has this username (in which case it's valid)
            String userUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + localId;
            URL checkSelfUrl = new URL(userUrl);
            HttpURLConnection selfConn = (HttpURLConnection) checkSelfUrl.openConnection();
            selfConn.setRequestMethod("GET");
            selfConn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int selfResponseCode = selfConn.getResponseCode();
            if (selfResponseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(selfConn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                JSONObject userDoc = new JSONObject(response.toString());
                if (userDoc.has("fields") && userDoc.getJSONObject("fields").has("username")) {
                    String currentUsername = userDoc.getJSONObject("fields").getJSONObject("username").getString("stringValue");
                    if (currentUsername.equals(username)) {
                        return true; // User is keeping current username
                    }
                }
            }
            
            // Check if username exists in the Usernames collection
            String checkUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Usernames/" + username;
            URL url = new URL(checkUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 404) {
                return true; // Username doesn't exist, so it's unique
            } else if (responseCode == 200) {
                // Check if this username is already assigned to this user
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                JSONObject usernameDoc = new JSONObject(response.toString());
                if (usernameDoc.has("fields") && usernameDoc.getJSONObject("fields").has("userId")) {
                    String userId = usernameDoc.getJSONObject("fields").getJSONObject("userId").getString("stringValue");
                    return userId.equals(localId); // Username is already assigned to this user
                }
                return false; // Username exists and belongs to someone else
            }
            
            return false; // Default to not unique on error
        } catch (Exception e) {
            System.out.println("Error checking username uniqueness: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Update the Usernames collection record
     */
    private void updateUsernameRecord(String username, String idToken, String localId) {
        try {
            // Get current username first
            String userUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + localId;
            URL checkUrl = new URL(userUrl);
            HttpURLConnection checkConn = (HttpURLConnection) checkUrl.openConnection();
            checkConn.setRequestMethod("GET");
            checkConn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            String currentUsername = null;
            
            int checkResponseCode = checkConn.getResponseCode();
            if (checkResponseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(checkConn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                
                JSONObject userDoc = new JSONObject(response.toString());
                if (userDoc.has("fields") && userDoc.getJSONObject("fields").has("username")) {
                    currentUsername = userDoc.getJSONObject("fields").getJSONObject("username").getString("stringValue");
                }
            }
            
            // If current username exists and is different, delete the old record
            if (currentUsername != null && !currentUsername.equals(username)) {
                String deleteUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Usernames/" + currentUsername;
                URL delUrl = new URL(deleteUrl);
                HttpURLConnection delConn = (HttpURLConnection) delUrl.openConnection();
                delConn.setRequestMethod("DELETE");
                delConn.setRequestProperty("Authorization", "Bearer " + idToken);
                delConn.getResponseCode(); // Just to execute the request
            }
            
            // Create or update the new username record
            String usernameUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Usernames/" + username;
            URL url = new URL(usernameUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT"); // PUT will create or update
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            JSONObject document = new JSONObject();
            JSONObject fields = new JSONObject();
            fields.put("userId", new JSONObject().put("stringValue", localId));
            document.put("fields", fields);
            
            OutputStream os = conn.getOutputStream();
            os.write(document.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            
            conn.getResponseCode(); // Just to execute the request
        } catch (Exception e) {
            System.out.println("Error updating username record: " + e.getMessage());
            e.printStackTrace();
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
