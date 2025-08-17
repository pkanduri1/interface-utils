package com.fabric.watcher.archive.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArchiveSearchAuditService.
 * 
 * <p>This test class focuses on testing the audit service functionality,
 * including thread-safe concurrent logging scenarios.</p>
 */
class ArchiveSearchAuditServiceTest {

    private ArchiveSearchAuditService auditService;
    
    @TempDir
    Path tempDir;
    
    private Path testAuditLogFile;

    @BeforeEach
    void setUp() throws IOException {
        auditService = new ArchiveSearchAuditService();
        testAuditLogFile = tempDir.resolve("test-audit.log");
        
        // Set test configuration
        ReflectionTestUtils.setField(auditService, "auditLogFile", testAuditLogFile.toString());
        ReflectionTestUtils.setField(auditService, "auditEnabled", true);
        
        // Initialize the service
        auditService.initialize();
    }

    @Test
    void testInitialize_CreatesLogFileAndDirectory() throws IOException {
        // Given
        Path newTempDir = tempDir.resolve("new-audit-dir");
        Path newLogFile = newTempDir.resolve("audit.log");
        ArchiveSearchAuditService newService = new ArchiveSearchAuditService();
        
        ReflectionTestUtils.setField(newService, "auditLogFile", newLogFile.toString());
        ReflectionTestUtils.setField(newService, "auditEnabled", true);
        
        // When
        newService.initialize();
        
        // Then
        assertTrue(Files.exists(newTempDir), "Audit directory should be created");
        assertTrue(Files.exists(newLogFile), "Audit log file should be created");
        
        List<String> lines = Files.readAllLines(newLogFile);
        assertTrue(lines.size() >= 4, "Header should be written to new log file");
        assertTrue(lines.get(0).contains("Archive Search API Audit Log"), "Header should contain title");
    }

    @Test
    void testInitialize_DisabledAudit() {
        // Given
        ArchiveSearchAuditService disabledService = new ArchiveSearchAuditService();
        ReflectionTestUtils.setField(disabledService, "auditEnabled", false);
        
        // When/Then - should not throw exception
        assertDoesNotThrow(() -> disabledService.initialize());
        assertFalse(disabledService.isAuditEnabled());
    }

    @Test
    void testLogAuthentication_Success() throws IOException {
        // Given
        String userId = "testuser";
        String ipAddress = "192.168.1.100";
        String details = "Login successful";
        
        // When
        auditService.logAuthentication(userId, true, ipAddress, details);
        
        // Then
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        String lastLine = logLines.get(logLines.size() - 1);
        
        assertAll(
            () -> assertTrue(lastLine.contains("AUTHENTICATION_SUCCESS"), "Should log success operation"),
            () -> assertTrue(lastLine.contains("User: " + userId), "Should log user ID"),
            () -> assertTrue(lastLine.contains("IP: " + ipAddress), "Should log IP address"),
            () -> assertTrue(lastLine.contains("Success: true"), "Should log success status"),
            () -> assertTrue(lastLine.contains("Details: " + details), "Should log details")
        );
    }

    @Test
    void testLogAuthentication_Failure() throws IOException {
        // Given
        String userId = "testuser";
        String ipAddress = "192.168.1.100";
        String details = "Invalid credentials";
        
        // When
        auditService.logAuthentication(userId, false, ipAddress, details);
        
        // Then
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        String lastLine = logLines.get(logLines.size() - 1);
        
        assertAll(
            () -> assertTrue(lastLine.contains("AUTHENTICATION_FAILURE"), "Should log failure operation"),
            () -> assertTrue(lastLine.contains("User: " + userId), "Should log user ID"),
            () -> assertTrue(lastLine.contains("Success: false"), "Should log failure status"),
            () -> assertTrue(lastLine.contains("Details: " + details), "Should log failure details")
        );
    }

    @Test
    void testLogFileUpload_Success() throws IOException {
        // Given
        String userId = "testuser";
        String fileName = "test.txt";
        String targetPath = "/opt/uploads";
        String ipAddress = "192.168.1.100";
        String details = "Upload completed successfully";
        
        // When
        auditService.logFileUpload(userId, fileName, targetPath, true, ipAddress, details);
        
        // Then
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        String lastLine = logLines.get(logLines.size() - 1);
        
        assertAll(
            () -> assertTrue(lastLine.contains("FILE_UPLOAD"), "Should log upload operation"),
            () -> assertTrue(lastLine.contains("User: " + userId), "Should log user ID"),
            () -> assertTrue(lastLine.contains("Resource: " + targetPath + "/" + fileName), "Should log full file path"),
            () -> assertTrue(lastLine.contains("Success: true"), "Should log success status"),
            () -> assertTrue(lastLine.contains("Details: " + details), "Should log details")
        );
    }

