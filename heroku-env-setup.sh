#!/bin/bash
# Heroku Environment Variables Setup Script for CashClimb
# Replace the placeholder values with your actual API keys before running

# Heroku app name - replace with your actual app name
HEROKU_APP="budgetingapplication-b11993b8549d"

echo "Setting up environment variables for Heroku app: $HEROKU_APP"

# Required API keys - Replace placeholder values with your actual keys
heroku config:set CASHCLIMB_ALPHAVANTAGEAPIKEY="your_alpha_vantage_api_key_here" --app $HEROKU_APP
heroku config:set CASHCLIMB_COINMARKETCAPAPIKEY="your_coinmarketcap_api_key_here" --app $HEROKU_APP
heroku config:set CASHCLIMB_ETHERSCANAPI="your_etherscan_api_key_here" --app $HEROKU_APP

# Optional Redis configuration - set to false if not using Redis
heroku config:set CASHCLIMB_REDIS_ENABLED="false" --app $HEROKU_APP

# Firebase configuration - Replace with your Firebase API key if different from the one in the code
heroku config:set FIREBASE_API_KEY="AIzaSyCMA1F8Xd4rCxGXssXIs8Da80qqP6jien8" --app $HEROKU_APP

# Java options to optimize for Heroku environment
heroku config:set JAVA_OPTS="-Xmx512m -Xms256m" --app $HEROKU_APP

# Verify the environment variables
echo "Verifying environment variables..."
heroku config --app $HEROKU_APP

echo "Environment variables setup complete."
echo "You can now deploy your application to Heroku."
