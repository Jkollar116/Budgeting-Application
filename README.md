# Budgeting-Application

A personal budgeting application with cryptocurrency wallet tracking capabilities.

## Overview

This application allows users to:
- Track personal budget and expenses
- Monitor cryptocurrency wallets (BTC and ETH)
- View real-time cryptocurrency market data
- Manage transactions

## Configuration

### API Keys

The application requires API keys for external services:
- [Etherscan API](https://etherscan.io/apis) - For Ethereum blockchain data
- [CoinMarketCap API](https://coinmarketcap.com/api/) - For cryptocurrency price data

These keys should be stored in `src/main/resources/keys.json`:

```json
{
  "etherscanApiKey": "YOUR_ETHERSCAN_API_KEY",
  "coinMarketCapApiKey": "YOUR_COINMARKETCAP_API_KEY"
}
```

## Security Improvements

- Removed hardcoded API keys from source code
- Added centralized configuration management using ConfigManager
- Implemented proper input validation for API requests
- Enhanced error handling with appropriate logging
- Added additional null checks for JSON parsing

## Development Setup

1. Clone the repository
2. Configure your API keys in `src/main/resources/keys.json`
3. Build the project with Maven:
   ```
   mvn clean install
   ```
4. Run the application:
   ```
   mvn spring-boot:run
   ```

## Features

- **Expense Tracking**: Record and categorize expenses
- **Wallet Monitoring**: Track Bitcoin and Ethereum wallet balances and transactions
- **Market Data**: View current cryptocurrency prices and 24-hour changes
- **Chat Interface**: Get assistance and tips through the chat feature
