#!/bin/bash
# Script to deploy CashClimb to Heroku with Firebase verification features

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting CashClimb Heroku deployment...${NC}"

# Check if Heroku CLI is installed
if ! command -v heroku &> /dev/null; then
    echo -e "${RED}Error: Heroku CLI is not installed.${NC}"
    echo "Please install it from: https://devcenter.heroku.com/articles/heroku-cli"
    exit 1
fi

# Check login status
echo -e "${YELLOW}Checking Heroku login status...${NC}"
HEROKU_AUTH=$(heroku auth:whoami 2>&1)
if [[ $HEROKU_AUTH == *"not logged in"* ]]; then
    echo -e "${YELLOW}You need to log in to Heroku first.${NC}"
    heroku login
fi

# Connect to the Heroku app
echo -e "${YELLOW}Connecting to the Heroku application...${NC}"
APP_NAME="budgetingapplication-b11993b8549d"
heroku git:remote -a $APP_NAME

# Check if connection was successful
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to connect to the Heroku app. Please check your permissions.${NC}"
    exit 1
fi

echo -e "${GREEN}Successfully connected to Heroku app: $APP_NAME${NC}"

# Check if there are uncommitted changes
if [[ $(git status --porcelain) ]]; then
    echo -e "${YELLOW}You have uncommitted changes. Committing them now...${NC}"
    git add .
    git commit -m "Updated Firebase verification and diagnostics"
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to commit changes. Please resolve any issues and try again.${NC}"
        exit 1
    fi
    echo -e "${GREEN}Changes committed successfully.${NC}"
fi

# Push to Heroku
echo -e "${YELLOW}Deploying to Heroku...${NC}"
git push heroku main

if [ $? -ne 0 ]; then
    echo -e "${RED}Deployment failed. Please check the error messages above.${NC}"
    exit 1
fi

echo -e "${GREEN}Deployment successful!${NC}"

# Display URL
echo -e "${YELLOW}Your application is now live at:${NC}"
echo -e "${GREEN}https://$APP_NAME.herokuapp.com${NC}"

# Display Firebase verification instructions
echo -e "\n${YELLOW}Firebase Verification Instructions:${NC}"
echo -e "1. Visit the live application at: ${GREEN}https://$APP_NAME.herokuapp.com${NC}"
echo -e "2. Log in to the application with your credentials"
echo -e "3. To verify Firebase connectivity, visit: ${GREEN}https://$APP_NAME.herokuapp.com/api/diagnostic/firebase${NC}"
echo -e "   This will run tests to ensure Firebase is working correctly."
echo -e "4. For more details on Firebase verification methods, see: ${GREEN}firebase-verification.md${NC}"

# Display log command
echo -e "\n${YELLOW}To check application logs:${NC}"
echo -e "${GREEN}heroku logs --tail -a $APP_NAME${NC}"

echo -e "\n${GREEN}Done!${NC}"
