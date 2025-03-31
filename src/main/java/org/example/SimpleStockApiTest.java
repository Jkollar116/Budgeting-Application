package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Simple standalone test for stock API that uses only JDK classes
 * This avoids dependency issues with the main application
 */
public class SimpleStockApiTest {
    private static final String API_KEY = "2470IDOB57MHSDPZ";
    private static final String BASE_URL = "https://www.alphavantage.co/query";
    
    public static void main(String[] args) {
        // Create HTTP client with 10 second timeout
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        System.out.println("======== Testing Alpha Vantage API Integration ========");
        System.out.println("API Key: " + API_KEY);
        
        // Test Global Quote endpoint (current stock price)
        testStockQuote(client, "AAPL");
        
        // Test stock history endpoint
        testStockHistory(client, "MSFT", "1D");
        
        System.out.println("\n======== Test Complete ========");
    }
    
    private static void testStockQuote(HttpClient client, String symbol) {
        System.out.println("\n== Testing Stock Quote for " + symbol + " ==");
        String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + API_KEY;
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();
            
            System.out.println("Status Code: " + statusCode);
            
            if (statusCode == 200) {
                // Check for API limit messages
                if (responseBody.contains("Note")) {
                    System.out.println("⚠️ API limit warning found:");
                    int noteIndex = responseBody.indexOf("Note");
                    String note = responseBody.substring(noteIndex, responseBody.indexOf("}", noteIndex));
                    System.out.println("  " + note);
                }
                
                // Check for valid data
                if (responseBody.contains("Global Quote") && responseBody.contains("price")) {
                    System.out.println("✅ SUCCESS! Received valid quote data");
                    
                    // Extract price - simple approach without using a JSON library
                    try {
                        int priceIndex = responseBody.indexOf("05. price");
                        if (priceIndex > 0) {
                            String priceStr = responseBody.substring(priceIndex + 12, responseBody.indexOf(",", priceIndex));
                            priceStr = priceStr.replace("\"", "").trim();
                            System.out.println("📈 Current Price: $" + priceStr);
                        }
                    } catch (Exception e) {
                        System.out.println("(Could not parse price from response)");
                    }
                } else {
                    System.out.println("❌ ERROR! Response does not contain expected quote data");
                    System.out.println("Response Body: " + responseBody);
                }
            } else {
                System.out.println("❌ ERROR! Non-200 status code");
                System.out.println("Response Body: " + responseBody);
            }
        } catch (Exception e) {
            System.out.println("❌ ERROR during API request:");
            e.printStackTrace();
        }
    }
    
    private static void testStockHistory(HttpClient client, String symbol, String timeframe) {
        System.out.println("\n== Testing Stock History for " + symbol + " (" + timeframe + ") ==");
        
        // Convert timeframe to API parameters
        String function;
        String interval = "";
        
        switch (timeframe) {
            case "1D":
                function = "TIME_SERIES_INTRADAY";
                interval = "&interval=5min";
                break;
            case "1W":
                function = "TIME_SERIES_INTRADAY";
                interval = "&interval=60min";
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
        
        String url = BASE_URL + "?function=" + function + interval + "&symbol=" + symbol + "&apikey=" + API_KEY;
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();
            
            System.out.println("Status Code: " + statusCode);
            
            if (statusCode == 200) {
                // Check for API limit messages
                if (responseBody.contains("Note")) {
                    System.out.println("⚠️ API limit warning found:");
                    int noteIndex = responseBody.indexOf("Note");
                    String note = responseBody.substring(noteIndex, responseBody.indexOf("}", noteIndex));
                    System.out.println("  " + note);
                }
                
                // Check for valid data by looking for time series data
                if (responseBody.contains("Time Series") || 
                    responseBody.contains("Weekly Time Series") || 
                    responseBody.contains("Daily Time Series")) {
                    System.out.println("✅ SUCCESS! Received valid history data");
                    System.out.println("📊 Response contains historical price data");
                    
                    // Count data points - rough estimation
                    int count = (responseBody.split("\\{").length - 2);
                    System.out.println("📈 Approximately " + count + " data points received");
                } else {
                    System.out.println("❌ ERROR! Response does not contain expected history data");
                    System.out.println("Response Body: " + responseBody);
                }
            } else {
                System.out.println("❌ ERROR! Non-200 status code");
                System.out.println("Response Body: " + responseBody);
            }
        } catch (Exception e) {
            System.out.println("❌ ERROR during API request:");
            e.printStackTrace();
        }
    }
}
