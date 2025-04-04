#!/bin/bash
# Heroku Pre-Deployment Script for CashClimb
# This script checks for common issues before deploying to Heroku

echo "==== CashClimb Heroku Pre-Deployment Checks ===="
echo

# Check if Procfile exists
if [ -f "Procfile" ]; then
    echo "✅ Procfile exists"
else
    echo "❌ Procfile not found. Creating..."
    echo "web: java \$JAVA_OPTS -jar target/firebase-login-app-1.0.0.jar" > Procfile
    echo "✅ Procfile created"
fi

# Check if system.properties exists
if [ -f "system.properties" ]; then
    echo "✅ system.properties exists"
else
    echo "❌ system.properties not found. Creating..."
    echo "java.runtime.version=17" > system.properties
    echo "✅ system.properties created"
fi

# Check for serviceAccountKey.json
if [ -f "src/main/resources/serviceAccountKey.json" ]; then
    echo "⚠️ WARNING: serviceAccountKey.json found in resources."
    echo "   Consider setting up Firebase credentials via environment variables for Heroku"
    echo "   These files are excluded in .gitignore and won't be available on Heroku"
else
    echo "ℹ️ No serviceAccountKey.json found in resources."
    echo "   Make sure you've set up Firebase credentials via environment variables for Heroku"
fi

# Check for hardcoded file paths in Java files
echo
echo "Checking for hardcoded file paths that might cause issues on Heroku..."
HARDCODED_PATHS=$(grep -r "src/main/resources" --include="*.java" src/)
if [ -n "$HARDCODED_PATHS" ]; then
    echo "⚠️ WARNING: Found hardcoded file paths that may cause issues on Heroku:"
    echo "$HARDCODED_PATHS"
    echo
    echo "   Consider updating to use classpath resources instead. For example:"
    echo "   Change: new File(\"src/main/resources/somefile.html\")"
    echo "   To: getClass().getClassLoader().getResourceAsStream(\"somefile.html\")"
else
    echo "✅ No problematic hardcoded paths found"
fi

# Verify Maven build works
echo
echo "Building the application with Maven to verify it compiles..."
mvn package -DskipTests
if [ $? -eq 0 ]; then
    echo "✅ Maven build successful"
else
    echo "❌ Maven build failed. Fix the issues before deploying to Heroku."
    exit 1
fi

# Check for environment variables reminder
echo
echo "==== Environment Variables Reminder ===="
echo "Ensure these variables are set in Heroku:"
echo "  - CASHCLIMB_ALPHAVANTAGEAPIKEY"
echo "  - CASHCLIMB_COINMARKETCAPAPIKEY"
echo "  - CASHCLIMB_ETHERSCANAPI"
echo "  - CASHCLIMB_REDIS_ENABLED (optional)"
echo
echo "To set these variables, use:"
echo "heroku config:set VARIABLE_NAME=value"

echo
echo "==== Pre-Deployment Checks Complete ===="
echo "If all checks passed, you're ready to deploy to Heroku!"
echo "Run: git push heroku main"
