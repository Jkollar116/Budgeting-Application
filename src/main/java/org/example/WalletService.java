package org.example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service that provides wallet information by combining blockchain data with price data.
 * Acts as a facade for the BlockchainApiService and CoinMarketCapService.
 */
public class WalletService {
    private static final Logger LOGGER = Logger.getLogger(WalletService.class.getName());
    private final BlockchainApiService blockchainApi;
    private final CoinMarketCapService cmcService;

    /**
     * Constructs a new WalletService with blockchain and price API services.
     */
    public WalletService() {
        this.blockchainApi = new BlockchainApiService();
        this.cmcService = new CoinMarketCapService();
    }

    /**
     * Retrieves combined wallet information including balance, transactions, and price data.
     *
     * @param address The wallet address to query
     * @param cryptoType The cryptocurrency type (BTC or ETH)
     * @return WalletInfo containing combined wallet information
     * @throws IOException if there's an error communicating with the APIs
     * @throws IllegalArgumentException if the address or crypto type is invalid
     */
    public WalletInfo getWalletInfo(String address, String cryptoType) throws IOException, IllegalArgumentException {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet address cannot be null or empty");
        }
        
        if (cryptoType == null || cryptoType.trim().isEmpty()) {
            throw new IllegalArgumentException("Cryptocurrency type cannot be null or empty");
        }
        
        try {
            WalletInfo blockchainInfo;
            CoinPrice coinPrice;

            LOGGER.info("Fetching wallet info for " + cryptoType + " address: " + address);
            
            cryptoType = cryptoType.toUpperCase().trim();
            if (cryptoType.equals("BTC")) {
                blockchainInfo = blockchainApi.getBitcoinWalletInfo(address);
                coinPrice = cmcService.getPrice("BTC");
            } else if (cryptoType.equals("ETH")) {
                blockchainInfo = blockchainApi.getEthereumWalletInfo(address);
                coinPrice = cmcService.getPrice("ETH");
            } else {
                throw new IllegalArgumentException("Unsupported crypto type: " + cryptoType + ". Supported types are BTC and ETH.");
            }

            return new WalletInfo(
                    blockchainInfo.balance(),
                    blockchainInfo.transactions(),
                    coinPrice.currentPrice(),
                    coinPrice.priceChangePercentage24h(),
                    blockchainInfo.marketCap(),
                    blockchainInfo.volume24h()
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching wallet info: " + e.getMessage(), e);
            return new WalletInfo(0.0, new ArrayList<>(), 0.0, 0.0, 0.0, 0.0);
        }
    }
    
    /**
     * Get only Bitcoin price information without a wallet address.
     * This is used for market data display.
     * 
     * @return WalletInfo with only price data (balance will be 0)
     * @throws IOException If API call fails
     */
    public WalletInfo getBitcoinPriceInfo() throws IOException {
        try {
            LOGGER.info("Fetching Bitcoin price info without wallet address");
            
            // Get price data directly from CoinMarketCap API
            LOGGER.info("Getting Bitcoin price directly from CoinMarketCap API");
            CoinPrice coinPrice = cmcService.getPrice("BTC");
            double currentPrice = coinPrice.currentPrice();
            double priceChange = coinPrice.priceChangePercentage24h();
            
            // Estimate market cap and volume based on current price
            double marketCap = currentPrice * 19850000; // Approximate circulating supply
            double volume24h = currentPrice * 300000; // Rough daily volume
            
            LOGGER.info("Successfully got Bitcoin price from CoinMarketCap API: " + currentPrice);
            
            // Return a wallet info with only price data (balance=0, empty transactions)
            return new WalletInfo(0.0, new ArrayList<>(), currentPrice, priceChange, marketCap, volume24h);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching Bitcoin price from CoinMarketCap API: " + e.getMessage(), e);
            throw new IOException("Failed to fetch real-time Bitcoin price data: " + e.getMessage());
        }
    }
    
    /**
     * Get only Ethereum price information without a wallet address.
     * This is used for market data display.
     * 
     * @return WalletInfo with only price data (balance will be 0)
     * @throws IOException If API call fails
     */
    public WalletInfo getEthereumPriceInfo() throws IOException {
        try {
            LOGGER.info("Fetching Ethereum price info without wallet address");
            
            // Get price data directly from CoinMarketCap API
            LOGGER.info("Getting Ethereum price directly from CoinMarketCap API");
            CoinPrice coinPrice = cmcService.getPrice("ETH");
            double currentPrice = coinPrice.currentPrice();
            double priceChange = coinPrice.priceChangePercentage24h();
            
            // Estimate market cap and volume based on current price
            double marketCap = currentPrice * 120000000; // Approximate circulating supply
            double volume24h = currentPrice * 6000000; // Rough daily volume
            
            LOGGER.info("Successfully got Ethereum price from CoinMarketCap API: " + currentPrice);
            
            // Return a wallet info with only price data (balance=0, empty transactions)
            return new WalletInfo(0.0, new ArrayList<>(), currentPrice, priceChange, marketCap, volume24h);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching Ethereum price from CoinMarketCap API: " + e.getMessage(), e);
            throw new IOException("Failed to fetch real-time Ethereum price data: " + e.getMessage());
        }
    }
}
