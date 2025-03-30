package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class HomeDataHandler implements HttpHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("HomeDataHandler: handle() invoked.");

        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        System.out.println("Cookies: " + cookies);
        if (cookies == null || !cookies.contains("idToken=") || !cookies.contains("localId=")) {
            System.out.println("No idToken or localId in cookies; sending 401");
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        String idToken = extractCookieValue(cookies, "idToken");
        String localId = extractCookieValue(cookies, "localId");
        System.out.println("Extracted idToken: " + idToken);
        System.out.println("Extracted localId: " + localId);

        if (idToken == null || localId == null) {
            System.out.println("Either idToken or localId is null; sending 401");
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                + localId + "/Expenses";
        System.out.println("Fetching from Firestore URL: " + firestoreUrl);

        URL url = new URL(firestoreUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        int responseCode = conn.getResponseCode();
        System.out.println("Firestore GET response code: " + responseCode);

        if (responseCode != 200) {
            System.out.println("Non-200 from Firestore, forwarding status " + responseCode);
            exchange.sendResponseHeaders(responseCode, -1);
            return;
        }

        // Read the raw JSON from Firestore
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line);
        }
        in.close();
        String responseBody = sb.toString();
        System.out.println("Firestore response body:\n" + responseBody);

        // Initialize placeholders
        int netWorth = 0;
        String netWorthBreakdown = "{\"cash\":0,\"equity\":0,\"investments\":0}";
        int totalIncome = 0;
        String totalIncomeBreakdown = "{\"salary\":0,\"bonus\":0,\"other\":0}";
        int billsDue = 0;

        double[] monthlyExpenses = new double[12];
        for (int i = 0; i < 12; i++) {
            monthlyExpenses[i] = 0;
        }

        double currentMonthTotal = 0;

        // Parse JSON with Jackson
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode documents = root.get("documents");

        if (documents != null && documents.isArray()) {
            int docIndex = 0;
            for (JsonNode doc : documents) {
                docIndex++;
                System.out.println("\n==== Processing expense doc #" + docIndex + " ====");
                JsonNode fields = doc.get("fields");
                if (fields == null) {
                    System.out.println("No 'fields' node here; skipping");
                    continue;
                }

                // Pull out the "date" -> "stringValue"
                String dateStr = safeGetString(fields, "date");
                System.out.println("dateStr = " + dateStr);

                // Pull out the "total" -> "doubleValue"
                double expense = safeGetDouble(fields, "total");
                System.out.println("expense = " + expense);

                // Debug other fields if you like
                String category = safeGetString(fields, "category");
                String name = safeGetString(fields, "name");
                System.out.println("category = " + category);
                System.out.println("name     = " + name);

                if (dateStr != null && !dateStr.isEmpty()) {
                    LocalDate parsedDate = parseDateFlexible(dateStr);
                    if (parsedDate == null) {
                        System.out.println("Could NOT parse date: " + dateStr + " in known formats");
                    } else {
                        int monthIndex = parsedDate.getMonthValue() - 1;
                        if (monthIndex < 0 || monthIndex > 11) {
                            System.out.println("Invalid month index " + monthIndex + " from date: " + parsedDate);
                        } else {
                            monthlyExpenses[monthIndex] += expense;
                            System.out.println("Added " + expense + " to monthlyExpenses[" + monthIndex + "]");
                        }

                        // Optionally track "current month" (this year + month).
                        LocalDate today = LocalDate.now();
                        if (parsedDate.getYear() == today.getYear()
                                && parsedDate.getMonthValue() == today.getMonthValue()) {
                            currentMonthTotal += expense;
                            System.out.println("Also added to currentMonthTotal => " + currentMonthTotal);
                        }
                    }
                } else {
                    System.out.println("No dateStr found; skipping");
                }
            }
        } else {
            System.out.println("No documents array found or not an array; maybe 0 expenses total.");
        }

        System.out.println("\nFinal monthlyExpenses array:");
        for (int i = 0; i < 12; i++) {
            System.out.println("Month " + (i + 1) + ": " + monthlyExpenses[i]);
        }
        System.out.println("Final currentMonthTotal: " + currentMonthTotal);

        // Build JSON response
        StringBuilder json = new StringBuilder();
        json.append("{\"fields\":{");
        json.append("\"netWorth\":{\"integerValue\":\"").append(netWorth).append("\"},");
        json.append("\"netWorthBreakdown\":{\"stringValue\":\"").append(netWorthBreakdown.replace("\"", "\\\"")).append("\"},");
        json.append("\"totalIncome\":{\"integerValue\":\"").append(totalIncome).append("\"},");
        json.append("\"totalIncomeBreakdown\":{\"stringValue\":\"").append(totalIncomeBreakdown.replace("\"", "\\\"")).append("\"},");
        json.append("\"totalExpenses\":{\"integerValue\":\"").append((int) currentMonthTotal).append("\"},");
        json.append("\"billsDue\":{\"integerValue\":\"").append(billsDue).append("\"},");
        json.append("\"monthlyExpenses\":{\"arrayValue\":{\"values\":[");
        for (int i = 0; i < 12; i++) {
            json.append("{\"integerValue\":\"").append((int) monthlyExpenses[i]).append("\"}");
            if (i < 11) {
                json.append(",");
            }
        }
        json.append("]}}");
        json.append("}}");

        String jsonStr = json.toString();
        System.out.println("Final JSON response:\n" + jsonStr);

        byte[] responseBytes = jsonStr.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
        System.out.println("HomeDataHandler: Response sent successfully.");
    }

    private LocalDate parseDateFlexible(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } catch (Exception e) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Safely retrieves a stringValue from fields[fieldName].stringValue
     * Returns null if not present
     */
    private String safeGetString(JsonNode fields, String fieldName) {
        JsonNode fieldNode = fields.get(fieldName);
        if (fieldNode == null) return null;
        JsonNode strVal = fieldNode.get("stringValue");
        if (strVal == null || strVal.isNull()) return null;
        return strVal.asText();
    }

    /**
     * Safely retrieves a doubleValue from fields[fieldName].doubleValue
     * Returns 0.0 if not present or parse fails
     */
    private double safeGetDouble(JsonNode fields, String fieldName) {
        JsonNode fieldNode = fields.get(fieldName);
        if (fieldNode == null) return 0.0;
        JsonNode dblVal = fieldNode.get("doubleValue");
        if (dblVal == null || dblVal.isNull()) return 0.0;
        try {
            return dblVal.asDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String extractCookieValue(String cookies, String name) {
        String[] parts = cookies.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring((name + "=").length());
            }
        }
        return null;
    }
}
