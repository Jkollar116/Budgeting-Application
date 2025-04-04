#!/bin/bash
# Heroku startup script for CashClimb
# This script creates the necessary directory structure and copies resources
# to work around hardcoded file paths in the application

echo "Starting CashClimb application setup..."

# Create necessary directories
echo "Creating directory structure..."
mkdir -p src/main/resources

# Copy resources from the classpath to the expected location
echo "Copying resources from classpath..."
cp -r target/classes/* src/main/resources/

# Print environment variables for debugging (excluding sensitive values)
echo "Environment setup:"
echo "PORT: $PORT"
echo "CASHCLIMB_REDIS_ENABLED: $CASHCLIMB_REDIS_ENABLED"
echo "API keys configured: $([ ! -z "$CASHCLIMB_ALPHAVANTAGEAPIKEY" ] && echo "AlphaVantage ✓") $([ ! -z "$CASHCLIMB_COINMARKETCAPAPIKEY" ] && echo "CoinMarketCap ✓") $([ ! -z "$CASHCLIMB_ETHERSCANAPI" ] && echo "Etherscan ✓")"

# Start the application
echo "Starting application..."
java $JAVA_OPTS -jar target/firebase-login-app-1.0.0.jar
