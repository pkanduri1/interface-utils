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

    /**
     * LDAP configuration for user authentication.
     */
    @NotNull
    private LdapConfig ldap = new LdapConfig();

    /**
     * Upload configuration for file upload operations.
     */
    @NotNull
    private UploadConfig upload = new UploadConfig();

    /**
     * Audit configuration for logging user activities.
     */
    @NotNull
    private AuditConfig audit = new AuditConfig();

    /**
     * Security configuration for authentication and authorization.
     */
    @NotNull
    private SecurityConfig security = new SecurityConfig();

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

    public LdapConfig getLdap() {
        return ldap;
    }

    public void setLdap(LdapConfig ldap) {
        this.ldap = ldap;
    }

    public UploadConfig getUpload() {
        return upload;
    }

    public void setUpload(UploadConfig upload) {
        this.upload = upload;
    }

    public AuditConfig getAudit() {
        return audit;
    }

    public void setAudit(AuditConfig audit) {
        this.audit = audit;
    }

    public SecurityConfig getSecurity() {
        return security;
    }

    public void setSecurity(SecurityConfig security) {
        this.security = security;
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
                ", ldap=" + ldap +
                ", upload=" + upload +
                ", audit=" + audit +
                ", security=" + security +
                '}';
    }

    /**
     * LDAP configuration properties.
     */
    public static class LdapConfig {
        
        /**
         * LDAP server URL.
         */
        private String url = "ldap://localhost:389";

        /**
         * Base DN for LDAP searches.
         */
        private String baseDn = "dc=company,dc=com";

        /**
         * User search base DN.
         */
        private String userSearchBase = "ou=users";

        /**
         * User search filter pattern.
         */
        private String userSearchFilter = "(sAMAccountName={0})";

        /**
         * Connection timeout in milliseconds.
         */
        @Min(1000)
        private int connectionTimeout = 5000;

        /**
         * Read timeout in milliseconds.
         */
        @Min(1000)
        private int readTimeout = 10000;

        /**
         * Whether to use SSL/TLS for LDAP connections.
         */
        private boolean useSSL = false;

        /**
         * LDAP bind DN for authentication.
         */
        private String bindDn;

        /**
         * LDAP bind password for authentication.
         */
        private String bindPassword;

        // Getters and Setters

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBaseDn() {
            return baseDn;
        }

        public void setBaseDn(String baseDn) {
            this.baseDn = baseDn;
        }

        public String getUserSearchBase() {
            return userSearchBase;
        }

        public void setUserSearchBase(String userSearchBase) {
            this.userSearchBase = userSearchBase;
        }

        public String getUserSearchFilter() {
            return userSearchFilter;
        }

        public void setUserSearchFilter(String userSearchFilter) {
            this.userSearchFilter = userSearchFilter;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }

        public boolean isUseSSL() {
            return useSSL;
        }

        public void setUseSSL(boolean useSSL) {
            this.useSSL = useSSL;
        }

        public String getBindDn() {
            return bindDn;
        }

        public void setBindDn(String bindDn) {
            this.bindDn = bindDn;
        }

        public String getBindPassword() {
            return bindPassword;
        }

        public void setBindPassword(String bindPassword) {
            this.bindPassword = bindPassword;
        }

        @Override
        public String toString() {
            return "LdapConfig{" +
                    "url='" + url + '\'' +
                    ", baseDn='" + baseDn + '\'' +
                    ", userSearchBase='" + userSearchBase + '\'' +
                    ", userSearchFilter='" + userSearchFilter + '\'' +
                    ", connectionTimeout=" + connectionTimeout +
                    ", readTimeout=" + readTimeout +
                    ", useSSL=" + useSSL +
                    ", bindDn='" + bindDn + '\'' +
                    ", bindPassword='[PROTECTED]'" +
                    '}';
        }
    }

    /**
     * Upload configuration properties.
     */
    public static class UploadConfig {
        
        /**
         * Base directory for file uploads.
         */
        private String uploadDirectory = "/opt/uploads";

        /**
         * List of allowed file extensions.
         */
        @NotEmpty
        private List<String> allowedExtensions = Arrays.asList(".txt", ".sql", ".xml", ".json", 
                                                              ".properties", ".yml", ".yaml", ".log");

        /**
         * Maximum upload file size in bytes.
         */
        @Min(1)
        private long maxUploadSize = 100L * 1024 * 1024; // 100MB

        /**
         * Temporary directory for upload processing.
         */
        private String tempDirectory = "/tmp/file-uploads";

        /**
         * Whether to create directories if they don't exist.
         */
        private boolean createDirectories = true;

        /**
         * Whether to preserve original file timestamps.
         */
        private boolean preserveTimestamps = true;

        /**
         * Maximum number of concurrent uploads.
         */
        @Min(1)
        private int maxConcurrentUploads = 3;

        // Getters and Setters

        public String getUploadDirectory() {
            return uploadDirectory;
        }

        public void setUploadDirectory(String uploadDirectory) {
            this.uploadDirectory = uploadDirectory;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public long getMaxUploadSize() {
            return maxUploadSize;
        }

        public void setMaxUploadSize(long maxUploadSize) {
            this.maxUploadSize = maxUploadSize;
        }

        public String getTempDirectory() {
            return tempDirectory;
        }

        public void setTempDirectory(String tempDirectory) {
            this.tempDirectory = tempDirectory;
        }

        public boolean isCreateDirectories() {
            return createDirectories;
        }

        public void setCreateDirectories(boolean createDirectories) {
            this.createDirectories = createDirectories;
        }

        public boolean isPreserveTimestamps() {
            return preserveTimestamps;
        }

        public void setPreserveTimestamps(boolean preserveTimestamps) {
            this.preserveTimestamps = preserveTimestamps;
        }

        public int getMaxConcurrentUploads() {
            return maxConcurrentUploads;
        }

        public void setMaxConcurrentUploads(int maxConcurrentUploads) {
            this.maxConcurrentUploads = maxConcurrentUploads;
        }

        @Override
        public String toString() {
            return "UploadConfig{" +
                    "uploadDirectory='" + uploadDirectory + '\'' +
                    ", allowedExtensions=" + allowedExtensions +
                    ", maxUploadSize=" + maxUploadSize +
                    ", tempDirectory='" + tempDirectory + '\'' +
                    ", createDirectories=" + createDirectories +
                    ", preserveTimestamps=" + preserveTimestamps +
                    ", maxConcurrentUploads=" + maxConcurrentUploads +
                    '}';
        }
    }

    /**
     * Audit configuration properties.
     */
    public static class AuditConfig {
        
        /**
         * Audit log file path.
         */
        private String logFile = "/var/log/archive-search/audit.log";

        /**
         * Maximum audit log file size.
         */
        private String maxFileSize = "10MB";

        /**
         * Maximum number of audit log files to keep.
         */
        @Min(1)
        private int maxHistory = 30;

        /**
         * Whether to enable audit logging.
         */
        private boolean enabled = true;

        /**
         * Log format pattern.
         */
        private String logPattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

        /**
         * Whether to log to console in addition to file.
         */
        private boolean logToConsole = false;

        /**
         * Whether to compress rotated log files.
         */
        private boolean compressRotatedFiles = true;

        // Getters and Setters

        public String getLogFile() {
            return logFile;
        }

        public void setLogFile(String logFile) {
            this.logFile = logFile;
        }

        public String getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(String maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public int getMaxHistory() {
            return maxHistory;
        }

        public void setMaxHistory(int maxHistory) {
            this.maxHistory = maxHistory;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLogPattern() {
            return logPattern;
        }

        public void setLogPattern(String logPattern) {
            this.logPattern = logPattern;
        }

        public boolean isLogToConsole() {
            return logToConsole;
        }

        public void setLogToConsole(boolean logToConsole) {
            this.logToConsole = logToConsole;
        }

        public boolean isCompressRotatedFiles() {
            return compressRotatedFiles;
        }

        public void setCompressRotatedFiles(boolean compressRotatedFiles) {
            this.compressRotatedFiles = compressRotatedFiles;
        }

        @Override
        public String toString() {
            return "AuditConfig{" +
                    "logFile='" + logFile + '\'' +
                    ", maxFileSize='" + maxFileSize + '\'' +
                    ", maxHistory=" + maxHistory +
                    ", enabled=" + enabled +
                    ", logPattern='" + logPattern + '\'' +
                    ", logToConsole=" + logToConsole +
                    ", compressRotatedFiles=" + compressRotatedFiles +
                    '}';
        }
    }

    /**
     * Security configuration properties.
     */
    public static class SecurityConfig {
        
        /**
         * Session timeout in minutes.
         */
        @Min(1)
        private int sessionTimeoutMinutes = 30;

        /**
         * Maximum number of failed login attempts before lockout.
         */
        @Min(1)
        private int maxLoginAttempts = 3;

        /**
         * Lockout duration in minutes after max failed attempts.
         */
        @Min(1)
        private int lockoutDurationMinutes = 15;

        /**
         * Whether to require authentication for all operations.
         */
        private boolean requireAuthentication = true;

        /**
         * JWT secret key (will be generated if not provided).
         */
        private String jwtSecret;

        /**
         * Whether to enable CSRF protection.
         */
        private boolean enableCsrfProtection = false;

        /**
         * List of IP addresses allowed to bypass authentication (for testing).
         */
        private List<String> allowedIpAddresses = new ArrayList<>();

        // Getters and Setters

        public int getSessionTimeoutMinutes() {
            return sessionTimeoutMinutes;
        }

        public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
            this.sessionTimeoutMinutes = sessionTimeoutMinutes;
        }

        public int getMaxLoginAttempts() {
            return maxLoginAttempts;
        }

        public void setMaxLoginAttempts(int maxLoginAttempts) {
            this.maxLoginAttempts = maxLoginAttempts;
        }

        public int getLockoutDurationMinutes() {
            return lockoutDurationMinutes;
        }

        public void setLockoutDurationMinutes(int lockoutDurationMinutes) {
            this.lockoutDurationMinutes = lockoutDurationMinutes;
        }

        public boolean isRequireAuthentication() {
            return requireAuthentication;
        }

        public void setRequireAuthentication(boolean requireAuthentication) {
            this.requireAuthentication = requireAuthentication;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public boolean isEnableCsrfProtection() {
            return enableCsrfProtection;
        }

        public void setEnableCsrfProtection(boolean enableCsrfProtection) {
            this.enableCsrfProtection = enableCsrfProtection;
        }

        public List<String> getAllowedIpAddresses() {
            return allowedIpAddresses;
        }

        public void setAllowedIpAddresses(List<String> allowedIpAddresses) {
            this.allowedIpAddresses = allowedIpAddresses;
        }

        @Override
        public String toString() {
            return "SecurityConfig{" +
                    "sessionTimeoutMinutes=" + sessionTimeoutMinutes +
                    ", maxLoginAttempts=" + maxLoginAttempts +
                    ", lockoutDurationMinutes=" + lockoutDurationMinutes +
                    ", requireAuthentication=" + requireAuthentication +
                    ", jwtSecret='[PROTECTED]'" +
                    ", enableCsrfProtection=" + enableCsrfProtection +
                    ", allowedIpAddresses=" + allowedIpAddresses +
                    '}';
        }
    }
}