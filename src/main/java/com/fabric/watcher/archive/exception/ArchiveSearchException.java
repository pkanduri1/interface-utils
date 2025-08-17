package com.fabric.watcher.archive.exception;

/**
 * Exception class for archive search operations.
 * 
 * <p>This exception is thrown when archive search operations fail due to
 * various reasons such as invalid parameters, security violations, or
 * system errors.</p>
 * 
 * @since 1.0
 */
public class ArchiveSearchException extends RuntimeException {

    /**
     * Error codes for different types of archive search exceptions.
     */
    public enum ErrorCode {
        // Authentication and authorization errors
        AUTHENTICATION_REQUIRED("ARCH001", "Authentication required"),
        AUTHENTICATION_FAILED("ARCH002", "Authentication failed"),
        INVALID_TOKEN("ARCH003", "Invalid authentication token"),
        TOKEN_EXPIRED("ARCH004", "Authentication token expired"),
        USER_LOCKED_OUT("ARCH005", "User account locked out"),
        
        // Path and file access errors
        PATH_NOT_ALLOWED("ARCH006", "Path access denied"),
        PATH_NOT_FOUND("ARCH007", "Path not found"),
        FILE_NOT_FOUND("ARCH008", "File not found"),
        FILE_ALREADY_EXISTS("ARCH009", "File already exists"),
        
        // File validation errors
        INVALID_FILE_TYPE("ARCH010", "Invalid file type"),
        FILE_TOO_LARGE("ARCH011", "File too large"),
        
        // Operation errors
        SEARCH_FAILED("ARCH012", "Search operation failed"),
        DOWNLOAD_FAILED("ARCH013", "Download operation failed"),
        UPLOAD_FAILED("ARCH014", "Upload operation failed"),
        UPLOAD_LIMIT_EXCEEDED("ARCH015", "Upload limit exceeded"),
        
        // Archive processing errors
        ARCHIVE_CORRUPTED("ARCH016", "Archive file corrupted"),
        UNSUPPORTED_FORMAT("ARCH017", "Unsupported file format"),
        
        // System errors
        SEARCH_TIMEOUT("ARCH018", "Search operation timed out"),
        ENVIRONMENT_RESTRICTED("ARCH019", "Feature disabled in production"),
        TOKEN_GENERATION_ERROR("ARCH020", "Token generation failed"),
        
        // Request validation errors
        INVALID_REQUEST("ARCH021", "Invalid request parameters"),
        
        // Not implemented
        NOT_IMPLEMENTED("ARCH022", "Feature not implemented");

        private final String code;
        private final String message;

        ErrorCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    private final ErrorCode errorCode;
    private final String details;

    /**
     * Constructor with error code and message.
     *
     * @param errorCode the error code
     * @param message   the error message
     */
    public ArchiveSearchException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * Constructor with error code, message, and details.
     *
     * @param errorCode the error code
     * @param message   the error message
     * @param details   additional error details
     */
    public ArchiveSearchException(ErrorCode errorCode, String message, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * Constructor with error code, message, and cause.
     *
     * @param errorCode the error code
     * @param message   the error message
     * @param cause     the underlying cause
     */
    public ArchiveSearchException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * Constructor with error code, message, details, and cause.
     *
     * @param errorCode the error code
     * @param message   the error message
     * @param details   additional error details
     * @param cause     the underlying cause
     */
    public ArchiveSearchException(ErrorCode errorCode, String message, String details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the error details.
     *
     * @return the error details
     */
    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ArchiveSearchException{");
        sb.append("errorCode=").append(errorCode.getCode());
        sb.append(", message='").append(getMessage()).append('\'');
        if (details != null) {
            sb.append(", details='").append(details).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}