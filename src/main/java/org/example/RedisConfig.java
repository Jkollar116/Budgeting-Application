package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration class for Redis integration
 */
public class RedisConfig {
    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);
    private static RedisTemplate<String, Object> redisTemplate;
    private static boolean redisAvailable = false;
    
    /**
     * Checks if Redis is available based on the configuration
     * 
     * @return true if Redis is available and configured
     */
    public static boolean isRedisAvailable() {
        if (redisTemplate != null) {
            return true;
        }
        
        // Try to initialize Redis
        ConfigManager configManager = ConfigManager.getInstance();
        String redisHost = configManager.getConfigValue("redis.host", "localhost");
        int redisPort = configManager.getConfigValueAsInt("redis.port", 6379);
        
        try {
            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
            
            // Check if authentication is required
            String redisPassword = configManager.getConfigValue("redis.password", null);
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                redisConfig.setPassword(redisPassword);
            }
            
            // Try to establish a connection
            RedisConnectionFactory connectionFactory = new LettuceConnectionFactory(redisConfig);
            ((LettuceConnectionFactory) connectionFactory).afterPropertiesSet();
            
            // Create and configure RedisTemplate
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
            template.setHashKeySerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
            template.afterPropertiesSet();
            
            // Test the connection
            try {
                template.getConnectionFactory().getConnection().ping();
                redisTemplate = template;
                redisAvailable = true;
                logger.info("Redis connection established successfully at {}:{}", redisHost, redisPort);
                return true;
            } catch (Exception e) {
                logger.error("Failed to connect to Redis: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error initializing Redis: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the configured RedisTemplate
     * 
     * @return The RedisTemplate instance or null if Redis is not available
     */
    public static RedisTemplate<String, Object> getRedisTemplate() {
        if (redisAvailable && redisTemplate != null) {
            return redisTemplate;
        } else if (isRedisAvailable()) {
            return redisTemplate;
        }
        return null;
    }
}
