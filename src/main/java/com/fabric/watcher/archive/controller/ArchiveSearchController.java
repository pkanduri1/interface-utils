package com.fabric.watcher.archive.controller;

import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.exception.ArchiveSearchException.ErrorCode;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.service.ArchiveSearchAuditService;
import com.fabric.watcher.archive.service.FileUploadService;
import com.fabric.watcher.archive.service.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for archive search and file operations.
 * 
 * <p>This controller provides endpoints for searching files, downloading files,
 * searching content within files, and uploading files. All endpoints require
 * authentication via JWT tokens.</p>
 * 
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/archive")
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
@Tag(name = "Archive Search", description = "File search, download, content search, and upload operations")
@SecurityRequirement(name = "bearerAuth")
public class ArchiveSearchController {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchController.class);

    private final FileUploadService fileUploadService;
    private final JwtTokenService jwtTokenService;
    private final ArchiveSearchAuditService auditService;

    /**
     * Constructor for ArchiveSearchController.
     *
     * @param fileUploadService the file upload service
     * @param jwtTokenService   the JWT token service
     * @param auditService      the audit logging service
     */
    public ArchiveSearchController(FileUploadService fileUploadService,
                                 JwtTokenService jwtTokenService,
                                 ArchiveSearchAuditService auditService) {
        this.fileUploadService = fileUploadService;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        
        logger.info("Archive Search Controller initialized");
    }

    /**
     * Searches for files matching the specified pattern.
     *
     * @param path        the path to search in
     * @param pattern     the file pattern to match
     * @param httpRequest the HTTP servlet request for authentication
     * @return ResponseEntity containing search results
     */
    @GetMapping("/search")
    @Operation(summary = "Search for files", 
               description = "Searches for files in directories and archives matching the specified pattern")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully",
                    content = @Content(schema = @Schema(implementation = FileSearchResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "400", description = "Invalid search parameters"),
        @ApiResponse(responseCode = "500", description = "Search operation failed")
    })
    public ResponseEntity<FileSearchResponse> searchFiles(
            @Parameter(description = "Path to search in", required = true, example = "/data/archives")
            @RequestParam String path,
            @Parameter(description = "File pattern to match", required = true, example = "*.log")
            @RequestParam String pattern,
            HttpServletRequest httpRequest) {
        
        UserContext userContext = extractUserContext(httpRequest);
        String ipAddress = getClientIpAddress(httpRequest);
        
        logger.debug("File search request from user: {} for path: {}, pattern: {}", 
                    userContext.getUserId(), path, pattern);

        try {
            long startTime = System.currentTimeMillis();
            
            // TODO: Implement actual file search logic when ArchiveSearchService is available
            // For now, return empty results as placeholder
            List<FileInfo> files = new ArrayList<>();
            long searchTime = System.currentTimeMillis() - startTime;
            
            FileSearchResponse response = new FileSearchResponse(files, 0, path, pattern, searchTime);
            
            // Log the search operation
            auditService.logFileSearch(userContext.getUserId(), pattern, path, 0, ipAddress, null);
            
            logger.info("File search completed for user: {}, found {} files in {}ms", 
                       userContext.getUserId(), files.size(), searchTime);
            
            return ResponseEntity.ok(response);

        } catch (ArchiveSearchException e) {
            logger.error("File search failed for user {}: {}", userContext.getUserId(), e.getMessage());
            auditService.logSecurityEvent(userContext.getUserId(), "FILE_SEARCH_FAILURE", path, 
                                         ipAddress, e.getErrorCode().getCode() + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during file search for user {}: {}", 
                        userContext.getUserId(), e.getMessage(), e);
            auditService.logSecurityEvent(userContext.getUserId(), "FILE_SEARCH_ERROR", path, 
                                         ipAddress, "UNEXPECTED_ERROR: " + e.getMessage());
            throw new ArchiveSearchException(ErrorCode.SEARCH_FAILED, 
                                           "File search failed due to unexpected error");
        }
    }

    /**
     * Downloads a specific file.
     *
     * @param filePath    the path to the file to download
     * @param httpRequest the HTTP servlet request for authentication
     * @return ResponseEntity containing the file resource
     */
    @GetMapping("/download")
    @Operation(summary = "Download file", 
               description = "Downloads a specific file from the file system or archive")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File downloaded successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "File not found"),
        @ApiResponse(responseCode = "500", description = "Download failed")
    })
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "Path to the file to download", required = true, 
                      example = "/data/archives/application.log")
            @RequestParam String filePath,
            HttpServletRequest httpRequest) {
        
        UserContext userContext = extractUserContext(httpRequest);
        String ipAddress = getClientIpAddress(httpRequest);
        
        logger.debug("File download request from user: {} for file: {}", 
                    userContext.getUserId(), filePath);

        try {
            // TODO: Implement actual file download logic when ArchiveSearchService is available
            // For now, throw not implemented exception
            throw new ArchiveSearchException(ErrorCode.NOT_IMPLEMENTED, 
                                           "File download functionality not yet implemented");

        } catch (ArchiveSearchException e) {
            logger.error("File download failed for user {}: {}", userContext.getUserId(), e.getMessage());
            auditService.logSecurityEvent(userContext.getUserId(), "FILE_DOWNLOAD_FAILURE", filePath, 
                                         ipAddress, e.getErrorCode().getCode() + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during file download for user {}: {}", 
                        userContext.getUserId(), e.getMessage(), e);
            auditService.logSecurityEvent(userContext.getUserId(), "FILE_DOWNLOAD_ERROR", filePath, 
                                         ipAddress, "UNEXPECTED_ERROR: " + e.getMessage());
            throw new ArchiveSearchException(ErrorCode.DOWNLOAD_FAILED, 
                                           "File download failed due to unexpected error");
        }
    }

    /**
     * Searches for content within a file.
     *
     * @param request     the content search request
     * @param httpRequest the HTTP servlet request for authentication
     * @return ResponseEntity containing search results
     */
    @PostMapping("/content-search")
    @Operation(summary = "Search file content", 
               description = "Searches for text content within a specific file")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content search completed successfully",
                    content = @Content(schema = @Schema(implementation = ContentSearchResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "400", description = "Invalid search parameters"),
        @ApiResponse(responseCode = "404", description = "File not found"),
        @ApiResponse(responseCode = "500", description = "Content search failed")
    })
    public ResponseEntity<ContentSearchResponse> searchContent(
            @Parameter(description = "Content search request", required = true)
            @Valid @RequestBody ContentSearchRequest request,
            HttpServletRequest httpRequest) {
        
        UserContext userContext = extractUserContext(httpRequest);
        String ipAddress = getClientIpAddress(httpRequest);
        
        logger.debug("Content search request from user: {} for file: {}, term: {}", 
                    userContext.getUserId(), request.getFilePath(), request.getSearchTerm());

        try {
            long startTime = System.currentTimeMillis();
            
            // TODO: Implement actual content search logic when ContentSearchService is available
            // For now, return empty results as placeholder
            List<SearchMatch> matches = new ArrayList<>();
            long searchTime = System.currentTimeMillis() - startTime;
            
            ContentSearchResponse response = new ContentSearchResponse(matches, 0, false, null, searchTime);
            
            // Log the content search operation
            auditService.logContentSearch(userContext.getUserId(), request.getSearchTerm(), 
                                        request.getFilePath(), 0, ipAddress, null);
            
            logger.info("Content search completed for user: {}, found {} matches in {}ms", 
                       userContext.getUserId(), matches.size(), searchTime);
            
            return ResponseEntity.ok(response);

        } catch (ArchiveSearchException e) {
            logger.error("Content search failed for user {}: {}", userContext.getUserId(), e.getMessage());
            auditService.logSecurityEvent(userContext.getUserId(), "CONTENT_SEARCH_FAILURE", 
                                         request.getFilePath(), ipAddress, 
                                         e.getErrorCode().getCode() + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during content search for user {}: {}", 
                        userContext.getUserId(), e.getMessage(), e);
            auditService.logSecurityEvent(userContext.getUserId(), "CONTENT_SEARCH_ERROR", 
                                         request.getFilePath(), ipAddress, 
                                         "UNEXPECTED_ERROR: " + e.getMessage());
            throw new ArchiveSearchException(ErrorCode.SEARCH_FAILED, 
                                           "Content search failed due to unexpected error");
        }
    }

    /**
     * Uploads a file to the specified target path.
     *
     * @param file        the file to upload
     * @param targetPath  the target path for the upload
     * @param overwrite   whether to overwrite existing files
     * @param description optional description for the upload
     * @param httpRequest the HTTP servlet request for authentication
     * @return ResponseEntity containing upload result
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload file", 
               description = "Uploads a file to the specified target path on the Linux server")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File uploaded successfully",
                    content = @Content(schema = @Schema(implementation = UploadResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "400", description = "Invalid upload parameters or file"),
        @ApiResponse(responseCode = "413", description = "File too large"),
        @ApiResponse(responseCode = "409", description = "File already exists and overwrite not enabled"),
        @ApiResponse(responseCode = "500", description = "Upload failed")
    })
    public ResponseEntity<UploadResponse> uploadFile(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Target path for the upload", required = true, 
                      example = "/opt/uploads/config.properties")
            @RequestParam("targetPath") String targetPath,
            @Parameter(description = "Whether to overwrite existing files", example = "false")
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite,
            @Parameter(description = "Optional description for the upload", 
                      example = "Configuration file update")
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest httpRequest) {
        
        UserContext userContext = extractUserContext(httpRequest);
        String ipAddress = getClientIpAddress(httpRequest);
        String sessionId = userContext.getSessionId();
        String userAgent = httpRequest.getHeader("User-Agent");
        
        logger.debug("File upload request from user: {} for file: {} to path: {}", 
                    userContext.getUserId(), file.getOriginalFilename(), targetPath);

        try {
            // Create upload request
            UploadRequest uploadRequest = new UploadRequest(file, targetPath, overwrite, description);
            
            // Perform the upload using the file upload service
            UploadResponse response = fileUploadService.uploadFile(uploadRequest, userContext.getUserId(), 
                                                                 sessionId, userAgent, ipAddress);
            
            logger.info("File upload completed successfully for user: {}, file: {}, target: {}", 
                       userContext.getUserId(), file.getOriginalFilename(), targetPath);
            
            return ResponseEntity.ok(response);

        } catch (ArchiveSearchException e) {
            logger.error("File upload failed for user {}: {}", userContext.getUserId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during file upload for user {}: {}", 
                        userContext.getUserId(), e.getMessage(), e);
            throw new ArchiveSearchException(ErrorCode.UPLOAD_FAILED, 
                                           "File upload failed due to unexpected error: " + e.getMessage());
        }
    }

    /**
     * Extracts user context from the HTTP request using JWT token.
     *
     * @param httpRequest the HTTP servlet request
     * @return the user context
     * @throws ArchiveSearchException if authentication fails
     */
    private UserContext extractUserContext(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        String token = jwtTokenService.extractTokenFromHeader(authHeader);
        
        if (token == null) {
            throw new ArchiveSearchException(ErrorCode.AUTHENTICATION_REQUIRED, 
                                           "Authentication token is required");
        }

        try {
            String userId = jwtTokenService.getUserIdFromToken(token);
            List<String> permissions = jwtTokenService.getPermissionsFromToken(token);
            
            // Create session ID from token hash for audit logging
            String sessionId = "session_" + Math.abs(token.hashCode());
            
            return new UserContext(userId, permissions, sessionId);
            
        } catch (ArchiveSearchException e) {
            if (e.getErrorCode() == ErrorCode.TOKEN_EXPIRED) {
                throw new ArchiveSearchException(ErrorCode.TOKEN_EXPIRED, 
                                               "Authentication token has expired. Please login again.");
            }
            throw new ArchiveSearchException(ErrorCode.INVALID_TOKEN, 
                                           "Invalid authentication token");
        }
    }

    /**
     * Extracts the client IP address from the HTTP request.
     *
     * @param request the HTTP servlet request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Inner class to hold user context information.
     */
    private static class UserContext {
        private final String userId;
        private final List<String> permissions;
        private final String sessionId;

        public UserContext(String userId, List<String> permissions, String sessionId) {
            this.userId = userId;
            this.permissions = permissions;
            this.sessionId = sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public List<String> getPermissions() {
            return permissions;
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}