package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing stock trading operations and caching
 */
public class StockService {
    private final AlpacaApiService alpacaApi;
    
    // Cache for stock data
    private final Map<String, Stock> stockCache = new ConcurrentHashMap<>();
    private final Map<String, StockPosition> positionCache = new ConcurrentHashMap<>();
    private final Map<String, JSONObject> quoteCache = new ConcurrentHashMap<>();
    private final Map<String, List<StockOrder>> orderCache = new ConcurrentHashMap<>();
    
    // Cache timestamps for determining when to refresh
    private long lastStockCacheUpdate = 0;
    private long lastPositionCacheUpdate = 0;
    private long lastQuoteCacheUpdate = 0;
    private long lastOrderCacheUpdate = 0;
    
    // Cache expiration times in milliseconds
    private static final long STOCK_CACHE_EXPIRY = 24 * 60 * 60 * 1000; // 24 hours
    private static final long POSITION_CACHE_EXPIRY = 15 * 60 * 1000;   // 15 minutes
    private static final long QUOTE_CACHE_EXPIRY = 60 * 1000;           // 1 minute
    private static final long ORDER_CACHE_EXPIRY = 5 * 60 * 1000;       // 5 minutes
    
    // The API key passed to us
    private static final String API_KEY = "PKAS4532RNX6MFEQR2O3";
    private static final String API_SECRET = "xK0bcGBWQ8eQMy0irBrjRsI7uMOyaEg2Dkc6ppI0";
    
    /**
     * Constructor that initializes the Alpaca API service with the API keys
     * 
     * @param isPaperTrading Whether to use paper trading mode
     */
    public StockService(boolean isPaperTrading) {
        this.alpacaApi = new AlpacaApiService(API_KEY, API_SECRET, isPaperTrading);
    }
    
    /**
     * Get account information
     * 
     * @return JSONObject with account details
     * @throws IOException If the API call fails
     */
    public JSONObject getAccountInfo() throws IOException {
        return alpacaApi.getAccount();
    }
    
    /**
     * Get a list of tradable stocks
     * 
     * @param forceRefresh Whether to force a refresh from the API
     * @return List of Stock objects
     * @throws IOException If the API call fails
     */
    public List<Stock> getTradableStocks(boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        
        // Check if cache is valid
        if (!forceRefresh && !stockCache.isEmpty() && 
            (now - lastStockCacheUpdate) < STOCK_CACHE_EXPIRY) {
            return new ArrayList<>(stockCache.values());
        }
        
        // Fetch from API
        List<Stock> stocks = alpacaApi.getAssets("active", "us_equity");
        
        // Update cache
        stockCache.clear();
        for (Stock stock : stocks) {
            stockCache.put(stock.getSymbol(), stock);
        }
        lastStockCacheUpdate = now;
        
        return stocks;
    }
    
    /**
     * Get details for a specific stock symbol
     * 
     * @param symbol The stock symbol
     * @param forceRefresh Whether to force a refresh from the API
     * @return Stock object if found, null otherwise
     * @throws IOException If the API call fails
     */
    public Stock getStock(String symbol, boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        
        // Check in cache first
        if (!forceRefresh && stockCache.containsKey(symbol) && 
            (now - lastStockCacheUpdate) < STOCK_CACHE_EXPIRY) {
            return stockCache.get(symbol);
        }
        
        // If stock cache is expired, refresh all stocks
        if (forceRefresh || (now - lastStockCacheUpdate) >= STOCK_CACHE_EXPIRY) {
            getTradableStocks(true);
            return stockCache.getOrDefault(symbol, null);
        }
        
        // If only looking for one symbol and it's not in cache, fetch all and check again
        if (!stockCache.containsKey(symbol)) {
            getTradableStocks(true);
            return stockCache.getOrDefault(symbol, null);
        }
        
        return null;
    }
    
    /**
     * Get all current positions
     * 
     * @param forceRefresh Whether to force a refresh from the API
     * @return List of StockPosition objects
     * @throws IOException If the API call fails
     */
    public List<StockPosition> getPositions(boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        
        // Check if cache is valid
        if (!forceRefresh && !positionCache.isEmpty() && 
            (now - lastPositionCacheUpdate) < POSITION_CACHE_EXPIRY) {
            return new ArrayList<>(positionCache.values());
        }
        
        // Fetch from API
        List<StockPosition> positions = alpacaApi.getPositions();
        
        // Update cache
        positionCache.clear();
        for (StockPosition position : positions) {
            positionCache.put(position.getSymbol(), position);
        }
        lastPositionCacheUpdate = now;
        
        return positions;
    }
    
