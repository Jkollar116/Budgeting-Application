package org.example;

import com.crazzyghost.alphavantage.AlphaVantage;
import com.crazzyghost.alphavantage.Config;
import com.crazzyghost.alphavantage.parameters.OutputSize;
import com.crazzyghost.alphavantage.parameters.Interval;
import com.crazzyghost.alphavantage.timeseries.response.StockUnit;
import com.crazzyghost.alphavantage.timeseries.response.TimeSeriesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for accessing Alpha Vantage API using the crazzyghost/alphavantage-java library
 */
public class AlphaVantageService {
    private static final Logger logger = LoggerFactory.getLogger(AlphaVantageService.class);
    private static AlphaVantageService instance;
    private boolean initialized = false;
    
    /**
     * Private constructor for singleton pattern
     */
    private AlphaVantageService() {
        // Initialization is done in initialize() method
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
        String apiKey = configManager.getAlphaVantageApiKey();
        
        if (apiKey == null || apiKey.isEmpty()) {
            logger.error("Alpha Vantage API key not configured");
            throw new IllegalStateException("Alpha Vantage API key is required");
        }
        
        // Use ConfigManager for timeout setting with default of 10 seconds
        int timeout = configManager.getConfigValueAsInt("alphavantage.timeout", 10);
        
        // Initialize Alpha Vantage API
        Config config = Config.builder()
            .key(apiKey)
            .timeOut(timeout)
            .build();
        
        AlphaVantage.api().init(config);
        initialized = true;
        
        logger.info("AlphaVantage API initialized with key: {}...", 
            apiKey.length() > 4 ? apiKey.substring(0, 4) + "..." : "****");
    }
    
    /**
     * Get daily stock data for a symbol
     */
    public TimeSeriesResponse getDailyStockData(String symbol) {
        if (!initialized) {
            initialize();
        }
        
        logger.info("Fetching daily stock data for: {}", symbol);
        
        return AlphaVantage.api()
            .timeSeries()
            .daily()
            .forSymbol(symbol)
            .outputSize(OutputSize.COMPACT)
            .fetchSync();
    }
    
    /**
     * Get latest stock quote data asynchronously
     */
    public void getDailyStockDataAsync(String symbol, ResponseCallback<TimeSeriesResponse> callback) {
        if (!initialized) {
            initialize();
        }
        
        logger.info("Fetching daily stock data asynchronously for: {}", symbol);
        
        AlphaVantage.api()
            .timeSeries()
            .daily()
            .forSymbol(symbol)
            .outputSize(OutputSize.COMPACT)
            .onSuccess(response -> callback.onSuccess((TimeSeriesResponse)response))
            .onFailure(exception -> callback.onFailure(exception))
            .fetch();
    }
    
    /**
     * Get intraday stock data for a symbol
     */
    public void getIntradayStockDataAsync(String symbol, Interval interval, 
                                          ResponseCallback<TimeSeriesResponse> callback) {
        if (!initialized) {
            initialize();
        }
        
        logger.info("Fetching intraday stock data for: {} with interval: {}", symbol, interval);
        
        AlphaVantage.api()
            .timeSeries()
            .intraday()
            .forSymbol(symbol)
            .interval(interval)
            .outputSize(OutputSize.COMPACT)
            .onSuccess(response -> callback.onSuccess((TimeSeriesResponse)response))
            .onFailure(exception -> callback.onFailure(exception))
            .fetch();
    }
    
    /**
     * Callback interface for handling async responses
     */
    public interface ResponseCallback<T> {
        void onSuccess(T response);
        void onFailure(Exception exception);
    }
    
    /**
     * Get stock data formatted for display
     */
    public static StockData convertToStockData(String symbol, StockUnit stockUnit) {
        return new StockData(
            symbol,
            stockUnit.getClose(),
            stockUnit.getHigh(),
            stockUnit.getLow(),
            stockUnit.getOpen(),
            stockUnit.getVolume()
        );
    }
    
    /**
     * Simple data class for stock data
     */
    public record StockData(String symbol, double price, double high, double low, double open, double volume) {}
}
