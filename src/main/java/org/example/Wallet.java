package org.example;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class Wallet {
    private final String label;
    private final String address;
    private final String cryptoType;
    private double balance;
    private double value;
    private double change24h;
    private List<Transaction> transactions;

    public Wallet(String label, String address, String cryptoType) {
        this.label = label;
        this.address = address;
        this.cryptoType = cryptoType;
        this.transactions = new ArrayList<>();
    }

    public void updateInfo(WalletInfo info) {
        this.balance = info.balance();
        this.transactions = info.transactions();
        this.value = balance * info.currentPrice();
        this.change24h = info.priceChange24h();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("label", label);
        json.put("address", address);
        json.put("cryptoType", cryptoType);
        json.put("balance", balance);
        json.put("value", value);
        json.put("change24h", change24h);

        if (!transactions.isEmpty()) {
            json.put("lastTransaction", transactions.get(0).toJSON());
        }

        return json;
    }

    // Getters
    public String getLabel() { return label; }
    public String getAddress() { return address; }
    public String getCryptoType() { return cryptoType; }
    public double getBalance() { return balance; }
    public double getValue() { return value; }
    public double getChange24h() { return change24h; }
    public List<Transaction> getTransactions() { return transactions; }
}
