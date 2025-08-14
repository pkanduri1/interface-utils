package com.fabric.watcher.service;

import com.fabric.watcher.config.WatchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileManagerTest {
    
    private FileManager fileManager;
    private WatchConfig testConfig;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        fileManager = new FileManager();
        
        // Create test configuration with temporary directories
        testConfig = new WatchConfig();
        testConfig.setName("test-config");
        testConfig.setProcessorType("test-processor");
        testConfig.setWatchFolder(tempDir.resolve("watch"));
        testConfig.setCompletedFolder(tempDir.resolve("completed"));
        testConfig.setErrorFolder(tempDir.resolve("error"));
        testConfig.setFilePatterns(List.of("*.sql", "*.txt"));
        testConfig.setPollingInterval(5000);
        testConfig.setEnabled(true);
    }
    
    @Test
    void testEnsureDirectoriesExist() throws IOException {
        // Initially directories don't exist
        assertFalse(Files.exists(testConfig.getWatchFolder()));
        assertFalse(Files.exists(testConfig.getCompletedFolder()));
        assertFalse(Files.exists(testConfig.getErrorFolder()));
        
        // Call ensureDirectoriesExist
        fileManager.ensureDirectoriesExist(testConfig);
        
        // Verify directories are created
        assertTrue(Files.exists(testConfig.getWatchFolder()));
        assertTrue(Files.exists(testConfig.getCompletedFolder()));
        assertTrue(Files.exists(testConfig.getErrorFolder()));
        assertTrue(Files.isDirectory(testConfig.getWatchFolder()));
        assertTrue(Files.isDirectory(testConfig.getCompletedFolder()));
        assertTrue(Files.isDirectory(testConfig.getErrorFolder()));
    }
    
    @Test
    void testMoveToCompleted() throws IOException {
        // Setup
        fileManager.ensureDirectoriesExist(testConfig);
        Path testFile = testConfig.getWatchFolder().resolve("test.sql");
        Files.createFile(testFile);
        Files.write(testFile, "SELECT 1;".getBytes());
        
        // Execute
        Path movedFile = fileManager.moveToCompleted(testFile, testConfig);
        
        // Verify
        assertFalse(Files.exists(testFile)); // Original file should be gone
        assertTrue(Files.exists(movedFile)); // Moved file should exist
        assertTrue(movedFile.getParent().equals(testConfig.getCompletedFolder()));
        
        // Verify filename format: test_YYYYMMDD_HHMMSS.sql
        String fileName = movedFile.getFileName().toString();
        assertTrue(fileName.startsWith("test_"));
        assertTrue(fileName.endsWith(".sql"));
        assertTrue(fileName.matches("test_\\d{8}_\\d{6}\\.sql"));
        
        // Verify content is preserved
        String content = Files.readString(movedFile);
        assertEquals("SELECT 1;", content);
    }
    
    @Test
    void testMoveToCompletedWithoutExtension() throws IOException {
        // Setup
        fileManager.ensureDirectoriesExist(testConfig);
        Path testFile = testConfig.getWatchFolder().resolve("testfile");
        Files.createFile(testFile);
        
        // Execute
        Path movedFile = fileManager.moveToCompleted(testFile, testConfig);
        
        // Verify
        String fileName = movedFile.getFileName().toString();
        assertTrue(fileName.startsWith("testfile_"));
        assertTrue(fileName.matches("testfile_\\d{8}_\\d{6}"));
        assertFalse(fileName.contains("."));
    }
    
    @Test
    void testMoveToError() throws IOException {
        // Setup
        fileManager.ensureDirectoriesExist(testConfig);
        Path testFile = testConfig.getWatchFolder().resolve("bad.sql");
        Files.createFile(testFile);
        Files.write(testFile, "INVALID SQL;".getBytes());
        
        String errorDetails = "Syntax error at line 1";
        
        // Execute
        Path movedFile = fileManager.moveToError(testFile, errorDetails, testConfig);
        
        // Verify
        assertFalse(Files.exists(testFile)); // Original file should be gone
        assertTrue(Files.exists(movedFile)); // Moved file should exist
        assertTrue(movedFile.getParent().equals(testConfig.getErrorFolder()));
        
        // Verify filename format: bad_ERROR_YYYYMMDD_HHMMSS_sanitized_error.sql
        String fileName = movedFile.getFileName().toString();
        assertTrue(fileName.startsWith("bad_ERROR_"));
        assertTrue(fileName.endsWith(".sql"));
        assertTrue(fileName.contains("Syntax_error_at_line_1"));
        
        // Verify content is preserved
        String content = Files.readString(movedFile);
        assertEquals("INVALID SQL;", content);
    }
    
    @Test
    void testMoveToErrorWithSpecialCharacters() throws IOException {
        // Setup
        fileManager.ensureDirectoriesExist(testConfig);
        Path testFile = testConfig.getWatchFolder().resolve("test.sql");
        Files.createFile(testFile);
        
        String errorDetails = "Error: Invalid character '@#$%' found!";
        
        // Execute
        Path movedFile = fileManager.moveToError(testFile, errorDetails, testConfig);
        
        // Verify error details are sanitized
        String fileName = movedFile.getFileName().toString();
        assertTrue(fileName.contains("Error_Invalid_character_found_"));
        assertFalse(fileName.contains("@#$%"));
    }
    
    @Test
    void testMoveToErrorWithLongErrorMessage() throws IOException {
        // Setup
        fileManager.ensureDirectoriesExist(testConfig);
        Path testFile = testConfig.getWatchFolder().resolve("test.sql");
        Files.createFile(testFile);
        
        String longError = "This is a very long error message that exceeds the maximum length allowed for filenames and should be truncated";
        
        // Execute
        Path movedFile = fileManager.moveToError(testFile, longError, testConfig);
        
        // Verify error message is truncated
        String fileName = movedFile.getFileName().toString();
        String[] parts = fileName.split("_ERROR_\\d{8}_\\d{6}_");
        assertTrue(parts.length > 1);
        String errorPart = parts[1].replace(".sql", "");
        assertTrue(errorPart.length() <= 50);
    }
    
    @Test
    void testMoveToErrorWithNullErrorDetails() throws IOException {
        // Setup
        fileManager.ensureDirectoriesExist(testConfig);
        Path testFile = testConfig.getWatchFolder().resolve("test.sql");
        Files.createFile(testFile);
        
        // Execute
        Path movedFile = fileManager.moveToError(testFile, null, testConfig);
        
        // Verify
        String fileName = movedFile.getFileName().toString();
        assertTrue(fileName.contains("UNKNOWN"));
    }
    
    @Test
    void testCopyFile() throws IOException {
        // Setup
        Path sourceFile = tempDir.resolve("source.txt");
        Path destFile = tempDir.resolve("subdir").resolve("dest.txt");
        Files.createFile(sourceFile);
        Files.write(sourceFile, "test content".getBytes());
        
        // Execute
        Path copiedFile = fileManager.copyFile(sourceFile, destFile);
        
        // Verify
        assertTrue(Files.exists(sourceFile)); // Original should still exist
        assertTrue(Files.exists(copiedFile)); // Copy should exist
        assertEquals(destFile, copiedFile);
        
        // Verify content
        String originalContent = Files.readString(sourceFile);
        String copiedContent = Files.readString(copiedFile);
        assertEquals(originalContent, copiedContent);
    }
    
    @Test
    void testDeleteFile() throws IOException {
        // Setup
        Path testFile = tempDir.resolve("delete-me.txt");
        Files.createFile(testFile);
        assertTrue(Files.exists(testFile));
        
        // Execute
        boolean deleted = fileManager.deleteFile(testFile);
        
        // Verify
        assertTrue(deleted);
        assertFalse(Files.exists(testFile));
    }
    
    @Test
    void testDeleteNonExistentFile() throws IOException {
        // Setup
        Path nonExistentFile = tempDir.resolve("does-not-exist.txt");
        assertFalse(Files.exists(nonExistentFile));
        
        // Execute
        boolean deleted = fileManager.deleteFile(nonExistentFile);
        
        // Verify
        assertFalse(deleted); // Should return false for non-existent file
    }
    
    @Test
    void testValidateDirectoryAccess() throws IOException {
        // Setup - create directories
        fileManager.ensureDirectoriesExist(testConfig);
        
        // Execute - should not throw exception
        assertDoesNotThrow(() -> fileManager.validateDirectoryAccess(testConfig));
    }
    
    @Test
    void testValidateDirectoryAccessWithNonExistentDirectory() {
        // Setup - don't create directories
        WatchConfig invalidConfig = new WatchConfig();
        invalidConfig.setWatchFolder(tempDir.resolve("nonexistent"));
        invalidConfig.setCompletedFolder(tempDir.resolve("nonexistent2"));
        invalidConfig.setErrorFolder(tempDir.resolve("nonexistent3"));
        
        // Execute and verify exception
        IOException exception = assertThrows(IOException.class, 
            () -> fileManager.validateDirectoryAccess(invalidConfig));
        assertTrue(exception.getMessage().contains("does not exist"));
    }
    
    @Test
    void testValidateDirectoryAccessWithFile() throws IOException {
        // Setup - create a file instead of directory
        Path fileInsteadOfDir = tempDir.resolve("file-not-dir.txt");
        Files.createFile(fileInsteadOfDir);
        
        WatchConfig invalidConfig = new WatchConfig();
        invalidConfig.setWatchFolder(fileInsteadOfDir);
        invalidConfig.setCompletedFolder(tempDir.resolve("completed"));
        invalidConfig.setErrorFolder(tempDir.resolve("error"));
        
        // Execute and verify exception
        IOException exception = assertThrows(IOException.class, 
            () -> fileManager.validateDirectoryAccess(invalidConfig));
        assertTrue(exception.getMessage().contains("is not a directory"));
    }
    
    @Test
    void testIsFileInUse() {
        // Test files that should be considered in use
        assertTrue(fileManager.isFileInUse(Paths.get("test.tmp")));
        assertTrue(fileManager.isFileInUse(Paths.get("test.processing")));
        assertTrue(fileManager.isFileInUse(Paths.get("test.TMP"))); // Case insensitive
        assertTrue(fileManager.isFileInUse(Paths.get("test.PROCESSING")));
        
        // Test files that should not be considered in use
        assertFalse(fileManager.isFileInUse(Paths.get("test.sql")));
        assertFalse(fileManager.isFileInUse(Paths.get("test.txt")));
        assertFalse(fileManager.isFileInUse(Paths.get("test")));
    }
    
    @Test
    void testGetFileSize() throws IOException {
        // Setup
        Path testFile = tempDir.resolve("size-test.txt");
        String content = "Hello, World!";
        Files.createFile(testFile);
        Files.write(testFile, content.getBytes());
        
        // Execute
        long size = fileManager.getFileSize(testFile);
        
        // Verify
        assertEquals(content.getBytes().length, size);
    }
    
    @Test
    void testGetFileSizeNonExistentFile() {
        // Setup
        Path nonExistentFile = tempDir.resolve("does-not-exist.txt");
        
        // Execute and verify exception
        assertThrows(IOException.class, () -> fileManager.getFileSize(nonExistentFile));
    }
    
    @Test
    void testIsFileReadable() throws IOException {
        // Setup
        Path readableFile = tempDir.resolve("readable.txt");
        Files.createFile(readableFile);
        
        Path nonExistentFile = tempDir.resolve("does-not-exist.txt");
        
        // Execute and verify
        assertTrue(fileManager.isFileReadable(readableFile));
        assertFalse(fileManager.isFileReadable(nonExistentFile));
    }
    
    @Test
    void testMoveToCompletedCreatesDirectoriesIfNeeded() throws IOException {
        // Setup - don't create directories first
        Path testFile = Files.createTempFile(tempDir, "test", ".sql");
        Files.write(testFile, "SELECT 1;".getBytes());
        
        // Execute - should create directories automatically
        Path movedFile = fileManager.moveToCompleted(testFile, testConfig);
        
        // Verify
        assertTrue(Files.exists(testConfig.getCompletedFolder()));
        assertTrue(Files.exists(movedFile));
    }
    
    @Test
    void testMoveToErrorCreatesDirectoriesIfNeeded() throws IOException {
        // Setup - don't create directories first
        Path testFile = Files.createTempFile(tempDir, "test", ".sql");
        Files.write(testFile, "INVALID SQL;".getBytes());
        
        // Execute - should create directories automatically
        Path movedFile = fileManager.moveToError(testFile, "Test error", testConfig);
        
        // Verify
        assertTrue(Files.exists(testConfig.getErrorFolder()));
        assertTrue(Files.exists(movedFile));
    }
}