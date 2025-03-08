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
                    coinPrice.priceChangePercentage24h()
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching wallet info: " + e.getMessage(), e);
            return new WalletInfo(0.0, new ArrayList<>(), 0.0, 0.0);
        }
    }
}
