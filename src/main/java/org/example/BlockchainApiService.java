package org.example;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlockchainApiService {
    private static final Logger LOGGER = Logger.getLogger(BlockchainApiService.class.getName());
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private final String etherscanApiKey;
    private static final String BLOCKCHAIN_INFO_API = "https://blockchain.info";
    private static final String ETHERSCAN_API = "https://api.etherscan.io/api";
    private static final String COINGECKO_API = "https://api.coingecko.com/api/v3";
    private static final String COINGECKO_BTC_ID = "bitcoin";
    private static final String COINGECKO_ETH_ID = "ethereum";

    // Cache control - store last update time for each endpoint to avoid hitting rate limits
    private final java.util.Map<String, Long> lastUpdateTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, JSONObject> responseCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 60 * 1000; // Increase cache to 60 seconds to reduce API call frequency

    public BlockchainApiService() {
        ConfigManager configManager = ConfigManager.getInstance();
        this.etherscanApiKey = configManager.getEtherscanApiKey();
        if (etherscanApiKey == null || etherscanApiKey.isEmpty()) {
            LOGGER.severe("Etherscan API key not found in configuration");
        }
    }

    private JSONObject getMarketData(String coinId) throws IOException {
        String coinGeckoUrl = COINGECKO_API + "/coins/" + coinId + "?localization=false&tickers=false&community_data=false&developer_data=false";
        return makeApiCall(coinGeckoUrl, null);
    }
    
    public WalletInfo getBitcoinWalletInfo(String address) throws IOException, IllegalArgumentException {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Bitcoin address cannot be null or empty");
        }
        
        try {
            // Get wallet data from blockchain.info API
            String walletUrl = BLOCKCHAIN_INFO_API + "/rawaddr/" + address;
            JSONObject walletResponse = makeApiCall(walletUrl, null);

            // Parse balance - convert satoshis to BTC
            BigDecimal balance = BigDecimal.valueOf(walletResponse.getLong("final_balance"))
                    .divide(BigDecimal.valueOf(100000000), 8, RoundingMode.HALF_UP);

            // Get current BTC price from blockchain.info ticker API
            double currentPrice = 0.0;
            double priceChange = 0.0;
            double marketCap = 0.0;
            double volume24h = 0.0;

            // Always get data from the API, no hardcoded fallbacks
            String tickerUrl = BLOCKCHAIN_INFO_API + "/ticker";
            JSONObject tickerResponse = makeApiCall(tickerUrl, null);
            JSONObject usdData = tickerResponse.getJSONObject("USD");

            currentPrice = usdData.getDouble("last");
            priceChange = usdData.has("24h") ?
                usdData.getDouble("24h") :
                ((usdData.getDouble("last") / usdData.getDouble("15m")) - 1) * 100;
                
            // Get additional market data from CoinGecko
            try {
                JSONObject marketData = getMarketData(COINGECKO_BTC_ID);
                
                if (marketData.has("market_data")) {
                    JSONObject market = marketData.getJSONObject("market_data");
                    
                    if (market.has("market_cap") && market.getJSONObject("market_cap").has("usd")) {
                        marketCap = market.getJSONObject("market_cap").getDouble("usd");
                    }
                    
                    if (market.has("total_volume") && market.getJSONObject("total_volume").has("usd")) {
                        volume24h = market.getJSONObject("total_volume").getDouble("usd");
                    }
                    
                    // If the price change from blockchain.info is unavailable, use CoinGecko's
                    if (priceChange == 0.0 && market.has("price_change_percentage_24h")) {
                        priceChange = market.getDouble("price_change_percentage_24h");
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error fetching Bitcoin market data from CoinGecko: " + e.getMessage());
            }

            // Get transactions
            List<Transaction> transactions = new ArrayList<>();
            if (walletResponse.has("txs")) {
                JSONArray txs = walletResponse.getJSONArray("txs");
                for (int i = 0; i < Math.min(txs.length(), 10); i++) {
                    JSONObject tx = txs.getJSONObject(i);
                    transactions.add(parseBitcoinTransaction(tx, address));
                }
            }

            return new WalletInfo(balance.doubleValue(), transactions, currentPrice, priceChange, marketCap, volume24h);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching Bitcoin wallet info: " + e.getMessage(), e);
            
            // Attempt to get minimal price data from CoinMarketCap as a fallback
            try {
                // Use CoinMarketCapService directly as a fallback
                CoinMarketCapService cmcService = new CoinMarketCapService();
                CoinPrice coinPrice = cmcService.getPrice("BTC");
                
                // Return minimal data with just price info
                return new WalletInfo(0.0, new ArrayList<>(), coinPrice.currentPrice(), 
                                      coinPrice.priceChangePercentage24h(), 0.0, 0.0);
            } catch (Exception fallbackEx) {
                LOGGER.log(Level.SEVERE, "Fallback to CoinMarketCap also failed: " + fallbackEx.getMessage(), fallbackEx);
                throw new IOException("All API attempts failed and no real-time data is available");
            }
        }
    }

    public WalletInfo getEthereumWalletInfo(String address) throws IOException, IllegalArgumentException {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Ethereum address cannot be null or empty");
        }

        if (etherscanApiKey == null || etherscanApiKey.isEmpty()) {
            throw new IllegalArgumentException("Etherscan API key is not configured");
        }

        try {
            // Get balance
            String balanceUrl = String.format("%s?module=account&action=balance&address=%s&tag=latest&apikey=%s",
                    ETHERSCAN_API, address, etherscanApiKey);
            JSONObject balanceResponse = makeApiCall(balanceUrl, null);

            BigDecimal balance = BigDecimal.ZERO;
            if (balanceResponse.getString("status").equals("1")) {
                balance = new BigDecimal(balanceResponse.getString("result"))
                        .divide(BigDecimal.valueOf(1000000000000000000L), 18, RoundingMode.HALF_UP);
            }

            // Get ETH price from Etherscan
            double currentPrice = 0.0;
            double priceChange = 0.0;
            double marketCap = 0.0;
            double volume24h = 0.0;

            // Always get price from API
            String priceUrl = String.format("%s?module=stats&action=ethprice&apikey=%s",
                    ETHERSCAN_API, etherscanApiKey);
            JSONObject priceResponse = makeApiCall(priceUrl, null);

            if (priceResponse.getString("status").equals("1")) {
                JSONObject result = priceResponse.getJSONObject("result");
                currentPrice = Double.parseDouble(result.getString("ethusd"));
            }
            
            // Get additional market data from CoinGecko
            try {
                JSONObject marketData = getMarketData(COINGECKO_ETH_ID);
                
                if (marketData.has("market_data")) {
                    JSONObject market = marketData.getJSONObject("market_data");
                    
                    if (market.has("market_cap") && market.getJSONObject("market_cap").has("usd")) {
                        marketCap = market.getJSONObject("market_cap").getDouble("usd");
                    }
                    
                    if (market.has("total_volume") && market.getJSONObject("total_volume").has("usd")) {
                        volume24h = market.getJSONObject("total_volume").getDouble("usd");
                    }
                    
                    // Get 24h price change from CoinGecko since Etherscan doesn't provide it
                    if (market.has("price_change_percentage_24h")) {
                        priceChange = market.getDouble("price_change_percentage_24h");
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error fetching Ethereum market data from CoinGecko: " + e.getMessage());
                // No longer using random fallback values
            }

            // Get transactions
            String txUrl = String.format("%s?module=account&action=txlist&address=%s&startblock=0&endblock=99999999&page=1&offset=10&sort=desc&apikey=%s",
                    ETHERSCAN_API, address, etherscanApiKey);
            JSONObject txResponse = makeApiCall(txUrl, null);

            List<Transaction> transactions = new ArrayList<>();
            if (txResponse.getString("status").equals("1")) {
                JSONArray txs = txResponse.getJSONArray("result");
                for (int i = 0; i < Math.min(txs.length(), 10); i++) {
                    JSONObject tx = txs.getJSONObject(i);
                    transactions.add(parseEthereumTransaction(tx, address));
                }
            }

            return new WalletInfo(balance.doubleValue(), transactions, currentPrice, priceChange, marketCap, volume24h);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching Ethereum wallet info: " + e.getMessage(), e);
            
            // Attempt to get minimal price data from CoinMarketCap as a fallback
            try {
                // Use CoinMarketCapService directly as a fallback
                CoinMarketCapService cmcService = new CoinMarketCapService();
                CoinPrice coinPrice = cmcService.getPrice("ETH");
                
                // Return minimal data with just price info
                return new WalletInfo(0.0, new ArrayList<>(), coinPrice.currentPrice(), 
                                     coinPrice.priceChangePercentage24h(), 0.0, 0.0);
            } catch (Exception fallbackEx) {
                LOGGER.log(Level.SEVERE, "Fallback to CoinMarketCap also failed: " + fallbackEx.getMessage(), fallbackEx);
                throw new IOException("All API attempts failed and no real-time data is available for Ethereum");
            }
        }
    }

    private Transaction parseBitcoinTransaction(JSONObject tx, String walletAddress) {
        try {
            JSONArray outputs = tx.getJSONArray("out");
            JSONObject firstOutput = outputs.getJSONObject(0);
            boolean isReceived = firstOutput.has("addr") && firstOutput.getString("addr").equals(walletAddress);

            // Calculate total value from outputs
            double value = 0;
            for (int i = 0; i < outputs.length(); i++) {
                JSONObject output = outputs.getJSONObject(i);
                if (output.has("value")) {
                    value += output.getLong("value");
                }
            }

            String fromAddress = "";
            if (tx.has("inputs") && tx.getJSONArray("inputs").length() > 0) {
                JSONObject input = tx.getJSONArray("inputs").getJSONObject(0);
                if (input.has("prev_out") && input.getJSONObject("prev_out").has("addr")) {
                    fromAddress = input.getJSONObject("prev_out").getString("addr");
                }
            }

            return new Transaction(
                    isReceived ? "RECEIVE" : "SEND",
                    BigDecimal.valueOf(value)
                            .divide(BigDecimal.valueOf(100000000), 8, RoundingMode.HALF_UP)
                            .doubleValue(),
                    new java.util.Date(tx.getLong("time") * 1000L).toString(),
                    tx.getString("hash"),
                    fromAddress,
                    firstOutput.getString("addr"),
                    tx.has("confirmations") && tx.getInt("confirmations") > 6 ? "CONFIRMED" : "PENDING"
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing Bitcoin transaction: " + e.getMessage(), e);
            return new Transaction("UNKNOWN", 0.0, "", "", "", "", "UNKNOWN");
        }
    }

    private Transaction parseEthereumTransaction(JSONObject tx, String walletAddress) {
        try {
            boolean isReceived = tx.getString("to").equalsIgnoreCase(walletAddress);

            return new Transaction(
                    isReceived ? "RECEIVE" : "SEND",
                    new BigDecimal(tx.getString("value"))
                            .divide(BigDecimal.valueOf(1000000000000000000L), 18, RoundingMode.HALF_UP)
                            .doubleValue(),
                    new java.util.Date(Long.parseLong(tx.getString("timeStamp")) * 1000L).toString(),
                    tx.getString("hash"),
                    tx.getString("from"),
                    tx.getString("to"),
                    tx.has("confirmations") && tx.getInt("confirmations") > 12 ? "CONFIRMED" : "PENDING"
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing Ethereum transaction: " + e.getMessage(), e);
            return new Transaction("UNKNOWN", 0.0, "", "", "", "", "UNKNOWN");
        }
    }

    private JSONObject makeApiCall(String url, String apiKey) throws IOException {
        // Include request endpoint in log for debugging
        String endpoint = url.split("\\?")[0]; // Get the base URL without query parameters for logging
        LOGGER.info("Making API call to: " + endpoint);
        
        // Check cache first
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime.containsKey(url)) {
            long lastUpdate = lastUpdateTime.get(url);
            if (currentTime - lastUpdate < CACHE_DURATION && responseCache.containsKey(url)) {
                // Return cached response if it's still valid
                LOGGER.info("Using cached response for: " + endpoint);
                return new JSONObject(responseCache.get(url).toString()); // Deep copy to avoid mutation issues
            }
        }

        // Ensure we're using the correct Etherscan API key if needed
        String effectiveApiKey = apiKey;
        if (url.contains("etherscan.io") && (effectiveApiKey == null || effectiveApiKey.isEmpty())) {
            // Use hardcoded Etherscan API key as backup
            effectiveApiKey = "G6YJ1PGVSDWY8VP11ZKYPQJ78VWIE7YAUQ";
            LOGGER.info("Using hardcoded Etherscan API key");
        }

        // Cache miss or expired, make a new API call
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "CashClimb/1.0");  // Add user agent to avoid certain API blocks

        // Use appropriate header based on the API we're calling
        if (effectiveApiKey != null) {
            if (url.contains("pro-api.coinmarketcap.com")) {
                requestBuilder.addHeader("X-CMC_PRO_API_KEY", effectiveApiKey);
            } else if (url.contains("etherscan.io")) {
                // The API key is already in the URL for Etherscan
            }
        }

        Request request = requestBuilder.build();

        // Add retry logic
        int maxRetries = 5; // Increase max retries to 5
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                LOGGER.info("Executing request to " + endpoint + " (Attempt " + (retryCount + 1) + "/" + maxRetries + ")");
                Response response = client.newCall(request).execute();
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No response body";
                    LOGGER.warning("API request failed with code " + response.code() + ": " + errorBody);
                    
                    if (response.code() == 429 || response.code() >= 500) {
                        // Rate limit hit or server error, wait and retry
                        retryCount++;
                        if (retryCount >= maxRetries) break;
                        
                        int sleepTime = 1000 * (1 << retryCount); // Exponential backoff (1s, 2s, 4s, 8s, 16s)
                        LOGGER.info("Rate limit or server error, retrying in " + sleepTime/1000 + " seconds...");
                        Thread.sleep(sleepTime);
                        continue;
                    }
                    throw new IOException("API request failed with code " + response.code() + ": " + errorBody);
                }

                String responseBody = response.body().string();
                
                // For debugging - log response for successful calls
                LOGGER.info("Received response from " + endpoint + ": " + 
                           (responseBody.length() > 100 ? responseBody.substring(0, 100) + "..." : responseBody));
                
                JSONObject jsonResponse = new JSONObject(responseBody);
                
                // Log success without sensitive data
                LOGGER.info("API call to " + endpoint + " successful");

                // Cache successful response
                lastUpdateTime.put(url, currentTime);
                responseCache.put(url, jsonResponse);

                return jsonResponse;
            } catch (IOException e) {
                lastException = e;
                LOGGER.warning("IO exception calling " + endpoint + ": " + e.getMessage());
                retryCount++;
                if (retryCount >= maxRetries) {
                    break;
                }
                // Wait before retrying (exponential backoff)
                try {
                    int sleepTime = 1000 * (1 << retryCount); // Exponential backoff (1s, 2s, 4s)
                    LOGGER.info("IO error, retrying in " + sleepTime/1000 + " seconds...");
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("API call interrupted", ie);
                }
            } catch (Exception e) {
                // Catch broader exceptions (JSON parsing, etc.)
                lastException = e;
                LOGGER.warning("Unexpected error calling " + endpoint + ": " + e.getMessage());
                retryCount++;
                if (retryCount >= maxRetries) {
                    break;
                }
                try {
                    Thread.sleep(1000 * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // If we failed all retry attempts, try cached response
        LOGGER.severe("API call failed for " + endpoint + " after " + maxRetries + " retries: " +
                (lastException != null ? lastException.getMessage() : "Unknown error"));

        // Check if we have a cached response we can use as fallback
        if (responseCache.containsKey(url)) {
            LOGGER.info("Using cached response as fallback for URL: " + endpoint);
            return new JSONObject(responseCache.get(url).toString());
        }
        
        // If no cached response and the endpoint is for blockchain.info, try an alternative endpoint
        if (url.contains("blockchain.info")) {
            try {
                // Try alternative API (CoinGecko) for Bitcoin price data
                if (url.contains("/ticker")) {
                    String alternativeUrl = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true";
                    LOGGER.info("Trying alternative API (CoinGecko) for Bitcoin price data: " + alternativeUrl);
                    
                    Request altRequest = new Request.Builder()
                        .url(alternativeUrl)
                        .addHeader("Accept", "application/json")
                        .build();
                    
                    Response altResponse = client.newCall(altRequest).execute();
                    if (altResponse.isSuccessful() && altResponse.body() != null) {
                        String altBody = altResponse.body().string();
                        JSONObject altJson = new JSONObject(altBody);
                        
                        // Convert CoinGecko format to blockchain.info format
                        JSONObject mockTicker = new JSONObject();
                        JSONObject usdData = new JSONObject();
                        
                        if (altJson.has("bitcoin") && altJson.getJSONObject("bitcoin").has("usd")) {
                            double price = altJson.getJSONObject("bitcoin").getDouble("usd");
                            double change = 0;
                            if (altJson.getJSONObject("bitcoin").has("usd_24h_change")) {
                                change = altJson.getJSONObject("bitcoin").getDouble("usd_24h_change");
                            }
                            
                            usdData.put("15m", price);
                            usdData.put("last", price);
                            usdData.put("buy", price * 0.995); // Approximate
                            usdData.put("sell", price * 1.005); // Approximate
                            usdData.put("symbol", "$");
                            usdData.put("24h", change);
                            
                            mockTicker.put("USD", usdData);
                            
                            LOGGER.info("Successfully fetched alternative Bitcoin price data: " + price);
                            return mockTicker;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to fetch from alternative API: " + e.getMessage());
            }
        }
        
        // No more mock data - throw exception indicating the real API call failed
        throw new IOException("API call failed after multiple retries and no fallback available");
    }
    
}