    @Test
    void testLogFileDownload() throws IOException {
        // Given
        String userId = "testuser";
        String fileName = "test.txt";
        String filePath = "/data/files/test.txt";
        String ipAddress = "192.168.1.100";
        String details = "Download completed";
        
        // When
        auditService.logFileDownload(userId, fileName, filePath, true, ipAddress, details);
        
        // Then
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        String lastLine = logLines.get(logLines.size() - 1);
        
        assertAll(
            () -> assertTrue(lastLine.contains("FILE_DOWNLOAD"), "Should log download operation"),
            () -> assertTrue(lastLine.contains("User: " + userId), "Should log user ID"),
            () -> assertTrue(lastLine.contains("Resource: " + filePath), "Should log file path"),
            () -> assertTrue(lastLine.contains("Success: true"), "Should log success status")
        );
    }

    @Test
    void testLogFileSearch() throws IOException {
        // Given
        String userId = "testuser";
        String searchTerm = "*.txt";
        String searchPath = "/data/files";
        int resultCount = 5;
        String ipAddress = "192.168.1.100";
        String details = "Pattern search completed";
        
        // When
        auditService.logFileSearch(userId, searchTerm, searchPath, resultCount, ipAddress, details);
        
        // Then
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        String lastLine = logLines.get(logLines.size() - 1);
        
        assertAll(
            () -> assertTrue(lastLine.contains("FILE_SEARCH"), "Should log search operation"),
            () -> assertTrue(lastLine.contains("User: " + userId), "Should log user ID"),
            () -> assertTrue(lastLine.contains("Resource: " + searchPath), "Should log search path"),
            () -> assertTrue(lastLine.contains("Search term: '" + searchTerm + "'"), "Should log search term"),
            () -> assertTrue(lastLine.contains("Results: " + resultCount), "Should log result count")
        );
    }

    @Test
    void testLogContentSearch() throws IOException {
        // Given
        String userId = "testuser";
        String searchTerm = "error";
        String filePath = "/data/logs/app.log";
        int resultCount = 3;
        String ipAddress = "192.168.1.100";
        String details = "Content search in log file";
        
        // When
        auditService.logContentSearch(userId, searchTerm, filePath, resultCount, ipAddress, details);
        
        // Then
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        String lastLine = logLines.get(logLines.size() - 1);
        
        assertAll(
            () -> assertTrue(lastLine.contains("CONTENT_SEARCH"), "Should log content search operation"),
            () -> assertTrue(lastLine.contains("User: " + userId), "Should log user ID"),
            () -> assertTrue(lastLine.contains("Resource: " + filePath), "Should log file path"),
            () -> assertTrue(lastLine.contains("Content search term: '" + searchTerm + "'"), "Should log search term"),
            () -> assertTrue(lastLine.contains("Matches: " + resultCount), "Should log match count")
        );
    }

    @Test
    void testLogSecurityEvent() throws IOException {
        // Given
        String userId = "testuser";
        String eventType = "PATH_TRAVERSAL_ATTEMPT";
        String resource = "/data/../etc/passwd";
        String ipAddress = "192.168.1.100";
        String details = "Attempted to access restricted path";
        
        // When
        auditService.logSecurityEvent(userId, eventType, resource, ipAddress, details);
        
        // Then
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        String lastLine = logLines.get(logLines.size() - 1);
        
        assertAll(
            () -> assertTrue(lastLine.contains("SECURITY_EVENT_" + eventType), "Should log security event with type"),
            () -> assertTrue(lastLine.contains("User: " + userId), "Should log user ID"),
            () -> assertTrue(lastLine.contains("Resource: " + resource), "Should log resource"),
            () -> assertTrue(lastLine.contains("Success: false"), "Should log as unsuccessful"),
            () -> assertTrue(lastLine.contains("Details: " + details), "Should log security details")
        );
    }

    @Test
    void testLogSessionEvent() throws IOException {
        // Given
        String userId = "testuser";
        String eventType = "LOGOUT";
        String ipAddress = "192.168.1.100";
        String details = "User initiated logout";
        
        // When
        auditService.logSessionEvent(userId, eventType, ipAddress, details);
        
        // Then
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        String lastLine = logLines.get(logLines.size() - 1);
        
        assertAll(
            () -> assertTrue(lastLine.contains("SESSION_" + eventType), "Should log session event with type"),
            () -> assertTrue(lastLine.contains("User: " + userId), "Should log user ID"),
            () -> assertTrue(lastLine.contains("Resource: SESSION"), "Should log session resource"),
            () -> assertTrue(lastLine.contains("Success: true"), "Should log as successful"),
            () -> assertTrue(lastLine.contains("Details: " + details), "Should log session details")
        );
    }

