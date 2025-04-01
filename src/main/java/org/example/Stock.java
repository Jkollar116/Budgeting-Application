package org.example;

/**
 * Represents a stock with its current market data
 */
public class Stock {
    private String symbol;
    private String name;
    private String exchange;
    private double price;
    private double change;
    private double changePercent;
    private long volume;
    private double open;
    private double high;
    private double low;
    private double previousClose;
    private String assetId;
    private String lastUpdated;

    /**
     * Constructor for Stock
     * 
     * @param symbol The stock symbol
     */
    public Stock(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Constructor for Stock with name
     * 
     * @param symbol The stock symbol
     * @param name The company name
     */
    public Stock(String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
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
     * Get the company name
     * 
     * @return The company name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the company name
     * 
     * @param name The company name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the exchange
     * 
     * @return The exchange
     */
    public String getExchange() {
        return exchange;
    }

    /**
     * Set the exchange
     * 
     * @param exchange The exchange
     */
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    /**
     * Get the current price
     * 
     * @return The current price
     */
    public double getPrice() {
        return price;
    }

    /**
     * Set the current price
     * 
     * @param price The current price
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Get the price change
     * 
     * @return The price change
     */
    public double getChange() {
        return change;
    }

    /**
     * Set the price change
     * 
     * @param change The price change
     */
    public void setChange(double change) {
        this.change = change;
    }

    /**
     * Get the price change percent
     * 
     * @return The price change percent
     */
    public double getChangePercent() {
        return changePercent;
    }

    /**
     * Set the price change percent
     * 
     * @param changePercent The price change percent
     */
    public void setChangePercent(double changePercent) {
        this.changePercent = changePercent;
    }

    /**
     * Get the trading volume
     * 
     * @return The trading volume
     */
    public long getVolume() {
        return volume;
    }

    /**
     * Set the trading volume
     * 
     * @param volume The trading volume
     */
    public void setVolume(long volume) {
        this.volume = volume;
    }

    /**
     * Get the opening price
     * 
     * @return The opening price
     */
    public double getOpen() {
        return open;
    }

    /**
     * Set the opening price
     * 
     * @param open The opening price
     */
    public void setOpen(double open) {
        this.open = open;
    }

    /**
     * Get the day's high price
     * 
     * @return The day's high price
     */
    public double getHigh() {
        return high;
    }

    /**
     * Set the day's high price
     * 
     * @param high The day's high price
     */
    public void setHigh(double high) {
        this.high = high;
    }

    /**
     * Get the day's low price
     * 
     * @return The day's low price
     */
    public double getLow() {
        return low;
    }

    /**
     * Set the day's low price
     * 
     * @param low The day's low price
     */
    public void setLow(double low) {
        this.low = low;
    }

    /**
     * Get the previous closing price
     * 
     * @return The previous closing price
     */
    public double getPreviousClose() {
        return previousClose;
    }

    /**
     * Set the previous closing price
     * 
     * @param previousClose The previous closing price
     */
    public void setPreviousClose(double previousClose) {
        this.previousClose = previousClose;
    }

    /**
     * Get the asset ID
     * 
     * @return The asset ID
     */
    public String getAssetId() {
        return assetId;
    }

    /**
     * Set the asset ID
     * 
     * @param assetId The asset ID
     */
    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    /**
     * Get the last updated timestamp
     * 
     * @return The last updated timestamp
     */
    public String getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Set the last updated timestamp
     * 
     * @param lastUpdated The last updated timestamp
     */
    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public String toString() {
        return "Stock{" +
                "symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", exchange='" + exchange + '\'' +
                ", price=" + price +
                ", change=" + change +
                ", changePercent=" + changePercent +
                ", volume=" + volume +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", previousClose=" + previousClose +
                ", assetId='" + assetId + '\'' +
                ", lastUpdated='" + lastUpdated + '\'' +
                '}';
    }
}
