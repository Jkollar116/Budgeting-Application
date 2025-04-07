package org.example;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CoinMarketCapService {
    private static final Logger LOGGER = Logger.getLogger(CoinMarketCapService.class.getName());
    private static final String API_URL = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private final String apiKey;
    private final OkHttpClient client;
    
    public CoinMarketCapService() {
        ConfigManager configManager = ConfigManager.getInstance();
        this.apiKey = configManager.getCoinMarketCapApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.severe("CoinMarketCap API key not found in configuration");
        }
        
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

        LOGGER.info("CoinMarketCapService initialized with API key: " + (apiKey != null ? apiKey.substring(0, 5) + "..." : "null"));
    }

    public CoinPrice getPrice(String symbol) throws IOException, IllegalArgumentException {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Cryptocurrency symbol cannot be null or empty");
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            String errorMsg = "CoinMarketCap API key is not configured";
            LOGGER.severe(errorMsg);
            throw new IOException(errorMsg);
        }
        
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                LOGGER.info("Attempting to fetch " + symbol + " price from CoinMarketCap (Attempt " + attempt + "/" + MAX_RETRIES + ")");
                return fetchPriceFromApi(symbol);
            } catch (Exception e) {
                lastException = e;
                LOGGER.warning("API attempt " + attempt + " failed for " + symbol + ": " + e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        String errorMsg = "All API attempts failed for " + symbol + ". Last error: " + 
                     (lastException != null ? lastException.getMessage() : "Unknown error");
        LOGGER.severe(errorMsg);
        throw new IOException(errorMsg);
    }
    
    private CoinPrice fetchPriceFromApi(String symbol) throws IOException, JSONException {
        String url = String.format("%s?symbol=%s&convert=USD", API_URL, symbol.toUpperCase());
        LOGGER.info("Making API call to CoinMarketCap: " + url);
        
        String effectiveApiKey = this.apiKey;
        if (effectiveApiKey == null || effectiveApiKey.isEmpty()) {
            effectiveApiKey = "e2fa2fa3-ef84-4e08-8a73-ae43c073ab0d";
            LOGGER.info("Using hardcoded API key as fallback");
        }
        
        LOGGER.info("Using API key beginning with: " + effectiveApiKey.substring(0, 5));
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-CMC_PRO_API_KEY", effectiveApiKey)
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Encoding", "deflate, gzip")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = "";
            if (response.body() != null) {
                responseBody = response.body().string();
            }
            
            if (!response.isSuccessful()) {
                LOGGER.severe("CoinMarketCap API error: " + response.code() + " - " + responseBody);
                LOGGER.severe("Request URL: " + url);
                LOGGER.severe("Request headers: X-CMC_PRO_API_KEY: " + effectiveApiKey.substring(0, 5) + "...");
                throw new IOException("API call failed with status code: " + response.code());
            }

            LOGGER.info("Received successful response from CoinMarketCap API");
            JSONObject jsonObject = new JSONObject(responseBody);

            try {
                JSONObject data = jsonObject.getJSONObject("data");
                JSONObject symbolData = data.getJSONObject(symbol.toUpperCase());
                JSONObject quote = symbolData.getJSONObject("quote");
                JSONObject usd = quote.getJSONObject("USD");

                double price = usd.getDouble("price");
                double change24h = usd.getDouble("percent_change_24h");
                
                LOGGER.info("Successfully retrieved " + symbol + " price: " + price + " (24h change: " + change24h + "%)");
                
                return new CoinPrice(price, change24h);
            } catch (JSONException e) {
                LOGGER.severe("Error parsing CoinMarketCap JSON response: " + e.getMessage());
                LOGGER.severe("Raw JSON: " + responseBody);
                throw e;
            }
        }
    }
    
    // Removed fallback method to ensure only real API data is used
}

record CoinPrice(double currentPrice, double priceChangePercentage24h) {}
