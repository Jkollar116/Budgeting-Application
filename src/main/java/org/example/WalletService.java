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
}
