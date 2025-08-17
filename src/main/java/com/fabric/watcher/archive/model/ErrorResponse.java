package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Response model for error conditions.
 * 
 * <p>This class represents an error response containing error details,
 * codes, and contextual information for API error handling.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Error response containing error details and context")
public class ErrorResponse {

    /**
     * The error code.
     */
    @Schema(description = "The error code", example = "ARCH001")
    @JsonProperty("errorCode")
    private String errorCode;

    /**
     * The error message.
     */
    @Schema(description = "The error message", example = "Path access denied")
    @JsonProperty("message")
    private String message;

    /**
     * Additional error details.
     */
    @Schema(description = "Additional error details", example = "The specified path is not within allowed directories")
    @JsonProperty("details")
    private String details;

    /**
     * The timestamp when the error occurred.
     */
    @Schema(description = "The timestamp when the error occurred")
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    /**
     * The request path that caused the error.
     */
    @Schema(description = "The request path that caused the error", example = "/api/v1/archive/search")
    @JsonProperty("path")
    private String path;

    /**
     * Default constructor.
     */
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor with basic error information.
     *
     * @param errorCode the error code
     * @param message   the error message
     */
    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor with all parameters.
     *
     * @param errorCode the error code
     * @param message   the error message
     * @param details   additional error details
     * @param path      the request path
     */
    public ErrorResponse(String errorCode, String message, String details, String path) {
        this.errorCode = errorCode;
        this.message = message;
        this.details = details;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Sets the error code.
     *
     * @param errorCode the error code to set
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Gets the error message.
     *
     * @return the error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the error message.
     *
     * @param message the error message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the error details.
     *
     * @return the error details
     */
    public String getDetails() {
        return details;
    }

    /**
     * Sets the error details.
     *
     * @param details the error details to set
     */
    public void setDetails(String details) {
        this.details = details;
    }

    /**
     * Gets the timestamp.
     *
     * @return the timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp.
     *
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the request path.
     *
     * @return the request path
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the request path.
     *
     * @param path the request path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "errorCode='" + errorCode + '\'' +
                ", message='" + message + '\'' +
                ", details='" + details + '\'' +
                ", timestamp=" + timestamp +
                ", path='" + path + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErrorResponse that = (ErrorResponse) o;
        return Objects.equals(errorCode, that.errorCode) &&
                Objects.equals(message, that.message) &&
                Objects.equals(details, that.details) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, message, details, timestamp, path);
    }
}