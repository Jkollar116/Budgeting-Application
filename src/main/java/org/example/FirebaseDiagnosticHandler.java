package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP Handler for Firebase diagnostics
 * Access at /api/diagnostic/firebase after deployment
 */
public class FirebaseDiagnosticHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only allow GET requests
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, 0);
            exchange.getResponseBody().close();
            return;
        }
        
        // Capture diagnostic output
        ByteArrayOutputStream diagnosticOutput = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        
        // Redirect System.out and System.err to our capture stream
        System.setOut(new PrintStream(diagnosticOutput, true, StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(diagnosticOutput, true, StandardCharsets.UTF_8.name()));
        
        try {
            // Run the Firebase diagnostic test in a separate thread
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                FirebaseDiagnostic.testFirebaseConnection();
            });
            
            // Wait for the diagnostic to complete (max 30 seconds)
            future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            
            // Also test Firestore availability through FirestoreService
            boolean firestoreAvailable = FirestoreService.getInstance().isAvailable();
            System.out.println("\n============ FIRESTORE SERVICE CHECK ============");
            System.out.println("FirestoreService.isAvailable(): " + firestoreAvailable);
            
            // Test read operation through service
            System.out.println("\n============ FIRESTORE READ TEST ============");
            String userId = "test-user-diagnostic";
            Map<String, Object> userProfile = FirestoreService.getInstance().getUserProfile(userId);
            System.out.println("Read user profile for test-user-diagnostic: " + (userProfile != null ? "Success" : "Failed"));
            System.out.println("Profile contents: " + (userProfile != null ? (userProfile.isEmpty() ? "Empty" : userProfile.toString()) : "Null"));
            
            // Test write operation through service
            System.out.println("\n============ FIRESTORE WRITE TEST ============");
            Map<String, Object> testData = new HashMap<>();
            testData.put("timestamp", System.currentTimeMillis());
            testData.put("diagnostic", "test");
            testData.put("source", "heroku");
            boolean saveResult = FirestoreService.getInstance().saveUserProfile(userId, testData);
            System.out.println("Write test profile: " + (saveResult ? "Success" : "Failed"));
            
        } catch (Exception e) {
            System.err.println("Error running diagnostic: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Restore original output streams
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        
        // Get the captured output
        String diagnosticResult = diagnosticOutput.toString(StandardCharsets.UTF_8.name());
        
        // Create HTML response
        String htmlResponse = "<!DOCTYPE html><html><head>" +
                "<title>Firebase Diagnostic Results</title>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }" +
                "h1 { color: #333; }" +
                "pre { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; }" +
                ".success { color: green; }" +
                ".failure { color: red; }" +
                "</style></head><body>" +
                "<h1>Firebase Diagnostic Results</h1>" +
                "<p>The following diagnostic test results show if Firebase is working correctly:</p>" +
                "<pre>" + diagnosticResult.replace("<", "&lt;").replace(">", "&gt;") + "</pre>" +
                "<p>If you see any errors above, please check your Firebase configuration.</p>" +
                "<p>Return to <a href='/'>home page</a>.</p>" +
                "</body></html>";
        
        // Send response
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] responseBytes = htmlResponse.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
}
