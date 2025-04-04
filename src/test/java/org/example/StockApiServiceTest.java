package org.example;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockApiServiceTest {

    @Mock
    private ConfigManager configManager;
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    @InjectMocks
    private StockApiService stockApiService;
    
    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Setup Redis mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Use reflection to set the redisTemplate field
        Field redisTemplateField = StockApiService.class.getDeclaredField("redisTemplate");
        redisTemplateField.setAccessible(true);
        redisTemplateField.set(stockApiService, redisTemplate);
        
        // Set useRedisCache to true
        Field useRedisCacheField = StockApiService.class.getDeclaredField("useRedisCache");
        useRedisCacheField.setAccessible(true);
        useRedisCacheField.set(stockApiService, true);
        
        // Mock the config manager
        when(configManager.getAlphaVantageApiKey()).thenReturn("test-api-key");
    }
    
    @Test
    void testGetStockQuote_fromRedisCache() throws Exception {
        // Setup test data
        Stock cachedStock = new Stock("AAPL");
        cachedStock.setPrice(150.0);
        cachedStock.setName("Apple Inc.");
        
        // Mock Redis cache hit
        when(valueOperations.get("quote_AAPL")).thenReturn(cachedStock);
        
        // Test
        Stock result = stockApiService.getStockQuote("AAPL");
        
        // Verify
        assertNotNull(result);
        assertEquals("AAPL", result.getSymbol());
        assertEquals(150.0, result.getPrice());
        assertEquals("Apple Inc.", result.getName());
        
        // Verify Redis was called but not HTTP
        verify(valueOperations).get("quote_AAPL");
        verifyNoMoreInteractions(valueOperations);
    }
    
    @Test
    void testGetStockQuote_nullSymbol() {
        // Test with null symbol
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            stockApiService.getStockQuote(null);
        });
        
        assertEquals("Stock symbol cannot be empty", exception.getMessage());
    }
    
    @Test
    void testGetStockQuote_emptySymbol() {
        // Test with empty symbol
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            stockApiService.getStockQuote("  ");
        });
        
        assertEquals("Stock symbol cannot be empty", exception.getMessage());
    }
    
    @Test
    void testGetApiKey_notConfigured() throws Exception {
        // Mock missing API key
        when(configManager.getAlphaVantageApiKey()).thenReturn(null);
        
        // Get the private method using reflection
        Method getApiKeyMethod = StockApiService.class.getDeclaredMethod("getApiKey");
        getApiKeyMethod.setAccessible(true);
        
        // Test
        Exception exception = assertThrows(IOException.class, () -> {
            getApiKeyMethod.invoke(stockApiService);
        });
        
        assertTrue(exception.getCause().getMessage().contains("Alpha Vantage API key not configured"));
    }
    
    @Test
    void testParseDouble() throws Exception {
        // Create test JsonObject
        JsonObject json = new JsonObject();
        json.addProperty("validDouble", "123.45");
        json.addProperty("invalidDouble", "not-a-number");
        
        // Get the private method using reflection
        Method parseDoubleMethod = StockApiService.class.getDeclaredMethod("parseDouble", JsonObject.class, String.class);
        parseDoubleMethod.setAccessible(true);
        
        // Test valid double
        double validResult = (double) parseDoubleMethod.invoke(stockApiService, json, "validDouble");
        assertEquals(123.45, validResult);
        
        // Test invalid double
        double invalidResult = (double) parseDoubleMethod.invoke(stockApiService, json, "invalidDouble");
        assertEquals(0.0, invalidResult);
        
        // Test missing key
        double missingResult = (double) parseDoubleMethod.invoke(stockApiService, json, "missingKey");
        assertEquals(0.0, missingResult);
    }
    
    // Note: More comprehensive tests would mock the HTTP response for actual API calls
    // This would test the full flow from API call to response parsing
}
