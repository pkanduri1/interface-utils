# Configuration Guide

This document provides comprehensive configuration guidance for the Database Script Watcher application, including the Archive Search API feature.

## Table of Contents

- [Environment Profiles](#environment-profiles)
- [Archive Search API Configuration](#archive-search-api-configuration)
- [LDAP Authentication Configuration](#ldap-authentication-configuration)
- [File Upload Configuration](#file-upload-configuration)
- [Audit Logging Configuration](#audit-logging-configuration)
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

## LDAP Authentication Configuration

### Overview

The Archive Search API includes LDAP-based authentication for secure access to file operations. This feature is only available in non-production environments and integrates with Active Directory.

### LDAP Configuration Properties

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `archive.search.ldap.url` | LDAP server URL | `ldap://localhost:389` | Yes |
| `archive.search.ldap.base-dn` | Base Distinguished Name | `dc=company,dc=com` | Yes |
| `archive.search.ldap.user-search-base` | User search base | `ou=users` | Yes |
| `archive.search.ldap.user-search-filter` | User search filter | `(sAMAccountName={0})` | Yes |
| `archive.search.ldap.connection-timeout` | Connection timeout (ms) | `5000` | No |
| `archive.search.ldap.read-timeout` | Read timeout (ms) | `10000` | No |
| `archive.search.ldap.use-ssl` | Enable SSL/TLS | `false` | No |
| `archive.search.ldap.bind-dn` | Service account DN | `null` | No |
| `archive.search.ldap.bind-password` | Service account password | `null` | No |

### Environment-Specific LDAP Configuration

#### Development Environment

```yaml
archive:
  search:
    ldap:
      url: ldap://dev-ad-server.company.com:389
      base-dn: dc=dev,dc=company,dc=com
      user-search-base: ou=developers,ou=users
      user-search-filter: (sAMAccountName={0})
      connection-timeout: 3000
      read-timeout: 5000
      use-ssl: false
```

#### Test Environment

```yaml
archive:
  search:
    ldap:
      url: ldap://test-ad-server.company.com:389
      base-dn: dc=test,dc=company,dc=com
      user-search-base: ou=testers,ou=users
      user-search-filter: (sAMAccountName={0})
      connection-timeout: 5000
      read-timeout: 10000
      use-ssl: false
```

#### Staging Environment

```yaml
archive:
  search:
    ldap:
      url: ldaps://staging-ad-server.company.com:636
      base-dn: dc=staging,dc=company,dc=com
      user-search-base: ou=users
      user-search-filter: (sAMAccountName={0})
      connection-timeout: 5000
      read-timeout: 10000
      use-ssl: true
      bind-dn: cn=service-account,ou=service-accounts,dc=staging,dc=company,dc=com
      bind-password: ${LDAP_SERVICE_PASSWORD}
```

### LDAP Environment Variables

```bash
# LDAP Server Configuration
export LDAP_URL=ldap://your-ad-server.company.com:389
export LDAP_BASE_DN=dc=company,dc=com
export LDAP_USER_SEARCH_BASE=ou=users
export LDAP_USER_SEARCH_FILTER="(sAMAccountName={0})"

# LDAP Connection Settings
export LDAP_CONNECTION_TIMEOUT=5000
export LDAP_READ_TIMEOUT=10000
export LDAP_USE_SSL=false

# Service Account (optional)
export LDAP_BIND_DN=cn=service-account,ou=service-accounts,dc=company,dc=com
export LDAP_BIND_PASSWORD=secure_service_password
```

### LDAP Troubleshooting

Common LDAP configuration issues:

1. **Connection timeout**
   - Verify LDAP server URL and port
   - Check network connectivity
   - Increase connection timeout if needed

2. **Authentication failures**
   - Verify base DN and user search base
   - Check user search filter format
   - Ensure user exists in specified OU

3. **SSL/TLS issues**
   - Verify certificate trust store
   - Check SSL port (usually 636)
   - Validate certificate chain

## File Upload Configuration

### Overview

The Archive Search API supports secure file uploads to specified Linux server paths. This feature includes file type validation, size limits, and comprehensive audit logging.

### Upload Configuration Properties

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `archive.search.upload.upload-directory` | Target upload directory | `/opt/uploads` | Yes |
| `archive.search.upload.allowed-extensions` | Allowed file extensions | See below | Yes |
| `archive.search.upload.max-upload-size` | Maximum file size (bytes) | `104857600` | No |
| `archive.search.upload.temp-directory` | Temporary processing directory | `/tmp/file-uploads` | No |
| `archive.search.upload.max-concurrent-uploads` | Concurrent upload limit | `3` | No |
| `archive.search.upload.create-directories` | Auto-create target directories | `true` | No |
| `archive.search.upload.preserve-timestamps` | Preserve file timestamps | `true` | No |

### Default Allowed File Extensions

```yaml
archive:
  search:
    upload:
      allowed-extensions:
        - .txt
        - .sql
        - .xml
        - .json
        - .properties
        - .yml
        - .yaml
        - .conf
        - .cfg
        - .log
```

### Environment-Specific Upload Configuration

#### Development Environment

```yaml
archive:
  search:
    upload:
      upload-directory: /tmp/dev-uploads
      allowed-extensions:
        - .txt
        - .sql
        - .json
        - .yml
        - .properties
      max-upload-size: 52428800  # 50MB
      temp-directory: /tmp/dev-file-uploads
      max-concurrent-uploads: 2
      create-directories: true
      preserve-timestamps: true
```

#### Test Environment

```yaml
archive:
  search:
    upload:
      upload-directory: /tmp/test-uploads
      allowed-extensions:
        - .txt
        - .sql
        - .json
      max-upload-size: 10485760  # 10MB
      temp-directory: /tmp/test-file-uploads
      max-concurrent-uploads: 1
      create-directories: true
      preserve-timestamps: false
```

#### Staging Environment

```yaml
archive:
  search:
    upload:
      upload-directory: /opt/staging-uploads
      allowed-extensions:
        - .txt
        - .sql
        - .xml
        - .json
        - .properties
        - .yml
        - .yaml
        - .conf
      max-upload-size: 104857600  # 100MB
      temp-directory: /tmp/staging-file-uploads
      max-concurrent-uploads: 3
      create-directories: true
      preserve-timestamps: true
```

### Upload Environment Variables

```bash
# Upload Directory Configuration
export UPLOAD_DIRECTORY=/opt/uploads
export UPLOAD_TEMP_DIR=/tmp/file-uploads

# Upload Limits
export UPLOAD_MAX_SIZE=104857600
export UPLOAD_MAX_CONCURRENT=3

# File Extensions (space-separated)
export UPLOAD_EXT_1=.txt
export UPLOAD_EXT_2=.sql
export UPLOAD_EXT_3=.xml
export UPLOAD_EXT_4=.json
export UPLOAD_EXT_5=.properties

# Upload Options
export UPLOAD_CREATE_DIRS=true
export UPLOAD_PRESERVE_TIMESTAMPS=true
```

### Upload Security Considerations

1. **File Type Validation**: Only allowed extensions are accepted
2. **Size Limits**: Configurable maximum file size
3. **Path Validation**: Target paths are validated for security
4. **Virus Scanning**: Consider integrating with antivirus solutions
5. **Audit Logging**: All upload activities are logged

## Audit Logging Configuration

### Overview

The Archive Search API includes comprehensive audit logging for all user activities, including authentication, file operations, and security events.

### Audit Configuration Properties

| Property | Description | Default | Required |
|----------|-------------|---------|----------|
| `archive.search.audit.enabled` | Enable audit logging | `true` | No |
| `archive.search.audit.log-file` | Audit log file path | `./logs/archive-search-audit.log` | Yes |
| `archive.search.audit.max-file-size` | Maximum log file size | `10MB` | No |
| `archive.search.audit.max-history` | Number of rotated files to keep | `30` | No |
| `archive.search.audit.compress-rotated-files` | Compress rotated files | `true` | No |
| `archive.search.audit.log-pattern` | Log entry format pattern | See below | No |
| `archive.search.audit.log-to-console` | Also log to console | `false` | No |

### Default Log Pattern

```yaml
archive:
  search:
    audit:
      log-pattern: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### Environment-Specific Audit Configuration

#### Development Environment

```yaml
archive:
  search:
    audit:
      enabled: true
      log-file: ./logs/dev-archive-search-audit.log
      max-file-size: 5MB
      max-history: 10
      compress-rotated-files: false
      log-to-console: true
```

#### Test Environment

```yaml
archive:
  search:
    audit:
      enabled: false  # Disabled for faster test execution
      log-file: ./logs/test-archive-search-audit.log
      max-file-size: 1MB
      max-history: 5
      compress-rotated-files: false
      log-to-console: false
```

#### Staging Environment

```yaml
archive:
  search:
    audit:
      enabled: true
      log-file: /var/log/archive-search/staging-audit.log
      max-file-size: 10MB
      max-history: 30
      compress-rotated-files: true
      log-to-console: false
```

### Audit Environment Variables

```bash
# Audit Logging Configuration
export AUDIT_ENABLED=true
export AUDIT_LOG_FILE=./logs/archive-search-audit.log
export AUDIT_MAX_FILE_SIZE=10MB
export AUDIT_MAX_HISTORY=30
export AUDIT_COMPRESS_ROTATED=true
export AUDIT_LOG_TO_CONSOLE=false

# Log Pattern
export AUDIT_LOG_PATTERN="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

### Audit Log Format

The audit log includes the following information for each event:

```
2024-01-15 10:30:45.123 [http-nio-8080-exec-1] INFO  ARCHIVE_SEARCH_AUDIT - AUTHENTICATION_SUCCESS user=john.doe ip=192.168.1.100 timestamp=2024-01-15T10:30:45.123Z
2024-01-15 10:31:12.456 [http-nio-8080-exec-2] INFO  ARCHIVE_SEARCH_AUDIT - FILE_SEARCH user=john.doe query=*.sql results=5 duration=234ms timestamp=2024-01-15T10:31:12.456Z
2024-01-15 10:32:05.789 [http-nio-8080-exec-3] INFO  ARCHIVE_SEARCH_AUDIT - FILE_DOWNLOAD user=john.doe file=/data/scripts/update.sql size=1024 timestamp=2024-01-15T10:32:05.789Z
2024-01-15 10:33:18.012 [http-nio-8080-exec-4] INFO  ARCHIVE_SEARCH_AUDIT - FILE_UPLOAD user=john.doe file=config.properties target=/opt/uploads/config.properties size=2048 timestamp=2024-01-15T10:33:18.012Z
```

### Audit Event Types

The following events are logged:

1. **AUTHENTICATION_SUCCESS**: Successful user authentication
2. **AUTHENTICATION_FAILURE**: Failed authentication attempt
3. **FILE_SEARCH**: File search operation
4. **CONTENT_SEARCH**: Content search within files
5. **FILE_DOWNLOAD**: File download operation
6. **FILE_UPLOAD**: File upload operation
7. **SECURITY_VIOLATION**: Security policy violation
8. **RATE_LIMIT_EXCEEDED**: Rate limiting triggered
9. **SESSION_TIMEOUT**: User session timeout
10. **SYSTEM_ERROR**: System-level errors

### Audit Log Monitoring

Consider implementing log monitoring and alerting for:

- Multiple failed authentication attempts
- Unusual file access patterns
- Large file downloads/uploads
- Security violations
- System errors

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

#### Production Deployment

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
      - ./logs:/opt/filewatcher/logs
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

#### Development Deployment with Archive Search API

```yaml
version: '3.8'
services:
  app:
    image: database-script-watcher:latest
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - ARCHIVE_SEARCH_ENABLED=true
      - DATABASE_URL=jdbc:postgresql://db:5432/filewatcher
      - DATABASE_USERNAME=filewatcher
      - DATABASE_PASSWORD=secure_password
      # LDAP Configuration
      - LDAP_URL=ldap://your-ad-server.company.com:389
      - LDAP_BASE_DN=dc=company,dc=com
      - LDAP_USER_SEARCH_BASE=ou=users
      - LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})
      - LDAP_CONNECTION_TIMEOUT=5000
      - LDAP_READ_TIMEOUT=10000
      - LDAP_USE_SSL=false
      # Upload Configuration
      - UPLOAD_DIRECTORY=/opt/uploads
      - UPLOAD_MAX_SIZE=104857600
      - UPLOAD_TEMP_DIR=/tmp/file-uploads
      - UPLOAD_MAX_CONCURRENT=3
      # Audit Configuration
      - AUDIT_ENABLED=true
      - AUDIT_LOG_FILE=/opt/filewatcher/logs/archive-search-audit.log
      - AUDIT_MAX_FILE_SIZE=10MB
      - AUDIT_MAX_HISTORY=30
    ports:
      - "8080:8080"
    volumes:
      - ./data:/opt/filewatcher/data
      - ./logs:/opt/filewatcher/logs
      - ./uploads:/opt/uploads
      - ./temp:/tmp/file-uploads
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

#### Production Deployment

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
        volumeMounts:
        - name: logs-volume
          mountPath: /opt/filewatcher/logs
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
      volumes:
      - name: logs-volume
        persistentVolumeClaim:
          claimName: logs-pvc
```

#### Development Deployment with Archive Search API

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: database-script-watcher-dev
spec:
  replicas: 1
  selector:
    matchLabels:
      app: database-script-watcher-dev
  template:
    metadata:
      labels:
        app: database-script-watcher-dev
    spec:
      containers:
      - name: app
        image: database-script-watcher:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "dev"
        - name: ARCHIVE_SEARCH_ENABLED
          value: "true"
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
        # LDAP Configuration
        - name: LDAP_URL
          valueFrom:
            configMapKeyRef:
              name: ldap-config
              key: url
        - name: LDAP_BASE_DN
          valueFrom:
            configMapKeyRef:
              name: ldap-config
              key: base-dn
        - name: LDAP_USER_SEARCH_BASE
          valueFrom:
            configMapKeyRef:
              name: ldap-config
              key: user-search-base
        - name: LDAP_USER_SEARCH_FILTER
          valueFrom:
            configMapKeyRef:
              name: ldap-config
              key: user-search-filter
        - name: LDAP_BIND_DN
          valueFrom:
            secretKeyRef:
              name: ldap-secret
              key: bind-dn
        - name: LDAP_BIND_PASSWORD
          valueFrom:
            secretKeyRef:
              name: ldap-secret
              key: bind-password
        # Upload Configuration
        - name: UPLOAD_DIRECTORY
          value: "/opt/uploads"
        - name: UPLOAD_MAX_SIZE
          value: "104857600"
        - name: UPLOAD_TEMP_DIR
          value: "/tmp/file-uploads"
        # Audit Configuration
        - name: AUDIT_ENABLED
          value: "true"
        - name: AUDIT_LOG_FILE
          value: "/opt/filewatcher/logs/archive-search-audit.log"
        volumeMounts:
        - name: logs-volume
          mountPath: /opt/filewatcher/logs
        - name: uploads-volume
          mountPath: /opt/uploads
        - name: temp-volume
          mountPath: /tmp/file-uploads
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
      volumes:
      - name: logs-volume
        persistentVolumeClaim:
          claimName: logs-pvc
      - name: uploads-volume
        persistentVolumeClaim:
          claimName: uploads-pvc
      - name: temp-volume
        emptyDir: {}

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: ldap-config
data:
  url: "ldap://dev-ad-server.company.com:389"
  base-dn: "dc=dev,dc=company,dc=com"
  user-search-base: "ou=users"
  user-search-filter: "(sAMAccountName={0})"

---
apiVersion: v1
kind: Secret
metadata:
  name: ldap-secret
type: Opaque
data:
  bind-dn: Y249c2VydmljZS1hY2NvdW50LG91PXNlcnZpY2UtYWNjb3VudHMsZGM9ZGV2LGRjPWNvbXBhbnksZGM9Y29t  # base64 encoded
  bind-password: c2VjdXJlX3Bhc3N3b3Jk  # base64 encoded
```

### Systemd Service

#### Production Service

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
Environment=DATABASE_USERNAME=filewatcher
Environment=DATABASE_PASSWORD=secure_password
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

#### Development Service with Archive Search API

```ini
[Unit]
Description=Database Script Watcher (Development)
After=network.target

[Service]
Type=simple
User=filewatcher
Group=filewatcher
WorkingDirectory=/opt/filewatcher
ExecStart=/usr/bin/java -jar /opt/filewatcher/database-script-watcher.jar
Environment=SPRING_PROFILES_ACTIVE=dev
Environment=ARCHIVE_SEARCH_ENABLED=true
Environment=DATABASE_URL=jdbc:postgresql://localhost:5432/filewatcher
Environment=DATABASE_USERNAME=filewatcher
Environment=DATABASE_PASSWORD=secure_password
# LDAP Configuration
Environment=LDAP_URL=ldap://dev-ad-server.company.com:389
Environment=LDAP_BASE_DN=dc=dev,dc=company,dc=com
Environment=LDAP_USER_SEARCH_BASE=ou=users
Environment=LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})
Environment=LDAP_CONNECTION_TIMEOUT=5000
Environment=LDAP_READ_TIMEOUT=10000
# Upload Configuration
Environment=UPLOAD_DIRECTORY=/opt/dev-uploads
Environment=UPLOAD_MAX_SIZE=104857600
Environment=UPLOAD_TEMP_DIR=/tmp/dev-file-uploads
# Audit Configuration
Environment=AUDIT_ENABLED=true
Environment=AUDIT_LOG_FILE=/var/log/filewatcher/dev-archive-search-audit.log
Environment=AUDIT_MAX_FILE_SIZE=10MB
Environment=AUDIT_MAX_HISTORY=30
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

## New Requirements and Dependencies

### LDAP Authentication Requirements

When enabling the Archive Search API with LDAP authentication, ensure the following requirements are met:

#### Network Requirements
- Network connectivity to LDAP/Active Directory server
- Firewall rules allowing LDAP traffic (port 389 for LDAP, 636 for LDAPS)
- DNS resolution for LDAP server hostname

#### LDAP Server Requirements
- Active Directory or compatible LDAP server
- Service account with read permissions (optional but recommended)
- User accounts in the specified organizational unit
- Proper LDAP schema with sAMAccountName attribute

#### Application Dependencies
- Spring LDAP dependencies (included in pom.xml)
- SSL certificates for LDAPS connections (if using SSL)

### File Upload Requirements

When enabling file upload functionality, ensure the following requirements are met:

#### File System Requirements
- Write permissions to upload directory
- Sufficient disk space for uploaded files
- Write permissions to temporary directory
- Proper file system permissions for the application user

#### Security Requirements
- File type validation enabled
- Virus scanning integration (recommended)
- Regular cleanup of temporary files
- Monitoring of upload directory size

#### Network Requirements
- Sufficient bandwidth for file transfers
- Connection to target Linux servers
- SSH/SCP access for file transfers (if using remote uploads)

### Audit Logging Requirements

When enabling audit logging, ensure the following requirements are met:

#### File System Requirements
- Write permissions to log directory
- Sufficient disk space for log files
- Log rotation configuration
- Backup strategy for audit logs

#### Compliance Requirements
- Log retention policies
- Log integrity protection
- Access controls for audit logs
- Regular log review processes

#### Monitoring Requirements
- Log monitoring and alerting
- Disk space monitoring for log directory
- Log parsing and analysis tools
- Integration with SIEM systems (if applicable)

### Environment-Specific Setup

#### Development Environment Setup

1. **LDAP Setup**
   ```bash
   # Test LDAP connectivity
   ldapsearch -x -H ldap://dev-ad-server.company.com:389 -D "cn=test-user,ou=users,dc=dev,dc=company,dc=com" -W -b "dc=dev,dc=company,dc=com" "(sAMAccountName=testuser)"
   ```

2. **Directory Setup**
   ```bash
   # Create required directories
   sudo mkdir -p /tmp/dev-uploads
   sudo mkdir -p /tmp/dev-file-uploads
   sudo mkdir -p ./logs
   
   # Set permissions
   sudo chown -R filewatcher:filewatcher /tmp/dev-uploads
   sudo chown -R filewatcher:filewatcher /tmp/dev-file-uploads
   sudo chown -R filewatcher:filewatcher ./logs
   ```

3. **Configuration Validation**
   ```bash
   # Validate configuration
   java -jar database-script-watcher.jar --spring.profiles.active=dev --validate-config-only
   ```

#### Production Environment Setup

1. **Security Hardening**
   ```bash
   # Disable Archive Search API
   export ARCHIVE_SEARCH_ENABLED=false
   export SPRING_PROFILES_ACTIVE=prod
   
   # Verify API is disabled
   curl -f http://localhost:8080/api/v1/archive/search || echo "API correctly disabled"
   ```

2. **Monitoring Setup**
   ```bash
   # Set up log monitoring
   tail -f /var/log/filewatcher/application.log | grep -i "archive.search"
   
   # Monitor for any archive search activity (should be none)
   grep -i "archive.search" /var/log/filewatcher/application.log
   ```

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