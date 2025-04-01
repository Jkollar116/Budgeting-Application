package org.example;

import org.json.JSONObject;

/**
 * Represents a cryptocurrency transaction with details like type, amount, and participants.
 * Used to model blockchain transactions for both Bitcoin and Ethereum networks.
 */
public class Transaction {
    private final String type;       // SEND, RECEIVE, UNKNOWN
    private final double amount;     // Transaction amount in cryptocurrency units
    private final String timestamp;  // Transaction time
    private final String txHash;     // Transaction hash/ID on the blockchain
    private final String from;       // Sender address
    private final String to;         // Recipient address
    private final String status;     // CONFIRMED, PENDING, UNKNOWN

    /**
     * Creates a new Transaction with the specified details.
     *
     * @param type      The transaction type (SEND, RECEIVE, UNKNOWN)
     * @param amount    The transaction amount in cryptocurrency units
     * @param timestamp The transaction timestamp
     * @param txHash    The transaction hash on the blockchain
     * @param from      The sender address
     * @param to        The recipient address
     * @param status    The transaction status (CONFIRMED, PENDING, UNKNOWN)
     */
    public Transaction(String type, double amount, String timestamp, String txHash, String from, String to, String status) {
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.txHash = txHash;
        this.from = from;
        this.to = to;
        this.status = status;
    }

    /**
     * Returns all transaction details as a JSON object.
     * 
     * @return JSONObject representing this transaction
     */
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

    // Getters
    public String getType() { return type; }
    public double getAmount() { return amount; }
    public String getTimestamp() { return timestamp; }
    public String getTxHash() { return txHash; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getStatus() { return status; }
}
