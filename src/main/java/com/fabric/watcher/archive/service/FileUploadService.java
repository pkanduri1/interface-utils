package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.model.UploadRequest;
import com.fabric.watcher.archive.model.UploadResponse;
import com.fabric.watcher.archive.security.SecurityValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Service for secure file uploads to Linux servers.
 * 
 * <p>This service provides secure file upload functionality with validation,
 * size limits, file type checking, and path security validation. It supports
 * uploading files to specified server paths with comprehensive audit logging.</p>
 * 
 * @since 1.0
 */
@Service
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class FileUploadService {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadService.class);

    private final ArchiveSearchProperties properties;
    private final SecurityValidator securityValidator;
    private final ArchiveSearchAuditService auditService;
    private final Semaphore uploadSemaphore;

    @Autowired
    public FileUploadService(ArchiveSearchProperties properties,
                           SecurityValidator securityValidator,
                           ArchiveSearchAuditService auditService) {
        this.properties = properties;
        this.securityValidator = securityValidator;
        this.auditService = auditService;
        this.uploadSemaphore = new Semaphore(properties.getUpload().getMaxConcurrentUploads());
    }

    /**
     * Uploads a file to the specified target path on the Linux server.
     * 
     * @param uploadRequest the upload request containing file and target path
     * @param userId the authenticated user ID
     * @param sessionId the session ID for audit logging
     * @param userAgent the user agent for audit logging
     * @param remoteAddr the remote address for audit logging
     * @return UploadResponse containing the result of the upload operation
     * @throws ArchiveSearchException if upload fails due to validation or I/O errors
     */
    public UploadResponse uploadFile(UploadRequest uploadRequest, String userId, 
                                   String sessionId, String userAgent, String remoteAddr) {
        long startTime = System.currentTimeMillis();
        
        logger.info("Starting file upload for user: {}, target path: {}", 
                   userId, uploadRequest.getTargetPath());

        // Log upload request
        auditService.logFileUploadRequest(sessionId, userAgent, remoteAddr, 
                                        uploadRequest.getFile().getOriginalFilename(),
                                        uploadRequest.getTargetPath());

        try {
            // Acquire upload semaphore to limit concurrent uploads
            if (!uploadSemaphore.tryAcquire()) {
                throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.UPLOAD_LIMIT_EXCEEDED, 
                    "Maximum concurrent uploads exceeded. Please try again later.");
            }

            try {
                // Validate the upload request
                validateUploadRequest(uploadRequest);

                // Validate file type and size
                validateFile(uploadRequest.getFile());

                // Validate and sanitize target path
                String sanitizedTargetPath = validateAndSanitizeTargetPath(uploadRequest.getTargetPath());

                // Create target directory if needed
                Path targetPath = Paths.get(sanitizedTargetPath);
                createDirectoriesIfNeeded(targetPath.getParent());

                // Check if file exists and handle overwrite logic
                handleExistingFile(targetPath, uploadRequest.isOverwrite());

                // Transfer the file
                transferFile(uploadRequest.getFile(), targetPath);

                // Create successful response
                UploadResponse response = new UploadResponse(
                    uploadRequest.getFile().getOriginalFilename(),
                    sanitizedTargetPath,
                    uploadRequest.getFile().getSize(),
                    userId
                );

                long duration = System.currentTimeMillis() - startTime;
                
                // Log successful upload
                auditService.logFileUploadSuccess(sessionId, 
                                                uploadRequest.getFile().getOriginalFilename(),
                                                sanitizedTargetPath, 
                                                uploadRequest.getFile().getSize(),
                                                duration);

                logger.info("File upload completed successfully for user: {}, file: {}, target: {}, duration: {}ms",
                           userId, uploadRequest.getFile().getOriginalFilename(), sanitizedTargetPath, duration);

                return response;

            } finally {
                uploadSemaphore.release();
            }

        } catch (ArchiveSearchException e) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.logFileUploadFailure(sessionId, 
                                            uploadRequest.getFile().getOriginalFilename(),
                                            uploadRequest.getTargetPath(),
                                            e.getErrorCode().getCode(),
                                            e.getMessage(),
                                            duration);
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.logFileUploadFailure(sessionId, 
                                            uploadRequest.getFile().getOriginalFilename(),
                                            uploadRequest.getTargetPath(),
                                            "UNEXPECTED_ERROR",
                                            e.getMessage(),
                                            duration);
            
            logger.error("Unexpected error during file upload for user: {}", userId, e);
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.UPLOAD_FAILED, 
                "File upload failed due to unexpected error: " + e.getMessage());
        }
    }

    /**
     * Validates the upload request parameters.
     * 
     * @param uploadRequest the upload request to validate
     * @throws ArchiveSearchException if validation fails
     */
    private void validateUploadRequest(UploadRequest uploadRequest) {
        if (uploadRequest == null) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.INVALID_REQUEST, "Upload request cannot be null");
        }

        if (uploadRequest.getFile() == null) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.INVALID_REQUEST, "File cannot be null");
        }

        if (uploadRequest.getTargetPath() == null || uploadRequest.getTargetPath().trim().isEmpty()) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.INVALID_REQUEST, "Target path cannot be null or empty");
        }

        if (uploadRequest.getFile().isEmpty()) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.INVALID_REQUEST, "File cannot be empty");
        }
    }

    /**
     * Validates the uploaded file for type and size restrictions.
     * 
     * @param file the file to validate
     * @throws ArchiveSearchException if validation fails
     */
    private void validateFile(MultipartFile file) {
        // Validate file size
        if (file.getSize() > properties.getUpload().getMaxUploadSize()) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.FILE_TOO_LARGE, 
                String.format("File size %d bytes exceeds maximum allowed size %d bytes", 
                             file.getSize(), properties.getUpload().getMaxUploadSize()));
        }

        // Validate file type by extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.INVALID_FILE_TYPE, "File name cannot be null or empty");
        }

        if (!isFileTypeAllowed(originalFilename)) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.INVALID_FILE_TYPE, 
                String.format("File type not allowed. Allowed extensions: %s", 
                             properties.getUpload().getAllowedExtensions()));
        }

        logger.debug("File validation passed for: {}, size: {} bytes", originalFilename, file.getSize());
    }

    /**
     * Validates and sanitizes the target path.
     * 
     * @param targetPath the target path to validate
     * @return the sanitized target path
     * @throws ArchiveSearchException if path validation fails
     */
    private String validateAndSanitizeTargetPath(String targetPath) {
        // Use security validator to sanitize and validate path
        String sanitizedPath = securityValidator.sanitizePath(targetPath);
        if (sanitizedPath == null) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.PATH_NOT_ALLOWED, 
                "Invalid target path: contains malicious patterns");
        }

        // Check if path is allowed
        if (!securityValidator.isPathAllowed(sanitizedPath, properties.getAllowedPaths())) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.PATH_NOT_ALLOWED, 
                "Target path is not within allowed directories");
        }

        // Ensure path is within upload directory
        Path uploadDir = Paths.get(properties.getUpload().getUploadDirectory()).toAbsolutePath().normalize();
        Path targetPathObj = Paths.get(sanitizedPath).toAbsolutePath().normalize();
        
        if (!targetPathObj.startsWith(uploadDir)) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.PATH_NOT_ALLOWED, 
                "Target path must be within the configured upload directory");
        }

        return targetPathObj.toString();
    }

    /**
     * Creates directories if they don't exist and creation is enabled.
     * 
     * @param parentPath the parent directory path
     * @throws ArchiveSearchException if directory creation fails
     */
    private void createDirectoriesIfNeeded(Path parentPath) {
        if (parentPath == null) {
            return;
        }

        if (!Files.exists(parentPath)) {
            if (properties.getUpload().isCreateDirectories()) {
                try {
                    Files.createDirectories(parentPath);
                    logger.debug("Created directories: {}", parentPath);
                } catch (IOException e) {
                    throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.UPLOAD_FAILED, 
                        "Failed to create target directories: " + e.getMessage());
                }
            } else {
                throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.PATH_NOT_FOUND, 
                    "Target directory does not exist and directory creation is disabled");
            }
        }
    }

    /**
     * Handles existing file logic based on overwrite setting.
     * 
     * @param targetPath the target file path
     * @param overwrite whether to overwrite existing files
     * @throws ArchiveSearchException if file exists and overwrite is not allowed
     */
    private void handleExistingFile(Path targetPath, boolean overwrite) {
        if (Files.exists(targetPath) && !overwrite) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.FILE_ALREADY_EXISTS, 
                "File already exists and overwrite is not enabled");
        }
    }

    /**
     * Transfers the file to the target path.
     * 
     * @param file the multipart file to transfer
     * @param targetPath the target path
     * @throws ArchiveSearchException if file transfer fails
     */
    private void transferFile(MultipartFile file, Path targetPath) {
        try {
            // Use atomic move with replace existing if overwrite is enabled
            file.transferTo(targetPath.toFile());
            
            // Preserve timestamps if configured
            if (properties.getUpload().isPreserveTimestamps()) {
                // Set last modified time to current time since we don't have original timestamp
                Files.setLastModifiedTime(targetPath, 
                    java.nio.file.attribute.FileTime.from(java.time.Instant.now()));
            }

            logger.debug("File transferred successfully to: {}", targetPath);

        } catch (IOException e) {
            throw new ArchiveSearchException(ArchiveSearchException.ErrorCode.UPLOAD_FAILED, 
                "Failed to transfer file to target location: " + e.getMessage());
        }
    }

    /**
     * Checks if the file type is allowed based on extension.
     * 
     * @param filename the filename to check
     * @return true if file type is allowed, false otherwise
     */
    private boolean isFileTypeAllowed(String filename) {
        if (filename == null) {
            return false;
        }

        List<String> allowedExtensions = properties.getUpload().getAllowedExtensions();
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return true; // Allow all if no restrictions configured
        }

        String lowerFilename = filename.toLowerCase();
        return allowedExtensions.stream()
                .anyMatch(ext -> lowerFilename.endsWith(ext.toLowerCase()));
    }

    /**
     * Validates if the upload path is allowed.
     * 
     * @param path the path to validate
     * @return true if path is allowed, false otherwise
     */
    public boolean validateUploadPath(String path) {
        try {
            validateAndSanitizeTargetPath(path);
            return true;
        } catch (ArchiveSearchException e) {
            logger.debug("Upload path validation failed for path: {}, reason: {}", path, e.getMessage());
            return false;
        }
    }

    /**
     * Validates if the file type is allowed.
     * 
     * @param fileName the file name to validate
     * @return true if file type is allowed, false otherwise
     */
    public boolean validateFileType(String fileName) {
        return isFileTypeAllowed(fileName);
    }
}