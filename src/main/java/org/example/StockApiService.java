package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service to interact with external stock APIs with enhanced caching and error handling
 */
@Service
public class StockApiService {
    private static final Logger logger = LoggerFactory.getLogger(StockApiService.class);
    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    // Alpha Vantage API settings
    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private static final int CACHE_EXPIRY_SECONDS = 60; // Cache expiry (1 minute)
    
    // API rate limiting - Alpha Vantage free tier limits
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long REQUEST_WINDOW_MS = 60 * 1000; // 1 minute in milliseconds
    
    // Request tracking for rate limiting
    private final Deque<Long> requestTimestamps = new LinkedList<>();
    
    // Local memory cache for API responses
    private final Map<String, CachedResponse> localCache = new ConcurrentHashMap<>();
    
    // Dependencies
    private final ConfigManager configManager;
    
    /**
     * Constructor with required dependencies
     */
    public StockApiService() {
        this.configManager = ConfigManager.getInstance();
        logger.info("StockApiService initialized with local memory cache");
    }
    
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
        boolean canMake = requestTimestamps.size() < MAX_REQUESTS_PER_MINUTE;
        
        if (!canMake) {
            logger.warn("API rate limit reached ({} requests per minute). Next request will be possible in {} seconds.", 
                    MAX_REQUESTS_PER_MINUTE,
                    (REQUEST_WINDOW_MS - (currentTime - requestTimestamps.peekFirst())) / 1000);
        }
        
