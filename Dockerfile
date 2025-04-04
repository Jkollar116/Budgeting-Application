# Multi-stage build for optimized container size
# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-focal AS builder

WORKDIR /app

# Copy maven configuration
COPY pom.xml .
COPY .mvn/ ./.mvn/
COPY mvnw .
COPY mvnw.cmd .

# Download all dependencies
# This will be cached if the pom.xml doesn't change
RUN ./mvnw dependency:go-offline -B || true

# Copy source code
COPY src ./src/

# Build the application
RUN ./mvnw package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-focal

WORKDIR /app

# Copy the built artifact from the builder stage
COPY --from=builder /app/target/firebase-login-app-1.0.0.jar app.jar

# Create directory for logs
RUN mkdir -p /app/logs && \
    # Set permissions
    chmod -R 755 /app/logs

# Environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENV SERVER_PORT=8080

# Add metadata
LABEL maintainer="CashClimb Team"
LABEL version="1.0.0"
LABEL description="CashClimb Financial Management Application"

EXPOSE 8080

# Run as non-root user for security
RUN groupadd -r cashclimb && \
    useradd -r -g cashclimb cashclimb
USER cashclimb

# Set the entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
