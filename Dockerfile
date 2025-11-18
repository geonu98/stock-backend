# Step 1: Build stage (Gradle)
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app
COPY ./backend ./
RUN gradle clean bootJar --no-daemon

# Step 2: Run stage
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
