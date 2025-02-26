package org.example;

import org.json.JSONObject;

public class Transaction {
    private final String type;
    private final double amount;
    private final String timestamp;
    private final String txHash;
    private final String from;
    private final String to;
    private final String status;

    public Transaction(String type, double amount, String timestamp, String txHash, String from, String to, String status) {
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.txHash = txHash;
        this.from = from;
        this.to = to;
        this.status = status;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("amount", amount);
        json.put("timestamp", timestamp);
        json.put("txHash", txHash);
        json.put("from", from);
        json.put("to", to);
        json.put("status", status);
        return json;
    }
}
