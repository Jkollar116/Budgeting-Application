package org.example;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.io.IOException;

public class HomeDataHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        int netWorth = 100000;
        int cash = 30000, equity = 50000, investments = 20000;
        int totalIncome = 5000;
        int salary = 3500, bonus = 1000, otherIncome = 500;
        int totalExpenses = 3200;
        int billsDue = 2;
        int[] monthlyExpenses = {265,267,266,268,265,267,266,268,265,267,266,270};
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"netWorth\":").append(netWorth).append(",")
                .append("\"netWorthBreakdown\":{")
                .append("\"cash\":").append(cash).append(",")
                .append("\"equity\":").append(equity).append(",")
                .append("\"investments\":").append(investments)
                .append("},")
                .append("\"totalIncome\":").append(totalIncome).append(",")
                .append("\"totalIncomeBreakdown\":{")
                .append("\"salary\":").append(salary).append(",")
                .append("\"bonus\":").append(bonus).append(",")
                .append("\"other\":").append(otherIncome)
                .append("},")
                .append("\"totalExpenses\":").append(totalExpenses).append(",")
                .append("\"billsDue\":").append(billsDue).append(",")
                .append("\"monthlyExpenses\":[");
        for (int i = 0; i < monthlyExpenses.length; i++) {
            json.append(monthlyExpenses[i]);
            if (i < monthlyExpenses.length - 1) {
                json.append(",");
            }
        }
        json.append("]}");
        byte[] responseBytes = json.toString().getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
}
