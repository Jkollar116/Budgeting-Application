package org.example;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with the Alpaca API for stock trading
 */
public class AlpacaApiService {
    // API Keys provided by user
    private final String apiKey;
    private final String apiSecret;
    
    // API URLs
    private static final String PAPER_TRADING_BASE_URL = "https://paper-api.alpaca.markets";
    private static final String LIVE_TRADING_BASE_URL = "https://api.alpaca.markets";
    private static final String DATA_BASE_URL = "https://data.alpaca.markets";
    
    // Endpoints
    private static final String ACCOUNT_ENDPOINT = "/v2/account";
    private static final String ORDERS_ENDPOINT = "/v2/orders";
    private static final String POSITIONS_ENDPOINT = "/v2/positions";
    private static final String ASSETS_ENDPOINT = "/v2/assets";
    private static final String BARS_ENDPOINT = "/v2/stocks/{symbol}/bars";
    private static final String QUOTES_ENDPOINT = "/v2/stocks/{symbol}/quotes";
    
    private final OkHttpClient client;
    private final boolean isPaperTrading;
    
    /**
     * Constructor with API keys
     * 
     * @param apiKey The Alpaca API key
     * @param apiSecret The Alpaca API secret
     * @param isPaperTrading Whether to use paper trading (true) or live trading (false)
     */
    public AlpacaApiService(String apiKey, String apiSecret, boolean isPaperTrading) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.isPaperTrading = isPaperTrading;
        
        // Create an HTTP client with appropriate timeouts
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Get account information
     * 
     * @return JSONObject with account details
     * @throws IOException If the API call fails
     */
    public JSONObject getAccount() throws IOException {
        return makeApiCall(ACCOUNT_ENDPOINT, "GET", null);
    }
    
    /**
     * Get a list of tradable assets
     * 
     * @param status Filter by asset status (active, inactive)
     * @param assetClass Filter by asset class (us_equity, etc.)
     * @return List of Stock objects
     * @throws IOException If the API call fails
     */
    public List<Stock> getAssets(String status, String assetClass) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(ASSETS_ENDPOINT);
        boolean hasParams = false;
        
        if (status != null && !status.isEmpty()) {
            urlBuilder.append("?status=").append(status);
            hasParams = true;
        }
        
        if (assetClass != null && !assetClass.isEmpty()) {
            urlBuilder.append(hasParams ? "&" : "?")
                     .append("asset_class=").append(assetClass);
        }
        
        JSONArray assetsArray = makeApiCall(urlBuilder.toString(), "GET", null).getJSONArray("assets");
        List<Stock> assets = new ArrayList<>();
        
        for (int i = 0; i < assetsArray.length(); i++) {
            JSONObject assetJson = assetsArray.getJSONObject(i);
            assets.add(new Stock(assetJson));
        }
        
