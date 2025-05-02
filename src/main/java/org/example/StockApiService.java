package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service to interact with external stock APIs
 */
public class StockApiService {
    private static final Logger LOGGER = Logger.getLogger(StockApiService.class.getName());
    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    // Alpha Vantage API settings
    private static String apiKey;
    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private static final int CACHE_EXPIRY_SECONDS = 300; // Cache data for 5 minutes to avoid hitting rate limits
    
    // Static initializer to load API key
    static {
        try {
            ConfigManager configManager = ConfigManager.getInstance();
            apiKey = configManager.getAlphaVantageApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                LOGGER.severe("Alpha Vantage API key not found in configuration");
            } else {
                LOGGER.info("Alpha Vantage API initialized with key: " + 
                    (apiKey.length() > 4 ? apiKey.substring(0, 4) + "..." : "****"));
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize Alpha Vantage API key: " + e.getMessage());
        }
    }
    
    // API rate limiting - Alpha Vantage free tier limits
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long REQUEST_WINDOW_MS = 60 * 1000; // 1 minute in milliseconds
    
    // Request tracking for rate limiting
    private final Deque<Long> requestTimestamps = new LinkedList<>();
    
    // Cache for API responses to reduce API calls
    private final Map<String, CachedResponse> responseCache = new ConcurrentHashMap<>();
    
    /**
     * Class to store cached API responses
     */
    private static class CachedResponse {
        final Object data;
        final Instant timestamp;
        
        CachedResponse(Object data) {
            this.data = data;
            this.timestamp = Instant.now();
        }
        
        boolean isExpired() {
            return Duration.between(timestamp, Instant.now()).getSeconds() > CACHE_EXPIRY_SECONDS;
        }
    }
    
    /**
     * Check if we can make another API request within rate limits
     * @return true if a request can be made, false otherwise
     */
    private synchronized boolean canMakeRequest() {
        final long currentTime = System.currentTimeMillis();
        
        // Remove timestamps older than the window
        while (!requestTimestamps.isEmpty() && (currentTime - requestTimestamps.peekFirst() > REQUEST_WINDOW_MS)) {
            requestTimestamps.removeFirst();
        }
        
        // Check if we're under the limit
        return requestTimestamps.size() < MAX_REQUESTS_PER_MINUTE;
    }
    
    /**
     * Record that we've made a request
     */
    private synchronized void recordRequest() {
        requestTimestamps.addLast(System.currentTimeMillis());
    }
    
    /**
     * Get stock quote from either cache or API
     * 
     * @param symbol The stock symbol
     * @return Stock object with current data
     * @throws IOException If an I/O error occurs
     */
    public Stock getStockQuote(String symbol) throws IOException {
        // Check cache first
        String cacheKey = "quote_" + symbol;
        CachedResponse cached = responseCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            LOGGER.info("Using cached quote data for " + symbol);
            return (Stock) cached.data;
        }
        
        // Check rate limits before making API call
        if (!canMakeRequest()) {
            throw new IOException("API rate limit reached (5 requests per minute). Please try again in a moment.");
        }
        
