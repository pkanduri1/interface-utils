package com.fabric.watcher.archive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for audit logging of archive search operations.
 * Provides comprehensive logging for security, compliance, and operational monitoring.
 */
@Service
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class ArchiveSearchAuditService {
    
    private static final Logger auditLogger = LoggerFactory.getLogger("ARCHIVE_SEARCH_AUDIT");
    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchAuditService.class);
    
    private final ObjectMapper objectMapper;
    
    // Audit event types
    public enum AuditEventType {
        FILE_SEARCH_REQUEST,
        FILE_SEARCH_SUCCESS,
        FILE_SEARCH_FAILURE,
        CONTENT_SEARCH_REQUEST,
        CONTENT_SEARCH_SUCCESS,
        CONTENT_SEARCH_FAILURE,
        DOWNLOAD_REQUEST,
        DOWNLOAD_SUCCESS,
        DOWNLOAD_FAILURE,
        SECURITY_VIOLATION,
        PATH_TRAVERSAL_ATTEMPT,
        UNAUTHORIZED_ACCESS,
        OPERATION_TIMEOUT,
        ARCHIVE_PROCESSING,
        API_ACCESS
    }
    
    public ArchiveSearchAuditService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Log a file search request.
     */
    public void logFileSearchRequest(String sessionId, String userAgent, String remoteAddr, 
                                   String searchPath, String pattern) {
        AuditEvent event = createBaseEvent(AuditEventType.FILE_SEARCH_REQUEST, sessionId, userAgent, remoteAddr)
                .addDetail("searchPath", sanitizePath(searchPath))
                .addDetail("pattern", pattern);
        
        logAuditEvent(event);
    }
    
    /**
     * Log a successful file search operation.
     */
    public void logFileSearchSuccess(String sessionId, String searchPath, String pattern, 
                                   int filesFound, long durationMs) {
        AuditEvent event = createBaseEvent(AuditEventType.FILE_SEARCH_SUCCESS, sessionId)
                .addDetail("searchPath", sanitizePath(searchPath))
                .addDetail("pattern", pattern)
                .addDetail("filesFound", filesFound)
                .addDetail("durationMs", durationMs);
        
        logAuditEvent(event);
    }
    
    /**
     * Log a failed file search operation.
     */
    public void logFileSearchFailure(String sessionId, String searchPath, String pattern, 
                                   String errorType, String errorMessage, long durationMs) {
        AuditEvent event = createBaseEvent(AuditEventType.FILE_SEARCH_FAILURE, sessionId)
                .addDetail("searchPath", sanitizePath(searchPath))
                .addDetail("pattern", pattern)
                .addDetail("errorType", errorType)
                .addDetail("errorMessage", sanitizeErrorMessage(errorMessage))
                .addDetail("durationMs", durationMs);
        
        logAuditEvent(event);
    }
    
    /**
     * Log a content search request.
     */
    public void logContentSearchRequest(String sessionId, String userAgent, String remoteAddr,
                                      String filePath, String searchTerm, boolean caseSensitive) {
        AuditEvent event = createBaseEvent(AuditEventType.CONTENT_SEARCH_REQUEST, sessionId, userAgent, remoteAddr)
                .addDetail("filePath", sanitizePath(filePath))
                .addDetail("searchTermLength", searchTerm != null ? searchTerm.length() : 0)
                .addDetail("caseSensitive", caseSensitive);
        
        logAuditEvent(event);
    }
    
    /**
     * Log a successful content search operation.
     */
    public void logContentSearchSuccess(String sessionId, String filePath, int matchesFound, 
                                      boolean truncated, long durationMs) {
        AuditEvent event = createBaseEvent(AuditEventType.CONTENT_SEARCH_SUCCESS, sessionId)
                .addDetail("filePath", sanitizePath(filePath))
                .addDetail("matchesFound", matchesFound)
                .addDetail("truncated", truncated)
                .addDetail("durationMs", durationMs);
        
        logAuditEvent(event);
    }
    
    /**
     * Log a failed content search operation.
     */
    public void logContentSearchFailure(String sessionId, String filePath, String errorType, 
                                      String errorMessage, long durationMs) {
        AuditEvent event = createBaseEvent(AuditEventType.CONTENT_SEARCH_FAILURE, sessionId)
                .addDetail("filePath", sanitizePath(filePath))
                .addDetail("errorType", errorType)
                .addDetail("errorMessage", sanitizeErrorMessage(errorMessage))
                .addDetail("durationMs", durationMs);
        
        logAuditEvent(event);
    }
    
    /**
     * Log a download request.
     */
    public void logDownloadRequest(String sessionId, String userAgent, String remoteAddr, String filePath) {
        AuditEvent event = createBaseEvent(AuditEventType.DOWNLOAD_REQUEST, sessionId, userAgent, remoteAddr)
                .addDetail("filePath", sanitizePath(filePath));
        
        logAuditEvent(event);
    }
    
    /**
     * Log a successful download operation.
     */
    public void logDownloadSuccess(String sessionId, String filePath, long fileSize, long durationMs) {
        AuditEvent event = createBaseEvent(AuditEventType.DOWNLOAD_SUCCESS, sessionId)
                .addDetail("filePath", sanitizePath(filePath))
                .addDetail("fileSize", fileSize)
                .addDetail("durationMs", durationMs);
        
        logAuditEvent(event);
    }
    
    /**
     * Log a failed download operation.
     */
    public void logDownloadFailure(String sessionId, String filePath, String errorType, 
                                 String errorMessage, long durationMs) {
        AuditEvent event = createBaseEvent(AuditEventType.DOWNLOAD_FAILURE, sessionId)
                .addDetail("filePath", sanitizePath(filePath))
                .addDetail("errorType", errorType)
                .addDetail("errorMessage", sanitizeErrorMessage(errorMessage))
                .addDetail("durationMs", durationMs);
        
        logAuditEvent(event);
    }
    
    /**
     * Log a security violation.
     */
    public void logSecurityViolation(String sessionId, String userAgent, String remoteAddr,
                                   String violationType, String attemptedPath, String details) {
        AuditEvent event = createBaseEvent(AuditEventType.SECURITY_VIOLATION, sessionId, userAgent, remoteAddr)
                .addDetail("violationType", violationType)
                .addDetail("attemptedPath", sanitizePath(attemptedPath))
                .addDetail("details", details)
                .addDetail("severity", "HIGH");
        
        logAuditEvent(event);
        
        // Also log as a warning for immediate attention
        logger.warn("SECURITY VIOLATION - Type: {}, Path: {}, Session: {}, Remote: {}", 
                violationType, sanitizePath(attemptedPath), sessionId, remoteAddr);
    }
    
    /**
     * Log a path traversal attempt.
     */
    public void logPathTraversalAttempt(String sessionId, String userAgent, String remoteAddr, 
                                      String attemptedPath) {
        AuditEvent event = createBaseEvent(AuditEventType.PATH_TRAVERSAL_ATTEMPT, sessionId, userAgent, remoteAddr)
                .addDetail("attemptedPath", sanitizePath(attemptedPath))
                .addDetail("severity", "CRITICAL");
        
        logAuditEvent(event);
        
        // Also log as an error for immediate attention
        logger.error("PATH TRAVERSAL ATTEMPT - Path: {}, Session: {}, Remote: {}", 
                sanitizePath(attemptedPath), sessionId, remoteAddr);
    }
    
    /**
     * Log unauthorized access attempt.
     */
    public void logUnauthorizedAccess(String sessionId, String userAgent, String remoteAddr,
                                    String attemptedResource, String reason) {
        AuditEvent event = createBaseEvent(AuditEventType.UNAUTHORIZED_ACCESS, sessionId, userAgent, remoteAddr)
                .addDetail("attemptedResource", sanitizePath(attemptedResource))
                .addDetail("reason", reason)
                .addDetail("severity", "HIGH");
        
        logAuditEvent(event);
    }
    
    /**
     * Log an operation timeout.
     */
    public void logOperationTimeout(String sessionId, String operationType, String resource, 
                                  long timeoutMs) {
        AuditEvent event = createBaseEvent(AuditEventType.OPERATION_TIMEOUT, sessionId)
                .addDetail("operationType", operationType)
                .addDetail("resource", sanitizePath(resource))
                .addDetail("timeoutMs", timeoutMs);
        
        logAuditEvent(event);
    }
    
    /**
     * Log archive processing activity.
     */
    public void logArchiveProcessing(String sessionId, String archivePath, String archiveFormat,
                                   int entriesProcessed, long durationMs) {
        AuditEvent event = createBaseEvent(AuditEventType.ARCHIVE_PROCESSING, sessionId)
                .addDetail("archivePath", sanitizePath(archivePath))
                .addDetail("archiveFormat", archiveFormat)
                .addDetail("entriesProcessed", entriesProcessed)
                .addDetail("durationMs", durationMs);
        
        logAuditEvent(event);
    }
    
    /**
     * Log general API access.
     */
    public void logApiAccess(String sessionId, String userAgent, String remoteAddr,
                           String endpoint, String method, int responseStatus) {
        AuditEvent event = createBaseEvent(AuditEventType.API_ACCESS, sessionId, userAgent, remoteAddr)
                .addDetail("endpoint", endpoint)
                .addDetail("method", method)
                .addDetail("responseStatus", responseStatus);
        
        logAuditEvent(event);
    }
    
    /**
     * Create a base audit event with common fields.
     */
    private AuditEvent createBaseEvent(AuditEventType eventType, String sessionId) {
        return new AuditEvent(eventType, sessionId);
    }
    
    /**
     * Create a base audit event with common fields including request information.
     */
    private AuditEvent createBaseEvent(AuditEventType eventType, String sessionId, 
                                     String userAgent, String remoteAddr) {
        return new AuditEvent(eventType, sessionId)
                .addDetail("userAgent", sanitizeUserAgent(userAgent))
                .addDetail("remoteAddr", remoteAddr);
    }
    
    /**
     * Log the audit event.
     */
    private void logAuditEvent(AuditEvent event) {
        try {
            // Set MDC for structured logging
            MDC.put("eventType", event.getEventType().name());
            MDC.put("sessionId", event.getSessionId());
            MDC.put("timestamp", event.getTimestamp());
            
            // Log as JSON for structured processing
            String jsonEvent = objectMapper.writeValueAsString(event.toMap());
            auditLogger.info(jsonEvent);
            
        } catch (Exception e) {
            logger.error("Failed to log audit event", e);
            // Fallback to simple logging
            auditLogger.error("AUDIT_LOG_ERROR - EventType: {}, SessionId: {}, Error: {}", 
                    event.getEventType(), event.getSessionId(), e.getMessage());
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Sanitize path information to prevent log injection and remove sensitive data.
     */
    private String sanitizePath(String path) {
        if (path == null) {
            return null;
        }
        
        // Remove potential log injection characters and limit length
        String sanitized = path.replaceAll("[\r\n\t]", "_")
                              .replaceAll("[\\p{Cntrl}]", "")
                              .trim();
        
        // Limit length to prevent log bloat
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 500) + "...[truncated]";
        }
        
        return sanitized;
    }
    
    /**
     * Sanitize error messages to prevent log injection.
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        
        String sanitized = message.replaceAll("[\r\n\t]", " ")
                                 .replaceAll("[\\p{Cntrl}]", "")
                                 .trim();
        
        // Limit length
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000) + "...[truncated]";
        }
        
        return sanitized;
    }
    
    /**
     * Sanitize user agent string.
     */
    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        
        String sanitized = userAgent.replaceAll("[\r\n\t]", " ")
                                   .replaceAll("[\\p{Cntrl}]", "")
                                   .trim();
        
        // Limit length
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...[truncated]";
        }
        
        return sanitized;
    }
    
    /**
     * Generate a session ID for tracking related operations.
     */
    public String generateSessionId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Inner class representing an audit event.
     */
    private static class AuditEvent {
        private final AuditEventType eventType;
        private final String sessionId;
        private final String timestamp;
        private final String eventId;
        private final Map<String, Object> details;
        
        public AuditEvent(AuditEventType eventType, String sessionId) {
            this.eventType = eventType;
            this.sessionId = sessionId != null ? sessionId : "unknown";
            this.timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
            this.eventId = UUID.randomUUID().toString();
            this.details = new HashMap<>();
        }
        
        public AuditEvent addDetail(String key, Object value) {
            if (value != null) {
                details.put(key, value);
            }
            return this;
        }
        
        public AuditEventType getEventType() {
            return eventType;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("eventType", eventType.name());
            map.put("sessionId", sessionId);
            map.put("timestamp", timestamp);
            map.put("eventId", eventId);
            map.put("service", "archive-search");
            map.put("version", "1.0.0");
            map.putAll(details);
            return map;
        }
    }
}