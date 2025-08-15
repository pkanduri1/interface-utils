package com.fabric.watcher.archive.exception;

import com.fabric.watcher.archive.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

/**
 * Global exception handler for archive search API operations.
 * Provides consistent error responses across all archive search endpoints.
 */
@ControllerAdvice(basePackages = "com.fabric.watcher.archive.controller")
public class ArchiveSearchExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchExceptionHandler.class);

    /**
     * Handle ArchiveSearchException with specific error codes
     */
    @ExceptionHandler(ArchiveSearchException.class)
    public ResponseEntity<ErrorResponse> handleArchiveSearchException(
            ArchiveSearchException ex, HttpServletRequest request) {
        
        logger.error("Archive search error [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage(), ex);
        
        HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
        ErrorResponse errorResponse = new ErrorResponse(
            ex.getErrorCode().getCode(),
            ex.getMessage(),
            ex.getDetails(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle security exceptions (path traversal, access denied, etc.)
     */
    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleSecurityException(
            Exception ex, HttpServletRequest request) {
        
        logger.error("Security violation in archive search: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            ArchiveSearchException.ErrorCode.SECURITY_VIOLATION.getCode(),
            "Access denied",
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle validation errors for request parameters
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        logger.error("Validation error in archive search request: {}", ex.getMessage());
        
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        
        ErrorResponse errorResponse = new ErrorResponse(
            ArchiveSearchException.ErrorCode.INVALID_PARAMETER.getCode(),
            "Validation failed",
            details,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle constraint violation exceptions
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        logger.error("Constraint violation in archive search: {}", ex.getMessage());
        
        String details = ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.joining(", "));
        
        ErrorResponse errorResponse = new ErrorResponse(
            ArchiveSearchException.ErrorCode.INVALID_PARAMETER.getCode(),
            "Parameter validation failed",
            details,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        
        logger.error("Invalid argument in archive search: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
            ArchiveSearchException.ErrorCode.INVALID_PARAMETER.getCode(),
            "Invalid parameter",
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle generic exceptions as internal server errors
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        logger.error("Unexpected error in archive search: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            "ARCH999",
            "Internal server error",
            "An unexpected error occurred",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Map error codes to appropriate HTTP status codes
     */
    private HttpStatus mapErrorCodeToHttpStatus(ArchiveSearchException.ErrorCode errorCode) {
        return switch (errorCode) {
            case PATH_NOT_ALLOWED, SECURITY_VIOLATION -> HttpStatus.FORBIDDEN;
            case FILE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_PARAMETER -> HttpStatus.BAD_REQUEST;
            case ENVIRONMENT_RESTRICTED -> HttpStatus.SERVICE_UNAVAILABLE;
            case SEARCH_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            case ARCHIVE_CORRUPTED, UNSUPPORTED_FORMAT, IO_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}