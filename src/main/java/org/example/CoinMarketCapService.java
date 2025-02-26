package org.example;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;

public class CoinMarketCapService {
    protected static final String API_KEY = "e2fa2fa3-ef84-4e08-8a73-ae43c073ab0d";
    protected static final String API_URL = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest";
    private final OkHttpClient client = new OkHttpClient();

    public CoinPrice getPrice(String symbol) throws IOException {
        try {
            String url = String.format("%s?symbol=%s&convert=USD", API_URL, symbol.toUpperCase());

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-CMC_PRO_API_KEY", API_KEY)
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
            System.err.println("Error fetching price from CoinMarketCap: " + e.getMessage());
            return new CoinPrice(0.0, 0.0); // Return default values on error
        }
    }
}
record CoinPrice(double currentPrice, double priceChangePercentage24h) {}