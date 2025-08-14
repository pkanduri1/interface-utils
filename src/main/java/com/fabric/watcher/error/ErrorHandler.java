package com.fabric.watcher.error;

import com.fabric.watcher.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive error handler that categorizes errors, tracks patterns,
 * and provides recovery strategies.
 */
@Component
public class ErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);
    
    private final MetricsService metricsService;
    private final Map<String, ErrorPattern> errorPatterns = new ConcurrentHashMap<>();
    
    @Autowired
    public ErrorHandler(MetricsService metricsService) {
        this.metricsService = metricsService;
    }
    
    /**
     * Handle an error with comprehensive categorization and tracking.
     * 
     * @param context the context in which the error occurred
     * @param exception the exception that occurred
     * @param operation the operation that was being performed
     * @return error handling result with recommended actions
     */
    public ErrorHandlingResult handleError(String context, Throwable exception, String operation) {
        ErrorCategory category = ErrorCategory.categorize(exception);
        String errorKey = generateErrorKey(context, exception);
        
        // Track error pattern
        String exceptionType = exception != null ? exception.getClass().getSimpleName() : "null";
        ErrorPattern pattern = errorPatterns.computeIfAbsent(errorKey, 
            k -> new ErrorPattern(context, exceptionType, category));
        pattern.recordOccurrence();
        
        // Log the error with appropriate level based on category and frequency
        logError(context, exception, operation, category, pattern);
        
        // Record metrics
        metricsService.recordError(category, context);
        
        // Determine recovery strategy
        RecoveryStrategy strategy = determineRecoveryStrategy(category, pattern, exception);
        
        return new ErrorHandlingResult(category, strategy, pattern.getOccurrenceCount(), 
                                     shouldAlert(pattern), getErrorMessage(exception, category));
    }
    
    /**
     * Generate a unique key for error pattern tracking.
     */
    private String generateErrorKey(String context, Throwable exception) {
        if (exception == null) {
            return context + ":null:0";
        }
        return context + ":" + exception.getClass().getSimpleName() + ":" + 
               (exception.getMessage() != null ? exception.getMessage().hashCode() : 0);
    }
    
    /**
     * Log error with appropriate level based on category and frequency.
     */
    private void logError(String context, Throwable exception, String operation, 
                         ErrorCategory category, ErrorPattern pattern) {
        String message = "Error in {}: {} - Operation: {} - Category: {} - Occurrence: {}";
        String exceptionMessage = exception != null ? exception.getMessage() : "null";
        
        if (pattern.getOccurrenceCount() == 1) {
            // First occurrence - log as error
            logger.error(message, context, exceptionMessage, operation, 
                        category.getDisplayName(), pattern.getOccurrenceCount(), exception);
        } else if (pattern.getOccurrenceCount() <= 5) {
            // Repeated occurrences - log as warn
            logger.warn(message, context, exceptionMessage, operation, 
                       category.getDisplayName(), pattern.getOccurrenceCount());
        } else {
            // Frequent occurrences - log as debug to avoid log spam
            logger.debug(message, context, exceptionMessage, operation, 
                        category.getDisplayName(), pattern.getOccurrenceCount());
        }
    }
    
    /**
     * Determine the appropriate recovery strategy based on error characteristics.
     */
    private RecoveryStrategy determineRecoveryStrategy(ErrorCategory category, 
                                                     ErrorPattern pattern, 
                                                     Throwable exception) {
        // If error is not retryable, fail fast
        if (!category.isRetryable()) {
            return RecoveryStrategy.FAIL_FAST;
        }
        
        // If error has occurred too frequently, circuit break
        if (pattern.getOccurrenceCount() > 10 && 
            pattern.getRecentOccurrenceRate() > 0.5) { // More than 50% failure rate
            return RecoveryStrategy.CIRCUIT_BREAK;
        }
        
        // If it's a database error, use exponential backoff
        if (category == ErrorCategory.DATABASE) {
            return RecoveryStrategy.EXPONENTIAL_BACKOFF;
        }
        
        // If it's a network error, use exponential backoff
        if (category == ErrorCategory.NETWORK) {
            return RecoveryStrategy.EXPONENTIAL_BACKOFF;
        }
        
        // If it's a file system error, use linear backoff
        if (category == ErrorCategory.FILE_SYSTEM) {
            return RecoveryStrategy.LINEAR_BACKOFF;
        }
        
        // Default to simple retry for other retryable errors
        return RecoveryStrategy.SIMPLE_RETRY;
    }
    
    /**
     * Determine if an alert should be sent based on error pattern.
     */
    private boolean shouldAlert(ErrorPattern pattern) {
        // Alert on first occurrence of critical errors
        if (pattern.getOccurrenceCount() == 1 && 
            (pattern.getCategory() == ErrorCategory.SECURITY || 
             pattern.getCategory() == ErrorCategory.RESOURCE)) {
            return true;
        }
        
        // Alert if error rate is high
        if (pattern.getOccurrenceCount() > 5 && 
            pattern.getRecentOccurrenceRate() > 0.3) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get a user-friendly error message based on category.
     */
    private String getErrorMessage(Throwable exception, ErrorCategory category) {
        String baseMessage = exception != null && exception.getMessage() != null ? 
            exception.getMessage() : "Unknown error";
        
        switch (category) {
            case DATABASE:
                return "Database operation failed: " + baseMessage;
            case FILE_SYSTEM:
                return "File system operation failed: " + baseMessage;
            case NETWORK:
                return "Network operation failed: " + baseMessage;
            case SECURITY:
                return "Security violation: " + baseMessage;
            case RESOURCE:
                return "Resource exhaustion: " + baseMessage;
            case APPLICATION:
                return "Application error: " + baseMessage;
            case EXTERNAL_SYSTEM:
                return "External system error: " + baseMessage;
            default:
                return "Unexpected error: " + baseMessage;
        }
    }
    
    /**
     * Get error statistics for monitoring.
     */
    public Map<String, ErrorPattern> getErrorPatterns() {
        return new ConcurrentHashMap<>(errorPatterns);
    }
    
    /**
     * Clear error patterns (useful for testing or maintenance).
     */
    public void clearErrorPatterns() {
        errorPatterns.clear();
    }
    
    /**
     * Inner class to track error patterns over time.
     */
    public static class ErrorPattern {
        private final String context;
        private final String exceptionType;
        private final ErrorCategory category;
        private final AtomicInteger occurrenceCount = new AtomicInteger(0);
        private volatile LocalDateTime firstOccurrence;
        private volatile LocalDateTime lastOccurrence;
        
        public ErrorPattern(String context, String exceptionType, ErrorCategory category) {
            this.context = context;
            this.exceptionType = exceptionType;
            this.category = category;
            this.firstOccurrence = LocalDateTime.now();
        }
        
        public void recordOccurrence() {
            occurrenceCount.incrementAndGet();
            lastOccurrence = LocalDateTime.now();
        }
        
        public int getOccurrenceCount() {
            return occurrenceCount.get();
        }
        
        public double getRecentOccurrenceRate() {
            if (firstOccurrence == null || lastOccurrence == null) {
                return 0.0;
            }
            
            // Calculate rate over the last hour
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            if (firstOccurrence.isBefore(oneHourAgo)) {
                // If pattern started more than an hour ago, calculate rate for last hour
                return Math.min(1.0, occurrenceCount.get() / 60.0); // Assume max 1 per minute
            } else {
                // If pattern is recent, calculate rate since first occurrence
                long minutesSinceFirst = java.time.Duration.between(firstOccurrence, LocalDateTime.now()).toMinutes();
                return minutesSinceFirst > 0 ? occurrenceCount.get() / (double) minutesSinceFirst : 1.0;
            }
        }
        
        // Getters
        public String getContext() { return context; }
        public String getExceptionType() { return exceptionType; }
        public ErrorCategory getCategory() { return category; }
        public LocalDateTime getFirstOccurrence() { return firstOccurrence; }
        public LocalDateTime getLastOccurrence() { return lastOccurrence; }
    }
    
    /**
     * Recovery strategies for different types of errors.
     */
    public enum RecoveryStrategy {
        SIMPLE_RETRY,
        LINEAR_BACKOFF,
        EXPONENTIAL_BACKOFF,
        CIRCUIT_BREAK,
        FAIL_FAST
    }
    
    /**
     * Result of error handling with recommended actions.
     */
    public static class ErrorHandlingResult {
        private final ErrorCategory category;
        private final RecoveryStrategy strategy;
        private final int occurrenceCount;
        private final boolean shouldAlert;
        private final String message;
        
        public ErrorHandlingResult(ErrorCategory category, RecoveryStrategy strategy, 
                                 int occurrenceCount, boolean shouldAlert, String message) {
            this.category = category;
            this.strategy = strategy;
            this.occurrenceCount = occurrenceCount;
            this.shouldAlert = shouldAlert;
            this.message = message;
        }
        
        // Getters
        public ErrorCategory getCategory() { return category; }
        public RecoveryStrategy getStrategy() { return strategy; }
        public int getOccurrenceCount() { return occurrenceCount; }
        public boolean shouldAlert() { return shouldAlert; }
        public String getMessage() { return message; }
    }
}