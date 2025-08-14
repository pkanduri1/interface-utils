package com.fabric.watcher.service;

import com.fabric.watcher.error.ErrorCategory;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingStatistics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for collecting and managing metrics for file processing operations.
 */
@Service
public class MetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, ProcessingStatistics> statisticsMap = new ConcurrentHashMap<>();
    
    // Metrics
    private final Counter filesProcessedCounter;
    private final Counter filesSuccessCounter;
    private final Counter filesFailedCounter;
    private final Counter filesSkippedCounter;
    private final Timer processingTimer;
    private final Timer sqlExecutionTimer;
    private final Counter databaseConnectionFailures;
    private final Counter fileSystemErrors;
    
    // Error handling and resilience metrics
    private final ConcurrentMap<String, Counter> errorCategoryCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> circuitBreakerStateCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> circuitBreakerRejectionCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> retryAttemptCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> retrySuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> retryFailureCounters = new ConcurrentHashMap<>();
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.filesProcessedCounter = Counter.builder("file_watcher.files.processed")
                .description("Total number of files processed")
                .register(meterRegistry);
                
        this.filesSuccessCounter = Counter.builder("file_watcher.files.success")
                .description("Number of successfully processed files")
                .register(meterRegistry);
                
        this.filesFailedCounter = Counter.builder("file_watcher.files.failed")
                .description("Number of failed file processing attempts")
                .register(meterRegistry);
                
        this.filesSkippedCounter = Counter.builder("file_watcher.files.skipped")
                .description("Number of skipped files")
                .register(meterRegistry);
                
        this.processingTimer = Timer.builder("file_watcher.processing.duration")
                .description("Time taken to process files")
                .register(meterRegistry);
                
        this.sqlExecutionTimer = Timer.builder("file_watcher.sql.execution.duration")
                .description("Time taken to execute SQL scripts")
                .register(meterRegistry);
                
        this.databaseConnectionFailures = Counter.builder("file_watcher.database.connection.failures")
                .description("Number of database connection failures")
                .register(meterRegistry);
                
        this.fileSystemErrors = Counter.builder("file_watcher.filesystem.errors")
                .description("Number of file system operation errors")
                .register(meterRegistry);
    }
    
    /**
     * Record metrics for a file processing result.
     * @param result the processing result to record
     */
    public void recordProcessingResult(ProcessingResult result) {
        String configName = result.getMetadata() != null ? 
            (String) result.getMetadata().get("configName") : "unknown";
        
        // Update counters
        filesProcessedCounter.increment();
        
        switch (result.getStatus()) {
            case SUCCESS:
                filesSuccessCounter.increment();
                break;
            case FAILURE:
                filesFailedCounter.increment();
                break;
            case SKIPPED:
                filesSkippedCounter.increment();
                break;
        }
        
        // Record processing time
        if (result.getExecutionDurationMs() > 0) {
            processingTimer.record(Duration.ofMillis(result.getExecutionDurationMs()));
        }
        
        // Update statistics
        updateStatistics(configName, result);
        
        logger.debug("Recorded metrics for file processing: config={}, status={}, duration={}ms", 
                configName, result.getStatus(), result.getExecutionDurationMs());
    }
    
    /**
     * Get processing statistics for a specific configuration.
     * @param configName the configuration name
     * @return the processing statistics
     */
    public ProcessingStatistics getStatistics(String configName) {
        return statisticsMap.get(configName);
    }
    
    /**
     * Get all processing statistics.
     * @return map of all processing statistics
     */
    public ConcurrentMap<String, ProcessingStatistics> getAllStatistics() {
        return new ConcurrentHashMap<>(statisticsMap);
    }
    
    /**
     * Initialize statistics for a configuration.
     * @param configName the configuration name
     * @param processorType the processor type
     */
    public void initializeStatistics(String configName, String processorType) {
        statisticsMap.putIfAbsent(configName, new ProcessingStatistics(configName, processorType));
        logger.info("Initialized statistics for configuration: {}", configName);
    }
    
    /**
     * Update statistics based on processing result.
     * @param configName the configuration name
     * @param result the processing result
     */
    private void updateStatistics(String configName, ProcessingResult result) {
        ProcessingStatistics stats = statisticsMap.computeIfAbsent(
                configName, 
                k -> new ProcessingStatistics(k, result.getProcessorType())
        );
        
        stats.incrementTotal();
        
        switch (result.getStatus()) {
            case SUCCESS:
                stats.incrementSuccess();
                stats.setCurrentStatus("SUCCESS");
                break;
            case FAILURE:
                stats.incrementFailure();
                stats.setCurrentStatus("FAILED");
                break;
            case SKIPPED:
                // For skipped files, we don't increment success or failure
                stats.setCurrentStatus("SKIPPED");
                break;
        }
    }
    
    /**
     * Record a custom metric.
     * @param name the metric name
     * @param value the metric value
     * @param tags optional tags
     */
    public void recordCustomMetric(String name, double value, String... tags) {
        meterRegistry.gauge(name, value, v -> v);
        logger.debug("Recorded custom metric: {}={}", name, value);
    }
    
    /**
     * Increment a custom counter.
     * @param name the counter name
     * @param tags optional tags
     */
    public void incrementCounter(String name, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .increment();
        logger.debug("Incremented counter: {}", name);
    }
    
    /**
     * Record SQL execution time.
     * @param duration the execution duration
     */
    public void recordSqlExecutionTime(Duration duration) {
        sqlExecutionTimer.record(duration);
        logger.debug("Recorded SQL execution time: {}ms", duration.toMillis());
    }
    
    /**
     * Record database connection failure.
     */
    public void recordDatabaseConnectionFailure() {
        databaseConnectionFailures.increment();
        logger.debug("Recorded database connection failure");
    }
    
    /**
     * Record file system error.
     */
    public void recordFileSystemError() {
        fileSystemErrors.increment();
        logger.debug("Recorded file system error");
    }
    
    /**
     * Record an error by category.
     * @param category the error category
     * @param context the context where the error occurred
     */
    public void recordError(ErrorCategory category, String context) {
        String metricName = "file_watcher.errors." + category.name().toLowerCase();
        Counter counter = errorCategoryCounters.computeIfAbsent(metricName, 
            name -> Counter.builder(name)
                .description("Number of " + category.getDisplayName() + " errors")
                .tag("category", category.name())
                .tag("context", context)
                .register(meterRegistry));
        counter.increment();
        logger.debug("Recorded {} error in context: {}", category.getDisplayName(), context);
    }
    
    /**
     * Record circuit breaker state change.
     * @param circuitBreakerName the circuit breaker name
     * @param newState the new state
     */
    public void recordCircuitBreakerStateChange(String circuitBreakerName, String newState) {
        String metricName = "file_watcher.circuit_breaker.state_changes";
        Counter counter = circuitBreakerStateCounters.computeIfAbsent(
            metricName + "." + circuitBreakerName + "." + newState.toLowerCase(),
            name -> Counter.builder(metricName)
                .description("Circuit breaker state changes")
                .tag("circuit_breaker", circuitBreakerName)
                .tag("state", newState)
                .register(meterRegistry));
        counter.increment();
        logger.debug("Recorded circuit breaker state change: {} -> {}", circuitBreakerName, newState);
    }
    
    /**
     * Record circuit breaker rejection.
     * @param circuitBreakerName the circuit breaker name
     */
    public void recordCircuitBreakerRejection(String circuitBreakerName) {
        String metricName = "file_watcher.circuit_breaker.rejections";
        Counter counter = circuitBreakerRejectionCounters.computeIfAbsent(
            metricName + "." + circuitBreakerName,
            name -> Counter.builder(metricName)
                .description("Circuit breaker rejections")
                .tag("circuit_breaker", circuitBreakerName)
                .register(meterRegistry));
        counter.increment();
        logger.debug("Recorded circuit breaker rejection: {}", circuitBreakerName);
    }
    
    /**
     * Record retry attempt.
     * @param retryName the retry configuration name
     * @param attemptNumber the attempt number
     */
    public void recordRetryAttempt(String retryName, int attemptNumber) {
        String metricName = "file_watcher.retry.attempts";
        Counter counter = retryAttemptCounters.computeIfAbsent(
            metricName + "." + retryName,
            name -> Counter.builder(metricName)
                .description("Retry attempts")
                .tag("retry_config", retryName)
                .register(meterRegistry));
        counter.increment();
        logger.debug("Recorded retry attempt: {} (attempt {})", retryName, attemptNumber);
    }
    
    /**
     * Record retry success.
     * @param retryName the retry configuration name
     * @param totalAttempts the total number of attempts made
     */
    public void recordRetrySuccess(String retryName, int totalAttempts) {
        String metricName = "file_watcher.retry.successes";
        Counter counter = retrySuccessCounters.computeIfAbsent(
            metricName + "." + retryName,
            name -> Counter.builder(metricName)
                .description("Retry successes")
                .tag("retry_config", retryName)
                .register(meterRegistry));
        counter.increment();
        logger.debug("Recorded retry success: {} (after {} attempts)", retryName, totalAttempts);
    }
    
    /**
     * Record retry failure.
     * @param retryName the retry configuration name
     * @param totalAttempts the total number of attempts made
     */
    public void recordRetryFailure(String retryName, int totalAttempts) {
        String metricName = "file_watcher.retry.failures";
        Counter counter = retryFailureCounters.computeIfAbsent(
            metricName + "." + retryName,
            name -> Counter.builder(metricName)
                .description("Retry failures")
                .tag("retry_config", retryName)
                .register(meterRegistry));
        counter.increment();
        logger.debug("Recorded retry failure: {} (after {} attempts)", retryName, totalAttempts);
    }
    
    /**
     * Get current metrics summary.
     * @return map of metric names to values
     */
    public Map<String, Double> getMetricsSummary() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("files.processed", filesProcessedCounter.count());
        metrics.put("files.success", filesSuccessCounter.count());
        metrics.put("files.failed", filesFailedCounter.count());
        metrics.put("files.skipped", filesSkippedCounter.count());
        metrics.put("processing.duration.mean", processingTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        metrics.put("sql.execution.duration.mean", sqlExecutionTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        metrics.put("database.connection.failures", databaseConnectionFailures.count());
        metrics.put("filesystem.errors", fileSystemErrors.count());
        
        // Add error category metrics
        errorCategoryCounters.forEach((name, counter) -> 
            metrics.put(name.replace("file_watcher.", ""), counter.count()));
        
        // Add circuit breaker metrics
        circuitBreakerStateCounters.forEach((name, counter) -> 
            metrics.put(name.replace("file_watcher.", ""), counter.count()));
        circuitBreakerRejectionCounters.forEach((name, counter) -> 
            metrics.put(name.replace("file_watcher.", ""), counter.count()));
        
        // Add retry metrics
        retryAttemptCounters.forEach((name, counter) -> 
            metrics.put(name.replace("file_watcher.", ""), counter.count()));
        retrySuccessCounters.forEach((name, counter) -> 
            metrics.put(name.replace("file_watcher.", ""), counter.count()));
        retryFailureCounters.forEach((name, counter) -> 
            metrics.put(name.replace("file_watcher.", ""), counter.count()));
        
        return metrics;
    }
}