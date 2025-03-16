package org.example;

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.*;

public class Login {
    private static Firestore db;


    public static void main(String[] args) throws Exception {

        try {
            // Initialize Firebase with a service account file
            initializeFirebase();
            System.out.println("Firebase initialized successfully");

            // Access Firestore
            Firestore db = FirestoreClient.getFirestore();

            // Your logic here...

        } catch (IOException | ApiException e) {
            System.err.println("Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
        }

        int port = 8000;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port specified. Using default port " + port);
            }
        }


        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/register", new RegisterHandler());
        server.createContext("/forgot", new ForgotPasswordHandler());

        server.createContext("/api/getData", new HomeDataHandler());
        server.createContext("/api/chat", new ChatHandler());
        server.createContext("/api/wallets", new CryptoApiHandler());
        server.setExecutor(null);
        server.start();


        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:" + port));
        }
    }

    static class StaticFileHandler implements HttpHandler {
        private final String basePath = "src/main/resources";
        public void handle(HttpExchange exchange) throws IOException {
            String uriPath = exchange.getRequestURI().getPath();
            if (uriPath.equals("/")) {
                uriPath = "/index.html";
            }
            File file = new File(basePath + uriPath).getCanonicalFile();
            if (!file.getPath().startsWith(new File(basePath).getCanonicalPath())) {
                exchange.sendResponseHeaders(403, 0);
                exchange.getResponseBody().close();
                return;
            }
            if (!file.isFile()) {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
                return;
            }
            String mime = "text/plain";
            if (uriPath.endsWith(".html")) {
                mime = "text/html";
            } else if (uriPath.endsWith(".css")) {
                mime = "text/css";
            } else if (uriPath.endsWith(".js")) {
                mime = "application/javascript";
            }
            exchange.getResponseHeaders().set("Content-Type", mime);
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                String email = "";
                String password = "";
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        String key = URLDecoder.decode(parts[0], "UTF-8");
                        String value = URLDecoder.decode(parts[1], "UTF-8");
                        if (key.equals("email")) {
                            email = value;
                        } else if (key.equals("password")) {
                            password = value;
                        }
                    }
                }

                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_API_KEY;
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"returnSecureToken\":true}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    // Successfully logged in, extract the userID (UID) from the Firebase response
                    InputStreamReader isrResponse = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                    BufferedReader inResponse = new BufferedReader(isrResponse);
                    StringBuilder response = new StringBuilder();
                    while ((line = inResponse.readLine()) != null) {
                        response.append(line);
                    }
                    inResponse.close();

                    // Parse JSON response to extract userID (UID)
                    String userID = parseUserIdFromResponse(response.toString());

                    // Set userID as a cookie for future requests
                    // Redirect to home page
                    exchange.getResponseHeaders().set("Location", "/home.html");
                    exchange.sendResponseHeaders(302, -1);
                    return;
                } else {
                    // Handle error case (invalid login)
                    InputStream errorStream = conn.getErrorStream();
                    InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    BufferedReader inError = new BufferedReader(isrError);
                    StringBuilder errorResponse = new StringBuilder();
                    while ((line = inError.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    inError.close();
                    String userMessage = "Invalid email or password. Please try again.";
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Login Error</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Login Error</h2><p>" + userMessage + "</p>"
                            + "<a href='/index.html'>Try Again</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    private static void initializeFirebase() throws IOException {
        // Path to your Firebase service account key JSON file
        FileInputStream serviceAccount = new FileInputStream("target/classes/key.json");

        // Initialize Firebase with credentials from the service account
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        // Initialize the FirebaseApp instance
        FirebaseApp.initializeApp(options);
    }

    static class RegisterHandler implements HttpHandler {
        private final String basePath = "src/main/resources";
        Firestore db = FirestoreClient.getFirestore();  // Initialize Firestore

        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String fileName = "/register.html";
                File file = new File(basePath + fileName).getCanonicalFile();
                if (!file.isFile()) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                String mime = "text/html";
                exchange.getResponseHeaders().set("Content-Type", mime);
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                String email = "";
                String password = "";
                String confirm = "";
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        String key = URLDecoder.decode(parts[0], "UTF-8");
                        String value = URLDecoder.decode(parts[1], "UTF-8");
                        if (key.equals("email")) {
                            email = value;
                        } else if (key.equals("password")) {
                            password = value;
                        } else if (key.equals("confirm")) {
                            confirm = value;
                        }
                    }
                }
                if (!password.equals(confirm)) {
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Registration Error</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Registration Error</h2><p>Passwords do not match. Please try again.</p>"
                            + "<a href='/register.html'>Try Again</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                    return;
                }
                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + FIREBASE_API_KEY;
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"returnSecureToken\":true}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {

                    // Create a user map to store user data in Firestore
                    Map<String, Object> user = new HashMap<>();
                    user.put("email", email);
                    user.put("password", password); // In a real-world app, don't store plain text passwords

                    // Reference to the Firestore "users" collection
                    CollectionReference usersCollection = db.collection("Users");

                    ApiFuture<DocumentReference> future = usersCollection.add(user);

                    try {
                        // Get the document reference after the document is successfully added
                        DocumentReference documentReference = future.get(); // This blocks until the operation completes
                        System.out.println("User added to Firestore with ID: " + documentReference.getId());

                        Map<String, Object> userDetails = new HashMap<>();
                        userDetails.put("name", "example"); // This is for userDetails

                        Map<String, Object> expense = new HashMap<>();
                        expense.put("amount", 100);  // Example data for expense
                        expense.put("dateSpent", 0); // Example category of expense
                        expense.put("categoryID", 0); // Example category of expense
                        expense.put("userID", 0); // Example category of expense
                        expense.put("expenseID", 0); // Example category of expense

                        Map<String, Object> categories = new HashMap<>();
                        categories.put("categoryID", 0);  // Example category data
                        categories.put("name", "Groceries");  // Example category data

                        Map<String, Object> bill = new HashMap<>();
                        bill.put("billID", 0); // Example bill amount
                        bill.put("userID", 0);
                        bill.put("amountDue", 0);
                        bill.put("dueDate", 0);
                        bill.put("reminderDate", 0);

                        Map<String, Object> income = new HashMap<>();
                        income.put("incomeID", 0);  // Example income source
                        income.put("userID", 0);  // Example income source
                        income.put("source", "Job");  // Example income source
                        income.put("amount", 0);  // Example income source
                        income.put("dateReceived", 0);  // Example income source

                        Map<String, Object> investment = new HashMap<>();
                        investment.put("investmentID", 0); // Example investment type
                        investment.put("userID", 0); // Example investment type
                        investment.put("type", "type"); // Example investment type
                        investment.put("amount", 0); // Example investment type
                        investment.put("buyDate", 0); // Example investment type
                        investment.put("sellDate", 0); // Example investment type

                        Map<String, Object> goal = new HashMap<>();
                        goal.put("goalID", 0);  // Example financial goal
                        goal.put("userID", 0);  // Example financial goal
                        goal.put("description", "Buy House");  // Example financial goal
                        goal.put("targetAmount", 0);  // Example financial goal
                        goal.put("dueDate", 0);  // Example financial goal

                        Map<String, Object> liability = new HashMap<>();
                        liability.put("liabilityID", 0);  // Example liability type
                        liability.put("userID", 0);  // Example liability type
                        liability.put("type", "Loan");  // Example liability type
                        liability.put("amount", 0);  // Example liability type

                        Map<String, Object> financialReport = new HashMap<>();
                        financialReport.put("reportID", 0); // Example year for the report
                        financialReport.put("userID", 0); // Example year for the report
                        financialReport.put("type", "example"); // Example year for the report
                        financialReport.put("generatedDate", 0); // Example year for the report

                        Map<String, Object> asset = new HashMap<>();
                        asset.put("assetID", 0);  // Example asset type
                        asset.put("userID", 0);  // Example asset type
                        asset.put("type", "string");  // Example asset type
                        asset.put("value", 0);  // Example asset typ
                        // Create a subcollection 'userDetails' under the created user document

                        Map<String, Object> bankAccount = new HashMap<>();
                        bankAccount.put("accountID", 0);  // Example asset type
                        bankAccount.put("userID", 0);  // Example asset type
                        bankAccount.put("bankName", "example");  // Example asset type
                        bankAccount.put("lastSyncDate", 0);  // Example asset type

                        //comment

                        CollectionReference userDetailsCollection = documentReference.collection("userDetails");

                        CollectionReference bankAccountCollection = documentReference.collection("bankAccount");
                        CollectionReference billCollection = documentReference.collection("bill");
                        CollectionReference incomeCollection = documentReference.collection("income");
                        CollectionReference investmentCollection = documentReference.collection("investment");
                        CollectionReference goalCollection = documentReference.collection("goal");
                        CollectionReference liabilityCollection = documentReference.collection("liability");
                        CollectionReference financialReportCollection = documentReference.collection("financialReport");
                        CollectionReference expenseCollection = documentReference.collection("expense");
                        CollectionReference assetCollection = documentReference.collection("asset");

                        ApiFuture<DocumentReference> investmentFuture = investmentCollection.add(investment);
                        ApiFuture<DocumentReference> billFuture = billCollection.add(bill);
                        ApiFuture<DocumentReference> incomeFuture = incomeCollection.add(income);
                        ApiFuture<DocumentReference> goalFuture = goalCollection.add(goal);
                        ApiFuture<DocumentReference> expenseFuture = expenseCollection.add(expense);
                        ApiFuture<DocumentReference> userDetailsFuture = userDetailsCollection.add(userDetails);
                        ApiFuture<DocumentReference> liabilityFuture = liabilityCollection.add(liability);
                        ApiFuture<DocumentReference> assetFuture = assetCollection.add(asset);
                        ApiFuture<DocumentReference> finacialReportFuture = financialReportCollection.add(financialReport);
                        ApiFuture<DocumentReference> bankAccountFuture = bankAccountCollection.add(bankAccount);

                        DocumentReference expenseFutureDocRef = expenseFuture.get();
                        CollectionReference categoriesCollection = expenseFutureDocRef.collection("Categories");
                        ApiFuture<DocumentReference> categoriesFuture = categoriesCollection.add(categories); // Add category

                        // Wait for the operation to complete
                        DocumentReference userDetailsDocRef = userDetailsFuture.get(); // This will block until the operation completes
                        System.out.println("User details added to subcollection with ID: " + userDetailsDocRef.getId());

                    } catch (Exception e) {
                        // Handle any errors that may occur during the Firestore operation
                        System.err.println("Error adding user to Firestore: " + e.getMessage());
                    }

                    exchange.getResponseHeaders().set("Location", "/index.html");
                    exchange.sendResponseHeaders(302, -1);
                    return;
                } else {
                    InputStream errorStream = conn.getErrorStream();
                    InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    BufferedReader in = new BufferedReader(isrError);
                    StringBuilder response = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    String errorResponse = response.toString();
                    String userMessage = "Registration failed. Please try again.";
                    if (errorResponse.contains("EMAIL_EXISTS")) {
                        userMessage = "This email is already registered. Please use a different email.";
                    }
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Registration Error</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Registration Error</h2><p>" + userMessage + "</p>"
                            + "<a href='/register.html'>Try Again</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class ForgotPasswordHandler implements HttpHandler {
        private final String basePath = "src/main/resources";
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String fileName = "/forgot.html";
                File file = new File(basePath + fileName).getCanonicalFile();
                if (!file.isFile()) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.getResponseBody().close();
                    return;
                }
                String mime = "text/html";
                exchange.getResponseHeaders().set("Content-Type", mime);
                byte[] bytes = Files.readAllBytes(file.toPath());
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String formData = buf.toString();
                String email = "";
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] parts = pair.split("=");
                    if (parts.length == 2) {
                        String key = URLDecoder.decode(parts[0], "UTF-8");
                        String value = URLDecoder.decode(parts[1], "UTF-8");
                        if (key.equals("email")) {
                            email = value;
                        }
                    }
                }
                String firebaseUrl = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=" + FIREBASE_API_KEY;
                URL url = new URL(firebaseUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String jsonPayload = "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"" + email + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.close();
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String successHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Password Reset</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Password Reset</h2><p>A password reset email has been sent. Please check your inbox.</p>"
                            + "<a href='/index.html'>Back to Login</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] successBytes = successHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, successBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(successBytes);
                    osResp.close();
                } else {
                    InputStream errorStream = conn.getErrorStream();
                    InputStreamReader isrError = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                    BufferedReader in = new BufferedReader(isrError);
                    StringBuilder response = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    String userMessage = "Failed to send password reset email. Please try again.";
                    String errorHtml = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Forgot Password Error</title>"
                            + "<link rel=\"stylesheet\" href=\"style.css\"></head><body>"
                            + "<div class=\"login-container\"><h2>Forgot Password Error</h2><p>" + userMessage + "</p>"
                            + "<a href='/forgot.html'>Try Again</a></div></body></html>";
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    byte[] errorBytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, errorBytes.length);
                    OutputStream osResp = exchange.getResponseBody();
                    osResp.write(errorBytes);
                    osResp.close();
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    private static String parseUserIdFromResponse(String response) {
        // Parse the response JSON to get the userID (UID)
        // You can use a JSON parsing library like Gson or Jackson here
        // Example: {"idToken":"your_token","email":"user_email","localId":"user_id",...}
        String userID = "";
        try {
            // For simplicity, assuming the response contains "localId": "user_id"
            int start = response.indexOf("\"localId\":\"") + 11;
            int end = response.indexOf("\"", start);
            if (start > -1 && end > -1) {
                userID = response.substring(start, end);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return userID;
    }

}
