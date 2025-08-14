# Interface Utils

A comprehensive Spring Boot service that provides automated interface utilities including file processing, database operations, and system integrations. The service monitors multiple configured folders, processes files through appropriate processors, and provides a foundation for various interface automation tasks.

## Features

- **Multi-folder Monitoring**: Monitor multiple directories simultaneously with different configurations
- **Pluggable Processors**: Support for SQL script execution and SQL*Loader log processing
- **Transaction Safety**: Proper transaction management for database operations
- **Error Handling**: Comprehensive error handling with retry mechanisms and circuit breakers
- **Monitoring & Observability**: Built-in health checks, metrics, and structured logging
- **REST API**: Control and monitoring endpoints
- **Hot Configuration Reload**: Update configurations without service restart

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Database (PostgreSQL recommended for production, H2 for development)

### Running the Application

1. **Clone and build the project:**
   ```bash
   git clone <repository-url>
   cd database-script-watcher
   mvn clean install
   ```

2. **Run with default configuration (H2 in-memory database):**
   ```bash
   mvn spring-boot:run
   ```

3. **Run with specific profile:**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   # or
   java -jar target/database-script-watcher-*.jar --spring.profiles.active=prod
   ```

4. **Access the application:**
   - Application: http://localhost:8080
   - Health Check: http://localhost:8080/actuator/health
   - Metrics: http://localhost:8080/actuator/metrics
   - H2 Console (dev only): http://localhost:8080/h2-console

## Configuration

### Environment Profiles

The application supports multiple environment profiles:

- **default**: Basic configuration with H2 database
- **dev**: Development environment with debug logging
- **test**: Test environment with fast polling
- **prod**: Production environment with PostgreSQL

### Configuration Files

Configuration files are located in the `config/` directory:

- `application-dev.yml`: Development environment
- `application-test.yml`: Test environment  
- `application-prod.yml`: Production environment

### Key Configuration Properties

#### Database Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/filewatcher
    username: filewatcher
    password: changeme
    driver-class-name: org.postgresql.Driver
```

#### File Watcher Configuration

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

### Environment Variables

For production deployment, use environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | Database connection URL | `jdbc:h2:mem:testdb` |
| `DATABASE_USERNAME` | Database username | `sa` |
| `DATABASE_PASSWORD` | Database password | `password` |
| `SQL_WATCH_FOLDER` | SQL scripts watch folder | `./sql-scripts` |
| `LOG_LEVEL_APP` | Application log level | `INFO` |
| `SERVER_PORT` | Server port | `8080` |

## File Processors

### SQL Script Processor

Processes SQL files and executes them against the configured database.

**Features:**
- Multi-statement SQL file support
- Transaction management (DDL and DML)
- SQL validation and parsing
- Error handling with detailed logging

**Supported File Types:** `*.sql`

**Configuration:**
```yaml
sql-scripts:
  processor-type: "sql-script"
  watch-folder: "./sql-scripts"
  file-patterns: ["*.sql"]
  enabled: true
```

### SQL*Loader Log Processor

Processes SQL*Loader log files and extracts audit information.

**Features:**
- Log file parsing and validation
- Audit information extraction
- Database audit logging
- Support for multiple log formats

**Supported File Types:** `*.log`, `*.ctl`

**Configuration:**
```yaml
sqlloader-logs:
  processor-type: "sqlloader-log"
  watch-folder: "./sqlloader-logs"
  file-patterns: ["*.log", "*.ctl"]
  enabled: false
```

## API Endpoints

### Health and Monitoring

