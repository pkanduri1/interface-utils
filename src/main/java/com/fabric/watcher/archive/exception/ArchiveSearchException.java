package com.fabric.watcher.archive.exception;

/**
 * Custom exception for archive search operations.
 * Provides specific error codes and details for different types of archive search failures.
 */
public class ArchiveSearchException extends RuntimeException {

    /**
     * Error code identifying the specific type of error
     */
    private final ErrorCode errorCode;

    /**
     * Additional details about the error
     */
    private final String details;

    /**
     * Constructor with error code and message
     */
    public ArchiveSearchException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * Constructor with error code, message, and details
     */
    public ArchiveSearchException(ErrorCode errorCode, String message, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * Constructor with error code, message, and cause
     */
    public ArchiveSearchException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * Constructor with error code, message, details, and cause
     */
    public ArchiveSearchException(ErrorCode errorCode, String message, String details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }

    /**
     * Enumeration of error codes for archive search operations
     */
    public enum ErrorCode {
        PATH_NOT_ALLOWED("ARCH001", "Path access denied"),
        FILE_NOT_FOUND("ARCH002", "File not found"),
        ARCHIVE_CORRUPTED("ARCH003", "Archive file is corrupted"),
        SEARCH_TIMEOUT("ARCH004", "Search operation timed out"),
        UNSUPPORTED_FORMAT("ARCH005", "Unsupported file format"),
        ENVIRONMENT_RESTRICTED("ARCH006", "Feature disabled in production"),
        INVALID_PARAMETER("ARCH007", "Invalid parameter provided"),
        IO_ERROR("ARCH008", "Input/output error occurred"),
        SECURITY_VIOLATION("ARCH009", "Security violation detected");

        private final String code;
        private final String defaultMessage;

        ErrorCode(String code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }

        public String getCode() {
            return code;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }
    }
}