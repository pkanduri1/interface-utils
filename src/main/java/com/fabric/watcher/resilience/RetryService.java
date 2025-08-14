package com.fabric.watcher.resilience;

import com.fabric.watcher.error.ErrorCategory;
import com.fabric.watcher.error.ErrorHandler;
import com.fabric.watcher.service.MetricsService;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Service for handling retry mechanisms with different backoff strategies
 * for various types of transient failures.
 */
@Service
public class RetryService {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryService.class);
    
    private final RetryRegistry retryRegistry;
    private final MetricsService metricsService;
    
    @Autowired
    public RetryService(MetricsService metricsService) {
        this.metricsService = metricsService;
        this.retryRegistry = RetryRegistry.ofDefaults();
        initializeRetryConfigurations();
    }
    
    /**
     * Initialize retry configurations for different scenarios.
     */
    private void initializeRetryConfigurations() {
        // Database retry configuration - exponential backoff
        RetryConfig databaseRetryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(attempt -> 1000L * (1L << (attempt - 1))) // Exponential: 1s, 2s, 4s
            .retryOnException(throwable -> 
                throwable instanceof DataAccessException ||
                throwable instanceof SocketException ||
                throwable instanceof SocketTimeoutException ||
                throwable instanceof TimeoutException ||
                (throwable instanceof RuntimeException && throwable.getMessage() != null &&
                 (throwable.getMessage().contains("Temporary failure") ||
                  throwable.getMessage().contains("Connection failed") ||
                  throwable.getMessage().contains("timeout") ||
                  throwable.getMessage().contains("database"))))
            .failAfterMaxAttempts(true)
            .build();
        
        Retry databaseRetry = retryRegistry.retry("database", databaseRetryConfig);
        databaseRetry.getEventPublisher()
            .onRetry(event -> {
                logger.warn("Database operation retry attempt {} of {}: {}", 
                           event.getNumberOfRetryAttempts(),
                           databaseRetryConfig.getMaxAttempts(),
                           event.getLastThrowable().getMessage());
                metricsService.recordRetryAttempt("database", event.getNumberOfRetryAttempts());
            })
            .onError(event -> {
                logger.error("Database operation failed after {} retry attempts", 
                           event.getNumberOfRetryAttempts(), event.getLastThrowable());
                metricsService.recordRetryFailure("database", event.getNumberOfRetryAttempts());
            })
            .onSuccess(event -> {
                if (event.getNumberOfRetryAttempts() > 0) {
                    logger.info("Database operation succeeded after {} retry attempts", 
                               event.getNumberOfRetryAttempts());
                    metricsService.recordRetrySuccess("database", event.getNumberOfRetryAttempts());
                }
            });
        
        // File system retry configuration - linear backoff
        RetryConfig fileSystemRetryConfig = RetryConfig.custom()
            .maxAttempts(5)
            .intervalFunction(attempt -> 500L * attempt) // Linear: 500ms, 1s, 1.5s, 2s, 2.5s
            .retryOnException(throwable -> {
                // Check direct IOException
                if (throwable instanceof IOException) {
                    return true;
                }
                // Check wrapped IOException
                if (throwable.getCause() instanceof IOException) {
                    return true;
                }
                // Check message content for file-related errors
                if (throwable.getMessage() != null && 
                    (throwable.getMessage().contains("file") ||
                     throwable.getMessage().contains("directory") ||
                     throwable.getMessage().contains("permission") ||
                     throwable.getMessage().contains("File access denied"))) {
                    return true;
                }
                return false;
            })
            .failAfterMaxAttempts(true)
            .build();
        
        Retry fileSystemRetry = retryRegistry.retry("filesystem", fileSystemRetryConfig);
        fileSystemRetry.getEventPublisher()
            .onRetry(event -> {
                logger.warn("File system operation retry attempt {} of {}: {}", 
                           event.getNumberOfRetryAttempts(),
                           fileSystemRetryConfig.getMaxAttempts(),
                           event.getLastThrowable().getMessage());
                metricsService.recordRetryAttempt("filesystem", event.getNumberOfRetryAttempts());
            })
            .onError(event -> {
                logger.error("File system operation failed after {} retry attempts", 
                           event.getNumberOfRetryAttempts(), event.getLastThrowable());
                metricsService.recordRetryFailure("filesystem", event.getNumberOfRetryAttempts());
            })
            .onSuccess(event -> {
                if (event.getNumberOfRetryAttempts() > 0) {
                    logger.info("File system operation succeeded after {} retry attempts", 
                               event.getNumberOfRetryAttempts());
                    metricsService.recordRetrySuccess("filesystem", event.getNumberOfRetryAttempts());
                }
            });
        
        // Network retry configuration - exponential backoff with jitter
        RetryConfig networkRetryConfig = RetryConfig.custom()
            .maxAttempts(4)
            .intervalFunction(attempt -> {
                // Exponential backoff with jitter: base * 2^(attempt-1) + random(0, 1000)
                long baseDelay = 2000L * (1L << (attempt - 1));
                long jitter = (long) (Math.random() * 1000);
                return baseDelay + jitter;
            })
            .retryOnException(throwable -> 
                throwable instanceof SocketException ||
                throwable instanceof SocketTimeoutException ||
                throwable instanceof TimeoutException ||
                (throwable.getMessage() != null && 
                 (throwable.getMessage().contains("timeout") ||
                  throwable.getMessage().contains("connection") ||
                  throwable.getMessage().contains("network"))))
            .failAfterMaxAttempts(true)
            .build();
        
        Retry networkRetry = retryRegistry.retry("network", networkRetryConfig);
        networkRetry.getEventPublisher()
            .onRetry(event -> {
                logger.warn("Network operation retry attempt {} of {}: {}", 
                           event.getNumberOfRetryAttempts(),
                           networkRetryConfig.getMaxAttempts(),
                           event.getLastThrowable().getMessage());
                metricsService.recordRetryAttempt("network", event.getNumberOfRetryAttempts());
            })
            .onError(event -> {
                logger.error("Network operation failed after {} retry attempts", 
                           event.getNumberOfRetryAttempts(), event.getLastThrowable());
                metricsService.recordRetryFailure("network", event.getNumberOfRetryAttempts());
            })
            .onSuccess(event -> {
                if (event.getNumberOfRetryAttempts() > 0) {
                    logger.info("Network operation succeeded after {} retry attempts", 
                               event.getNumberOfRetryAttempts());
                    metricsService.recordRetrySuccess("network", event.getNumberOfRetryAttempts());
                }
            });
        
        // Simple retry configuration - for less critical operations
        RetryConfig simpleRetryConfig = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(Duration.ofMillis(1000))
            .retryOnException(throwable -> true) // Retry on any exception
            .failAfterMaxAttempts(true)
            .build();
        
        Retry simpleRetry = retryRegistry.retry("simple", simpleRetryConfig);
        simpleRetry.getEventPublisher()
            .onRetry(event -> {
                logger.debug("Simple retry attempt {} of {}: {}", 
                           event.getNumberOfRetryAttempts(),
                           simpleRetryConfig.getMaxAttempts(),
                           event.getLastThrowable().getMessage());
                metricsService.recordRetryAttempt("simple", event.getNumberOfRetryAttempts());
            });
    }
    
    /**
     * Execute an operation with retry based on error category.
     * 
     * @param operation the operation to execute
     * @param category the error category to determine retry strategy
     * @param context the context for logging
     * @return the result of the operation
     */
    public <T> T executeWithRetry(Supplier<T> operation, ErrorCategory category, String context) {
        String retryName = getRetryNameForCategory(category);
        Retry retry = retryRegistry.retry(retryName);
        
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, operation);
        
        logger.debug("Executing operation with {} retry strategy in context: {}", retryName, context);
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            logger.error("Operation failed after all retry attempts in context: {}", context, e);
            throw e;
        }
    }
    
    /**
     * Execute an operation with retry based on recovery strategy.
     * 
     * @param operation the operation to execute
     * @param strategy the recovery strategy
     * @param context the context for logging
     * @return the result of the operation
     */
    public <T> T executeWithRetry(Supplier<T> operation, ErrorHandler.RecoveryStrategy strategy, String context) {
        String retryName = getRetryNameForStrategy(strategy);
        Retry retry = retryRegistry.retry(retryName);
        
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, operation);
        
        logger.debug("Executing operation with {} retry strategy in context: {}", retryName, context);
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            logger.error("Operation failed after all retry attempts in context: {}", context, e);
            throw e;
        }
    }
    
    /**
     * Execute a database operation with exponential backoff retry.
     * 
     * @param operation the database operation to execute
     * @param context the context for logging
     * @return the result of the operation
     */
    public <T> T executeDatabaseOperationWithRetry(Supplier<T> operation, String context) {
        return executeWithRetry(operation, ErrorCategory.DATABASE, context);
    }
    
    /**
     * Execute a file system operation with linear backoff retry.
     * 
     * @param operation the file system operation to execute
     * @param context the context for logging
     * @return the result of the operation
     */
    public <T> T executeFileSystemOperationWithRetry(Supplier<T> operation, String context) {
        return executeWithRetry(operation, ErrorCategory.FILE_SYSTEM, context);
    }
    
    /**
     * Execute a network operation with exponential backoff and jitter retry.
     * 
     * @param operation the network operation to execute
     * @param context the context for logging
     * @return the result of the operation
     */
    public <T> T executeNetworkOperationWithRetry(Supplier<T> operation, String context) {
        return executeWithRetry(operation, ErrorCategory.NETWORK, context);
    }
    
    /**
     * Get the appropriate retry configuration name based on error category.
     * 
     * @param category the error category
     * @return the retry configuration name
     */
    private String getRetryNameForCategory(ErrorCategory category) {
        switch (category) {
            case DATABASE:
                return "database";
            case FILE_SYSTEM:
                return "filesystem";
            case NETWORK:
                return "network";
            default:
                return "simple";
        }
    }
    
    /**
     * Get the appropriate retry configuration name based on recovery strategy.
     * 
     * @param strategy the recovery strategy
     * @return the retry configuration name
     */
    private String getRetryNameForStrategy(ErrorHandler.RecoveryStrategy strategy) {
        switch (strategy) {
            case EXPONENTIAL_BACKOFF:
                return "database";
            case LINEAR_BACKOFF:
                return "filesystem";
            case SIMPLE_RETRY:
                return "simple";
            default:
                return "simple";
        }
    }
    
    /**
     * Check if an operation should be retried based on the exception.
     * 
     * @param exception the exception that occurred
     * @param category the error category
     * @return true if the operation should be retried
     */
    public boolean shouldRetry(Throwable exception, ErrorCategory category) {
        if (!category.isRetryable()) {
            return false;
        }
        
        String retryName = getRetryNameForCategory(category);
        Retry retry = retryRegistry.retry(retryName);
        
        // Check the exception and its cause
        boolean shouldRetry = retry.getRetryConfig().getExceptionPredicate().test(exception);
        if (!shouldRetry && exception.getCause() != null) {
            shouldRetry = retry.getRetryConfig().getExceptionPredicate().test(exception.getCause());
        }
        
        return shouldRetry;
    }
    
    /**
     * Get retry statistics for monitoring.
     * 
     * @param retryName the retry configuration name
     * @return retry statistics
     */
    public RetryStatistics getRetryStatistics(String retryName) {
        // Check if the retry configuration exists in our known configurations
        if (!retryName.equals("database") && !retryName.equals("filesystem") && 
            !retryName.equals("network") && !retryName.equals("simple")) {
            return null;
        }
        
        try {
            Retry retry = retryRegistry.retry(retryName);
            if (retry != null) {
                return new RetryStatistics(
                    retryName,
                    retry.getRetryConfig().getMaxAttempts(),
                    retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                    retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt(),
                    retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt(),
                    retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()
                );
            }
        } catch (Exception e) {
            // Return null if retry configuration doesn't exist
            logger.debug("Retry configuration '{}' not found", retryName);
        }
        return null;
    }
    
    /**
     * Statistics for retry operations.
     */
    public static class RetryStatistics {
        private final String name;
        private final int maxAttempts;
        private final long successfulCallsWithoutRetry;
        private final long successfulCallsWithRetry;
        private final long failedCallsWithoutRetry;
        private final long failedCallsWithRetry;
        
        public RetryStatistics(String name, int maxAttempts, 
                             long successfulCallsWithoutRetry, long successfulCallsWithRetry,
                             long failedCallsWithoutRetry, long failedCallsWithRetry) {
            this.name = name;
            this.maxAttempts = maxAttempts;
            this.successfulCallsWithoutRetry = successfulCallsWithoutRetry;
            this.successfulCallsWithRetry = successfulCallsWithRetry;
            this.failedCallsWithoutRetry = failedCallsWithoutRetry;
            this.failedCallsWithRetry = failedCallsWithRetry;
        }
        
        // Getters
        public String getName() { return name; }
        public int getMaxAttempts() { return maxAttempts; }
        public long getSuccessfulCallsWithoutRetry() { return successfulCallsWithoutRetry; }
        public long getSuccessfulCallsWithRetry() { return successfulCallsWithRetry; }
        public long getFailedCallsWithoutRetry() { return failedCallsWithoutRetry; }
        public long getFailedCallsWithRetry() { return failedCallsWithRetry; }
        
        public long getTotalCalls() {
            return successfulCallsWithoutRetry + successfulCallsWithRetry + 
                   failedCallsWithoutRetry + failedCallsWithRetry;
        }
        
        public double getSuccessRate() {
            long totalCalls = getTotalCalls();
            if (totalCalls == 0) return 0.0;
            return (double) (successfulCallsWithoutRetry + successfulCallsWithRetry) / totalCalls;
        }
        
        public double getRetryRate() {
            long totalCalls = getTotalCalls();
            if (totalCalls == 0) return 0.0;
            return (double) (successfulCallsWithRetry + failedCallsWithRetry) / totalCalls;
        }
    }
}