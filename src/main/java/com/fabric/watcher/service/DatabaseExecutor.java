package com.fabric.watcher.service;

import com.fabric.watcher.error.ErrorCategory;
import com.fabric.watcher.error.ErrorHandler;
import com.fabric.watcher.resilience.CircuitBreakerService;
import com.fabric.watcher.resilience.GracefulDegradationService;
import com.fabric.watcher.resilience.RetryService;
import com.fabric.watcher.util.CorrelationIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Service for executing SQL scripts against the database.
 * Handles transaction management, retry logic, and error handling.
 */
@Service
public class DatabaseExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseExecutor.class);
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    
    // SQL statement type patterns
    private static final Pattern DDL_PATTERN = Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP|TRUNCATE)\\s+", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private static final Pattern DML_PATTERN = Pattern.compile(
        "^\\s*(INSERT|UPDATE|DELETE|MERGE)\\s+", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    @Autowired(required = false)
    private MetricsService metricsService;
    
    @Autowired
    private ErrorHandler errorHandler;
    
    @Autowired
    private CircuitBreakerService circuitBreakerService;
    
    @Autowired
    private RetryService retryService;
    
    @Autowired
    private GracefulDegradationService gracefulDegradationService;
    
    /**
     * Execute a SQL script file with proper transaction management.
     * 
     * @param sqlFile the SQL file to execute
     * @param statements the parsed SQL statements
     * @return execution result with details
     */
    public SqlExecutionResult executeScript(File sqlFile, List<String> statements) {
        logger.info("Executing SQL script: {} with {} statements", sqlFile.getName(), statements.size());
        
        long startTime = System.currentTimeMillis();
        SqlExecutionResult result = new SqlExecutionResult(sqlFile.getName());
        
        // Check if database is available through circuit breaker
        if (!circuitBreakerService.isDatabaseAvailable()) {
            logger.warn("Database circuit breaker is open, cannot execute SQL script: {}", sqlFile.getName());
            result.setSuccess(false);
            result.setErrorMessage("Database unavailable - circuit breaker is open");
            gracefulDegradationService.enterDegradedMode("database", "Circuit breaker open during SQL execution");
            return result;
        }
        
        try {
            // Execute with circuit breaker protection
            final SqlExecutionResult finalResult = result; // Make effectively final for lambda
            result = circuitBreakerService.executeDatabaseOperation(
                () -> executeScriptInternal(statements, finalResult),
                () -> {
                    // Fallback: mark as failed and enter degraded mode
                    SqlExecutionResult fallbackResult = new SqlExecutionResult(sqlFile.getName());
                    fallbackResult.setSuccess(false);
                    fallbackResult.setErrorMessage("Database operation failed - using fallback");
                    gracefulDegradationService.enterDegradedMode("database", "Database operation fallback triggered");
                    return fallbackResult;
                }
            );
            
            if (result.isSuccess()) {
                logger.info("Successfully executed SQL script: {} in {}ms", 
                           sqlFile.getName(), result.getExecutionTimeMs());
                // Exit degraded mode if we were in it
                gracefulDegradationService.exitDegradedMode("database");
            } else {
                logger.error("Failed to execute SQL script: {} - {}", 
                           sqlFile.getName(), result.getErrorMessage());
            }
            
        } catch (Exception e) {
            // Handle error with comprehensive error handling
            ErrorHandler.ErrorHandlingResult errorResult = errorHandler.handleError(
                "DatabaseExecutor.executeScript", e, "SQL script execution");
            
            logger.error("Error executing SQL script: {} - Category: {}, Strategy: {}", 
                       sqlFile.getName(), errorResult.getCategory(), errorResult.getStrategy());
            
            result.setSuccess(false);
            result.setErrorMessage(errorResult.getMessage());
            result.setException(e);
            
            // Enter degraded mode for database errors
            if (errorResult.getCategory() == ErrorCategory.DATABASE) {
                gracefulDegradationService.enterDegradedMode("database", errorResult.getMessage());
            }
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(duration);
            
            // Record SQL execution time
            if (metricsService != null) {
                metricsService.recordSqlExecutionTime(java.time.Duration.ofMillis(duration));
            }
        }
        
        return result;
    }
    
    /**
     * Internal method to execute SQL script with proper error handling.
     */
    private SqlExecutionResult executeScriptInternal(List<String> statements, SqlExecutionResult result) {
        // Categorize statements
        boolean hasDDL = statements.stream().anyMatch(stmt -> DDL_PATTERN.matcher(stmt).find());
        boolean hasDML = statements.stream().anyMatch(stmt -> DML_PATTERN.matcher(stmt).find());
        
        if (hasDDL && hasDML) {
            // Mixed statements - execute separately
            return executeMixedStatements(statements, result);
        } else if (hasDDL) {
            // DDL only - execute without transaction
            return executeDDLStatements(statements, result);
        } else {
            // DML only or other - execute in transaction
            return executeDMLStatements(statements, result);
        }
    }
    
    /**
     * Execute DDL statements without transaction management.
     * DDL statements typically auto-commit and cannot be rolled back.
     */
    private SqlExecutionResult executeDDLStatements(List<String> statements, SqlExecutionResult result) {
        logger.debug("Executing {} DDL statements", statements.size());
        
        for (int i = 0; i < statements.size(); i++) {
            String statement = statements.get(i);
            try {
                executeDDL(statement);
                result.incrementSuccessfulStatements();
                logger.debug("Executed DDL statement {}/{}: {}", i + 1, statements.size(), 
                           truncateStatement(statement));
            } catch (Exception e) {
                logger.error("Failed to execute DDL statement {}/{}: {}", i + 1, statements.size(), 
                           truncateStatement(statement), e);
                result.setSuccess(false);
                result.setErrorMessage("DDL execution failed at statement " + (i + 1) + ": " + e.getMessage());
                result.setFailedStatement(statement);
                result.setException(e);
                return result;
            }
        }
        
        result.setSuccess(true);
        return result;
    }
    
    /**
     * Execute DML statements within a transaction.
     * All statements will be rolled back if any fails.
     */
    private SqlExecutionResult executeDMLStatements(List<String> statements, SqlExecutionResult result) {
        logger.debug("Executing {} DML statements in transaction", statements.size());
        
        Boolean success = transactionTemplate.execute(status -> {
            try {
                for (int i = 0; i < statements.size(); i++) {
                    String statement = statements.get(i);
                    executeDML(statement);
                    result.incrementSuccessfulStatements();
                    logger.debug("Executed DML statement {}/{}: {}", i + 1, statements.size(), 
                               truncateStatement(statement));
                }
                return true;
            } catch (Exception e) {
                logger.error("DML transaction failed, rolling back", e);
                result.setErrorMessage("DML execution failed: " + e.getMessage());
                result.setException(e);
                status.setRollbackOnly();
                return false;
            }
        });
        
        result.setSuccess(Boolean.TRUE.equals(success));
        return result;
    }
    
    /**
     * Execute mixed DDL and DML statements.
     * DDL statements are executed individually, DML statements in transactions.
     */
    private SqlExecutionResult executeMixedStatements(List<String> statements, SqlExecutionResult result) {
        logger.debug("Executing {} mixed statements", statements.size());
        
        for (int i = 0; i < statements.size(); i++) {
            String statement = statements.get(i);
            try {
                if (DDL_PATTERN.matcher(statement).find()) {
                    executeDDL(statement);
                } else {
                    // Execute DML in its own transaction
                    Boolean success = transactionTemplate.execute(status -> {
                        try {
                            executeDML(statement);
                            return true;
                        } catch (Exception e) {
                            logger.error("DML statement failed in mixed execution", e);
                            status.setRollbackOnly();
                            throw new RuntimeException(e);
                        }
                    });
                    
                    if (!Boolean.TRUE.equals(success)) {
                        throw new RuntimeException("DML statement execution failed");
                    }
                }
                
                result.incrementSuccessfulStatements();
                logger.debug("Executed mixed statement {}/{}: {}", i + 1, statements.size(), 
                           truncateStatement(statement));
                
            } catch (Exception e) {
                logger.error("Failed to execute mixed statement {}/{}: {}", i + 1, statements.size(), 
                           truncateStatement(statement), e);
                result.setSuccess(false);
                result.setErrorMessage("Mixed execution failed at statement " + (i + 1) + ": " + e.getMessage());
                result.setFailedStatement(statement);
                result.setException(e);
                return result;
            }
        }
        
        result.setSuccess(true);
        return result;
    }
    
    /**
     * Execute a single DDL statement with retry logic.
     */
    public void executeDDL(String statement) {
        retryService.executeDatabaseOperationWithRetry(() -> {
            jdbcTemplate.execute(statement);
            return null;
        }, "DDL: " + truncateStatement(statement));
    }
    
    /**
     * Execute a single DML statement with retry logic.
     */
    public void executeDML(String statement) {
        retryService.executeDatabaseOperationWithRetry(() -> {
            jdbcTemplate.execute(statement);
            return null;
        }, "DML: " + truncateStatement(statement));
    }
    
    /**
     * Execute a parameterized update statement with retry logic.
     * Used for INSERT, UPDATE, DELETE operations with parameters.
     * 
     * @param sql the SQL statement with parameter placeholders
     * @param params the parameters for the SQL statement
     * @return the number of rows affected
     */
    public int executeUpdate(String sql, Object... params) {
        return retryService.executeDatabaseOperationWithRetry(() -> {
            return jdbcTemplate.update(sql, params);
        }, "UPDATE: " + truncateStatement(sql));
    }
    
    /**
     * Execute an operation with exponential backoff retry logic.
     * @deprecated Use RetryService instead for better error handling and metrics
     */
    @Deprecated
    private <T> T executeWithRetry(RetryableOperation<T> operation, String operationDescription) {
        // Delegate to RetryService for consistency
        return retryService.executeDatabaseOperationWithRetry(() -> {
            try {
                return operation.execute();
            } catch (DataAccessException e) {
                throw new RuntimeException(e);
            }
        }, operationDescription);
    }
    
    /**
     * Test database connectivity with circuit breaker protection.
     */
    public boolean testConnection() {
        return circuitBreakerService.executeDatabaseOperation(() -> {
            try {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                return true;
            } catch (Exception e) {
                ErrorHandler.ErrorHandlingResult errorResult = errorHandler.handleError(
                    "DatabaseExecutor.testConnection", e, "Database connectivity test");
                logger.error("Database connection test failed - Category: {}", 
                           errorResult.getCategory(), e);
                return false;
            }
        }, () -> {
            logger.warn("Database connection test failed - circuit breaker is open");
            return false;
        });
    }
    
    /**
     * Get database connection information.
     */
    public String getDatabaseInfo() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT CONCAT('Database: ', DATABASE(), ' | Version: ', VERSION())", 
                String.class
            );
        } catch (Exception e) {
            logger.debug("Could not retrieve database info", e);
            return "Database info not available";
        }
    }
    
    /**
     * Truncate statement for logging purposes.
     */
    private String truncateStatement(String statement) {
        if (statement == null) {
            return "null";
        }
        
        String cleaned = statement.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "..." : cleaned;
    }
    
    /**
     * Functional interface for retryable operations.
     */
    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute() throws DataAccessException;
    }
    
    /**
     * Result of SQL script execution.
     */
    public static class SqlExecutionResult {
        private final String filename;
        private boolean success;
        private String errorMessage;
        private Exception exception;
        private long executionTimeMs;
        private int successfulStatements;
        private String failedStatement;
        
        public SqlExecutionResult(String filename) {
            this.filename = filename;
            this.success = false;
            this.successfulStatements = 0;
        }
        
        // Getters and setters
        public String getFilename() {
            return filename;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public Exception getException() {
            return exception;
        }
        
        public void setException(Exception exception) {
            this.exception = exception;
        }
        
        public long getExecutionTimeMs() {
            return executionTimeMs;
        }
        
        public void setExecutionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
        }
        
        public int getSuccessfulStatements() {
            return successfulStatements;
        }
        
        public void incrementSuccessfulStatements() {
            this.successfulStatements++;
        }
        
        public String getFailedStatement() {
            return failedStatement;
        }
        
        public void setFailedStatement(String failedStatement) {
            this.failedStatement = failedStatement;
        }
        
        @Override
        public String toString() {
            return "SqlExecutionResult{" +
                    "filename='" + filename + '\'' +
                    ", success=" + success +
                    ", executionTimeMs=" + executionTimeMs +
                    ", successfulStatements=" + successfulStatements +
                    '}';
        }
    }
}