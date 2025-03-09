package org.example;

import java.io.IOException;
import java.util.ArrayList;

public class WalletService {
    private final BlockchainApiService blockchainApi;
    private final CoinMarketCapService cmcService;

    public WalletService() {
        this.blockchainApi = new BlockchainApiService();
        this.cmcService = new CoinMarketCapService();
    }

    /**
     * Get real-time wallet information for a specific address and crypto type
     * @param address The wallet address
     * @param cryptoType The cryptocurrency type (BTC or ETH)
     * @return WalletInfo containing balance, transactions, and price data
     * @throws IOException If API call fails
     */
    public WalletInfo getWalletInfo(String address, String cryptoType) throws IOException {
        try {
            WalletInfo blockchainInfo;
            CoinPrice coinPrice;

            if (cryptoType.equals("BTC")) {
                blockchainInfo = blockchainApi.getBitcoinWalletInfo(address);
                coinPrice = cmcService.getPrice("BTC");
            } else if (cryptoType.equals("ETH")) {
                blockchainInfo = blockchainApi.getEthereumWalletInfo(address);
                coinPrice = cmcService.getPrice("ETH");
            } else {
                throw new IllegalArgumentException("Unsupported crypto type: " + cryptoType);
            }

            return new WalletInfo(
                    blockchainInfo.balance(),
                    blockchainInfo.transactions(),
                    coinPrice.currentPrice(),
                    coinPrice.priceChangePercentage24h()
            );
        } catch (Exception e) {
            System.err.println("Error fetching wallet info: " + e.getMessage());
            return new WalletInfo(0.0, new ArrayList<>(), 0.0, 0.0);
        }
    }
    
    /**
     * Get only Bitcoin price information without a wallet address
     * This is used for market data display
     * @return WalletInfo with only price data (balance will be 0)
     * @throws IOException If API call fails
     */
    public WalletInfo getBitcoinPriceInfo() throws IOException {
        try {
            // Get price data from API
            double currentPrice = 0.0;
            double priceChange = 0.0;
            
            try {
                // Try to get from our custom BlockchainApiService first
                String dummyAddress = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"; // Bitcoin genesis block address
                WalletInfo info = blockchainApi.getBitcoinWalletInfo(dummyAddress);
                currentPrice = info.currentPrice();
                priceChange = info.priceChange24h();
            } catch (Exception e) {
                // Fall back to CoinMarketCap if blockchain.info fails
                CoinPrice coinPrice = cmcService.getPrice("BTC");
                currentPrice = coinPrice.currentPrice();
                priceChange = coinPrice.priceChangePercentage24h();
            }
            
            // Return a wallet info with only price data (balance=0, empty transactions)
            return new WalletInfo(0.0, new ArrayList<>(), currentPrice, priceChange);
        } catch (Exception e) {
            System.err.println("Error fetching Bitcoin price info: " + e.getMessage());
            return new WalletInfo(0.0, new ArrayList<>(), 0.0, 0.0);
        }
    }
    
    /**
     * Get only Ethereum price information without a wallet address
     * This is used for market data display
     * @return WalletInfo with only price data (balance will be 0)
     * @throws IOException If API call fails
     */
    public WalletInfo getEthereumPriceInfo() throws IOException {
        try {
            // Get price data from API
            double currentPrice = 0.0;
            double priceChange = 0.0;
            
            try {
                // Try to get from our custom BlockchainApiService first
                String dummyAddress = "0xde0b295669a9fd93d5f28d9ec85e40f4cb697bae"; // Ethereum Foundation address
                WalletInfo info = blockchainApi.getEthereumWalletInfo(dummyAddress);
                currentPrice = info.currentPrice();
                priceChange = info.priceChange24h();
            } catch (Exception e) {
                // Fall back to CoinMarketCap if Etherscan fails
                CoinPrice coinPrice = cmcService.getPrice("ETH");
                currentPrice = coinPrice.currentPrice();
                priceChange = coinPrice.priceChangePercentage24h();
            }
            
            // Return a wallet info with only price data (balance=0, empty transactions)
            return new WalletInfo(0.0, new ArrayList<>(), currentPrice, priceChange);
        } catch (Exception e) {
            System.err.println("Error fetching Ethereum price info: " + e.getMessage());
            return new WalletInfo(0.0, new ArrayList<>(), 0.0, 0.0);
        }
    }
}
