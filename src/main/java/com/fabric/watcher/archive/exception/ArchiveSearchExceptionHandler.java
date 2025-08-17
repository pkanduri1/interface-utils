package com.fabric.watcher.archive.exception;

import com.fabric.watcher.archive.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for archive search operations.
 * 
 * <p>This class handles exceptions thrown by archive search controllers
 * and converts them to appropriate HTTP responses with error details.</p>
 * 
 * @since 1.0
 */
@ControllerAdvice
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class ArchiveSearchExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchExceptionHandler.class);

    /**
     * Handles ArchiveSearchException instances.
     *
     * @param ex      the archive search exception
     * @param request the web request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(ArchiveSearchException.class)
    public ResponseEntity<ErrorResponse> handleArchiveSearchException(ArchiveSearchException ex, WebRequest request) {
        logger.debug("Handling ArchiveSearchException: {} - {}", ex.getErrorCode().getCode(), ex.getMessage());

        HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
        
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode().getCode(),
            ex.getMessage(),
            ex.getDetails(),
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handles validation errors from request body validation.
     *
     * @param ex      the method argument not valid exception
     * @param request the web request
     * @return ResponseEntity with validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        logger.debug("Handling validation exception with {} field errors", ex.getBindingResult().getFieldErrorCount());

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        String details = "Validation failed for fields: " + fieldErrors.toString();
        
        ErrorResponse errorResponse = new ErrorResponse(
            "ARCH021",
            "Invalid request parameters",
            details,
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles security exceptions.
     *
     * @param ex      the security exception
     * @param request the web request
     * @return ResponseEntity with security error details
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex, WebRequest request) {
        logger.warn("Security exception: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            "ARCH006",
            "Access denied",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handles illegal argument exceptions.
     *
     * @param ex      the illegal argument exception
     * @param request the web request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        logger.debug("Illegal argument exception: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            "ARCH021",
            "Invalid request parameters",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles all other unexpected exceptions.
     *
     * @param ex      the exception
     * @param request the web request
     * @return ResponseEntity with generic error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        logger.error("Unexpected exception in archive search operation", ex);

        ErrorResponse errorResponse = new ErrorResponse(
            "ARCH999",
            "Internal server error",
            "An unexpected error occurred. Please try again later.",
            request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Maps error codes to appropriate HTTP status codes.
     *
     * @param errorCode the error code
     * @return the corresponding HTTP status
     */
    private HttpStatus mapErrorCodeToHttpStatus(ArchiveSearchException.ErrorCode errorCode) {
        switch (errorCode) {
            case AUTHENTICATION_REQUIRED:
            case AUTHENTICATION_FAILED:
            case INVALID_TOKEN:
            case TOKEN_EXPIRED:
                return HttpStatus.UNAUTHORIZED;
                
            case USER_LOCKED_OUT:
                return HttpStatus.LOCKED;
                
            case PATH_NOT_ALLOWED:
                return HttpStatus.FORBIDDEN;
                
            case PATH_NOT_FOUND:
            case FILE_NOT_FOUND:
                return HttpStatus.NOT_FOUND;
                
            case FILE_ALREADY_EXISTS:
                return HttpStatus.CONFLICT;
                
            case INVALID_FILE_TYPE:
            case INVALID_REQUEST:
                return HttpStatus.BAD_REQUEST;
                
            case FILE_TOO_LARGE:
                return HttpStatus.PAYLOAD_TOO_LARGE;
                
            case UPLOAD_LIMIT_EXCEEDED:
                return HttpStatus.TOO_MANY_REQUESTS;
                
            case SEARCH_TIMEOUT:
                return HttpStatus.REQUEST_TIMEOUT;
                
            case ENVIRONMENT_RESTRICTED:
                return HttpStatus.SERVICE_UNAVAILABLE;
                
            case NOT_IMPLEMENTED:
                return HttpStatus.NOT_IMPLEMENTED;
                
            case SEARCH_FAILED:
            case DOWNLOAD_FAILED:
            case UPLOAD_FAILED:
            case ARCHIVE_CORRUPTED:
            case UNSUPPORTED_FORMAT:
            case TOKEN_GENERATION_ERROR:
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}