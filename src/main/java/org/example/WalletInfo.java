package org.example;

import java.util.List;

/**
 * Immutable wallet information record containing cryptocurrency balance,
 * transaction history, and current market data.
 * 
 * @param balance The wallet balance in cryptocurrency units
 * @param transactions List of transactions associated with this wallet
 * @param currentPrice Current price of the cryptocurrency in USD
 * @param priceChange24h 24-hour price change percentage
 */
public record WalletInfo(
        double balance,
        List<Transaction> transactions,
        double currentPrice,
        double priceChange24h
) {}
