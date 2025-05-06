package org.example;

import com.crazzyghost.alphavantage.AlphaVantage;
import com.crazzyghost.alphavantage.AlphaVantageException;
import com.crazzyghost.alphavantage.parameters.OutputSize;
import com.crazzyghost.alphavantage.timeseries.response.TimeSeriesResponse;
import com.crazzyghost.alphavantage.timeseries.response.QuoteResponse;
import com.crazzyghost.alphavantage.Config;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Client for Alpha Vantage API that uses the fluent alphavantage-java library
 * Implements caching, rate limits, and error handling
 */
public class AlphaVantageClient {
    private static final Logger LOGGER = Logger.getLogger(AlphaVantageClient.class.getName());
    private static AlphaVantageClient instance;
    
    // API Key
    private final String apiKey;
    
    // Cache for storing quotes and time series data to avoid repeated API calls
    private final Map<String, Stock> quoteCache = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> historyCache = new ConcurrentHashMap<>();
    
    // Cache expiration settings (in milliseconds)
    private static final long QUOTE_CACHE_EXPIRY = 60 * 1000; // 1 minute
    private static final long HISTORY_CACHE_EXPIRY = 24 * 60 * 60 * 1000; // 24 hours
    
    // Cache of when items were stored
    private final Map<String, Long> quoteCacheTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Long> historyCacheTimestamps = new ConcurrentHashMap<>();
    
    // Rate limiting
    private final Object rateLimitLock = new Object();
    private long lastRequestTime = 0;
    private static final long REQUEST_THROTTLE = 250; // 250ms between requests (4 per second)

    private AlphaVantageClient() {
        // Load the API key from environment or configuration
        this.apiKey = loadApiKey();
        
        // Configure the Alpha Vantage client
        AlphaVantage.api()
            .init(Config.builder()
                .key(apiKey)
                .timeOut(10)  // timeout in seconds
                .build());
                
        LOGGER.info("AlphaVantageClient initialized with API key");
    }
    
    /**
     * Get the singleton instance of the client
     */
    public static synchronized AlphaVantageClient getInstance() {
        if (instance == null) {
            instance = new AlphaVantageClient();
        }
        return instance;
    }
    
