package com.fabric.watcher.error;

import com.fabric.watcher.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.net.SocketException;
import java.security.AccessControlException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ErrorHandlerTest {
    
    @Mock
    private MetricsService metricsService;
    
    private ErrorHandler errorHandler;
    
    @BeforeEach
    void setUp() {
        errorHandler = new ErrorHandler(metricsService);
    }
    
    @Test
    void testHandleDatabaseError() {
        // Given
        DataAccessException exception = new DataAccessException("Connection failed") {};
        String context = "SQL execution";
        String operation = "INSERT INTO test_table";
        
        // When
        ErrorHandler.ErrorHandlingResult result = errorHandler.handleError(context, exception, operation);
        
        // Then
        assertEquals(ErrorCategory.DATABASE, result.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.EXPONENTIAL_BACKOFF, result.getStrategy());
        assertEquals(1, result.getOccurrenceCount());
        assertFalse(result.shouldAlert()); // First occurrence of database error shouldn't alert
        assertTrue(result.getMessage().contains("Database operation failed"));
        
        verify(metricsService).recordError(ErrorCategory.DATABASE, context);
    }
    
    @Test
    void testHandleFileSystemError() {
        // Given
        IOException exception = new IOException("Permission denied");
        String context = "File processing";
        String operation = "Move file to completed folder";
        
        // When
        ErrorHandler.ErrorHandlingResult result = errorHandler.handleError(context, exception, operation);
        
        // Then
        assertEquals(ErrorCategory.FILE_SYSTEM, result.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.LINEAR_BACKOFF, result.getStrategy());
        assertEquals(1, result.getOccurrenceCount());
        assertFalse(result.shouldAlert());
        assertTrue(result.getMessage().contains("File system operation failed"));
        
        verify(metricsService).recordError(ErrorCategory.FILE_SYSTEM, context);
    }
    
    @Test
    void testHandleNetworkError() {
        // Given
        SocketException exception = new SocketException("Connection timeout");
        String context = "External service call";
        String operation = "API request";
        
        // When
        ErrorHandler.ErrorHandlingResult result = errorHandler.handleError(context, exception, operation);
        
        // Then
        assertEquals(ErrorCategory.NETWORK, result.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.EXPONENTIAL_BACKOFF, result.getStrategy());
        assertEquals(1, result.getOccurrenceCount());
        assertFalse(result.shouldAlert());
        assertTrue(result.getMessage().contains("Network operation failed"));
        
        verify(metricsService).recordError(ErrorCategory.NETWORK, context);
    }
    
    @Test
    void testHandleSecurityError() {
        // Given
        AccessControlException exception = new AccessControlException("Access denied");
        String context = "File access";
        String operation = "Read configuration file";
        
        // When
        ErrorHandler.ErrorHandlingResult result = errorHandler.handleError(context, exception, operation);
        
        // Then
        assertEquals(ErrorCategory.SECURITY, result.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.FAIL_FAST, result.getStrategy());
        assertEquals(1, result.getOccurrenceCount());
        assertTrue(result.shouldAlert()); // Security errors should alert immediately
        assertTrue(result.getMessage().contains("Security violation"));
        
        verify(metricsService).recordError(ErrorCategory.SECURITY, context);
    }
    
    @Test
    void testRepeatedErrorsChangeStrategy() {
        // Given
        DataAccessException exception = new DataAccessException("Connection failed") {};
        String context = "SQL execution";
        String operation = "INSERT INTO test_table";
        
        // When - simulate multiple occurrences
        for (int i = 0; i < 12; i++) {
            errorHandler.handleError(context, exception, operation);
        }
        
        ErrorHandler.ErrorHandlingResult result = errorHandler.handleError(context, exception, operation);
        
        // Then
        assertEquals(ErrorCategory.DATABASE, result.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.CIRCUIT_BREAK, result.getStrategy());
        assertEquals(13, result.getOccurrenceCount());
        assertTrue(result.shouldAlert()); // High error rate should trigger alert
    }
    
    @Test
    void testErrorPatternTracking() {
        // Given
        DataAccessException exception = new DataAccessException("Connection failed") {};
        String context = "SQL execution";
        String operation = "INSERT INTO test_table";
        
        // When
        errorHandler.handleError(context, exception, operation);
        errorHandler.handleError(context, exception, operation);
        errorHandler.handleError(context, exception, operation);
        
        // Then
        var errorPatterns = errorHandler.getErrorPatterns();
        assertFalse(errorPatterns.isEmpty());
        
        ErrorHandler.ErrorPattern pattern = errorPatterns.values().iterator().next();
        assertEquals(3, pattern.getOccurrenceCount());
        assertEquals(context, pattern.getContext());
        assertEquals(ErrorCategory.DATABASE, pattern.getCategory());
        assertNotNull(pattern.getFirstOccurrence());
        assertNotNull(pattern.getLastOccurrence());
    }
    
    @Test
    void testClearErrorPatterns() {
        // Given
        DataAccessException exception = new DataAccessException("Connection failed") {};
        String context = "SQL execution";
        String operation = "INSERT INTO test_table";
        
        errorHandler.handleError(context, exception, operation);
        assertFalse(errorHandler.getErrorPatterns().isEmpty());
        
        // When
        errorHandler.clearErrorPatterns();
        
        // Then
        assertTrue(errorHandler.getErrorPatterns().isEmpty());
    }
    
    @Test
    void testNullExceptionHandling() {
        // Given
        String context = "Test context";
        String operation = "Test operation";
        
        // When
        ErrorHandler.ErrorHandlingResult result = errorHandler.handleError(context, null, operation);
        
        // Then
        assertEquals(ErrorCategory.UNKNOWN, result.getCategory());
        assertEquals(ErrorHandler.RecoveryStrategy.FAIL_FAST, result.getStrategy());
        assertEquals(1, result.getOccurrenceCount());
        assertFalse(result.shouldAlert());
        
        verify(metricsService).recordError(ErrorCategory.UNKNOWN, context);
    }
    
    @Test
    void testErrorCategorization() {
        // Test various exception types are categorized correctly
        assertEquals(ErrorCategory.DATABASE, ErrorCategory.categorize(new DataAccessException("test") {}));
        assertEquals(ErrorCategory.FILE_SYSTEM, ErrorCategory.categorize(new IOException("test")));
        assertEquals(ErrorCategory.NETWORK, ErrorCategory.categorize(new SocketException("test")));
        assertEquals(ErrorCategory.SECURITY, ErrorCategory.categorize(new AccessControlException("test")));
        assertEquals(ErrorCategory.RESOURCE, ErrorCategory.categorize(new OutOfMemoryError("test")));
        assertEquals(ErrorCategory.APPLICATION, ErrorCategory.categorize(new IllegalArgumentException("test")));
        assertEquals(ErrorCategory.UNKNOWN, ErrorCategory.categorize(new RuntimeException("test")));
    }
    
    @Test
    void testRetryableCategories() {
        // Test which categories are retryable
        assertTrue(ErrorCategory.DATABASE.isRetryable());
        assertTrue(ErrorCategory.FILE_SYSTEM.isRetryable());
        assertTrue(ErrorCategory.NETWORK.isRetryable());
        assertTrue(ErrorCategory.EXTERNAL_SYSTEM.isRetryable());
        assertTrue(ErrorCategory.RESOURCE.isRetryable());
        
        assertFalse(ErrorCategory.SECURITY.isRetryable());
        assertFalse(ErrorCategory.APPLICATION.isRetryable());
        assertFalse(ErrorCategory.UNKNOWN.isRetryable());
    }
}