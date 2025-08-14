package com.fabric.watcher.archive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration properties for the Archive Search API.
 * 
 * <p>This class contains all configurable properties for the archive search functionality,
 * including security settings, performance limits, and supported file types.</p>
 * 
 * @since 1.0
 */
@ConfigurationProperties(prefix = "archive.search")
@Validated
public class ArchiveSearchProperties {

    /**
     * Whether the archive search API is enabled.
     * Defaults to false for security reasons.
     */
    private boolean enabled = false;

    /**
     * List of allowed base paths for file search operations.
     * Only files within these paths can be accessed.
     */
    @NotNull
    private List<String> allowedPaths = new ArrayList<>();

    /**
     * List of paths that should be excluded from search operations.
     * These paths will be blocked even if they are within allowed paths.
     */
    @NotNull
    private List<String> excludedPaths = new ArrayList<>();

    /**
     * Maximum file size in bytes that can be processed.
     * Defaults to 100MB.
     */
    @Min(1)
    private long maxFileSize = 100L * 1024 * 1024; // 100MB

    /**
     * Maximum number of search results to return.
     * Defaults to 100.
     */
    @Min(1)
    private int maxSearchResults = 100;

    /**
     * Timeout for search operations in seconds.
     * Defaults to 30 seconds.
     */
    @Min(1)
    private int searchTimeoutSeconds = 30;

    /**
     * List of supported archive file types.
     * Only these archive types will be processed.
     */
    @NotEmpty
    private List<String> supportedArchiveTypes = Arrays.asList("zip", "tar", "tar.gz", "jar");

    /**
     * Maximum number of concurrent search operations allowed.
     * Defaults to 5.
     */
    @Min(1)
    private int maxConcurrentOperations = 5;

    /**
     * Whether to enable detailed audit logging for security purposes.
     * Defaults to true.
     */
    private boolean auditLoggingEnabled = true;

    /**
     * Maximum depth for directory traversal during search operations.
     * Defaults to 10 levels.
     */
    @Min(1)
    private int maxDirectoryDepth = 10;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedPaths() {
        return allowedPaths;
    }

    public void setAllowedPaths(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxSearchResults() {
        return maxSearchResults;
    }

    public void setMaxSearchResults(int maxSearchResults) {
        this.maxSearchResults = maxSearchResults;
    }

    public int getSearchTimeoutSeconds() {
        return searchTimeoutSeconds;
    }

    public void setSearchTimeoutSeconds(int searchTimeoutSeconds) {
        this.searchTimeoutSeconds = searchTimeoutSeconds;
    }

    public List<String> getSupportedArchiveTypes() {
        return supportedArchiveTypes;
    }

    public void setSupportedArchiveTypes(List<String> supportedArchiveTypes) {
        this.supportedArchiveTypes = supportedArchiveTypes;
    }

    public int getMaxConcurrentOperations() {
        return maxConcurrentOperations;
    }

    public void setMaxConcurrentOperations(int maxConcurrentOperations) {
        this.maxConcurrentOperations = maxConcurrentOperations;
    }

    public boolean isAuditLoggingEnabled() {
        return auditLoggingEnabled;
    }

    public void setAuditLoggingEnabled(boolean auditLoggingEnabled) {
        this.auditLoggingEnabled = auditLoggingEnabled;
    }

    public int getMaxDirectoryDepth() {
        return maxDirectoryDepth;
    }

    public void setMaxDirectoryDepth(int maxDirectoryDepth) {
        this.maxDirectoryDepth = maxDirectoryDepth;
    }

    @Override
    public String toString() {
        return "ArchiveSearchProperties{" +
                "enabled=" + enabled +
                ", allowedPaths=" + allowedPaths +
                ", excludedPaths=" + excludedPaths +
                ", maxFileSize=" + maxFileSize +
                ", maxSearchResults=" + maxSearchResults +
                ", searchTimeoutSeconds=" + searchTimeoutSeconds +
                ", supportedArchiveTypes=" + supportedArchiveTypes +
                ", maxConcurrentOperations=" + maxConcurrentOperations +
                ", auditLoggingEnabled=" + auditLoggingEnabled +
                ", maxDirectoryDepth=" + maxDirectoryDepth +
                '}';
    }
}