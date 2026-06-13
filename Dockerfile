# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace/app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x ./gradlew

# Download dependencies (cached if dependencies don't change)
RUN ./gradlew dependencies --no-daemon || return 0

# Copy source code
COPY src src

# Build the application (skip tests for faster docker build, tests should be run in CI)
RUN ./gradlew build -x test --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /workspace/app/build/libs/*.jar app.jar

# Expose the default Spring Boot port (if web endpoints are used)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
