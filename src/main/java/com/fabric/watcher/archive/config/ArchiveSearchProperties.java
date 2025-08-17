package com.fabric.watcher.archive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for Archive Search API
 */
@Component
@ConfigurationProperties(prefix = "archive.search")
public class ArchiveSearchProperties {
    
    private boolean enabled = false;
    private List<String> allowedPaths;
    private List<String> excludedPaths;
    private long maxFileSize = 104857600L; // 100MB
    private int maxSearchResults = 100;
    private int searchTimeoutSeconds = 30;
    private List<String> supportedArchiveTypes;
    private int maxConcurrentOperations = 5;
    private boolean auditLoggingEnabled = true;
    private int maxDirectoryDepth = 10;
    private boolean pathTraversalProtection = true;
    private boolean fileTypeValidation = true;
    
    private LdapConfig ldap = new LdapConfig();
    private UploadConfig upload = new UploadConfig();
    private AuditConfig audit = new AuditConfig();
    private SecurityConfig security = new SecurityConfig();
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public List<String> getAllowedPaths() { return allowedPaths; }
    public void setAllowedPaths(List<String> allowedPaths) { this.allowedPaths = allowedPaths; }
    
    public List<String> getExcludedPaths() { return excludedPaths; }
    public void setExcludedPaths(List<String> excludedPaths) { this.excludedPaths = excludedPaths; }
    
    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }
    
    public int getMaxSearchResults() { return maxSearchResults; }
    public void setMaxSearchResults(int maxSearchResults) { this.maxSearchResults = maxSearchResults; }
    
    public int getSearchTimeoutSeconds() { return searchTimeoutSeconds; }
    public void setSearchTimeoutSeconds(int searchTimeoutSeconds) { this.searchTimeoutSeconds = searchTimeoutSeconds; }
    
    public List<String> getSupportedArchiveTypes() { return supportedArchiveTypes; }
    public void setSupportedArchiveTypes(List<String> supportedArchiveTypes) { this.supportedArchiveTypes = supportedArchiveTypes; }
    
    public int getMaxConcurrentOperations() { return maxConcurrentOperations; }
    public void setMaxConcurrentOperations(int maxConcurrentOperations) { this.maxConcurrentOperations = maxConcurrentOperations; }
    
    public boolean isAuditLoggingEnabled() { return auditLoggingEnabled; }
    public void setAuditLoggingEnabled(boolean auditLoggingEnabled) { this.auditLoggingEnabled = auditLoggingEnabled; }
    
    public int getMaxDirectoryDepth() { return maxDirectoryDepth; }
    public void setMaxDirectoryDepth(int maxDirectoryDepth) { this.maxDirectoryDepth = maxDirectoryDepth; }
    
    public boolean isPathTraversalProtection() { return pathTraversalProtection; }
    public void setPathTraversalProtection(boolean pathTraversalProtection) { this.pathTraversalProtection = pathTraversalProtection; }
    
    public boolean isFileTypeValidation() { return fileTypeValidation; }
    public void setFileTypeValidation(boolean fileTypeValidation) { this.fileTypeValidation = fileTypeValidation; }
    
    public LdapConfig getLdap() { return ldap; }
    public void setLdap(LdapConfig ldap) { this.ldap = ldap; }
    
    public UploadConfig getUpload() { return upload; }
    public void setUpload(UploadConfig upload) { this.upload = upload; }
    
    public AuditConfig getAudit() { return audit; }
    public void setAudit(AuditConfig audit) { this.audit = audit; }
    
    public SecurityConfig getSecurity() { return security; }
    public void setSecurity(SecurityConfig security) { this.security = security; }
    
    public static class LdapConfig {
        private String url = "ldap://localhost:389";
        private String baseDn = "dc=company,dc=com";
        private String userSearchBase = "ou=users";
        private String userSearchFilter = "(sAMAccountName={0})";
        private int connectionTimeout = 5000;
        private int readTimeout = 10000;
        private boolean useSsl = false;
        private String bindDn;
        private String bindPassword;
        
        // Getters and setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getBaseDn() { return baseDn; }
        public void setBaseDn(String baseDn) { this.baseDn = baseDn; }
        
        public String getUserSearchBase() { return userSearchBase; }
        public void setUserSearchBase(String userSearchBase) { this.userSearchBase = userSearchBase; }
        
        public String getUserSearchFilter() { return userSearchFilter; }
        public void setUserSearchFilter(String userSearchFilter) { this.userSearchFilter = userSearchFilter; }
        
        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        
        public int getReadTimeout() { return readTimeout; }
        public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
        
        public boolean isUseSsl() { return useSsl; }
        public void setUseSsl(boolean useSsl) { this.useSsl = useSsl; }
        
        public String getBindDn() { return bindDn; }
        public void setBindDn(String bindDn) { this.bindDn = bindDn; }
        
        public String getBindPassword() { return bindPassword; }
        public void setBindPassword(String bindPassword) { this.bindPassword = bindPassword; }
    }
    
    public static class UploadConfig {
        private String uploadDirectory = "/opt/uploads";
        private List<String> allowedExtensions;
        private long maxUploadSize = 104857600L; // 100MB
        private String tempDirectory = "/tmp/file-uploads";
        private boolean createDirectories = true;
        private boolean preserveTimestamps = true;
        private int maxConcurrentUploads = 3;
        
        // Getters and setters
        public String getUploadDirectory() { return uploadDirectory; }
        public void setUploadDirectory(String uploadDirectory) { this.uploadDirectory = uploadDirectory; }
        
        public List<String> getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(List<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
        
        public long getMaxUploadSize() { return maxUploadSize; }
        public void setMaxUploadSize(long maxUploadSize) { this.maxUploadSize = maxUploadSize; }
        
        public String getTempDirectory() { return tempDirectory; }
        public void setTempDirectory(String tempDirectory) { this.tempDirectory = tempDirectory; }
        
        public boolean isCreateDirectories() { return createDirectories; }
        public void setCreateDirectories(boolean createDirectories) { this.createDirectories = createDirectories; }
        
        public boolean isPreserveTimestamps() { return preserveTimestamps; }
        public void setPreserveTimestamps(boolean preserveTimestamps) { this.preserveTimestamps = preserveTimestamps; }
        
        public int getMaxConcurrentUploads() { return maxConcurrentUploads; }
        public void setMaxConcurrentUploads(int maxConcurrentUploads) { this.maxConcurrentUploads = maxConcurrentUploads; }
    }
    
    public static class AuditConfig {
        private String logFile = "./logs/archive-search-audit.log";
        private String maxFileSize = "10MB";
        private int maxHistory = 30;
        private boolean enabled = true;
        private String logPattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";
        private boolean logToConsole = false;
        private boolean compressRotatedFiles = true;
        
        // Getters and setters
        public String getLogFile() { return logFile; }
        public void setLogFile(String logFile) { this.logFile = logFile; }
        
        public String getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(String maxFileSize) { this.maxFileSize = maxFileSize; }
        
        public int getMaxHistory() { return maxHistory; }
        public void setMaxHistory(int maxHistory) { this.maxHistory = maxHistory; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getLogPattern() { return logPattern; }
        public void setLogPattern(String logPattern) { this.logPattern = logPattern; }
        
        public boolean isLogToConsole() { return logToConsole; }
        public void setLogToConsole(boolean logToConsole) { this.logToConsole = logToConsole; }
        
        public boolean isCompressRotatedFiles() { return compressRotatedFiles; }
        public void setCompressRotatedFiles(boolean compressRotatedFiles) { this.compressRotatedFiles = compressRotatedFiles; }
    }
    
    public static class SecurityConfig {
        private int maxLoginAttempts = 3;
        private int lockoutDurationMinutes = 15;
        private int sessionTimeoutMinutes = 30;
        
        // Getters and setters
        public int getMaxLoginAttempts() { return maxLoginAttempts; }
        public void setMaxLoginAttempts(int maxLoginAttempts) { this.maxLoginAttempts = maxLoginAttempts; }
        
        public int getLockoutDurationMinutes() { return lockoutDurationMinutes; }
        public void setLockoutDurationMinutes(int lockoutDurationMinutes) { this.lockoutDurationMinutes = lockoutDurationMinutes; }
        
        public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
        public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }
    }
}