        return assets;
    }
    
    /**
     * Get current positions
     * 
     * @return List of StockPosition objects
     * @throws IOException If the API call fails
     */
    public List<StockPosition> getPositions() throws IOException {
        JSONArray positionsArray = makeApiCall(POSITIONS_ENDPOINT, "GET", null).getJSONArray("positions");
        List<StockPosition> positions = new ArrayList<>();
        
        for (int i = 0; i < positionsArray.length(); i++) {
            JSONObject positionJson = positionsArray.getJSONObject(i);
            positions.add(new StockPosition(positionJson));
        }
        
        return positions;
    }
    
    /**
     * Get a specific position by symbol
     * 
     * @param symbol The stock symbol
     * @return StockPosition object or null if not found
     * @throws IOException If the API call fails
     */
    public StockPosition getPosition(String symbol) throws IOException {
        try {
            JSONObject positionJson = makeApiCall(POSITIONS_ENDPOINT + "/" + symbol, "GET", null);
            return new StockPosition(positionJson);
        } catch (IOException e) {
            if (e.getMessage().contains("404")) {
                return null; // No position for this symbol
            }
            throw e;
        }
    }
    
    /**
     * Place a new order
     * 
     * @param order The order to place
     * @return The updated order with server-assigned ID
     * @throws IOException If the API call fails
     * @throws IllegalArgumentException If the order is invalid
     */
    public StockOrder placeOrder(StockOrder order) throws IOException {
        if (!order.validate()) {
            throw new IllegalArgumentException("Invalid order parameters");
        }
        
        JSONObject orderJson = order.toJSON();
        JSONObject response = makeApiCall(ORDERS_ENDPOINT, "POST", orderJson);
        
        // Create a new order object from the response
        return new StockOrder(response);
    }
    
    /**
     * Get a list of orders
     * 
     * @param status Filter by order status
     * @param limit Maximum number of orders to return
     * @param after Only return orders after this timestamp
     * @return List of StockOrder objects
     * @throws IOException If the API call fails
     */
    public List<StockOrder> getOrders(String status, int limit, String after) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(ORDERS_ENDPOINT);
        boolean hasParams = false;
        
        if (status != null && !status.isEmpty()) {
            urlBuilder.append("?status=").append(status);
            hasParams = true;
        }
        
        if (limit > 0) {
            urlBuilder.append(hasParams ? "&" : "?")
                     .append("limit=").append(limit);
            hasParams = true;
        }
        
        if (after != null && !after.isEmpty()) {
            urlBuilder.append(hasParams ? "&" : "?")
                     .append("after=").append(after);
        }
        
        JSONArray ordersArray = makeApiCall(urlBuilder.toString(), "GET", null).getJSONArray("orders");
        List<StockOrder> orders = new ArrayList<>();
        
        for (int i = 0; i < ordersArray.length(); i++) {
            JSONObject orderJson = ordersArray.getJSONObject(i);
            orders.add(new StockOrder(orderJson));
        }
        
        return orders;
    }
    
    /**
     * Get an order by ID
     * 
     * @param orderId The order ID
     * @return StockOrder object or null if not found
     * @throws IOException If the API call fails
     */
    public StockOrder getOrder(String orderId) throws IOException {
        try {
            JSONObject orderJson = makeApiCall(ORDERS_ENDPOINT + "/" + orderId, "GET", null);
            return new StockOrder(orderJson);
        } catch (IOException e) {
            if (e.getMessage().contains("404")) {
                return null; // Order not found
            }
            throw e;
        }
    }
    
    /**
     * Cancel an order
     * 
     * @param orderId The order ID to cancel
     * @return true if successfully canceled, false otherwise
     * @throws IOException If the API call fails
     */
    public boolean cancelOrder(String orderId) throws IOException {
        try {
            makeApiCall(ORDERS_ENDPOINT + "/" + orderId, "DELETE", null);
            return true;
        } catch (IOException e) {
            if (e.getMessage().contains("404")) {
                return false; // Order not found
            }
            throw e;
        }
    }
    
    /**
     * Get current market data for a symbol
     * 
     * @param symbol The stock symbol
     * @return JSONObject with current market data
     * @throws IOException If the API call fails
     */
    public JSONObject getQuote(String symbol) throws IOException {
        String endpoint = QUOTES_ENDPOINT.replace("{symbol}", symbol);
        return makeApiCall(endpoint, "GET", null);
    }
    
    /**
     * Get historical bar data for a symbol
     * 
     * @param symbol The stock symbol
     * @param timeframe The bar timeframe (1Min, 5Min, 15Min, 1H, 1D)
     * @param start Start time in ISO 8601 format
     * @param end End time in ISO 8601 format
     * @param limit Maximum number of bars to return
     * @return JSONArray of bar data
     * @throws IOException If the API call fails
     */
    public JSONArray getBars(String symbol, String timeframe, String start, String end, int limit) throws IOException {
        String endpoint = BARS_ENDPOINT.replace("{symbol}", symbol);
        StringBuilder urlBuilder = new StringBuilder(endpoint);
        
        urlBuilder.append("?timeframe=").append(timeframe);
        
        if (start != null && !start.isEmpty()) {
            urlBuilder.append("&start=").append(start);
        }
        
        if (end != null && !end.isEmpty()) {
            urlBuilder.append("&end=").append(end);
        }
        
        if (limit > 0) {
            urlBuilder.append("&limit=").append(limit);
        }
        
        JSONObject response = makeApiCall(urlBuilder.toString(), "GET", null);
        return response.getJSONArray("bars");
    }
    
    /**
     * Make an API call to the Alpaca API
     * 
     * @param endpoint The API endpoint
     * @param method The HTTP method (GET, POST, DELETE)
     * @param body The request body (for POST requests)
     * @return JSONObject containing the response
     * @throws IOException If the API call fails
     */
    private JSONObject makeApiCall(String endpoint, String method, JSONObject body) throws IOException {
        // Determine base URL based on whether we're using paper trading
        String baseUrl = isPaperTrading ? PAPER_TRADING_BASE_URL : LIVE_TRADING_BASE_URL;
        
        // For market data endpoints, use the data base URL
        if (endpoint.startsWith("/v2/stocks")) {
            baseUrl = DATA_BASE_URL;
        }
        
        // Build the request
        Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl + endpoint)
                .header("APCA-API-KEY-ID", apiKey)
                .header("APCA-API-SECRET-KEY", apiSecret);
        
        // Add method and body
        switch (method.toUpperCase()) {
            case "GET":
                requestBuilder.get();
                break;
            case "POST":
                if (body != null) {
                    RequestBody requestBody = RequestBody.create(
                            body.toString(), MediaType.parse("application/json"));
                    requestBuilder.post(requestBody);
                } else {
                    requestBuilder.post(RequestBody.create("", null));
                }
                break;
            case "DELETE":
                requestBuilder.delete();
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        // Execute the request
        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String responseString = responseBody != null ? responseBody.string() : "";
            
            if (!response.isSuccessful()) {
                throw new IOException("API call failed with code " + response.code() + ": " + responseString);
            }
            
            // For empty responses (like successful DELETE), return empty JSONObject
            if (responseString.isEmpty()) {
                return new JSONObject();
            }
            
            return new JSONObject(responseString);
        }
    }
    
    /**
     * Format a LocalDateTime to ISO 8601 string for API requests
     */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_INSTANT);
    }
}
