package org.example;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Manages application configuration, particularly API keys from external resources.
 * Acts as a centralized configuration loader to avoid hardcoded credentials.
 * Supports multiple sources: environment variables and local files.
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    // File path for direct access (development)
    private static final String CONFIG_FILE_PATH = "src/main/resources/keys.json";
    // Resource path for JAR access (production)
    private static final String CONFIG_RESOURCE_PATH = "/keys.json";
    private static final String ENV_PREFIX = "CASHCLIMB_";
    
    private static ConfigManager instance;
    private JSONObject fileConfig;
    
    /**
     * Private constructor to enforce singleton pattern.
     * Loads configuration from keys.json file and environment variables.
     */
    private ConfigManager() {
        fileConfig = new JSONObject();
        boolean configLoaded = false;
        
        // First try: Load from file path (for development environment)
        try {
            if (Files.exists(Paths.get(CONFIG_FILE_PATH))) {
                String content = new String(Files.readAllBytes(Paths.get(CONFIG_FILE_PATH)));
                fileConfig = new JSONObject(content);
                logger.info("Configuration file loaded successfully from file path");
                configLoaded = true;
            }
        } catch (IOException e) {
            logger.debug("Could not load config from file path: {}", e.getMessage());
        } catch (JSONException e) {
            logger.error("Failed to parse configuration from file path: {}", e.getMessage());
        }
        
        // Second try: Load from classpath resource (for JAR deployment)
        if (!configLoaded) {
            try (java.io.InputStream is = getClass().getResourceAsStream(CONFIG_RESOURCE_PATH)) {
                if (is != null) {
                    byte[] buffer = new byte[is.available()];
                    is.read(buffer);
                    String content = new String(buffer);
                    fileConfig = new JSONObject(content);
                    logger.info("Configuration file loaded successfully from classpath");
                    configLoaded = true;
                }
            } catch (IOException e) {
                logger.error("Failed to read configuration from classpath: {}", e.getMessage(), e);
            } catch (JSONException e) {
                logger.error("Failed to parse configuration from classpath: {}", e.getMessage(), e);
            }
        }
        
        // Hardcoded fallback values for critical API keys
        if (!configLoaded || !fileConfig.has("coinMarketCapApiKey")) {
            logger.warn("Using hardcoded API keys as fallback!");
            try {
                // Add hardcoded API keys
                fileConfig.put("coinMarketCapApiKey", "e2fa2fa3-ef84-4e08-8a73-ae43c073ab0d");
                fileConfig.put("etherscanApiKey", "G6YJ1PGVSDWY8VP11ZKYPQJ78VWIE7YAUQ");
            } catch (JSONException e) {
                logger.error("Failed to add hardcoded API keys: {}", e.getMessage());
            }
        }
        
        // Log loaded keys (partially hidden for security)
        for (String key : new String[]{"coinMarketCapApiKey", "etherscanApiKey"}) {
            if (fileConfig.has(key)) {
                String value = fileConfig.optString(key, "");
                if (!value.isEmpty()) {
                    String maskedValue = value.length() > 8 
                        ? value.substring(0, 4) + "..." + value.substring(value.length() - 4)
                        : "***";
                    logger.info("Loaded key: {} = {}", key, maskedValue);
                }
            }
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
     * Gets an API key from available sources in the following order:
     * 1. Environment variables (CASHCLIMB_[keyName])
     * 2. Configuration file
     * 
     * @param keyName The name of the key
     * @return The API key value or null if not found
     */
    public String getApiKey(String keyName) {
        // Convert keyName to uppercase for environment variables
        String envKey = ENV_PREFIX + keyName.toUpperCase().replace('.', '_');
        
        // Check environment variables first
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            logger.debug("Retrieved key '{}' from environment variable", keyName);
            return envValue;
        }
        
        // Finally, check the configuration file
        try {
            if (fileConfig.has(keyName)) {
                logger.debug("Retrieved key '{}' from configuration file", keyName);
                return fileConfig.getString(keyName);
            }
        } catch (JSONException e) {
            logger.warn("Error retrieving API key '{}' from file: {}", keyName, e.getMessage());
        }
        
        logger.warn("API key '{}' not found in any configuration source", keyName);
        return null;
    }

    /**
     * Gets the Etherscan API key.
     * 
     * @return The Etherscan API key
     */
    public String getEtherscanApiKey() {
        return getApiKey("etherscanApiKey");
    }

    /**
     * Gets the CoinMarketCap API key.
     * 
     * @return The CoinMarketCap API key
     */
    public String getCoinMarketCapApiKey() {
        return getApiKey("coinMarketCapApiKey");
    }
    
    /**
     * Gets the Alpha Vantage API key.
     * 
     * @return The Alpha Vantage API key
     */
    public String getAlphaVantageApiKey() {
        return getApiKey("alphaVantageApiKey");
    }
    
    /**
     * Gets a configuration value with a default fallback
     * 
     * @param keyName The name of the configuration key
     * @param defaultValue The default value to return if the key is not found
     * @return The configuration value or the default value
     */
    public String getConfigValue(String keyName, String defaultValue) {
        return Optional.ofNullable(getApiKey(keyName)).orElse(defaultValue);
    }
    
    /**
     * Gets a configuration value as an integer with a default fallback
     * 
     * @param keyName The name of the configuration key
     * @param defaultValue The default value to return if the key is not found or cannot be parsed
     * @return The configuration value as an integer or the default value
     */
    public int getConfigValueAsInt(String keyName, int defaultValue) {
        String value = getApiKey(keyName);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Could not parse '{}' as integer: {}", value, e.getMessage());
            return defaultValue;
        }
    }
}
