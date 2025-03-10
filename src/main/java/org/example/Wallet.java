package org.example;

import org.json.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Wallet {
    private String id; // Unique identifier (can be generated)
    private final String label;
    private final String address;
    private final String cryptoType;
    private double balance;
    private double value;
    private double change24h;
    private String lastUpdated; // ISO-8601 timestamp
    private List<Transaction> transactions;

    public Wallet(String label, String address, String cryptoType) {
        this.label = label;
        this.address = address;
        this.cryptoType = cryptoType;
        this.transactions = new ArrayList<>();
        this.id = java.util.UUID.randomUUID().toString(); // Generate a unique ID
        this.lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public void updateInfo(WalletInfo info) {
        this.balance = info.balance();
        this.transactions = info.transactions();
        this.value = balance * info.currentPrice();
        this.change24h = info.priceChange24h();
        this.lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("label", label);
        json.put("address", address);
        json.put("cryptoType", cryptoType);
        json.put("balance", balance);
        json.put("value", value);
        json.put("change24h", change24h);
        json.put("lastUpdated", lastUpdated);

        if (!transactions.isEmpty()) {
            json.put("lastTransaction", transactions.get(0).toJSON());
        }

        return json;
    }

    // Getters
    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getAddress() { return address; }
    public String getCryptoType() { return cryptoType; }
    public double getBalance() { return balance; }
    public double getValue() { return value; }
    public double getChange24h() { return change24h; }
    public String getLastUpdated() { return lastUpdated; }
    public List<Transaction> getTransactions() { return transactions; }
    
    // Setters for mutable fields
    public void setId(String id) { this.id = id; }
    public void setBalance(double balance) { this.balance = balance; }
    public void setValue(double value) { this.value = value; }
    public void setChange24h(double change24h) { this.change24h = change24h; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
}
