# syntax=docker/dockerfile:1

# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace/app

# Keep the Gradle home in a fixed place so it can be cached by BuildKit
ENV GRADLE_USER_HOME=/gradle

# Copy gradle wrapper and build files first: this layer only changes when the
# build configuration changes, so dependency resolution stays cached
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew

# Warm the dependency cache (non-fatal: the real build resolves what is missing)
RUN --mount=type=cache,target=/gradle \
    ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src src

# Build the executable jar (tests are run in CI, not during the image build)
RUN --mount=type=cache,target=/gradle \
    ./gradlew bootJar --no-daemon \
    && cp build/libs/*.jar /workspace/app.jar

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as an unprivileged user
RUN addgroup -S app && adduser -S -G app app

# Copy the built JAR from the builder stage
COPY --from=builder --chown=app:app /workspace/app.jar app.jar

USER app

# Expose the default Spring Boot port (web UI / endpoints)
EXPOSE 8080

# Let the JVM size its heap from the container memory limit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
