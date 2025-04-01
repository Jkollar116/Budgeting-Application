package org.example;

import java.util.Date;

/**
 * Represents a stock transaction (buy or sell)
 */
public class StockTransaction {
    private String symbol;
    private String type; // "buy" or "sell"
    private int quantity;
    private double price;
    private Date timestamp;

    /**
     * Constructor for StockTransaction
     * 
     * @param symbol The stock symbol
     * @param type The transaction type ("buy" or "sell")
     * @param quantity The quantity of shares
     * @param price The price per share
     * @param timestamp The timestamp of the transaction
     */
    public StockTransaction(String symbol, String type, int quantity, double price, Date timestamp) {
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
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
     * Get the transaction type
     * 
     * @return The transaction type
     */
    public String getType() {
        return type;
    }

    /**
     * Set the transaction type
     * 
     * @param type The transaction type
     */
    public void setType(String type) {
        this.type = type;
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
     * Get the price per share
     * 
     * @return The price per share
     */
    public double getPrice() {
        return price;
    }

    /**
     * Set the price per share
     * 
     * @param price The price per share
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Get the timestamp of the transaction
     * 
     * @return The timestamp of the transaction
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Set the timestamp of the transaction
     * 
     * @param timestamp The timestamp of the transaction
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "StockTransaction{" +
                "symbol='" + symbol + '\'' +
                ", type='" + type + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", timestamp=" + timestamp +
                '}';
    }
}
