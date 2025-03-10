package org.example;

import org.json.JSONObject;

/**
 * Represents a stock with basic market information
 */
public class Stock {
    private String symbol;
    private String name;
    private String exchange;
    private String assetClass;
    private boolean tradable;
    private String id; // Alpaca asset ID

    public Stock(String symbol, String name, String exchange, String assetClass, boolean tradable, String id) {
        this.symbol = symbol;
        this.name = name;
        this.exchange = exchange;
        this.assetClass = assetClass;
        this.tradable = tradable;
        this.id = id;
    }

    public Stock(JSONObject json) {
        this.id = json.optString("id", "");
        this.symbol = json.optString("symbol", "");
        this.name = json.optString("name", "");
        this.exchange = json.optString("exchange", "");
        this.assetClass = json.optString("class", "");
        this.tradable = json.optBoolean("tradable", false);
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getExchange() {
        return exchange;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public boolean isTradable() {
        return tradable;
    }

    public String getId() {
        return id;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("symbol", symbol);
        json.put("name", name);
        json.put("exchange", exchange);
        json.put("assetClass", assetClass);
        json.put("tradable", tradable);
        return json;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - %s", symbol, name, exchange);
    }
}
