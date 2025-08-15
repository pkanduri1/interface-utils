package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.security.SecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Main orchestrator service for archive search operations.
 * 
 * <p>This service coordinates file system scanning, archive processing, and content searching
 * while providing timeout handling and resource management. It serves as the primary entry
 * point for all archive search functionality.</p>
 * 
 * @since 1.0
 */
@Service
public class ArchiveSearchService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchService.class);

    private final ArchiveSearchProperties properties;
    private final SecurityValidator securityValidator;
    private final FileSystemService fileSystemService;
    private final ArchiveHandlerService archiveHandlerService;
    private final ContentSearchService contentSearchService;
    private final ExecutorService executorService;

    @Autowired
    public ArchiveSearchService(ArchiveSearchProperties properties,
                               SecurityValidator securityValidator,
                               FileSystemService fileSystemService,
                               ArchiveHandlerService archiveHandlerService,
                               ContentSearchService contentSearchService) {
        this.properties = properties;
        this.securityValidator = securityValidator;
        this.fileSystemService = fileSystemService;
        this.archiveHandlerService = archiveHandlerService;
        this.contentSearchService = contentSearchService;
        
        // Create thread pool for timeout handling
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "archive-search-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Searches for files matching the specified pattern in the given path.
     * This includes both regular files and files within archives.
     * 
     * @param path the directory path to search in
     * @param pattern the wildcard pattern to match (supports * and ?)
     * @return FileSearchResponse containing matching files and search metadata
     * @throws IOException if search operation fails
     * @throws IllegalArgumentException if path or pattern is invalid
     * @throws SecurityException if path access is denied
     * @throws TimeoutException if search operation times out
     */
    public FileSearchResponse searchFiles(String path, String pattern) 
            throws IOException, IllegalArgumentException, SecurityException, TimeoutException {
        
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Search path cannot be null or empty");
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Search pattern cannot be null or empty");
        }

        logger.info("Starting file search - path: '{}', pattern: '{}'", path, pattern);
        long startTime = System.currentTimeMillis();

        // Validate path security
        if (!securityValidator.isPathAllowed(path)) {
            throw new SecurityException("Access denied to path: " + path);
        }

        Path searchPath = Paths.get(path);
        if (!Files.exists(searchPath)) {
            logger.warn("Search path does not exist: {}", path);
            return new FileSearchResponse(new ArrayList<>(), 0, path, pattern, 
                    System.currentTimeMillis() - startTime);
        }

        // Execute search with timeout
        Future<FileSearchResponse> searchTask = executorService.submit(() -> {
            try {
                return performFileSearch(searchPath, pattern, path, startTime);
            } catch (Exception e) {
                logger.error("Error during file search execution", e);
                throw new RuntimeException(e);
            }
        });

        try {
            return searchTask.get(properties.getSearchTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            searchTask.cancel(true);
            logger.warn("File search timed out after {} seconds for path: {}", 
                    properties.getSearchTimeoutSeconds(), path);
            throw new TimeoutException("Search operation timed out after " + 
                    properties.getSearchTimeoutSeconds() + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Search operation was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new RuntimeException("Search operation failed", cause);
            }
        }
    }

    /**
     * Downloads a specific file as an InputStream.
     * Supports both regular files and files within archives.
     * 
     * @param filePath the path to the file to download
     * @return InputStream for the file content
     * @throws IOException if file cannot be accessed or read
     * @throws IllegalArgumentException if file path is invalid
     * @throws SecurityException if file access is denied
     */
    public InputStream downloadFile(String filePath) 
            throws IOException, IllegalArgumentException, SecurityException {
        
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        logger.info("Starting file download for path: '{}'", filePath);

        // Check if this is an archive entry (contains archive path separator)
        if (filePath.contains("::")) {
            return downloadArchiveEntry(filePath);
        } else {
            return downloadRegularFile(filePath);
        }
    }

    /**
     * Searches for content within a specific file.
     * Supports both regular files and files within archives.
     * 
     * @param request the content search request
     * @return ContentSearchResponse containing search results
     * @throws IOException if file cannot be accessed or read
     * @throws IllegalArgumentException if request is invalid
     * @throws SecurityException if file access is denied
     * @throws TimeoutException if search operation times out
     */
    public ContentSearchResponse searchContent(ContentSearchRequest request) 
            throws IOException, IllegalArgumentException, SecurityException, TimeoutException {
        
        if (request == null) {
            throw new IllegalArgumentException("Content search request cannot be null");
        }
        if (request.getFilePath() == null || request.getFilePath().trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        if (request.getSearchTerm() == null || request.getSearchTerm().trim().isEmpty()) {
            throw new IllegalArgumentException("Search term cannot be null or empty");
        }

        logger.info("Starting content search - file: '{}', term: '{}'", 
                request.getFilePath(), request.getSearchTerm());

        // Execute content search with timeout
        Future<ContentSearchResponse> searchTask = executorService.submit(() -> {
            try {
                // Check if this is an archive entry
                if (request.getFilePath().contains("::")) {
                    return searchContentInArchiveEntry(request);
                } else {
                    return searchContentInRegularFile(request);
                }
            } catch (Exception e) {
                logger.error("Error during content search execution", e);
                throw new RuntimeException(e);
            }
        });

        try {
            return searchTask.get(properties.getSearchTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            searchTask.cancel(true);
            logger.warn("Content search timed out after {} seconds for file: {}", 
                    properties.getSearchTimeoutSeconds(), request.getFilePath());
            throw new TimeoutException("Content search operation timed out after " + 
                    properties.getSearchTimeoutSeconds() + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Content search operation was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new RuntimeException("Content search operation failed", cause);
            }
        }
    }

    /**
     * Performs the actual file search operation.
     */
    private FileSearchResponse performFileSearch(Path searchPath, String pattern, 
                                               String originalPath, long startTime) throws IOException {
        List<FileInfo> allFiles = new ArrayList<>();

        // Search in regular files
        List<FileInfo> regularFiles = fileSystemService.scanDirectory(searchPath, pattern);
        allFiles.addAll(regularFiles);

        // Search in archives
        List<FileInfo> archiveFiles = searchInArchives(searchPath, pattern);
        allFiles.addAll(archiveFiles);

        // Limit results
        if (allFiles.size() > properties.getMaxSearchResults()) {
            logger.debug("Limiting search results to {} items", properties.getMaxSearchResults());
            allFiles = allFiles.subList(0, properties.getMaxSearchResults());
        }

        long searchTime = System.currentTimeMillis() - startTime;
        logger.info("File search completed in {}ms. Found {} files matching pattern '{}'", 
                searchTime, allFiles.size(), pattern);

        return new FileSearchResponse(allFiles, allFiles.size(), originalPath, pattern, searchTime);
    }

    /**
     * Searches for files within archives in the specified directory.
     */
    private List<FileInfo> searchInArchives(Path directory, String pattern) throws IOException {
        List<FileInfo> archiveResults = new ArrayList<>();

        // Find all archive files in the directory
        List<FileInfo> allFiles = fileSystemService.scanDirectory(directory, "*");
        
        for (FileInfo fileInfo : allFiles) {
            Path filePath = Paths.get(fileInfo.getFullPath());
            
            if (archiveHandlerService.isArchiveFile(filePath)) {
                try {
                    // Validate archive file access
                    if (securityValidator.isFileAccessible(filePath)) {
                        List<FileInfo> archiveContents = archiveHandlerService.listArchiveContents(filePath, pattern);
                        archiveResults.addAll(archiveContents);
                        
                        // Check if we've reached the limit
                        if (archiveResults.size() >= properties.getMaxSearchResults()) {
                            logger.debug("Reached maximum search results limit while processing archives");
                            break;
                        }
                    } else {
                        logger.warn("Access denied to archive file: {}", filePath);
                    }
                } catch (IOException e) {
                    logger.warn("Error processing archive file: {}", filePath, e);
                    // Continue with other archives
                }
            }
        }

        return archiveResults;
    }

    /**
     * Downloads a regular file.
     */
    private InputStream downloadRegularFile(String filePath) throws IOException, SecurityException {
        // Validate path security
        if (!securityValidator.isPathAllowed(filePath)) {
            throw new SecurityException("Access denied to file: " + filePath);
        }

        Path file = Paths.get(filePath);
        
        if (!securityValidator.isFileAccessible(file)) {
            throw new SecurityException("File is not accessible: " + filePath);
        }

        if (!Files.exists(file)) {
            throw new IOException("File not found: " + filePath);
        }

        if (!Files.isRegularFile(file)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        logger.debug("Downloading regular file: {}", filePath);
        return Files.newInputStream(file);
    }

    /**
     * Downloads a file from within an archive.
     */
    private InputStream downloadArchiveEntry(String filePath) throws IOException, SecurityException {
        // Parse archive path and entry path
        String[] parts = filePath.split("::", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid archive entry path format. Expected: archivePath::entryPath");
        }

        String archivePath = parts[0];
        String entryPath = parts[1];

        // Validate archive path security
        if (!securityValidator.isPathAllowed(archivePath)) {
            throw new SecurityException("Access denied to archive: " + archivePath);
        }

        Path archive = Paths.get(archivePath);
        
        if (!securityValidator.isFileAccessible(archive)) {
            throw new SecurityException("Archive is not accessible: " + archivePath);
        }

        if (!archiveHandlerService.isArchiveFile(archive)) {
            throw new IOException("File is not a supported archive format: " + archivePath);
        }

        logger.debug("Downloading archive entry: {} from {}", entryPath, archivePath);
        return archiveHandlerService.extractFileFromArchive(archive, entryPath);
    }

    /**
     * Searches content in a regular file.
     */
    private ContentSearchResponse searchContentInRegularFile(ContentSearchRequest request) 
            throws IOException, SecurityException {
        
        // Validate path security
        if (!securityValidator.isPathAllowed(request.getFilePath())) {
            throw new SecurityException("Access denied to file: " + request.getFilePath());
        }

        Path file = Paths.get(request.getFilePath());
        
        if (!securityValidator.isFileAccessible(file)) {
            throw new SecurityException("File is not accessible: " + request.getFilePath());
        }

        // Check if file appears to be text
        if (!contentSearchService.isTextFile(file)) {
            throw new IOException("File does not appear to be a text file: " + request.getFilePath());
        }

        logger.debug("Searching content in regular file: {}", request.getFilePath());
        return contentSearchService.searchInFile(request);
    }

    /**
     * Searches content in an archive entry.
     */
    private ContentSearchResponse searchContentInArchiveEntry(ContentSearchRequest request) 
            throws IOException, SecurityException {
        
        // Parse archive path and entry path
        String[] parts = request.getFilePath().split("::", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid archive entry path format. Expected: archivePath::entryPath");
        }

        String archivePath = parts[0];
        String entryPath = parts[1];

        // Validate archive path security
        if (!securityValidator.isPathAllowed(archivePath)) {
            throw new SecurityException("Access denied to archive: " + archivePath);
        }

        Path archive = Paths.get(archivePath);
        
        if (!securityValidator.isFileAccessible(archive)) {
            throw new SecurityException("Archive is not accessible: " + archivePath);
        }

        if (!archiveHandlerService.isArchiveFile(archive)) {
            throw new IOException("File is not a supported archive format: " + archivePath);
        }

        logger.debug("Searching content in archive entry: {} from {}", entryPath, archivePath);
        return contentSearchService.searchInArchiveFile(archive, entryPath, request);
    }

    /**
     * Validates if a path is allowed for access.
     * This is a convenience method that delegates to the SecurityValidator.
     * 
     * @param path the path to validate
     * @return true if the path is allowed, false otherwise
     */
    public boolean isPathAllowed(String path) {
        return securityValidator.isPathAllowed(path);
    }

    /**
     * Shuts down the executor service when the service is destroyed.
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            logger.info("Shutting down ArchiveSearchService executor");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}