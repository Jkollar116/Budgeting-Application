package org.example;

import java.util.List;

public record WalletInfo(
        double balance,
        List<Transaction> transactions,
        double currentPrice,
        double priceChange24h
) {}
