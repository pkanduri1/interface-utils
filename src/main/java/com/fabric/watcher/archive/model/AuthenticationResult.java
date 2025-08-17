package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Result model for authentication operations.
 * 
 * <p>This class represents the result of an authentication attempt,
 * containing success status, user information, and error details if applicable.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Result of an authentication operation")
public class AuthenticationResult {

    /**
     * Whether the authentication was successful.
     */
    @Schema(description = "Whether the authentication was successful", example = "true")
    @JsonProperty("success")
    private boolean success;

    /**
     * The authenticated user ID.
     */
    @Schema(description = "Authenticated user ID", example = "john.doe")
    @JsonProperty("userId")
    private String userId;

    /**
     * Error message if authentication failed.
     */
    @Schema(description = "Error message if authentication failed", example = "Invalid credentials")
    @JsonProperty("errorMessage")
    private String errorMessage;

    /**
     * User details if authentication was successful.
     */
    @Schema(description = "User details if authentication was successful")
    @JsonProperty("userDetails")
    private UserDetails userDetails;

    /**
     * Default constructor.
     */
    public AuthenticationResult() {
    }

    /**
     * Constructor for successful authentication.
     *
     * @param userId      the authenticated user ID
     * @param userDetails the user details
     */
    public AuthenticationResult(String userId, UserDetails userDetails) {
        this.success = true;
        this.userId = userId;
        this.userDetails = userDetails;
        this.errorMessage = null;
    }

    /**
     * Constructor for failed authentication.
     *
     * @param errorMessage the error message
     */
    public AuthenticationResult(String errorMessage) {
        this.success = false;
        this.userId = null;
        this.userDetails = null;
        this.errorMessage = errorMessage;
    }

    /**
     * Constructor with all parameters.
     *
     * @param success      whether the authentication was successful
     * @param userId       the user ID
     * @param errorMessage the error message
     * @param userDetails  the user details
     */
    public AuthenticationResult(boolean success, String userId, String errorMessage, UserDetails userDetails) {
        this.success = success;
        this.userId = userId;
        this.errorMessage = errorMessage;
        this.userDetails = userDetails;
    }

    /**
     * Gets whether the authentication was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets whether the authentication was successful.
     *
     * @param success true if successful, false otherwise
     */
    public void setSuccess(boolean success) {
        this.success = success;
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
     * Gets the error message.
     *
     * @return the error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error message.
     *
     * @param errorMessage the error message to set
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Gets the user details.
     *
     * @return the user details
     */
    public UserDetails getUserDetails() {
        return userDetails;
    }

    /**
     * Sets the user details.
     *
     * @param userDetails the user details to set
     */
    public void setUserDetails(UserDetails userDetails) {
        this.userDetails = userDetails;
    }

    @Override
    public String toString() {
        return "AuthenticationResult{" +
                "success=" + success +
                ", userId='" + userId + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", userDetails=" + userDetails +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthenticationResult that = (AuthenticationResult) o;
        return success == that.success &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(errorMessage, that.errorMessage) &&
                Objects.equals(userDetails, that.userDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, userId, errorMessage, userDetails);
    }
}