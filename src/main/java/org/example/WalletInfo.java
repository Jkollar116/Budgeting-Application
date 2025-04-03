package org.example;

import java.util.List;

public record WalletInfo(
        double balance,
        List<Transaction> transactions,
        double currentPrice,
        double priceChange24h,
        double marketCap,
        double volume24h
) {
    public WalletInfo(double balance, List<Transaction> transactions, double currentPrice, double priceChange24h) {
        this(balance, transactions, currentPrice, priceChange24h, 0.0, 0.0);
    }
}
