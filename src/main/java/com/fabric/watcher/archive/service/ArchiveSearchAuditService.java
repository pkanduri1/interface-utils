package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.model.AuditEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for centralized audit logging of archive search operations.
 * 
 * <p>This service provides thread-safe audit logging capabilities for all
 * archive search related operations including authentication, file uploads,
 * downloads, and searches. All audit entries are written to a centralized
 * log file with timestamps for compliance and security monitoring.</p>
 * 
 * @since 1.0
 */
@Service
public class ArchiveSearchAuditService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchAuditService.class);
    
    private static final String DEFAULT_AUDIT_LOG_FILE = "/var/log/archive-search/audit.log";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ObjectMapper objectMapper;
    
    @Value("${archive.search.audit.log-file:" + DEFAULT_AUDIT_LOG_FILE + "}")
    private String auditLogFile;
    
    @Value("${archive.search.audit.enabled:true}")
    private boolean auditEnabled;
    
    private Path auditLogPath;

    /**
     * Constructor for ArchiveSearchAuditService.
     */
    public ArchiveSearchAuditService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Initialize the audit service and create necessary directories.
     */
    @PostConstruct
    public void initialize() {
        if (!auditEnabled) {
            logger.info("Audit logging is disabled");
            return;
        }
        
        try {
            auditLogPath = Paths.get(auditLogFile);
            
            // Create parent directories if they don't exist
            Path parentDir = auditLogPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.info("Created audit log directory: {}", parentDir);
            }
            
            // Create audit log file if it doesn't exist and write header
            if (!Files.exists(auditLogPath)) {
                writeAuditHeader();
                logger.info("Created audit log file: {}", auditLogPath);
            }
            
            logger.info("Audit service initialized with log file: {}", auditLogPath);
        } catch (IOException e) {
            logger.error("Failed to initialize audit service", e);
            throw new RuntimeException("Failed to initialize audit service", e);
        }
    }

    /**
     * Logs authentication events.
     *
     * @param userId    the user ID attempting authentication
     * @param success   whether authentication was successful
     * @param ipAddress the IP address of the client
     * @param details   additional details about the authentication attempt
     */
    public void logAuthentication(String userId, boolean success, String ipAddress, String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEntry entry = new AuditEntry(
            userId,
            success ? "AUTHENTICATION_SUCCESS" : "AUTHENTICATION_FAILURE",
            "AUTH_ENDPOINT",
            success,
            details,
            ipAddress
        );
        
        writeAuditEntry(entry);
        
        if (success) {
            logger.info("Authentication successful for user: {}", userId);
        } else {
            logger.warn("Authentication failed for user: {} from IP: {}", userId, ipAddress);
        }
    }

    /**
     * Logs file upload events.
     *
     * @param userId     the user ID performing the upload
     * @param fileName   the name of the uploaded file
     * @param targetPath the target path where the file was uploaded
     * @param success    whether the upload was successful
     * @param ipAddress  the IP address of the client
     * @param details    additional details about the upload
     */
    public void logFileUpload(String userId, String fileName, String targetPath, boolean success, 
                             String ipAddress, String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEntry entry = new AuditEntry(
            userId,
            "FILE_UPLOAD",
            targetPath + "/" + fileName,
            success,
            details,
            ipAddress
        );
        
        writeAuditEntry(entry);
        
        if (success) {
            logger.info("File upload successful - User: {}, File: {}, Target: {}", userId, fileName, targetPath);
        } else {
            logger.warn("File upload failed - User: {}, File: {}, Target: {}", userId, fileName, targetPath);
        }
    }

    /**
     * Logs file download events.
     *
     * @param userId    the user ID performing the download
     * @param fileName  the name of the downloaded file
     * @param filePath  the path of the downloaded file
     * @param success   whether the download was successful
     * @param ipAddress the IP address of the client
     * @param details   additional details about the download
     */
    public void logFileDownload(String userId, String fileName, String filePath, boolean success, 
                               String ipAddress, String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEntry entry = new AuditEntry(
            userId,
            "FILE_DOWNLOAD",
            filePath,
            success,
            details,
            ipAddress
        );
        
        writeAuditEntry(entry);
        
        if (success) {
            logger.info("File download successful - User: {}, File: {}", userId, fileName);
        } else {
            logger.warn("File download failed - User: {}, File: {}", userId, fileName);
        }
    }

    /**
     * Logs file search events.
     *
     * @param userId      the user ID performing the search
     * @param searchTerm  the search term or pattern used
     * @param searchPath  the path where the search was performed
     * @param resultCount the number of results found
     * @param ipAddress   the IP address of the client
     * @param details     additional details about the search
     */
    public void logFileSearch(String userId, String searchTerm, String searchPath, int resultCount, 
                             String ipAddress, String details) {
        if (!auditEnabled) {
            return;
        }
        
        String searchDetails = String.format("Search term: '%s', Results: %d, %s", 
                                            searchTerm, resultCount, details != null ? details : "");
        
        AuditEntry entry = new AuditEntry(
            userId,
            "FILE_SEARCH",
            searchPath,
            true,
            searchDetails,
            ipAddress
        );
        
        writeAuditEntry(entry);
        
        logger.info("File search performed - User: {}, Term: '{}', Path: {}, Results: {}", 
                   userId, searchTerm, searchPath, resultCount);
    }

    /**
     * Logs content search events.
     *
     * @param userId      the user ID performing the content search
     * @param searchTerm  the search term used in content search
     * @param filePath    the file path where content search was performed
     * @param resultCount the number of matches found
     * @param ipAddress   the IP address of the client
     * @param details     additional details about the content search
     */
    public void logContentSearch(String userId, String searchTerm, String filePath, int resultCount, 
                                String ipAddress, String details) {
        if (!auditEnabled) {
            return;
        }
        
        String searchDetails = String.format("Content search term: '%s', Matches: %d, %s", 
                                            searchTerm, resultCount, details != null ? details : "");
        
        AuditEntry entry = new AuditEntry(
            userId,
            "CONTENT_SEARCH",
            filePath,
            true,
            searchDetails,
            ipAddress
        );
        
        writeAuditEntry(entry);
        
        logger.info("Content search performed - User: {}, Term: '{}', File: {}, Matches: {}", 
                   userId, searchTerm, filePath, resultCount);
    }

    /**
     * Logs security events such as access violations or suspicious activities.
     *
     * @param userId    the user ID associated with the security event
     * @param eventType the type of security event
     * @param resource  the resource involved in the security event
     * @param ipAddress the IP address of the client
     * @param details   detailed information about the security event
     */
    public void logSecurityEvent(String userId, String eventType, String resource, 
                                String ipAddress, String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEntry entry = new AuditEntry(
            userId,
            "SECURITY_EVENT_" + eventType,
            resource,
            false,
            details,
            ipAddress
        );
        
        writeAuditEntry(entry);
        
        logger.warn("Security event - User: {}, Type: {}, Resource: {}, Details: {}", 
                   userId, eventType, resource, details);
    }

    /**
     * Logs session events such as logout or session timeout.
     *
     * @param userId    the user ID associated with the session event
     * @param eventType the type of session event (LOGOUT, TIMEOUT, etc.)
     * @param ipAddress the IP address of the client
     * @param details   additional details about the session event
     */
    public void logSessionEvent(String userId, String eventType, String ipAddress, String details) {
        if (!auditEnabled) {
            return;
        }
        
        AuditEntry entry = new AuditEntry(
            userId,
            "SESSION_" + eventType,
            "SESSION",
            true,
            details,
            ipAddress
        );
        
        writeAuditEntry(entry);
        
        logger.info("Session event - User: {}, Type: {}, Details: {}", userId, eventType, details);
    }

    /**
     * Writes an audit entry to the log file in a thread-safe manner.
     *
     * @param entry the audit entry to write
     */
    private void writeAuditEntry(AuditEntry entry) {
        if (!auditEnabled || auditLogPath == null) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            String logLine = formatAuditEntry(entry);
            
            try (BufferedWriter writer = Files.newBufferedWriter(auditLogPath, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(logLine);
                writer.newLine();
                writer.flush();
            }
            
        } catch (IOException e) {
            logger.error("Failed to write audit entry to log file: {}", auditLogPath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Formats an audit entry for logging.
     *
     * @param entry the audit entry to format
     * @return the formatted log line
     */
    private String formatAuditEntry(AuditEntry entry) {
        try {
            // Create a structured log entry with timestamp, operation, user, and details
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append(entry.getTimestamp().format(TIMESTAMP_FORMATTER));
            logBuilder.append(" | ");
            logBuilder.append(entry.getOperation());
            logBuilder.append(" | ");
            logBuilder.append("User: ").append(entry.getUserId() != null ? entry.getUserId() : "UNKNOWN");
            logBuilder.append(" | ");
            logBuilder.append("Resource: ").append(entry.getResource() != null ? entry.getResource() : "N/A");
            logBuilder.append(" | ");
            logBuilder.append("Success: ").append(entry.isSuccess());
            logBuilder.append(" | ");
            logBuilder.append("IP: ").append(entry.getIpAddress() != null ? entry.getIpAddress() : "UNKNOWN");
            
            if (entry.getDetails() != null && !entry.getDetails().trim().isEmpty()) {
                logBuilder.append(" | ");
                logBuilder.append("Details: ").append(entry.getDetails());
            }
            
            return logBuilder.toString();
            
        } catch (Exception e) {
            logger.error("Failed to format audit entry", e);
            return String.format("%s | ERROR | Failed to format audit entry: %s", 
                               LocalDateTime.now().format(TIMESTAMP_FORMATTER), e.getMessage());
        }
    }

    /**
     * Writes the audit log header when creating a new log file.
     */
    private void writeAuditHeader() throws IOException {
        if (auditLogPath == null) {
            return;
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(auditLogPath, 
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            writer.write("# Archive Search API Audit Log");
            writer.newLine();
            writer.write("# Format: TIMESTAMP | OPERATION | User: USER_ID | Resource: RESOURCE | Success: BOOLEAN | IP: IP_ADDRESS | Details: DETAILS");
            writer.newLine();
            writer.write("# Started: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            writer.newLine();
            writer.write("# ================================================================================");
            writer.newLine();
            writer.flush();
        }
    }

    /**
     * Gets the current audit log file path.
     *
     * @return the audit log file path
     */
    public String getAuditLogFile() {
        return auditLogFile;
    }

    /**
     * Checks if audit logging is enabled.
     *
     * @return true if audit logging is enabled, false otherwise
     */
    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    /**
     * Gets the number of lines in the audit log file.
     * This method is primarily for testing purposes.
     *
     * @return the number of lines in the audit log file
     * @throws IOException if there's an error reading the file
     */
    public long getAuditLogLineCount() throws IOException {
        if (!auditEnabled || auditLogPath == null || !Files.exists(auditLogPath)) {
            return 0;
        }
        
        lock.readLock().lock();
        try {
            return Files.lines(auditLogPath).count();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Log file upload request
     */
    public void logFileUploadRequest(String userId, String fileName, String ipAddress, String targetPath, String fileSize) {
        String details = String.format("Upload request - File: %s, Size: %s", fileName, fileSize);
        logFileUpload(userId, fileName, targetPath, true, ipAddress, details);
    }
    
    /**
     * Log successful file upload
     */
    public void logFileUploadSuccess(String userId, String fileName, String targetPath, long fileSize, long duration) {
        String details = String.format("Upload successful - Size: %d bytes, Duration: %d ms", fileSize, duration);
        logFileUpload(userId, fileName, targetPath, true, "unknown", details);
    }
    
    /**
     * Log failed file upload
     */
    public void logFileUploadFailure(String userId, String fileName, String ipAddress, String reason, String targetPath, long fileSize) {
        String details = String.format("Upload failed - Reason: %s, Size: %d bytes", reason, fileSize);
        logFileUpload(userId, fileName, targetPath, false, ipAddress, details);
    }
}