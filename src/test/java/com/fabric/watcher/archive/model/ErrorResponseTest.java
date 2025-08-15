package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorResponse model.
 * Tests error response creation, serialization, and field validation.
 */
class ErrorResponseTest {

    @Test
    void testDefaultConstructor() {
        // When
        ErrorResponse errorResponse = new ErrorResponse();

        // Then
        assertNotNull(errorResponse.getTimestamp());
        assertNull(errorResponse.getErrorCode());
        assertNull(errorResponse.getMessage());
        assertNull(errorResponse.getDetails());
        assertNull(errorResponse.getPath());
    }

    @Test
    void testConstructorWithErrorCodeAndMessage() {
        // Given
        String errorCode = "ARCH001";
        String message = "Test error message";

        // When
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message);

        // Then
        assertEquals(errorCode, errorResponse.getErrorCode());
        assertEquals(message, errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp());
        assertNull(errorResponse.getDetails());
        assertNull(errorResponse.getPath());
    }

    @Test
    void testConstructorWithDetails() {
        // Given
        String errorCode = "ARCH002";
        String message = "File not found";
        String details = "The specified file does not exist";

        // When
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message, details);

        // Then
        assertEquals(errorCode, errorResponse.getErrorCode());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(details, errorResponse.getDetails());
        assertNotNull(errorResponse.getTimestamp());
        assertNull(errorResponse.getPath());
    }

    @Test
    void testFullConstructor() {
        // Given
        String errorCode = "ARCH003";
        String message = "Archive corrupted";
        String details = "Invalid ZIP header";
        String path = "/api/v1/archive/search";

        // When
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message, details, path);

        // Then
        assertEquals(errorCode, errorResponse.getErrorCode());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(details, errorResponse.getDetails());
        assertEquals(path, errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testSettersAndGetters() {
        // Given
        ErrorResponse errorResponse = new ErrorResponse();
        String errorCode = "ARCH004";
        String message = "Timeout occurred";
        String details = "Operation exceeded time limit";
        String path = "/api/v1/archive/download";
        LocalDateTime timestamp = LocalDateTime.now();

        // When
        errorResponse.setErrorCode(errorCode);
        errorResponse.setMessage(message);
        errorResponse.setDetails(details);
        errorResponse.setPath(path);
        errorResponse.setTimestamp(timestamp);

        // Then
        assertEquals(errorCode, errorResponse.getErrorCode());
        assertEquals(message, errorResponse.getMessage());
        assertEquals(details, errorResponse.getDetails());
        assertEquals(path, errorResponse.getPath());
        assertEquals(timestamp, errorResponse.getTimestamp());
    }

    @Test
    void testTimestampIsSetAutomatically() {
        // Given
        LocalDateTime beforeCreation = LocalDateTime.now();

        // When
        ErrorResponse errorResponse = new ErrorResponse("ARCH001", "Test message");
        LocalDateTime afterCreation = LocalDateTime.now();

        // Then
        assertNotNull(errorResponse.getTimestamp());
        assertTrue(errorResponse.getTimestamp().isAfter(beforeCreation.minusSeconds(1)));
        assertTrue(errorResponse.getTimestamp().isBefore(afterCreation.plusSeconds(1)));
    }

    @Test
    void testToString() {
        // Given
        String errorCode = "ARCH005";
        String message = "Unsupported format";
        String details = "File format not supported";
        String path = "/api/v1/archive/search";
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message, details, path);

        // When
        String toString = errorResponse.toString();

        // Then
        assertTrue(toString.contains("ErrorResponse{"));
        assertTrue(toString.contains("errorCode='" + errorCode + "'"));
        assertTrue(toString.contains("message='" + message + "'"));
        assertTrue(toString.contains("details='" + details + "'"));
        assertTrue(toString.contains("path='" + path + "'"));
        assertTrue(toString.contains("timestamp="));
    }

    @Test
    void testToStringWithNullValues() {
        // Given
        ErrorResponse errorResponse = new ErrorResponse();

        // When
        String toString = errorResponse.toString();

        // Then
        assertTrue(toString.contains("ErrorResponse{"));
        assertTrue(toString.contains("errorCode='null'"));
        assertTrue(toString.contains("message='null'"));
        assertTrue(toString.contains("details='null'"));
        assertTrue(toString.contains("path='null'"));
        assertTrue(toString.contains("timestamp="));
    }

    @Test
    void testErrorResponseImmutabilityAfterCreation() {
        // Given
        String originalErrorCode = "ARCH001";
        String originalMessage = "Original message";
        ErrorResponse errorResponse = new ErrorResponse(originalErrorCode, originalMessage);
        LocalDateTime originalTimestamp = errorResponse.getTimestamp();

        // When - modify fields
        errorResponse.setErrorCode("ARCH999");
        errorResponse.setMessage("Modified message");

        // Then - fields should be modifiable (this is expected behavior)
        assertEquals("ARCH999", errorResponse.getErrorCode());
        assertEquals("Modified message", errorResponse.getMessage());
        assertEquals(originalTimestamp, errorResponse.getTimestamp());
    }

    @Test
    void testErrorResponseWithEmptyStrings() {
        // Given
        String errorCode = "";
        String message = "";
        String details = "";
        String path = "";

        // When
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message, details, path);

        // Then
        assertEquals("", errorResponse.getErrorCode());
        assertEquals("", errorResponse.getMessage());
        assertEquals("", errorResponse.getDetails());
        assertEquals("", errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
    }
}