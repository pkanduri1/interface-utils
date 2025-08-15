package com.fabric.watcher.archive.service;

import com.fabric.watcher.service.MetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for collecting and managing metrics specific to archive search operations.
 * Integrates with the existing MetricsService infrastructure.
 */
@Service
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class ArchiveSearchMetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchMetricsService.class);
    
    private final MetricsService metricsService;
    private final MeterRegistry meterRegistry;
    
    // Archive search specific metrics
    private final Counter fileSearchRequestsCounter;
    private final Counter contentSearchRequestsCounter;
    private final Counter downloadRequestsCounter;
    private final Counter fileSearchSuccessCounter;
    private final Counter fileSearchFailureCounter;
    private final Counter contentSearchSuccessCounter;
    private final Counter contentSearchFailureCounter;
    private final Counter downloadSuccessCounter;
    private final Counter downloadFailureCounter;
    private final Timer fileSearchTimer;
    private final Timer contentSearchTimer;
    private final Timer downloadTimer;
    private final Counter archiveFilesProcessedCounter;
    private final Counter regularFilesProcessedCounter;
    private final Counter securityViolationsCounter;
    private final Counter timeoutCounter;
    private final Counter pathTraversalAttemptsCounter;
    
    // Archive format specific counters
    private final ConcurrentMap<String, Counter> archiveFormatCounters = new ConcurrentHashMap<>();
    
    // File size distribution counters
    private final Counter smallFilesCounter; // < 1MB
    private final Counter mediumFilesCounter; // 1MB - 10MB
    private final Counter largeFilesCounter; // > 10MB
    
    public ArchiveSearchMetricsService(MetricsService metricsService, MeterRegistry meterRegistry) {
        this.metricsService = metricsService;
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.fileSearchRequestsCounter = Counter.builder("archive_search.file_search.requests")
                .description("Total number of file search requests")
                .register(meterRegistry);
                
        this.contentSearchRequestsCounter = Counter.builder("archive_search.content_search.requests")
                .description("Total number of content search requests")
                .register(meterRegistry);
                
        this.downloadRequestsCounter = Counter.builder("archive_search.download.requests")
                .description("Total number of download requests")
                .register(meterRegistry);
                
        this.fileSearchSuccessCounter = Counter.builder("archive_search.file_search.success")
                .description("Number of successful file search operations")
                .register(meterRegistry);
                
        this.fileSearchFailureCounter = Counter.builder("archive_search.file_search.failure")
                .description("Number of failed file search operations")
                .register(meterRegistry);
                
        this.contentSearchSuccessCounter = Counter.builder("archive_search.content_search.success")
                .description("Number of successful content search operations")
                .register(meterRegistry);
                
        this.contentSearchFailureCounter = Counter.builder("archive_search.content_search.failure")
                .description("Number of failed content search operations")
                .register(meterRegistry);
                
        this.downloadSuccessCounter = Counter.builder("archive_search.download.success")
                .description("Number of successful download operations")
                .register(meterRegistry);
                
        this.downloadFailureCounter = Counter.builder("archive_search.download.failure")
                .description("Number of failed download operations")
                .register(meterRegistry);
                
        this.fileSearchTimer = Timer.builder("archive_search.file_search.duration")
                .description("Time taken for file search operations")
                .register(meterRegistry);
                
        this.contentSearchTimer = Timer.builder("archive_search.content_search.duration")
                .description("Time taken for content search operations")
                .register(meterRegistry);
                
        this.downloadTimer = Timer.builder("archive_search.download.duration")
                .description("Time taken for download operations")
                .register(meterRegistry);
                
        this.archiveFilesProcessedCounter = Counter.builder("archive_search.archive_files.processed")
                .description("Number of archive files processed")
                .register(meterRegistry);
                
        this.regularFilesProcessedCounter = Counter.builder("archive_search.regular_files.processed")
                .description("Number of regular files processed")
                .register(meterRegistry);
                
        this.securityViolationsCounter = Counter.builder("archive_search.security.violations")
                .description("Number of security violations detected")
                .register(meterRegistry);
                
        this.timeoutCounter = Counter.builder("archive_search.operations.timeout")
                .description("Number of operations that timed out")
                .register(meterRegistry);
                
        this.pathTraversalAttemptsCounter = Counter.builder("archive_search.security.path_traversal_attempts")
                .description("Number of path traversal attempts detected")
                .register(meterRegistry);
                
        // File size distribution counters
        this.smallFilesCounter = Counter.builder("archive_search.files.size.small")
                .description("Number of small files processed (< 1MB)")
                .register(meterRegistry);
                
        this.mediumFilesCounter = Counter.builder("archive_search.files.size.medium")
                .description("Number of medium files processed (1MB - 10MB)")
                .register(meterRegistry);
                
        this.largeFilesCounter = Counter.builder("archive_search.files.size.large")
                .description("Number of large files processed (> 10MB)")
                .register(meterRegistry);
    }
    
    /**
     * Record a file search request.
     */
    public void recordFileSearchRequest() {
        fileSearchRequestsCounter.increment();
        logger.debug("Recorded file search request");
    }
    
    /**
     * Record a successful file search operation.
     * @param duration the operation duration
     * @param filesFound the number of files found
     */
    public void recordFileSearchSuccess(Duration duration, int filesFound) {
        fileSearchSuccessCounter.increment();
        fileSearchTimer.record(duration);
        metricsService.recordCustomMetric("archive_search.file_search.files_found", filesFound);
        logger.debug("Recorded successful file search: duration={}ms, files={}", duration.toMillis(), filesFound);
    }
    
    /**
     * Record a failed file search operation.
     * @param duration the operation duration
     * @param errorType the type of error that occurred
     */
    public void recordFileSearchFailure(Duration duration, String errorType) {
        fileSearchFailureCounter.increment();
        fileSearchTimer.record(duration);
        metricsService.incrementCounter("archive_search.file_search.errors", "error_type", errorType);
        logger.debug("Recorded failed file search: duration={}ms, error={}", duration.toMillis(), errorType);
    }
    
    /**
     * Record a content search request.
     */
    public void recordContentSearchRequest() {
        contentSearchRequestsCounter.increment();
        logger.debug("Recorded content search request");
    }
    
    /**
     * Record a successful content search operation.
     * @param duration the operation duration
     * @param matchesFound the number of matches found
     * @param truncated whether results were truncated
     */
    public void recordContentSearchSuccess(Duration duration, int matchesFound, boolean truncated) {
        contentSearchSuccessCounter.increment();
        contentSearchTimer.record(duration);
        metricsService.recordCustomMetric("archive_search.content_search.matches_found", matchesFound);
        if (truncated) {
            metricsService.incrementCounter("archive_search.content_search.truncated");
        }
        logger.debug("Recorded successful content search: duration={}ms, matches={}, truncated={}", 
                duration.toMillis(), matchesFound, truncated);
    }
    
    /**
     * Record a failed content search operation.
     * @param duration the operation duration
     * @param errorType the type of error that occurred
     */
    public void recordContentSearchFailure(Duration duration, String errorType) {
        contentSearchFailureCounter.increment();
        contentSearchTimer.record(duration);
        metricsService.incrementCounter("archive_search.content_search.errors", "error_type", errorType);
        logger.debug("Recorded failed content search: duration={}ms, error={}", duration.toMillis(), errorType);
    }
    
    /**
     * Record a download request.
     */
    public void recordDownloadRequest() {
        downloadRequestsCounter.increment();
        logger.debug("Recorded download request");
    }
    
    /**
     * Record a successful download operation.
     * @param duration the operation duration
     * @param fileSize the size of the downloaded file in bytes
     */
    public void recordDownloadSuccess(Duration duration, long fileSize) {
        downloadSuccessCounter.increment();
        downloadTimer.record(duration);
        recordFileSizeDistribution(fileSize);
        metricsService.recordCustomMetric("archive_search.download.bytes_transferred", fileSize);
        logger.debug("Recorded successful download: duration={}ms, size={} bytes", duration.toMillis(), fileSize);
    }
    
    /**
     * Record a failed download operation.
     * @param duration the operation duration
     * @param errorType the type of error that occurred
     */
    public void recordDownloadFailure(Duration duration, String errorType) {
        downloadFailureCounter.increment();
        downloadTimer.record(duration);
        metricsService.incrementCounter("archive_search.download.errors", "error_type", errorType);
        logger.debug("Recorded failed download: duration={}ms, error={}", duration.toMillis(), errorType);
    }
    
    /**
     * Record processing of an archive file.
     * @param archiveFormat the format of the archive (zip, tar, etc.)
     * @param entriesProcessed the number of entries processed in the archive
     */
    public void recordArchiveFileProcessed(String archiveFormat, int entriesProcessed) {
        archiveFilesProcessedCounter.increment();
        
        // Track by archive format
        String metricName = "archive_search.archive_format." + archiveFormat.toLowerCase();
        Counter formatCounter = archiveFormatCounters.computeIfAbsent(metricName,
            name -> Counter.builder(name)
                .description("Number of " + archiveFormat + " archives processed")
                .tag("format", archiveFormat)
                .register(meterRegistry));
        formatCounter.increment();
        
        metricsService.recordCustomMetric("archive_search.archive.entries_processed", entriesProcessed);
        logger.debug("Recorded archive file processed: format={}, entries={}", archiveFormat, entriesProcessed);
    }
    
    /**
     * Record processing of a regular file.
     */
    public void recordRegularFileProcessed() {
        regularFilesProcessedCounter.increment();
        logger.debug("Recorded regular file processed");
    }
    
    /**
     * Record a security violation.
     * @param violationType the type of security violation
     * @param path the path that caused the violation
     */
    public void recordSecurityViolation(String violationType, String path) {
        securityViolationsCounter.increment();
        metricsService.incrementCounter("archive_search.security.violations", 
                "violation_type", violationType);
        logger.warn("Recorded security violation: type={}, path={}", violationType, path);
    }
    
    /**
     * Record an operation timeout.
     * @param operationType the type of operation that timed out
     */
    public void recordTimeout(String operationType) {
        timeoutCounter.increment();
        metricsService.incrementCounter("archive_search.timeouts", "operation_type", operationType);
        logger.warn("Recorded operation timeout: type={}", operationType);
    }
    
    /**
     * Record a path traversal attempt.
     * @param attemptedPath the path that was attempted
     */
    public void recordPathTraversalAttempt(String attemptedPath) {
        pathTraversalAttemptsCounter.increment();
        logger.warn("Recorded path traversal attempt: path={}", attemptedPath);
    }
    
    /**
     * Record file size distribution for analytics.
     * @param fileSize the file size in bytes
     */
    private void recordFileSizeDistribution(long fileSize) {
        if (fileSize < 1024 * 1024) { // < 1MB
            smallFilesCounter.increment();
        } else if (fileSize < 10 * 1024 * 1024) { // 1MB - 10MB
            mediumFilesCounter.increment();
        } else { // > 10MB
            largeFilesCounter.increment();
        }
    }
    
    /**
     * Get archive search metrics summary.
     * @return map of metric names to values
     */
    public Map<String, Double> getMetricsSummary() {
        Map<String, Double> metrics = new HashMap<>();
        
        // Request metrics
        metrics.put("file_search.requests", fileSearchRequestsCounter.count());
        metrics.put("content_search.requests", contentSearchRequestsCounter.count());
        metrics.put("download.requests", downloadRequestsCounter.count());
        
        // Success/failure metrics
        metrics.put("file_search.success", fileSearchSuccessCounter.count());
        metrics.put("file_search.failure", fileSearchFailureCounter.count());
        metrics.put("content_search.success", contentSearchSuccessCounter.count());
        metrics.put("content_search.failure", contentSearchFailureCounter.count());
        metrics.put("download.success", downloadSuccessCounter.count());
        metrics.put("download.failure", downloadFailureCounter.count());
        
        // Performance metrics
        metrics.put("file_search.duration.mean", fileSearchTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        metrics.put("content_search.duration.mean", contentSearchTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        metrics.put("download.duration.mean", downloadTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        
        // File processing metrics
        metrics.put("archive_files.processed", archiveFilesProcessedCounter.count());
        metrics.put("regular_files.processed", regularFilesProcessedCounter.count());
        
        // Security metrics
        metrics.put("security.violations", securityViolationsCounter.count());
        metrics.put("security.path_traversal_attempts", pathTraversalAttemptsCounter.count());
        
        // Operational metrics
        metrics.put("operations.timeout", timeoutCounter.count());
        
        // File size distribution
        metrics.put("files.size.small", smallFilesCounter.count());
        metrics.put("files.size.medium", mediumFilesCounter.count());
        metrics.put("files.size.large", largeFilesCounter.count());
        
        // Archive format metrics
        archiveFormatCounters.forEach((name, counter) -> 
            metrics.put(name.replace("archive_search.", ""), counter.count()));
        
        return metrics;
    }
    
    /**
     * Calculate success rates for operations.
     * @return map of operation success rates
     */
    public Map<String, Double> getSuccessRates() {
        Map<String, Double> rates = new HashMap<>();
        
        // File search success rate
        double fileSearchTotal = fileSearchSuccessCounter.count() + fileSearchFailureCounter.count();
        if (fileSearchTotal > 0) {
            rates.put("file_search.success_rate", fileSearchSuccessCounter.count() / fileSearchTotal * 100);
        }
        
        // Content search success rate
        double contentSearchTotal = contentSearchSuccessCounter.count() + contentSearchFailureCounter.count();
        if (contentSearchTotal > 0) {
            rates.put("content_search.success_rate", contentSearchSuccessCounter.count() / contentSearchTotal * 100);
        }
        
        // Download success rate
        double downloadTotal = downloadSuccessCounter.count() + downloadFailureCounter.count();
        if (downloadTotal > 0) {
            rates.put("download.success_rate", downloadSuccessCounter.count() / downloadTotal * 100);
        }
        
        return rates;
    }
}