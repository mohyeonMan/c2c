# C2C MVP Application Dockerfile
# Multi-stage build for optimized production image

# Build stage
FROM eclipse-temurin:21-jdk-jammy as builder

# Install build dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradle wrapper executable
RUN chmod +x ./gradlew

# Download dependencies (for better caching)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build application
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    dumb-init \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system c2c \
    && adduser --system --ingroup c2c c2c

# Set working directory
WORKDIR /app

# Copy application JAR from build stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership
RUN chown -R c2c:c2c /app

# Create logs directory
RUN mkdir -p /logs && chown c2c:c2c /logs

# Switch to non-root user
USER c2c

# Environment variables
ENV SPRING_PROFILES_ACTIVE=default
ENV JAVA_OPTS=""

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Labels for metadata
LABEL maintainer="C2C Team" \
      version="1.0.0" \
      description="C2C MVP WebSocket Chat Application" \
      org.opencontainers.image.title="C2C MVP" \
      org.opencontainers.image.description="Real-time WebSocket chat application with Redis and PostgreSQL" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="C2C Team"