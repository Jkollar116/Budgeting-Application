package org.example;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public class CryptoTest {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        // Static file handler for serving HTML/CSS/JS files
        server.createContext("/", new Login.StaticFileHandler());

        // Crypto API endpoint
        server.createContext("/api/wallets", new CryptoApiHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Crypto test server started on port 8000");
        System.out.println("Open http://localhost:8000/crypto.html in your browser");
    }
}
