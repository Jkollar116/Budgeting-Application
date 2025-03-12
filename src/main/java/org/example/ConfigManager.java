package org.example;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages application configuration, particularly API keys from external resources.
 * Acts as a centralized configuration loader to avoid hardcoded credentials.
 */
public class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    private static final String CONFIG_FILE = "src/main/resources/keys.json";
    private static ConfigManager instance;
    private JSONObject config;

    /**
     * Private constructor to enforce singleton pattern.
     * Loads configuration from keys.json file.
     */
    private ConfigManager() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)));
            config = new JSONObject(content);
            LOGGER.info("Configuration loaded successfully");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read configuration file: " + e.getMessage(), e);
            config = new JSONObject();
        } catch (JSONException e) {
            LOGGER.log(Level.SEVERE, "Failed to parse configuration: " + e.getMessage(), e);
            config = new JSONObject();
        }
    }

    /**
     * Gets the singleton instance of ConfigManager.
     * 
     * @return The ConfigManager instance
     */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Gets an API key from the configuration.
     * 
     * @param keyName The name of the key in the configuration file
     * @return The API key value or null if not found
     */
    public String getApiKey(String keyName) {
        try {
            if (config.has(keyName)) {
                return config.getString(keyName);
            }
            LOGGER.warning("API key not found in configuration: " + keyName);
            return null;
        } catch (JSONException e) {
            LOGGER.log(Level.WARNING, "Error retrieving API key: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets the Etherscan API key from configuration.
     * 
     * @return The Etherscan API key
     */
    public String getEtherscanApiKey() {
        return getApiKey("etherscanApiKey");
    }

    /**
     * Gets the CoinMarketCap API key from configuration.
     * 
     * @return The CoinMarketCap API key
     */
    public String getCoinMarketCapApiKey() {
        return getApiKey("coinMarketCapApiKey");
    }
}
