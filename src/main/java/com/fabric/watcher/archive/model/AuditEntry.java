package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Audit entry model for logging user activities.
 * 
 * <p>This class represents an audit log entry containing information about
 * user activities for security and compliance purposes.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Audit entry for logging user activities")
public class AuditEntry {

    /**
     * Timestamp when the activity occurred.
     */
    @Schema(description = "Timestamp when the activity occurred")
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    /**
     * The user ID who performed the activity.
     */
    @Schema(description = "User ID who performed the activity", example = "john.doe")
    @JsonProperty("userId")
    private String userId;

    /**
     * The operation that was performed.
     */
    @Schema(description = "Operation that was performed", example = "FILE_UPLOAD")
    @JsonProperty("operation")
    private String operation;

    /**
     * The resource that was accessed or modified.
     */
    @Schema(description = "Resource that was accessed or modified", example = "/opt/uploads/config.properties")
    @JsonProperty("resource")
    private String resource;

    /**
     * Whether the operation was successful.
     */
    @Schema(description = "Whether the operation was successful", example = "true")
    @JsonProperty("success")
    private boolean success;

    /**
     * Additional details about the operation.
     */
    @Schema(description = "Additional details about the operation", example = "File uploaded successfully")
    @JsonProperty("details")
    private String details;

    /**
     * The IP address from which the operation was performed.
     */
    @Schema(description = "IP address from which the operation was performed", example = "192.168.1.100")
    @JsonProperty("ipAddress")
    private String ipAddress;

    /**
     * The session ID associated with the operation.
     */
    @Schema(description = "Session ID associated with the operation")
    @JsonProperty("sessionId")
    private String sessionId;

    /**
     * The user agent string from the client.
     */
    @Schema(description = "User agent string from the client")
    @JsonProperty("userAgent")
    private String userAgent;

    /**
     * Default constructor.
     */
    public AuditEntry() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor with required parameters.
     *
     * @param userId    the user ID
     * @param operation the operation
     * @param resource  the resource
     * @param success   whether the operation was successful
     */
    public AuditEntry(String userId, String operation, String resource, boolean success) {
        this();
        this.userId = userId;
        this.operation = operation;
        this.resource = resource;
        this.success = success;
    }

    /**
     * Constructor with common parameters.
     *
     * @param userId    the user ID
     * @param operation the operation
     * @param resource  the resource
     * @param success   whether the operation was successful
     * @param details   additional details
     * @param ipAddress the IP address
     */
    public AuditEntry(String userId, String operation, String resource, boolean success, 
                     String details, String ipAddress) {
        this(userId, operation, resource, success);
        this.details = details;
        this.ipAddress = ipAddress;
    }

    /**
     * Constructor with all parameters.
     *
     * @param timestamp  the timestamp
     * @param userId     the user ID
     * @param operation  the operation
     * @param resource   the resource
     * @param success    whether the operation was successful
     * @param details    additional details
     * @param ipAddress  the IP address
     * @param sessionId  the session ID
     * @param userAgent  the user agent
     */
    public AuditEntry(LocalDateTime timestamp, String userId, String operation, String resource, 
                     boolean success, String details, String ipAddress, String sessionId, String userAgent) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.operation = operation;
        this.resource = resource;
        this.success = success;
        this.details = details;
        this.ipAddress = ipAddress;
        this.sessionId = sessionId;
        this.userAgent = userAgent;
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
     * Gets the operation.
     *
     * @return the operation
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Sets the operation.
     *
     * @param operation the operation to set
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * Gets the resource.
     *
     * @return the resource
     */
    public String getResource() {
        return resource;
    }

    /**
     * Sets the resource.
     *
     * @param resource the resource to set
     */
    public void setResource(String resource) {
        this.resource = resource;
    }

    /**
     * Gets whether the operation was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets whether the operation was successful.
     *
     * @param success true if successful, false otherwise
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Gets the details.
     *
     * @return the details
     */
    public String getDetails() {
        return details;
    }

    /**
     * Sets the details.
     *
     * @param details the details to set
     */
    public void setDetails(String details) {
        this.details = details;
    }

    /**
     * Gets the IP address.
     *
     * @return the IP address
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Sets the IP address.
     *
     * @param ipAddress the IP address to set
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Gets the session ID.
     *
     * @return the session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the session ID.
     *
     * @param sessionId the session ID to set
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Gets the user agent.
     *
     * @return the user agent
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Sets the user agent.
     *
     * @param userAgent the user agent to set
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public String toString() {
        return "AuditEntry{" +
                "timestamp=" + timestamp +
                ", userId='" + userId + '\'' +
                ", operation='" + operation + '\'' +
                ", resource='" + resource + '\'' +
                ", success=" + success +
                ", details='" + details + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", userAgent='" + userAgent + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEntry that = (AuditEntry) o;
        return success == that.success &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(operation, that.operation) &&
                Objects.equals(resource, that.resource) &&
                Objects.equals(details, that.details) &&
                Objects.equals(ipAddress, that.ipAddress) &&
                Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(userAgent, that.userAgent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, userId, operation, resource, success, details, 
                           ipAddress, sessionId, userAgent);
    }
}