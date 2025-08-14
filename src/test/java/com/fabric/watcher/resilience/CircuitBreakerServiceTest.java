package com.fabric.watcher.resilience;

import com.fabric.watcher.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerServiceTest {
    
    @Mock
    private MetricsService metricsService;
    
    private CircuitBreakerService circuitBreakerService;
    
    @BeforeEach
    void setUp() {
        circuitBreakerService = new CircuitBreakerService(metricsService);
    }
    
    @Test
    void testDatabaseOperationSuccess() {
        // Given
        String expectedResult = "success";
        
        // When
        String result = circuitBreakerService.executeDatabaseOperation(
            () -> expectedResult,
            () -> "fallback"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertTrue(circuitBreakerService.isDatabaseAvailable());
    }
    
    @Test
    void testDatabaseOperationWithFallback() {
        // Given
        String fallbackResult = "fallback";
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // When - simulate multiple failures to trigger circuit breaker
        String result = null;
        for (int i = 0; i < 10; i++) {
            result = circuitBreakerService.executeDatabaseOperation(
                () -> {
                    attemptCount.incrementAndGet();
                    throw new DataAccessException("Connection failed") {};
                },
                () -> fallbackResult
            );
        }
        
        // Then
        assertEquals(fallbackResult, result);
        assertTrue(attemptCount.get() > 0); // Some attempts were made
    }
    
    @Test
    void testFileSystemOperationSuccess() {
        // Given
        String expectedResult = "file_success";
        
        // When
        String result = circuitBreakerService.executeFileSystemOperation(
            () -> expectedResult,
            () -> "file_fallback"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertTrue(circuitBreakerService.isFileSystemAvailable());
    }
    
    @Test
    void testExternalSystemOperationSuccess() {
        // Given
        String expectedResult = "external_success";
        
        // When
        String result = circuitBreakerService.executeExternalSystemOperation(
            () -> expectedResult,
            () -> "external_fallback"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertTrue(circuitBreakerService.isExternalSystemAvailable());
    }
    
    @Test
    void testCircuitBreakerStates() {
        // When
        Map<String, String> states = circuitBreakerService.getAllCircuitBreakerStates();
        
        // Then
        assertNotNull(states);
        assertTrue(states.containsKey("database"));
        assertTrue(states.containsKey("filesystem"));
        assertTrue(states.containsKey("external"));
        
        // All should start in CLOSED state
        assertEquals("CLOSED", states.get("database"));
        assertEquals("CLOSED", states.get("filesystem"));
        assertEquals("CLOSED", states.get("external"));
    }
    
    @Test
    void testGetCircuitBreakerState() {
        // When/Then
        assertEquals("CLOSED", circuitBreakerService.getCircuitBreakerState("database"));
        assertEquals("CLOSED", circuitBreakerService.getCircuitBreakerState("filesystem"));
        assertEquals("CLOSED", circuitBreakerService.getCircuitBreakerState("external"));
        assertEquals("NOT_FOUND", circuitBreakerService.getCircuitBreakerState("nonexistent"));
    }
    
    @Test
    void testForceOpenAndClose() {
        // Given
        String circuitBreakerName = "database";
        
        // When - force open
        circuitBreakerService.forceOpen(circuitBreakerName);
        
        // Then
        assertEquals("OPEN", circuitBreakerService.getCircuitBreakerState(circuitBreakerName));
        assertFalse(circuitBreakerService.isDatabaseAvailable());
        
        // When - force close
        circuitBreakerService.forceClose(circuitBreakerName);
        
        // Then
        assertEquals("CLOSED", circuitBreakerService.getCircuitBreakerState(circuitBreakerName));
        assertTrue(circuitBreakerService.isDatabaseAvailable());
    }
    
    @Test
    void testCircuitBreakerOpenPreventsExecution() {
        // Given
        circuitBreakerService.forceOpen("database");
        String fallbackResult = "fallback_executed";
        
        // When
        String result = circuitBreakerService.executeDatabaseOperation(
            () -> {
                fail("Operation should not be executed when circuit is open");
                return "should_not_execute";
            },
            () -> fallbackResult
        );
        
        // Then
        assertEquals(fallbackResult, result);
    }
    
    @Test
    void testGetCircuitBreakerForCategory() {
        // Test error category to circuit breaker mapping
        assertEquals("database", circuitBreakerService.getCircuitBreakerForCategory(
            com.fabric.watcher.error.ErrorCategory.DATABASE));
        assertEquals("filesystem", circuitBreakerService.getCircuitBreakerForCategory(
            com.fabric.watcher.error.ErrorCategory.FILE_SYSTEM));
        assertEquals("external", circuitBreakerService.getCircuitBreakerForCategory(
            com.fabric.watcher.error.ErrorCategory.EXTERNAL_SYSTEM));
        assertEquals("external", circuitBreakerService.getCircuitBreakerForCategory(
            com.fabric.watcher.error.ErrorCategory.NETWORK));
        assertEquals("database", circuitBreakerService.getCircuitBreakerForCategory(
            com.fabric.watcher.error.ErrorCategory.UNKNOWN));
    }
    
    @Test
    void testCircuitBreakerEventHandling() throws InterruptedException {
        // Given - force multiple failures to trigger state transitions
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // When - execute operations that will fail
        for (int i = 0; i < 6; i++) { // Exceed minimum calls threshold
            try {
                circuitBreakerService.executeDatabaseOperation(
                    () -> {
                        failureCount.incrementAndGet();
                        throw new DataAccessException("Simulated failure") {};
                    },
                    () -> "fallback"
                );
            } catch (Exception e) {
                // Expected failures
            }
        }
        
        // Give circuit breaker time to process events
        Thread.sleep(100);
        
        // Then - verify metrics were recorded
        verify(metricsService, times(failureCount.get())).recordCircuitBreakerStateChange(
            eq("database"), any(String.class));
    }
    
    @Test
    void testAvailabilityChecks() {
        // Initially all should be available
        assertTrue(circuitBreakerService.isDatabaseAvailable());
        assertTrue(circuitBreakerService.isFileSystemAvailable());
        assertTrue(circuitBreakerService.isExternalSystemAvailable());
        
        // Force open and check availability
        circuitBreakerService.forceOpen("database");
        assertFalse(circuitBreakerService.isDatabaseAvailable());
        assertTrue(circuitBreakerService.isFileSystemAvailable()); // Others should still be available
        
        circuitBreakerService.forceOpen("filesystem");
        assertFalse(circuitBreakerService.isFileSystemAvailable());
        
        circuitBreakerService.forceOpen("external");
        assertFalse(circuitBreakerService.isExternalSystemAvailable());
    }
}