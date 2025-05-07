//package org.example;
//
//import org.json.JSONArray;
//import org.json.JSONObject;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.nio.charset.StandardCharsets;
//import java.time.Instant;
//import java.time.LocalDate;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.UUID;
//
///**
// * Utility class for calculating a user's net worth based on all their financial data.
// * This includes assets, liabilities, stock/crypto positions, bank balances, etc.
// */
//public class NetWorthCalculator {
//
//    /**
//     * Calculate user's net worth using all available financial data
//     */
//    public static double calculateNetWorth(String idToken, String userId) throws Exception {
//        double totalAssets = 0;
//        double totalLiabilities = 0;
//
//        // Get assets and liabilities
//        Map<String, Double> assetsLiabilities = getAssetsAndLiabilities(idToken, userId);
//        totalAssets += assetsLiabilities.get("assets");
//        totalLiabilities += assetsLiabilities.get("liabilities");
//
//        // Get stock positions
//        totalAssets += getStocksValue(idToken, userId);
//
//        // Get crypto positions
//        totalAssets += getCryptoValue(idToken, userId);
//
//        // Get bank account balances
//        totalAssets += getBankBalances(idToken, userId);
//
//        // Net worth = assets - liabilities
//        return totalAssets - totalLiabilities;
//    }
//
//    /**
//     * Get total value of assets and liabilities
//     */
//    private static Map<String, Double> getAssetsAndLiabilities(String idToken, String userId) throws Exception {
//        double totalAssets = 0;
//        double totalLiabilities = 0;
//
//        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + userId + "/AssetsLiabilities";
//
//        URL url = new URL(firestoreUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("GET");
//        conn.setRequestProperty("Authorization", "Bearer " + idToken);
//
//        int responseCode = conn.getResponseCode();
//
//        if (responseCode == 200) {
//            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
//            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = in.readLine()) != null) {
//                response.append(line);
//            }
//            in.close();
//
//            JSONObject firebaseResponse = new JSONObject(response.toString());
//
//            if (firebaseResponse.has("documents")) {
//                JSONArray documents = firebaseResponse.getJSONArray("documents");
//
//                for (int i = 0; i < documents.length(); i++) {
//                    JSONObject document = documents.getJSONObject(i);
//                    JSONObject fields = document.getJSONObject("fields");
//
//                    // Get type
//                    String type = fields.has("type") ? fields.getJSONObject("type").getString("stringValue") : "";
//
//                    // Get value
//                    double value = 0;
//                    if (fields.has("value")) {
//                        if (fields.getJSONObject("value").has("doubleValue")) {
//                            value = fields.getJSONObject("value").getDouble("doubleValue");
//                        } else if (fields.getJSONObject("value").has("integerValue")) {
//                            value = fields.getJSONObject("value").getInt("integerValue");
//                        }
//                    }
//
//                    // Add to appropriate total
//                    if ("asset".equalsIgnoreCase(type)) {
//                        totalAssets += value;
//                    } else if ("liability".equalsIgnoreCase(type)) {
//                        totalLiabilities += value;
//                    }
//                }
//            }
//        }
//
//        Map<String, Double> result = new HashMap<>();
//        result.put("assets", totalAssets);
//        result.put("liabilities", totalLiabilities);
//
//        return result;
//    }
//
//    /**
//     * Get total value of stock positions
//     */
//    private static double getStocksValue(String idToken, String userId) throws Exception {
//        double totalValue = 0;
//
//        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + userId + "/StockPositions";
//
//        URL url = new URL(firestoreUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("GET");
//        conn.setRequestProperty("Authorization", "Bearer " + idToken);
//
//        int responseCode = conn.getResponseCode();
//
//        if (responseCode == 200) {
//            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
//            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = in.readLine()) != null) {
//                response.append(line);
//            }
//            in.close();
//
//            JSONObject firebaseResponse = new JSONObject(response.toString());
//
//            if (firebaseResponse.has("documents")) {
//                JSONArray documents = firebaseResponse.getJSONArray("documents");
//
//                for (int i = 0; i < documents.length(); i++) {
//                    JSONObject document = documents.getJSONObject(i);
//                    JSONObject fields = document.getJSONObject("fields");
//
//                    // Get current value
//                    double value = 0;
//                    if (fields.has("currentValue")) {
//                        if (fields.getJSONObject("currentValue").has("doubleValue")) {
//                            value = fields.getJSONObject("currentValue").getDouble("doubleValue");
//                        } else if (fields.getJSONObject("currentValue").has("integerValue")) {
//                            value = fields.getJSONObject("currentValue").getInt("integerValue");
//                        }
//                    }
//
//                    totalValue += value;
//                }
//            }
//        }
//
//        return totalValue;
//    }
//
//    /**
//     * Get total value of crypto positions
//     */
//    private static double getCryptoValue(String idToken, String userId) throws Exception {
//        double totalValue = 0;
//
//        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + userId + "/CryptoWallets";
//
//        URL url = new URL(firestoreUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("GET");
//        conn.setRequestProperty("Authorization", "Bearer " + idToken);
//
//        int responseCode = conn.getResponseCode();
//
//        if (responseCode == 200) {
//            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
//            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = in.readLine()) != null) {
//                response.append(line);
//            }
//            in.close();
//
//            JSONObject firebaseResponse = new JSONObject(response.toString());
//
//            if (firebaseResponse.has("documents")) {
//                JSONArray documents = firebaseResponse.getJSONArray("documents");
//
//                for (int i = 0; i < documents.length(); i++) {
//                    JSONObject document = documents.getJSONObject(i);
//                    JSONObject fields = document.getJSONObject("fields");
//
//                    // Get balance and current price for each coin
//                    if (fields.has("holdings") && fields.getJSONObject("holdings").has("mapValue")) {
//                        JSONObject holdings = fields.getJSONObject("holdings").getJSONObject("mapValue").getJSONObject("fields");
//
//                        for (String coin : holdings.keySet()) {
//                            double amount = 0;
//                            JSONObject holdingObj = holdings.getJSONObject(coin);
//
//                            if (holdingObj.has("doubleValue")) {
//                                amount = holdingObj.getDouble("doubleValue");
//                            } else if (holdingObj.has("integerValue")) {
//                                amount = holdingObj.getInt("integerValue");
//                            }
//
//                            // Get current price from CryptoWallets/PriceCache if available
//                            double price = getCryptoPriceFromCache(idToken, userId, coin);
//                            totalValue += amount * price;
//                        }
//                    }
//                }
//            }
//        }
//
//        return totalValue;
//    }
//
//    /**
//     * Get crypto price from price cache
//     */
//    private static double getCryptoPriceFromCache(String idToken, String userId, String coin) throws Exception {
//        double price = 0;
//
//        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/CryptoPrices/PriceCache";
//
//        URL url = new URL(firestoreUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("GET");
//        conn.setRequestProperty("Authorization", "Bearer " + idToken);
//
//        int responseCode = conn.getResponseCode();
//
//        if (responseCode == 200) {
//            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
//            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = in.readLine()) != null) {
//                response.append(line);
//            }
//            in.close();
//
//            JSONObject priceCache = new JSONObject(response.toString());
//
//            if (priceCache.has("fields") && priceCache.getJSONObject("fields").has("prices") &&
//                    priceCache.getJSONObject("fields").getJSONObject("prices").has("mapValue")) {
//
//                JSONObject prices = priceCache.getJSONObject("fields").getJSONObject("prices")
//                        .getJSONObject("mapValue").getJSONObject("fields");
//
//                if (prices.has(coin.toUpperCase())) {
//                    JSONObject coinObj = prices.getJSONObject(coin.toUpperCase());
//
//                    if (coinObj.has("doubleValue")) {
//                        price = coinObj.getDouble("doubleValue");
//                    } else if (coinObj.has("integerValue")) {
//                        price = coinObj.getInt("integerValue");
//                    }
//                }
//            }
//        }
//
//        return price;
//    }
//
//    /**
//     * Get total bank account balances
//     */
//    private static double getBankBalances(String idToken, String userId) throws Exception {
//        double totalBalance = 0;
//
//        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/" + userId + "/BankAccounts";
//
//        URL url = new URL(firestoreUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("GET");
//        conn.setRequestProperty("Authorization", "Bearer " + idToken);
//
//        int responseCode = conn.getResponseCode();
//
//        if (responseCode == 200) {
//            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
//            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = in.readLine()) != null) {
//                response.append(line);
//            }
//            in.close();
//
//            JSONObject firebaseResponse = new JSONObject(response.toString());
//
//            if (firebaseResponse.has("documents")) {
//                JSONArray documents = firebaseResponse.getJSONArray("documents");
//
//                for (int i = 0; i < documents.length(); i++) {
//                    JSONObject document = documents.getJSONObject(i);
//                    JSONObject fields = document.getJSONObject("fields");
//
//                    // Get balance
//                    double balance = 0;
//                    if (fields.has("balance")) {
//                        if (fields.getJSONObject("balance").has("doubleValue")) {
//                            balance = fields.getJSONObject("balance").getDouble("doubleValue");
//                        } else if (fields.getJSONObject("balance").has("integerValue")) {
//                            balance = fields.getJSONObject("balance").getInt("integerValue");
//                        }
//                    }
//
//                    totalBalance += balance;
//                }
//            }
//        }
//
//        return totalBalance;
//    }
//
//    /**
//     * Save net worth history for tracking over time
//     */
//    public static void saveNetWorthHistory(String idToken, String userId, double netWorth) throws Exception {
//        String historyId = UUID.randomUUID().toString();
//
//        JSONObject document = new JSONObject();
//        JSONObject fields = new JSONObject();
//
//        fields.put("netWorth", new JSONObject().put("doubleValue", netWorth));
//        fields.put("date", new JSONObject().put("stringValue", LocalDate.now().toString()));
//        fields.put("timestamp", new JSONObject().put("timestampValue", Instant.now().toString()));
//
//        document.put("fields", fields);
//
//        // Save to Firestore
//        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
//                + userId + "/NetWorthHistory/" + historyId;
//
//        URL url = new URL(firestoreUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("POST");
//        conn.setRequestProperty("Content-Type", "application/json");
//        conn.setRequestProperty("Authorization", "Bearer " + idToken);
//        conn.setDoOutput(true);
//
//        OutputStream os = conn.getOutputStream();
//        os.write(document.toString().getBytes(StandardCharsets.UTF_8));
//        os.close();
//
//        conn.getResponseCode();
//    }
//
//    /**
//     * Get net worth history for a given time period
//     */
//    public static JSONArray getNetWorthHistory(String idToken, String userId, String period) throws Exception {
//        JSONArray history = new JSONArray();
//
//        LocalDate startDate;
//        LocalDate endDate = LocalDate.now();
//
//        // Determine start date based on period
//        switch (period) {
//            case "week":
//                startDate = endDate.minusWeeks(1);
//                break;
//            case "month":
//                startDate = endDate.minusMonths(1);
//                break;
//            case "quarter":
//                startDate = endDate.minusMonths(3);
//                break;
//            case "year":
//                startDate = endDate.minusYears(1);
//                break;
//            case "all":
//                startDate = LocalDate.of(2000, 1, 1); // Far back in time
//                break;
//            default:
//                startDate = endDate.minusMonths(1); // Default to 1 month
//        }
//
//        String firestoreUrl = "https://firestore.googleapis.com/v1/projects/cashclimb-d162c/databases/(default)/documents/Users/"
//                + userId + "/NetWorthHistory";
//
//        URL url = new URL(firestoreUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("GET");
//        conn.setRequestProperty("Authorization", "Bearer " + idToken);
//
//        int responseCode = conn.getResponseCode();
//
//        if (responseCode == 200) {
//            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
//            StringBuilder response = new StringBuilder();
//            String line;
//            while ((line = in.readLine()) != null) {
//                response.append(line);
//            }
//            in.close();
//
//            JSONObject firebaseResponse = new JSONObject(response.toString());
//            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//
//            if (firebaseResponse.has("documents")) {
//                JSONArray documents = firebaseResponse.getJSONArray("documents");
//                Map<String, Double> dateValues = new HashMap<>();
//
//                for (int i = 0; i < documents.length(); i++) {
//                    JSONObject document = documents.getJSONObject(i);
//                    JSONObject fields = document.getJSONObject("fields");
//
//                    if (fields.has("date") && fields.has("netWorth")) {
//                        String dateStr = fields.getJSONObject("date").getString("stringValue");
//                        LocalDate date = LocalDate.parse(dateStr, formatter);
//
//                        // Check if date is within the period
//                        if ((date.isEqual(startDate) || date.isAfter(startDate)) &&
//                            (date.isEqual(endDate) || date.isBefore(endDate))) {
//
//                            double netWorth = 0;
//                            if (fields.getJSONObject("netWorth").has("doubleValue")) {
//                                netWorth = fields.getJSONObject("netWorth").getDouble("doubleValue");
//                            } else if (fields.getJSONObject("netWorth").has("integerValue")) {
//                                netWorth = fields.getJSONObject("netWorth").getInt("integerValue");
//                            }
//
//                            // Handle multiple entries on the same date by keeping the latest one
//                            dateValues.put(dateStr, netWorth);
//                        }
//                    }
//                }
//
//                // Convert map to JSONArray of points
//                for (Map.Entry<String, Double> entry : dateValues.entrySet()) {
//                    JSONObject point = new JSONObject();
//                    point.put("date", entry.getKey());
//                    point.put("netWorth", entry.getValue());
//                    history.put(point);
//                }
//            }
//        }
//
//        return history;
//    }
//}
