package org.example;

/**
 * Represents a stock position in a user's portfolio
 */
public class StockPosition {
    private String symbol;
    private int quantity;
    private double averagePrice;

    /**
     * Constructor for StockPosition
     * 
     * @param symbol The stock symbol
     * @param quantity The quantity of shares
     * @param averagePrice The average purchase price
     */
    public StockPosition(String symbol, int quantity, double averagePrice) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
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
     * Get the average purchase price
     * 
     * @return The average purchase price
     */
    public double getAveragePrice() {
        return averagePrice;
    }

    /**
     * Set the average purchase price
     * 
     * @param averagePrice The average purchase price
     */
    public void setAveragePrice(double averagePrice) {
        this.averagePrice = averagePrice;
    }

    @Override
    public String toString() {
        return "StockPosition{" +
                "symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", averagePrice=" + averagePrice +
                '}';
    }
}
