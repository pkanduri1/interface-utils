package com.fabric.watcher.controller;

import com.fabric.watcher.health.DatabaseHealthIndicator;
import com.fabric.watcher.health.FileWatcherHealthIndicator;
import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MonitoringController.
 */
@ExtendWith(MockitoExtension.class)
class MonitoringControllerTest {
    
    @Mock
    private FileWatcherService fileWatcherService;
    
    @Mock
    private MetricsService metricsService;
    
    @Mock
    private DatabaseHealthIndicator databaseHealthIndicator;
    
    @Mock
    private FileWatcherHealthIndicator fileWatcherHealthIndicator;
    
    private MonitoringController controller;
    
    @BeforeEach
    void setUp() {
        controller = new MonitoringController(fileWatcherService, metricsService, 
                                            databaseHealthIndicator, fileWatcherHealthIndicator);
    }
    
    @Test
    void testGetStatusWhenServiceRunning() {
        // Setup
        when(fileWatcherService.isRunning()).thenReturn(true);
        
        ConcurrentMap<String, ProcessingStatistics> stats = new ConcurrentHashMap<>();
        stats.put("config1", new ProcessingStatistics("config1", "sql-script"));
        when(metricsService.getAllStatistics()).thenReturn(stats);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getStatus();
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("serviceRunning"));
        assertEquals(1, body.get("totalConfigurations"));
        assertNotNull(body.get("correlationId"));
        assertNotNull(body.get("timestamp"));
        assertNotNull(body.get("statistics"));
    }
    
    @Test
    void testGetStatusWhenServiceNotRunning() {
        // Setup
        when(fileWatcherService.isRunning()).thenReturn(false);
        when(metricsService.getAllStatistics()).thenReturn(new ConcurrentHashMap<>());
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getStatus();
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("serviceRunning"));
        assertEquals(0, body.get("totalConfigurations"));
    }
    
    @Test
    void testGetStatusWithException() {
        // Setup
        when(fileWatcherService.isRunning()).thenThrow(new RuntimeException("Service error"));
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getStatus();
        
        // Verify
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue(body.get("error").toString().contains("Service error"));
        assertNotNull(body.get("correlationId"));
    }
    
    @Test
    void testGetAllStatistics() {
        // Setup
        ConcurrentMap<String, ProcessingStatistics> stats = new ConcurrentHashMap<>();
        ProcessingStatistics stat1 = new ProcessingStatistics("config1", "sql-script");
        ProcessingStatistics stat2 = new ProcessingStatistics("config2", "sqlloader-log");
        stats.put("config1", stat1);
        stats.put("config2", stat2);
        
        when(metricsService.getAllStatistics()).thenReturn(stats);
        
        // Execute
        ResponseEntity<Map<String, ProcessingStatistics>> response = controller.getStatistics();
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertTrue(response.getBody().containsKey("config1"));
        assertTrue(response.getBody().containsKey("config2"));
    }
    
    @Test
    void testGetStatisticsForSpecificConfig() {
        // Setup
        String configName = "test-config";
        ProcessingStatistics stats = new ProcessingStatistics(configName, "sql-script");
        stats.setTotalFilesProcessed(10);
        stats.setSuccessfulExecutions(8);
        stats.setFailedExecutions(2);
        stats.setLastProcessingTime(LocalDateTime.now());
        
        when(metricsService.getStatistics(configName)).thenReturn(stats);
        
        // Execute
        ResponseEntity<ProcessingStatistics> response = controller.getStatistics(configName);
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(configName, response.getBody().getConfigName());
        assertEquals(10, response.getBody().getTotalFilesProcessed());
        assertEquals(8, response.getBody().getSuccessfulExecutions());
        assertEquals(2, response.getBody().getFailedExecutions());
    }
    
    @Test
    void testGetStatisticsForNonExistentConfig() {
        // Setup
        String configName = "non-existent";
        when(metricsService.getStatistics(configName)).thenReturn(null);
        
        // Execute
        ResponseEntity<ProcessingStatistics> response = controller.getStatistics(configName);
        
        // Verify
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
    
    @Test
    void testPauseWatchingSuccess() {
        // Setup
        String configName = "test-config";
        when(fileWatcherService.pauseWatching(configName)).thenReturn(true);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.pauseWatching(configName);
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals(configName, body.get("configName"));
        assertEquals("pause", body.get("action"));
        assertNotNull(body.get("correlationId"));
    }
    
    @Test
    void testPauseWatchingFailure() {
        // Setup
        String configName = "non-existent";
        when(fileWatcherService.pauseWatching(configName)).thenReturn(false);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.pauseWatching(configName);
        
        // Verify
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("success"));
        assertEquals(configName, body.get("configName"));
        assertTrue(body.get("error").toString().contains("not found"));
    }
    
    @Test
    void testResumeWatchingSuccess() {
        // Setup
        String configName = "test-config";
        when(fileWatcherService.resumeWatching(configName)).thenReturn(true);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.resumeWatching(configName);
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals(configName, body.get("configName"));
        assertEquals("resume", body.get("action"));
    }
    
    @Test
    void testResumeWatchingFailure() {
        // Setup
        String configName = "non-existent";
        when(fileWatcherService.resumeWatching(configName)).thenReturn(false);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.resumeWatching(configName);
        
        // Verify
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("success"));
        assertEquals(configName, body.get("configName"));
        assertTrue(body.get("error").toString().contains("not found"));
    }
    
    @Test
    void testPauseWatchingWithException() {
        // Setup
        String configName = "test-config";
        when(fileWatcherService.pauseWatching(configName)).thenThrow(new RuntimeException("Service error"));
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.pauseWatching(configName);
        
        // Verify
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue(body.get("error").toString().contains("Service error"));
        assertNotNull(body.get("correlationId"));
    }
    
    @Test
    void testGetMetrics() {
        // Setup
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("files.processed", 100.0);
        metrics.put("files.success", 85.0);
        metrics.put("files.failed", 15.0);
        metrics.put("files.skipped", 5.0);
        metrics.put("processing.duration.mean", 1500.0);
        metrics.put("sql.execution.duration.mean", 1200.0);
        metrics.put("database.connection.failures", 2.0);
        metrics.put("filesystem.errors", 1.0);
        
        when(metricsService.getMetricsSummary()).thenReturn(metrics);
        
        // Execute
        ResponseEntity<Map<String, Double>> response = controller.getMetrics();
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(8, response.getBody().size());
        assertEquals(100.0, response.getBody().get("files.processed"));
        assertEquals(85.0, response.getBody().get("files.success"));
        assertEquals(15.0, response.getBody().get("files.failed"));
        assertEquals(5.0, response.getBody().get("files.skipped"));
        assertEquals(1500.0, response.getBody().get("processing.duration.mean"));
        assertEquals(1200.0, response.getBody().get("sql.execution.duration.mean"));
        assertEquals(2.0, response.getBody().get("database.connection.failures"));
        assertEquals(1.0, response.getBody().get("filesystem.errors"));
    }
    
    @Test
    void testGetMetricsWithException() {
        // Setup
        when(metricsService.getMetricsSummary()).thenThrow(new RuntimeException("Metrics error"));
        
        // Execute
        ResponseEntity<Map<String, Double>> response = controller.getMetrics();
        
        // Verify
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
    
    @Test
    void testGetWatchStatus() {
        // Setup
        Map<String, String> watchStatus = new HashMap<>();
        watchStatus.put("sql-scripts", "RUNNING");
        watchStatus.put("sqlloader-logs", "PAUSED");
        
        when(fileWatcherService.getWatchStatus()).thenReturn(watchStatus);
        
        // Execute
        ResponseEntity<Map<String, String>> response = controller.getWatchStatus();
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("RUNNING", response.getBody().get("sql-scripts"));
        assertEquals("PAUSED", response.getBody().get("sqlloader-logs"));
    }
    
    @Test
    void testGetWatchStatusWithException() {
        // Setup
        when(fileWatcherService.getWatchStatus()).thenThrow(new RuntimeException("Watch status error"));
        
        // Execute
        ResponseEntity<Map<String, String>> response = controller.getWatchStatus();
        
        // Verify
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
    
    @Test
    void testGetHealthWhenAllServicesUp() {
        // Setup
        Map<String, Object> databaseHealth = new HashMap<>();
        databaseHealth.put("status", "UP");
        databaseHealth.put("database", "Connected");
        when(databaseHealthIndicator.checkHealth()).thenReturn(databaseHealth);
        
        Map<String, Object> serviceHealth = new HashMap<>();
        serviceHealth.put("status", "UP");
        serviceHealth.put("service", "Running");
        when(fileWatcherHealthIndicator.checkHealth()).thenReturn(serviceHealth);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getHealth();
        
        // Verify
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertNotNull(response.getBody().get("database"));
        assertNotNull(response.getBody().get("fileWatcher"));
        assertNotNull(response.getBody().get("correlationId"));
        assertNotNull(response.getBody().get("timestamp"));
    }
    
    @Test
    void testGetHealthWhenDatabaseDown() {
        // Setup
        Map<String, Object> databaseHealth = new HashMap<>();
        databaseHealth.put("status", "DOWN");
        databaseHealth.put("database", "Disconnected");
        when(databaseHealthIndicator.checkHealth()).thenReturn(databaseHealth);
        
        Map<String, Object> serviceHealth = new HashMap<>();
        serviceHealth.put("status", "UP");
        serviceHealth.put("service", "Running");
        when(fileWatcherHealthIndicator.checkHealth()).thenReturn(serviceHealth);
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getHealth();
        
        // Verify
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DOWN", response.getBody().get("status"));
    }
    
    @Test
    void testGetHealthWithException() {
        // Setup
        when(databaseHealthIndicator.checkHealth()).thenThrow(new RuntimeException("Health check error"));
        
        // Execute
        ResponseEntity<Map<String, Object>> response = controller.getHealth();
        
        // Verify
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DOWN", response.getBody().get("status"));
        assertTrue(response.getBody().get("error").toString().contains("Health check error"));
    }
}