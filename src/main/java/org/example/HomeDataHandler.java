package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class HomeDataHandler implements HttpHandler {
    private final Firestore db;

    public HomeDataHandler() {
        // Initialize Firestore once at startup
        FirestoreService.initialize();
        this.db = FirestoreService.getInstance().getDb();
        System.out.println("HomeDataHandler: Firestore initialized: " + (db != null));
    }

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

        String responseJson;
        try {
            responseJson = buildResponseJson(localId);
            byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            System.out.println("HomeDataHandler: Response sent successfully.");
        } catch (Exception e) {
            System.out.println("HomeDataHandler: Error building response: " + e);
            exchange.sendResponseHeaders(500, -1);
        }
    }

    private String buildResponseJson(String localId) throws Exception {
        JSONObject doc = new JSONObject();
        JSONObject fieldsObj = new JSONObject();
        doc.put("fields", fieldsObj);

        // Fetch monthly expenses and incomes from Firestore
        double[] monthlyExpenses = fetchMonthly("Expenses", localId);
        double[] monthlyIncomes  = fetchMonthly("Income", localId);

        // Calculate current month totals
        LocalDate today = LocalDate.now();
        int idx = today.getMonthValue() - 1;
        double currentMonthExpenses = (idx >= 0 && idx < 12) ? monthlyExpenses[idx] : 0;
        double currentMonthIncomes  = (idx >= 0 && idx < 12) ? monthlyIncomes[idx]  : 0;

        // Build arrays
        JSONArray expArr = new JSONArray();
        JSONArray incArr = new JSONArray();
        for (int i = 0; i < 12; i++) {
            expArr.put(new JSONObject().put("integerValue", String.valueOf((int)Math.round(monthlyExpenses[i]))));
            incArr.put(new JSONObject().put("integerValue", String.valueOf((int)Math.round(monthlyIncomes[i]))));
        }
        fieldsObj.put("monthlyExpenses", new JSONObject().put("arrayValue", new JSONObject().put("values", expArr)));
        fieldsObj.put("monthlyIncomes",  new JSONObject().put("arrayValue", new JSONObject().put("values", incArr)));

        // Totals
        fieldsObj.put("totalExpenses", new JSONObject().put("integerValue", String.valueOf((int)Math.round(currentMonthExpenses))));
        fieldsObj.put("totalIncomes",  new JSONObject().put("integerValue", String.valueOf((int)Math.round(currentMonthIncomes))));

        // Net worth, income, bills – you can compute similarly by querying other collections or documents
        int netWorth    = computeNetWorth(localId);
        int totalIncome = computeCumulativeIncome(localId);
        int billsDue    = computeBillsDue(localId);

        fieldsObj.put("netWorth", new JSONObject().put("integerValue", String.valueOf(netWorth)));
        fieldsObj.put("totalIncome", new JSONObject().put("integerValue", String.valueOf(totalIncome)));
        fieldsObj.put("billsDue", new JSONObject().put("integerValue", String.valueOf(billsDue)));

        return doc.toString();
    }

    private double[] fetchMonthly(String subCollection, String localId) throws Exception {
        double[] monthly = new double[12];
        CollectionReference colRef = db
                .collection("Users")
                .document(localId)
                .collection(subCollection);
        ApiFuture<QuerySnapshot> future = colRef.get();
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        for (QueryDocumentSnapshot d : docs) {
            Map<String, Object> fields = d.getData();
            String dateStr = (String)((Map<?,?>)fields.get("date")).get("stringValue");
            double total   = Double.parseDouble((String)((Map<?,?>)fields.get("total")).get("integerValue"));
            LocalDate date = parseAnyDate(dateStr);
            if (date != null) {
                int mo = date.getMonthValue() - 1;
                if (mo >= 0 && mo < 12) {
                    monthly[mo] += total;
                }
            }
        }
        return monthly;
    }

    // Placeholder implementations; replace with your actual Firestore queries if stored elsewhere
    private int computeNetWorth(String localId) {
        // e.g. sum of cash + investments collections
        return 0;
    }
    private int computeCumulativeIncome(String localId) {
        // e.g. sum over all Income documents
        return 0;
    }
    private int computeBillsDue(String localId) {
        // e.g. count of bills where dueDate <= today
        return 0;
    }

    private String extractCookieValue(String cookies, String name) {
        for (String part : cookies.split(";")) {
            String t = part.trim();
            if (t.startsWith(name + "=")) {
                return t.substring((name + "=").length());
            }
        }
        return null;
    }

    private LocalDate parseAnyDate(String ds) {
        if (ds == null || ds.isEmpty()) return null;
        try {
            return LocalDate.parse(ds, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            try {
                return LocalDate.parse(ds, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
