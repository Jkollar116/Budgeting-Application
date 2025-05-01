package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.google.gson.*;

/**
 * IncomeHandler
 *
 * GET: Fetches Firestore income documents, computes monthly totals, and returns a JSON that contains:
 *      - "incomes": an array of the original Firestore document objects,
 *      - "monthlyIncomes": an array (12 doubles) with each month’s income total,
 *      - "yearlyIncome": the sum of the monthly incomes.
 *
 * POST: Adds a new income document to Firestore.
 */
public class IncomeHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("IncomeHandler invoked: " + exchange.getRequestMethod());

        // Grab cookies to see if we have idToken + localId
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        System.out.println("IncomeHandler cookies: " + cookies);
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            System.out.println("401 - Missing idToken/localId in cookies");
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        System.out.println("Extracted idToken: " + idToken);
        System.out.println("Extracted localId: " + localId);
        if (idToken == null || localId == null) {
            System.out.println("401 - null tokens");
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleGetIncomes(exchange, idToken, localId);
        } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleAddIncome(exchange, idToken, localId);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    /** GET: fetch docs from Firestore, parse them into monthly totals, and return incomes as well */
    private void handleGetIncomes(HttpExchange exchange, String idToken, String localId) throws IOException {
        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Income";
        System.out.println("IncomeHandler GET URL: " + firestoreUrl);

        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);

        int responseCode = conn.getResponseCode();
        System.out.println("IncomeHandler GET response code: " + responseCode);
        if (responseCode != 200) {
            exchange.sendResponseHeaders(responseCode, -1);
            return;
        }

        // Read raw JSON from Firestore
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line);
        }
        in.close();
        String responseBody = sb.toString();
        System.out.println("IncomeHandler raw GET response:\n" + responseBody);

        // Build JSON response that includes both the incomes and calculated values
        String calculationJson = computeMonthlyYearly(responseBody);

        byte[] respBytes = calculationJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, respBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(respBytes);
        }
    }

    /**
     * POST: Add one income document to Firestore with the fields date, name, frequency, recurring, total.
     */
    private void handleAddIncome(HttpExchange exchange, String idToken, String localId) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
        }
        String requestBody = body.toString();
        System.out.println("IncomeHandler requestBody: " + requestBody);

        // extract JSON fields from the request (using a simple regex helper)
        String dateVal       = getJsonValue(requestBody, "date");
        String nameVal       = getJsonValue(requestBody, "name");
        String freqVal       = getJsonValue(requestBody, "frequency");
        String recurringVal  = getJsonValue(requestBody, "recurring");
        String totalValStr   = getJsonValue(requestBody, "total");

        double totalVal;
        try {
            totalVal = Double.parseDouble(totalValStr);
        } catch(Exception e) {
            System.out.println("400 - invalid totalValue format");
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        boolean isRecurring = "true".equalsIgnoreCase(recurringVal);

        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Income";
        System.out.println("Firestore URL (POST): " + firestoreUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(firestoreUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        conn.setDoOutput(true);

        // Parse date to extract year and month for easier querying
        LocalDate parsedDate = tryParseDate(dateVal);
        String yearMonthField = "";
        String yearField = "";
        
        if (parsedDate != null) {
            int year = parsedDate.getYear();
            int month = parsedDate.getMonthValue();
            yearMonthField = year + "_" + (month < 10 ? "0" : "") + month;
            yearField = String.valueOf(year);
        }
        
        // Generate a unique transaction ID for better tracking
        String transactionId = "inc_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 1000);
        
        // Current timestamp for created/updated metadata
        long currentTimestamp = System.currentTimeMillis();
        
        // Construct JSON to store as doubleValue for total, with enhanced metadata
        String jsonToFirestore = "{\"fields\":{"
                + "\"date\":{\"stringValue\":\"" + dateVal + "\"},"
                + "\"name\":{\"stringValue\":\"" + nameVal + "\"},"
                + "\"frequency\":{\"stringValue\":\"" + freqVal + "\"},"
                + "\"recurring\":{\"stringValue\":\"" + isRecurring + "\"},"
                + "\"total\":{\"doubleValue\":" + totalVal + "},"
                + "\"yearMonth\":{\"stringValue\":\"" + yearMonthField + "\"},"
                + "\"year\":{\"stringValue\":\"" + yearField + "\"},"
                + "\"transactionId\":{\"stringValue\":\"" + transactionId + "\"},"
                + "\"createdAt\":{\"integerValue\":" + currentTimestamp + "},"
                + "\"updatedAt\":{\"integerValue\":" + currentTimestamp + "}"
                + "}}";
        System.out.println("jsonToFirestore: " + jsonToFirestore);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonToFirestore.getBytes(StandardCharsets.UTF_8));
        }
        int responseCodePost = conn.getResponseCode();
        System.out.println("Firestore POST response code: " + responseCodePost);

        if (responseCodePost == 200 || responseCodePost == 201) {
            // After successfully adding the income, update the monthly summary
            updateMonthlySummary(idToken, localId, parsedDate, totalVal, isRecurring, freqVal);
            
            String success = "Income added successfully.";
            exchange.sendResponseHeaders(200, success.length());
            try (OutputStream respOs = exchange.getResponseBody()) {
                respOs.write(success.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            // Log error body if available
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                BufferedReader errBr = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                StringBuilder errBody = new StringBuilder();
                String ln;
                while ((ln = errBr.readLine()) != null) {
                    errBody.append(ln);
                }
                errBr.close();
                System.out.println("Firestore Income error body: " + errBody);
            }
            exchange.sendResponseHeaders(responseCodePost, -1);
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
            System.out.println("Invalid JSON from Firestore in computeMonthlyYearly");
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

                System.out.println("Doc parse => date=" + dateStr + ", total=" + total
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
                System.out.println("tryParseDate failed for: " + ds);
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
     * Utility: Extract a cookie value from the cookies string.
     */
    private static String extractCookieValue(String cookies, String name) {
        String[] parts = cookies.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring((name + "=").length());
            }
        }
        return null;
    }

    /**
     * Updates the monthly summary document in Firestore for faster dashboard reads.
     * Creates or updates a document in the Summaries collection with pre-aggregated income data.
     */
    private void updateMonthlySummary(String idToken, String localId, LocalDate date, double amount,
                                      boolean isRecurring, String frequency) {
        if (date == null) {
            System.out.println("Cannot update monthly summary: Invalid date");
            return;
        }
        
        try {
            int year = date.getYear();
            int month = date.getMonthValue();
            String yearMonth = year + "_" + (month < 10 ? "0" : "") + month;
            
            // Firestore URL for the monthly summary document
            String summaryUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Summaries/income_" + yearMonth;
            
            // First check if the document exists
            URL getUrl = new URL(summaryUrl);
            HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
            getConn.setRequestMethod("GET");
            getConn.setRequestProperty("Authorization", "Bearer " + idToken);
            
            boolean documentExists = false;
            double existingTotal = 0.0;
            int existingCount = 0;
            
            int getResponseCode = getConn.getResponseCode();
            if (getResponseCode == 200) {
                // Document exists, parse the current data
                documentExists = true;
                BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                
                String response = sb.toString();
                JsonObject root = JsonParser.parseString(response).getAsJsonObject();
                if (root.has("fields")) {
                    JsonObject fields = root.getAsJsonObject("fields");
                    existingTotal = getDoubleField(fields, "totalIncome");
                    existingCount = (int) getDoubleField(fields, "incomeCount");
                }
            }
            
            // Calculate new total and count
            double newTotal = existingTotal + amount;
            int newCount = existingCount + 1;
            
            // Current timestamp for metadata
            long currentTimestamp = System.currentTimeMillis();
            
            // Build JSON for update/create
            String jsonBody;
            if (documentExists) {
                // PATCH request for existing document
                jsonBody = "{\"fields\":{"
                        + "\"totalIncome\":{\"doubleValue\":" + newTotal + "},"
                        + "\"incomeCount\":{\"integerValue\":" + newCount + "},"
                        + "\"lastUpdated\":{\"integerValue\":" + currentTimestamp + "}"
                        + "}}";
            } else {
                // Create new document with all fields
                jsonBody = "{\"fields\":{"
                        + "\"totalIncome\":{\"doubleValue\":" + amount + "},"
                        + "\"incomeCount\":{\"integerValue\":1},"
                        + "\"yearMonth\":{\"stringValue\":\"" + yearMonth + "\"},"
                        + "\"year\":{\"stringValue\":\"" + year + "\"},"
                        + "\"month\":{\"integerValue\":" + month + "},"
                        + "\"createdAt\":{\"integerValue\":" + currentTimestamp + "},"
                        + "\"lastUpdated\":{\"integerValue\":" + currentTimestamp + "}"
                        + "}}";
            }
            
            // Send the request to update or create the summary document
            URL updateUrl = new URL(summaryUrl);
            HttpURLConnection updateConn = (HttpURLConnection) updateUrl.openConnection();
            updateConn.setRequestMethod(documentExists ? "PATCH" : "PUT");
            updateConn.setRequestProperty("Content-Type", "application/json");
            updateConn.setRequestProperty("Authorization", "Bearer " + idToken);
            updateConn.setDoOutput(true);
            
            try (OutputStream os = updateConn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            
            int updateResponseCode = updateConn.getResponseCode();
            if (updateResponseCode == 200 || updateResponseCode == 201) {
                System.out.println("Monthly income summary updated successfully for " + yearMonth);
            } else {
                System.out.println("Failed to update monthly income summary. Response code: " + updateResponseCode);
                
                // Log error response if available
                try (InputStream errorStream = updateConn.getErrorStream()) {
                    if (errorStream != null) {
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
                        StringBuilder errorSb = new StringBuilder();
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            errorSb.append(errorLine);
                        }
                        System.out.println("Error response: " + errorSb.toString());
                    }
                } catch (Exception e) {
                    System.out.println("Error reading error stream: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.out.println("Exception in updateMonthlySummary: " + e.getMessage());
            e.printStackTrace();
        }
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
