package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler for stock trading API endpoints
 */
public class StockApiHandler implements HttpHandler {
    private final StockService stockService;

    /**
     * Constructor
     */
    public StockApiHandler() {
        // Initialize with paper trading mode
        this.stockService = new StockService(true);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            // Handle preflight requests
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String method = exchange.getRequestMethod();

            // Parse query parameters
            Map<String, String> queryParams = parseQueryParams(query);

            // Route based on path and method
            if (path.equals("/api/stocks") && method.equals("GET")) {
                handleGetStocks(exchange, queryParams);
            } else if (path.equals("/api/stocks/search") && method.equals("GET")) {
                handleSearchStocks(exchange, queryParams);
            } else if (path.matches("/api/stocks/[^/]+") && method.equals("GET")) {
                handleGetStock(exchange, path);
            } else if (path.equals("/api/portfolio") && method.equals("GET")) {
                handleGetPortfolio(exchange);
            } else if (path.equals("/api/orders") && method.equals("GET")) {
                handleGetOrders(exchange, queryParams);
            } else if (path.equals("/api/orders") && method.equals("POST")) {
                handlePlaceOrder(exchange);
            } else if (path.matches("/api/orders/[^/]+") && method.equals("DELETE")) {
                handleCancelOrder(exchange, path);
            } else if (path.matches("/api/stocks/[^/]+/history") && method.equals("GET")) {
                handleGetStockHistory(exchange, path, queryParams);
            } else if (path.equals("/api/account") && method.equals("GET")) {
                handleGetAccount(exchange);
            } else {
                // Route not found
                sendResponse(exchange, new JSONObject().put("error", "Not found").toString(), 404);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, new JSONObject().put("error", e.getMessage()).toString(), 500);
        }
    }

    /**
     * Handle GET /api/stocks - Get all tradable stocks
     */
    private void handleGetStocks(HttpExchange exchange, Map<String, String> params) throws IOException {
        boolean forceRefresh = "true".equalsIgnoreCase(params.get("refresh"));
        
        List<Stock> stocks = stockService.getTradableStocks(forceRefresh);
        
        // Convert to JSON array
        JSONArray jsonArray = new JSONArray();
        for (Stock stock : stocks) {
            jsonArray.put(stock.toJSON());
        }
        
        // Wrap in response object
        JSONObject response = new JSONObject();
        response.put("stocks", jsonArray);
        response.put("count", stocks.size());
        
        sendResponse(exchange, response.toString(), 200);
    }

    /**
     * Handle GET /api/stocks/search - Search for stocks
     */
    private void handleSearchStocks(HttpExchange exchange, Map<String, String> params) throws IOException {
        String query = params.get("q");
        if (query == null || query.isEmpty()) {
            sendResponse(exchange, new JSONObject().put("error", "Search query required").toString(), 400);
            return;
        }
        
        // Get all stocks and filter by query
        List<Stock> allStocks = stockService.getTradableStocks(false);
        List<Stock> filteredStocks = new ArrayList<>();
        String queryLower = query.toLowerCase();
        
        for (Stock stock : allStocks) {
            if (stock.getSymbol().toLowerCase().contains(queryLower) || 
                stock.getName().toLowerCase().contains(queryLower)) {
                filteredStocks.add(stock);
            }
            
            // Limit results to 20 for performance
            if (filteredStocks.size() >= 20) {
                break;
            }
        }
        
        // Convert to JSON array
        JSONArray jsonArray = new JSONArray();
        for (Stock stock : filteredStocks) {
            jsonArray.put(stock.toJSON());
        }
        
        // Wrap in response object
        JSONObject response = new JSONObject();
        response.put("stocks", jsonArray);
        response.put("count", filteredStocks.size());
        
        sendResponse(exchange, response.toString(), 200);
    }

    /**
     * Handle GET /api/stocks/{symbol} - Get details for a stock
     */
    private void handleGetStock(HttpExchange exchange, String path) throws IOException {
        String symbol = path.substring(path.lastIndexOf('/') + 1);
        
        // Get stock details
        Stock stock = stockService.getStock(symbol, false);
        if (stock == null) {
            sendResponse(exchange, new JSONObject().put("error", "Stock not found").toString(), 404);
            return;
        }
        
        // Get current quote
        JSONObject quote = stockService.getQuote(symbol);
        
        // Combine stock details and quote
        JSONObject response = stock.toJSON();
        response.put("quote", quote);
        
        sendResponse(exchange, response.toString(), 200);
    }

    /**
     * Handle GET /api/portfolio - Get user's portfolio
     */
    private void handleGetPortfolio(HttpExchange exchange) throws IOException {
        List<StockPosition> positions = stockService.getPositions(false);
        
        // Calculate portfolio summary
        double totalValue = 0;
        double totalCost = 0;
        double totalPL = 0;
        
        // Convert positions to JSON array and calculate summary
        JSONArray positionsArray = new JSONArray();
        for (StockPosition position : positions) {
            positionsArray.put(position.toJSON());
            
            totalValue += position.getMarketValue();
            totalCost += position.getAverageEntryPrice() * position.getQuantity();
            totalPL += position.getUnrealizedProfitLoss();
        }
        
        // Create response
        JSONObject response = new JSONObject();
        response.put("positions", positionsArray);
        response.put("summary", new JSONObject()
            .put("totalValue", totalValue)
            .put("totalCost", totalCost)
            .put("totalPL", totalPL)
            .put("totalPLPercent", totalCost > 0 ? (totalPL / totalCost) * 100 : 0)
            .put("positionCount", positions.size())
        );
        
        sendResponse(exchange, response.toString(), 200);
    }

    /**
     * Handle GET /api/orders - Get list of orders
     */
    private void handleGetOrders(HttpExchange exchange, Map<String, String> params) throws IOException {
        try {
            String status = params.getOrDefault("status", "open");
            int limit = Integer.parseInt(params.getOrDefault("limit", "50"));
            boolean forceRefresh = "true".equalsIgnoreCase(params.get("refresh"));
            
            List<StockOrder> orders = stockService.getOrders(status, limit, forceRefresh);
            
            // Convert to JSON array
            JSONArray jsonArray = new JSONArray();
            for (StockOrder order : orders) {
                jsonArray.put(order.toDetailedJSON());
            }
            
            // Wrap in response object
            JSONObject response = new JSONObject();
            response.put("orders", jsonArray);
            response.put("count", orders.size());
            
            sendResponse(exchange, response.toString(), 200);
        } catch (Exception e) {
            // In case of any error, return an empty orders list
            JSONObject response = new JSONObject();
            response.put("orders", new JSONArray());
            response.put("count", 0);
            response.put("error", "Error retrieving orders: " + e.getMessage());
            
            sendResponse(exchange, response.toString(), 200);
        }
    }

    /**
     * Handle POST /api/orders - Place a new order
     */
    private void handlePlaceOrder(HttpExchange exchange) throws IOException {
        // Read request body
        String requestBody = readRequestBody(exchange);
        JSONObject orderRequest = new JSONObject(requestBody);
        
        try {
            // Extract common parameters
            String symbol = orderRequest.getString("symbol");
            int quantity = orderRequest.getInt("quantity");
            String orderType = orderRequest.getString("orderType");
            String timeInForce = orderRequest.optString("timeInForce", "day");
            
            // Place order based on type
            StockOrder order;
            switch (orderType.toLowerCase()) {
                case "market":
                    order = stockService.placeMarketOrder(
                        symbol, quantity, timeInForce);
                    break;
                case "limit":
                    double limitPrice = orderRequest.getDouble("limitPrice");
                    order = stockService.placeLimitOrder(
                        symbol, quantity, limitPrice, timeInForce);
                    break;
                case "stop":
                    double stopPrice = orderRequest.getDouble("stopPrice");
                    order = stockService.placeStopOrder(
                        symbol, quantity, stopPrice, timeInForce);
                    break;
                case "stop_limit":
                    double slStopPrice = orderRequest.getDouble("stopPrice");
                    double slLimitPrice = orderRequest.getDouble("limitPrice");
                    order = stockService.placeStopLimitOrder(
                        symbol, quantity, slStopPrice, slLimitPrice, timeInForce);
                    break;
                case "trailing_stop":
                    double trailPercent = orderRequest.getDouble("trailPercent");
                    order = stockService.placeTrailingStopOrder(
                        symbol, quantity, trailPercent, timeInForce);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported order type: " + orderType);
            }
            
            // Return the created order
            sendResponse(exchange, order.toDetailedJSON().toString(), 201);
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, new JSONObject()
                .put("error", "Invalid order parameters")
                .put("message", e.getMessage())
                .toString(), 400);
        } catch (Exception e) {
            sendResponse(exchange, new JSONObject()
                .put("error", "Failed to place order")
                .put("message", e.getMessage())
                .toString(), 500);
        }
    }

    /**
     * Handle DELETE /api/orders/{orderId} - Cancel an order
     */
    private void handleCancelOrder(HttpExchange exchange, String path) throws IOException {
        String orderId = path.substring(path.lastIndexOf('/') + 1);
        
        boolean success = stockService.cancelOrder(orderId);
        
        if (success) {
            sendResponse(exchange, new JSONObject()
                .put("success", true)
                .put("message", "Order canceled")
                .put("orderId", orderId)
                .toString(), 200);
        } else {
            sendResponse(exchange, new JSONObject()
                .put("error", "Failed to cancel order")
                .put("message", "Order not found or already executed")
                .toString(), 404);
        }
    }

    /**
     * Handle GET /api/stocks/{symbol}/history - Get historical data
     */
    private void handleGetStockHistory(HttpExchange exchange, String path, Map<String, String> params) throws IOException {
        String symbol = path.split("/")[3]; // Extract symbol from path
        String timeframe = params.getOrDefault("timeframe", "1D");
        int limit = Integer.parseInt(params.getOrDefault("limit", "100"));
        
        JSONArray bars = stockService.getHistoricalData(symbol, timeframe, limit);
        
        JSONObject response = new JSONObject();
        response.put("symbol", symbol);
        response.put("timeframe", timeframe);
        response.put("bars", bars);
        
        sendResponse(exchange, response.toString(), 200);
    }

    /**
     * Handle GET /api/account - Get account information
     */
    private void handleGetAccount(HttpExchange exchange) throws IOException {
        JSONObject account = stockService.getAccountInfo();
        sendResponse(exchange, account.toString(), 200);
    }

    /**
     * Parse query parameters into a map
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> result = new HashMap<>();
        
        if (query == null || query.isEmpty()) {
            return result;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.toString());
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.toString());
                    result.put(key, value);
                }
            } catch (UnsupportedEncodingException e) {
                // This shouldn't happen with UTF-8
                e.printStackTrace();
            }
        }
        
        return result;
    }

    /**
     * Read request body as string
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            
            return body.toString();
        }
    }

    /**
     * Send HTTP response
     */
    private void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
