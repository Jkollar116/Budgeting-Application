package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handler for leaderboard related endpoints.
 * Provides functionality to retrieve leaderboard data and user rankings based on net worth.
 */
public class LeaderboardHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("LeaderboardHandler invoked: " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        
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
        
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        if (path.equals("/api/leaderboard") && "GET".equalsIgnoreCase(method)) {
            handleGetLeaderboard(exchange, idToken, localId);
        } else if (path.equals("/api/leaderboard/rank") && "GET".equalsIgnoreCase(method)) {
            handleGetUserRank(exchange, idToken, localId);
        } else if (path.matches("/api/leaderboard/user/[^/]+") && "GET".equalsIgnoreCase(method)) {
            String userId = path.substring(path.lastIndexOf('/') + 1);
            handleGetUserDetails(exchange, idToken, localId, userId);
        } else if (path.equals("/api/netWorth/recalculate") && "POST".equalsIgnoreCase(method)) {
            handleRecalculateNetWorth(exchange, idToken, localId);
        } else {
            System.out.println("404 - Unsupported path/method: " + path + " " + method);
            exchange.sendResponseHeaders(404, -1);
        }
    }
    
    /**
     * Handle GET request to retrieve the full leaderboard
     */
    private void handleGetLeaderboard(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Fetch all users with net worth data
            List<LeaderboardUser> users = getAllUsersWithNetWorth(idToken);
            
            // Sort users by net worth (descending)
            users.sort(Comparator.comparing(LeaderboardUser::getNetWorth).reversed());
            
            // Assign ranks
            int rank = 1;
            for (LeaderboardUser user : users) {
                user.setRank(rank++);
                user.setCurrentUser(user.getId().equals(localId));
            }
            
            // Create response
            JSONObject response = new JSONObject();
            JSONArray usersArray = new JSONArray();
            
            for (LeaderboardUser user : users) {
                JSONObject userJson = new JSONObject();
                userJson.put("id", user.getId());
                userJson.put("username", user.getUsername());
                userJson.put("name", user.getName());
                userJson.put("netWorth", user.getNetWorth());
                userJson.put("rank", user.getRank());
                userJson.put("isCurrentUser", user.isCurrentUser());
                usersArray.put(userJson);
            }
            
            response.put("users", usersArray);
            response.put("totalUsers", users.size());
            
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "{\"error\":\"" + e.getMessage() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorMessage.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorMessage.getBytes());
            os.close();
        }
    }
    
    /**
     * Handle GET request to get the current user's rank
     */
    private void handleGetUserRank(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Fetch all users with net worth data
            List<LeaderboardUser> users = getAllUsersWithNetWorth(idToken);
            
            // Sort users by net worth (descending)
            users.sort(Comparator.comparing(LeaderboardUser::getNetWorth).reversed());
            
            // Find current user's rank
            int rank = 1;
            int userRank = 0;
            double userNetWorth = 0;
            
            for (LeaderboardUser user : users) {
                if (user.getId().equals(localId)) {
                    userRank = rank;
                    userNetWorth = user.getNetWorth();
                    break;
                }
                rank++;
            }
            
            // Create response
            JSONObject response = new JSONObject();
            response.put("rank", userRank);
            response.put("netWorth", userNetWorth);
            response.put("totalUsers", users.size());
            
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "{\"error\":\"" + e.getMessage() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorMessage.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorMessage.getBytes());
            os.close();
        }
    }
    
    /**
     * Handle GET request to get detailed information about a specific user
     */
    private void handleGetUserDetails(HttpExchange exchange, String idToken, String localId, String userId) throws IOException {
        try {
            // Get the user's details and rank
            LeaderboardUser user = getUserDetails(idToken, userId);
            
            if (user == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            
            // Get user's rank
            List<LeaderboardUser> users = getAllUsersWithNetWorth(idToken);
            users.sort(Comparator.comparing(LeaderboardUser::getNetWorth).reversed());
            
            int rank = 1;
            for (LeaderboardUser u : users) {
                if (u.getId().equals(userId)) {
                    user.setRank(rank);
                    break;
                }
                rank++;
            }
            
            // Create response
            JSONObject response = new JSONObject();
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("name", user.getName());
            response.put("netWorth", user.getNetWorth());
            response.put("rank", user.getRank());
            
            // Add optional profile fields if available
            if (user.getAge() > 0) {
                response.put("age", user.getAge());
            }
            
            if (user.getCareer() != null && !user.getCareer().isEmpty()) {
                response.put("career", user.getCareer());
            }
            
            if (user.getCareerDescription() != null && !user.getCareerDescription().isEmpty()) {
                response.put("careerDescription", user.getCareerDescription());
            }
            
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "{\"error\":\"" + e.getMessage() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorMessage.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorMessage.getBytes());
            os.close();
        }
    }
    
    /**
     * Handle POST request to recalculate the user's net worth
     */
    private void handleRecalculateNetWorth(HttpExchange exchange, String idToken, String localId) throws IOException {
        try {
            // Use NetWorthCalculator to recalculate net worth
            double netWorth = NetWorthCalculator.calculateNetWorth(idToken, localId);
            
            // Save the updated net worth to history
            NetWorthCalculator.saveNetWorthHistory(idToken, localId, netWorth);
            
            // Update the user's net worth field
            updateUserNetWorth(idToken, localId, netWorth);
            
            // Create response
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("netWorth", netWorth);
            
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "{\"error\":\"" + e.getMessage() + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, errorMessage.length());
            OutputStream os = exchange.getResponseBody();
            os.write(errorMessage.getBytes());
            os.close();
        }
    }
    
    /**
     * Get all users with net worth data for the leaderboard
     */
    private List<LeaderboardUser> getAllUsersWithNetWorth(String idToken) throws Exception {
        List<LeaderboardUser> users = new ArrayList<>();
        
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users";
        
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
            
            JSONObject firebaseResponse = new JSONObject(response.toString());
            
            if (firebaseResponse.has("documents")) {
                JSONArray documents = firebaseResponse.getJSONArray("documents");
                
                for (int i = 0; i < documents.length(); i++) {
                    JSONObject document = documents.getJSONObject(i);
                    JSONObject fields = document.getJSONObject("fields");
                    
                    // Skip users without net worth data
                    if (!fields.has("netWorth")) {
                        continue;
                    }
                    
                    // Extract user ID from document name
                    String name = document.getString("name");
                    String userId = name.substring(name.lastIndexOf('/') + 1);
                    
                    // Create user object
                    LeaderboardUser user = new LeaderboardUser();
                    user.setId(userId);
                    
                    // Get net worth
                    if (fields.getJSONObject("netWorth").has("doubleValue")) {
                        user.setNetWorth(fields.getJSONObject("netWorth").getDouble("doubleValue"));
                    } else if (fields.getJSONObject("netWorth").has("integerValue")) {
                        user.setNetWorth(fields.getJSONObject("netWorth").getInt("integerValue"));
                    } else {
                        continue; // Skip if net worth is not a number
                    }
                    
                    // Get username (email if username not available)
                    if (fields.has("username")) {
                        user.setUsername(fields.getJSONObject("username").getString("stringValue"));
                    } else if (fields.has("email")) {
                        String email = fields.getJSONObject("email").getString("stringValue");
                        user.setUsername(email.substring(0, email.indexOf('@')));
                    } else {
                        user.setUsername("User " + userId.substring(0, 6));
                    }
                    
                    // Get name if available
                    if (fields.has("name")) {
                        user.setName(fields.getJSONObject("name").getString("stringValue"));
                    }
                    
                    users.add(user);
                }
            }
        } else {
            throw new Exception("Failed to fetch users: " + responseCode);
        }
        
        return users;
    }
    
    /**
     * Get detailed information about a specific user
     */
    private LeaderboardUser getUserDetails(String idToken, String userId) throws Exception {
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + userId;
        
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
            
            JSONObject userDoc = new JSONObject(response.toString());
            
            if (userDoc.has("fields")) {
                JSONObject fields = userDoc.getJSONObject("fields");
                
                // Create user object
                LeaderboardUser user = new LeaderboardUser();
                user.setId(userId);
                
                // Get net worth
                if (fields.has("netWorth")) {
                    if (fields.getJSONObject("netWorth").has("doubleValue")) {
                        user.setNetWorth(fields.getJSONObject("netWorth").getDouble("doubleValue"));
                    } else if (fields.getJSONObject("netWorth").has("integerValue")) {
                        user.setNetWorth(fields.getJSONObject("netWorth").getInt("integerValue"));
                    }
                }
                
                // Get username (email if username not available)
                if (fields.has("username")) {
                    user.setUsername(fields.getJSONObject("username").getString("stringValue"));
                } else if (fields.has("email")) {
                    String email = fields.getJSONObject("email").getString("stringValue");
                    user.setUsername(email.substring(0, email.indexOf('@')));
                } else {
                    user.setUsername("User " + userId.substring(0, 6));
                }
                
                // Get name if available
                if (fields.has("name")) {
                    user.setName(fields.getJSONObject("name").getString("stringValue"));
                }
                
                // Get profile details if available
                if (fields.has("age")) {
                    if (fields.getJSONObject("age").has("integerValue")) {
                        user.setAge(fields.getJSONObject("age").getInt("integerValue"));
                    }
                }
                
                if (fields.has("career")) {
                    user.setCareer(fields.getJSONObject("career").getString("stringValue"));
                }
                
                if (fields.has("careerDescription")) {
                    user.setCareerDescription(fields.getJSONObject("careerDescription").getString("stringValue"));
                }
                
                return user;
            }
        }
        
        return null;
    }
    
    /**
     * Update the user's net worth field in Firestore
     */
    private void updateUserNetWorth(String idToken, String localId, double netWorth) {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + localId;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            JSONObject updateData = new JSONObject();
            JSONObject fields = new JSONObject();
            fields.put("netWorth", new JSONObject().put("doubleValue", netWorth));
            updateData.put("fields", fields);
            
            OutputStream os = conn.getOutputStream();
            os.write(updateData.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.out.println("Error updating user net worth: " + responseCode);
            }
        } catch (Exception e) {
            System.out.println("Error updating user net worth: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Extract a cookie value from the cookie string
     */
    private static String extractCookieValue(String cookies, String name) {
        if (cookies == null) {
            return null;
        }
        
        String[] parts = cookies.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring((name + "=").length());
            }
        }
        return null;
    }
    
    /**
     * Class to represent a user in the leaderboard
     */
    private static class LeaderboardUser {
        private String id;
        private String username;
        private String name;
        private double netWorth;
        private int rank;
        private boolean isCurrentUser;
        private int age;
        private String career;
        private String careerDescription;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public double getNetWorth() {
            return netWorth;
        }
        
        public void setNetWorth(double netWorth) {
            this.netWorth = netWorth;
        }
        
        public int getRank() {
            return rank;
        }
        
        public void setRank(int rank) {
            this.rank = rank;
        }
        
        public boolean isCurrentUser() {
            return isCurrentUser;
        }
        
        public void setCurrentUser(boolean currentUser) {
            isCurrentUser = currentUser;
        }
        
        public int getAge() {
            return age;
        }
        
        public void setAge(int age) {
            this.age = age;
        }
        
        public String getCareer() {
            return career;
        }
        
        public void setCareer(String career) {
            this.career = career;
        }
        
        public String getCareerDescription() {
            return careerDescription;
        }
        
        public void setCareerDescription(String careerDescription) {
            this.careerDescription = careerDescription;
        }
    }
}
