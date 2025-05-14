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

        // Ensure the arrays have 12 entries (one for each month)
        if (monthlyExpenses.length != 12) {
            double[] newMonthlyExpenses = new double[12];
            for (int i = 0; i < Math.min(12, monthlyExpenses.length); i++) {
                newMonthlyExpenses[i] = monthlyExpenses[i];
            }
            monthlyExpenses = newMonthlyExpenses;
        }
        
        if (monthlyIncomes.length != 12) {
            double[] newMonthlyIncomes = new double[12];
            for (int i = 0; i < Math.min(12, monthlyIncomes.length); i++) {
                newMonthlyIncomes[i] = monthlyIncomes[i];
            }
            monthlyIncomes = newMonthlyIncomes;
        }

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
        fieldsObj.put("totalIncome",  new JSONObject().put("integerValue", String.valueOf((int)Math.round(currentMonthIncomes))));

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

    // Implementation of computing net worth from assets and liabilities
    private int computeNetWorth(String localId) {
        try {
            // Get assets total
            int assetsTotal = 0;
            CollectionReference assetsRef = db
                .collection("Users")
                .document(localId)
                .collection("Assets");
            ApiFuture<QuerySnapshot> assetsFuture = assetsRef.get();
            List<QueryDocumentSnapshot> assetsDocs = assetsFuture.get().getDocuments();
            for (QueryDocumentSnapshot d : assetsDocs) {
                Map<String, Object> fields = d.getData();
                if (fields.containsKey("value") && fields.get("value") instanceof Map) {
                    Map<?, ?> valueField = (Map<?, ?>)fields.get("value");
                    if (valueField.containsKey("integerValue")) {
                        assetsTotal += Integer.parseInt((String)valueField.get("integerValue"));
                    } else if (valueField.containsKey("doubleValue")) {
                        assetsTotal += (int)Math.round(Double.parseDouble(valueField.get("doubleValue").toString()));
                    }
                }
            }
            
            // Get liabilities total
            int liabilitiesTotal = 0;
            CollectionReference liabilitiesRef = db
                .collection("Users")
                .document(localId)
                .collection("Liabilities");
            ApiFuture<QuerySnapshot> liabilitiesFuture = liabilitiesRef.get();
            List<QueryDocumentSnapshot> liabilitiesDocs = liabilitiesFuture.get().getDocuments();
            for (QueryDocumentSnapshot d : liabilitiesDocs) {
                Map<String, Object> fields = d.getData();
                if (fields.containsKey("value") && fields.get("value") instanceof Map) {
                    Map<?, ?> valueField = (Map<?, ?>)fields.get("value");
                    if (valueField.containsKey("integerValue")) {
                        liabilitiesTotal += Integer.parseInt((String)valueField.get("integerValue"));
                    } else if (valueField.containsKey("doubleValue")) {
                        liabilitiesTotal += (int)Math.round(Double.parseDouble(valueField.get("doubleValue").toString()));
                    }
                }
            }
            
            // Net worth is assets minus liabilities
            return assetsTotal - liabilitiesTotal;
        } catch (Exception e) {
            System.out.println("Error computing net worth: " + e.getMessage());
            // Return a default value in case of error
            return 0;
        }
    }
    
    // Implementation of computing total income across all income documents
    private int computeCumulativeIncome(String localId) {
        try {
            int totalIncome = 0;
            CollectionReference incomeRef = db
                .collection("Users")
                .document(localId)
                .collection("Income");
            ApiFuture<QuerySnapshot> incomeFuture = incomeRef.get();
            List<QueryDocumentSnapshot> incomeDocs = incomeFuture.get().getDocuments();
            
            for (QueryDocumentSnapshot d : incomeDocs) {
                Map<String, Object> fields = d.getData();
                if (fields.containsKey("total") && fields.get("total") instanceof Map) {
                    Map<?, ?> totalField = (Map<?, ?>)fields.get("total");
                    if (totalField.containsKey("integerValue")) {
                        totalIncome += Integer.parseInt((String)totalField.get("integerValue"));
                    } else if (totalField.containsKey("doubleValue")) {
                        totalIncome += (int)Math.round(Double.parseDouble(totalField.get("doubleValue").toString()));
                    }
                }
            }
            
            return totalIncome;
        } catch (Exception e) {
            System.out.println("Error computing cumulative income: " + e.getMessage());
            // Return a default value in case of error
            return 0;
        }
    }
    
    // Implementation of counting bills due soon
    private int computeBillsDue(String localId) {
        try {
            int billsDue = 0;
            CollectionReference billsRef = db
                .collection("Users")
                .document(localId)
                .collection("Bills");
                
            LocalDate today = LocalDate.now();
            LocalDate nextWeek = today.plusDays(7); // Bills due within a week
            
            ApiFuture<QuerySnapshot> billsFuture = billsRef.get();
            List<QueryDocumentSnapshot> billsDocs = billsFuture.get().getDocuments();
            
            for (QueryDocumentSnapshot d : billsDocs) {
                Map<String, Object> fields = d.getData();
                if (fields.containsKey("dueDate") && fields.get("dueDate") instanceof Map) {
                    Map<?, ?> dueDateField = (Map<?, ?>)fields.get("dueDate");
                    if (dueDateField.containsKey("stringValue")) {
                        String dueDateStr = (String)dueDateField.get("stringValue");
                        LocalDate dueDate = parseAnyDate(dueDateStr);
                        
                        // Count if due date is between today and next week
                        if (dueDate != null && 
                            (dueDate.isEqual(today) || dueDate.isAfter(today)) && 
                            dueDate.isBefore(nextWeek)) {
                            billsDue++;
                        }
                    }
                }
            }
            
            return billsDue;
        } catch (Exception e) {
            System.out.println("Error computing bills due: " + e.getMessage());
            // Return a default value in case of error
            return 0;
        }
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