    @Test
    void testConcurrentAuditLogging() throws InterruptedException, IOException {
        // Given
        int numberOfThreads = 10;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // When - Execute concurrent audit operations
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String userId = "user" + threadId;
                        String operation = "operation" + j;
                        
                        // Mix different types of audit operations
                        switch (j % 4) {
                            case 0:
                                auditService.logAuthentication(userId, true, "192.168.1." + threadId, "Concurrent auth " + j);
                                break;
                            case 1:
                                auditService.logFileUpload(userId, "file" + j + ".txt", "/uploads", true, "192.168.1." + threadId, "Concurrent upload " + j);
                                break;
                            case 2:
                                auditService.logFileDownload(userId, "file" + j + ".txt", "/downloads/file" + j + ".txt", true, "192.168.1." + threadId, "Concurrent download " + j);
                                break;
                            case 3:
                                auditService.logFileSearch(userId, "*.txt", "/data", j, "192.168.1." + threadId, "Concurrent search " + j);
                                break;
                        }
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
        executor.shutdown();
        
        // Then - Verify all operations were logged
        long expectedOperations = numberOfThreads * operationsPerThread;
        assertEquals(expectedOperations, successCount.get(), "All operations should have been executed");
        
        // Verify log file integrity
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        
        // Count non-header lines (actual audit entries)
        long auditEntries = logLines.stream()
            .filter(line -> !line.startsWith("#") && !line.trim().isEmpty())
            .count();
        
        assertEquals(expectedOperations, auditEntries, "All audit entries should be written to log file");
        
        // Verify no corruption in log entries
        for (String line : logLines) {
            if (!line.startsWith("#") && !line.trim().isEmpty()) {
                assertAll(
                    () -> assertTrue(line.contains(" | "), "Log line should contain separators"),
                    () -> assertTrue(line.contains("User: "), "Log line should contain user information"),
                    () -> assertTrue(line.contains("Success: "), "Log line should contain success status")
                );
            }
        }
    }

    @Test
    void testConcurrentReadWrite() throws InterruptedException, IOException {
        // Given
        int writerThreads = 5;
        int readerThreads = 3;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(writerThreads + readerThreads);
        CountDownLatch latch = new CountDownLatch(writerThreads + readerThreads);
        List<Exception> exceptions = new ArrayList<>();
        
        // When - Execute concurrent read and write operations
        // Start writer threads
        for (int i = 0; i < writerThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        auditService.logAuthentication("writer" + threadId, true, "192.168.1." + threadId, "Write operation " + j);
                        Thread.sleep(1); // Small delay to increase chance of concurrent access
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Start reader threads
        for (int i = 0; i < readerThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        long lineCount = auditService.getAuditLogLineCount();
                        assertTrue(lineCount >= 0, "Line count should be non-negative");
                        Thread.sleep(2); // Small delay to increase chance of concurrent access
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
        executor.shutdown();
        
        // Then - Verify no exceptions occurred during concurrent access
        assertTrue(exceptions.isEmpty(), "No exceptions should occur during concurrent read/write: " + exceptions);
        
        // Verify final state
        long finalLineCount = auditService.getAuditLogLineCount();
        assertTrue(finalLineCount >= writerThreads * operationsPerThread, "All write operations should be recorded");
    }

    @Test
    void testAuditDisabled() throws IOException {
        // Given
        ArchiveSearchAuditService disabledService = new ArchiveSearchAuditService();
        ReflectionTestUtils.setField(disabledService, "auditEnabled", false);
        
        // When
        disabledService.logAuthentication("testuser", true, "192.168.1.100", "Test");
        disabledService.logFileUpload("testuser", "test.txt", "/uploads", true, "192.168.1.100", "Test");
        
        // Then - No log file should be created or written to
        assertFalse(disabledService.isAuditEnabled(), "Audit should be disabled");
        assertEquals(0, disabledService.getAuditLogLineCount(), "No entries should be logged when disabled");
    }

    @Test
    void testNullAndEmptyValues() throws IOException {
        // Given/When - Log entries with null and empty values
        auditService.logAuthentication(null, true, null, null);
        auditService.logFileUpload("", "test.txt", "", true, "", "");
        auditService.logSecurityEvent("testuser", "TEST", null, "192.168.1.100", null);
        
        // Then - Should handle gracefully without throwing exceptions
        List<String> logLines = Files.readAllLines(testAuditLogFile);
        long auditEntries = logLines.stream()
            .filter(line -> !line.startsWith("#") && !line.trim().isEmpty())
            .count();
        
        assertEquals(3, auditEntries, "All entries should be logged even with null/empty values");
        
        // Verify entries contain appropriate defaults
        String lastLine = logLines.get(logLines.size() - 1);
        assertAll(
            () -> assertTrue(lastLine.contains("User: testuser"), "Should handle valid user ID"),
            () -> assertTrue(lastLine.contains("Resource: N/A"), "Should handle null resource"),
            () -> assertTrue(lastLine.contains("IP: 192.168.1.100"), "Should handle valid IP")
        );
    }

    @Test
    void testGetAuditLogLineCount() throws IOException {
        // Given - Initial state
        long initialCount = auditService.getAuditLogLineCount();
        
        // When - Add some audit entries
        auditService.logAuthentication("user1", true, "192.168.1.100", "Test 1");
        auditService.logAuthentication("user2", false, "192.168.1.101", "Test 2");
        auditService.logFileUpload("user3", "file.txt", "/uploads", true, "192.168.1.102", "Test 3");
        
        // Then
        long finalCount = auditService.getAuditLogLineCount();
        assertEquals(initialCount + 3, finalCount, "Line count should increase by number of entries added");
    }

    @Test
    void testAuditLogFileProperty() {
        // Given/When
        String logFile = auditService.getAuditLogFile();
        
        // Then
        assertEquals(testAuditLogFile.toString(), logFile, "Should return configured audit log file path");
    }
}