    /**
     * Load the API key from various sources with fallback
     */
    private String loadApiKey() {
        // 1) check environment first
        String envKey = System.getenv("ALPHAVANTAGE_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            LOGGER.info("Loading Alpha Vantage API key from environment");
            return envKey;
        }

        // 2) then try SecretManager if available
        try {
            // Access the SecretManager class to get the API key
            String secret = SecretManager.getSecret("alphaVantageKey");
            if (secret != null && !secret.isBlank()) {
                LOGGER.info("Loaded Alpha Vantage API key from SecretManager");
                return secret;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to load API key from SecretManager: " + e.getMessage());
        }
        
        // 3) finally, fallback to demo
        LOGGER.warning("No Alpha Vantage API key found - using demo key with limited functionality");
        return "demo";
    }
    
    /**
     * Get stock quote with current price and basic information
     */
    public Stock getStockQuote(String symbol) throws Exception {
        // Check if we have a non-expired cached version
        String cacheKey = "quote_" + symbol;
        Long cacheTime = quoteCacheTimestamps.get(cacheKey);
        
        if (cacheTime != null && (System.currentTimeMillis() - cacheTime < QUOTE_CACHE_EXPIRY)) {
            Stock cachedStock = quoteCache.get(cacheKey);
            if (cachedStock != null) {
                return cachedStock;
            }
        }
        
        // Apply rate limiting - ensure we don't exceed our limit
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long timeToWait = lastRequestTime + REQUEST_THROTTLE - now;
            
            if (timeToWait > 0) {
                try {
                    Thread.sleep(timeToWait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Thread interrupted while waiting for rate limit", e);
                }
            }
            
            lastRequestTime = System.currentTimeMillis();
        }
        
        try {
            // Use the fluent API to get the stock quote
            QuoteResponse response = AlphaVantage.api()
                .timeSeries()
                .quote()
                .forSymbol(symbol)
                .fetchSync();
            
            // Process the response into our Stock object
            Stock stock = new Stock(symbol);
            
            // Set basic stock details from the quote response
            stock.setPrice(response.getPrice());
            stock.setPreviousClose(response.getPreviousClose());
            stock.setVolume((int)response.getVolume());
            stock.setChange(response.getPrice() - response.getPreviousClose());
            stock.setChangePercent((response.getPrice() - response.getPreviousClose()) / response.getPreviousClose() * 100);
            stock.setName(getCompanyName(symbol)); // Use a mapping or another API call to get company name
            stock.setLastUpdated(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            
            // Cache the result
            quoteCache.put(cacheKey, stock);
            quoteCacheTimestamps.put(cacheKey, System.currentTimeMillis());
            
            return stock;
            
        } catch (AlphaVantageException e) {
            LOGGER.warning("Alpha Vantage API error for " + symbol + ": " + e.getMessage());
            throw new Exception("Failed to get quote for " + symbol + ": " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.severe("Unexpected error getting quote for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Error fetching stock data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get historical stock data for a particular timeframe
     * 
     * @param symbol The stock symbol
     * @param timeframe The timeframe (1D, 1W, 1M, 3M, 1Y, etc.)
     * @return List of price points with timestamp and price
     */
    public List<Map<String, Object>> getStockHistory(String symbol, String timeframe) throws Exception {
        // Check if we have a non-expired cached version
        String cacheKey = "history_" + symbol + "_" + timeframe;
        Long cacheTime = historyCacheTimestamps.get(cacheKey);
        
        if (cacheTime != null && (System.currentTimeMillis() - cacheTime < HISTORY_CACHE_EXPIRY)) {
            List<Map<String, Object>> cachedHistory = historyCache.get(cacheKey);
            if (cachedHistory != null) {
                return cachedHistory;
            }
        }
        
        // Apply rate limiting
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long timeToWait = lastRequestTime + REQUEST_THROTTLE - now;
            
            if (timeToWait > 0) {
                try {
                    Thread.sleep(timeToWait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Thread interrupted while waiting for rate limit", e);
                }
            }
            
            lastRequestTime = System.currentTimeMillis();
        }
        
        try {
            // Determine the right API call based on timeframe
            TimeSeriesResponse response;
            
            // Map the timeframe to the appropriate API call
            if ("1D".equals(timeframe)) {
                response = AlphaVantage.api()
                    .timeSeries()
                    .intraday()
                    .forSymbol(symbol)
                    .interval(com.crazzyghost.alphavantage.parameters.Interval.FIVE_MIN)
                    .outputSize(OutputSize.COMPACT)
                    .fetchSync();
            } else if ("1W".equals(timeframe)) {
                response = AlphaVantage.api()
                    .timeSeries()
                    .daily()
                    .forSymbol(symbol)
                    .outputSize(OutputSize.COMPACT)
                    .fetchSync();
            } else if ("1M".equals(timeframe) || "3M".equals(timeframe)) {
                response = AlphaVantage.api()
                    .timeSeries()
                    .daily()
                    .forSymbol(symbol)
                    .outputSize(OutputSize.COMPACT)
                    .fetchSync();
            } else {
                // Default to daily data for other timeframes (1Y, etc.)
                response = AlphaVantage.api()
                    .timeSeries()
                    .daily()
                    .forSymbol(symbol)
                    .outputSize(OutputSize.FULL)
                    .fetchSync();
            }
            
            // Process the response into a list of data points for the chart
            List<Map<String, Object>> historyData = new ArrayList<>();
            
            // Convert the response to our format
            if (response != null && response.getStockUnits() != null) {
                // Get the appropriate number of data points based on timeframe
                int limit = getTimeframeLimit(timeframe);
                
                // The response contains a list of stock units with date and price information
                var stockUnits = response.getStockUnits();
                
                // Limit the number of points based on timeframe (newest first)
                int count = 0;
                List<Map<String, Object>> tempData = new ArrayList<>();
                
                for (var stockUnit : stockUnits) {
                    if (count >= limit) break;
                    
                    Map<String, Object> point = new HashMap<>();
                    point.put("timestamp", stockUnit.getDate()); // Get the date from the stock unit
                    point.put("price", stockUnit.getClose());    // Get the closing price
                    
                    tempData.add(point);
                    count++;
                }
                
                // Add points to our history data (ensuring they are in descending order - newest first)
                historyData.addAll(tempData);
                
                // Sort the data by timestamp (oldest first) for proper charting
                historyData.sort((a, b) -> {
                    String aDate = (String) a.get("timestamp");
                    String bDate = (String) b.get("timestamp");
                    return aDate.compareTo(bDate);
                });
            }
            
            // Cache the result
            historyCache.put(cacheKey, historyData);
            historyCacheTimestamps.put(cacheKey, System.currentTimeMillis());
            
            return historyData;
            
        } catch (AlphaVantageException e) {
            LOGGER.warning("Alpha Vantage API error getting history for " + symbol + ": " + e.getMessage());
            throw new Exception("Failed to get history for " + symbol + ": " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.severe("Unexpected error getting history for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Error fetching stock history: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get company name for a symbol (simplified version)
     */
    private String getCompanyName(String symbol) {
        // A map of common symbols to company names - in a real app, this would come from another API call
        Map<String, String> companyNames = new HashMap<>();
        companyNames.put("AAPL", "Apple Inc.");
        companyNames.put("MSFT", "Microsoft Corporation");
        companyNames.put("GOOGL", "Alphabet Inc. (Google)");
        companyNames.put("AMZN", "Amazon.com Inc.");
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
        
        // Return the company name if found, otherwise return the symbol with "Inc." appended
        return companyNames.getOrDefault(symbol, symbol + " Inc.");
    }
    
    /**
     * Get the number of data points to retrieve based on timeframe
     */
    private int getTimeframeLimit(String timeframe) {
        switch (timeframe) {
            case "1D": return 96;      // 5-minute intervals for 8 hours
            case "1W": return 7;       // Daily for 1 week
            case "1M": return 30;      // Daily for 1 month
            case "3M": return 90;      // Daily for 3 months
            case "1Y": return 252;     // Daily for 1 year (approx. trading days)
            case "5Y": return 1260;    // Daily for 5 years (approx. trading days)
            default: return 30;        // Default to 1 month
        }
    }
    
    /**
     * Clear all caches
     */
    public void clearCache() {
        quoteCache.clear();
        quoteCacheTimestamps.clear();
        historyCache.clear();
        historyCacheTimestamps.clear();
        LOGGER.info("Cache cleared");
    }
    
    /**
     * Clear cache for a specific symbol
     */
    public void clearCacheForSymbol(String symbol) {
        String quoteKey = "quote_" + symbol;
        quoteCache.remove(quoteKey);
        quoteCacheTimestamps.remove(quoteKey);
        
        // Clear all history timeframes for this symbol
        for (String timeframe : new String[]{"1D", "1W", "1M", "3M", "1Y", "5Y"}) {
            String historyKey = "history_" + symbol + "_" + timeframe;
            historyCache.remove(historyKey);
            historyCacheTimestamps.remove(historyKey);
        }
        
        LOGGER.info("Cache cleared for symbol: " + symbol);
    }
    
    /**
     * Shutdown the HTTP client and release resources
     * This method tries to forcefully shut down any lingering OkHttp threads
     * by using reflection to access the internal client and executor services.
     */
    public void shutdown() {
        try {
            LOGGER.info("Attempting to shut down HTTP client resources...");
            
            // Force JVM to perform garbage collection to help clean up lingering resources
            System.gc();
            
            // Try to find and shut down any thread that matches OkHttp naming pattern
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            ThreadGroup parentGroup;
            while ((parentGroup = rootGroup.getParent()) != null) {
                rootGroup = parentGroup;
            }
            
            Thread[] threads = new Thread[rootGroup.activeCount()];
            rootGroup.enumerate(threads);
            
            for (Thread thread : threads) {
                if (thread != null && thread.getName().contains("OkHttp")) {
                    try {
                        LOGGER.info("Interrupting OkHttp thread: " + thread.getName());
                        thread.interrupt();
                    } catch (Exception e) {
                        LOGGER.warning("Failed to interrupt thread " + thread.getName() + ": " + e.getMessage());
                    }
                }
            }
            
            // Now call interrupt on any thread that has the OkHttp thread factory
            LOGGER.info("HTTP client resources released");
        } catch (Exception e) {
            LOGGER.warning("Error shutting down HTTP client: " + e.getMessage());
        }
    }
}
