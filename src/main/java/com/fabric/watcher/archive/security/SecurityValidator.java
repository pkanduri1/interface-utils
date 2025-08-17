package com.fabric.watcher.archive.security;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Security validator for file operations and path validation
 */
@Component
public class SecurityValidator {
    
    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
        "(\\.\\./|\\.\\.\\\\|%2e%2e%2f|%2e%2e%5c|\\x2e\\x2e\\x2f|\\x2e\\x2e\\x5c)"
    );
    
    private static final List<String> DANGEROUS_EXTENSIONS = List.of(
        ".exe", ".bat", ".cmd", ".com", ".scr", ".pif", ".vbs", ".js", ".jar", ".class"
    );
    
    /**
     * Validates if a file path is safe and doesn't contain path traversal attempts
     */
    public boolean isPathSafe(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        // Check for path traversal patterns
        if (DANGEROUS_PATTERNS.matcher(filePath.toLowerCase()).find()) {
            return false;
        }
        
        // Check for null bytes
        if (filePath.contains("\0")) {
            return false;
        }
        
        // Normalize the path and check if it's within allowed bounds
        try {
            Path normalizedPath = Paths.get(filePath).normalize();
            String normalizedString = normalizedPath.toString();
            
            // Check if normalization revealed path traversal
            if (normalizedString.contains("..")) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validates if a file path is within allowed directories
     */
    public boolean isPathAllowed(String filePath, List<String> allowedPaths) {
        if (!isPathSafe(filePath)) {
            return false;
        }
        
        if (allowedPaths == null || allowedPaths.isEmpty()) {
            return false;
        }
        
        try {
            Path targetPath = Paths.get(filePath).normalize().toAbsolutePath();
            
            for (String allowedPath : allowedPaths) {
                Path allowed = Paths.get(allowedPath).normalize().toAbsolutePath();
                if (targetPath.startsWith(allowed)) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validates if a file extension is safe
     */
    public boolean isFileExtensionSafe(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        String lowerFileName = fileName.toLowerCase();
        
        for (String dangerousExt : DANGEROUS_EXTENSIONS) {
            if (lowerFileName.endsWith(dangerousExt)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validates file size
     */
    public boolean isFileSizeValid(long fileSize, long maxSize) {
        return fileSize > 0 && fileSize <= maxSize;
    }
    
    /**
     * Validates if a file name is safe (no dangerous characters)
     */
    public boolean isFileNameSafe(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        // Check for dangerous characters
        String dangerousChars = "<>:\"|?*\0";
        for (char c : dangerousChars.toCharArray()) {
            if (fileName.indexOf(c) >= 0) {
                return false;
            }
        }
        
        // Check for reserved names on Windows
        String[] reservedNames = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", 
                                 "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", 
                                 "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
        
        String upperFileName = fileName.toUpperCase();
        for (String reserved : reservedNames) {
            if (upperFileName.equals(reserved) || upperFileName.startsWith(reserved + ".")) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Sanitizes a file name by removing dangerous characters
     */
    public String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        
        // Replace dangerous characters with underscores
        String sanitized = fileName.replaceAll("[<>:\"|?*\\x00-\\x1f]", "_");
        
        // Trim whitespace and dots from the end
        sanitized = sanitized.trim().replaceAll("[\\.\\s]+$", "");
        
        // Ensure it's not empty after sanitization
        if (sanitized.isEmpty()) {
            sanitized = "file";
        }
        
        return sanitized;
    }
    
    /**
     * Validates directory depth to prevent deep directory traversal
     */
    public boolean isDirectoryDepthValid(String path, int maxDepth) {
        if (path == null) {
            return false;
        }
        
        try {
            Path normalizedPath = Paths.get(path).normalize();
            int depth = normalizedPath.getNameCount();
            return depth <= maxDepth;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Sanitizes a file path by removing dangerous elements
     */
    public String sanitizePath(String path) {
        if (path == null) {
            return null;
        }
        
        // Remove path traversal attempts
        String sanitized = path.replaceAll("(\\.\\./|\\.\\.\\\\)", "");
        
        // Remove null bytes
        sanitized = sanitized.replace("\0", "");
        
        // Normalize path separators
        sanitized = sanitized.replace("\\", "/");
        
        // Remove multiple consecutive slashes
        sanitized = sanitized.replaceAll("/+", "/");
        
        // Remove leading slash if present
        if (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1);
        }
        
        return sanitized;
    }
}