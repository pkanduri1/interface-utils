package com.fabric.watcher.service;

import com.fabric.watcher.config.WatchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service responsible for file operations including moving completed and failed files,
 * creating directory structures, and managing file naming conventions.
 */
@Service
public class FileManager {
    
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    @Autowired(required = false)
    private MetricsService metricsService;
    
    /**
     * Move a successfully processed file to the completed folder with timestamp.
     * 
     * @param file The file to move
     * @param config The watch configuration containing folder paths
     * @return The path of the moved file
     * @throws IOException if the file operation fails
     */
    public Path moveToCompleted(Path file, WatchConfig config) throws IOException {
        ensureDirectoriesExist(config);
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String originalName = file.getFileName().toString();
        String nameWithoutExtension = getNameWithoutExtension(originalName);
        String extension = getFileExtension(originalName);
        
        String newFileName = nameWithoutExtension + "_" + timestamp + extension;
        Path targetPath = config.getCompletedFolder().resolve(newFileName);
        
        logger.info("Moving completed file {} to {}", file, targetPath);
        
        try {
            return Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to move file {} to completed folder: {}", file, e.getMessage());
            if (metricsService != null) {
                metricsService.recordFileSystemError();
            }
            throw e;
        }
    }
    
    /**
     * Move a failed file to the error folder with error details in filename.
     * 
     * @param file The file to move
     * @param errorDetails Brief description of the error
     * @param config The watch configuration containing folder paths
     * @return The path of the moved file
     * @throws IOException if the file operation fails
     */
    public Path moveToError(Path file, String errorDetails, WatchConfig config) throws IOException {
        ensureDirectoriesExist(config);
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String originalName = file.getFileName().toString();
        String nameWithoutExtension = getNameWithoutExtension(originalName);
        String extension = getFileExtension(originalName);
        
        // Sanitize error details for filename
        String sanitizedError = sanitizeForFilename(errorDetails);
        String newFileName = nameWithoutExtension + "_ERROR_" + timestamp + "_" + sanitizedError + extension;
        
        Path targetPath = config.getErrorFolder().resolve(newFileName);
        
        logger.info("Moving error file {} to {} with error: {}", file, targetPath, errorDetails);
        
        try {
            return Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to move file {} to error folder: {}", file, e.getMessage());
            if (metricsService != null) {
                metricsService.recordFileSystemError();
            }
            throw e;
        }
    }
    
    /**
     * Copy a file to a specified destination.
     * 
     * @param source The source file
     * @param destination The destination path
     * @return The path of the copied file
     * @throws IOException if the copy operation fails
     */
    public Path copyFile(Path source, Path destination) throws IOException {
        logger.debug("Copying file {} to {}", source, destination);
        
        try {
            // Ensure parent directory exists
            Files.createDirectories(destination.getParent());
            return Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to copy file {} to {}: {}", source, destination, e.getMessage());
            if (metricsService != null) {
                metricsService.recordFileSystemError();
            }
            throw e;
        }
    }
    
    /**
     * Delete a file safely.
     * 
     * @param file The file to delete
     * @return true if the file was deleted, false if it didn't exist
     * @throws IOException if the delete operation fails
     */
    public boolean deleteFile(Path file) throws IOException {
        logger.debug("Deleting file {}", file);
        
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.error("Failed to delete file {}: {}", file, e.getMessage());
            if (metricsService != null) {
                metricsService.recordFileSystemError();
            }
            throw e;
        }
    }
    
    /**
     * Ensure all required directories exist for the given watch configuration.
     * Creates directories if they don't exist.
     * 
     * @param config The watch configuration
     * @throws IOException if directory creation fails
     */
    public void ensureDirectoriesExist(WatchConfig config) throws IOException {
        createDirectoryIfNotExists(config.getWatchFolder());
        createDirectoryIfNotExists(config.getCompletedFolder());
        createDirectoryIfNotExists(config.getErrorFolder());
    }
    
    /**
     * Validate that all required directories in the configuration are accessible.
     * 
     * @param config The watch configuration to validate
     * @throws IOException if any directory is not accessible
     */
    public void validateDirectoryAccess(WatchConfig config) throws IOException {
        validateDirectoryAccess(config.getWatchFolder(), "watch");
        validateDirectoryAccess(config.getCompletedFolder(), "completed");
        validateDirectoryAccess(config.getErrorFolder(), "error");
    }
    
    /**
     * Check if a file is currently being written to (has .tmp or .processing extension).
     * 
     * @param file The file to check
     * @return true if the file appears to be in use
     */
    public boolean isFileInUse(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return fileName.endsWith(".tmp") || fileName.endsWith(".processing");
    }
    
    /**
     * Get the size of a file in bytes.
     * 
     * @param file The file to check
     * @return The file size in bytes
     * @throws IOException if the file cannot be accessed
     */
    public long getFileSize(Path file) throws IOException {
        return Files.size(file);
    }
    
    /**
     * Check if a file exists and is readable.
     * 
     * @param file The file to check
     * @return true if the file exists and is readable
     */
    public boolean isFileReadable(Path file) {
        return Files.exists(file) && Files.isReadable(file);
    }
    
    // Private helper methods
    
    private void createDirectoryIfNotExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            logger.info("Creating directory: {}", directory);
            Files.createDirectories(directory);
        }
    }
    
    private void validateDirectoryAccess(Path directory, String type) throws IOException {
        if (!Files.exists(directory)) {
            throw new IOException(String.format("%s directory does not exist: %s", type, directory));
        }
        
        if (!Files.isDirectory(directory)) {
            throw new IOException(String.format("%s path is not a directory: %s", type, directory));
        }
        
        if (!Files.isReadable(directory)) {
            throw new IOException(String.format("%s directory is not readable: %s", type, directory));
        }
        
        if (!Files.isWritable(directory)) {
            throw new IOException(String.format("%s directory is not writable: %s", type, directory));
        }
    }
    
    private String getNameWithoutExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
    }
    
    private String sanitizeForFilename(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "UNKNOWN";
        }
        
        // Replace invalid filename characters and limit length
        String sanitized = input.replaceAll("[^a-zA-Z0-9._-]", "_")
                               .replaceAll("_{2,}", "_")
                               .trim();
        
        // Limit length to prevent overly long filenames
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        
        return sanitized.isEmpty() ? "UNKNOWN" : sanitized;
    }
}