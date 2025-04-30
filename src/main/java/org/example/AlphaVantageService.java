package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 * Service for accessing Alpha Vantage API
 */
public class AlphaVantageService {
    private static final Logger logger = Logger.getLogger(AlphaVantageService.class.getName());
    private static AlphaVantageService instance;
    private boolean initialized = false;
    private final String apiKey;
    private final HttpClient httpClient;
    
    /**
     * Private constructor for singleton pattern
     */
    private AlphaVantageService() {
        ConfigManager configManager = ConfigManager.getInstance();
        this.apiKey = configManager.getAlphaVantageApiKey();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warning("Alpha Vantage API key not found in configuration");
        } else {
            logger.info("Alpha Vantage API initialized with key: " + 
                (apiKey.length() > 4 ? apiKey.substring(0, 4) + "..." : "****"));
            initialized = true;
        }
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized AlphaVantageService getInstance() {
        if (instance == null) {
            instance = new AlphaVantageService();
        }
        return instance;
    }
    
    /**
     * Initialize the Alpha Vantage API
     */
    public void initialize() {
        if (initialized) {
            logger.info("AlphaVantage API already initialized");
            return;
        }
        
        ConfigManager configManager = ConfigManager.getInstance();
        String newApiKey = configManager.getAlphaVantageApiKey();
        
        if (newApiKey == null || newApiKey.isEmpty()) {
            logger.warning("Alpha Vantage API key not configured");
            return;
        }
        
        initialized = true;
        logger.info("AlphaVantage API initialized with key: " + 
            (newApiKey.length() > 4 ? newApiKey.substring(0, 4) + "..." : "****"));
    }
    
    /**
     * Get stock quote for a symbol
     * 
     * @param symbol Stock symbol
     * @return JSONObject with quote data or null on error
     */
    public JSONObject getStockQuote(String symbol) {
        if (!initialized) {
            initialize();
        }
        
        if (!initialized) {
            logger.severe("Cannot get stock quote - Alpha Vantage API not initialized");
            return null;
        }
        
        try {
            String url = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + apiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            } else {
                logger.warning("Error response from Alpha Vantage API: " + response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error calling Alpha Vantage API", e);
            return null;
        }
    }
    
    /**
     * Get daily time series data for a stock
     * 
     * @param symbol Stock symbol
     * @return JSONObject with daily time series data or null on error
     */
    public JSONObject getDailyTimeSeries(String symbol) {
        if (!initialized) {
            initialize();
        }
        
        if (!initialized) {
            logger.severe("Cannot get daily time series - Alpha Vantage API not initialized");
            return null;
        }
        
        try {
            String url = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&apikey=" + apiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            } else {
                logger.warning("Error response from Alpha Vantage API: " + response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error calling Alpha Vantage API", e);
            return null;
        }
    }
    
    /**
     * Check if a symbol is valid by attempting to fetch its data
     * 
     * @param symbol Stock symbol
     * @return true if the symbol is valid
     */
    public boolean isValidSymbol(String symbol) {
        JSONObject response = getStockQuote(symbol);
        if (response == null) {
            return false;
        }
        
        // Check if response contains an error message
        if (response.has("Error Message") || response.has("Note")) {
            return false;
        }
        
        // Check if the response has Global Quote data
        if (!response.has("Global Quote") || response.getJSONObject("Global Quote").isEmpty()) {
            return false;
        }
        
        return true;
    }

    /**
     * Get daily stock data for a symbol
     * For backward compatibility with existing code
     */
    public Map<String, Object> getDailyStockData(String symbol) {
        if (!initialized) {
            initialize();
        }
        
        logger.info("Fetching daily stock data for: " + symbol);
        
        JSONObject result = getDailyTimeSeries(symbol);
        if (result == null) {
            return new HashMap<>();
        }
        
        // Convert JSONObject to Map for compatibility with existing code
        Map<String, Object> resultMap = new HashMap<>();
        for (String key : result.keySet()) {
            resultMap.put(key, result.get(key));
        }
        
        return resultMap;
    }
    
    /**
     * Callback interface for handling async responses
     * Kept for backward compatibility
     */
    public interface ResponseCallback<T> {
        void onSuccess(T response);
        void onFailure(Exception exception);
    }
    
    /**
     * Get latest stock quote data asynchronously
     * For backward compatibility with existing code
     */
    public void getDailyStockDataAsync(String symbol, ResponseCallback<Map<String, Object>> callback) {
        if (!initialized) {
            initialize();
        }
        
        logger.info("Fetching daily stock data asynchronously for: " + symbol);
        
        CompletableFuture.supplyAsync(() -> {
            JSONObject result = getDailyTimeSeries(symbol);
            if (result == null) {
                return new HashMap<String, Object>();
            }
            
            // Convert JSONObject to Map for compatibility
            Map<String, Object> resultMap = new HashMap<>();
            for (String key : result.keySet()) {
                resultMap.put(key, result.get(key));
            }
            
            return resultMap;
        }).thenAccept(callback::onSuccess)
          .exceptionally(ex -> {
              callback.onFailure(new Exception(ex));
              return null;
          });
    }
    
    /**
     * Simple data class for stock data
     * Kept for backward compatibility
     */
    public record StockData(String symbol, double price, double high, double low, double open, double volume) {}
}
