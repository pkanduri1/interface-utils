package com.fabric.watcher.archive.controller;

import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.security.EnvironmentGuard;
import com.fabric.watcher.archive.service.ArchiveSearchService;
import com.fabric.watcher.archive.service.ArchiveSearchAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.file.Paths;
import jakarta.servlet.http.HttpServletRequest;

/**
 * REST controller for archive search operations.
 * Provides endpoints for searching files, downloading files, and searching content within files.
 * This controller is only enabled in non-production environments for security reasons.
 */
@RestController
@RequestMapping("/api/v1/archive")
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
@Validated
@Tag(name = "Archive Search", description = "API for searching and downloading files from directories and archives")
public class ArchiveSearchController {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchController.class);

    private final ArchiveSearchService archiveSearchService;
    private final EnvironmentGuard environmentGuard;
    private final ArchiveSearchAuditService auditService;

    public ArchiveSearchController(ArchiveSearchService archiveSearchService, 
                                 EnvironmentGuard environmentGuard,
                                 ArchiveSearchAuditService auditService) {
        this.archiveSearchService = archiveSearchService;
        this.environmentGuard = environmentGuard;
        this.auditService = auditService;
    }

    /**
     * Search for files in directories and archives using wildcard patterns.
     * 
     * @param path the directory path to search in
     * @param pattern the filename pattern with wildcards (* and ?)
     * @return list of matching files with metadata
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search for files using wildcard patterns",
        description = "Search for files in both regular directories and archive files using wildcard patterns. " +
                     "Supports * (match any characters) and ? (match single character) wildcards.",
        tags = {"File Search"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Search completed successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = FileSearchResponse.class),
                examples = @ExampleObject(
                    name = "Successful search",
                    value = """
                    {
                      "files": [
                        {
                          "fileName": "data.txt",
                          "fullPath": "/data/archives/data.txt",
                          "relativePath": "data.txt",
                          "size": 1024,
                          "lastModified": "2024-01-15T10:30:00",
                          "type": "REGULAR",
                          "archivePath": null
                        }
                      ],
                      "totalCount": 1,
                      "searchPath": "/data/archives",
                      "searchPattern": "*.txt",
                      "searchTimeMs": 150
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid search parameters",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Path access denied or security violation",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Search path not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Service unavailable in production environment",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    public ResponseEntity<FileSearchResponse> searchFiles(
            @Parameter(
                description = "Directory path to search in. Must be within allowed paths.",
                example = "/data/archives",
                required = true
            )
            @RequestParam 
            @NotBlank(message = "Path cannot be blank")
            @Size(max = 1000, message = "Path cannot exceed 1000 characters")
            String path,
            
            @Parameter(
                description = "Filename pattern with wildcards. Use * for any characters, ? for single character.",
                example = "*.txt",
                required = true
            )
            @RequestParam 
            @NotBlank(message = "Pattern cannot be blank")
            @Size(max = 255, message = "Pattern cannot exceed 255 characters")
            String pattern,
            
            HttpServletRequest request) {

        // Extract request information for monitoring
        String sessionId = auditService.generateSessionId();
        String userAgent = request.getHeader("User-Agent");
        String remoteAddr = getClientIpAddress(request);
        
        logger.info("Searching for files in path: {} with pattern: {}, session: {}", path, pattern, sessionId);
        
        // Log API access
        auditService.logApiAccess(sessionId, userAgent, remoteAddr, "/api/v1/archive/search", "GET", 200);
        
        // Validate environment
        if (!environmentGuard.isNonProductionEnvironment()) {
            auditService.logApiAccess(sessionId, userAgent, remoteAddr, "/api/v1/archive/search", "GET", 503);
            throw new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.ENVIRONMENT_RESTRICTED,
                "Archive search API is disabled in production environment"
            );
        }

        try {
            FileSearchResponse response = archiveSearchService.searchFiles(path, pattern, sessionId, userAgent, remoteAddr);
            logger.info("File search completed. Found {} files in {}ms", 
                       response.getTotalCount(), response.getSearchTimeMs());
            return ResponseEntity.ok(response);
            
        } catch (ArchiveSearchException e) {
            logger.error("Archive search error: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            logger.error("Security violation during file search: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during file search", e);
            throw new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.IO_ERROR,
                "File search operation failed",
                e.getMessage(),
                e
            );
        }
    }

    /**
     * Download a specific file from directory or archive.
     * 
     * @param filePath the full path to the file to download
     * @return the file content as a stream
     */
    @GetMapping("/download")
    @Operation(
        summary = "Download a specific file",
        description = "Download a file from either a regular directory or from within an archive file. " +
                     "The file path should be obtained from a previous search operation.",
        tags = {"File Download"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "File downloaded successfully",
            content = @Content(
                mediaType = "application/octet-stream",
                schema = @Schema(type = "string", format = "binary")
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid file path",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "File access denied or security violation",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "File not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Service unavailable in production environment",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    public ResponseEntity<Resource> downloadFile(
            @Parameter(
                description = "Full path to the file to download. Should be obtained from search results.",
                example = "/data/archives/data.txt",
                required = true
            )
            @RequestParam 
            @NotBlank(message = "File path cannot be blank")
            @Size(max = 1000, message = "File path cannot exceed 1000 characters")
            String filePath,
            
            HttpServletRequest request) {

        // Extract request information for monitoring
        String sessionId = auditService.generateSessionId();
        String userAgent = request.getHeader("User-Agent");
        String remoteAddr = getClientIpAddress(request);
        
        logger.info("Downloading file: {}, session: {}", filePath, sessionId);
        
        // Log API access
        auditService.logApiAccess(sessionId, userAgent, remoteAddr, "/api/v1/archive/download", "GET", 200);
        
        // Validate environment
        if (!environmentGuard.isNonProductionEnvironment()) {
            auditService.logApiAccess(sessionId, userAgent, remoteAddr, "/api/v1/archive/download", "GET", 503);
            throw new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.ENVIRONMENT_RESTRICTED,
                "Archive search API is disabled in production environment"
            );
        }

        try {
            InputStream fileStream = archiveSearchService.downloadFile(filePath, sessionId, userAgent, remoteAddr);
            
            // Extract filename for Content-Disposition header
            String fileName = Paths.get(filePath).getFileName().toString();
            
            // Determine content type based on file extension
            String contentType = determineContentType(fileName);
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);
            
            logger.info("File download initiated for: {}", fileName);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(fileStream));
                
        } catch (ArchiveSearchException e) {
            logger.error("Archive search error during download: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            logger.error("Security violation during file download: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during file download", e);
            throw new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.IO_ERROR,
                "File download operation failed",
                e.getMessage(),
                e
            );
        }
    }

    /**
     * Search for text content within a specific file.
     * 
     * @param request the content search request containing file path and search parameters
     * @return search results with matching lines
     */
    @PostMapping("/content-search")
    @Operation(
        summary = "Search for text content within a file",
        description = "Search for specific text within a file's content. Returns matching lines with context. " +
                     "Results are limited to 100 lines for performance reasons.",
        tags = {"Content Search"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Content search completed successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ContentSearchResponse.class),
                examples = @ExampleObject(
                    name = "Successful content search",
                    value = """
                    {
                      "matches": [
                        {
                          "lineNumber": 15,
                          "lineContent": "This is a sample line with search term",
                          "columnStart": 30,
                          "columnEnd": 41
                        }
                      ],
                      "totalMatches": 1,
                      "truncated": false,
                      "downloadSuggestion": null,
                      "searchTimeMs": 85
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid search request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "File access denied or security violation",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "File not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "File cannot be searched (binary file, corrupted, etc.)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Service unavailable in production environment",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    public ResponseEntity<ContentSearchResponse> searchContent(
            @Parameter(
                description = "Content search request with file path and search parameters",
                required = true
            )
            @Valid @RequestBody ContentSearchRequest request,
            
            HttpServletRequest httpRequest) {

        // Extract request information for monitoring
        String sessionId = auditService.generateSessionId();
        String userAgent = httpRequest.getHeader("User-Agent");
        String remoteAddr = getClientIpAddress(httpRequest);
        
        logger.info("Searching content in file: {} for term: {}, session: {}", 
                   request.getFilePath(), request.getSearchTerm(), sessionId);
        
        // Log API access
        auditService.logApiAccess(sessionId, userAgent, remoteAddr, "/api/v1/archive/content-search", "POST", 200);
        
        // Validate environment
        if (!environmentGuard.isNonProductionEnvironment()) {
            auditService.logApiAccess(sessionId, userAgent, remoteAddr, "/api/v1/archive/content-search", "POST", 503);
            throw new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.ENVIRONMENT_RESTRICTED,
                "Archive search API is disabled in production environment"
            );
        }

        try {
            ContentSearchResponse response = archiveSearchService.searchContent(request, sessionId, userAgent, remoteAddr);
            
            logger.info("Content search completed. Found {} matches in {}ms", 
                       response.getTotalMatches(), response.getSearchTimeMs());
            return ResponseEntity.ok(response);
            
        } catch (ArchiveSearchException e) {
            logger.error("Archive search error during content search: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            logger.error("Security violation during content search: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during content search", e);
            throw new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.IO_ERROR,
                "Content search operation failed",
                e.getMessage(),
                e
            );
        }
    }

    /**
     * Get API status and configuration information.
     * 
     * @return API status information
     */
    @GetMapping("/status")
    @Operation(
        summary = "Get archive search API status",
        description = "Returns the current status of the archive search API including configuration and environment information.",
        tags = {"System"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Status retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "API status",
                    value = """
                    {
                      "enabled": true,
                      "environment": "development",
                      "version": "1.0.0",
                      "supportedArchiveTypes": ["zip", "tar", "tar.gz", "jar"],
                      "maxFileSize": 104857600,
                      "maxSearchResults": 100
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Service unavailable in production environment",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    public ResponseEntity<Object> getStatus() {
        logger.info("Getting archive search API status");
        
        try {
            Object status = archiveSearchService.getApiStatus();
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error retrieving API status", e);
            throw e;
        }
    }

    /**
     * Determine content type based on file extension
     */
    private String determineContentType(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        
        if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".log")) {
            return MediaType.TEXT_PLAIN_VALUE;
        } else if (lowerFileName.endsWith(".json")) {
            return MediaType.APPLICATION_JSON_VALUE;
        } else if (lowerFileName.endsWith(".xml")) {
            return MediaType.APPLICATION_XML_VALUE;
        } else if (lowerFileName.endsWith(".csv")) {
            return "text/csv";
        } else if (lowerFileName.endsWith(".zip")) {
            return "application/zip";
        } else if (lowerFileName.endsWith(".tar") || lowerFileName.endsWith(".tar.gz")) {
            return "application/x-tar";
        } else {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
    
    /**
     * Extract client IP address from request, considering proxy headers.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}