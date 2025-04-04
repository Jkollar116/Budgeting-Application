# CashClimb API Documentation

This document outlines the available API endpoints in the CashClimb application.

## Authentication

Most API endpoints require authentication. Include the Firebase ID token in the request header:

```
Authorization: Bearer <firebase_id_token>
```

## API Endpoints

### User Data

#### Get User Profile

```
GET /api/user/profile
```

Returns the current user's profile information.

**Response**:
```json
{
  "uid": "user123",
  "email": "user@example.com",
  "displayName": "John Doe",
  "photoURL": "https://example.com/photo.jpg",
  "createdAt": "2024-01-15T08:30:00Z"
}
```

### Dashboard Data

#### Get Dashboard Data

```
GET /api/getData
```

Returns consolidated data for the dashboard.

**Response**:
```json
{
  "fields": {
    "netWorth": { "integerValue": 15000 },
    "totalIncome": { "integerValue": 5000 },
    "totalExpenses": { "integerValue": 3000 },
    "billsDue": { "integerValue": 2 },
    "monthlyExpenses": {
      "arrayValue": {
        "values": [
          { "integerValue": 2800 },
          { "integerValue": 3100 },
          { "integerValue": 2950 }
        ]
      }
    },
    "netWorthBreakdown": {
      "mapValue": {
        "fields": {
          "cash": { "integerValue": 5000 },
          "equity": { "integerValue": 5000 },
          "investments": { "integerValue": 5000 }
        }
      }
    },
    "totalIncomeBreakdown": {
      "mapValue": {
        "fields": {
          "salary": { "integerValue": 4000 },
          "bonus": { "integerValue": 500 },
          "other": { "integerValue": 500 }
        }
      }
    }
  }
}
```

### Expenses

#### Get Expenses

```
GET /api/expenses
```

Returns the user's expenses.

**Query Parameters**:
- `period`: Optional. Filter by period (daily, weekly, monthly, yearly). Default: monthly.
- `category`: Optional. Filter by expense category.
- `limit`: Optional. Limit the number of results. Default: 20.

**Response**:
```json
{
  "documents": [
    {
      "name": "expenses/expense123",
      "fields": {
        "amount": { "doubleValue": 50.25 },
        "category": { "stringValue": "Groceries" },
        "date": { "timestampValue": "2024-03-15T14:30:00Z" },
        "description": { "stringValue": "Weekly grocery shopping" }
      }
    }
  ]
}
```

#### Add Expense

```
POST /api/expenses
```

Adds a new expense.

**Request Body**:
```json
{
  "amount": 50.25,
  "category": "Groceries",
  "date": "2024-03-15T14:30:00Z",
  "description": "Weekly grocery shopping"
}
```

**Response**:
```json
{
  "id": "expense123",
  "status": "success"
}
```

### Financial Goals

#### Get Goals

```
GET /api/goals
```

Returns the user's financial goals.

**Response**:
```json
{
  "documents": [
    {
      "name": "goals/goal123",
      "fields": {
        "name": { "stringValue": "Emergency Fund" },
        "category": { "stringValue": "Savings" },
        "targetAmount": { "doubleValue": 5000.00 },
        "currentAmount": { "doubleValue": 2500.00 },
        "deadline": { "stringValue": "2025-12-31" }
      }
    }
  ]
}
```

#### Add Goal

```
POST /api/goals
```

Adds a new financial goal.

**Request Body**:
```json
{
  "name": "Emergency Fund",
  "category": "Savings",
  "targetAmount": 5000.00,
  "currentAmount": 2500.00,
  "deadline": "2025-12-31"
}
```

**Response**:
```json
{
  "id": "goal123",
  "status": "success"
}
```

#### Update Goal

```
PUT /api/goals/{goalId}
```

Updates an existing financial goal.

**Request Body**:
```json
{
  "currentAmount": 3000.00
}
```

**Response**:
```json
{
  "status": "success",
  "updated": ["currentAmount"]
}
```

#### Delete Goal

```
DELETE /api/goals/{goalId}
```

Deletes a financial goal.

**Response**:
```json
{
  "status": "success"
}
```

### Stock Market Data

#### Get Stock Quote

```
GET /api/stocks/quote/{symbol}
```

Returns current stock quote information.

**Response**:
```json
{
  "symbol": "AAPL",
  "name": "Apple Inc.",
  "price": 150.25,
  "change": 2.75,
  "changePercent": 1.86,
  "open": 148.50,
  "high": 151.00,
  "low": 147.80,
  "volume": 75600000,
  "previousClose": 147.50,
  "lastUpdated": "2024-03-15"
}
```

#### Get Stock History

```
GET /api/stocks/history/{symbol}
```

Returns historical price data for a stock.

**Query Parameters**:
- `timeframe`: Optional. One of: 1D, 1W, 1M, 3M, 1Y. Default: 1M.

**Response**:
```json
[
  {
    "timestamp": 1709481600000,
    "price": 150.25,
    "open": 148.50,
    "high": 151.00,
    "low": 147.80,
    "volume": 75600000
  },
  {
    "timestamp": 1709395200000,
    "price": 147.50,
    "open": 146.80,
    "high": 148.20,
    "low": 146.50,
    "volume": 68400000
  }
]
```

### Cryptocurrency Data

#### Get Crypto Price

```
GET /api/crypto/price/{symbol}
```

Returns current cryptocurrency price information.

**Response**:
```json
{
  "symbol": "BTC",
  "name": "Bitcoin",
  "price": 45000.00,
  "change24h": 1200.00,
  "changePercent24h": 2.75,
  "marketCap": 857000000000,
  "volume24h": 28500000000,
  "lastUpdated": "2024-03-15T14:30:00Z"
}
```

#### Get Wallet Info

```
GET /api/crypto/wallets/{address}
```

Returns information about a cryptocurrency wallet.

**Response**:
```json
{
  "address": "0x1234567890abcdef1234567890abcdef12345678",
  "balances": [
    {
      "coin": "ETH",
      "balance": 2.5,
      "usdValue": 7500.00
    },
    {
      "coin": "USDT",
      "balance": 1000.00,
      "usdValue": 1000.00
    }
  ],
  "totalValue": 8500.00,
  "lastUpdated": "2024-03-15T14:30:00Z"
}
```

## Error Handling

All API endpoints follow a consistent error response format:

```json
{
  "status": "error",
  "code": 404,
  "message": "Resource not found",
  "details": "The requested goal with ID goal456 does not exist."
}
```

Common error codes:
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 429: Too Many Requests
- 500: Internal Server Error

## Rate Limiting

API endpoints are rate-limited to protect against abuse. The current limits are:

- 60 requests per minute for authenticated users
- 5 requests per minute for stock market API requests (due to external API constraints)

When rate limited, the API will respond with a 429 status code and headers indicating when you can retry:

```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1678893600
