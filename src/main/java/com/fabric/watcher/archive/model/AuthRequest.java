package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request model for user authentication.
 * 
 * <p>This class represents the authentication request containing user credentials
 * for LDAP-based authentication.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Authentication request containing user credentials")
public class AuthRequest {

    /**
     * The user ID for authentication.
     */
    @Schema(description = "User ID for authentication", example = "john.doe", required = true)
    @NotBlank(message = "User ID cannot be blank")
    @Size(min = 1, max = 100, message = "User ID must be between 1 and 100 characters")
    @JsonProperty("userId")
    private String userId;

    /**
     * The password for authentication.
     */
    @Schema(description = "User password for authentication", required = true)
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 1, max = 255, message = "Password must be between 1 and 255 characters")
    @JsonProperty("password")
    private String password;

    /**
     * Default constructor.
     */
    public AuthRequest() {
    }

    /**
     * Constructor with parameters.
     *
     * @param userId   the user ID
     * @param password the password
     */
    public AuthRequest(String userId, String password) {
        this.userId = userId;
        this.password = password;
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
     * Gets the password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     *
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "AuthRequest{" +
                "userId='" + userId + '\'' +
                ", password='[PROTECTED]'" +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuthRequest that = (AuthRequest) o;

        if (userId != null ? !userId.equals(that.userId) : that.userId != null) return false;
        return password != null ? password.equals(that.password) : that.password == null;
    }

    @Override
    public int hashCode() {
        int result = userId != null ? userId.hashCode() : 0;
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }
}