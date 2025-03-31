package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Simple utility to test the Alpha Vantage API connection
 * Run this to check if the API key is working properly
 */
public class AlphaVantageTest {
    private static final String API_KEY = "2470IDOB57MHSDPZ";
    private static final String BASE_URL = "https://www.alphavantage.co/query";
    
    public static void main(String[] args) {
        // Create HTTP client with 10 second timeout
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        // Test Global Quote endpoint
        String symbol = "AAPL"; // Apple Inc. stock symbol
        String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + symbol + "&apikey=" + API_KEY;
        
        System.out.println("Testing Alpha Vantage API with key: " + API_KEY);
        System.out.println("URL: " + url);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("\nResponse Status Code: " + response.statusCode());
            
            String responseBody = response.body();
            System.out.println("\nResponse Body:");
            System.out.println(responseBody);
            
            // Check for API limit messages
            if (responseBody.contains("Thank you for using Alpha Vantage")) {
                System.out.println("\n⚠️ API limit message detected. You may have reached your daily limit.");
            }
            
            // Check if we got valid data
            if (responseBody.contains("Global Quote") && 
                responseBody.contains("05. price")) {
                System.out.println("\n✅ SUCCESS! API returned valid stock data");
            } else {
                System.out.println("\n❌ ERROR! API did not return expected data format");
            }
            
        } catch (IOException | InterruptedException e) {
            System.out.println("\n❌ ERROR connecting to Alpha Vantage API:");
            e.printStackTrace();
        }
    }
}
