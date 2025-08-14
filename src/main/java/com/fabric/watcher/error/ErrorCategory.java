package com.fabric.watcher.error;

/**
 * Enumeration of error categories for comprehensive error handling.
 */
public enum ErrorCategory {
    
    /**
     * File system related errors.
     */
    FILE_SYSTEM("File System", true),
    
    /**
     * Database connectivity and execution errors.
     */
    DATABASE("Database", true),
    
    /**
     * Application logic and configuration errors.
     */
    APPLICATION("Application", false),
    
    /**
     * External system integration errors.
     */
    EXTERNAL_SYSTEM("External System", true),
    
    /**
     * Security and permission related errors.
     */
    SECURITY("Security", false),
    
    /**
     * Resource exhaustion errors (memory, disk space, etc.).
     */
    RESOURCE("Resource", true),
    
    /**
     * Network connectivity errors.
     */
    NETWORK("Network", true),
    
    /**
     * Unknown or unclassified errors.
     */
    UNKNOWN("Unknown", false);
    
    private final String displayName;
    private final boolean retryable;
    
    ErrorCategory(String displayName, boolean retryable) {
        this.displayName = displayName;
        this.retryable = retryable;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    /**
     * Categorize an exception based on its type and characteristics.
     * 
     * @param exception the exception to categorize
     * @return the appropriate error category
     */
    public static ErrorCategory categorize(Throwable exception) {
        if (exception == null) {
            return UNKNOWN;
        }
        
        String className = exception.getClass().getSimpleName().toLowerCase();
        String message = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        // Security errors (most specific first)
        if (className.contains("security") || 
            className.contains("authentication") || 
            className.contains("authorization") ||
            className.contains("accesscontrol")) {
            return SECURITY;
        }
        
        // Network errors (specific socket/timeout errors)
        if (className.contains("socket") || 
            className.contains("timeout") ||
            message.contains("connection timeout") ||
            message.contains("network") ||
            message.contains("connection refused")) {
            return NETWORK;
        }
        
        // Database errors (specific database-related errors)
        String fullClassName = exception.getClass().getName().toLowerCase();
        if (className.contains("dataaccess") || 
            className.contains("sql") || 
            className.contains("jdbc") ||
            fullClassName.contains("dataaccess") ||
            exception.getClass().getSimpleName().contains("DataAccess") ||
            message.contains("database") ||
            message.contains("sql") ||
            exception instanceof org.springframework.dao.DataAccessException) {
            return DATABASE;
        }
        
        // Application errors (check before file system to avoid conflicts)
        if (className.contains("illegal") || 
            className.contains("invalid") || 
            className.contains("configuration") ||
            message.contains("configuration") ||
            message.contains("invalid")) {
            return APPLICATION;
        }
        
        // File system errors
        if (className.contains("ioexception") || 
            className.contains("filenotfound") ||
            className.contains("file") || 
            className.contains("path") ||
            className.contains("directory") ||
            message.contains("file") ||
            message.contains("directory") ||
            message.contains("permission") ||
            message.contains("access denied")) {
            return FILE_SYSTEM;
        }
        
        // Resource errors
        if (className.contains("outofmemory") || 
            className.contains("resource") ||
            message.contains("out of memory") ||
            message.contains("disk space") ||
            message.contains("resource")) {
            return RESOURCE;
        }
        
        return UNKNOWN;
    }
}