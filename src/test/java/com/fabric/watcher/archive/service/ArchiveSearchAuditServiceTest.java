package com.fabric.watcher.archive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ArchiveSearchAuditServiceTest {

    private ArchiveSearchAuditService auditService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditService = new ArchiveSearchAuditService(objectMapper);
    }

    @Test
    void shouldGenerateSessionId() {
        // When
        String sessionId1 = auditService.generateSessionId();
        String sessionId2 = auditService.generateSessionId();

        // Then
        assertThat(sessionId1).isNotNull();
        assertThat(sessionId2).isNotNull();
        assertThat(sessionId1).isNotEqualTo(sessionId2);
        assertThat(sessionId1).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldLogFileSearchRequest() {
        // Given
        String sessionId = "test-session-123";
        String userAgent = "Mozilla/5.0 (Test Browser)";
        String remoteAddr = "192.168.1.100";
        String searchPath = "/data/archives";
        String pattern = "*.txt";

        // When - This should not throw any exceptions
        auditService.logFileSearchRequest(sessionId, userAgent, remoteAddr, searchPath, pattern);

        // Then - No exception means success
        // In a real scenario, we would verify the log output, but that's complex to test
        // The important thing is that the method executes without error
    }

    @Test
    void shouldLogFileSearchSuccess() {
        // Given
        String sessionId = "test-session-123";
        String searchPath = "/data/archives";
        String pattern = "*.txt";
        int filesFound = 5;
        long durationMs = 150;

        // When
        auditService.logFileSearchSuccess(sessionId, searchPath, pattern, filesFound, durationMs);

        // Then - No exception means success
    }

    @Test
    void shouldLogFileSearchFailure() {
        // Given
        String sessionId = "test-session-123";
        String searchPath = "/data/archives";
        String pattern = "*.txt";
        String errorType = "SECURITY_VIOLATION";
        String errorMessage = "Access denied to path";
        long durationMs = 50;

        // When
        auditService.logFileSearchFailure(sessionId, searchPath, pattern, errorType, errorMessage, durationMs);

        // Then - No exception means success
    }

    @Test
    void shouldLogContentSearchRequest() {
        // Given
        String sessionId = "test-session-123";
        String userAgent = "Mozilla/5.0 (Test Browser)";
        String remoteAddr = "192.168.1.100";
        String filePath = "/data/archives/file.txt";
        String searchTerm = "search term";
        boolean caseSensitive = true;

        // When
        auditService.logContentSearchRequest(sessionId, userAgent, remoteAddr, filePath, searchTerm, caseSensitive);

        // Then - No exception means success
    }

    @Test
    void shouldLogContentSearchSuccess() {
        // Given
        String sessionId = "test-session-123";
        String filePath = "/data/archives/file.txt";
        int matchesFound = 10;
        boolean truncated = false;
        long durationMs = 200;

        // When
        auditService.logContentSearchSuccess(sessionId, filePath, matchesFound, truncated, durationMs);

        // Then - No exception means success
    }

    @Test
    void shouldLogDownloadRequest() {
        // Given
        String sessionId = "test-session-123";
        String userAgent = "Mozilla/5.0 (Test Browser)";
        String remoteAddr = "192.168.1.100";
        String filePath = "/data/archives/file.txt";

        // When
        auditService.logDownloadRequest(sessionId, userAgent, remoteAddr, filePath);

        // Then - No exception means success
    }

    @Test
    void shouldLogDownloadSuccess() {
        // Given
        String sessionId = "test-session-123";
        String filePath = "/data/archives/file.txt";
        long fileSize = 1024;
        long durationMs = 300;

        // When
        auditService.logDownloadSuccess(sessionId, filePath, fileSize, durationMs);

        // Then - No exception means success
    }

    @Test
    void shouldLogSecurityViolation() {
        // Given
        String sessionId = "test-session-123";
        String userAgent = "Mozilla/5.0 (Test Browser)";
        String remoteAddr = "192.168.1.100";
        String violationType = "PATH_TRAVERSAL";
        String attemptedPath = "../../../etc/passwd";
        String details = "Path traversal attempt detected";

        // When
        auditService.logSecurityViolation(sessionId, userAgent, remoteAddr, violationType, attemptedPath, details);

        // Then - No exception means success
    }

    @Test
    void shouldLogPathTraversalAttempt() {
        // Given
        String sessionId = "test-session-123";
        String userAgent = "Mozilla/5.0 (Test Browser)";
        String remoteAddr = "192.168.1.100";
        String attemptedPath = "../../../etc/passwd";

        // When
        auditService.logPathTraversalAttempt(sessionId, userAgent, remoteAddr, attemptedPath);

        // Then - No exception means success
    }

    @Test
    void shouldLogUnauthorizedAccess() {
        // Given
        String sessionId = "test-session-123";
        String userAgent = "Mozilla/5.0 (Test Browser)";
        String remoteAddr = "192.168.1.100";
        String attemptedResource = "/restricted/file.txt";
        String reason = "Path not in allowed list";

        // When
        auditService.logUnauthorizedAccess(sessionId, userAgent, remoteAddr, attemptedResource, reason);

        // Then - No exception means success
    }

    @Test
    void shouldLogOperationTimeout() {
        // Given
        String sessionId = "test-session-123";
        String operationType = "FILE_SEARCH";
        String resource = "/data/archives";
        long timeoutMs = 30000;

        // When
        auditService.logOperationTimeout(sessionId, operationType, resource, timeoutMs);

        // Then - No exception means success
    }

    @Test
    void shouldLogArchiveProcessing() {
        // Given
        String sessionId = "test-session-123";
        String archivePath = "/data/archives/archive.zip";
        String archiveFormat = "zip";
        int entriesProcessed = 25;
        long durationMs = 500;

        // When
        auditService.logArchiveProcessing(sessionId, archivePath, archiveFormat, entriesProcessed, durationMs);

        // Then - No exception means success
    }

    @Test
    void shouldLogApiAccess() {
        // Given
        String sessionId = "test-session-123";
        String userAgent = "Mozilla/5.0 (Test Browser)";
        String remoteAddr = "192.168.1.100";
        String endpoint = "/api/v1/archive/search";
        String method = "GET";
        int responseStatus = 200;

        // When
        auditService.logApiAccess(sessionId, userAgent, remoteAddr, endpoint, method, responseStatus);

        // Then - No exception means success
    }

    @Test
    void shouldHandleNullValues() {
        // Given
        String sessionId = "test-session-123";

        // When - These should not throw exceptions even with null values
        auditService.logFileSearchRequest(sessionId, null, null, null, null);
        auditService.logContentSearchRequest(sessionId, null, null, null, null, false);
        auditService.logDownloadRequest(sessionId, null, null, null);
        auditService.logSecurityViolation(sessionId, null, null, "VIOLATION", null, null);

        // Then - No exception means success
    }

    @Test
    void shouldHandleLongStrings() {
        // Given
        String sessionId = "test-session-123";
        String longPath = "a".repeat(2000); // Very long path
        String longUserAgent = "b".repeat(500); // Very long user agent
        String longErrorMessage = "c".repeat(2000); // Very long error message

        // When - These should handle long strings gracefully
        auditService.logFileSearchRequest(sessionId, longUserAgent, "127.0.0.1", longPath, "*.txt");
        auditService.logFileSearchFailure(sessionId, longPath, "*.txt", "ERROR", longErrorMessage, 100);

        // Then - No exception means success
    }

    @Test
    void shouldHandleSpecialCharacters() {
        // Given
        String sessionId = "test-session-123";
        String pathWithSpecialChars = "/path/with\nnewlines\tand\rcarriage\u0000returns";
        String userAgentWithSpecialChars = "Browser\nwith\tspecial\rchars";
        String errorWithSpecialChars = "Error\nmessage\twith\rspecial\u0001chars";

        // When - These should sanitize special characters
        auditService.logFileSearchRequest(sessionId, userAgentWithSpecialChars, "127.0.0.1", pathWithSpecialChars, "*.txt");
        auditService.logFileSearchFailure(sessionId, pathWithSpecialChars, "*.txt", "ERROR", errorWithSpecialChars, 100);

        // Then - No exception means success
    }
}