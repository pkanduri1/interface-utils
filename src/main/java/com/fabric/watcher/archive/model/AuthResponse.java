package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * Response model for user authentication.
 * 
 * <p>This class represents the authentication response containing authentication
 * token and user information after successful LDAP authentication.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Authentication response containing token and user information")
public class AuthResponse {

    /**
     * The authentication token.
     */
    @Schema(description = "JWT authentication token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @JsonProperty("token")
    private String token;

    /**
     * The authenticated user ID.
     */
    @Schema(description = "Authenticated user ID", example = "john.doe")
    @JsonProperty("userId")
    private String userId;

    /**
     * Token expiration time in seconds.
     */
    @Schema(description = "Token expiration time in seconds", example = "3600")
    @JsonProperty("expiresIn")
    private long expiresIn;

    /**
     * List of user permissions.
     */
    @Schema(description = "List of user permissions", example = "[\"file.read\", \"file.upload\"]")
    @JsonProperty("permissions")
    private List<String> permissions;

    /**
     * Default constructor.
     */
    public AuthResponse() {
    }

    /**
     * Constructor with parameters.
     *
     * @param token       the authentication token
     * @param userId      the user ID
     * @param expiresIn   the token expiration time in seconds
     * @param permissions the list of user permissions
     */
    public AuthResponse(String token, String userId, long expiresIn, List<String> permissions) {
        this.token = token;
        this.userId = userId;
        this.expiresIn = expiresIn;
        this.permissions = permissions;
    }

    /**
     * Gets the authentication token.
     *
     * @return the authentication token
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the authentication token.
     *
     * @param token the authentication token to set
     */
    public void setToken(String token) {
        this.token = token;
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
     * Gets the token expiration time.
     *
     * @return the token expiration time in seconds
     */
    public long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Sets the token expiration time.
     *
     * @param expiresIn the token expiration time in seconds to set
     */
    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    /**
     * Gets the list of user permissions.
     *
     * @return the list of user permissions
     */
    public List<String> getPermissions() {
        return permissions;
    }

    /**
     * Sets the list of user permissions.
     *
     * @param permissions the list of user permissions to set
     */
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    @Override
    public String toString() {
        return "AuthResponse{" +
                "token='[PROTECTED]'" +
                ", userId='" + userId + '\'' +
                ", expiresIn=" + expiresIn +
                ", permissions=" + permissions +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthResponse that = (AuthResponse) o;
        return expiresIn == that.expiresIn &&
                Objects.equals(token, that.token) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, userId, expiresIn, permissions);
    }
}