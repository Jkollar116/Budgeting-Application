package org.example;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class BlockchainApiService {
    private final OkHttpClient client = new OkHttpClient();
    private static final String ETHERSCAN_API_KEY = "G6YJ1PGVSDWY8VP11ZKYPQJ78VWIE7YAUQ";
    private static final String BLOCKCHAIN_INFO_API = "https://blockchain.info";
    private static final String ETHERSCAN_API = "https://api.etherscan.io/api";

    public WalletInfo getBitcoinWalletInfo(String address) throws IOException {
        try {
            String url = BLOCKCHAIN_INFO_API + "/rawaddr/" + address;
            JSONObject response = makeApiCall(url, null);

            BigDecimal balance = BigDecimal.valueOf(response.getLong("final_balance"))
                    .divide(BigDecimal.valueOf(100000000), 8, RoundingMode.HALF_UP);

            List<Transaction> transactions = new ArrayList<>();
            if (response.has("txs")) {
                JSONArray txs = response.getJSONArray("txs");
                for (int i = 0; i < Math.min(txs.length(), 10); i++) {
                    JSONObject tx = txs.getJSONObject(i);
                    transactions.add(parseBitcoinTransaction(tx, address));
                }
            }

            return new WalletInfo(balance.doubleValue(), transactions, 0.0, 0.0);
        } catch (Exception e) {
            System.err.println("Error fetching Bitcoin wallet info: " + e.getMessage());
            return new WalletInfo(0.0, new ArrayList<>(), 0.0, 0.0);
        }
    }

    public WalletInfo getEthereumWalletInfo(String address) throws IOException {
        try {
            // Get balance
            String balanceUrl = String.format("%s?module=account&action=balance&address=%s&tag=latest&apikey=%s",
                    ETHERSCAN_API, address, ETHERSCAN_API_KEY);
            JSONObject balanceResponse = makeApiCall(balanceUrl, null);

            BigDecimal balance = BigDecimal.ZERO;
            if (balanceResponse.getString("status").equals("1")) {
                balance = new BigDecimal(balanceResponse.getString("result"))
                        .divide(BigDecimal.valueOf(1000000000000000000L), 18, RoundingMode.HALF_UP);
            }

            // Get transactions
            String txUrl = String.format("%s?module=account&action=txlist&address=%s&startblock=0&endblock=99999999&page=1&offset=10&sort=desc&apikey=%s",
                    ETHERSCAN_API, address, ETHERSCAN_API_KEY);
            JSONObject txResponse = makeApiCall(txUrl, null);

            List<Transaction> transactions = new ArrayList<>();
            if (txResponse.getString("status").equals("1")) {
                JSONArray txs = txResponse.getJSONArray("result");
                for (int i = 0; i < Math.min(txs.length(), 10); i++) {
                    JSONObject tx = txs.getJSONObject(i);
                    transactions.add(parseEthereumTransaction(tx, address));
                }
            }

            return new WalletInfo(balance.doubleValue(), transactions, 0.0, 0.0);
        } catch (Exception e) {
            System.err.println("Error fetching Ethereum wallet info: " + e.getMessage());
            return new WalletInfo(0.0, new ArrayList<>(), 0.0, 0.0);
        }
    }

    private Transaction parseBitcoinTransaction(JSONObject tx, String walletAddress) {
        try {
            JSONArray outputs = tx.getJSONArray("out");
            JSONObject firstOutput = outputs.getJSONObject(0);
            boolean isReceived = firstOutput.has("addr") && firstOutput.getString("addr").equals(walletAddress);

            // Calculate total value from outputs
            double value = 0;
            for (int i = 0; i < outputs.length(); i++) {
                JSONObject output = outputs.getJSONObject(i);
                if (output.has("value")) {
                    value += output.getLong("value");
                }
            }

            String fromAddress = "";
            if (tx.has("inputs") && tx.getJSONArray("inputs").length() > 0) {
                JSONObject input = tx.getJSONArray("inputs").getJSONObject(0);
                if (input.has("prev_out") && input.getJSONObject("prev_out").has("addr")) {
                    fromAddress = input.getJSONObject("prev_out").getString("addr");
                }
            }

            return new Transaction(
                    isReceived ? "RECEIVE" : "SEND",
                    BigDecimal.valueOf(value)
                            .divide(BigDecimal.valueOf(100000000), 8, RoundingMode.HALF_UP)
                            .doubleValue(),
                    new java.util.Date(tx.getLong("time") * 1000L).toString(),
                    tx.getString("hash"),
                    fromAddress,
                    firstOutput.getString("addr"),
                    tx.has("confirmations") && tx.getInt("confirmations") > 6 ? "CONFIRMED" : "PENDING"
            );
        } catch (Exception e) {
            System.err.println("Error parsing Bitcoin transaction: " + e.getMessage());
            return new Transaction("UNKNOWN", 0.0, "", "", "", "", "UNKNOWN");
        }
    }

    private Transaction parseEthereumTransaction(JSONObject tx, String walletAddress) {
        try {
            boolean isReceived = tx.getString("to").equalsIgnoreCase(walletAddress);

            return new Transaction(
                    isReceived ? "RECEIVE" : "SEND",
                    new BigDecimal(tx.getString("value"))
                            .divide(BigDecimal.valueOf(1000000000000000000L), 18, RoundingMode.HALF_UP)
                            .doubleValue(),
                    new java.util.Date(Long.parseLong(tx.getString("timeStamp")) * 1000L).toString(),
                    tx.getString("hash"),
                    tx.getString("from"),
                    tx.getString("to"),
                    tx.has("confirmations") && tx.getInt("confirmations") > 12 ? "CONFIRMED" : "PENDING"
            );
        } catch (Exception e) {
            System.err.println("Error parsing Ethereum transaction: " + e.getMessage());
            return new Transaction("UNKNOWN", 0.0, "", "", "", "", "UNKNOWN");
        }
    }

    private JSONObject makeApiCall(String url, String apiKey) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url);

        if (apiKey != null) {
            requestBuilder.addHeader("X-CMC_PRO_API_KEY", apiKey);
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected response " + response);
            return new JSONObject(response.body().string());
        }
    }
}
