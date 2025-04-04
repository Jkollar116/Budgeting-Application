# Deploying to Heroku

Follow these steps to deploy your application to your existing Heroku site at https://budgetingapplication-b11993b8549d.herokuapp.com/

## Step 1: Install Heroku CLI

If you haven't already installed the Heroku CLI, download and install it from:
https://devcenter.heroku.com/articles/heroku-cli

## Step 2: Login to Heroku

Open a command prompt and run:
```
heroku login
```

This will open a browser where you can log in to your Heroku account.

## Step 3: Connect to Your Existing Heroku App

Navigate to your project directory and run:
```
heroku git:remote -a budgetingapplication-b11993b8549d
```

This connects your local repository to your existing Heroku app.

## Step 4: Set Environment Variables

Set the required environment variables:
```
heroku config:set CASHCLIMB_ALPHAVANTAGEAPIKEY=your_alpha_vantage_api_key -a budgetingapplication-b11993b8549d
heroku config:set CASHCLIMB_COINMARKETCAPAPIKEY=your_coinmarketcap_api_key -a budgetingapplication-b11993b8549d
heroku config:set CASHCLIMB_ETHERSCANAPI=your_etherscan_api_key -a budgetingapplication-b11993b8549d
heroku config:set CASHCLIMB_REDIS_ENABLED=false -a budgetingapplication-b11993b8549d
heroku config:set JAVA_OPTS="-Xmx512m -Xms256m" -a budgetingapplication-b11993b8549d
```

Replace `your_alpha_vantage_api_key`, `your_coinmarketcap_api_key`, and `your_etherscan_api_key` with your actual API keys. If you don't have these keys, you can skip setting them since we've added a fallback key for Alpha Vantage in the code.

## Step 5: Commit Your Changes

Ensure all your changes are committed:
```
git add .
git commit -m "Removed AWS dependencies and prepared for Heroku deployment"
```

## Step 6: Deploy to Heroku

Push your code to Heroku:
```
git push heroku main
```

If your main branch has a different name (such as master), use:
```
git push heroku master:main
```

## Step 7: Verify Deployment

After deployment completes, open your application:
```
heroku open -a budgetingapplication-b11993b8549d
```

Or visit https://budgetingapplication-b11993b8549d.herokuapp.com/ in your browser.

## Step 8: Check Logs

If you experience any issues, check the logs:
```
heroku logs --tail -a budgetingapplication-b11993b8549d
```

## Additional Commands

### Restart the application:
```
heroku restart -a budgetingapplication-b11993b8549d
```

### View application information:
```
heroku info -a budgetingapplication-b11993b8549d
```

### View configured environment variables:
```
heroku config -a budgetingapplication-b11993b8549d