        return canMake;
    }
    
    /**
     * Record that we've made a request
     */
    private synchronized void recordRequest() {
        requestTimestamps.addLast(System.currentTimeMillis());
        logger.debug("API request recorded. Total in current window: {}", requestTimestamps.size());
    }
    
    /**
     * Get the API key from configuration
     * @return The Alpha Vantage API key
     * @throws IOException If the API key is not available
     */
    private String getApiKey() throws IOException {
        // First try to get from environment variables via ConfigManager
        String apiKey = configManager.getAlphaVantageApiKey();
        
        // If not found, use the hardcoded key that's already in StockHandler
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Alpha Vantage API key not found in environment. Using fallback key.");
            apiKey = "2470IDOB57MHSDPZ"; // Hardcoded fallback key from StockHandler
        }
        
        return apiKey;
    }
    
    /**
     * Get stock quote from either cache or API
     * 
     * @param symbol The stock symbol
     * @return Stock object with current data
     * @throws IOException If an I/O error occurs
     */
    public Stock getStockQuote(String symbol) throws IOException {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Stock symbol cannot be empty");
        }
        
        symbol = symbol.trim().toUpperCase();
        String cacheKey = "quote_" + symbol;
        
        // Check local cache
        CachedResponse cached = localCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.info("Using local cached quote data for {}", symbol);
            return (Stock) cached.data;
        }
        
        // Check rate limits before making API call
        if (!canMakeRequest()) {
            throw new IOException("API rate limit reached (5 requests per minute). Please try again in a moment.");
        }
        
        logger.info("Fetching stock quote from API for symbol: {}", symbol);
        long startTime = System.currentTimeMillis();
        
        try {
            String apiKey = getApiKey();
            String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + apiKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            recordRequest();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            
            // Log response time for monitoring
            long responseTime = System.currentTimeMillis() - startTime;
            logger.debug("API response received in {}ms for symbol: {}", responseTime, symbol);
            
            if (response.statusCode() != 200) {
                logger.error("API returned non-200 status code: {} for symbol: {}", response.statusCode(), symbol);
                throw new IOException("API returned error status: " + response.statusCode());
            }
            
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            // Check for API errors
            if (json.has("Error Message")) {
                String errorMsg = json.get("Error Message").getAsString();
                logger.error("API Error for symbol {}: {}", symbol, errorMsg);
                throw new IOException("API Error: " + errorMsg);
            }
            
            if (json.has("Note") && json.get("Note").getAsString().contains("API call frequency")) {
                String noteMsg = json.get("Note").getAsString();
                logger.error("API daily limit reached for symbol {}: {}", symbol, noteMsg);
                throw new IOException("API daily limit reached: " + noteMsg);
            }
            
            if (!json.has("Global Quote") || json.get("Global Quote").getAsJsonObject().size() == 0) {
                logger.error("Stock data not found for symbol: {}", symbol);
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
            
            // Cache the response in local memory
            localCache.put(cacheKey, new CachedResponse(stock));
            
            logger.info("Successfully retrieved stock quote for {}: ${}", symbol, stock.getPrice());
            return stock;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Request interrupted for symbol {}: {}", symbol, e.getMessage());
            throw new IOException("Request interrupted: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching quote for {}: {}", symbol, e.getMessage(), e);
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
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Stock symbol cannot be empty");
        }
        
        if (timeframe == null || timeframe.trim().isEmpty()) {
            timeframe = "1M"; // Default to 1 month if not specified
        }
        
        symbol = symbol.trim().toUpperCase();
        timeframe = timeframe.trim();
        String cacheKey = "history_" + symbol + "_" + timeframe;
        
        // Check local cache
        CachedResponse cached = localCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.info("Using local cached history data for {} ({})", symbol, timeframe);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) cached.data;
            return result;
        }
        
        // Check rate limits before making API call
        if (!canMakeRequest()) {
            throw new IOException("API rate limit reached (5 requests per minute). Please try again in a moment.");
        }
        
        logger.info("Fetching stock history from API for {} with timeframe {}", symbol, timeframe);
        long startTime = System.currentTimeMillis();
        
        try {
            String apiKey = getApiKey();
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
                    logger.warn("Unknown timeframe: {}. Falling back to daily data.", timeframe);
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
            
            // Log response time for monitoring
            long responseTime = System.currentTimeMillis() - startTime;
            logger.debug("API response received in {}ms for {} history ({})", responseTime, symbol, timeframe);
            
            if (response.statusCode() != 200) {
                logger.error("API returned non-200 status code: {} for {} history", response.statusCode(), symbol);
                throw new IOException("API returned error status: " + response.statusCode());
            }
            
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            // Check for API errors
            if (json.has("Error Message")) {
                String errorMsg = json.get("Error Message").getAsString();
                logger.error("API Error for {} history: {}", symbol, errorMsg);
                throw new IOException("API Error: " + errorMsg);
            }
            
            if (json.has("Note") && json.get("Note").getAsString().contains("API call frequency")) {
                String noteMsg = json.get("Note").getAsString();
                logger.error("API daily limit reached for {} history: {}", symbol, noteMsg);
                throw new IOException("API daily limit reached: " + noteMsg);
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
                logger.error("Failed to find time series data in response for {}", symbol);
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
                    LocalDateTime localDateTime;
                    if (dateTime.contains(":")) {
                        // Format: yyyy-MM-dd HH:mm:ss
                        localDateTime = LocalDateTime.parse(
                            dateTime, 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        );
                    } else {
                        // Format: yyyy-MM-dd
                        localDateTime = LocalDateTime.parse(
                            dateTime + "T00:00:00", 
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        );
                    }
                    timestamp = localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
                } catch (Exception e) {
                    logger.warn("Failed to parse date: {}", dateTime);
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
            
            // Cache the response in local memory
            localCache.put(cacheKey, new CachedResponse(historyData));
            
            logger.info("Successfully retrieved {} history points for {} ({})", historyData.size(), symbol, timeframe);
            return historyData;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Request interrupted for {} history: {}", symbol, e.getMessage());
            throw new IOException("Request interrupted: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching history for {}: {}", symbol, e.getMessage(), e);
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
            logger.warn("Failed to parse double for key: {}", key);
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
            logger.warn("Failed to parse long for key: {}", key);
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
            logger.warn("Failed to parse percentage for key: {}", key);
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