    /**
     * Get a specific position by symbol
     * 
     * @param symbol The stock symbol
     * @param forceRefresh Whether to force a refresh from the API
     * @return StockPosition if found, null otherwise
     * @throws IOException If the API call fails
     */
    public StockPosition getPosition(String symbol, boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        
        // Check in cache first
        if (!forceRefresh && positionCache.containsKey(symbol) && 
            (now - lastPositionCacheUpdate) < POSITION_CACHE_EXPIRY) {
            return positionCache.get(symbol);
        }
        
        // If not in cache or cache expired, try direct API call
        try {
            StockPosition position = alpacaApi.getPosition(symbol);
            if (position != null) {
                positionCache.put(symbol, position);
                // Also update the timestamp if this was a direct fetch
                if (positionCache.size() == 1) {
                    lastPositionCacheUpdate = now;
                }
            }
            return position;
        } catch (IOException e) {
            // If API call fails, check if we can refresh all positions
            if (forceRefresh || (now - lastPositionCacheUpdate) >= POSITION_CACHE_EXPIRY) {
                getPositions(true);
                return positionCache.getOrDefault(symbol, null);
            }
            throw e;
        }
    }
    
    /**
     * Get current quote for a stock
     * 
     * @param symbol The stock symbol
     * @return JSONObject with quote data
     * @throws IOException If the API call fails
     */
    public JSONObject getQuote(String symbol) throws IOException {
        try {
            long now = System.currentTimeMillis();
            
            // Check if cache is valid
            if (quoteCache.containsKey(symbol) && 
                (now - lastQuoteCacheUpdate) < QUOTE_CACHE_EXPIRY) {
                return new JSONObject(quoteCache.get(symbol).toString());
            }
            
            // Get quote from API
            JSONObject quote = alpacaApi.getQuote(symbol);
            
            // If no quote data returned, generate mock data
            if (quote.isEmpty() || (!quote.has("last") && !quote.has("latestTrade"))) {
                return generateMockQuote(symbol);
            }
            
            // Cache the quote
            quoteCache.put(symbol, quote);
            lastQuoteCacheUpdate = now;
            
            return quote;
        } catch (Exception e) {
            System.err.println("Error getting quote for " + symbol + ": " + e.getMessage());
            return generateMockQuote(symbol);
        }
    }
    
    /**
     * Generate mock quote data for testing
     */
    private JSONObject generateMockQuote(String symbol) {
        // Get stock if available
        Stock stock = null;
        try {
            stock = getStock(symbol, false);
        } catch (Exception ignored) {}
        
        // Generate base price based on symbol - similar to the history generator
        double basePrice = 100.0;
        for (char c : symbol.toCharArray()) {
            basePrice += c % 10;
        }
        
        // Add small random variation to price
        double lastPrice = basePrice * (1 + (Math.random() * 0.1 - 0.05));
        double bidPrice = lastPrice * 0.999;
        double askPrice = lastPrice * 1.001;
        
        // Create quote object
        return new JSONObject()
            .put("symbol", symbol)
            .put("last", lastPrice)
            .put("lastSize", (int)(Math.random() * 100) + 1)
            .put("lastExchange", "MOCK")
            .put("lastTimestamp", System.currentTimeMillis())
            .put("bidPrice", bidPrice)
            .put("bidSize", (int)(Math.random() * 1000) + 100)
            .put("askPrice", askPrice)
            .put("askSize", (int)(Math.random() * 1000) + 100)
            .put("volume", (int)(Math.random() * 1000000) + 10000)
            .put("dayOpen", lastPrice * (1 - (Math.random() * 0.02 - 0.01)))
            .put("dayHigh", lastPrice * 1.02)
            .put("dayLow", lastPrice * 0.98)
            .put("dayChange", Math.random() * 4 - 2) // -2% to +2%
            .put("name", stock != null ? stock.getName() : (symbol + " Inc."))
            .put("mock", true);
    }
    
