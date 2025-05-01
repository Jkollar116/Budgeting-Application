package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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

/**
 * Handler for stock-related API endpoints
 */
public class StockHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(StockHandler.class.getName());
    private static final Gson gson = new Gson();
    private static final StockApiService apiService = new StockApiService();
    
    // Mock data for user accounts and positions
    // In a real application, this would be stored in a database
    private static final Map<String, StockPosition> USER_POSITIONS = new ConcurrentHashMap<>();
    private static final List<StockOrder> OPEN_ORDERS = Collections.synchronizedList(new ArrayList<>());
    private static final List<StockTransaction> ORDER_HISTORY = Collections.synchronizedList(new ArrayList<>());
    
    // Initialize some demo user data
    static {
        // Sample positions for demo
        USER_POSITIONS.put("AAPL", new StockPosition("AAPL", 10, 170.25));
        USER_POSITIONS.put("MSFT", new StockPosition("MSFT", 5, 410.50));
        USER_POSITIONS.put("NVDA", new StockPosition("NVDA", 8, 880.75));
        
        // Sample order history
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, -2);
        ORDER_HISTORY.add(new StockTransaction("AAPL", "buy", 5, 175.50, cal.getTime()));
        
        cal.add(Calendar.DAY_OF_MONTH, -1);
        ORDER_HISTORY.add(new StockTransaction("MSFT", "buy", 5, 415.20, cal.getTime()));
        
        cal.add(Calendar.DAY_OF_MONTH, -3);
        ORDER_HISTORY.add(new StockTransaction("NVDA", "buy", 3, 875.25, cal.getTime()));
        
        cal.add(Calendar.DAY_OF_MONTH, -5);
        ORDER_HISTORY.add(new StockTransaction("TSLA", "buy", 10, 165.75, cal.getTime()));
        
        cal.add(Calendar.DAY_OF_MONTH, -2);
        ORDER_HISTORY.add(new StockTransaction("TSLA", "sell", 10, 172.30, cal.getTime()));
    }

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
                // Not a path we should handle
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(302, -1);
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
        // Calculate account value from positions and current stock prices
        double portfolioValue = 0.0;
        
        try {
            for (StockPosition position : USER_POSITIONS.values()) {
                try {
                    // Get current price from the API
                    Stock stock = apiService.getStockQuote(position.getSymbol());
                    double currentPrice = stock.getPrice();
                    portfolioValue += position.getQuantity() * currentPrice;
                } catch (Exception e) {
                    LOGGER.warning("Failed to get price for " + position.getSymbol() + ": " + e.getMessage());
                    // Use average price as fallback
                    portfolioValue += position.getQuantity() * position.getAveragePrice();
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error calculating portfolio value: " + e.getMessage());
            // Fallback to a simpler calculation if API calls fail
            portfolioValue = USER_POSITIONS.values().stream()
                    .mapToDouble(pos -> pos.getQuantity() * pos.getAveragePrice())
                    .sum();
        }
        
        // Mock data for demonstration
        double cash = 10000.00;
        double buyingPower = cash;
        double equity = portfolioValue + cash;
        double lastEquity = equity - 500; // Simulate some previous value
        
        JsonObject response = new JsonObject();
        response.addProperty("portfolio_value", portfolioValue);
        response.addProperty("cash", cash);
        response.addProperty("buying_power", buyingPower);
        response.addProperty("equity", equity);
        response.addProperty("last_equity", lastEquity);
        
        sendResponse(exchange, 200, gson.toJson(response));
    }
    
    private void handlePortfolioRequest(HttpExchange exchange, String userId) throws IOException {
        JsonObject response = new JsonObject();
        List<Map<String, Object>> positions = new ArrayList<>();
        
        for (StockPosition pos : USER_POSITIONS.values()) {
            String symbol = pos.getSymbol();
            int qty = pos.getQuantity();
            double avgPrice = pos.getAveragePrice();
            double currentPrice = 0.0;
            
            try {
                // Get current price from the API
                Stock stock = apiService.getStockQuote(symbol);
                currentPrice = stock.getPrice();
            } catch (Exception e) {
                LOGGER.warning("Failed to get price for " + symbol + ": " + e.getMessage());
                // Use average price as fallback
                currentPrice = avgPrice;
            }
            
            double marketValue = qty * currentPrice;
            double unrealizedPL = marketValue - (qty * avgPrice);
            double unrealizedPLPC = (unrealizedPL / (qty * avgPrice)) * 100;
            
            Map<String, Object> posMap = new HashMap<>();
            posMap.put("symbol", symbol);
            posMap.put("qty", qty);
            posMap.put("avg_entry_price", avgPrice);
            posMap.put("current_price", currentPrice);
            posMap.put("market_value", marketValue);
            posMap.put("unrealized_pl", unrealizedPL);
            posMap.put("unrealized_plpc", unrealizedPLPC);
            
            positions.add(posMap);
        }
        
        response.add("positions", gson.toJsonTree(positions));
        sendResponse(exchange, 200, gson.toJson(response));
    }
    
    private void handleGetOrdersRequest(HttpExchange exchange, String userId) throws IOException {
        String status = exchange.getRequestURI().getQuery();
        boolean isOpen = status == null || status.contains("status=open");
        
        List<Map<String, Object>> orders;
        if (isOpen) {
            orders = OPEN_ORDERS.stream()
                    .map(this::orderToMap)
                    .collect(Collectors.toList());
        } else {
            orders = ORDER_HISTORY.stream()
                    .map(this::transactionToMap)
                    .collect(Collectors.toList());
        }
        
        JsonObject response = new JsonObject();
        response.add("orders", gson.toJsonTree(orders));
        sendResponse(exchange, 200, gson.toJson(response));
    }
    
    private void handlePlaceOrderRequest(HttpExchange exchange, String userId) throws IOException {
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
        
        // Create transaction record
        StockTransaction transaction = new StockTransaction(symbol, side, quantity, price, new Date());
        ORDER_HISTORY.add(transaction);
        
        // Update user's position
        updatePosition(userId, symbol, side, quantity, price);
        
        // Return success response
        JsonObject response = new JsonObject();
        response.addProperty("id", UUID.randomUUID().toString());
        response.addProperty("status", "filled");
        response.addProperty("symbol", symbol);
        response.addProperty("side", side);
        response.addProperty("qty", quantity);
        response.addProperty("type", orderType);
        response.addProperty("price", price);
        
        sendResponse(exchange, 200, gson.toJson(response));
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
    
    private void updatePosition(String userId, String symbol, String side, int quantity, double price) {
        StockPosition position = USER_POSITIONS.getOrDefault(symbol, new StockPosition(symbol, 0, 0.0));
        
        if ("buy".equals(side)) {
            int newQuantity = position.getQuantity() + quantity;
            double newAvgPrice = ((position.getQuantity() * position.getAveragePrice()) + (quantity * price)) / newQuantity;
            position.setQuantity(newQuantity);
            position.setAveragePrice(newAvgPrice);
        } else { // sell
            int newQuantity = position.getQuantity() - quantity;
            if (newQuantity <= 0) {
                USER_POSITIONS.remove(symbol);
            } else {
                position.setQuantity(newQuantity);
                // Average price doesn't change when selling
                USER_POSITIONS.put(symbol, position);
            }
        }
        
        if (position.getQuantity() > 0) {
            USER_POSITIONS.put(symbol, position);
        }
    }
    
    private Map<String, Object> orderToMap(StockOrder order) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", order.getId());
        map.put("symbol", order.getSymbol());
        map.put("type", order.getType());
        map.put("side", order.getSide());
        map.put("qty", order.getQuantity());
        map.put("limit_price", order.getLimitPrice());
        map.put("stop_price", order.getStopPrice());
        map.put("status", order.getStatus());
        map.put("created_at", order.getCreatedAt().getTime());
        return map;
    }
    
    private Map<String, Object> transactionToMap(StockTransaction transaction) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", UUID.randomUUID().toString());
        map.put("symbol", transaction.getSymbol());
        map.put("type", "market");
        map.put("side", transaction.getType());
        map.put("qty", transaction.getQuantity());
        map.put("price", transaction.getPrice());
        map.put("status", "filled");
        map.put("created_at", transaction.getTimestamp().getTime());
        return map;
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}
