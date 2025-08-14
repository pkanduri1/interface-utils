package com.fabric.watcher.archive.security;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Security validator for archive search operations.
 * Provides path traversal prevention and access control validation.
 */
@Component
public class SecurityValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityValidator.class);
    
    // Pattern to detect path traversal attempts
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(".*(\\.\\./|\\.\\.\\\\).*");
    
    // Pattern to detect null bytes (used in path traversal attacks)
    private static final Pattern NULL_BYTE_PATTERN = Pattern.compile(".*\\x00.*");
    
    private final ArchiveSearchProperties properties;
    
    @Autowired
    public SecurityValidator(ArchiveSearchProperties properties) {
        this.properties = properties;
    }
    
    /**
     * Validates if the given path is allowed for access.
     * 
     * @param path the path to validate
     * @return true if the path is allowed, false otherwise
     */
    public boolean isPathAllowed(String path) {
        if (path == null || path.trim().isEmpty()) {
            logger.warn("Path validation failed: null or empty path");
            return false;
        }
        
        try {
            // First sanitize the path
            String sanitizedPath = sanitizePath(path);
            if (sanitizedPath == null) {
                return false;
            }
            
            // Convert to canonical path to resolve any symbolic links or relative references
            Path canonicalPath;
            try {
                // Use toRealPath to resolve symbolic links
                canonicalPath = Paths.get(sanitizedPath).toRealPath();
            } catch (IOException e) {
                // If the path doesn't exist, use toAbsolutePath().normalize() as fallback
                canonicalPath = Paths.get(sanitizedPath).toAbsolutePath().normalize();
            }
            String canonicalPathString = canonicalPath.toString();
            
            // Check if path is in allowed directories
            boolean isAllowed = isPathInAllowedDirectories(canonicalPathString);
            if (!isAllowed) {
                logger.warn("Path access denied - not in allowed directories: {}", sanitizedPath);
                return false;
            }
            
            // Check if path is not in excluded directories
            boolean isExcluded = isPathInExcludedDirectories(canonicalPathString);
            if (isExcluded) {
                logger.warn("Path access denied - in excluded directories: {}", sanitizedPath);
                return false;
            }
            
            logger.debug("Path validation successful: {}", sanitizedPath);
            return true;
            
        } catch (Exception e) {
            logger.error("Path validation failed for path: {}", path, e);
            return false;
        }
    }
    
    /**
     * Validates if the given file is accessible.
     * 
     * @param file the file path to validate
     * @return true if the file is accessible, false otherwise
     */
    public boolean isFileAccessible(Path file) {
        if (file == null) {
            logger.warn("File accessibility check failed: null file path");
            return false;
        }
        
        try {
            // Check if path is allowed
            if (!isPathAllowed(file.toString())) {
                return false;
            }
            
            // Check if file exists and is readable
            if (!Files.exists(file)) {
                logger.warn("File accessibility check failed: file does not exist: {}", file);
                return false;
            }
            
            if (!Files.isReadable(file)) {
                logger.warn("File accessibility check failed: file is not readable: {}", file);
                return false;
            }
            
            // Check file size limits
            long fileSize = Files.size(file);
            if (fileSize > properties.getMaxFileSize()) {
                logger.warn("File accessibility check failed: file size {} exceeds limit {}: {}", 
                    fileSize, properties.getMaxFileSize(), file);
                return false;
            }
            
            logger.debug("File accessibility check successful: {}", file);
            return true;
            
        } catch (IOException e) {
            logger.error("File accessibility check failed for file: {}", file, e);
            return false;
        }
    }
    
    /**
     * Sanitizes the given path by removing dangerous characters and patterns.
     * 
     * @param path the path to sanitize
     * @return the sanitized path, or null if the path is malicious
     */
    public String sanitizePath(String path) {
        if (path == null) {
            return null;
        }
        
        // Check for path traversal attempts
        if (isPathTraversalAttempt(path)) {
            logger.warn("Path sanitization failed: path traversal attempt detected: {}", path);
            return null;
        }
        
        // Check for null bytes
        if (NULL_BYTE_PATTERN.matcher(path).matches()) {
            logger.warn("Path sanitization failed: null byte detected: {}", path);
            return null;
        }
        
        // Normalize path separators to system default
        String sanitized = path.replace('\\', '/');
        
        // Remove any double slashes
        sanitized = sanitized.replaceAll("/+", "/");
        
        // Trim whitespace
        sanitized = sanitized.trim();
        
        logger.debug("Path sanitized from '{}' to '{}'", path, sanitized);
        return sanitized;
    }
    
    /**
     * Checks if the given path contains path traversal patterns.
     * 
     * @param path the path to check
     * @return true if path traversal is detected, false otherwise
     */
    private boolean isPathTraversalAttempt(String path) {
        if (path == null) {
            return false;
        }
        
        // Check for common path traversal patterns
        return PATH_TRAVERSAL_PATTERN.matcher(path).matches() ||
               path.contains("..") ||
               path.contains("%2e%2e") ||  // URL encoded ..
               path.contains("%252e%252e") || // Double URL encoded ..
               path.contains("0x2e0x2e") ||   // Hex encoded ..
               path.contains("\\x2e\\x2e");   // Escaped hex encoded ..
    }
    
    /**
     * Checks if the path is within allowed directories.
     * 
     * @param canonicalPath the canonical path to check
     * @return true if the path is in allowed directories, false otherwise
     */
    private boolean isPathInAllowedDirectories(String canonicalPath) {
        List<String> allowedPaths = properties.getAllowedPaths();
        
        if (allowedPaths == null || allowedPaths.isEmpty()) {
            logger.warn("No allowed paths configured - denying access");
            return false;
        }
        
        for (String allowedPath : allowedPaths) {
            try {
                Path allowedCanonicalPath;
                try {
                    // Try to get real path first
                    allowedCanonicalPath = Paths.get(allowedPath).toRealPath();
                } catch (IOException e) {
                    // Fallback to absolute path if real path fails
                    allowedCanonicalPath = Paths.get(allowedPath).toAbsolutePath().normalize();
                }
                
                if (canonicalPath.startsWith(allowedCanonicalPath.toString())) {
                    logger.debug("Path {} is within allowed directory {}", canonicalPath, allowedPath);
                    return true;
                }
            } catch (Exception e) {
                logger.error("Error checking allowed path {}: {}", allowedPath, e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * Checks if the path is within excluded directories.
     * 
     * @param canonicalPath the canonical path to check
     * @return true if the path is in excluded directories, false otherwise
     */
    private boolean isPathInExcludedDirectories(String canonicalPath) {
        List<String> excludedPaths = properties.getExcludedPaths();
        
        if (excludedPaths == null || excludedPaths.isEmpty()) {
            return false;
        }
        
        for (String excludedPath : excludedPaths) {
            try {
                Path excludedCanonicalPath;
                try {
                    // Try to get real path first
                    excludedCanonicalPath = Paths.get(excludedPath).toRealPath();
                } catch (IOException e) {
                    // Fallback to absolute path if real path fails
                    excludedCanonicalPath = Paths.get(excludedPath).toAbsolutePath().normalize();
                }
                
                if (canonicalPath.startsWith(excludedCanonicalPath.toString())) {
                    logger.debug("Path {} is within excluded directory {}", canonicalPath, excludedPath);
                    return true;
                }
            } catch (Exception e) {
                logger.error("Error checking excluded path {}: {}", excludedPath, e.getMessage());
            }
        }
        
        return false;
    }
}