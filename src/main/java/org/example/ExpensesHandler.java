package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpensesHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("ExpensesHandler invoked: " + exchange.getRequestMethod());
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        System.out.println("ExpensesHandler cookies: " + cookies);
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
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Expenses";
            System.out.println("Firestore GET URL: " + firestoreUrl);
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            int responseCode = conn.getResponseCode();
            System.out.println("Firestore GET response code: " + responseCode);
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } else {
                exchange.sendResponseHeaders(responseCode, -1);
            }
        } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            StringBuilder body = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
            br.close();
            String requestBody = body.toString();
            System.out.println("ExpensesHandler requestBody: " + requestBody);
            String dateValue = getJsonValue(requestBody, "date");
            String nameValue = getJsonValue(requestBody, "name");
            String catValue = getJsonValue(requestBody, "category");
            String totalValue = getJsonValue(requestBody, "total");
            double amount;
            try {
                amount = Double.parseDouble(totalValue);
            } catch (Exception e) {
                System.out.println("400 - invalid totalValue format");
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
                    + localId + "/Expenses";
            System.out.println("Firestore URL (POST): " + firestoreUrl);
            URL url = new URL(firestoreUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            String jsonToFirestore = "{\"fields\":{"
                    + "\"date\":{\"stringValue\":\"" + dateValue + "\"},"
                    + "\"name\":{\"stringValue\":\"" + nameValue + "\"},"
                    + "\"category\":{\"stringValue\":\"" + catValue + "\"},"
                    + "\"total\":{\"doubleValue\":" + amount + "}"
                    + "}}";
            System.out.println("jsonToFirestore: " + jsonToFirestore);
            OutputStream os = conn.getOutputStream();
            os.write(jsonToFirestore.getBytes(StandardCharsets.UTF_8));
            os.close();
            int responseCodePost = conn.getResponseCode();
            System.out.println("Firestore POST response code: " + responseCodePost);
            if (responseCodePost == 200 || responseCodePost == 201) {
                String success = "Expense added successfully.";
                exchange.sendResponseHeaders(200, success.length());
                OutputStream respOs = exchange.getResponseBody();
                respOs.write(success.getBytes(StandardCharsets.UTF_8));
                respOs.close();
            } else {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    BufferedReader errBr = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                    StringBuilder errBody = new StringBuilder();
                    String ln;
                    while ((ln = errBr.readLine()) != null) {
                        errBody.append(ln);
                    }
                    errBr.close();
                    System.out.println("Firestore error body: " + errBody.toString());
                }
                exchange.sendResponseHeaders(responseCodePost, -1);
            }
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

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

    private static String getJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|([\\d.-]+))");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return matcher.group(1);
            } else if (matcher.group(2) != null) {
                return matcher.group(2);
            }
        }
        return "";
    }
}
