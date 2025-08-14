# Database Script Watcher - Deployment Guide

This guide provides comprehensive instructions for deploying the Database Script Watcher service in different environments.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Configuration](#configuration)
4. [Database Setup](#database-setup)
5. [Deployment Methods](#deployment-methods)
6. [Post-Deployment Verification](#post-deployment-verification)
7. [Monitoring Setup](#monitoring-setup)
8. [Troubleshooting](#troubleshooting)

## Prerequisites

### System Requirements

- **Java**: OpenJDK 17 or higher
- **Maven**: 3.6 or higher (for building from source)
- **Docker**: 20.10 or higher (for containerized deployment)
- **Docker Compose**: 2.0 or higher
- **Database**: PostgreSQL 12+ (recommended) or H2 (development only)

### Hardware Requirements

| Environment | CPU | Memory | Storage |
|-------------|-----|--------|---------|
| Development | 2 cores | 4GB RAM | 10GB |
| Test | 2 cores | 4GB RAM | 20GB |
| Production | 4+ cores | 8GB+ RAM | 100GB+ |

## Environment Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd database-script-watcher
```

### 2. Create Environment File

```bash
cp .env.example .env
# Edit .env with your specific configuration
```

### 3. Create Required Directories

```bash
mkdir -p data/sql-scripts/{completed,error}
mkdir -p data/sqlloader-logs/{completed,error}
mkdir -p logs
```

## Configuration

### Environment Profiles

The application supports multiple profiles:

- **dev**: Development environment with H2 database
- **test**: Test environment with fast polling
- **prod**: Production environment with PostgreSQL

### Configuration Files

| File | Purpose |
|------|---------|
| `config/application-dev.yml` | Development configuration |
| `config/application-test.yml` | Test configuration |
| `config/application-prod.yml` | Production configuration |

### Key Configuration Properties

#### Database Configuration

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: ${DATABASE_DRIVER}
```

#### File Watcher Configuration

```yaml
file-watcher:
  watch-configs:
    sql-scripts:
      watch-folder: ${SQL_WATCH_FOLDER}
      polling-interval: ${SQL_POLLING_INTERVAL}
      enabled: ${SQL_PROCESSOR_ENABLED}
```

## Database Setup

### PostgreSQL Setup

1. **Install PostgreSQL**:
   ```bash
   # Ubuntu/Debian
   sudo apt-get install postgresql postgresql-contrib
   
   # CentOS/RHEL
   sudo yum install postgresql-server postgresql-contrib
   
   # macOS
   brew install postgresql
   ```

2. **Create Database and User**:
   ```sql
   CREATE DATABASE filewatcher;
   CREATE USER filewatcher WITH PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE filewatcher TO filewatcher;
   ```

3. **Run Migrations**:
   ```bash
   # Using the provided SQL scripts
   psql -U filewatcher -d filewatcher -f database/init/01-create-database.sql
   ```

### H2 Database (Development Only)

For development, H2 is configured automatically. Access the console at:
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: `password`

## Deployment Methods

### Method 1: Docker Compose (Recommended)

1. **Build and Deploy**:
   ```bash
   ./scripts/deploy.sh latest prod
   ```

2. **Manual Docker Compose**:
   ```bash
   # Build the application
   mvn clean package -DskipTests
   
   # Start services
   docker-compose up -d
   ```

3. **Check Status**:
   ```bash
   docker-compose ps
   docker-compose logs file-watcher
   ```

### Method 2: Standalone JAR

1. **Build Application**:
   ```bash
   mvn clean package -DskipTests
   ```

2. **Run Application**:
   ```bash
   java -jar target/database-script-watcher-*.jar \
     --spring.profiles.active=prod \
     --spring.config.location=config/application-prod.yml
   ```

### Method 3: Docker Only

1. **Build Docker Image**:
   ```bash
   docker build -t database-script-watcher:latest .
   ```

2. **Run Container**:
   ```bash
   docker run -d \
     --name database-script-watcher \
     -p 8080:8080 \
     -v $(pwd)/data:/opt/filewatcher \
     -v $(pwd)/logs:/var/log/file-watcher \
     -e SPRING_PROFILES_ACTIVE=prod \
     -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/filewatcher \
     database-script-watcher:latest
   ```

### Method 4: Kubernetes

1. **Create Namespace**:
   ```bash
   kubectl create namespace file-watcher
   ```

2. **Apply Configurations**:
   ```bash
   kubectl apply -f k8s/configmap.yaml
   kubectl apply -f k8s/secret.yaml
   kubectl apply -f k8s/deployment.yaml
   kubectl apply -f k8s/service.yaml
   ```

## Post-Deployment Verification

### 1. Health Checks

```bash
# Run comprehensive health check
./scripts/health-check.sh --verbose --docker --filesystem

# Basic health check
curl http://localhost:8080/actuator/health
```

### 2. API Verification

```bash
# Check file watcher status
curl http://localhost:8080/api/filewatcher/status

# Check processing statistics
curl http://localhost:8080/api/filewatcher/statistics

# Check metrics
curl http://localhost:8080/actuator/metrics
```

### 3. Test File Processing

1. **Create Test SQL File**:
   ```bash
   echo "SELECT 1 as test_query;" > data/sql-scripts/test.sql
   ```

2. **Monitor Processing**:
   ```bash
   # Watch logs
   tail -f logs/file-watcher.log
   
   # Check completed folder
   ls -la data/sql-scripts/completed/
   ```

## Monitoring Setup

### Prometheus and Grafana

1. **Start Monitoring Stack**:
   ```bash
   docker-compose up -d prometheus grafana
   ```

2. **Access Dashboards**:
   - Grafana: http://localhost:3000 (admin/admin123)
   - Prometheus: http://localhost:9090

3. **Import Dashboard**:
   - Use the provided dashboard JSON in `monitoring/grafana/dashboards/`

### Custom Alerts

1. **Configure Alertmanager**:
   ```yaml
   # alertmanager.yml
   global:
     smtp_smarthost: 'localhost:587'
     smtp_from: 'alerts@yourcompany.com'
   
   route:
     group_by: ['alertname']
     group_wait: 10s
     group_interval: 10s
     repeat_interval: 1h
     receiver: 'web.hook'
   
   receivers:
   - name: 'web.hook'
     email_configs:
     - to: 'admin@yourcompany.com'
       subject: 'File Watcher Alert'
   ```

## Environment-Specific Deployment

### Development Environment

```bash
# Quick start for development
./scripts/deploy.sh latest dev

# Or manually
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Test Environment

```bash
# Deploy to test environment
./scripts/deploy.sh latest test

# Run integration tests
mvn test -Dspring.profiles.active=test
```

### Production Environment

```bash
# Set production environment variables
export DATABASE_URL="jdbc:postgresql://prod-db:5432/filewatcher"
export DATABASE_USERNAME="filewatcher"
export DATABASE_PASSWORD="secure_password"

# Deploy to production
./scripts/deploy.sh 1.0.0 prod
```

## Security Considerations

### 1. Database Security

- Use strong passwords
- Enable SSL/TLS connections
- Restrict database access to application servers only
- Regular security updates

### 2. Application Security

- Store sensitive configuration in environment variables
- Use encrypted passwords where possible
- Implement proper file system permissions
- Regular security scanning

### 3. Network Security

- Use firewalls to restrict access
- Enable HTTPS in production
- Implement proper authentication for management endpoints

## Backup and Recovery

### 1. Database Backup

```bash
# Create backup
pg_dump -U filewatcher -h localhost filewatcher > backup_$(date +%Y%m%d_%H%M%S).sql

# Restore backup
psql -U filewatcher -h localhost filewatcher < backup_20240115_120000.sql
```

### 2. Configuration Backup

```bash
# Backup configuration
tar -czf config_backup_$(date +%Y%m%d).tar.gz config/ .env
```

### 3. Data Backup

```bash
# Backup processed files
tar -czf data_backup_$(date +%Y%m%d).tar.gz data/
```

## Troubleshooting

### Common Issues

1. **Application Won't Start**
   ```bash
   # Check logs
   docker-compose logs file-watcher
   
   # Check configuration
   ./scripts/health-check.sh --basic
   ```

2. **Database Connection Issues**
   ```bash
   # Test database connectivity
   psql -U filewatcher -h localhost -d filewatcher -c "SELECT 1;"
   
   # Check connection pool
   curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
   ```

3. **Files Not Processing**
   ```bash
   # Check folder permissions
   ls -la data/sql-scripts/
   
   # Check watcher status
   curl http://localhost:8080/api/filewatcher/status
   ```

4. **High Memory Usage**
   ```bash
   # Check JVM metrics
   curl http://localhost:8080/actuator/metrics/jvm.memory.used
   
   # Adjust JVM settings
   export JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"
   ```

### Log Analysis

```bash
# Search for errors
grep -i error logs/file-watcher.log

# Monitor processing
grep "Processing file" logs/file-watcher.log | tail -10

# Check circuit breaker status
grep "Circuit breaker" logs/file-watcher.log
```

### Performance Tuning

1. **Database Optimization**:
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
         minimum-idle: 5
   ```

2. **File Processing Optimization**:
   ```yaml
   file-watcher:
     watch-configs:
       sql-scripts:
         polling-interval: 5000  # Adjust based on load
   ```

3. **JVM Tuning**:
   ```bash
   export JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
   ```

## Maintenance

### Regular Tasks

1. **Log Rotation**:
   ```bash
   # Setup logrotate
   sudo cp scripts/logrotate.conf /etc/logrotate.d/file-watcher
   ```

2. **Database Maintenance**:
   ```sql
   -- Clean old processing history (run monthly)
   SELECT cleanup_processing_history(90);
   
   -- Analyze tables
   ANALYZE processing_history;
   ANALYZE processing_statistics;
   ```

3. **Health Monitoring**:
   ```bash
   # Setup cron job for health checks
   0 */6 * * * /opt/filewatcher/scripts/health-check.sh --basic
   ```

## Support and Documentation

- **Application Logs**: `/var/log/file-watcher/file-watcher.log`
- **Health Endpoint**: `http://localhost:8080/actuator/health`
- **Metrics Endpoint**: `http://localhost:8080/actuator/metrics`
- **API Documentation**: Available at runtime via Spring Boot Actuator

For additional support, check the main README.md file and application logs.