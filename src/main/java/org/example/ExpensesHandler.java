package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.cloud.firestore.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Enhanced handler for expense-related API endpoints
 * Uses FirestoreService for more reliable Firebase operations
 */
public class ExpensesHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ExpensesHandler.class.getName());
    private final FirestoreService firestoreService;
    private static final String EXPENSES_COLLECTION = "Expenses";
    private static final String SUMMARIES_COLLECTION = "Summaries";

    public ExpensesHandler() {
        this.firestoreService = FirestoreService.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LOGGER.info("ExpensesHandler invoked: " + exchange.getRequestMethod());
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            LOGGER.warning("Unauthorized expense operation attempt: missing cookies");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        
        if (idToken == null || localId == null) {
            LOGGER.warning("Unauthorized expense operation attempt: invalid cookies");
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        if (!firestoreService.isInitialized()) {
            LOGGER.severe("Firestore service not initialized");
            exchange.sendResponseHeaders(500, -1);
            return;
        }

        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            handleGet(exchange, localId);
        } else if ("POST".equalsIgnoreCase(method)) {
            handlePost(exchange, localId);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            handleDelete(exchange, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handleGet(HttpExchange exchange, String localId) throws IOException {
        try {
            LOGGER.info("Fetching expenses for user: " + localId);
            
            // Get collection from Firestore
            CollectionReference expensesRef = firestoreService.getSubcollection(localId, EXPENSES_COLLECTION);
            
            if (expensesRef == null) {
                LOGGER.severe("Failed to get expenses collection reference");
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            
            // Query all expenses
            QuerySnapshot querySnapshot = expensesRef.get().get();
            
            // Convert to JSON response
            JSONObject response = new JSONObject();
            JSONArray documents = new JSONArray();
            
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Map<String, Object> data = doc.getData();
                
                if (data != null) {
                    // Convert to Firestore API format (compatible with original implementation)
                    JSONObject item = new JSONObject();
                    item.put("name", doc.getReference().getPath());
                    
                    JSONObject fields = new JSONObject();
                    
                    // Convert each field to Firestore API format
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        
                        if (value instanceof String) {
                            fields.put(key, new JSONObject().put("stringValue", value));
                        } else if (value instanceof Double || value instanceof Float) {
                            fields.put(key, new JSONObject().put("doubleValue", value));
                        } else if (value instanceof Integer || value instanceof Long) {
                            fields.put(key, new JSONObject().put("integerValue", value));
                        } else if (value instanceof Boolean) {
                            fields.put(key, new JSONObject().put("booleanValue", value));
                        } else if (value instanceof Date) {
                            fields.put(key, new JSONObject().put("timestampValue", 
                                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                                            .format((Date)value)));
                        }
                    }
                    
                    item.put("fields", fields);
                    documents.put(item);
                }
            }
            
            response.put("documents", documents);
            
            // Send response
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.getResponseBody().close();
            
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Error fetching expenses", e);
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private void handlePost(HttpExchange exchange, String localId) throws IOException {
        try {
            // Read request body
            String body = readAll(exchange.getRequestBody());
            LOGGER.info("Received expense POST payload: " + body);
            
            // Parse JSON data
            JSONObject jsonRequest = new JSONObject(body);
            String date = jsonRequest.optString("date", "");
            String name = jsonRequest.optString("name", "");
            String category = jsonRequest.optString("category", "");
            double total;
            
            try {
                total = jsonRequest.getDouble("total");
            } catch (Exception e) {
                LOGGER.warning("Invalid expense total value");
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            
            if (date.isEmpty() || name.isEmpty() || category.isEmpty()) {
                LOGGER.warning("Missing required expense fields");
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            
            // Parse date to extract year and month for easier querying
            LocalDate parsedDate = tryParseDate(date);
            String yearMonthField = "";
            String yearField = "";
            int year = 0;
            int month = 0;
            
            if (parsedDate != null) {
                year = parsedDate.getYear();
                month = parsedDate.getMonthValue();
                yearMonthField = year + "_" + (month < 10 ? "0" : "") + month;
                yearField = String.valueOf(year);
            } else {
                LOGGER.warning("Could not parse date: " + date);
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            
            // Generate a unique transaction ID for better tracking
            String transactionId = "exp_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 1000);
            
            // Current timestamp for created/updated metadata
            long currentTimestamp = System.currentTimeMillis();
            
            // Prepare data for Firestore
            Map<String, Object> expenseData = new HashMap<>();
            expenseData.put("date", date);
            expenseData.put("name", name);
            expenseData.put("category", category);
            expenseData.put("total", total);
            expenseData.put("yearMonth", yearMonthField);
            expenseData.put("year", yearField);
            expenseData.put("transactionId", transactionId);
            expenseData.put("createdAt", currentTimestamp);
            expenseData.put("updatedAt", currentTimestamp);
            
            // Save to Firestore using our service
            String docId = UUID.randomUUID().toString();
            DocumentReference docRef = firestoreService.getSubcollectionDocument(localId, EXPENSES_COLLECTION, docId);
            
            if (docRef == null) {
                LOGGER.severe("Failed to get expense document reference");
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            
            // Save the expense document
            docRef.set(expenseData).get();
            LOGGER.info("Expense saved successfully with ID: " + docId);
            
            // Update the monthly summary
            updateMonthlySummary(localId, parsedDate, total, category);
            
            // Prepare success response
            byte[] msg = "Expense added successfully.".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, msg.length);
            exchange.getResponseBody().write(msg);
            exchange.getResponseBody().close();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving expense", e);
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private void handleDelete(HttpExchange exchange, String localId) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            String docId = null;
            
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("docId=")) {
                        docId = URLDecoder.decode(param.substring(6), "UTF-8");
                        break;
                    }
                }
            }
            
            if (docId == null || docId.isEmpty()) {
                LOGGER.warning("DELETE missing docId parameter");
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            
            // Get the document reference
            DocumentReference docRef = firestoreService.getSubcollectionDocument(localId, EXPENSES_COLLECTION, docId);
            
            if (docRef == null) {
                LOGGER.severe("Failed to get expense document reference for deletion");
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            
            // Get the expense details before deleting to update summary
            DocumentSnapshot docSnapshot = docRef.get().get();
            
            if (docSnapshot.exists()) {
                Map<String, Object> data = docSnapshot.getData();
                if (data != null) {
                    // Extract the data we need to update the summary
                    String date = (String) data.get("date");
                    String category = (String) data.get("category");
                    double total = 0.0;
                    
                    if (data.get("total") instanceof Number) {
                        total = ((Number) data.get("total")).doubleValue();
                    }
                    
                    // Get the date object
                    LocalDate parsedDate = tryParseDate(date);
                    
                    // Delete the document
                    docRef.delete().get();
                    LOGGER.info("Expense deleted successfully: " + docId);
                    
                    // Update the monthly summary by subtracting the expense
                    if (parsedDate != null && total > 0) {
                        updateMonthlySummary(localId, parsedDate, -total, category);
                    }
                } else {
                    // Delete anyway even if we couldn't get the data
                    docRef.delete().get();
                    LOGGER.info("Expense deleted successfully (no data available): " + docId);
                }
                
                exchange.sendResponseHeaders(200, -1);
            } else {
                LOGGER.warning("Expense document not found: " + docId);
                exchange.sendResponseHeaders(404, -1);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting expense", e);
            exchange.sendResponseHeaders(500, -1);
        }
    }

    /**
     * Updates the monthly summary document in Firestore for faster dashboard reads.
     * Creates or updates a document in the Summaries collection with pre-aggregated expense data.
     */
    private void updateMonthlySummary(String localId, LocalDate date, double amount, String category) {
        if (date == null) {
            LOGGER.warning("Cannot update monthly summary: Invalid date");
            return;
        }
        
        try {
            int year = date.getYear();
            int month = date.getMonthValue();
            String yearMonth = year + "_" + (month < 10 ? "0" : "") + month;
            String summaryId = "expense_" + yearMonth;
            
            // Get the summary document reference
            DocumentReference summaryRef = firestoreService.getSubcollectionDocument(
                localId, SUMMARIES_COLLECTION, summaryId);
            
            if (summaryRef == null) {
                LOGGER.severe("Failed to get summary document reference");
                return;
            }
            
            // Check if the document exists
            DocumentSnapshot summarySnapshot = summaryRef.get().get();
            boolean documentExists = summarySnapshot.exists();
            
            double existingTotal = 0.0;
            int existingCount = 0;
            Map<String, Double> categories = new HashMap<>();
            
            if (documentExists) {
                Map<String, Object> data = summarySnapshot.getData();
                
                if (data != null) {
                    // Extract existing data
                    if (data.get("totalExpense") instanceof Number) {
                        existingTotal = ((Number) data.get("totalExpense")).doubleValue();
                    }
                    
                    if (data.get("expenseCount") instanceof Number) {
                        existingCount = ((Number) data.get("expenseCount")).intValue();
                    }
                    
                    // Extract existing category map
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingCategories = (Map<String, Object>) data.get("categories");
                    
                    if (existingCategories != null) {
                        for (Map.Entry<String, Object> entry : existingCategories.entrySet()) {
                            if (entry.getValue() instanceof Number) {
                                categories.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                            }
                        }
                    }
                }
            }
            
            // Calculate new values
            double newTotal = existingTotal + amount;
            int newCount = amount > 0 ? existingCount + 1 : Math.max(0, existingCount - 1);
            
            // Update category total
            double categoryTotal = categories.getOrDefault(category, 0.0) + amount;
            categories.put(category, Math.max(0, categoryTotal)); // Ensure we don't go negative
            
            // Create new summary data
            Map<String, Object> summaryData = new HashMap<>();
            summaryData.put("totalExpense", newTotal);
            summaryData.put("expenseCount", newCount);
            summaryData.put("categories", categories);
            summaryData.put("yearMonth", yearMonth);
            summaryData.put("year", String.valueOf(year));
            summaryData.put("month", month);
            summaryData.put("lastUpdated", System.currentTimeMillis());
            
            if (!documentExists) {
                summaryData.put("createdAt", System.currentTimeMillis());
            }
            
            // Save or update the summary document
            summaryRef.set(summaryData).get();
            LOGGER.info("Monthly expense summary updated successfully for " + yearMonth);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating monthly expense summary", e);
        }
    }

    private String readAll(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String extractCookieValue(String cookies, String name) {
        for (String part : cookies.split(";")) {
            String t = part.trim();
            if (t.startsWith(name + "=")) {
                return t.substring(name.length() + 1);
            }
        }
        return null;
    }
    
    /**
     * Utility: Safely parse a date from a string with either "yyyy-MM-dd" or "MM/dd/yyyy" format.
     */
    private LocalDate tryParseDate(String ds) {
        if (ds == null || ds.trim().isEmpty())
            return null;
        try {
            return LocalDate.parse(ds, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            // Fallback to the alternate format
            try {
                return LocalDate.parse(ds, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } catch (Exception ex2) {
                LOGGER.warning("tryParseDate failed for: " + ds);
                return null;
            }
        }
    }
}
