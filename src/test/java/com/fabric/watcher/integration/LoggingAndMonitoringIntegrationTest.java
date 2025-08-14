package com.fabric.watcher.integration;

import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.MetricsService;
import com.fabric.watcher.util.CorrelationIdUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for logging and monitoring functionality.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoggingAndMonitoringIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private MetricsService metricsService;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Test
    void testCorrelationIdInLogging() {
        // Test correlation ID functionality
        String correlationId = CorrelationIdUtil.generateAndSet();
        CorrelationIdUtil.setFileName("test.sql");
        CorrelationIdUtil.setProcessorType("sql-script");
        
        try {
            assertEquals(correlationId, MDC.get("correlationId"));
            assertEquals("test.sql", MDC.get("fileName"));
            assertEquals("sql-script", MDC.get("processorType"));
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    @Test
    void testMetricsCollection() {
        // Initialize statistics
        String configName = "test-integration-config";
        metricsService.initializeStatistics(configName, "sql-script");
        
        // Create and record processing results
        ProcessingResult successResult = createProcessingResult("success.sql", "sql-script", 
                ProcessingResult.ExecutionStatus.SUCCESS, 1500L, configName);
        ProcessingResult failureResult = createProcessingResult("failure.sql", "sql-script", 
                ProcessingResult.ExecutionStatus.FAILURE, 500L, configName);
        
        metricsService.recordProcessingResult(successResult);
        metricsService.recordProcessingResult(failureResult);
        
        // Verify statistics
        ProcessingStatistics stats = metricsService.getStatistics(configName);
        assertNotNull(stats);
        assertEquals(2, stats.getTotalFilesProcessed());
        assertEquals(1, stats.getSuccessfulExecutions());
        assertEquals(1, stats.getFailedExecutions());
        
        // Verify metrics
        assertTrue(meterRegistry.counter("file_watcher.files.processed").count() >= 2.0);
        assertTrue(meterRegistry.counter("file_watcher.files.success").count() >= 1.0);
        assertTrue(meterRegistry.counter("file_watcher.files.failed").count() >= 1.0);
    }
    
    @Test
    void testMonitoringEndpoints() {
        String baseUrl = "http://localhost:" + port + "/api/monitoring";
        
        // Test status endpoint
        ResponseEntity<Map> statusResponse = restTemplate.getForEntity(baseUrl + "/status", Map.class);
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertNotNull(statusResponse.getBody());
        assertTrue(statusResponse.getBody().containsKey("serviceRunning"));
        assertTrue(statusResponse.getBody().containsKey("correlationId"));
        assertTrue(statusResponse.getBody().containsKey("timestamp"));
        
        // Test statistics endpoint
        ResponseEntity<Map> statsResponse = restTemplate.getForEntity(baseUrl + "/statistics", Map.class);
        assertEquals(HttpStatus.OK, statsResponse.getStatusCode());
        assertNotNull(statsResponse.getBody());
    }
    
    @Test
    void testHealthEndpoints() {
        String baseUrl = "http://localhost:" + port + "/actuator";
        
        // Test general health endpoint
        ResponseEntity<Map> healthResponse = restTemplate.getForEntity(baseUrl + "/health", Map.class);
        assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
        assertNotNull(healthResponse.getBody());
        assertTrue(healthResponse.getBody().containsKey("status"));
        
        // Test metrics endpoint
        ResponseEntity<Map> metricsResponse = restTemplate.getForEntity(baseUrl + "/metrics", Map.class);
        assertEquals(HttpStatus.OK, metricsResponse.getStatusCode());
        assertNotNull(metricsResponse.getBody());
        assertTrue(metricsResponse.getBody().containsKey("names"));
    }
    
    @Test
    void testCustomMetrics() {
        // Record custom metrics
        metricsService.recordCustomMetric("test.custom.metric", 42.0);
        metricsService.incrementCounter("test.custom.counter");
        
        // Verify metrics are recorded
        assertNotNull(meterRegistry.find("test.custom.metric").gauge());
        assertTrue(meterRegistry.counter("test.custom.counter").count() >= 1.0);
    }
    
    @Test
    void testPauseResumeEndpoints() {
        String baseUrl = "http://localhost:" + port + "/api/monitoring";
        
        // Test pause endpoint (should return bad request for non-existent config)
        ResponseEntity<Map> pauseResponse = restTemplate.postForEntity(
                baseUrl + "/pause/non-existent", null, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, pauseResponse.getStatusCode());
        assertNotNull(pauseResponse.getBody());
        assertFalse((Boolean) pauseResponse.getBody().get("success"));
        
        // Test resume endpoint (should return bad request for non-existent config)
        ResponseEntity<Map> resumeResponse = restTemplate.postForEntity(
                baseUrl + "/resume/non-existent", null, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resumeResponse.getStatusCode());
        assertNotNull(resumeResponse.getBody());
        assertFalse((Boolean) resumeResponse.getBody().get("success"));
    }
    
    @Test
    void testStatisticsEndpointForSpecificConfig() {
        String baseUrl = "http://localhost:" + port + "/api/monitoring";
        String configName = "test-specific-config";
        
        // Initialize statistics
        metricsService.initializeStatistics(configName, "sql-script");
        
        // Record some processing results
        ProcessingResult result = createProcessingResult("test.sql", "sql-script", 
                ProcessingResult.ExecutionStatus.SUCCESS, 1000L, configName);
        metricsService.recordProcessingResult(result);
        
        // Test specific config statistics endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl + "/statistics/" + configName, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(configName, response.getBody().get("configName"));
        assertEquals("sql-script", response.getBody().get("processorType"));
        assertEquals(1, response.getBody().get("totalFilesProcessed"));
        assertEquals(1, response.getBody().get("successfulExecutions"));
        assertEquals(0, response.getBody().get("failedExecutions"));
        
        // Test non-existent config
        ResponseEntity<Map> notFoundResponse = restTemplate.getForEntity(
                baseUrl + "/statistics/non-existent", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, notFoundResponse.getStatusCode());
    }
    
    @Test
    void testMetricsEndpoint() {
        String baseUrl = "http://localhost:" + port + "/api/monitoring";
        
        // Record some metrics first
        metricsService.recordDatabaseConnectionFailure();
        metricsService.recordFileSystemError();
        
        // Test metrics endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/metrics", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify metrics are present
        assertTrue(response.getBody().containsKey("files.processed"));
        assertTrue(response.getBody().containsKey("files.success"));
        assertTrue(response.getBody().containsKey("files.failed"));
        assertTrue(response.getBody().containsKey("files.skipped"));
        assertTrue(response.getBody().containsKey("processing.duration.mean"));
        assertTrue(response.getBody().containsKey("sql.execution.duration.mean"));
        assertTrue(response.getBody().containsKey("database.connection.failures"));
        assertTrue(response.getBody().containsKey("filesystem.errors"));
        
        // Verify recorded failures
        assertTrue((Double) response.getBody().get("database.connection.failures") >= 1.0);
        assertTrue((Double) response.getBody().get("filesystem.errors") >= 1.0);
    }
    
    @Test
    void testWatchStatusEndpoint() {
        String baseUrl = "http://localhost:" + port + "/api/monitoring";
        
        // Test watch status endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/watch-status", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // The response should be a map of configuration names to their status
        // Since we're in test mode, it might be empty or contain test configurations
        assertTrue(response.getBody() instanceof Map);
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
}