- `GET /actuator/health` - Application health status
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/info` - Application information

### File Watcher Control

- `GET /api/filewatcher/status` - Get service status
- `POST /api/filewatcher/pause/{configName}` - Pause specific watcher
- `POST /api/filewatcher/resume/{configName}` - Resume specific watcher
- `GET /api/filewatcher/history/{configName}` - Get processing history
- `GET /api/filewatcher/statistics` - Get processing statistics

### Monitoring

- `GET /api/monitoring/metrics` - Detailed metrics
- `GET /api/monitoring/health` - Comprehensive health check

## Directory Structure

The application expects the following directory structure:

```
/opt/filewatcher/
├── sql-scripts/
│   ├── completed/
│   └── error/
├── sqlloader-logs/
│   ├── completed/
│   └── error/
└── logs/
```

Directories are created automatically if they don't exist.

## File Processing Workflow

1. **Detection**: Files are detected in watched folders based on configured patterns
2. **Processing**: Files are routed to appropriate processors
3. **Execution**: Processors execute their specific logic (SQL execution, log parsing, etc.)
4. **Completion**: Successfully processed files are moved to `completed/` folder
5. **Error Handling**: Failed files are moved to `error/` folder with error details

## Logging

The application uses structured logging with correlation IDs for traceability.

**Log Levels:**
- `ERROR`: Critical errors requiring immediate attention
- `WARN`: Warning conditions that should be monitored
- `INFO`: General information about application flow
- `DEBUG`: Detailed debugging information

**Log Format:**
```
2024-01-15 10:30:45 [main] INFO [corr-123] [script.sql] [sql-script] c.f.w.service.FileWatcherService - Processing file: script.sql
```

## Monitoring and Alerting

### Metrics

The application exposes Prometheus-compatible metrics:

- `file_watcher_files_processed_total` - Total files processed
- `file_watcher_processing_duration_seconds` - Processing duration
- `file_watcher_errors_total` - Total processing errors
- `database_connections_active` - Active database connections

### Health Checks

- **Database Health**: Checks database connectivity
- **File System Health**: Checks watched folder accessibility
- **Circuit Breaker Health**: Monitors circuit breaker states

## Error Handling

### Circuit Breakers

The application uses circuit breakers to handle failures gracefully:

- **Database Circuit Breaker**: Protects against database failures
- **File System Circuit Breaker**: Handles file system issues
- **External Circuit Breaker**: Manages external service failures

### Retry Mechanisms

- Exponential backoff for transient failures
- Configurable retry attempts and delays
- Different retry strategies for different error types

## Development

### Running Tests

```bash
# Run all tests
mvn test

# Run integration tests only
mvn test -Dtest="*IntegrationTest"

# Run with specific profile
mvn test -Dspring.profiles.active=test
```

### Building

```bash
# Build JAR
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Build Docker image
mvn clean package
docker build -t database-script-watcher .
```

### Adding New Processors

1. Implement the `FileProcessor` interface
2. Register the processor in `FileProcessorRegistryImpl`
3. Add configuration properties
4. Create unit and integration tests

Example:
```java
@Component
public class CustomProcessor implements FileProcessor {
    @Override
    public boolean supports(WatchConfig config) {
        return "custom-processor".equals(config.getProcessorType());
    }
    
    @Override
    public ProcessingResult processFile(File file, WatchConfig config) {
        // Implementation
    }
}
```

## Troubleshooting

### Common Issues

1. **Files not being processed**
   - Check folder permissions
   - Verify file patterns match
   - Check if watcher is enabled
   - Review logs for errors

2. **Database connection errors**
   - Verify database is running
   - Check connection parameters
   - Review circuit breaker status

3. **High memory usage**
   - Check for large files being processed
   - Review connection pool settings
   - Monitor garbage collection

### Debug Mode

Enable debug logging for troubleshooting:

```yaml
logging:
  level:
    com.fabric.watcher: DEBUG
```

### Log Analysis

Key log patterns to monitor:

- `Processing file:` - File processing start
- `File processed successfully:` - Successful completion
- `Error processing file:` - Processing errors
- `Circuit breaker opened:` - Circuit breaker activation

## Security Considerations

- **File System Access**: Restrict service account permissions
- **Database Credentials**: Use encrypted passwords in production
- **API Security**: Implement authentication for control endpoints
- **Audit Logging**: All SQL executions are logged for security auditing

## Performance Tuning

### Database Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### File Processing

- Adjust polling intervals based on expected file volume
- Use appropriate file patterns to reduce scanning overhead
- Monitor processing duration metrics

### JVM Tuning

```bash
java -Xms512m -Xmx2g -XX:+UseG1GC -jar database-script-watcher.jar
```

## Support

For issues and questions:

1. Check the logs for error messages
2. Review configuration settings
3. Consult the troubleshooting section
4. Check health endpoints for system status

## License

[Add your license information here]