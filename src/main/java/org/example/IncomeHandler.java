package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.cloud.firestore.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced handler for income-related API endpoints
 * Uses FirestoreService for more reliable Firebase operations
 *
 * GET: Fetches Firestore income documents, computes monthly totals, and returns a JSON that contains:
 *      - "incomes": an array of the original Firestore document objects,
 *      - "monthlyIncomes": an array (12 doubles) with each month's income total,
 *      - "yearlyIncome": the sum of the monthly incomes.
 *
 * POST: Adds a new income document to Firestore.
 */
public class IncomeHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(IncomeHandler.class.getName());
    private final FirestoreService firestoreService;
    private static final String INCOME_COLLECTION = "Income";
    private static final String SUMMARIES_COLLECTION = "Summaries";

    public IncomeHandler() {
        this.firestoreService = FirestoreService.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LOGGER.info("IncomeHandler invoked: " + exchange.getRequestMethod());
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            LOGGER.warning("401 - Missing idToken/localId in cookies");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        
        if (idToken == null || localId == null) {
            LOGGER.warning("401 - null tokens");
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        if (!firestoreService.isInitialized()) {
            LOGGER.severe("Firestore service not initialized");
            exchange.sendResponseHeaders(500, -1);
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleGetIncomes(exchange, localId);
        } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleAddIncome(exchange, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    /** 
     * GET: fetch docs from Firestore, parse them into monthly totals, and return incomes as well
     */
    private void handleGetIncomes(HttpExchange exchange, String localId) throws IOException {
        try {
            LOGGER.info("Fetching incomes for user: " + localId);
            
            // Get collection from Firestore
            CollectionReference incomesRef = firestoreService.getSubcollection(localId, INCOME_COLLECTION);
            
            if (incomesRef == null) {
                LOGGER.severe("Failed to get income collection reference");
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            
            // Query all income documents
            QuerySnapshot querySnapshot = incomesRef.get().get();
            
            // Convert the QuerySnapshot to a format compatible with the computeMonthlyYearly method
            JsonObject firestoreResponse = new JsonObject();
            JsonArray documents = new JsonArray();
            
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Map<String, Object> data = doc.getData();
                
                if (data != null) {
                    // Convert to Firestore API format
                    JsonObject docObject = new JsonObject();
                    String docPath = doc.getReference().getPath();
                    docObject.addProperty("name", docPath);
                    
                    JsonObject fields = new JsonObject();
                    
                    // Convert each field to Firestore API format
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        
                        JsonObject fieldValue = new JsonObject();
                        if (value instanceof String) {
                            fieldValue.addProperty("stringValue", (String)value);
                            fields.add(key, fieldValue);
                        } else if (value instanceof Double || value instanceof Float) {
                            fieldValue.addProperty("doubleValue", ((Number)value).doubleValue());
                            fields.add(key, fieldValue);
                        } else if (value instanceof Integer || value instanceof Long) {
                            fieldValue.addProperty("integerValue", ((Number)value).longValue());
                            fields.add(key, fieldValue);
                        } else if (value instanceof Boolean) {
                            fieldValue.addProperty("booleanValue", (Boolean)value);
                            fields.add(key, fieldValue);
                        } else if (value instanceof Date) {
                            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                                    .format((Date)value);
                            fieldValue.addProperty("timestampValue", dateStr);
                            fields.add(key, fieldValue);
                        }
                    }
                    
                    docObject.add("fields", fields);
                    documents.add(docObject);
                }
            }
            
            firestoreResponse.add("documents", documents);
            
            // Compute monthly and yearly totals and format response
            String calculationJson = computeMonthlyYearly(firestoreResponse.toString());
            
            // Send response
            byte[] respBytes = calculationJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, respBytes.length);
            exchange.getResponseBody().write(respBytes);
            exchange.getResponseBody().close();
            
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Error fetching incomes", e);
            exchange.sendResponseHeaders(500, -1);
        }
    }

    /**
     * POST: Add one income document to Firestore with the fields date, name, frequency, recurring, total.
     */
    private void handleAddIncome(HttpExchange exchange, String localId) throws IOException {
        try {
            // Read request body
            String requestBody = readAll(exchange.getRequestBody());
            LOGGER.info("Received income POST payload: " + requestBody);
            
            // Parse JSON data
            String dateVal = getJsonValue(requestBody, "date");
            String nameVal = getJsonValue(requestBody, "name");
            String freqVal = getJsonValue(requestBody, "frequency");
            String recurringVal = getJsonValue(requestBody, "recurring");
            String totalValStr = getJsonValue(requestBody, "total");
            
            double totalVal;
            try {
                totalVal = Double.parseDouble(totalValStr);
            } catch (Exception e) {
                LOGGER.warning("400 - invalid totalValue format");
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            
            boolean isRecurring = "true".equalsIgnoreCase(recurringVal);
            
            // Parse date to extract year and month for easier querying
            LocalDate parsedDate = tryParseDate(dateVal);
            String yearMonthField = "";
            String yearField = "";
            
            if (parsedDate != null) {
                int year = parsedDate.getYear();
                int month = parsedDate.getMonthValue();
                yearMonthField = year + "_" + (month < 10 ? "0" : "") + month;
                yearField = String.valueOf(year);
            } else {
                LOGGER.warning("Could not parse date: " + dateVal);
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            
            // Generate a unique transaction ID for better tracking
            String transactionId = "inc_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 1000);
            
            // Current timestamp for created/updated metadata
            long currentTimestamp = System.currentTimeMillis();
            
            // Prepare data for Firestore
            Map<String, Object> incomeData = new HashMap<>();
            incomeData.put("date", dateVal);
            incomeData.put("name", nameVal);
            incomeData.put("frequency", freqVal);
            incomeData.put("recurring", String.valueOf(isRecurring));
            incomeData.put("total", totalVal);
            incomeData.put("yearMonth", yearMonthField);
            incomeData.put("year", yearField);
            incomeData.put("transactionId", transactionId);
            incomeData.put("createdAt", currentTimestamp);
            incomeData.put("updatedAt", currentTimestamp);
            
            // Save to Firestore using our service
            String docId = UUID.randomUUID().toString();
            DocumentReference docRef = firestoreService.getSubcollectionDocument(localId, INCOME_COLLECTION, docId);
            
            if (docRef == null) {
                LOGGER.severe("Failed to get income document reference");
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            
            // Save the income document
            docRef.set(incomeData).get();
            LOGGER.info("Income saved successfully with ID: " + docId);
            
            // Update the monthly summary
            updateMonthlySummary(localId, parsedDate, totalVal, isRecurring, freqVal);
            
            // Prepare success response
            String success = "Income added successfully.";
            byte[] successBytes = success.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, successBytes.length);
            exchange.getResponseBody().write(successBytes);
            exchange.getResponseBody().close();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving income", e);
            exchange.sendResponseHeaders(500, -1);
        }
    }

    /**
     * Parse the Firestore JSON via Gson, compute monthly and yearly income totals,
     * and return a JSON string formatted as:
     *
     * {
     *    "incomes": [ ... ],         // The original list of income documents
     *    "monthlyIncomes": [12 doubles],
     *    "yearlyIncome": <double>
     * }
     */
    private String computeMonthlyYearly(String firestoreResponseBody) {
        JsonObject root;
        try {
            root = JsonParser.parseString(firestoreResponseBody).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warning("Invalid JSON from Firestore in computeMonthlyYearly");
            return "{\"incomes\":[],\"monthlyIncomes\":[0,0,0,0,0,0,0,0,0,0,0,0],\"yearlyIncome\":0}";
        }

        double[] monthly = new double[12];
        for (int i = 0; i < 12; i++) {
            monthly[i] = 0.0;
        }
        double yearlyTotal = 0.0;

        JsonArray docs = new JsonArray();
        if (root.has("documents")) {
            docs = root.getAsJsonArray("documents");
            // Process each document to compute monthly totals
            for (JsonElement docElem : docs) {
                JsonObject docObj = docElem.getAsJsonObject();
                if (!docObj.has("fields")) {
                    continue;
                }
                JsonObject fieldsObj = docObj.getAsJsonObject("fields");

                double total = getDoubleField(fieldsObj, "total");
                String dateStr = getStringField(fieldsObj, "date");
                String freqStr = getStringField(fieldsObj, "frequency");
                String recurringStr = getStringField(fieldsObj, "recurring");
                boolean isRecurring = "true".equalsIgnoreCase(recurringStr);

                LOGGER.fine("Doc parse => date=" + dateStr + ", total=" + total
                        + ", freq=" + freqStr + ", recurring=" + isRecurring);

                double[] docMonthly = new double[12];
                for (int i = 0; i < 12; i++) {
                    docMonthly[i] = 0.0;
                }

                if (isRecurring) {
                    // Distribute the annual income based on frequency
                    double annualValue;
                    switch ((freqStr != null) ? freqStr.toLowerCase() : "") {
                        case "weekly":
                            annualValue = total * 52.0;
                            break;
                        case "biweekly":
                            annualValue = total * 26.0;
                            break;
                        case "monthly":
                            annualValue = total * 12.0;
                            break;
                        case "yearly":
                            annualValue = total;
                            break;
                        default:
                            // For "once" or unrecognized frequency, assign it to its date if possible
                            LocalDate singleDate = tryParseDate(dateStr);
                            if (singleDate != null) {
                                int mo = singleDate.getMonthValue() - 1;
                                if (mo >= 0 && mo < 12) {
                                    docMonthly[mo] += total;
                                }
                            }
                            // Merge and continue
                            for (int m = 0; m < 12; m++) {
                                monthly[m] += docMonthly[m];
                            }
                            continue;
                    }
                    // Evenly distribute the annual value across the 12 months
                    double monthlyVal = annualValue / 12.0;
                    for (int j = 0; j < 12; j++) {
                        docMonthly[j] = monthlyVal;
                    }
                } else {
                    // Non-recurring income is applied only on its specific date
                    LocalDate parsedDate = tryParseDate(dateStr);
                    if (parsedDate != null) {
                        int mo = parsedDate.getMonthValue() - 1;
                        if (mo >= 0 && mo < 12) {
                            docMonthly[mo] += total;
                        }
                    }
                }
                // Accumulate this document's monthly amounts
                for (int i = 0; i < 12; i++) {
                    monthly[i] += docMonthly[i];
                }
            }
        }

        // Sum up the yearly total from monthly totals
        for (double mVal : monthly) {
            yearlyTotal += mVal;
        }

        // Build the output JSON object
        JsonObject out = new JsonObject();
        // Add the original income documents as an array in "incomes"
        out.add("incomes", docs);

        // Build the monthlyIncomes array
        JsonArray monthsArr = new JsonArray();
        for (int i = 0; i < 12; i++) {
            monthsArr.add(monthly[i]);
        }
        out.add("monthlyIncomes", monthsArr);

        // Add yearly income
        out.addProperty("yearlyIncome", yearlyTotal);

        return out.toString();
    }

    /**
     * Updates the monthly summary document in Firestore for faster dashboard reads.
     * Creates or updates a document in the Summaries collection with pre-aggregated income data.
     */
    private void updateMonthlySummary(String localId, LocalDate date, double amount,
                                     boolean isRecurring, String frequency) {
        if (date == null) {
            LOGGER.warning("Cannot update monthly summary: Invalid date");
            return;
        }
        
        try {
            int year = date.getYear();
            int month = date.getMonthValue();
            String yearMonth = year + "_" + (month < 10 ? "0" : "") + month;
            String summaryId = "income_" + yearMonth;
            
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
            
            if (documentExists) {
                Map<String, Object> data = summarySnapshot.getData();
                
                if (data != null) {
                    // Extract existing data
                    if (data.get("totalIncome") instanceof Number) {
                        existingTotal = ((Number) data.get("totalIncome")).doubleValue();
                    }
                    
                    if (data.get("incomeCount") instanceof Number) {
                        existingCount = ((Number) data.get("incomeCount")).intValue();
                    }
                }
            }
            
            // Calculate new values
            double newTotal = existingTotal + amount;
            int newCount = existingCount + 1;
            
            // Create new summary data
            Map<String, Object> summaryData = new HashMap<>();
            summaryData.put("totalIncome", newTotal);
            summaryData.put("incomeCount", newCount);
            summaryData.put("yearMonth", yearMonth);
            summaryData.put("year", String.valueOf(year));
            summaryData.put("month", month);
            summaryData.put("lastUpdated", System.currentTimeMillis());
            
            if (!documentExists) {
                summaryData.put("createdAt", System.currentTimeMillis());
            }
            
            // Save or update the summary document
            summaryRef.set(summaryData).get();
            LOGGER.info("Monthly income summary updated successfully for " + yearMonth);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating monthly income summary", e);
        }
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

    /**
     * Utility: Retrieve the "stringValue" from a field in fieldsObj, if present.
     */
    private String getStringField(JsonObject fieldsObj, String fieldName) {
        if (!fieldsObj.has(fieldName))
            return null;
        JsonObject sub = fieldsObj.getAsJsonObject(fieldName);
        if (sub == null)
            return null;
        if (sub.has("stringValue")) {
            return sub.get("stringValue").getAsString();
        }
        return null;
    }

    /**
     * Utility: Retrieve a numeric value from a field as a double.
     */
    private double getDoubleField(JsonObject fieldsObj, String fieldName) {
        if (!fieldsObj.has(fieldName))
            return 0.0;
        JsonObject sub = fieldsObj.getAsJsonObject(fieldName);
        if (sub == null)
            return 0.0;
        if (sub.has("doubleValue")) {
            try {
                return sub.get("doubleValue").getAsDouble();
            } catch (Exception e) {
                return 0.0;
            }
        }
        if (sub.has("integerValue")) {
            try {
                return sub.get("integerValue").getAsDouble();
            } catch (Exception e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    /**
     * Utility: Read all content from an input stream
     */
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

    /**
     * Utility: Extract a cookie value from the cookies string.
     */
    private static String extractCookieValue(String cookies, String name) {
        for (String part : cookies.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring((name + "=").length());
            }
        }
        return null;
    }
    
    /**
     * Utility: Simple extraction of a JSON key's value from a JSON string using regex.
     * This supports both quoted strings and numeric/boolean values.
     */
    private static String getJsonValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|([\\d.Ee+\\-]+|true|false|null))";
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                if (m.group(1) != null) {
                    return m.group(1);
                } else if (m.group(2) != null) {
                    return m.group(2);
                }
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
        return "";
    }
}
