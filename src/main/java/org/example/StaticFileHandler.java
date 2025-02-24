package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.nio.file.Files;

public class StaticFileHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        // Default to index.html
        if (path.equals("/")) {
            path = "/index.html";
        }

        // Load file from resources
        try {
            String contentType = getContentType(path);
            InputStream is = getClass().getResourceAsStream(path);

            if (is == null) {
                String response = "404 (Not Found)\n";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = is.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            String response = "500 (Internal Server Error)\n";
            exchange.sendResponseHeaders(500, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".json")) return "application/json";
        return "text/plain";
    }
}
