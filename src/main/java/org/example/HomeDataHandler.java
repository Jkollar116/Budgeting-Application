package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.json.JSONObject;
import org.json.JSONArray;

public class HomeDataHandler implements HttpHandler {
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
        String responseJson = buildResponseJson(localId, idToken);
        byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
        System.out.println("HomeDataHandler: Response sent successfully.");
    }
    private String buildResponseJson(String localId, String idToken) {
        try {
            JSONObject doc = new JSONObject();
            doc.put("fields", new JSONObject());
            JSONObject fieldsObj = doc.getJSONObject("fields");
            int netWorth = 0;
            fieldsObj.put("netWorth", new JSONObject().put("integerValue", String.valueOf(netWorth)));
            fieldsObj.put("netWorthBreakdown", new JSONObject().put("stringValue", "{\"cash\":0,\"equity\":0,\"investments\":0}"));
            int totalIncome = 0;
            fieldsObj.put("totalIncome", new JSONObject().put("integerValue", String.valueOf(totalIncome)));
            fieldsObj.put("totalIncomeBreakdown", new JSONObject().put("stringValue", "{\"salary\":0,\"bonus\":0,\"other\":0}"));
            int billsDue = 0;
            fieldsObj.put("billsDue", new JSONObject().put("integerValue", String.valueOf(billsDue)));
            double currentMonthTotalExpenses = 0.0;
            double currentMonthTotalIncomes = 0.0;
            double[] monthlyExpenses = fetchExpensesMonthly(localId, idToken);
            double[] monthlyIncomes = fetchIncomesMonthly(localId, idToken);
            LocalDate today = LocalDate.now();
            int thisMonthIndex = today.getMonthValue() - 1;
            if (thisMonthIndex >= 0 && thisMonthIndex < 12) {
                currentMonthTotalExpenses = monthlyExpenses[thisMonthIndex];
                currentMonthTotalIncomes = monthlyIncomes[thisMonthIndex];
            }
            JSONArray expenseArr = new JSONArray();
            for(int i=0;i<12;i++){
                expenseArr.put(new JSONObject().put("integerValue", String.valueOf((int) monthlyExpenses[i])));
            }
            fieldsObj.put("monthlyExpenses", new JSONObject().put("arrayValue", new JSONObject().put("values", expenseArr)));
            JSONArray incomeArr = new JSONArray();
            for(int i=0;i<12;i++){
                incomeArr.put(new JSONObject().put("integerValue", String.valueOf((int) monthlyIncomes[i])));
            }
            fieldsObj.put("monthlyIncomes", new JSONObject().put("arrayValue", new JSONObject().put("values", incomeArr)));
            int totalExpThisMonth = (int) Math.round(currentMonthTotalExpenses);
            fieldsObj.put("totalExpenses", new JSONObject().put("integerValue", String.valueOf(totalExpThisMonth)));
            fieldsObj.put("totalIncomes", new JSONObject().put("integerValue", String.valueOf((int)Math.round(currentMonthTotalIncomes))));
            return doc.toString();
        } catch(Exception e) {
            System.out.println("Error building HomeData JSON: " + e);
            return "{\"fields\":{}}";
        }
    }
    private double[] fetchExpensesMonthly(String localId, String idToken) {
        System.out.println("Fetching expenses for localId: " + localId);
        double[] monthly = new double[12];
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Expenses";
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            int code = conn.getResponseCode();
            System.out.println("Expenses GET response code: " + code);
            if (code != 200) {
                return monthly;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = in.readLine()) != null) sb.append(ln);
            in.close();
            String resp = sb.toString();
            System.out.println("Expenses raw GET response:\n" + resp);
            monthly = parseExpenseDocs(resp);
        } catch(Exception e){
            System.out.println("Error fetching expenses: " + e);
        }
        return monthly;
    }
    private double[] parseExpenseDocs(String body) {
        double[] monthly = new double[12];
        try {
            JSONObject root = new JSONObject(body);
            if (!root.has("documents")) {
                return monthly;
            }
            JSONArray docs = root.getJSONArray("documents");
            for (int i=0; i<docs.length(); i++){
                JSONObject doc = docs.getJSONObject(i);
                JSONObject fields = doc.optJSONObject("fields");
                if (fields == null) continue;
                String dateStr = getStringValue(fields, "date");
                double total = getDoubleValue(fields, "total");
                LocalDate parsed = parseAnyDate(dateStr);
                if(parsed!=null){
                    int mo = parsed.getMonthValue()-1;
                    if(mo>=0 && mo<12){
                        monthly[mo]+= total;
                    }
                }
            }
        } catch(Exception e){
            System.out.println("parseExpenseDocs error: " + e);
        }
        return monthly;
    }
    private double[] fetchIncomesMonthly(String localId, String idToken) {
        System.out.println("Fetching incomes for localId: " + localId);
        double[] monthly = new double[12];
        try {
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Income";
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            int code = conn.getResponseCode();
            System.out.println("Incomes GET response code: " + code);
            if (code != 200) {
                return monthly;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = in.readLine()) != null) sb.append(ln);
            in.close();
            String resp = sb.toString();
            System.out.println("Incomes raw GET response:\n" + resp);
            monthly = parseIncomeDocs(resp);
        } catch(Exception e){
            System.out.println("Error fetching incomes: " + e);
        }
        return monthly;
    }
    private double[] parseIncomeDocs(String body) {
        double[] monthly = new double[12];
        try {
            JSONObject root = new JSONObject(body);
            if (!root.has("documents")) {
                return monthly;
            }
            JSONArray docs = root.getJSONArray("documents");
            for (int i=0; i<docs.length(); i++){
                JSONObject doc = docs.getJSONObject(i);
                JSONObject fields = doc.optJSONObject("fields");
                if (fields == null) continue;
                String dateStr = getStringValue(fields, "date");
                double total = getDoubleValue(fields, "total");
                boolean isRecurring = "true".equalsIgnoreCase(getStringValue(fields,"recurring"));
                String freq = getStringValue(fields,"frequency");
                if (isRecurring) {
                    double annualValue=0.0;
                    switch(freq.toLowerCase()){
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
                            LocalDate singleDate = parseAnyDate(dateStr);
                            if(singleDate!=null){
                                int m=singleDate.getMonthValue()-1;
                                if(m>=0 && m<12){
                                    monthly[m]+= total;
                                }
                            }
                            continue;
                    }
                    double monthlyVal = annualValue/12.0;
                    for(int j=0;j<12;j++){
                        monthly[j]+= monthlyVal;
                    }
                } else {
                    LocalDate parsed = parseAnyDate(dateStr);
                    if(parsed!=null){
                        int mo = parsed.getMonthValue()-1;
                        if(mo>=0 && mo<12){
                            monthly[mo]+= total;
                        }
                    }
                }
            }
        } catch(Exception e){
            System.out.println("parseIncomeDocs error: " + e);
        }
        return monthly;
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
    private LocalDate parseAnyDate(String ds){
        if(ds==null||ds.isEmpty()) return null;
        try{
            return LocalDate.parse(ds, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch(Exception e){
            try{
                return LocalDate.parse(ds, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } catch(Exception e2){
                return null;
            }
        }
    }
    private String getStringValue(JSONObject fields, String fieldName){
        if(!fields.has(fieldName)) return "";
        JSONObject node = fields.getJSONObject(fieldName);
        if(node.has("stringValue")){
            return node.getString("stringValue");
        }
        return "";
    }
    private double getDoubleValue(JSONObject fields, String fieldName){
        if(!fields.has(fieldName)) return 0.0;
        JSONObject node = fields.getJSONObject(fieldName);
        if(node.has("doubleValue")){
            return node.getDouble("doubleValue");
        }
        return 0.0;
    }
}