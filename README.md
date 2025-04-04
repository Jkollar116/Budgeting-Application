# CashClimb Financial Management Application

CashClimb is a comprehensive financial management application that helps users track their expenses, manage budgets, monitor investments, and gain insights into their financial health.

## 🌟 Key Features

- **Dashboard**: Visualize your financial health with dynamic charts and metrics
- **Budgeting & Goals**: Set financial goals and track your progress
- **Expense Tracking**: Log and categorize expenses
- **Investment Management**: Track stocks and cryptocurrency investments
- **Secure Authentication**: User accounts with Firebase authentication

## 🚀 Technical Highlights

### Architecture

- **Backend**: Java Spring Boot application
- **Frontend**: Custom HTML/CSS/JS with responsive design
- **Database**: Firestore for data persistence
- **Authentication**: Firebase Authentication
- **API Integration**: Stock market and cryptocurrency data APIs

### Recent Improvements

#### Enhanced Error Handling & Logging
- Implemented SLF4J with Logback for structured logging
- Added comprehensive error tracking and contextual logs
- Created logback.xml with console and file appenders

#### Advanced Security Measures
- Removed hardcoded API keys and replaced with secure configuration
- Multiple credential sources (environment variables, AWS Secrets Manager, config files)
- API key rotation support

#### Distributed Caching with Redis
- Added Redis integration for high-performance caching
- Fallback to local caching when Redis is unavailable
- Configurable cache timeouts for different data types

#### Automated Testing
- Comprehensive unit tests with JUnit and Mockito
- Test coverage for critical service components
- Reflection-based testing for private methods

#### CI/CD Pipeline
- GitHub Actions workflow for continuous integration
- Automated build, test, and analysis
- Prepared for SonarCloud code quality analysis

#### Containerization
- Docker support for consistent deployment
- Containerized application with proper resource configuration

## 🛠️ Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Redis (optional, for distributed caching)

### Configuration

1. Configure environment variables:
   ```
   CASHCLIMB_ALPHAVANTAGEAPIKEY=your_api_key
   CASHCLIMB_COINMARKETCAPAPIKEY=your_api_key
   CASHCLIMB_ETHERSCANAPI=your_api_key
   ```

2. For Redis (optional):
   ```
   CASHCLIMB_REDIS_ENABLED=true
   CASHCLIMB_REDIS_HOST=localhost
   CASHCLIMB_REDIS_PORT=6379
   CASHCLIMB_REDIS_PASSWORD=your_redis_password  # if required
   ```

3. For AWS Secrets Manager (optional):
   ```
   USE_AWS_SECRETS=true
   AWS_REGION=us-east-1
   AWS_ACCESS_KEY_ID=your_access_key
   AWS_SECRET_ACCESS_KEY=your_secret_key
   ```

### Building the Application

```bash
mvn clean install
```

### Running the Application

```bash
java -jar target/firebase-login-app-1.0.0.jar
```

### Using Docker

Build the Docker image:
```bash
docker build -t cashclimb:latest .
```

Run the container:
```bash
docker run -p 8080:8080 \
  -e CASHCLIMB_ALPHAVANTAGEAPIKEY=your_api_key \
  -e CASHCLIMB_REDIS_ENABLED=false \
  cashclimb:latest
```

## 🧪 Testing

Run the test suite:
```bash
mvn test
```

## 📚 API Documentation

[API Documentation](docs/api.md) - Details about the available endpoints and request/response formats.

## 📊 Monitoring

Application exposes Actuator endpoints for monitoring:
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Prometheus: `/actuator/prometheus`

## 🌐 Frontend Access

Once running, access the application at: http://localhost:8080

## 🔧 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
