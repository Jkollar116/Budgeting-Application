// Enhanced API client for the budgeting application
// This file provides the frontend with methods to communicate with the backend services

/**
 * Fetches dashboard data from the HomeDataHandler endpoint
 * Returns data for graphs and displays on the home page
 */
async function fetchDashboardData() {
    try {
        // Add a custom timeout to prevent long-hanging requests
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 10000);
        
        const response = await fetch('/api/homeData', {
            signal: controller.signal
        });
        
        // Clear the timeout if the request completes before timeout
        clearTimeout(timeoutId);
        
        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }
        
        const data = await response.json();
        return data;
    } catch (error) {
        console.error('Error fetching dashboard data:', error);
        
        // Return a default structure with empty values as fallback
        // This structure matches what's created in the backend's createEmptyResponse()
        const emptyMonthlyValues = Array(12).fill(0).map(val => ({ integerValue: "0" }));
        
        return {
            fields: {
                netWorth: { integerValue: "0" },
                totalIncome: { integerValue: "0" },
                totalExpenses: { integerValue: "0" },
                billsDue: { integerValue: "0" },
                totalInvestments: { integerValue: "0" },
                netWorthBreakdown: { stringValue: JSON.stringify({ cash: 0, stocks: 0, crypto: 0 }) },
                totalIncomeBreakdown: { stringValue: JSON.stringify({ salary: 0, bonus: 0, other: 0 }) },
                monthlyExpenses: { arrayValue: { values: emptyMonthlyValues } },
                monthlyIncomes: { arrayValue: { values: emptyMonthlyValues } },
                monthlyStockValues: { arrayValue: { values: emptyMonthlyValues } }
            }
        };
    }
}

/**
 * Fetches expenses data for visualization and analysis
 */
async function fetchExpensesData() {
    try {
        const response = await fetch('/api/expenses');
        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching expenses data:', error);
        return { documents: [] };
    }
}

/**
 * Fetches income data for visualization and analysis
 */
async function fetchIncomeData() {
    try {
        const response = await fetch('/api/income');
        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching income data:', error);
        return { documents: [], monthlyIncomes: [0,0,0,0,0,0,0,0,0,0,0,0], yearlyIncome: 0 };
    }
}

/**
 * Fetches bills data to display bills due on the dashboard
 */
async function fetchBillsData() {
    try {
        const response = await fetch('/api/bills');
        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching bills data:', error);
        return { documents: [] };
    }
}

/**
 * Fetches portfolio data for investments dashboard
 */
async function fetchPortfolioData() {
    try {
        const response = await fetch('/api/portfolio');
        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching portfolio data:', error);
        return { documents: [] };
    }
}

/**
 * Adds a new expense
 */
async function addExpense(expenseData) {
    try {
        const response = await fetch('/api/expenses', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(expenseData)
        });
        
        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }
        
        return await response.json();
    } catch (error) {
        console.error('Error adding expense:', error);
        throw error;
    }
}

/**
 * Adds a new income entry
 */
async function addIncome(incomeData) {
    try {
        const response = await fetch('/api/income', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(incomeData)
        });
        
        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }
        
        return await response.json();
    } catch (error) {
        console.error('Error adding income:', error);
        throw error;
    }
}

/**
 * Creates or updates an asset or liability
 */
async function saveAssetLiability(type, data) {
    try {
        const endpoint = type === 'asset' ? '/api/assets' : '/api/liabilities';
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        
        if (!response.ok) {
            throw new Error(`API error: ${response.status}`);
        }
        
        return await response.json();
    } catch (error) {
        console.error(`Error saving ${type}:`, error);
        throw error;
    }
}

// Format currency with proper locale settings
function formatCurrency(amount) {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD',
        minimumFractionDigits: 2
    }).format(amount);
}

// Format percentage with proper locale settings
function formatPercentage(value) {
    return new Intl.NumberFormat('en-US', {
        style: 'percent',
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
    }).format(value / 100);
}

// Parse a currency string back to a number
function parseCurrency(currencyString) {
    if (!currencyString) return 0;
    return parseFloat(currencyString.replace(/[^0-9.-]+/g, ""));
}