        try {
            String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + apiKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            recordRequest();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            // Check for API errors
            if (json.has("Error Message")) {
                throw new IOException("API Error: " + json.get("Error Message").getAsString());
            }
            
            if (json.has("Note") && json.get("Note").getAsString().contains("API call frequency")) {
                throw new IOException("API daily limit reached: " + json.get("Note").getAsString());
            }
            
            if (!json.has("Global Quote") || json.get("Global Quote").getAsJsonObject().size() == 0) {
                throw new IOException("Stock symbol '" + symbol + "' not found or API limit reached");
            }
            
            JsonObject quote = json.getAsJsonObject("Global Quote");
            
            Stock stock = new Stock(symbol);
            stock.setPrice(parseDouble(quote, "05. price"));
            stock.setPreviousClose(parseDouble(quote, "08. previous close"));
            stock.setOpen(parseDouble(quote, "02. open"));
            stock.setHigh(parseDouble(quote, "03. high"));
            stock.setLow(parseDouble(quote, "04. low"));
            stock.setVolume(parseLong(quote, "06. volume"));
            stock.setChange(parseDouble(quote, "09. change"));
            stock.setChangePercent(parsePercentage(quote, "10. change percent"));
            stock.setLastUpdated(getStringValue(quote, "07. latest trading day"));
            stock.setName(getCompanyName(symbol));
            
            // Cache the response
            responseCache.put(cacheKey, new CachedResponse(stock));
            
            return stock;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching quote for " + symbol, e);
            throw new IOException("Failed to get stock data: " + e.getMessage());
        }
    }
    
    /**
     * Get stock price history for a timeframe with caching
     * 
     * @param symbol The stock symbol
     * @param timeframe The timeframe (1D, 1W, 1M, 3M, 1Y)
     * @return List of price/volume data points
     * @throws IOException If an I/O error occurs
     */
    public List<Map<String, Object>> getStockHistory(String symbol, String timeframe) throws IOException {
        // Check cache first
        String cacheKey = "history_" + symbol + "_" + timeframe;
        CachedResponse cached = responseCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            LOGGER.info("Using cached history data for " + symbol + " (" + timeframe + ")");
            return (List<Map<String, Object>>) cached.data;
        }
        
        // Check rate limits before making API call
        if (!canMakeRequest()) {
            throw new IOException("API rate limit reached (5 requests per minute). Please try again in a moment.");
        }
        
        try {
            String function;
            
            // Map timeframe to Alpha Vantage function and interval
            switch (timeframe) {
                case "1D":
                    function = "TIME_SERIES_INTRADAY&interval=5min";
                    break;
                case "1W":
                    function = "TIME_SERIES_INTRADAY&interval=60min";
                    break;
                case "1M":
                case "3M":
                    function = "TIME_SERIES_DAILY";
                    break;
                case "1Y":
                    function = "TIME_SERIES_WEEKLY";
                    break;
                default:
                    function = "TIME_SERIES_DAILY";
            }
            
            String url = BASE_URL + "?function=" + function + "&symbol=" + symbol + "&apikey=" + apiKey;
            if (function.contains("INTRADAY")) {
                url += "&outputsize=full";
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            recordRequest();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            // Check for API errors
            if (json.has("Error Message")) {
                throw new IOException("API Error: " + json.get("Error Message").getAsString());
            }
            
            if (json.has("Note") && json.get("Note").getAsString().contains("API call frequency")) {
                throw new IOException("API daily limit reached: " + json.get("Note").getAsString());
            }
            
            // Find the time series data property - different for each function
            String timeSeriesKey = null;
            for (String key : json.keySet()) {
                if (key.contains("Time Series")) {
                    timeSeriesKey = key;
                    break;
                }
            }
            
            if (timeSeriesKey == null) {
                throw new IOException("Failed to find time series data in response for " + symbol);
            }
            
            JsonObject timeSeries = json.getAsJsonObject(timeSeriesKey);
            List<Map<String, Object>> historyData = new ArrayList<>();
            
            // Limit the number of data points based on timeframe
            int limit;
            switch (timeframe) {
                case "1D":
                    limit = 78; // For 5min intraday (6.5 hours = 78 5-min periods)
                    break;
                case "1W":
                    limit = 5 * 7; // 5 days with 7 hourly data points
                    break;
                case "1M":
                    limit = 30;
                    break;
                case "3M":
                    limit = 90;
                    break;
                case "1Y":
                    limit = 52;
                    break;
                default:
                    limit = 30;
            }
            
            int count = 0;
            for (String dateTime : timeSeries.keySet()) {
                if (count >= limit) break;
                
                JsonObject dataPoint = timeSeries.getAsJsonObject(dateTime);
                Map<String, Object> point = new HashMap<>();
                
                // Get timestamp in milliseconds
                long timestamp = 0;
                try {
                    // Try to parse the date/time string to epoch milliseconds
                    // The exact format depends on the API function
                    java.time.LocalDateTime localDateTime;
                    if (dateTime.contains(":")) {
                        // Format: yyyy-MM-dd HH:mm:ss
                        localDateTime = java.time.LocalDateTime.parse(
                            dateTime, 
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        );
                    } else {
                        // Format: yyyy-MM-dd
                        localDateTime = java.time.LocalDateTime.parse(
                            dateTime + "T00:00:00", 
                            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        );
                    }
                    timestamp = localDateTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
                } catch (Exception e) {
                    LOGGER.warning("Failed to parse date: " + dateTime);
                    // Use the count as a fallback to ensure points are in order
                    timestamp = System.currentTimeMillis() - (count * 24 * 60 * 60 * 1000L);
                }
                
                point.put("timestamp", timestamp);
                
                // Different APIs use different field names
                if (dataPoint.has("1. open")) {
                    point.put("price", parseDouble(dataPoint, "4. close"));
                    point.put("open", parseDouble(dataPoint, "1. open"));
                    point.put("high", parseDouble(dataPoint, "2. high"));
                    point.put("low", parseDouble(dataPoint, "3. low"));
                    point.put("volume", parseLong(dataPoint, "5. volume"));
                } else {
                    point.put("price", parseDouble(dataPoint, "close"));
                    point.put("open", parseDouble(dataPoint, "open"));
                    point.put("high", parseDouble(dataPoint, "high"));
                    point.put("low", parseDouble(dataPoint, "low"));
                    point.put("volume", parseLong(dataPoint, "volume"));
                }
                
                historyData.add(point);
                count++;
            }
            
            // Cache the response
            responseCache.put(cacheKey, new CachedResponse(historyData));
            
            return historyData;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching history for " + symbol, e);
            throw new IOException("Failed to get stock history: " + e.getMessage());
        }
    }
    
    /**
     * Helper method to get company name for a symbol.
     * In a real implementation, you would call a separate API endpoint
     * to get full company details.
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
    
    /**
     * Helper method to safely parse double values from JSON
     */
    private double parseDouble(JsonObject json, String key) {
        try {
            if (json.has(key)) {
                return Double.parseDouble(json.get(key).getAsString().trim());
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Failed to parse double for key: " + key);
        }
        return 0.0;
    }
    
    /**
     * Helper method to safely parse long values from JSON
     */
    private long parseLong(JsonObject json, String key) {
        try {
            if (json.has(key)) {
                return Long.parseLong(json.get(key).getAsString().trim());
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Failed to parse long for key: " + key);
        }
        return 0L;
    }
    
    /**
     * Helper method to parse percentage strings
     */
    private double parsePercentage(JsonObject json, String key) {
        try {
            if (json.has(key)) {
                String percentStr = json.get(key).getAsString().trim();
                // Remove the % character if present
                if (percentStr.endsWith("%")) {
                    percentStr = percentStr.substring(0, percentStr.length() - 1);
                }
                return Double.parseDouble(percentStr);
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Failed to parse percentage for key: " + key);
        }
        return 0.0;
    }
    
    /**
     * Helper method to get string values from JSON
     */
    private String getStringValue(JsonObject json, String key) {
        if (json.has(key)) {
            return json.get(key).getAsString().trim();
        }
        return "";
    }
}
