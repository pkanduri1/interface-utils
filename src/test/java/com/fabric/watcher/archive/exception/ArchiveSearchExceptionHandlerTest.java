package com.fabric.watcher.archive.exception;

import com.fabric.watcher.archive.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ArchiveSearchExceptionHandler.
 * Tests error handling and HTTP status code mapping for various exception types.
 */
@ExtendWith(MockitoExtension.class)
class ArchiveSearchExceptionHandlerTest {

    @InjectMocks
    private ArchiveSearchExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/archive/search");
    }

    @Test
    void testHandleArchiveSearchException_PathNotAllowed() {
        // Given
        ArchiveSearchException exception = new ArchiveSearchException(
            ArchiveSearchException.ErrorCode.PATH_NOT_ALLOWED,
            "Access denied to path",
            "/forbidden/path"
        );

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleArchiveSearchException(exception, request);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH001", response.getBody().getErrorCode());
        assertEquals("Access denied to path", response.getBody().getMessage());
        assertEquals("/forbidden/path", response.getBody().getDetails());
        assertEquals("/api/v1/archive/search", response.getBody().getPath());
    }

    @Test
    void testHandleArchiveSearchException_FileNotFound() {
        // Given
        ArchiveSearchException exception = new ArchiveSearchException(
            ArchiveSearchException.ErrorCode.FILE_NOT_FOUND,
            "File does not exist"
        );

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleArchiveSearchException(exception, request);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH002", response.getBody().getErrorCode());
        assertEquals("File does not exist", response.getBody().getMessage());
    }

    @Test
    void testHandleArchiveSearchException_ArchiveCorrupted() {
        // Given
        ArchiveSearchException exception = new ArchiveSearchException(
            ArchiveSearchException.ErrorCode.ARCHIVE_CORRUPTED,
            "Archive file is corrupted",
            "Invalid ZIP header"
        );

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleArchiveSearchException(exception, request);

        // Then
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH003", response.getBody().getErrorCode());
        assertEquals("Archive file is corrupted", response.getBody().getMessage());
        assertEquals("Invalid ZIP header", response.getBody().getDetails());
    }

    @Test
    void testHandleArchiveSearchException_SearchTimeout() {
        // Given
        ArchiveSearchException exception = new ArchiveSearchException(
            ArchiveSearchException.ErrorCode.SEARCH_TIMEOUT,
            "Search operation timed out"
        );

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleArchiveSearchException(exception, request);

        // Then
        assertEquals(HttpStatus.REQUEST_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH004", response.getBody().getErrorCode());
        assertEquals("Search operation timed out", response.getBody().getMessage());
    }

    @Test
    void testHandleArchiveSearchException_EnvironmentRestricted() {
        // Given
        ArchiveSearchException exception = new ArchiveSearchException(
            ArchiveSearchException.ErrorCode.ENVIRONMENT_RESTRICTED,
            "Feature disabled in production"
        );

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleArchiveSearchException(exception, request);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH006", response.getBody().getErrorCode());
        assertEquals("Feature disabled in production", response.getBody().getMessage());
    }

    @Test
    void testHandleArchiveSearchException_InvalidParameter() {
        // Given
        ArchiveSearchException exception = new ArchiveSearchException(
            ArchiveSearchException.ErrorCode.INVALID_PARAMETER,
            "Invalid parameter provided"
        );

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleArchiveSearchException(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH007", response.getBody().getErrorCode());
        assertEquals("Invalid parameter provided", response.getBody().getMessage());
    }

    @Test
    void testHandleSecurityException() {
        // Given
        SecurityException exception = new SecurityException("Security violation detected");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleSecurityException(exception, request);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH009", response.getBody().getErrorCode());
        assertEquals("Access denied", response.getBody().getMessage());
        assertEquals("Security violation detected", response.getBody().getDetails());
    }

    @Test
    void testHandleAccessDeniedException() {
        // Given
        AccessDeniedException exception = new AccessDeniedException("Access denied to file");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleSecurityException(exception, request);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH009", response.getBody().getErrorCode());
        assertEquals("Access denied", response.getBody().getMessage());
        assertEquals("Access denied to file", response.getBody().getDetails());
    }

    @Test
    void testHandleValidationException() {
        // Given
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("request", "path", "Path is required");
        FieldError fieldError2 = new FieldError("request", "pattern", "Pattern cannot be empty");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));
        when(exception.getMessage()).thenReturn("Validation failed");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH007", response.getBody().getErrorCode());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertTrue(response.getBody().getDetails().contains("Path is required"));
        assertTrue(response.getBody().getDetails().contains("Pattern cannot be empty"));
    }

    @Test
    void testHandleConstraintViolationException() {
        // Given
        ConstraintViolationException exception = mock(ConstraintViolationException.class);
        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        
        when(violation1.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation1.getPropertyPath().toString()).thenReturn("path");
        when(violation1.getMessage()).thenReturn("must not be null");
        
        when(violation2.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation2.getPropertyPath().toString()).thenReturn("pattern");
        when(violation2.getMessage()).thenReturn("must not be empty");
        
        when(exception.getConstraintViolations()).thenReturn(Set.of(violation1, violation2));
        when(exception.getMessage()).thenReturn("Constraint violation");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleConstraintViolationException(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH007", response.getBody().getErrorCode());
        assertEquals("Parameter validation failed", response.getBody().getMessage());
        assertTrue(response.getBody().getDetails().contains("path: must not be null"));
        assertTrue(response.getBody().getDetails().contains("pattern: must not be empty"));
    }

    @Test
    void testHandleIllegalArgumentException() {
        // Given
        IllegalArgumentException exception = new IllegalArgumentException("Invalid argument provided");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgumentException(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH007", response.getBody().getErrorCode());
        assertEquals("Invalid parameter", response.getBody().getMessage());
        assertEquals("Invalid argument provided", response.getBody().getDetails());
    }

    @Test
    void testHandleGenericException() {
        // Given
        RuntimeException exception = new RuntimeException("Unexpected error occurred");

        // When
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(exception, request);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ARCH999", response.getBody().getErrorCode());
        assertEquals("Internal server error", response.getBody().getMessage());
        assertEquals("An unexpected error occurred", response.getBody().getDetails());
    }

    @Test
    void testErrorCodeToHttpStatusMapping() {
        // Test all error codes map to appropriate HTTP status codes
        assertEquals(HttpStatus.FORBIDDEN, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.PATH_NOT_ALLOWED));
        assertEquals(HttpStatus.FORBIDDEN, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.SECURITY_VIOLATION));
        assertEquals(HttpStatus.NOT_FOUND, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.FILE_NOT_FOUND));
        assertEquals(HttpStatus.BAD_REQUEST, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.INVALID_PARAMETER));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.ENVIRONMENT_RESTRICTED));
        assertEquals(HttpStatus.REQUEST_TIMEOUT, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.SEARCH_TIMEOUT));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.ARCHIVE_CORRUPTED));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.UNSUPPORTED_FORMAT));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, 
            getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode.IO_ERROR));
    }

    private HttpStatus getHttpStatusForErrorCode(ArchiveSearchException.ErrorCode errorCode) {
        ArchiveSearchException exception = new ArchiveSearchException(errorCode, "Test message");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleArchiveSearchException(exception, request);
        return HttpStatus.valueOf(response.getStatusCode().value());
    }
}