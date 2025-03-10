package org.example;

import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a stock order with various order types (market, limit, stop, etc.)
 */
public class StockOrder {
    
    public enum OrderType {
        MARKET, LIMIT, STOP, STOP_LIMIT, TRAILING_STOP
    }
    
    public enum OrderSide {
        BUY, SELL
    }
    
    public enum TimeInForce {
        DAY, GTC, IOC, FOK
    }
    
    public enum OrderStatus {
        NEW, PARTIALLY_FILLED, FILLED, CANCELED, EXPIRED, REJECTED, PENDING_CANCEL, PENDING_NEW
    }
    
    private String id;
    private String clientOrderId; // Client-generated unique ID
    private String symbol;
    private String assetId;
    private OrderType type;
    private OrderSide side;
    private int quantity;
    private int filledQuantity;
    private double limitPrice;
    private double stopPrice;
    private double trailPercent;
    private double trailPrice;
    private TimeInForce timeInForce;
    private OrderStatus status;
    private long createdAt;
    private long updatedAt;
    private long filledAt;
    private long expiredAt;
    private long canceledAt;
    
    // Constructor for creating a new order
    public StockOrder(String symbol, String assetId, OrderType type, OrderSide side, 
                     int quantity, TimeInForce timeInForce) {
        this.clientOrderId = generateClientOrderId();
        this.symbol = symbol;
        this.assetId = assetId;
        this.type = type;
        this.side = side;
        this.quantity = quantity;
        this.filledQuantity = 0;
        this.timeInForce = timeInForce;
        this.status = OrderStatus.NEW;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }
    
    // Constructor from JSON (for deserializing API responses)
    public StockOrder(JSONObject json) {
        this.id = json.optString("id", "");
        this.clientOrderId = json.optString("client_order_id", "");
        this.symbol = json.getString("symbol");
        this.assetId = json.optString("asset_id", "");
        
        // Parse order type
        String typeStr = json.getString("type");
        try {
            this.type = OrderType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.type = OrderType.MARKET; // Default to MARKET if unknown
        }
        
        // Parse order side
        String sideStr = json.getString("side");
        try {
            this.side = OrderSide.valueOf(sideStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.side = OrderSide.BUY; // Default to BUY if unknown
        }
        
        this.quantity = json.getInt("qty");
        this.filledQuantity = json.optInt("filled_qty", 0);
        this.limitPrice = json.optDouble("limit_price", 0.0);
        this.stopPrice = json.optDouble("stop_price", 0.0);
        this.trailPercent = json.optDouble("trail_percent", 0.0);
        this.trailPrice = json.optDouble("trail_price", 0.0);
        
        // Parse time in force
        String tifStr = json.getString("time_in_force");
        try {
            this.timeInForce = TimeInForce.valueOf(tifStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.timeInForce = TimeInForce.DAY; // Default to DAY if unknown
        }
        
        // Parse status
        String statusStr = json.getString("status");
        try {
            this.status = OrderStatus.valueOf(statusStr.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            this.status = OrderStatus.NEW; // Default to NEW if unknown
        }
        
        // Parse timestamps
        this.createdAt = parseTimestamp(json.optString("created_at", ""));
        this.updatedAt = parseTimestamp(json.optString("updated_at", ""));
        this.filledAt = parseTimestamp(json.optString("filled_at", ""));
        this.expiredAt = parseTimestamp(json.optString("expired_at", ""));
        this.canceledAt = parseTimestamp(json.optString("canceled_at", ""));
    }
    
    // Setters for order parameters
    
    // Limit price - required for LIMIT and STOP_LIMIT orders
    public StockOrder setLimitPrice(double limitPrice) {
        this.limitPrice = limitPrice;
        return this;
    }
    
    // Stop price - required for STOP and STOP_LIMIT orders
    public StockOrder setStopPrice(double stopPrice) {
        this.stopPrice = stopPrice;
        return this;
    }
    
    // Trail percent - required for TRAILING_STOP orders (as percentage)
    public StockOrder setTrailPercent(double trailPercent) {
        this.trailPercent = trailPercent;
        return this;
    }
    
    // Trail price - alternative for TRAILING_STOP orders (as absolute price)
    public StockOrder setTrailPrice(double trailPrice) {
        this.trailPrice = trailPrice;
        return this;
    }
    
    // Update order status (usually from API responses)
    public void updateStatus(OrderStatus newStatus, int newFilledQuantity) {
        this.status = newStatus;
        this.filledQuantity = newFilledQuantity;
        this.updatedAt = System.currentTimeMillis();
        
        if (newStatus == OrderStatus.FILLED) {
            this.filledAt = this.updatedAt;
        } else if (newStatus == OrderStatus.EXPIRED) {
            this.expiredAt = this.updatedAt;
        } else if (newStatus == OrderStatus.CANCELED) {
            this.canceledAt = this.updatedAt;
        }
    }
    
    // Validate order before submission
    public boolean validate() {
        // Basic validation
        if (quantity <= 0) {
            return false;
        }
        
        // Order-specific validation
        switch (type) {
            case LIMIT:
                return limitPrice > 0;
            case STOP:
                return stopPrice > 0;
            case STOP_LIMIT:
                return limitPrice > 0 && stopPrice > 0;
            case TRAILING_STOP:
                return trailPercent > 0 || trailPrice > 0;
            default:
                return true; // MARKET orders need no specific validation
        }
    }
    
    // Generate client order ID
    private String generateClientOrderId() {
        // Format: PREFIX-TIMESTAMP-RANDOM
        return "order-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }
    
    // Parse ISO-8601 timestamp
    private long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return 0;
        }
        
        try {
            return Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }
    
    // Format timestamp to ISO-8601
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_INSTANT);
    }
    
    // Convert to JSON for API requests
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("symbol", symbol);
        json.put("qty", quantity);
        json.put("side", side.toString().toLowerCase());
        json.put("type", type.toString().toLowerCase());
        json.put("time_in_force", timeInForce.toString().toLowerCase());
        
        // Add conditional price parameters based on order type
        if (type == OrderType.LIMIT || type == OrderType.STOP_LIMIT) {
            json.put("limit_price", limitPrice);
        }
        
        if (type == OrderType.STOP || type == OrderType.STOP_LIMIT) {
            json.put("stop_price", stopPrice);
        }
        
        if (type == OrderType.TRAILING_STOP) {
            if (trailPercent > 0) {
                json.put("trail_percent", trailPercent);
            } else {
                json.put("trail_price", trailPrice);
            }
        }
        
        // Add client order ID if available
        if (clientOrderId != null && !clientOrderId.isEmpty()) {
            json.put("client_order_id", clientOrderId);
        }
        
        return json;
    }
    
