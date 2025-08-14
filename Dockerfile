# Multi-stage build for Interface Utils
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Install required packages
RUN apk add --no-cache \
    curl \
    bash \
    tzdata

# Create application user
RUN addgroup -g 1001 interfaceutils && \
    adduser -D -s /bin/bash -u 1001 -G interfaceutils interfaceutils

# Set working directory
WORKDIR /opt/interfaceutils

# Create required directories
RUN mkdir -p \
    /opt/interfaceutils/sql-scripts/completed \
    /opt/interfaceutils/sql-scripts/error \
    /opt/interfaceutils/sqlloader-logs/completed \
    /opt/interfaceutils/sqlloader-logs/error \
    /var/log/interface-utils \
    /opt/interfaceutils/config

# Copy the JAR file from builder stage
COPY --from=builder /app/target/interface-utils-*.jar app.jar

# Copy configuration files
COPY config/ ./config/

# Set ownership
RUN chown -R interfaceutils:interfaceutils /opt/interfaceutils /var/log/interface-utils

# Switch to non-root user
USER interfaceutils

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set JVM options
ENV JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Default environment
ENV SPRING_PROFILES_ACTIVE=prod

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]