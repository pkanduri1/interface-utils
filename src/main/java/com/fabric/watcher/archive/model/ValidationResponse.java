package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Response model for session validation.
 * 
 * <p>This class represents the response for session validation requests,
 * indicating whether the current session/token is valid.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Session validation response")
public class ValidationResponse {

    /**
     * Whether the session/token is valid.
     */
    @Schema(description = "Whether the session/token is valid", example = "true")
    @JsonProperty("valid")
    private boolean valid;

    /**
     * The user ID associated with the session.
     */
    @Schema(description = "User ID associated with the session", example = "john.doe")
    @JsonProperty("userId")
    private String userId;

    /**
     * Remaining time until token expiration in seconds.
     */
    @Schema(description = "Remaining time until token expiration in seconds", example = "1800")
    @JsonProperty("remainingTime")
    private Long remainingTime;

    /**
     * Error message if validation failed.
     */
    @Schema(description = "Error message if validation failed", example = "Token expired")
    @JsonProperty("message")
    private String message;

    /**
     * Default constructor.
     */
    public ValidationResponse() {
    }

    /**
     * Constructor for valid session.
     *
     * @param userId        the user ID
     * @param remainingTime the remaining time until expiration
     */
    public ValidationResponse(String userId, Long remainingTime) {
        this.valid = true;
        this.userId = userId;
        this.remainingTime = remainingTime;
    }

    /**
     * Constructor for invalid session.
     *
     * @param message the error message
     */
    public ValidationResponse(String message) {
        this.valid = false;
        this.message = message;
    }

    /**
     * Gets whether the session is valid.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Sets whether the session is valid.
     *
     * @param valid the validity status
     */
    public void setValid(boolean valid) {
        this.valid = valid;
    }

    /**
     * Gets the user ID.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID.
     *
     * @param userId the user ID to set
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Gets the remaining time until expiration.
     *
     * @return the remaining time in seconds
     */
    public Long getRemainingTime() {
        return remainingTime;
    }

    /**
     * Sets the remaining time until expiration.
     *
     * @param remainingTime the remaining time in seconds
     */
    public void setRemainingTime(Long remainingTime) {
        this.remainingTime = remainingTime;
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

    @Override
    public String toString() {
        return "ValidationResponse{" +
                "valid=" + valid +
                ", userId='" + userId + '\'' +
                ", remainingTime=" + remainingTime +
                ", message='" + message + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResponse that = (ValidationResponse) o;
        return valid == that.valid &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(remainingTime, that.remainingTime) &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valid, userId, remainingTime, message);
    }
}