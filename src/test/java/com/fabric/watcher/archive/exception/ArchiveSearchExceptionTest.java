package com.fabric.watcher.archive.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArchiveSearchException class.
 * Tests exception creation, error codes, and message handling.
 */
class ArchiveSearchExceptionTest {

    @Test
    void testExceptionWithErrorCodeAndMessage() {
        // Given
        ArchiveSearchException.ErrorCode errorCode = ArchiveSearchException.ErrorCode.FILE_NOT_FOUND;
        String message = "Test file not found";

        // When
        ArchiveSearchException exception = new ArchiveSearchException(errorCode, message);

        // Then
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertNull(exception.getDetails());
        assertNull(exception.getCause());
    }

    @Test
    void testExceptionWithErrorCodeMessageAndDetails() {
        // Given
        ArchiveSearchException.ErrorCode errorCode = ArchiveSearchException.ErrorCode.ARCHIVE_CORRUPTED;
        String message = "Archive is corrupted";
        String details = "Invalid ZIP file header";

        // When
        ArchiveSearchException exception = new ArchiveSearchException(errorCode, message, details);

        // Then
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(details, exception.getDetails());
        assertNull(exception.getCause());
    }

    @Test
    void testExceptionWithErrorCodeMessageAndCause() {
        // Given
        ArchiveSearchException.ErrorCode errorCode = ArchiveSearchException.ErrorCode.IO_ERROR;
        String message = "IO operation failed";
        RuntimeException cause = new RuntimeException("Underlying cause");

        // When
        ArchiveSearchException exception = new ArchiveSearchException(errorCode, message, cause);

        // Then
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertNull(exception.getDetails());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testExceptionWithAllParameters() {
        // Given
        ArchiveSearchException.ErrorCode errorCode = ArchiveSearchException.ErrorCode.SEARCH_TIMEOUT;
        String message = "Search operation timed out";
        String details = "Operation exceeded 30 seconds";
        RuntimeException cause = new RuntimeException("Timeout cause");

        // When
        ArchiveSearchException exception = new ArchiveSearchException(errorCode, message, details, cause);

        // Then
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(message, exception.getMessage());
        assertEquals(details, exception.getDetails());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testErrorCodeProperties() {
        // Test each error code has proper code and default message
        ArchiveSearchException.ErrorCode[] errorCodes = ArchiveSearchException.ErrorCode.values();
        
        for (ArchiveSearchException.ErrorCode errorCode : errorCodes) {
            assertNotNull(errorCode.getCode(), "Error code should not be null");
            assertNotNull(errorCode.getDefaultMessage(), "Default message should not be null");
            assertFalse(errorCode.getCode().isEmpty(), "Error code should not be empty");
            assertFalse(errorCode.getDefaultMessage().isEmpty(), "Default message should not be empty");
        }
    }

    @Test
    void testSpecificErrorCodes() {
        // Test specific error codes have expected values
        assertEquals("ARCH001", ArchiveSearchException.ErrorCode.PATH_NOT_ALLOWED.getCode());
        assertEquals("Path access denied", ArchiveSearchException.ErrorCode.PATH_NOT_ALLOWED.getDefaultMessage());

        assertEquals("ARCH002", ArchiveSearchException.ErrorCode.FILE_NOT_FOUND.getCode());
        assertEquals("File not found", ArchiveSearchException.ErrorCode.FILE_NOT_FOUND.getDefaultMessage());

        assertEquals("ARCH003", ArchiveSearchException.ErrorCode.ARCHIVE_CORRUPTED.getCode());
        assertEquals("Archive file is corrupted", ArchiveSearchException.ErrorCode.ARCHIVE_CORRUPTED.getDefaultMessage());

        assertEquals("ARCH004", ArchiveSearchException.ErrorCode.SEARCH_TIMEOUT.getCode());
        assertEquals("Search operation timed out", ArchiveSearchException.ErrorCode.SEARCH_TIMEOUT.getDefaultMessage());

        assertEquals("ARCH005", ArchiveSearchException.ErrorCode.UNSUPPORTED_FORMAT.getCode());
        assertEquals("Unsupported file format", ArchiveSearchException.ErrorCode.UNSUPPORTED_FORMAT.getDefaultMessage());

        assertEquals("ARCH006", ArchiveSearchException.ErrorCode.ENVIRONMENT_RESTRICTED.getCode());
        assertEquals("Feature disabled in production", ArchiveSearchException.ErrorCode.ENVIRONMENT_RESTRICTED.getDefaultMessage());

        assertEquals("ARCH007", ArchiveSearchException.ErrorCode.INVALID_PARAMETER.getCode());
        assertEquals("Invalid parameter provided", ArchiveSearchException.ErrorCode.INVALID_PARAMETER.getDefaultMessage());

        assertEquals("ARCH008", ArchiveSearchException.ErrorCode.IO_ERROR.getCode());
        assertEquals("Input/output error occurred", ArchiveSearchException.ErrorCode.IO_ERROR.getDefaultMessage());

        assertEquals("ARCH009", ArchiveSearchException.ErrorCode.SECURITY_VIOLATION.getCode());
        assertEquals("Security violation detected", ArchiveSearchException.ErrorCode.SECURITY_VIOLATION.getDefaultMessage());
    }

    @Test
    void testExceptionIsRuntimeException() {
        // Given
        ArchiveSearchException exception = new ArchiveSearchException(
            ArchiveSearchException.ErrorCode.FILE_NOT_FOUND, "Test message");

        // Then
        assertTrue(exception instanceof RuntimeException);
    }
}