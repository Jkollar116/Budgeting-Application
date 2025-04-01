package org.example;

import java.util.Date;
import java.util.UUID;

/**
 * Represents a stock order (buy or sell)
 */
public class StockOrder {
    private String id;
    private String symbol;
    private String type; // "market", "limit", "stop", "stop_limit", etc.
    private String side; // "buy" or "sell"
    private int quantity;
    private double limitPrice;
    private double stopPrice;
    private String status; // "open", "filled", "canceled", etc.
    private Date createdAt;

    /**
     * Constructor for StockOrder
     * 
     * @param symbol The stock symbol
     * @param type The order type ("market", "limit", "stop", etc.)
     * @param side The order side ("buy" or "sell")
     * @param quantity The quantity of shares
     */
    public StockOrder(String symbol, String type, String side, int quantity) {
        this.id = UUID.randomUUID().toString();
        this.symbol = symbol;
        this.type = type;
        this.side = side;
        this.quantity = quantity;
        this.status = "open";
        this.createdAt = new Date();
    }

    /**
     * Constructor for limit order
     * 
     * @param symbol The stock symbol
     * @param side The order side ("buy" or "sell")
     * @param quantity The quantity of shares
     * @param limitPrice The limit price
     */
    public StockOrder(String symbol, String side, int quantity, double limitPrice) {
        this(symbol, "limit", side, quantity);
        this.limitPrice = limitPrice;
    }

    /**
     * Constructor for stop order
     * 
     * @param symbol The stock symbol
     * @param side The order side ("buy" or "sell")
     * @param quantity The quantity of shares
     * @param stopPrice The stop price
     * @param isStop Whether this is a stop order (true) or stop-limit order (false)
     */
    public StockOrder(String symbol, String side, int quantity, double stopPrice, boolean isStop) {
        this(symbol, isStop ? "stop" : "stop_limit", side, quantity);
        this.stopPrice = stopPrice;
    }

    /**
     * Constructor for stop-limit order
     * 
     * @param symbol The stock symbol
     * @param side The order side ("buy" or "sell")
     * @param quantity The quantity of shares
     * @param stopPrice The stop price
     * @param limitPrice The limit price
     */
    public StockOrder(String symbol, String side, int quantity, double stopPrice, double limitPrice) {
        this(symbol, "stop_limit", side, quantity);
        this.stopPrice = stopPrice;
        this.limitPrice = limitPrice;
    }

    /**
     * Get the order ID
     * 
     * @return The order ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set the order ID
     * 
     * @param id The order ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the stock symbol
     * 
     * @return The stock symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Set the stock symbol
     * 
     * @param symbol The stock symbol
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Get the order type
     * 
     * @return The order type
     */
    public String getType() {
        return type;
    }

    /**
     * Set the order type
     * 
     * @param type The order type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the order side
     * 
     * @return The order side
     */
    public String getSide() {
        return side;
    }

    /**
     * Set the order side
     * 
     * @param side The order side
     */
    public void setSide(String side) {
        this.side = side;
    }

    /**
     * Get the quantity of shares
     * 
     * @return The quantity of shares
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * Set the quantity of shares
     * 
     * @param quantity The quantity of shares
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /**
     * Get the limit price
     * 
     * @return The limit price
     */
    public double getLimitPrice() {
        return limitPrice;
    }

    /**
     * Set the limit price
     * 
     * @param limitPrice The limit price
     */
    public void setLimitPrice(double limitPrice) {
        this.limitPrice = limitPrice;
    }

    /**
     * Get the stop price
     * 
     * @return The stop price
     */
    public double getStopPrice() {
        return stopPrice;
    }

    /**
     * Set the stop price
     * 
     * @param stopPrice The stop price
     */
    public void setStopPrice(double stopPrice) {
        this.stopPrice = stopPrice;
    }

    /**
     * Get the order status
     * 
     * @return The order status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Set the order status
     * 
     * @param status The order status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Get the creation timestamp
     * 
     * @return The creation timestamp
     */
    public Date getCreatedAt() {
        return createdAt;
    }

    /**
     * Set the creation timestamp
     * 
     * @param createdAt The creation timestamp
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "StockOrder{" +
                "id='" + id + '\'' +
                ", symbol='" + symbol + '\'' +
                ", type='" + type + '\'' +
                ", side='" + side + '\'' +
                ", quantity=" + quantity +
                ", limitPrice=" + limitPrice +
                ", stopPrice=" + stopPrice +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
