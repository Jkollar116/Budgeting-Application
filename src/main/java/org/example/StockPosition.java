package org.example;

import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents a user's position in a particular stock
 */
public class StockPosition {
    private String symbol;
    private int quantity;
    private double averageEntryPrice;
    private double currentPrice;
    private double marketValue;
    private double unrealizedProfitLoss;
    private double unrealizedProfitLossPercent;
    private String assetId;
    private long lastUpdated;

    public StockPosition(String symbol, int quantity, double averageEntryPrice, 
                        double currentPrice, String assetId) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageEntryPrice = averageEntryPrice;
        this.currentPrice = currentPrice;
        this.assetId = assetId;
        this.lastUpdated = System.currentTimeMillis();
        
        // Calculate derived values
        updateDerivedValues();
    }

    public StockPosition(JSONObject json) {
        this.symbol = json.getString("symbol");
        this.quantity = json.getInt("qty");
        this.averageEntryPrice = json.getDouble("avg_entry_price");
        this.currentPrice = json.getDouble("current_price");
        this.marketValue = json.getDouble("market_value");
        this.unrealizedProfitLoss = json.getDouble("unrealized_pl");
        this.unrealizedProfitLossPercent = json.getDouble("unrealized_plpc");
        this.assetId = json.getString("asset_id");
        this.lastUpdated = json.optLong("last_updated", System.currentTimeMillis());
    }

    /**
     * Updates the current price and recalculates derived values
     */
    public void updatePrice(double newPrice) {
        this.currentPrice = newPrice;
        this.lastUpdated = System.currentTimeMillis();
        updateDerivedValues();
    }

    /**
     * Updates the position after a new trade
     */
    public void updateAfterTrade(int additionalQuantity, double tradePrice) {
        if (this.quantity + additionalQuantity == 0) {
            // Position closed
            this.quantity = 0;
            this.averageEntryPrice = 0;
        } else if (additionalQuantity > 0) {
            // Buying more
            double totalCost = (this.quantity * this.averageEntryPrice) + (additionalQuantity * tradePrice);
            this.quantity += additionalQuantity;
            this.averageEntryPrice = totalCost / this.quantity;
        } else {
            // Selling some but not all
            this.quantity += additionalQuantity; // additionalQuantity is negative when selling
        }
        
        updateDerivedValues();
    }

    private void updateDerivedValues() {
        // Calculate market value
        this.marketValue = this.quantity * this.currentPrice;
        
        // Calculate unrealized P&L
        this.unrealizedProfitLoss = this.marketValue - (this.quantity * this.averageEntryPrice);
        
        // Calculate unrealized P&L percent
        if (this.quantity > 0 && this.averageEntryPrice > 0) {
            double costBasis = this.quantity * this.averageEntryPrice;
            this.unrealizedProfitLossPercent = (this.unrealizedProfitLoss / costBasis) * 100;
        } else {
            this.unrealizedProfitLossPercent = 0;
        }
        
        // Round values for display
        this.marketValue = round(this.marketValue, 2);
        this.unrealizedProfitLoss = round(this.unrealizedProfitLoss, 2);
        this.unrealizedProfitLossPercent = round(this.unrealizedProfitLossPercent, 2);
    }
    
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getAverageEntryPrice() {
        return averageEntryPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getMarketValue() {
        return marketValue;
    }

    public double getUnrealizedProfitLoss() {
        return unrealizedProfitLoss;
    }

    public double getUnrealizedProfitLossPercent() {
        return unrealizedProfitLossPercent;
    }

    public String getAssetId() {
        return assetId;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("symbol", symbol);
        json.put("qty", quantity);
        json.put("avg_entry_price", averageEntryPrice);
        json.put("current_price", currentPrice);
        json.put("market_value", marketValue);
        json.put("unrealized_pl", unrealizedProfitLoss);
        json.put("unrealized_plpc", unrealizedProfitLossPercent);
        json.put("asset_id", assetId);
        json.put("last_updated", lastUpdated);
        return json;
    }
}
