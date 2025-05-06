package org.example;

import java.util.List;
import java.util.Map;

public class HomeData {
    private long netWorth;
    private Map<String, Long> netWorthBreakdown;
    private long totalIncome;
    private Map<String, Long> totalIncomeBreakdown;
    private long totalExpenses;
    private long totalInvestments;
    private int billsDue;
    private List<Long> monthlyIncomes;
    private List<Long> monthlyExpenses;
    private List<Long> monthlyStockValues;

    // Getters and setters
    public long getNetWorth() {
        return netWorth;
    }

    public void setNetWorth(long netWorth) {
        this.netWorth = netWorth;
    }

    public Map<String, Long> getNetWorthBreakdown() {
        return netWorthBreakdown;
    }

    public void setNetWorthBreakdown(Map<String, Long> netWorthBreakdown) {
        this.netWorthBreakdown = netWorthBreakdown;
    }

    public long getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(long totalIncome) {
        this.totalIncome = totalIncome;
    }

    public Map<String, Long> getTotalIncomeBreakdown() {
        return totalIncomeBreakdown;
    }

    public void setTotalIncomeBreakdown(Map<String, Long> totalIncomeBreakdown) {
        this.totalIncomeBreakdown = totalIncomeBreakdown;
    }

    public long getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(long totalExpenses) {
        this.totalExpenses = totalExpenses;
    }

    public long getTotalInvestments() {
        return totalInvestments;
    }

    public void setTotalInvestments(long totalInvestments) {
        this.totalInvestments = totalInvestments;
    }

    public int getBillsDue() {
        return billsDue;
    }

    public void setBillsDue(int billsDue) {
        this.billsDue = billsDue;
    }

    public List<Long> getMonthlyIncomes() {
        return monthlyIncomes;
    }

    public void setMonthlyIncomes(List<Long> monthlyIncomes) {
        this.monthlyIncomes = monthlyIncomes;
    }

    public List<Long> getMonthlyExpenses() {
        return monthlyExpenses;
    }

    public void setMonthlyExpenses(List<Long> monthlyExpenses) {
        this.monthlyExpenses = monthlyExpenses;
    }

    public List<Long> getMonthlyStockValues() {
        return monthlyStockValues;
    }

    public void setMonthlyStockValues(List<Long> monthlyStockValues) {
        this.monthlyStockValues = monthlyStockValues;
    }
}
