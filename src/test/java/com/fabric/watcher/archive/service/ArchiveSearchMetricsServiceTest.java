package com.fabric.watcher.archive.service;

import com.fabric.watcher.service.MetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArchiveSearchMetricsServiceTest {

    @Mock
    private MetricsService metricsService;

    private MeterRegistry meterRegistry;
    private ArchiveSearchMetricsService archiveSearchMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        archiveSearchMetricsService = new ArchiveSearchMetricsService(metricsService, meterRegistry);
    }

    @Test
    void shouldRecordFileSearchRequest() {
        // When
        archiveSearchMetricsService.recordFileSearchRequest();

        // Then
        Counter counter = meterRegistry.find("archive_search.file_search.requests").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordFileSearchSuccess() {
        // Given
        Duration duration = Duration.ofMillis(150);
        int filesFound = 5;

        // When
        archiveSearchMetricsService.recordFileSearchSuccess(duration, filesFound);

        // Then
        Counter successCounter = meterRegistry.find("archive_search.file_search.success").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);

        Timer timer = meterRegistry.find("archive_search.file_search.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        verify(metricsService).recordCustomMetric("archive_search.file_search.files_found", filesFound);
    }

    @Test
    void shouldRecordFileSearchFailure() {
        // Given
        Duration duration = Duration.ofMillis(100);
        String errorType = "SECURITY_VIOLATION";

        // When
        archiveSearchMetricsService.recordFileSearchFailure(duration, errorType);

        // Then
        Counter failureCounter = meterRegistry.find("archive_search.file_search.failure").counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);

        Timer timer = meterRegistry.find("archive_search.file_search.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        verify(metricsService).incrementCounter("archive_search.file_search.errors", "error_type", errorType);
    }

    @Test
    void shouldRecordContentSearchSuccess() {
        // Given
        Duration duration = Duration.ofMillis(200);
        int matchesFound = 10;
        boolean truncated = true;

        // When
        archiveSearchMetricsService.recordContentSearchSuccess(duration, matchesFound, truncated);

        // Then
        Counter successCounter = meterRegistry.find("archive_search.content_search.success").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);

        Timer timer = meterRegistry.find("archive_search.content_search.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        verify(metricsService).recordCustomMetric("archive_search.content_search.matches_found", matchesFound);
        verify(metricsService).incrementCounter("archive_search.content_search.truncated");
    }

    @Test
    void shouldRecordDownloadSuccess() {
        // Given
        Duration duration = Duration.ofMillis(300);
        long fileSize = 1024 * 1024; // 1MB

        // When
        archiveSearchMetricsService.recordDownloadSuccess(duration, fileSize);

        // Then
        Counter successCounter = meterRegistry.find("archive_search.download.success").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);

        Timer timer = meterRegistry.find("archive_search.download.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        // Should record medium file size
        Counter mediumFilesCounter = meterRegistry.find("archive_search.files.size.medium").counter();
        assertThat(mediumFilesCounter).isNotNull();
        assertThat(mediumFilesCounter.count()).isEqualTo(1.0);

        verify(metricsService).recordCustomMetric("archive_search.download.bytes_transferred", fileSize);
    }

    @Test
    void shouldRecordArchiveFileProcessed() {
        // Given
        String archiveFormat = "zip";
        int entriesProcessed = 15;

        // When
        archiveSearchMetricsService.recordArchiveFileProcessed(archiveFormat, entriesProcessed);

        // Then
        Counter archiveCounter = meterRegistry.find("archive_search.archive_files.processed").counter();
        assertThat(archiveCounter).isNotNull();
        assertThat(archiveCounter.count()).isEqualTo(1.0);

        Counter formatCounter = meterRegistry.find("archive_search.archive_format.zip").counter();
        assertThat(formatCounter).isNotNull();
        assertThat(formatCounter.count()).isEqualTo(1.0);

        verify(metricsService).recordCustomMetric("archive_search.archive.entries_processed", entriesProcessed);
    }

    @Test
    void shouldRecordSecurityViolation() {
        // Given
        String violationType = "PATH_TRAVERSAL";
        String path = "/malicious/../path";

        // When
        archiveSearchMetricsService.recordSecurityViolation(violationType, path);

        // Then
        Counter violationsCounter = meterRegistry.find("archive_search.security.violations").counter();
        assertThat(violationsCounter).isNotNull();
        assertThat(violationsCounter.count()).isEqualTo(1.0);

        verify(metricsService).incrementCounter("archive_search.security.violations", "violation_type", violationType);
    }

    @Test
    void shouldRecordTimeout() {
        // Given
        String operationType = "FILE_SEARCH";

        // When
        archiveSearchMetricsService.recordTimeout(operationType);

        // Then
        Counter timeoutCounter = meterRegistry.find("archive_search.operations.timeout").counter();
        assertThat(timeoutCounter).isNotNull();
        assertThat(timeoutCounter.count()).isEqualTo(1.0);

        verify(metricsService).incrementCounter("archive_search.timeouts", "operation_type", operationType);
    }

    @Test
    void shouldRecordPathTraversalAttempt() {
        // Given
        String attemptedPath = "../../../etc/passwd";

        // When
        archiveSearchMetricsService.recordPathTraversalAttempt(attemptedPath);

        // Then
        Counter pathTraversalCounter = meterRegistry.find("archive_search.security.path_traversal_attempts").counter();
        assertThat(pathTraversalCounter).isNotNull();
        assertThat(pathTraversalCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldGetMetricsSummary() {
        // Given - record some metrics
        archiveSearchMetricsService.recordFileSearchRequest();
        archiveSearchMetricsService.recordFileSearchSuccess(Duration.ofMillis(100), 5);
        archiveSearchMetricsService.recordDownloadRequest();
        archiveSearchMetricsService.recordSecurityViolation("PATH_TRAVERSAL", "/bad/path");

        // When
        Map<String, Double> summary = archiveSearchMetricsService.getMetricsSummary();

        // Then
        assertThat(summary).isNotEmpty();
        assertThat(summary.get("file_search.requests")).isEqualTo(1.0);
        assertThat(summary.get("file_search.success")).isEqualTo(1.0);
        assertThat(summary.get("download.requests")).isEqualTo(1.0);
        assertThat(summary.get("security.violations")).isEqualTo(1.0);
        assertThat(summary.get("file_search.duration.mean")).isGreaterThan(0.0);
    }

    @Test
    void shouldCalculateSuccessRates() {
        // Given - record some successes and failures
        archiveSearchMetricsService.recordFileSearchSuccess(Duration.ofMillis(100), 5);
        archiveSearchMetricsService.recordFileSearchSuccess(Duration.ofMillis(150), 3);
        archiveSearchMetricsService.recordFileSearchFailure(Duration.ofMillis(50), "ERROR");

        archiveSearchMetricsService.recordDownloadSuccess(Duration.ofMillis(200), 1024);
        archiveSearchMetricsService.recordDownloadFailure(Duration.ofMillis(100), "NOT_FOUND");

        // When
        Map<String, Double> successRates = archiveSearchMetricsService.getSuccessRates();

        // Then
        assertThat(successRates.get("file_search.success_rate")).isEqualTo(66.66666666666666); // 2/3 * 100
        assertThat(successRates.get("download.success_rate")).isEqualTo(50.0); // 1/2 * 100
    }

    @Test
    void shouldRecordFileSizeDistribution() {
        // Given
        long smallFileSize = 500 * 1024; // 500KB
        long mediumFileSize = 5 * 1024 * 1024; // 5MB
        long largeFileSize = 50 * 1024 * 1024; // 50MB

        // When
        archiveSearchMetricsService.recordDownloadSuccess(Duration.ofMillis(100), smallFileSize);
        archiveSearchMetricsService.recordDownloadSuccess(Duration.ofMillis(200), mediumFileSize);
        archiveSearchMetricsService.recordDownloadSuccess(Duration.ofMillis(300), largeFileSize);

        // Then
        Counter smallFilesCounter = meterRegistry.find("archive_search.files.size.small").counter();
        assertThat(smallFilesCounter.count()).isEqualTo(1.0);

        Counter mediumFilesCounter = meterRegistry.find("archive_search.files.size.medium").counter();
        assertThat(mediumFilesCounter.count()).isEqualTo(1.0);

        Counter largeFilesCounter = meterRegistry.find("archive_search.files.size.large").counter();
        assertThat(largeFilesCounter.count()).isEqualTo(1.0);
    }
}