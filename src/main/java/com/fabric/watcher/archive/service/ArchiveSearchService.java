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
import java.time.Duration;
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
    private final ArchiveSearchMetricsService metricsService;
    private final ArchiveSearchAuditService auditService;
    private final ExecutorService executorService;

    @Autowired
    public ArchiveSearchService(ArchiveSearchProperties properties,
                               SecurityValidator securityValidator,
                               FileSystemService fileSystemService,
                               ArchiveHandlerService archiveHandlerService,
                               ContentSearchService contentSearchService,
                               ArchiveSearchMetricsService metricsService,
                               ArchiveSearchAuditService auditService) {
        this.properties = properties;
        this.securityValidator = securityValidator;
        this.fileSystemService = fileSystemService;
        this.archiveHandlerService = archiveHandlerService;
        this.contentSearchService = contentSearchService;
        this.metricsService = metricsService;
        this.auditService = auditService;
        
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
        return searchFiles(path, pattern, null, null, null);
    }
    
    /**
     * Searches for files with monitoring and audit logging.
     */
    public FileSearchResponse searchFiles(String path, String pattern, String sessionId, 
                                        String userAgent, String remoteAddr) 
            throws IOException, IllegalArgumentException, SecurityException, TimeoutException {
        
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Search path cannot be null or empty");
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Search pattern cannot be null or empty");
        }

        // Generate session ID if not provided
        if (sessionId == null) {
            sessionId = auditService.generateSessionId();
        }

        logger.info("Starting file search - path: '{}', pattern: '{}', session: '{}'", path, pattern, sessionId);
        long startTime = System.currentTimeMillis();

        // Record metrics and audit
        metricsService.recordFileSearchRequest();
        auditService.logFileSearchRequest(sessionId, userAgent, remoteAddr, path, pattern);

        try {
            // Validate path security
            if (!securityValidator.isPathAllowed(path)) {
                auditService.logSecurityViolation(sessionId, userAgent, remoteAddr, 
                        "PATH_ACCESS_DENIED", path, "Path not in allowed list");
                metricsService.recordSecurityViolation("PATH_ACCESS_DENIED", path);
                throw new SecurityException("Access denied to path: " + path);
            }

            Path searchPath = Paths.get(path);
            if (!Files.exists(searchPath)) {
                logger.warn("Search path does not exist: {}", path);
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
                FileSearchResponse response = new FileSearchResponse(new ArrayList<>(), 0, path, pattern, duration.toMillis());
                
                metricsService.recordFileSearchSuccess(duration, 0);
                auditService.logFileSearchSuccess(sessionId, path, pattern, 0, duration.toMillis());
                
                return response;
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
                FileSearchResponse response = searchTask.get(properties.getSearchTimeoutSeconds(), TimeUnit.SECONDS);
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
                
                metricsService.recordFileSearchSuccess(duration, response.getTotalCount());
                auditService.logFileSearchSuccess(sessionId, path, pattern, response.getTotalCount(), duration.toMillis());
                
                return response;
                
            } catch (java.util.concurrent.TimeoutException e) {
                searchTask.cancel(true);
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
                
                metricsService.recordTimeout("FILE_SEARCH");
                metricsService.recordFileSearchFailure(duration, "TIMEOUT");
                auditService.logOperationTimeout(sessionId, "FILE_SEARCH", path, duration.toMillis());
                auditService.logFileSearchFailure(sessionId, path, pattern, "TIMEOUT", 
                        "Search operation timed out", duration.toMillis());
                
                logger.warn("File search timed out after {} seconds for path: {}", 
                        properties.getSearchTimeoutSeconds(), path);
                throw new TimeoutException("Search operation timed out after " + 
                        properties.getSearchTimeoutSeconds() + " seconds");
            }
            
        } catch (SecurityException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordFileSearchFailure(duration, "SECURITY_VIOLATION");
            auditService.logFileSearchFailure(sessionId, path, pattern, "SECURITY_VIOLATION", e.getMessage(), duration.toMillis());
            throw e;
        } catch (IllegalArgumentException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordFileSearchFailure(duration, "INVALID_ARGUMENT");
            auditService.logFileSearchFailure(sessionId, path, pattern, "INVALID_ARGUMENT", e.getMessage(), duration.toMillis());
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordFileSearchFailure(duration, "INTERRUPTED");
            auditService.logFileSearchFailure(sessionId, path, pattern, "INTERRUPTED", e.getMessage(), duration.toMillis());
            throw new RuntimeException("Search operation was interrupted", e);
        } catch (ExecutionException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            Throwable cause = e.getCause();
            String errorType = cause.getClass().getSimpleName();
            
            metricsService.recordFileSearchFailure(duration, errorType);
            auditService.logFileSearchFailure(sessionId, path, pattern, errorType, cause.getMessage(), duration.toMillis());
            
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new RuntimeException("Search operation failed", cause);
            }
        } catch (Exception e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordFileSearchFailure(duration, e.getClass().getSimpleName());
            auditService.logFileSearchFailure(sessionId, path, pattern, e.getClass().getSimpleName(), e.getMessage(), duration.toMillis());
            throw e;
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
        return downloadFile(filePath, null, null, null);
    }
    
    /**
     * Downloads a file with monitoring and audit logging.
     */
    public InputStream downloadFile(String filePath, String sessionId, String userAgent, String remoteAddr) 
            throws IOException, IllegalArgumentException, SecurityException {
        
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        // Generate session ID if not provided
        if (sessionId == null) {
            sessionId = auditService.generateSessionId();
        }

        logger.info("Starting file download for path: '{}', session: '{}'", filePath, sessionId);
        long startTime = System.currentTimeMillis();

        // Record metrics and audit
        metricsService.recordDownloadRequest();
        auditService.logDownloadRequest(sessionId, userAgent, remoteAddr, filePath);

        try {
            InputStream inputStream;
            long fileSize = 0;
            
            // Check if this is an archive entry (contains archive path separator)
            if (filePath.contains("::")) {
                inputStream = downloadArchiveEntry(filePath);
                // For archive entries, we can't easily determine size beforehand
                fileSize = -1;
            } else {
                // For regular files, get size before download
                Path file = Paths.get(filePath);
                if (Files.exists(file) && Files.isRegularFile(file)) {
                    fileSize = Files.size(file);
                }
                inputStream = downloadRegularFile(filePath);
            }
            
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordDownloadSuccess(duration, fileSize > 0 ? fileSize : 0);
            auditService.logDownloadSuccess(sessionId, filePath, fileSize, duration.toMillis());
            
            return inputStream;
            
        } catch (SecurityException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordDownloadFailure(duration, "SECURITY_VIOLATION");
            auditService.logDownloadFailure(sessionId, filePath, "SECURITY_VIOLATION", e.getMessage(), duration.toMillis());
            throw e;
        } catch (IllegalArgumentException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordDownloadFailure(duration, "INVALID_ARGUMENT");
            auditService.logDownloadFailure(sessionId, filePath, "INVALID_ARGUMENT", e.getMessage(), duration.toMillis());
            throw e;
        } catch (IOException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordDownloadFailure(duration, "IO_ERROR");
            auditService.logDownloadFailure(sessionId, filePath, "IO_ERROR", e.getMessage(), duration.toMillis());
            throw e;
        } catch (Exception e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordDownloadFailure(duration, e.getClass().getSimpleName());
            auditService.logDownloadFailure(sessionId, filePath, e.getClass().getSimpleName(), e.getMessage(), duration.toMillis());
            throw e;
        }
    }

    /**
     * Searches for content within a specific file.
     * Supports both regular files and files within archives.
     * 
     * @param filePath the path to the file to search in
     * @param searchTerm the text to search for
     * @param caseSensitive whether the search should be case sensitive
     * @param wholeWord whether to match whole words only
     * @return ContentSearchResponse containing search results
     * @throws IOException if file cannot be accessed or read
     * @throws IllegalArgumentException if parameters are invalid
     * @throws SecurityException if file access is denied
     * @throws TimeoutException if search operation times out
     */
    public ContentSearchResponse searchContent(String filePath, String searchTerm, 
                                             Boolean caseSensitive, Boolean wholeWord) 
            throws IOException, IllegalArgumentException, SecurityException, TimeoutException {
        
        ContentSearchRequest request = new ContentSearchRequest(filePath, searchTerm, caseSensitive, wholeWord);
        return searchContent(request);
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
        return searchContent(request, null, null, null);
    }
    
    /**
     * Searches content with monitoring and audit logging.
     */
    public ContentSearchResponse searchContent(ContentSearchRequest request, String sessionId, 
                                             String userAgent, String remoteAddr) 
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

        // Generate session ID if not provided
        if (sessionId == null) {
            sessionId = auditService.generateSessionId();
        }

        logger.info("Starting content search - file: '{}', term: '{}', session: '{}'", 
                request.getFilePath(), request.getSearchTerm(), sessionId);
        long startTime = System.currentTimeMillis();

        // Record metrics and audit
        metricsService.recordContentSearchRequest();
        auditService.logContentSearchRequest(sessionId, userAgent, remoteAddr, 
                request.getFilePath(), request.getSearchTerm(), 
                request.getCaseSensitive() != null ? request.getCaseSensitive() : false);

        try {
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
                ContentSearchResponse response = searchTask.get(properties.getSearchTimeoutSeconds(), TimeUnit.SECONDS);
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
                
                metricsService.recordContentSearchSuccess(duration, response.getTotalMatches(), response.isTruncated());
                auditService.logContentSearchSuccess(sessionId, request.getFilePath(), 
                        response.getTotalMatches(), response.isTruncated(), duration.toMillis());
                
                return response;
                
            } catch (java.util.concurrent.TimeoutException e) {
                searchTask.cancel(true);
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
                
                metricsService.recordTimeout("CONTENT_SEARCH");
                metricsService.recordContentSearchFailure(duration, "TIMEOUT");
                auditService.logOperationTimeout(sessionId, "CONTENT_SEARCH", request.getFilePath(), duration.toMillis());
                auditService.logContentSearchFailure(sessionId, request.getFilePath(), "TIMEOUT", 
                        "Content search operation timed out", duration.toMillis());
                
                logger.warn("Content search timed out after {} seconds for file: {}", 
                        properties.getSearchTimeoutSeconds(), request.getFilePath());
                throw new TimeoutException("Content search operation timed out after " + 
                        properties.getSearchTimeoutSeconds() + " seconds");
            }
            
        } catch (SecurityException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordContentSearchFailure(duration, "SECURITY_VIOLATION");
            auditService.logContentSearchFailure(sessionId, request.getFilePath(), "SECURITY_VIOLATION", e.getMessage(), duration.toMillis());
            throw e;
        } catch (IllegalArgumentException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordContentSearchFailure(duration, "INVALID_ARGUMENT");
            auditService.logContentSearchFailure(sessionId, request.getFilePath(), "INVALID_ARGUMENT", e.getMessage(), duration.toMillis());
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordContentSearchFailure(duration, "INTERRUPTED");
            auditService.logContentSearchFailure(sessionId, request.getFilePath(), "INTERRUPTED", e.getMessage(), duration.toMillis());
            throw new RuntimeException("Content search operation was interrupted", e);
        } catch (ExecutionException e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            Throwable cause = e.getCause();
            String errorType = cause.getClass().getSimpleName();
            
            metricsService.recordContentSearchFailure(duration, errorType);
            auditService.logContentSearchFailure(sessionId, request.getFilePath(), errorType, cause.getMessage(), duration.toMillis());
            
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new RuntimeException("Content search operation failed", cause);
            }
        } catch (Exception e) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            metricsService.recordContentSearchFailure(duration, e.getClass().getSimpleName());
            auditService.logContentSearchFailure(sessionId, request.getFilePath(), e.getClass().getSimpleName(), e.getMessage(), duration.toMillis());
            throw e;
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
     * Gets the current API status and configuration information.
     * 
     * @return API status object containing configuration and runtime information
     */
    public Object getApiStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        
        status.put("enabled", properties.isEnabled());
        status.put("environment", System.getProperty("spring.profiles.active", "unknown"));
        status.put("version", "1.0.0");
        status.put("supportedArchiveTypes", properties.getSupportedArchiveTypes());
        status.put("maxFileSize", properties.getMaxFileSize());
        status.put("maxSearchResults", properties.getMaxSearchResults());
        status.put("searchTimeoutSeconds", properties.getSearchTimeoutSeconds());
        status.put("allowedPaths", properties.getAllowedPaths());
        status.put("excludedPaths", properties.getExcludedPaths());
        
        return status;
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