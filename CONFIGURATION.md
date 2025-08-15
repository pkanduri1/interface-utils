# Configuration Guide

This document provides comprehensive configuration guidance for the Database Script Watcher application, including the Archive Search API feature.

## Table of Contents

- [Environment Profiles](#environment-profiles)
- [Archive Search API Configuration](#archive-search-api-configuration)
- [Database Configuration](#database-configuration)
- [File Watcher Configuration](#file-watcher-configuration)
- [Security Configuration](#security-configuration)
- [Monitoring Configuration](#monitoring-configuration)
- [Deployment Examples](#deployment-examples)

## Environment Profiles

The application supports multiple environment profiles with different configurations:

### Development Profile (`dev`)

**Purpose**: Local development and testing
**Database**: H2 in-memory
**Archive Search API**: Enabled
**Logging**: Debug level

```yaml
spring:
  profiles:
    active: dev

archive:
  search:
    enabled: true
    allowed-paths:
      - "./dev-data/archive"
      - "./dev-data/sql-scripts"
    max-file-size: 52428800  # 50MB
    search-timeout-seconds: 15
```

### Test Profile (`test`)

**Purpose**: Automated testing and CI/CD
**Database**: H2 in-memory
**Archive Search API**: Enabled with restrictions
**Logging**: Minimal

```yaml
spring:
  profiles:
    active: test

archive:
  search:
    enabled: true
    max-file-size: 10485760  # 10MB
    max-search-results: 20
    search-timeout-seconds: 5
    audit-logging-enabled: false
```

### Production Profile (`prod`)

**Purpose**: Production deployment
**Database**: PostgreSQL
**Archive Search API**: **DISABLED**
**Logging**: INFO level

```yaml
spring:
  profiles:
    active: prod

archive:
  search:
    enabled: false  # CRITICAL: Never enable in production
```

## Archive Search API Configuration

### ⚠️ Security Notice

The Archive Search API is designed for development and testing environments only. It is automatically disabled in production for security reasons.

### Configuration Properties

| Property | Description | Default | Environment |
|----------|-------------|---------|-------------|
| `archive.search.enabled` | Enable/disable the API | `false` | All |
| `archive.search.allowed-paths` | List of allowed base paths | `[]` | Dev/Test |
| `archive.search.excluded-paths` | List of excluded paths | `[]` | Dev/Test |
| `archive.search.max-file-size` | Maximum file size in bytes | `104857600` | Dev/Test |
| `archive.search.max-search-results` | Maximum search results | `100` | Dev/Test |
| `archive.search.search-timeout-seconds` | Operation timeout | `30` | Dev/Test |
| `archive.search.max-concurrent-operations` | Concurrent operation limit | `5` | Dev/Test |
| `archive.search.audit-logging-enabled` | Enable audit logging | `true` | Dev/Test |
| `archive.search.max-directory-depth` | Maximum directory traversal depth | `10` | Dev/Test |

### Environment-Specific Configuration

#### Development Environment

```yaml
archive:
  search:
    enabled: true
    allowed-paths:
      - "./dev-data/archive"
      - "./dev-data/temp"
      - "./dev-data/sql-scripts"
      - "./dev-data/sqlloader-logs"
    excluded-paths:
      - "./config"
      - "./logs"
      - "./dev-data/sensitive"
    max-file-size: 52428800  # 50MB
    max-search-results: 50
    search-timeout-seconds: 15
    supported-archive-types:
      - zip
      - tar
      - tar.gz
      - jar
    max-concurrent-operations: 3
    audit-logging-enabled: true
    max-directory-depth: 5
```

#### Test Environment

```yaml
archive:
  search:
    enabled: true
    allowed-paths:
      - "./test-data/archive"
      - "./test-data/temp"
      - "./test-data/sql-scripts"
      - "./test-data/sqlloader-logs"
    excluded-paths:
      - "./config"
      - "./logs"
    max-file-size: 10485760  # 10MB
    max-search-results: 20
    search-timeout-seconds: 5
    supported-archive-types:
      - zip
      - tar
      - jar
    max-concurrent-operations: 2
    audit-logging-enabled: false
    max-directory-depth: 3
```

#### Production Environment

```yaml
archive:
  search:
    enabled: false  # NEVER enable in production
    # All other properties are ignored when disabled
    allowed-paths: []
    excluded-paths: []
    max-file-size: 0
    max-search-results: 0
    search-timeout-seconds: 0
    supported-archive-types: []
    max-concurrent-operations: 0
    audit-logging-enabled: false
    max-directory-depth: 0
```

### Environment Variables

Use these environment variables to override configuration:

```bash
# Archive Search API
export ARCHIVE_SEARCH_ENABLED=false
export ARCHIVE_ALLOWED_PATHS="./data/archive,./temp"
export ARCHIVE_MAX_FILE_SIZE=104857600
export ARCHIVE_SEARCH_TIMEOUT=30

# Environment Profile
export SPRING_PROFILES_ACTIVE=prod
```

## Database Configuration

### Development (H2)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: password
  
  h2:
    console:
      enabled: true
      path: /h2-console
```

### Production (PostgreSQL)

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/filewatcher}
    driver-class-name: ${DATABASE_DRIVER:org.postgresql.Driver}
    username: ${DATABASE_USERNAME:filewatcher}
    password: ${DATABASE_PASSWORD:changeme}
    hikari:
      maximum-pool-size: ${DATABASE_MAX_POOL_SIZE:20}
      minimum-idle: ${DATABASE_MIN_IDLE:5}
      connection-timeout: ${DATABASE_CONNECTION_TIMEOUT:30000}
```

## File Watcher Configuration

### Basic Configuration

```yaml
file-watcher:
  global:
    max-retry-attempts: 3
    retry-delay: 1000
  
  watch-configs:
    sql-scripts:
      name: "SQL Script Processor"
      processor-type: "sql-script"
      watch-folder: "./sql-scripts"
      completed-folder: "./sql-scripts/completed"
      error-folder: "./sql-scripts/error"
      file-patterns: ["*.sql"]
      polling-interval: 5000
      enabled: true
```

### Environment-Specific Paths

#### Development
```yaml
watch-configs:
  sql-scripts:
    watch-folder: "./dev-data/sql-scripts"
    polling-interval: 2000  # Faster for development
```

#### Production
```yaml
watch-configs:
  sql-scripts:
    watch-folder: ${SQL_WATCH_FOLDER:/opt/filewatcher/sql-scripts}
    polling-interval: ${SQL_POLLING_INTERVAL:10000}
```

## Security Configuration

### Archive Search API Security

The Archive Search API includes multiple security layers:

1. **Environment-Based Disabling**
   ```yaml
   # Automatically disabled in production
   spring:
     profiles:
       active: prod
   ```

2. **Path Traversal Protection**
   ```yaml
   archive:
     search:
       path-traversal-protection: true
       allowed-paths:
         - "./safe/directory"
       excluded-paths:
         - "./sensitive/data"
   ```

3. **File Type Validation**
   ```yaml
   archive:
     search:
       file-type-validation: true
       supported-archive-types:
         - zip
         - tar
         - jar
   ```

4. **Audit Logging**
   ```yaml
   archive:
     search:
       audit-logging-enabled: true
   
   logging:
     level:
       ARCHIVE_SEARCH_AUDIT: INFO
   ```

### Production Security Checklist

- [ ] `spring.profiles.active=prod`
- [ ] `archive.search.enabled=false`
- [ ] `springdoc.swagger-ui.enabled=false`
- [ ] Database credentials in environment variables
- [ ] File system permissions restricted
- [ ] Audit logging enabled

## Monitoring Configuration

### Metrics Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

### Archive Search Metrics

When enabled, the Archive Search API exposes these metrics:

- `archive_search_requests_total`
- `archive_search_duration_seconds`
- `archive_search_files_found_total`
- `archive_search_errors_total`

## Deployment Examples

### Docker Compose

```yaml
version: '3.8'
services:
  app:
    image: database-script-watcher:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - ARCHIVE_SEARCH_ENABLED=false
      - DATABASE_URL=jdbc:postgresql://db:5432/filewatcher
      - DATABASE_USERNAME=filewatcher
      - DATABASE_PASSWORD=secure_password
    ports:
      - "8080:8080"
    volumes:
      - ./data:/opt/filewatcher/data
    depends_on:
      - db
  
  db:
    image: postgres:13
    environment:
      - POSTGRES_DB=filewatcher
      - POSTGRES_USER=filewatcher
      - POSTGRES_PASSWORD=secure_password
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: database-script-watcher
spec:
  replicas: 1
  selector:
    matchLabels:
      app: database-script-watcher
  template:
    metadata:
      labels:
        app: database-script-watcher
    spec:
      containers:
      - name: app
        image: database-script-watcher:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: ARCHIVE_SEARCH_ENABLED
          value: "false"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        - name: DATABASE_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: username
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: password
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
```

### Systemd Service

```ini
[Unit]
Description=Database Script Watcher
After=network.target

[Service]
Type=simple
User=filewatcher
Group=filewatcher
WorkingDirectory=/opt/filewatcher
ExecStart=/usr/bin/java -jar /opt/filewatcher/database-script-watcher.jar
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=ARCHIVE_SEARCH_ENABLED=false
Environment=DATABASE_URL=jdbc:postgresql://localhost:5432/filewatcher
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

## Configuration Validation

### Startup Validation

The application validates configuration at startup:

1. **Environment Detection**: Automatically detects production environment
2. **Archive Search Validation**: Ensures API is disabled in production
3. **Path Validation**: Validates allowed/excluded paths exist
4. **Database Connectivity**: Tests database connection
5. **File System Access**: Validates watch folder permissions

### Health Checks

Monitor configuration health through actuator endpoints:

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Archive search health (dev/test only)
curl http://localhost:8080/actuator/health/archiveSearch
```

### Configuration Troubleshooting

Common configuration issues:

1. **Archive Search API not available**
   - Check environment profile: `spring.profiles.active`
   - Verify `archive.search.enabled=true` in dev/test
   - Ensure not running with production profile

2. **Path access denied**
   - Verify paths in `allowed-paths` configuration
   - Check file system permissions
   - Review excluded paths configuration

3. **Database connection issues**
   - Validate database URL and credentials
   - Check network connectivity
   - Review connection pool settings

## Best Practices

### Security Best Practices

1. **Never enable Archive Search API in production**
2. **Use environment variables for sensitive configuration**
3. **Implement proper file system permissions**
4. **Enable audit logging for security monitoring**
5. **Regularly review allowed/excluded paths**

### Performance Best Practices

1. **Tune connection pool settings for your workload**
2. **Adjust polling intervals based on file volume**
3. **Set appropriate file size limits**
4. **Monitor memory usage with large files**
5. **Use circuit breakers for resilience**

### Operational Best Practices

1. **Use profile-specific configuration files**
2. **Implement proper logging configuration**
3. **Set up monitoring and alerting**
4. **Document environment-specific settings**
5. **Test configuration changes in non-production first**