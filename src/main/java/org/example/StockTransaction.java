package org.example;

import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a completed stock transaction (executed order)
 */
public class StockTransaction {
    private String id;
    private String orderId;
    private String symbol;
    private String assetId;
    private int quantity;
    private double price;
    private StockOrder.OrderSide side;
    private double fees;
    private long timestamp;

    /**
     * Constructor for creating a transaction from an executed order
     */
    public StockTransaction(String orderId, String symbol, String assetId, int quantity, 
                           double price, StockOrder.OrderSide side, double fees) {
        this.id = generateTransactionId();
        this.orderId = orderId;
        this.symbol = symbol;
        this.assetId = assetId;
        this.quantity = quantity;
        this.price = price;
        this.side = side;
        this.fees = fees;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructor from JSON (for deserializing API responses)
     */
    public StockTransaction(JSONObject json) {
        this.id = json.optString("id", generateTransactionId());
        this.orderId = json.optString("order_id", "");
        this.symbol = json.getString("symbol");
        this.assetId = json.optString("asset_id", "");
        this.quantity = json.getInt("qty");
        this.price = json.getDouble("price");
        
        // Parse order side
        String sideStr = json.getString("side");
        try {
            this.side = StockOrder.OrderSide.valueOf(sideStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.side = StockOrder.OrderSide.BUY; // Default to BUY if unknown
        }
        
        this.fees = json.optDouble("fees", 0.0);
        
        // Parse timestamp
        String timestampStr = json.optString("transaction_time", "");
        if (timestampStr != null && !timestampStr.isEmpty()) {
            try {
                this.timestamp = Instant.parse(timestampStr).toEpochMilli();
            } catch (Exception e) {
                this.timestamp = System.currentTimeMillis();
            }
        } else {
            this.timestamp = json.optLong("timestamp", System.currentTimeMillis());
        }
    }

    /**
     * Generate a unique transaction ID
     */
    private String generateTransactionId() {
        return "tx-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    /**
     * Format timestamp to ISO-8601
     */
    private String formatTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_INSTANT);
    }

    /**
     * Calculate the total value of the transaction
     */
    public double getTotalValue() {
        return quantity * price;
    }

    /**
     * Calculate the net value of the transaction (after fees)
     */
    public double getNetValue() {
        return getTotalValue() - fees;
    }

    /**
     * Convert to JSON for API requests and storage
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("order_id", orderId);
        json.put("symbol", symbol);
        json.put("asset_id", assetId);
        json.put("qty", quantity);
        json.put("price", price);
        json.put("side", side.toString().toLowerCase());
        json.put("fees", fees);
        json.put("timestamp", timestamp);
        json.put("transaction_time", formatTimestamp(timestamp));
        json.put("total_value", getTotalValue());
        json.put("net_value", getNetValue());
        return json;
    }

    // Getters
    
    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getAssetId() {
        return assetId;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public StockOrder.OrderSide getSide() {
        return side;
    }

    public double getFees() {
        return fees;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFormattedTimestamp() {
        return formatTimestamp(timestamp);
    }
}
