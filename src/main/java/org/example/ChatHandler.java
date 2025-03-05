package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ChatHandler implements HttpHandler {
    private static final String API_KEY = ("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");
    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            InputStream is = exchange.getRequestBody();
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(response.statusCode(), responseBody.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBody.getBytes(StandardCharsets.UTF_8));
            os.close();
        } catch(Exception e) {
            e.printStackTrace();
            String errMsg = "{\"error\": \"" + e.getMessage().replaceAll("\"", "'") + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(500, errMsg.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(errMsg.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
    }
}
