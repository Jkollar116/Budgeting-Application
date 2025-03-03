FROM amazoncorretto:11

WORKDIR /app

COPY target/firebase-login-app-1.0.0.jar /app/app.jar

# Port the application will run on
EXPOSE 8000

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