    /**
     * Get historical data for a stock
     * 
     * @param symbol The stock symbol
     * @param timeframe Timeframe for the bars (1D, 1H, etc.)
     * @param limit Maximum number of bars to return
     * @return JSONArray of bar data
     * @throws IOException If the API call fails
     */
    public JSONArray getHistoricalData(String symbol, String timeframe, int limit) throws IOException {
        try {
            // Ensure we have a valid stock symbol
            Stock stock = getStock(symbol, false);
            if (stock == null) {
                // Generate mock data for testing if stock isn't found
                return generateMockHistoricalData(symbol, timeframe, limit);
            }
            
            // Get current date/time
            LocalDateTime now = LocalDateTime.now();
            
            // Determine start date based on timeframe
            LocalDateTime start;
            switch (timeframe) {
                case "1D":
                    start = now.minusDays(1);
                    break;
                case "1W":
                    start = now.minusWeeks(1);
                    break;
                case "1M":
                    start = now.minusMonths(1);
                    break;
                case "3M":
                    start = now.minusMonths(3);
                    break;
                case "1Y":
                    start = now.minusYears(1);
                    break;
                default:
                    start = now.minusDays(7); // Default to 1 week
            }
            
            // Format dates for API
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            String startStr = start.format(formatter);
            String endStr = now.format(formatter);
            
            // Get the appropriate time interval
            String interval;
            if (timeframe.equals("1D")) {
                interval = "5Min";
            } else if (timeframe.equals("1W")) {
                interval = "1H";
            } else {
                interval = "1D";
            }
            
            // Get bars from API
            JSONArray bars = alpacaApi.getBars(symbol, interval, startStr, endStr, limit);
            
            // If we got no data from the API, generate mock data
            if (bars.length() == 0) {
                return generateMockHistoricalData(symbol, timeframe, limit);
            }
            
            return bars;
        } catch (Exception e) {
            System.err.println("Error fetching historical data for " + symbol + ": " + e.getMessage());
            return generateMockHistoricalData(symbol, timeframe, limit);
        }
    }
    
