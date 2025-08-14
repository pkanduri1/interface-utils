package com.fabric.watcher.service;

import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingStatistics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsService.
 */
class MetricsServiceTest {
    
    private MetricsService metricsService;
    private MeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }
    
    @Test
    void testInitializeStatistics() {
        String configName = "test-config";
        String processorType = "sql-script";
        
        metricsService.initializeStatistics(configName, processorType);
        
        ProcessingStatistics stats = metricsService.getStatistics(configName);
        assertNotNull(stats);
        assertEquals(configName, stats.getConfigName());
        assertEquals(processorType, stats.getProcessorType());
        assertEquals(0, stats.getTotalFilesProcessed());
        assertEquals(0, stats.getSuccessfulExecutions());
        assertEquals(0, stats.getFailedExecutions());
    }
    
    @Test
    void testRecordSuccessfulProcessingResult() {
        String configName = "test-config";
        metricsService.initializeStatistics(configName, "sql-script");
        
        ProcessingResult result = createProcessingResult("test.sql", "sql-script", 
                ProcessingResult.ExecutionStatus.SUCCESS, 1500L, configName);
        
        metricsService.recordProcessingResult(result);
        
        ProcessingStatistics stats = metricsService.getStatistics(configName);
        assertEquals(1, stats.getTotalFilesProcessed());
        assertEquals(1, stats.getSuccessfulExecutions());
        assertEquals(0, stats.getFailedExecutions());
        assertEquals("SUCCESS", stats.getCurrentStatus());
        assertNotNull(stats.getLastProcessingTime());
        
        // Check metrics
        assertEquals(1.0, meterRegistry.counter("file_watcher.files.processed").count());
        assertEquals(1.0, meterRegistry.counter("file_watcher.files.success").count());
        assertEquals(0.0, meterRegistry.counter("file_watcher.files.failed").count());
        assertEquals(0.0, meterRegistry.counter("file_watcher.files.skipped").count());
    }
    
    @Test
    void testRecordFailedProcessingResult() {
        String configName = "test-config";
        metricsService.initializeStatistics(configName, "sql-script");
        
        ProcessingResult result = createProcessingResult("test.sql", "sql-script", 
                ProcessingResult.ExecutionStatus.FAILURE, 500L, configName);
        
        metricsService.recordProcessingResult(result);
        
        ProcessingStatistics stats = metricsService.getStatistics(configName);
        assertEquals(1, stats.getTotalFilesProcessed());
        assertEquals(0, stats.getSuccessfulExecutions());
        assertEquals(1, stats.getFailedExecutions());
        assertEquals("FAILED", stats.getCurrentStatus());
        
        // Check metrics
        assertEquals(1.0, meterRegistry.counter("file_watcher.files.processed").count());
        assertEquals(0.0, meterRegistry.counter("file_watcher.files.success").count());
        assertEquals(1.0, meterRegistry.counter("file_watcher.files.failed").count());
        assertEquals(0.0, meterRegistry.counter("file_watcher.files.skipped").count());
    }
    
    @Test
    void testGetAllStatistics() {
        metricsService.initializeStatistics("config1", "sql-script");
        metricsService.initializeStatistics("config2", "sqlloader-log");
        
        ConcurrentMap<String, ProcessingStatistics> allStats = metricsService.getAllStatistics();
        
        assertEquals(2, allStats.size());
        assertTrue(allStats.containsKey("config1"));
        assertTrue(allStats.containsKey("config2"));
    }
    
    @Test
    void testGetStatisticsForNonExistentConfig() {
        ProcessingStatistics stats = metricsService.getStatistics("non-existent");
        assertNull(stats);
    }
    
    @Test
    void testRecordCustomMetric() {
        assertDoesNotThrow(() -> {
            metricsService.recordCustomMetric("custom.metric", 42.0);
        });
        
        assertNotNull(meterRegistry.find("custom.metric").gauge());
    }
    
    @Test
    void testIncrementCounter() {
        assertDoesNotThrow(() -> {
            metricsService.incrementCounter("custom.counter", "tag1", "value1");
        });
        
        assertEquals(1.0, meterRegistry.counter("custom.counter", "tag1", "value1").count());
    }
    
    @Test
    void testMultipleProcessingResults() {
        String configName = "test-config";
        metricsService.initializeStatistics(configName, "sql-script");
        
        // Record multiple results
        for (int i = 0; i < 5; i++) {
            ProcessingResult result = createProcessingResult("test" + i + ".sql", "sql-script", 
                    ProcessingResult.ExecutionStatus.SUCCESS, 1000L + i * 100, configName);
            metricsService.recordProcessingResult(result);
        }
        
        // Record one failure
        ProcessingResult failureResult = createProcessingResult("failure.sql", "sql-script", 
                ProcessingResult.ExecutionStatus.FAILURE, 500L, configName);
        metricsService.recordProcessingResult(failureResult);
        
        ProcessingStatistics stats = metricsService.getStatistics(configName);
        assertEquals(6, stats.getTotalFilesProcessed());
        assertEquals(5, stats.getSuccessfulExecutions());
        assertEquals(1, stats.getFailedExecutions());
        assertEquals("FAILED", stats.getCurrentStatus()); // Last status
        
        // Check metrics
        assertEquals(6.0, meterRegistry.counter("file_watcher.files.processed").count());
        assertEquals(5.0, meterRegistry.counter("file_watcher.files.success").count());
        assertEquals(1.0, meterRegistry.counter("file_watcher.files.failed").count());
    }
    
    private ProcessingResult createProcessingResult(String filename, String processorType, 
                                                  ProcessingResult.ExecutionStatus status, 
                                                  long duration, String configName) {
        ProcessingResult result = new ProcessingResult(filename, processorType, status);
        result.setExecutionDurationMs(duration);
        result.setExecutionTime(LocalDateTime.now());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("configName", configName);
        result.setMetadata(metadata);
        
        return result;
    }
    
    @Test
    void testRecordSkippedProcessingResult() {
        String configName = "test-config";
        metricsService.initializeStatistics(configName, "sql-script");
        
        ProcessingResult result = createProcessingResult("test.sql", "sql-script", 
                ProcessingResult.ExecutionStatus.SKIPPED, 100L, configName);
        
        metricsService.recordProcessingResult(result);
        
        ProcessingStatistics stats = metricsService.getStatistics(configName);
        assertEquals(1, stats.getTotalFilesProcessed());
        assertEquals(0, stats.getSuccessfulExecutions());
        assertEquals(0, stats.getFailedExecutions());
        
        // Check metrics
        assertEquals(1.0, meterRegistry.counter("file_watcher.files.processed").count());
        assertEquals(0.0, meterRegistry.counter("file_watcher.files.success").count());
        assertEquals(0.0, meterRegistry.counter("file_watcher.files.failed").count());
        assertEquals(1.0, meterRegistry.counter("file_watcher.files.skipped").count());
    }
    
    @Test
    void testRecordSqlExecutionTime() {
        Duration duration = Duration.ofMillis(1500);
        
        metricsService.recordSqlExecutionTime(duration);
        
        assertEquals(1, meterRegistry.timer("file_watcher.sql.execution.duration").count());
        assertTrue(meterRegistry.timer("file_watcher.sql.execution.duration").totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }
    
    @Test
    void testRecordDatabaseConnectionFailure() {
        metricsService.recordDatabaseConnectionFailure();
        
        assertEquals(1.0, meterRegistry.counter("file_watcher.database.connection.failures").count());
    }
    
    @Test
    void testRecordFileSystemError() {
        metricsService.recordFileSystemError();
        
        assertEquals(1.0, meterRegistry.counter("file_watcher.filesystem.errors").count());
    }
    
    @Test
    void testGetMetricsSummary() {
        // Record some metrics
        metricsService.recordDatabaseConnectionFailure();
        metricsService.recordFileSystemError();
        metricsService.recordSqlExecutionTime(Duration.ofMillis(1000));
        
        String configName = "test-config";
        metricsService.initializeStatistics(configName, "sql-script");
        ProcessingResult result = createProcessingResult("test.sql", "sql-script", 
                ProcessingResult.ExecutionStatus.SUCCESS, 1500L, configName);
        metricsService.recordProcessingResult(result);
        
        Map<String, Double> summary = metricsService.getMetricsSummary();
        
        assertNotNull(summary);
        assertEquals(1.0, summary.get("files.processed"));
        assertEquals(1.0, summary.get("files.success"));
        assertEquals(0.0, summary.get("files.failed"));
        assertEquals(0.0, summary.get("files.skipped"));
        assertEquals(1.0, summary.get("database.connection.failures"));
        assertEquals(1.0, summary.get("filesystem.errors"));
        assertTrue(summary.get("processing.duration.mean") > 0);
        assertTrue(summary.get("sql.execution.duration.mean") > 0);
    }
}