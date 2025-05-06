package org.example;

import java.util.List;
import java.util.Map;

/**
 * Test client for AlphaVantageClient to demonstrate usage of the fluent API
 */
public class AlphaVantageTestClient {

    public static void main(String[] args) {
        System.out.println("AlphaVantage Java Client Test");
        System.out.println("============================");
        
        // Get the singleton instance of our client
        AlphaVantageClient client = AlphaVantageClient.getInstance();
        
        try {
            // Test stock quote - get current price info
            System.out.println("\nTesting Global Quote for AAPL:");
            System.out.println("------------------------------");
            Stock appleStock = client.getStockQuote("AAPL");
            
            System.out.println("Symbol: " + appleStock.getSymbol());
            System.out.println("Name: " + appleStock.getName());
            System.out.println("Price: $" + appleStock.getPrice());
            System.out.println("Previous Close: $" + appleStock.getPreviousClose());
            System.out.println("Change: " + appleStock.getChange());
            System.out.println("Change %: " + String.format("%.2f", appleStock.getChangePercent()) + "%");
            System.out.println("Volume: " + appleStock.getVolume());
            System.out.println("Last Updated: " + appleStock.getLastUpdated());
            
            // Wait to ensure we don't hit rate limits
            Thread.sleep(1000);
            
            // Test daily history for a different stock (e.g. MSFT)
            System.out.println("\nTesting Daily Time Series for MSFT (last 10 days):");
            System.out.println("----------------------------------------------");
            List<Map<String, Object>> msftHistory = client.getStockHistory("MSFT", "1M");
            
            // Display the first 10 data points (or fewer if less available)
            int count = 0;
            for (Map<String, Object> dataPoint : msftHistory) {
                if (count++ >= 10) break;
                
                System.out.println("Date: " + dataPoint.get("timestamp") + 
                                  " | Close Price: $" + dataPoint.get("price"));
            }
            
            // Wait to ensure we don't hit rate limits
            Thread.sleep(1000);
            
            // Test caching - second request should be much faster and use cached data
            System.out.println("\nTesting Cache (second request for AAPL):");
            System.out.println("----------------------------------------");
            long startTime = System.currentTimeMillis();
            Stock cachedStock = client.getStockQuote("AAPL");
            long endTime = System.currentTimeMillis();
            
            System.out.println("Symbol: " + cachedStock.getSymbol());
            System.out.println("Price: $" + cachedStock.getPrice());
            System.out.println("Execution time: " + (endTime - startTime) + "ms (should be fast if cached)");
            
            // Test time-based chart data
            System.out.println("\nTesting Intraday Data for GOOGL (1D timeframe):");
            System.out.println("---------------------------------------------");
            List<Map<String, Object>> googleHistory = client.getStockHistory("GOOGL", "1D");
            
            System.out.println("Total data points: " + googleHistory.size());
            // Print first and last data points to show the time range
            if (!googleHistory.isEmpty()) {
                System.out.println("First point: " + googleHistory.get(0));
                System.out.println("Last point: " + googleHistory.get(googleHistory.size() - 1));
            }
            
            System.out.println("\nTest completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during API test: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources to prevent lingering OkHttp threads
            System.out.println("\nCleaning up resources...");
            client.shutdown();
            System.out.println("HTTP client resources released");
        }
    }
}