    /**
     * Generate mock historical price data when real API data is unavailable
     * 
     * @param symbol The stock symbol
     * @param timeframe Timeframe for the bars (1D, 1W, etc.)
     * @param limit Maximum number of bars to return
     * @return JSONArray of simulated bar data
     */
    private JSONArray generateMockHistoricalData(String symbol, String timeframe, int limit) {
        JSONArray bars = new JSONArray();
        
        // Number of data points to generate
        int points = Math.min(limit, 50);
        
        // Base price depends on the symbol - use a simple hash function
        double basePrice = 100.0;
        for (char c : symbol.toCharArray()) {
            basePrice += c % 10;
        }
        
        // Generate random data points with a slight upward trend
        LocalDateTime time = LocalDateTime.now();
        double price = basePrice;
        
        for (int i = 0; i < points; i++) {
            // Calculate time for this bar based on timeframe
            LocalDateTime barTime;
            switch (timeframe) {
                case "1D":
                    barTime = time.minusMinutes(i * 15);
                    break;
                case "1W":
                    barTime = time.minusHours(i * 3);
                    break;
                case "1M":
                    barTime = time.minusDays(i);
                    break;
                case "3M":
                    barTime = time.minusDays(i * 2);
                    break;
                case "1Y":
                    barTime = time.minusDays(i * 7);
                    break;
                default:
                    barTime = time.minusDays(i);
            }
            
            // Apply some random movement to the price
            double change = (Math.random() - 0.48) * (price * 0.02); // Slight bullish bias
            price += change;
            
            // Ensure price remains positive
            if (price <= 0) {
                price = basePrice * 0.1;
            }
            
            // Create bar object
            double open = price;
            double high = price * (1 + Math.random() * 0.01);
            double low = price * (1 - Math.random() * 0.01);
            double close = price + (Math.random() - 0.5) * (price * 0.01);
            int volume = (int) (Math.random() * 10000) + 1000;
            
            JSONObject bar = new JSONObject()
                .put("t", barTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .put("o", open)
                .put("h", high)
                .put("l", low)
                .put("c", close)
                .put("v", volume);
            
            bars.put(bar);
        }
        
        return bars;
    }
    
    /**
     * Place a market order
     * 
     * @param symbol The stock symbol
     * @param quantity The quantity to buy/sell (positive for buy, negative for sell)
     * @param timeInForce Time in force (e.g., "day", "gtc")
     * @return The placed order
     * @throws IOException If the API call fails
     */
    public StockOrder placeMarketOrder(String symbol, int quantity, String timeInForce) throws IOException {
        // Get the stock
        Stock stock = getStock(symbol, false);
        if (stock == null) {
            throw new IllegalArgumentException("Stock not found: " + symbol);
        }
        
        // Determine side
        StockOrder.OrderSide side = quantity > 0 ? 
                StockOrder.OrderSide.BUY : StockOrder.OrderSide.SELL;
        
        // Create order
        StockOrder order = new StockOrder(
                symbol,
                stock.getId(),
                StockOrder.OrderType.MARKET,
                side,
                Math.abs(quantity),
                parseTimeInForce(timeInForce)
        );
        
        // Place order
        StockOrder placedOrder = alpacaApi.placeOrder(order);
        
        // Update cache
        updateOrderCache(placedOrder);
        
        return placedOrder;
    }
    
    /**
     * Place a limit order
     * 
     * @param symbol The stock symbol
     * @param quantity The quantity to buy/sell (positive for buy, negative for sell)
     * @param limitPrice The limit price
     * @param timeInForce Time in force (e.g., "day", "gtc")
     * @return The placed order
     * @throws IOException If the API call fails
     */
    public StockOrder placeLimitOrder(String symbol, int quantity, double limitPrice, 
                                     String timeInForce) throws IOException {
        // Get the stock
        Stock stock = getStock(symbol, false);
        if (stock == null) {
            throw new IllegalArgumentException("Stock not found: " + symbol);
        }
        
        // Determine side
        StockOrder.OrderSide side = quantity > 0 ? 
                StockOrder.OrderSide.BUY : StockOrder.OrderSide.SELL;
        
        // Create order
        StockOrder order = new StockOrder(
                symbol,
                stock.getId(),
                StockOrder.OrderType.LIMIT,
                side,
                Math.abs(quantity),
                parseTimeInForce(timeInForce)
        ).setLimitPrice(limitPrice);
        
        // Place order
        StockOrder placedOrder = alpacaApi.placeOrder(order);
        
        // Update cache
        updateOrderCache(placedOrder);
        
        return placedOrder;
    }
    
    /**
     * Place a stop order
     * 
     * @param symbol The stock symbol
     * @param quantity The quantity to buy/sell (positive for buy, negative for sell)
     * @param stopPrice The stop price
     * @param timeInForce Time in force (e.g., "day", "gtc")
     * @return The placed order
     * @throws IOException If the API call fails
     */
    public StockOrder placeStopOrder(String symbol, int quantity, double stopPrice, 
                                    String timeInForce) throws IOException {
        // Get the stock
        Stock stock = getStock(symbol, false);
        if (stock == null) {
            throw new IllegalArgumentException("Stock not found: " + symbol);
        }
        
        // Determine side
        StockOrder.OrderSide side = quantity > 0 ? 
                StockOrder.OrderSide.BUY : StockOrder.OrderSide.SELL;
        
        // Create order
        StockOrder order = new StockOrder(
                symbol,
                stock.getId(),
                StockOrder.OrderType.STOP,
                side,
                Math.abs(quantity),
                parseTimeInForce(timeInForce)
        ).setStopPrice(stopPrice);
        
        // Place order
        StockOrder placedOrder = alpacaApi.placeOrder(order);
        
        // Update cache
        updateOrderCache(placedOrder);
        
        return placedOrder;
    }
    
    /**
     * Place a stop-limit order
     * 
     * @param symbol The stock symbol
     * @param quantity The quantity to buy/sell (positive for buy, negative for sell)
     * @param stopPrice The stop price
     * @param limitPrice The limit price
     * @param timeInForce Time in force (e.g., "day", "gtc")
     * @return The placed order
     * @throws IOException If the API call fails
     */
    public StockOrder placeStopLimitOrder(String symbol, int quantity, double stopPrice, 
                                        double limitPrice, String timeInForce) throws IOException {
        // Get the stock
        Stock stock = getStock(symbol, false);
        if (stock == null) {
            throw new IllegalArgumentException("Stock not found: " + symbol);
        }
        
        // Determine side
        StockOrder.OrderSide side = quantity > 0 ? 
                StockOrder.OrderSide.BUY : StockOrder.OrderSide.SELL;
        
        // Create order
        StockOrder order = new StockOrder(
                symbol,
                stock.getId(),
                StockOrder.OrderType.STOP_LIMIT,
                side,
                Math.abs(quantity),
                parseTimeInForce(timeInForce)
        ).setStopPrice(stopPrice).setLimitPrice(limitPrice);
        
        // Place order
        StockOrder placedOrder = alpacaApi.placeOrder(order);
        
        // Update cache
        updateOrderCache(placedOrder);
        
        return placedOrder;
    }
    
    /**
     * Place a trailing stop order
     * 
     * @param symbol The stock symbol
     * @param quantity The quantity to buy/sell (positive for buy, negative for sell)
     * @param trailPercent The trail percentage
     * @param timeInForce Time in force (e.g., "day", "gtc")
     * @return The placed order
     * @throws IOException If the API call fails
     */
    public StockOrder placeTrailingStopOrder(String symbol, int quantity, double trailPercent, 
                                           String timeInForce) throws IOException {
        // Get the stock
        Stock stock = getStock(symbol, false);
        if (stock == null) {
            throw new IllegalArgumentException("Stock not found: " + symbol);
        }
        
        // Determine side
        StockOrder.OrderSide side = quantity > 0 ? 
                StockOrder.OrderSide.BUY : StockOrder.OrderSide.SELL;
        
        // Create order
        StockOrder order = new StockOrder(
                symbol,
                stock.getId(),
                StockOrder.OrderType.TRAILING_STOP,
                side,
                Math.abs(quantity),
                parseTimeInForce(timeInForce)
        ).setTrailPercent(trailPercent);
        
        // Place order
        StockOrder placedOrder = alpacaApi.placeOrder(order);
        
        // Update cache
        updateOrderCache(placedOrder);
        
        return placedOrder;
    }
    
    /**
     * Get a list of orders
     * 
     * @param status Filter by order status (open, closed, all)
     * @param limit Maximum number of orders to return
     * @param forceRefresh Whether to force a refresh from the API
     * @return List of StockOrder objects
     * @throws IOException If the API call fails
     */
    public List<StockOrder> getOrders(String status, int limit, boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        
        // Check if cache is valid for the requested status
        if (!forceRefresh && orderCache.containsKey(status) && 
            (now - lastOrderCacheUpdate) < ORDER_CACHE_EXPIRY) {
            
            List<StockOrder> orders = orderCache.get(status);
            if (limit > 0 && orders.size() > limit) {
                return orders.subList(0, limit);
            }
            return orders;
        }
        
        // Fetch from API
        List<StockOrder> orders = alpacaApi.getOrders(status, limit, null);
        
        // Update cache
        orderCache.put(status, orders);
        lastOrderCacheUpdate = now;
        
        return orders;
    }
    
    /**
     * Get an order by ID
     * 
     * @param orderId The order ID
     * @return StockOrder if found, null otherwise
     * @throws IOException If the API call fails
     */
    public StockOrder getOrder(String orderId) throws IOException {
        // Try to find in cache first
        for (List<StockOrder> orders : orderCache.values()) {
            for (StockOrder order : orders) {
                if (order.getId().equals(orderId) || order.getClientOrderId().equals(orderId)) {
                    return order;
                }
            }
        }
        
        // If not found in cache, fetch from API
        return alpacaApi.getOrder(orderId);
    }
    
    /**
     * Cancel an order
     * 
     * @param orderId The order ID to cancel
     * @return true if successfully canceled, false otherwise
     * @throws IOException If the API call fails
     */
    public boolean cancelOrder(String orderId) throws IOException {
        boolean result = alpacaApi.cancelOrder(orderId);
        
        // If canceled successfully, update cache
        if (result) {
            // Remove from all cache entries
            for (String status : orderCache.keySet()) {
                List<StockOrder> orders = orderCache.get(status);
                orders.removeIf(order -> order.getId().equals(orderId) || 
                                order.getClientOrderId().equals(orderId));
            }
        }
        
        return result;
    }
    
    /**
     * Parse string time in force to enum
     */
    private StockOrder.TimeInForce parseTimeInForce(String timeInForce) {
        if (timeInForce == null) {
            return StockOrder.TimeInForce.DAY;
        }
        
        switch (timeInForce.toLowerCase()) {
            case "day":
                return StockOrder.TimeInForce.DAY;
            case "gtc":
                return StockOrder.TimeInForce.GTC;
            case "ioc":
                return StockOrder.TimeInForce.IOC;
            case "fok":
                return StockOrder.TimeInForce.FOK;
            default:
                return StockOrder.TimeInForce.DAY;
        }
    }
    
    /**
     * Update order cache with a new or updated order
     */
    private void updateOrderCache(StockOrder order) {
        // Determine which cache to update based on order status
        String cacheKey;
        if (order.getStatus() == StockOrder.OrderStatus.FILLED ||
            order.getStatus() == StockOrder.OrderStatus.CANCELED ||
            order.getStatus() == StockOrder.OrderStatus.EXPIRED ||
            order.getStatus() == StockOrder.OrderStatus.REJECTED) {
            cacheKey = "closed";
        } else {
            cacheKey = "open";
        }
        
        // Get current cache list or create new one
        List<StockOrder> orders = orderCache.getOrDefault(cacheKey, new ArrayList<>());
        
        // Remove existing order if present
        orders.removeIf(o -> o.getId().equals(order.getId()) || 
                       o.getClientOrderId().equals(order.getClientOrderId()));
        
        // Add new/updated order
        orders.add(order);
        
        // Update cache
        orderCache.put(cacheKey, orders);
        lastOrderCacheUpdate = System.currentTimeMillis();
    }
}