    // Full JSON representation including all fields
    public JSONObject toDetailedJSON() {
        JSONObject json = toJSON();
        
        // Add all additional fields
        if (id != null && !id.isEmpty()) {
            json.put("id", id);
        }
        
        json.put("asset_id", assetId);
        json.put("filled_qty", filledQuantity);
        json.put("status", status.toString().toLowerCase().replace("_", "-"));
        
        // Add timestamps if available
        String createdAtStr = formatTimestamp(createdAt);
        if (createdAtStr != null) {
            json.put("created_at", createdAtStr);
        }
        
        String updatedAtStr = formatTimestamp(updatedAt);
        if (updatedAtStr != null) {
            json.put("updated_at", updatedAtStr);
        }
        
        String filledAtStr = formatTimestamp(filledAt);
        if (filledAtStr != null) {
            json.put("filled_at", filledAtStr);
        }
        
        String expiredAtStr = formatTimestamp(expiredAt);
        if (expiredAtStr != null) {
            json.put("expired_at", expiredAtStr);
        }
        
        String canceledAtStr = formatTimestamp(canceledAt);
        if (canceledAtStr != null) {
            json.put("canceled_at", canceledAtStr);
        }
        
        return json;
    }
    
    // Getters
    
    public String getId() {
        return id;
    }
    
    public String getClientOrderId() {
        return clientOrderId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public String getAssetId() {
        return assetId;
    }
    
    public OrderType getType() {
        return type;
    }
    
    public OrderSide getSide() {
        return side;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public int getFilledQuantity() {
        return filledQuantity;
    }
    
    public double getLimitPrice() {
        return limitPrice;
    }
    
    public double getStopPrice() {
        return stopPrice;
    }
    
    public double getTrailPercent() {
        return trailPercent;
    }
    
    public double getTrailPrice() {
        return trailPrice;
    }
    
    public TimeInForce getTimeInForce() {
        return timeInForce;
    }
    
    public OrderStatus getStatus() {
        return status;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public long getFilledAt() {
        return filledAt;
    }
    
    public long getExpiredAt() {
        return expiredAt;
    }
    
    public long getCanceledAt() {
        return canceledAt;
    }
}
