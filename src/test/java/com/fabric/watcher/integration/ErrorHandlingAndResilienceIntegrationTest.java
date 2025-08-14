package com.fabric.watcher.integration;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.error.ErrorCategory;
import com.fabric.watcher.error.ErrorHandler;
import com.fabric.watcher.resilience.CircuitBreakerService;
import com.fabric.watcher.resilience.GracefulDegradationService;
import com.fabric.watcher.resilience.RetryService;
import com.fabric.watcher.service.DatabaseExecutor;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class ErrorHandlingAndResilienceIntegrationTest {
    
    @Autowired
    private ErrorHandler errorHandler;
    
    @Autowired
    private CircuitBreakerService circuitBreakerService;
    
    @Autowired
    private RetryService retryService;
    
    @Autowired
    private GracefulDegradationService gracefulDegradationService;
    
    @Autowired
    private MetricsService metricsService;
    
    @MockBean
    private JdbcTemplate jdbcTemplate;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        // Reset circuit breakers to closed state
        circuitBreakerService.forceClose("database");
        circuitBreakerService.forceClose("filesystem");
        circuitBreakerService.forceClose("external");
        
        // Clear error patterns
        errorHandler.clearErrorPatterns();
    }
    
    @Test
    void testCompleteErrorHandlingFlow() {
        // Given
        DataAccessException exception = new DataAccessException("Connection failed") {};
        String context = "Integration test";
        String operation = "Test operation";
        
        // When - handle error
        ErrorHandler.ErrorHandlingResult result = errorHandler.handleError(context, exception, operation);
        
        // Then
        assertEquals(ErrorCategory.DATABASE, result.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.EXPONENTIAL_BACKOFF, result.getStrategy());
        assertFalse(result.shouldAlert()); // First occurrence shouldn't alert
        
        // Verify error pattern tracking
        Map<String, ErrorHandler.ErrorPattern> patterns = errorHandler.getErrorPatterns();
        assertFalse(patterns.isEmpty());
        
        ErrorHandler.ErrorPattern pattern = patterns.values().iterator().next();
        assertEquals(1, pattern.getOccurrenceCount());
        assertEquals(ErrorCategory.DATABASE, pattern.getCategory());
    }
    
    @Test
    void testCircuitBreakerIntegration() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // When - execute operation that will trigger circuit breaker
        String result = circuitBreakerService.executeDatabaseOperation(
            () -> {
                attemptCount.incrementAndGet();
                throw new DataAccessException("Persistent failure") {};
            },
            () -> "fallback_executed"
        );
        
        // Then
        assertEquals("fallback_executed", result);
        assertTrue(attemptCount.get() > 0);
    }
    
    @Test
    void testRetryServiceIntegration() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        String expectedResult = "retry_success";
        
        // When - execute operation that succeeds after retry
        String result = retryService.executeDatabaseOperationWithRetry(
            () -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new DataAccessException("Temporary failure") {};
                }
                return expectedResult;
            },
            "integration test"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertEquals(3, attemptCount.get());
        
        // Verify retry statistics
        RetryService.RetryStatistics stats = retryService.getRetryStatistics("database");
        assertNotNull(stats);
        assertTrue(stats.getTotalCalls() > 0);
    }
    
    @Test
    void testGracefulDegradationIntegration() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Files.createDirectories(watchFolder);
        
        WatchConfig config = new WatchConfig();
        config.setName("integration-test");
        config.setWatchFolder(watchFolder);
        config.setProcessorType("sql-script");
        
        // Create test file
        Path testFile = watchFolder.resolve("test.sql");
        Files.write(testFile, "SELECT 1;".getBytes());
        
        // Enter degraded mode
        gracefulDegradationService.enterDegradedMode("database", "Integration test failure");
        
        // When - handle database unavailable
        boolean queued = gracefulDegradationService.handleDatabaseUnavailable(testFile.toFile(), config);
        
        // Then
        assertTrue(queued);
        assertFalse(Files.exists(testFile)); // Original file moved
        assertTrue(gracefulDegradationService.isInDegradedMode("database"));
        assertTrue(gracefulDegradationService.isInGlobalDegradationMode());
        
        // When - exit degraded mode and process queue
        gracefulDegradationService.exitDegradedMode("database");
        int processedFromQueue = gracefulDegradationService.processQueuedFiles(config);
        
        // Then
        assertEquals(1, processedFromQueue);
        assertFalse(gracefulDegradationService.isInDegradedMode("database"));
        assertFalse(gracefulDegradationService.isInGlobalDegradationMode());
        assertTrue(Files.exists(watchFolder.resolve("test.sql"))); // File restored
    }
    
    @Test
    void testHealthCheckIntegration() {
        // Given - mock circuit breaker states
        circuitBreakerService.forceOpen("database");
        
        // When - perform health check
        gracefulDegradationService.checkSystemHealth();
        
        // Then
        assertTrue(gracefulDegradationService.isInDegradedMode("database"));
        
        // When - close circuit breaker and check again
        circuitBreakerService.forceClose("database");
        gracefulDegradationService.checkSystemHealth();
        
        // Then
        assertFalse(gracefulDegradationService.isInDegradedMode("database"));
    }
    
    @Test
    void testErrorEscalationFlow() {
        // Given
        DataAccessException exception = new DataAccessException("Persistent failure") {};
        String context = "Escalation test";
        String operation = "Test operation";
        
        // When - simulate repeated errors to trigger escalation
        ErrorHandler.ErrorHandlingResult result = null;
        for (int i = 0; i < 15; i++) {
            result = errorHandler.handleError(context, exception, operation);
        }
        
        // Then - strategy should escalate to circuit breaker
        assertEquals(ErrorCategory.DATABASE, result.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.CIRCUIT_BREAK, result.getStrategy());
        assertTrue(result.shouldAlert()); // High error rate should trigger alert
        assertEquals(15, result.getOccurrenceCount());
    }
    
    @Test
    void testDifferentErrorCategoriesHandling() {
        // Test database error
        ErrorHandler.ErrorHandlingResult dbResult = errorHandler.handleError(
            "test", new DataAccessException("DB error") {}, "db op");
        assertEquals(ErrorCategory.DATABASE, dbResult.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.EXPONENTIAL_BACKOFF, dbResult.getStrategy());
        
        // Test file system error
        ErrorHandler.ErrorHandlingResult fsResult = errorHandler.handleError(
            "test", new IOException("File error"), "file op");
        assertEquals(ErrorCategory.FILE_SYSTEM, fsResult.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.LINEAR_BACKOFF, fsResult.getStrategy());
        
        // Test security error
        ErrorHandler.ErrorHandlingResult secResult = errorHandler.handleError(
            "test", new SecurityException("Security error"), "security op");
        assertEquals(ErrorCategory.SECURITY, secResult.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.FAIL_FAST, secResult.getStrategy());
        assertTrue(secResult.shouldAlert()); // Security errors should alert immediately
    }
    
    @Test
    void testCircuitBreakerStateTransitions() {
        // Initially closed
        assertEquals("CLOSED", circuitBreakerService.getCircuitBreakerState("database"));
        assertTrue(circuitBreakerService.isDatabaseAvailable());
        
        // Force open
        circuitBreakerService.forceOpen("database");
        assertEquals("OPEN", circuitBreakerService.getCircuitBreakerState("database"));
        assertFalse(circuitBreakerService.isDatabaseAvailable());
        
        // Force close
        circuitBreakerService.forceClose("database");
        assertEquals("CLOSED", circuitBreakerService.getCircuitBreakerState("database"));
        assertTrue(circuitBreakerService.isDatabaseAvailable());
    }
    
    @Test
    void testMetricsIntegration() {
        // Given
        DataAccessException exception = new DataAccessException("Test error") {};
        
        // When - handle error
        errorHandler.handleError("metrics test", exception, "test operation");
        
        // Then - verify metrics were recorded
        Map<String, Double> metrics = metricsService.getMetricsSummary();
        assertNotNull(metrics);
        
        // Should contain error metrics
        assertTrue(metrics.keySet().stream().anyMatch(key -> key.contains("errors")));
    }
    
    @Test
    void testEndToEndResilienceFlow() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Files.createDirectories(watchFolder);
        
        WatchConfig config = new WatchConfig();
        config.setName("e2e-test");
        config.setWatchFolder(watchFolder);
        config.setProcessorType("sql-script");
        
        // Create test file
        Path testFile = watchFolder.resolve("e2e-test.sql");
        Files.write(testFile, "CREATE TABLE test (id INT);".getBytes());
        
        // Simulate database failure
        doThrow(new DataAccessException("Connection lost") {}).when(jdbcTemplate).execute(anyString());
        
        // When - process file (this would normally be done by FileWatcherService)
        // Simulate the error handling that would occur
        try {
            jdbcTemplate.execute("CREATE TABLE test (id INT);");
        } catch (DataAccessException e) {
            // Handle error
            ErrorHandler.ErrorHandlingResult errorResult = errorHandler.handleError(
                "DatabaseExecutor", e, "SQL execution");
            
            // Enter degraded mode based on error
            if (errorResult.getCategory() == ErrorCategory.DATABASE) {
                gracefulDegradationService.enterDegradedMode("database", errorResult.getMessage());
            }
            
            // Queue file for later processing
            gracefulDegradationService.handleDatabaseUnavailable(testFile.toFile(), config);
        }
        
        // Then - verify system is in degraded state
        assertTrue(gracefulDegradationService.isInDegradedMode("database"));
        assertTrue(gracefulDegradationService.isInGlobalDegradationMode());
        assertFalse(Files.exists(testFile)); // File should be queued
        
        // When - database recovers
        reset(jdbcTemplate);
        gracefulDegradationService.exitDegradedMode("database");
        int processedFromQueue = gracefulDegradationService.processQueuedFiles(config);
        
        // Then - verify recovery
        assertEquals(1, processedFromQueue);
        assertFalse(gracefulDegradationService.isInDegradedMode("database"));
        assertFalse(gracefulDegradationService.isInGlobalDegradationMode());
        assertTrue(Files.exists(watchFolder.resolve("e2e-test.sql"))); // File restored for processing
    }
}