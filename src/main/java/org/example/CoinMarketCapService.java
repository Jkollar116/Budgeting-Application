package org.example;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for retrieving cryptocurrency price information from CoinMarketCap API.
 */
public class CoinMarketCapService {
    private static final Logger LOGGER = Logger.getLogger(CoinMarketCapService.class.getName());
    private static final String API_URL = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest";
    private final String apiKey;
    private final OkHttpClient client = new OkHttpClient();
    
    /**
     * Constructs a CoinMarketCapService with API key from configuration.
     */
    public CoinMarketCapService() {
        ConfigManager configManager = ConfigManager.getInstance();
        this.apiKey = configManager.getCoinMarketCapApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.severe("CoinMarketCap API key not found in configuration");
        }
    }

    /**
     * Retrieves current price information for a cryptocurrency.
     *
     * @param symbol Symbol of the cryptocurrency (e.g., BTC, ETH)
     * @return CoinPrice containing current price and 24h change percentage
     * @throws IOException if there's an error communicating with the API
     * @throws IllegalArgumentException if the symbol is invalid or API key is missing
     */
    public CoinPrice getPrice(String symbol) throws IOException, IllegalArgumentException {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Cryptocurrency symbol cannot be null or empty");
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("CoinMarketCap API key is not configured");
        }
        try {
            String url = String.format("%s?symbol=%s&convert=USD", API_URL, symbol.toUpperCase());

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-CMC_PRO_API_KEY", apiKey)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("API call failed: " + response.code());
                }

                String jsonData = response.body().string();
                JSONObject jsonObject = new JSONObject(jsonData);

                // Navigate through the JSON structure
                JSONObject data = jsonObject.getJSONObject("data");
                JSONObject symbolData = data.getJSONObject(symbol.toUpperCase());
                JSONObject quote = symbolData.getJSONObject("quote");
                JSONObject usd = quote.getJSONObject("USD");

                return new CoinPrice(
                        usd.getDouble("price"),
                        usd.getDouble("percent_change_24h")
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching price from CoinMarketCap: " + e.getMessage(), e);
            return new CoinPrice(0.0, 0.0); // Return default values on error
        }
    }
}
record CoinPrice(double currentPrice, double priceChangePercentage24h) {}
