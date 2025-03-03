FROM amazoncorretto:11

WORKDIR /app

# Copy the application jar
COPY target/firebase-login-app-1.0.0.jar /app/app.jar

# Copy resources if they're not in the JAR
COPY src/main/resources /app/src/main/resources

# Expose the port
EXPOSE 8000

# Run the application
CMD ["java", "-jar", "app.jar"]
