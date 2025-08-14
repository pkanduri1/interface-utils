package com.fabric.watcher.resilience;

import com.fabric.watcher.error.ErrorCategory;
import com.fabric.watcher.error.ErrorHandler;
import com.fabric.watcher.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeast;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {
    
    @Mock
    private MetricsService metricsService;
    
    private RetryService retryService;
    
    @BeforeEach
    void setUp() {
        retryService = new RetryService(metricsService);
    }
    
    @Test
    void testSuccessfulOperationWithoutRetry() {
        // Given
        String expectedResult = "success";
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // When
        String result = retryService.executeWithRetry(
            () -> {
                attemptCount.incrementAndGet();
                return expectedResult;
            },
            ErrorCategory.DATABASE,
            "test operation"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertEquals(1, attemptCount.get()); // Should succeed on first attempt
    }
    
    @Test
    void testDatabaseOperationWithRetry() {
        // Given
        String expectedResult = "success";
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // When
        String result = retryService.executeDatabaseOperationWithRetry(
            () -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new DataAccessException("Connection failed") {};
                }
                return expectedResult;
            },
            "database operation"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertEquals(3, attemptCount.get()); // Should succeed on third attempt
        
        // Verify retry metrics were recorded
        verify(metricsService, atLeast(1)).recordRetryAttempt(eq("database"), any(Integer.class));
        verify(metricsService).recordRetrySuccess(eq("database"), any(Integer.class));
    }
    
    @Test
    void testFileSystemOperationWithRetry() {
        // Given
        String expectedResult = "file_success";
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // When
        String result = retryService.executeFileSystemOperationWithRetry(
            () -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 2) {
                    throw new RuntimeException(new IOException("File access denied"));
                }
                return expectedResult;
            },
            "file operation"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertEquals(2, attemptCount.get()); // Should succeed on second attempt
        
        // Verify retry metrics were recorded
        verify(metricsService, atLeast(1)).recordRetryAttempt(eq("filesystem"), any(Integer.class));
        verify(metricsService).recordRetrySuccess(eq("filesystem"), any(Integer.class));
    }
    
    @Test
    void testNetworkOperationWithRetry() {
        // Given
        String expectedResult = "network_success";
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // When
        String result = retryService.executeNetworkOperationWithRetry(
            () -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 2) {
                    throw new RuntimeException(new SocketException("Connection timeout"));
                }
                return expectedResult;
            },
            "network operation"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertEquals(2, attemptCount.get()); // Should succeed on second attempt
        
        // Verify retry metrics were recorded
        verify(metricsService, atLeast(1)).recordRetryAttempt(eq("network"), any(Integer.class));
        verify(metricsService).recordRetrySuccess(eq("network"), any(Integer.class));
    }
    
    @Test
    void testRetryWithRecoveryStrategy() {
        // Given
        String expectedResult = "strategy_success";
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // When
        String result = retryService.executeWithRetry(
            () -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 2) {
                    throw new RuntimeException("Temporary failure");
                }
                return expectedResult;
            },
            ErrorHandler.RecoveryStrategy.EXPONENTIAL_BACKOFF,
            "strategy operation"
        );
        
        // Then
        assertEquals(expectedResult, result);
        assertEquals(2, attemptCount.get());
    }
    
    @Test
    void testRetryFailureAfterMaxAttempts() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        DataAccessException persistentException = new DataAccessException("Persistent failure") {};
        
        // When/Then
        assertThrows(RuntimeException.class, () -> {
            retryService.executeDatabaseOperationWithRetry(
                () -> {
                    attemptCount.incrementAndGet();
                    throw persistentException;
                },
                "failing operation"
            );
        });
        
        // Should have attempted maximum number of times (3 for database)
        assertEquals(3, attemptCount.get());
        
        // Verify failure metrics were recorded
        verify(metricsService, atLeast(1)).recordRetryAttempt(eq("database"), any(Integer.class));
        verify(metricsService).recordRetryFailure(eq("database"), any(Integer.class));
    }
    
    @Test
    void testShouldRetryBasedOnException() {
        // Test retryable exceptions
        assertTrue(retryService.shouldRetry(new DataAccessException("test") {}, ErrorCategory.DATABASE));
        assertTrue(retryService.shouldRetry(new RuntimeException(new IOException("test")), ErrorCategory.FILE_SYSTEM));
        assertTrue(retryService.shouldRetry(new RuntimeException(new SocketException("test")), ErrorCategory.NETWORK));
        
        // Test non-retryable categories
        assertFalse(retryService.shouldRetry(new RuntimeException("test"), ErrorCategory.SECURITY));
        assertFalse(retryService.shouldRetry(new RuntimeException("test"), ErrorCategory.APPLICATION));
    }
    
    @Test
    void testRetryStatistics() {
        // Given - execute some operations to generate statistics
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        // Execute successful operation
        retryService.executeDatabaseOperationWithRetry(
            () -> {
                attemptCount.incrementAndGet();
                return "success";
            },
            "stats test 1"
        );
        
        // Execute operation that succeeds after retry
        try {
            retryService.executeDatabaseOperationWithRetry(
                () -> {
                    int attempt = attemptCount.incrementAndGet();
                    if (attempt == 2) { // Fail on first call, succeed on second
                        throw new DataAccessException("Temporary failure") {};
                    }
                    return "success";
                },
                "stats test 2"
            );
        } catch (Exception e) {
            // Expected for this test
        }
        
        // When
        RetryService.RetryStatistics stats = retryService.getRetryStatistics("database");
        
        // Then
        assertNotNull(stats);
        assertEquals("database", stats.getName());
        assertEquals(3, stats.getMaxAttempts());
        assertTrue(stats.getTotalCalls() > 0);
    }
    
    @Test
    void testRetryStatisticsCalculations() {
        // Given
        RetryService.RetryStatistics stats = new RetryService.RetryStatistics(
            "test", 3, 5, 2, 1, 3
        );
        
        // When/Then
        assertEquals(11, stats.getTotalCalls()); // 5 + 2 + 1 + 3
        assertEquals(7.0 / 11.0, stats.getSuccessRate(), 0.001); // (5 + 2) / 11
        assertEquals(5.0 / 11.0, stats.getRetryRate(), 0.001); // (2 + 3) / 11
    }
    
    @Test
    void testRetryStatisticsWithZeroCalls() {
        // Given
        RetryService.RetryStatistics stats = new RetryService.RetryStatistics(
            "test", 3, 0, 0, 0, 0
        );
        
        // When/Then
        assertEquals(0, stats.getTotalCalls());
        assertEquals(0.0, stats.getSuccessRate());
        assertEquals(0.0, stats.getRetryRate());
    }
    
    @Test
    void testNonExistentRetryStatistics() {
        // When
        RetryService.RetryStatistics stats = retryService.getRetryStatistics("nonexistent");
        
        // Then
        assertNull(stats);
    }
}