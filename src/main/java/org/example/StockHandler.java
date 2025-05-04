package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Handler for stock-related API endpoints
 */
public class StockHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(StockHandler.class.getName());
    private static final Gson gson = new Gson();
    
    // Services
    private final StockApiService apiService = new StockApiService();
    private final FirestoreService firestoreService = FirestoreService.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        try {
            String userId = getUserId(exchange);
            if (userId == null) {
                sendResponse(exchange, 401, "{ \"error\": \"Unauthorized\" }");
                return;
            }
            
            // Check if the path starts with our API prefix
            if (!path.startsWith("/api/stocks")) {
                // This is a request for the stocks.html page or another resource, not an API call
                // We should let the server's default file handler process it
                exchange.sendResponseHeaders(404, -1); // Send 404 to let default handler take over
                return;
            }
            
            // Handle our specific endpoints
            if (path.equals("/api/stocks/account")) {
                handleAccountRequest(exchange, userId);
            } else if (path.equals("/api/stocks/portfolio")) {
                handlePortfolioRequest(exchange, userId);
            } else if (path.equals("/api/stocks/orders")) {
                if ("GET".equals(method)) {
                    handleGetOrdersRequest(exchange, userId);
                } else if ("POST".equals(method)) {
                    handlePlaceOrderRequest(exchange, userId);
                } else {
                    sendResponse(exchange, 405, "{ \"error\": \"Method not allowed\" }");
                }
            } else if (path.matches("/api/stocks/orders/[\\w-]+")) {
                if ("DELETE".equals(method)) {
                    handleCancelOrderRequest(exchange, userId);
                } else {
                    sendResponse(exchange, 405, "{ \"error\": \"Method not allowed\" }");
                }
            } else if (path.matches("/api/stocks/[\\w-]+/history")) {
                handleStockHistoryRequest(exchange);
            } else if (path.matches("/api/stocks/[\\w-]+")) {
                handleGetStockRequest(exchange);
            } else {
                sendResponse(exchange, 404, "{ \"error\": \"Not found\" }");
            }
        } catch (Exception e) {
            LOGGER.severe("Error handling request: " + e.getMessage());
            e.printStackTrace();
            sendResponse(exchange, 500, "{ \"error\": \"Internal server error\" }");
        }
    }
    
    private String getUserId(HttpExchange exchange) {
        // In a real application, this would validate the user's session/token
        // For demo purposes, we'll return a mock user ID
        return "user123";
    }
    
    private void handleAccountRequest(HttpExchange exchange, String userId) throws IOException {
        // Extract idToken from cookies for Firebase API calls
        String idToken = extractAuthTokenFromCookies(exchange);
        if (idToken == null) {
            sendResponse(exchange, 401, "{ \"error\": \"No authentication token found\" }");
            return;
        }
        
        // Calculate account value from positions and current stock prices from Firebase
        double portfolioValue = 0.0;
        double cash = 10000.00; // Default cash value - can be fetched from Firebase profile
        
        try {
            // Get user's stock positions from Firebase
            portfolioValue = getStockPortfolioValue(userId, idToken);
            
            // Get user's cash balance from Firebase (or use default)
            cash = getCashBalance(userId, idToken);
            
            double buyingPower = cash;
            double equity = portfolioValue + cash;
            
            // Get historical equity value for comparison
            double lastEquity = getLastEquityValue(userId, idToken);
            if (lastEquity <= 0) {
                lastEquity = equity - 500; // Fallback if no historical data
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("portfolio_value", portfolioValue);
            response.addProperty("cash", cash);
            response.addProperty("buying_power", buyingPower);
            response.addProperty("equity", equity);
            response.addProperty("last_equity", lastEquity);
            
            // Store current equity value for future reference
            storeEquitySnapshot(userId, idToken, equity);
            
            sendResponse(exchange, 200, gson.toJson(response));
            
        } catch (Exception e) {
            LOGGER.severe("Error calculating account value: " + e.getMessage());
            e.printStackTrace();
            
            // Return a fallback response with basic data
            JsonObject response = new JsonObject();
            response.addProperty("portfolio_value", portfolioValue);
            response.addProperty("cash", cash);
            response.addProperty("buying_power", cash);
            response.addProperty("equity", portfolioValue + cash);
            response.addProperty("last_equity", portfolioValue + cash - 500);
            
            sendResponse(exchange, 200, gson.toJson(response));
        }
    }
    
    private void handlePortfolioRequest(HttpExchange exchange, String userId) throws IOException {
        String idToken = extractAuthTokenFromCookies(exchange);
        if (idToken == null) {
            sendResponse(exchange, 401, "{ \"error\": \"No authentication token found\" }");
            return;
        }
        
        JsonObject response = new JsonObject();
        List<Map<String, Object>> positions = new ArrayList<>();
        
        try {
            // Fetch user's stock positions from Firebase
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/StockPositions";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();
                
                // Parse the positions data
                JSONObject root = new JSONObject(responseBuilder.toString());
                if (root.has("documents")) {
                    JSONArray documents = root.getJSONArray("documents");
                    
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject doc = documents.getJSONObject(i);
                        if (doc.has("fields")) {
                            JSONObject fields = doc.getJSONObject("fields");
                            
                            // Extract position data
                            String symbol = "";
                            int quantity = 0;
                            double avgPrice = 0.0;
                            double currentPrice = 0.0;
                            
                            if (fields.has("symbol") && fields.getJSONObject("symbol").has("stringValue")) {
                                symbol = fields.getJSONObject("symbol").getString("stringValue");
                            }
                            
                            if (fields.has("quantity") && fields.getJSONObject("quantity").has("integerValue")) {
                                quantity = Integer.parseInt(fields.getJSONObject("quantity").getString("integerValue"));
                            }
                            
                            if (fields.has("averagePrice") && fields.getJSONObject("averagePrice").has("doubleValue")) {
                                avgPrice = fields.getJSONObject("averagePrice").getDouble("doubleValue");
                            }
                            
                            if (quantity > 0 && !symbol.isEmpty()) {
                                try {
                                    // Get current price from Alpha Vantage API
                                    Stock stock = apiService.getStockQuote(symbol);
                                    currentPrice = stock.getPrice();
                                } catch (Exception e) {
                                    LOGGER.warning("Failed to get price for " + symbol + ": " + e.getMessage());
                                    currentPrice = avgPrice; // Use average price as fallback
                                }
                                
                                double marketValue = quantity * currentPrice;
                                double unrealizedPL = marketValue - (quantity * avgPrice);
                                double unrealizedPLPC = (unrealizedPL / (quantity * avgPrice)) * 100;
                                
                                Map<String, Object> posMap = new HashMap<>();
                                posMap.put("symbol", symbol);
                                posMap.put("qty", quantity);
                                posMap.put("avg_entry_price", avgPrice);
                                posMap.put("current_price", currentPrice);
                                posMap.put("market_value", marketValue);
                                posMap.put("unrealized_pl", unrealizedPL);
                                posMap.put("unrealized_plpc", unrealizedPLPC);
                                
                                positions.add(posMap);
                                
                                // Update last price in Firebase for future reference
                                updateLastPrice(userId, idToken, symbol, currentPrice);
                            }
                        }
                    }
                }
            } else {
                LOGGER.warning("Failed to fetch stock positions: HTTP " + responseCode);
            }
        } catch (Exception e) {
            LOGGER.severe("Error fetching portfolio: " + e.getMessage());
            e.printStackTrace();
        }
        
        response.add("positions", gson.toJsonTree(positions));
        sendResponse(exchange, 200, gson.toJson(response));
    }
    
    private void handleGetOrdersRequest(HttpExchange exchange, String userId) throws IOException {
        String idToken = extractAuthTokenFromCookies(exchange);
        if (idToken == null) {
            sendResponse(exchange, 401, "{ \"error\": \"No authentication token found\" }");
            return;
        }
        
        String status = exchange.getRequestURI().getQuery();
        boolean isOpen = status == null || status.contains("status=open");
        
        JsonObject response = new JsonObject();
        List<Map<String, Object>> orders = new ArrayList<>();
        
        try {
            // Fetch orders from Firebase
            String collectionName = isOpen ? "OpenOrders" : "OrderHistory";
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/" + collectionName;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();
                
                // Parse the orders data
                JSONObject root = new JSONObject(responseBuilder.toString());
                if (root.has("documents")) {
                    JSONArray documents = root.getJSONArray("documents");
                    
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject doc = documents.getJSONObject(i);
                        if (doc.has("fields")) {
                            JSONObject fields = doc.getJSONObject("fields");
                            
                            // Extract order data
                            Map<String, Object> orderMap = new HashMap<>();
                            
                            // Get the document ID from its name
                            String name = doc.getString("name");
                            String orderId = name.substring(name.lastIndexOf('/') + 1);
                            orderMap.put("id", orderId);
                            
                            if (fields.has("symbol") && fields.getJSONObject("symbol").has("stringValue")) {
                                orderMap.put("symbol", fields.getJSONObject("symbol").getString("stringValue"));
                            }
                            
                            if (fields.has("type") && fields.getJSONObject("type").has("stringValue")) {
                                orderMap.put("type", fields.getJSONObject("type").getString("stringValue"));
                            }
                            
                            if (fields.has("side") && fields.getJSONObject("side").has("stringValue")) {
                                orderMap.put("side", fields.getJSONObject("side").getString("stringValue"));
                            }
                            
                            if (fields.has("quantity") && fields.getJSONObject("quantity").has("integerValue")) {
                                orderMap.put("qty", Integer.parseInt(fields.getJSONObject("quantity").getString("integerValue")));
                            }
                            
                            if (fields.has("price") && fields.getJSONObject("price").has("doubleValue")) {
                                orderMap.put("price", fields.getJSONObject("price").getDouble("doubleValue"));
                            }
                            
                            if (fields.has("status") && fields.getJSONObject("status").has("stringValue")) {
                                orderMap.put("status", fields.getJSONObject("status").getString("stringValue"));
                            }
                            
                            if (fields.has("createdAt") && fields.getJSONObject("createdAt").has("timestampValue")) {
                                String timestampStr = fields.getJSONObject("createdAt").getString("timestampValue");
                                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                                Date date = isoFormat.parse(timestampStr);
                                orderMap.put("created_at", date.getTime());
                            }
                            
                            orders.add(orderMap);
                        }
                    }
                }
            } else {
                LOGGER.warning("Failed to fetch orders: HTTP " + responseCode);
            }
        } catch (Exception e) {
            LOGGER.severe("Error fetching orders: " + e.getMessage());
            e.printStackTrace();
        }
        
        response.add("orders", gson.toJsonTree(orders));
        sendResponse(exchange, 200, gson.toJson(response));
    }
    
    private void handlePlaceOrderRequest(HttpExchange exchange, String userId) throws IOException {
        String idToken = extractAuthTokenFromCookies(exchange);
        if (idToken == null) {
            sendResponse(exchange, 401, "{ \"error\": \"No authentication token found\" }");
            return;
        }
        
        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                .lines().collect(Collectors.joining("\n"));
        
        JsonObject orderData = gson.fromJson(requestBody, JsonObject.class);
        String symbol = orderData.get("symbol").getAsString();
        int quantity = orderData.get("quantity").getAsInt();
        String orderType = orderData.get("orderType").getAsString();
        String timeInForce = orderData.get("timeInForce").getAsString();
        
        // For demo purposes, we'll execute all orders immediately
        boolean isBuy = quantity > 0;
        String side = isBuy ? "buy" : "sell";
        quantity = Math.abs(quantity);
        
        // Get current stock price from the API
        double price;
        try {
            Stock stock = apiService.getStockQuote(symbol);
            price = stock.getPrice();
            if (price <= 0.0) {
                sendResponse(exchange, 400, "{ \"error\": \"Invalid price returned from API\" }");
                return;
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to get stock price: " + e.getMessage());
            sendResponse(exchange, 400, "{ \"error\": \"Failed to get current price: " + e.getMessage() + "\" }");
            return;
        }
        
        try {
            // Generate a unique order ID
            String orderId = UUID.randomUUID().toString();
            
            // Store order in Firebase - fix path format to include proper separators
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/OrderHistory";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            // Create Firestore document
            JSONObject requestJson = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("symbol", new JSONObject().put("stringValue", symbol));
            fields.put("type", new JSONObject().put("stringValue", orderType));
            fields.put("side", new JSONObject().put("stringValue", side));
            fields.put("quantity", new JSONObject().put("integerValue", String.valueOf(quantity)));
            fields.put("price", new JSONObject().put("doubleValue", price));
            fields.put("status", new JSONObject().put("stringValue", "filled"));
            fields.put("createdAt", new JSONObject().put("timestampValue", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date())));
            
            requestJson.put("fields", fields);
            
            // Write to Firestore
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Check response
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
                
                LOGGER.severe("Error creating order in Firebase: " + errorResponse.toString());
                sendResponse(exchange, responseCode, "{ \"error\": \"Failed to store order\" }");
                return;
            }
            
            // Update position in Firebase
            updateStockPosition(userId, idToken, symbol, side, quantity, price);
            
            // Update portfolio summary for home page
            updatePortfolioSummary(userId, idToken);
            
            // Return success response with order details
            JsonObject response = new JsonObject();
            response.addProperty("id", orderId);
            response.addProperty("status", "filled");
            response.addProperty("symbol", symbol);
            response.addProperty("side", side);
            response.addProperty("qty", quantity);
            response.addProperty("type", orderType);
            response.addProperty("price", price);
            
            sendResponse(exchange, 200, gson.toJson(response));
        } catch (Exception e) {
            LOGGER.severe("Error processing order: " + e.getMessage());
            e.printStackTrace();
            sendResponse(exchange, 500, "{ \"error\": \"Failed to process order: " + e.getMessage() + "\" }");
        }
    }
    
    private void handleCancelOrderRequest(HttpExchange exchange, String userId) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String orderId = path.substring(path.lastIndexOf('/') + 1);
        
        // In a real app, we would find and cancel the specific order
        // For demo, we'll just return success
        JsonObject response = new JsonObject();
        response.addProperty("id", orderId);
        response.addProperty("status", "canceled");
        
        sendResponse(exchange, 200, gson.toJson(response));
    }
    
    private void handleGetStockRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String symbol = path.substring(path.lastIndexOf('/') + 1);

        try {
            // Use our StockApiService to get stock data
            Stock stock = apiService.getStockQuote(symbol);
            
            // Build custom JSON response manually
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"symbol\":\"").append(symbol).append("\",");
            jsonBuilder.append("\"name\":\"").append(stock.getName()).append("\",");
            
            jsonBuilder.append("\"quote\":{");
            jsonBuilder.append("\"price\":").append(stock.getPrice()).append(",");
            jsonBuilder.append("\"previousClose\":").append(stock.getPreviousClose()).append(",");
            jsonBuilder.append("\"volume\":").append(stock.getVolume()).append(",");
            jsonBuilder.append("\"change\":").append(stock.getChange()).append(",");
            jsonBuilder.append("\"changePercent\":").append(stock.getChangePercent()).append(",");
            jsonBuilder.append("\"lastUpdated\":\"").append(stock.getLastUpdated()).append("\"");
            jsonBuilder.append("}");
            
            jsonBuilder.append("}");
            
            sendResponse(exchange, 200, jsonBuilder.toString());
        } catch (Exception e) {
            // Unexpected errors
            LOGGER.severe("Unexpected error when getting stock data: " + e.getMessage());
            e.printStackTrace();
            
            String errorJson = "{\"error\": \"Failed to retrieve stock data: " + e.getMessage().replace("\"", "'") + "\", \"symbol\": \"" + symbol + "\"}";
            sendResponse(exchange, 500, errorJson);
        }
    }
    
    /**
     * Helper method to extract values from JSON response using string operations
     * This avoids dependency on Gson for simple extraction
     */
    private String extractJsonValue(String json, String key) {
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) return "0";
        
        int valueStart = json.indexOf(":", keyIndex) + 1;
        int valueEnd = json.indexOf(",", valueStart);
        if (valueEnd < 0) valueEnd = json.indexOf("}", valueStart);
        
        String rawValue = json.substring(valueStart, valueEnd).trim();
        return rawValue.replace("\"", "");
    }
    
    /**
     * Helper method to get company names without dependency on external libraries
     */
    private String getCompanyName(String symbol) {
        Map<String, String> companyNames = new HashMap<>();
        companyNames.put("AAPL", "Apple Inc.");
        companyNames.put("MSFT", "Microsoft Corporation");
        companyNames.put("AMZN", "Amazon.com Inc.");
        companyNames.put("GOOGL", "Alphabet Inc.");
        companyNames.put("META", "Meta Platforms Inc.");
        companyNames.put("TSLA", "Tesla Inc.");
        companyNames.put("NVDA", "NVIDIA Corporation");
        companyNames.put("JPM", "JPMorgan Chase & Co.");
        companyNames.put("V", "Visa Inc.");
        companyNames.put("JNJ", "Johnson & Johnson");
        companyNames.put("WMT", "Walmart Inc.");
        companyNames.put("PG", "Procter & Gamble Co.");
        companyNames.put("MA", "Mastercard Inc.");
        companyNames.put("UNH", "UnitedHealth Group Inc.");
        companyNames.put("HD", "Home Depot Inc.");
        
        return companyNames.getOrDefault(symbol, symbol + " Inc.");
    }
    
    private void handleStockHistoryRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String symbol = path.substring(path.lastIndexOf('/', path.lastIndexOf('/') - 1) + 1, path.lastIndexOf('/'));
        
        String query = exchange.getRequestURI().getQuery();
        String timeframe = "1D"; // Default
        if (query != null && query.contains("timeframe=")) {
            timeframe = query.split("timeframe=")[1].split("&")[0];
        }
        
        try {
            // Use our StockApiService for history data
            List<Map<String, Object>> historyData = apiService.getStockHistory(symbol, timeframe);
            
            // Convert the history data to a JSON response
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"history\": [");
            
            for (int i = 0; i < historyData.size(); i++) {
                Map<String, Object> point = historyData.get(i);
                if (i > 0) jsonBuilder.append(",");
                
                jsonBuilder.append("{");
                // Format timestamp as ISO string if it's a long value
                Object timestamp = point.get("timestamp");
                if (timestamp instanceof Long) {
                    Date date = new Date((Long) timestamp);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    jsonBuilder.append("\"timestamp\":\"").append(dateFormat.format(date)).append("\",");
                } else {
                    jsonBuilder.append("\"timestamp\":\"").append(timestamp).append("\",");
                }
                
                jsonBuilder.append("\"price\":").append(point.get("price"));
                jsonBuilder.append("}");
            }
            
            jsonBuilder.append("]}");
            
            sendResponse(exchange, 200, jsonBuilder.toString());
        } catch (Exception e) {
            // Handle error
            LOGGER.severe("Error getting stock history: " + e.getMessage());
            String errorJson = "{\"error\": \"Failed to retrieve stock history: " + e.getMessage().replace("\"", "'") + "\", \"symbol\": \"" + symbol + "\", \"timeframe\": \"" + timeframe + "\"}";
            sendResponse(exchange, 500, errorJson);
        }
    }
    
    /**
     * Extract the Firebase ID token from request cookies
     */
    private String extractAuthTokenFromCookies(HttpExchange exchange) {
        // Get the Cookie header
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return null;
        }
        
        // Parse cookies
        Map<String, String> cookies = new HashMap<>();
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2) {
                cookies.put(parts[0], parts[1]);
            }
        }
        
        // Extract ID token
        return cookies.get("idToken");
    }
    
    /**
     * Calculate the total value of all stock positions
     */
    private double getStockPortfolioValue(String userId, String idToken) {
        double portfolioValue = 0.0;
        
        try {
            // Fetch user's stock positions from Firebase
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/StockPositions";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();
                
                // Parse the positions data
                JSONObject root = new JSONObject(responseBuilder.toString());
                if (root.has("documents")) {
                    JSONArray documents = root.getJSONArray("documents");
                    
                    for (int i = 0; i < documents.length(); i++) {
                        JSONObject doc = documents.getJSONObject(i);
                        if (doc.has("fields")) {
                            JSONObject fields = doc.getJSONObject("fields");
                            
                            // Extract position data
                            String symbol = "";
                            int quantity = 0;
                            double currentPrice = 0.0;
                            
                            if (fields.has("symbol") && fields.getJSONObject("symbol").has("stringValue")) {
                                symbol = fields.getJSONObject("symbol").getString("stringValue");
                            }
                            
                            if (fields.has("quantity") && fields.getJSONObject("quantity").has("integerValue")) {
                                quantity = Integer.parseInt(fields.getJSONObject("quantity").getString("integerValue"));
                            }
                            
                            if (fields.has("lastPrice") && fields.getJSONObject("lastPrice").has("doubleValue")) {
                                // Use stored last price as a starting point
                                currentPrice = fields.getJSONObject("lastPrice").getDouble("doubleValue");
                            }
                            
                            if (quantity > 0 && !symbol.isEmpty()) {
                                try {
                                    // Try to get current price from API
                                    Stock stock = apiService.getStockQuote(symbol);
                                    currentPrice = stock.getPrice();
                                } catch (Exception e) {
                                    // If API call fails, use the stored price (or default to 0)
                                    LOGGER.warning("Failed to get price for " + symbol + ", using stored price: " + e.getMessage());
                                }
                                
                                // Add to portfolio value
                                portfolioValue += quantity * currentPrice;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error calculating portfolio value: " + e.getMessage());
            e.printStackTrace();
        }
        
        return portfolioValue;
    }
    
    /**
     * Get user's cash balance from Firebase
     */
    private double getCashBalance(String userId, String idToken) {
        double cashBalance = 10000.0; // Default starting cash
        
        try {
            // Check if the user has a cash balance document
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/AccountInfo/cash";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();
                
                JSONObject doc = new JSONObject(responseBuilder.toString());
                if (doc.has("fields") && doc.getJSONObject("fields").has("balance")) {
                    JSONObject balanceField = doc.getJSONObject("fields").getJSONObject("balance");
                    if (balanceField.has("doubleValue")) {
                        cashBalance = balanceField.getDouble("doubleValue");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error getting cash balance, using default: " + e.getMessage());
        }
        
        return cashBalance;
    }
    
    /**
     * Get historical equity value for comparison
     */
    private double getLastEquityValue(String userId, String idToken) {
        double lastEquity = 0.0;
        
        try {
            // Get the most recent equity snapshot
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/AccountInfo/equitySnapshots?orderBy=timestamp&limitToLast=1";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();
                
                JSONObject root = new JSONObject(responseBuilder.toString());
                if (root.has("documents") && root.getJSONArray("documents").length() > 0) {
                    JSONObject doc = root.getJSONArray("documents").getJSONObject(0);
                    if (doc.has("fields") && doc.getJSONObject("fields").has("value")) {
                        JSONObject valueField = doc.getJSONObject("fields").getJSONObject("value");
                        if (valueField.has("doubleValue")) {
                            lastEquity = valueField.getDouble("doubleValue");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error getting historical equity, using 0: " + e.getMessage());
        }
        
        return lastEquity;
    }
    
    /**
     * Store current equity value for future reference
     */
    private void storeEquitySnapshot(String userId, String idToken, double equity) {
        try {
            String snapshotId = UUID.randomUUID().toString();
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/AccountInfo/equitySnapshots/" + snapshotId;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            JSONObject requestJson = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("value", new JSONObject().put("doubleValue", equity));
            fields.put("timestamp", new JSONObject().put("timestampValue", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date())));
            
            requestJson.put("fields", fields);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                LOGGER.warning("Failed to store equity snapshot: HTTP " + responseCode);
            }
        } catch (Exception e) {
            LOGGER.warning("Error storing equity snapshot: " + e.getMessage());
        }
    }
    
    /**
     * Update the last known price for a stock in Firebase
     */
    private void updateLastPrice(String userId, String idToken, String symbol, double price) {
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/StockPositions/" + symbol;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            JSONObject requestJson = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("lastPrice", new JSONObject().put("doubleValue", price));
            fields.put("lastUpdated", new JSONObject().put("timestampValue", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date())));
            
            requestJson.put("fields", fields);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                LOGGER.warning("Failed to update last price: HTTP " + responseCode);
            }
        } catch (Exception e) {
            LOGGER.warning("Error updating last price: " + e.getMessage());
        }
    }
    
    /**
     * Update or create a stock position in Firebase
     */
    private void updateStockPosition(String userId, String idToken, String symbol, String side, int quantity, double price) {
        try {
            // First, check if the position already exists
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/StockPositions/" + symbol;
            
            URL getUrl = new URL(firestoreUrl);
            HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
            getConn.setRequestMethod("GET");
            getConn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            int getResponseCode = getConn.getResponseCode();
            boolean positionExists = (getResponseCode == 200);
            
            int existingQuantity = 0;
            double existingAvgPrice = 0.0;
            
            if (positionExists) {
                // Read existing position data
                BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();
                
                JSONObject doc = new JSONObject(responseBuilder.toString());
                if (doc.has("fields")) {
                    JSONObject fields = doc.getJSONObject("fields");
                    
                    if (fields.has("quantity") && fields.getJSONObject("quantity").has("integerValue")) {
                        existingQuantity = Integer.parseInt(fields.getJSONObject("quantity").getString("integerValue"));
                    }
                    
                    if (fields.has("averagePrice") && fields.getJSONObject("averagePrice").has("doubleValue")) {
                        existingAvgPrice = fields.getJSONObject("averagePrice").getDouble("doubleValue");
                    }
                }
            }
            
            // Calculate new position data
            int newQuantity;
            double newAvgPrice;
            
            if ("buy".equals(side)) {
                newQuantity = existingQuantity + quantity;
                if (existingQuantity > 0) {
                    newAvgPrice = ((existingQuantity * existingAvgPrice) + (quantity * price)) / newQuantity;
                } else {
                    newAvgPrice = price;
                }
            } else { // sell
                newQuantity = existingQuantity - quantity;
                newAvgPrice = existingAvgPrice; // Average price doesn't change when selling
            }
            
            // Update or delete the position
            if (newQuantity <= 0) {
                // Delete position if quantity is zero or negative
                if (positionExists) {
                    URL deleteUrl = new URL(firestoreUrl);
                    HttpURLConnection deleteConn = (HttpURLConnection) deleteUrl.openConnection();
                    deleteConn.setRequestMethod("DELETE");
                    deleteConn.setRequestProperty("Authorization", "Bearer " + idToken);
                    
                    int deleteResponseCode = deleteConn.getResponseCode();
                    if (deleteResponseCode >= 400) {
                        LOGGER.warning("Failed to delete stock position: HTTP " + deleteResponseCode);
                    }
                }
            } else {
                // Create or update position
                URL updateUrl = new URL(firestoreUrl);
                HttpURLConnection updateConn = (HttpURLConnection) updateUrl.openConnection();
                updateConn.setRequestMethod(positionExists ? "PATCH" : "POST");
                updateConn.setRequestProperty("Content-Type", "application/json");
                updateConn.setRequestProperty("Authorization", "Bearer " + idToken);
                updateConn.setDoOutput(true);
                
                JSONObject requestJson = new JSONObject();
                JSONObject fields = new JSONObject();
                
                fields.put("symbol", new JSONObject().put("stringValue", symbol));
                fields.put("quantity", new JSONObject().put("integerValue", String.valueOf(newQuantity)));
                fields.put("averagePrice", new JSONObject().put("doubleValue", newAvgPrice));
                fields.put("lastPrice", new JSONObject().put("doubleValue", price));
                fields.put("lastUpdated", new JSONObject().put("timestampValue", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date())));
                
                requestJson.put("fields", fields);
                
                try (OutputStream os = updateConn.getOutputStream()) {
                    byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int updateResponseCode = updateConn.getResponseCode();
                if (updateResponseCode >= 400) {
                    LOGGER.warning("Failed to update stock position: HTTP " + updateResponseCode);
                }
            }
            
            // Update cash balance
            updateCashBalance(userId, idToken, side, quantity, price);
            
        } catch (Exception e) {
            LOGGER.severe("Error updating stock position: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Update user's cash balance after a trade
     */
    private void updateCashBalance(String userId, String idToken, String side, int quantity, double price) {
        try {
            double tradeValue = quantity * price;
            double cashChange = "buy".equals(side) ? -tradeValue : tradeValue;
            
            // Get current cash balance
            double currentCash = getCashBalance(userId, idToken);
            double newCash = currentCash + cashChange;
            
            // Update cash balance in Firebase
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/AccountInfo/cash";
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); // Use POST with merge option
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            JSONObject requestJson = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("balance", new JSONObject().put("doubleValue", newCash));
            fields.put("lastUpdated", new JSONObject().put("timestampValue", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date())));
            
            requestJson.put("fields", fields);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                LOGGER.warning("Failed to update cash balance: HTTP " + responseCode);
            }
        } catch (Exception e) {
            LOGGER.warning("Error updating cash balance: " + e.getMessage());
        }
    }
    
    /**
     * Update portfolio summary data for the home page
     */
    private void updatePortfolioSummary(String userId, String idToken) {
        try {
            // Get current date for summary ID
            SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM");
            String monthId = monthFormat.format(new Date());
            
            // Calculate total portfolio value
            double portfolioValue = getStockPortfolioValue(userId, idToken);
            double cashBalance = getCashBalance(userId, idToken);
            double totalValue = portfolioValue + cashBalance;
            
            // Create or update monthly summary
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + userId + "/Summaries/portfolio_" + monthId;
            
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); // Use POST with merge option
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            
            JSONObject requestJson = new JSONObject();
            JSONObject fields = new JSONObject();
            
            fields.put("portfolioValue", new JSONObject().put("doubleValue", portfolioValue));
            fields.put("cashBalance", new JSONObject().put("doubleValue", cashBalance));
            fields.put("totalValue", new JSONObject().put("doubleValue", totalValue));
            fields.put("lastUpdated", new JSONObject().put("timestampValue", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date())));
            
            // Add date information for easier querying
            fields.put("year", new JSONObject().put("integerValue", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));
            fields.put("month", new JSONObject().put("integerValue", String.valueOf(Calendar.getInstance().get(Calendar.MONTH) + 1)));
            
            requestJson.put("fields", fields);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                LOGGER.warning("Failed to update portfolio summary: HTTP " + responseCode);
            }
        } catch (Exception e) {
            LOGGER.warning("Error updating portfolio summary: " + e.getMessage());
        }
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}
