package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.model.UploadRequest;
import com.fabric.watcher.archive.model.UploadResponse;
import com.fabric.watcher.archive.security.SecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileUploadService.
 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock
    private ArchiveSearchProperties properties;

    @Mock
    private ArchiveSearchProperties.UploadConfig uploadConfig;

    @Mock
    private SecurityValidator securityValidator;

    @Mock
    private ArchiveSearchAuditService auditService;

    @Mock
    private MultipartFile multipartFile;

    @TempDir
    Path tempDir;

    private FileUploadService fileUploadService;

    private static final String TEST_USER_ID = "testuser";
    private static final String TEST_SESSION_ID = "session123";
    private static final String TEST_USER_AGENT = "TestAgent/1.0";
    private static final String TEST_REMOTE_ADDR = "192.168.1.100";

    @BeforeEach
    void setUp() {
        when(properties.getUpload()).thenReturn(uploadConfig);
        
        // Default upload config setup
        when(uploadConfig.getMaxUploadSize()).thenReturn(100L * 1024 * 1024); // 100MB
        when(uploadConfig.getAllowedExtensions()).thenReturn(Arrays.asList(".txt", ".sql", ".xml", ".json"));
        when(uploadConfig.getUploadDirectory()).thenReturn(tempDir.toString());
        when(uploadConfig.isCreateDirectories()).thenReturn(true);
        when(uploadConfig.isPreserveTimestamps()).thenReturn(true);
        when(uploadConfig.getMaxConcurrentUploads()).thenReturn(3);

        fileUploadService = new FileUploadService(properties, securityValidator, auditService);
    }

    @Test
    void testSuccessfulFileUpload() throws IOException {
        // Arrange
        String fileName = "test.txt";
        String targetPath = tempDir.resolve("uploads/test.txt").toString();
        String fileContent = "Test file content";
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn((long) fileContent.length());
        when(multipartFile.isEmpty()).thenReturn(false);
        doNothing().when(multipartFile).transferTo(any(java.io.File.class));
        
        when(securityValidator.sanitizePath(anyString())).thenReturn(targetPath);
        when(securityValidator.isPathAllowed(anyString())).thenReturn(true);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, targetPath);

        // Act
        UploadResponse response = fileUploadService.uploadFile(
            uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(fileName, response.getFileName());
        assertEquals(targetPath, response.getTargetPath());
        assertEquals(fileContent.length(), response.getFileSize());
        assertEquals(TEST_USER_ID, response.getUploadedBy());
        assertNotNull(response.getUploadedAt());

        // Verify audit logging
        verify(auditService).logFileUploadRequest(TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR, fileName, targetPath);
        verify(auditService).logFileUploadSuccess(eq(TEST_SESSION_ID), eq(fileName), eq(targetPath), 
                                                eq((long) fileContent.length()), anyLong());
    }

    @Test
    void testUploadWithNullRequest() {
        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(null, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH015", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("Upload request cannot be null"));
    }

    @Test
    void testUploadWithNullFile() {
        // Arrange
        UploadRequest uploadRequest = new UploadRequest(null, "/test/path");

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH015", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("File cannot be null"));
    }

    @Test
    void testUploadWithEmptyFile() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(true);
        UploadRequest uploadRequest = new UploadRequest(multipartFile, "/test/path");

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH015", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("File cannot be empty"));
    }

    @Test
    void testUploadWithNullTargetPath() {
        // Arrange
        when(multipartFile.isEmpty()).thenReturn(false);
        UploadRequest uploadRequest = new UploadRequest(multipartFile, null);

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH015", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("Target path cannot be null or empty"));
    }

    @Test
    void testUploadWithFileTooLarge() {
        // Arrange
        String fileName = "large.txt";
        long largeFileSize = 200L * 1024 * 1024; // 200MB, exceeds 100MB limit
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn(largeFileSize);
        when(multipartFile.isEmpty()).thenReturn(false);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, "/test/large.txt");

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH018", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("File size"));
        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));

        // Verify audit logging for failure
        verify(auditService).logFileUploadFailure(eq(TEST_SESSION_ID), eq(fileName), eq("/test/large.txt"), 
                                                eq("ARCH018"), anyString(), anyLong());
    }

    @Test
    void testUploadWithInvalidFileType() {
        // Arrange
        String fileName = "test.exe"; // Not in allowed extensions
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.isEmpty()).thenReturn(false);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, "/test/test.exe");

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH019", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("File type not allowed"));
    }

    @Test
    void testUploadWithNullFileName() {
        // Arrange
        when(multipartFile.getOriginalFilename()).thenReturn(null);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.isEmpty()).thenReturn(false);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, "/test/path");

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH019", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("File name cannot be null or empty"));
    }

    @Test
    void testUploadWithMaliciousPath() {
        // Arrange
        String fileName = "test.txt";
        String maliciousPath = "../../../etc/passwd";
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.isEmpty()).thenReturn(false);
        
        when(securityValidator.sanitizePath(maliciousPath)).thenReturn(null); // Indicates malicious path
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, maliciousPath);

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH001", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("contains malicious patterns"));
    }

    @Test
    void testUploadWithPathNotAllowed() {
        // Arrange
        String fileName = "test.txt";
        String targetPath = "/forbidden/path/test.txt";
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.isEmpty()).thenReturn(false);
        
        when(securityValidator.sanitizePath(targetPath)).thenReturn(targetPath);
        when(securityValidator.isPathAllowed(targetPath)).thenReturn(false);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, targetPath);

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH001", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("not within allowed directories"));
    }

    @Test
    void testUploadWithPathOutsideUploadDirectory() {
        // Arrange
        String fileName = "test.txt";
        String targetPath = "/outside/upload/dir/test.txt";
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.isEmpty()).thenReturn(false);
        
        when(securityValidator.sanitizePath(targetPath)).thenReturn(targetPath);
        when(securityValidator.isPathAllowed(targetPath)).thenReturn(true);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, targetPath);

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH001", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("must be within the configured upload directory"));
    }

    @Test
    void testUploadWithExistingFileAndOverwriteDisabled() throws IOException {
        // Arrange
        String fileName = "existing.txt";
        Path targetPath = tempDir.resolve("existing.txt");
        Files.createFile(targetPath); // Create existing file
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.isEmpty()).thenReturn(false);
        
        when(securityValidator.sanitizePath(anyString())).thenReturn(targetPath.toString());
        when(securityValidator.isPathAllowed(anyString())).thenReturn(true);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, targetPath.toString(), false, null);

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH020", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("File already exists and overwrite is not enabled"));
    }

    @Test
    void testUploadWithExistingFileAndOverwriteEnabled() throws IOException {
        // Arrange
        String fileName = "existing.txt";
        Path targetPath = tempDir.resolve("existing.txt");
        Files.createFile(targetPath); // Create existing file
        String fileContent = "New content";
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn((long) fileContent.length());
        when(multipartFile.isEmpty()).thenReturn(false);
        doNothing().when(multipartFile).transferTo(any(java.io.File.class));
        
        when(securityValidator.sanitizePath(anyString())).thenReturn(targetPath.toString());
        when(securityValidator.isPathAllowed(anyString())).thenReturn(true);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, targetPath.toString(), true, null);

        // Act
        UploadResponse response = fileUploadService.uploadFile(
            uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(fileName, response.getFileName());
    }

    @Test
    void testUploadWithDirectoryCreationDisabled() {
        // Arrange
        String fileName = "test.txt";
        Path nonExistentDir = tempDir.resolve("nonexistent");
        Path targetPath = nonExistentDir.resolve("test.txt");
        
        when(uploadConfig.isCreateDirectories()).thenReturn(false);
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.isEmpty()).thenReturn(false);
        
        when(securityValidator.sanitizePath(anyString())).thenReturn(targetPath.toString());
        when(securityValidator.isPathAllowed(anyString())).thenReturn(true);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, targetPath.toString());

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH021", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("Target directory does not exist and directory creation is disabled"));
    }

    @Test
    void testUploadWithIOException() throws IOException {
        // Arrange
        String fileName = "test.txt";
        String targetPath = tempDir.resolve("test.txt").toString();
        
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.isEmpty()).thenReturn(false);
        doThrow(new IOException("Transfer failed")).when(multipartFile).transferTo(any(java.io.File.class));
        
        when(securityValidator.sanitizePath(anyString())).thenReturn(targetPath);
        when(securityValidator.isPathAllowed(anyString())).thenReturn(true);
        
        UploadRequest uploadRequest = new UploadRequest(multipartFile, targetPath);

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, () ->
            fileUploadService.uploadFile(uploadRequest, TEST_USER_ID, TEST_SESSION_ID, TEST_USER_AGENT, TEST_REMOTE_ADDR));

        assertEquals("ARCH017", exception.getErrorCode().getCode());
        assertTrue(exception.getMessage().contains("Failed to transfer file"));
    }

    @Test
    void testValidateUploadPath() {
        // Arrange
        String validPath = "/valid/path";
        String invalidPath = "/invalid/path";
        
        when(securityValidator.sanitizePath(validPath)).thenReturn(validPath);
        when(securityValidator.isPathAllowed(validPath)).thenReturn(true);
        when(uploadConfig.getUploadDirectory()).thenReturn("/valid");
        
        when(securityValidator.sanitizePath(invalidPath)).thenReturn(null);

        // Act & Assert
        assertTrue(fileUploadService.validateUploadPath(validPath));
        assertFalse(fileUploadService.validateUploadPath(invalidPath));
    }

    @Test
    void testValidateFileType() {
        // Act & Assert
        assertTrue(fileUploadService.validateFileType("test.txt"));
        assertTrue(fileUploadService.validateFileType("script.sql"));
        assertTrue(fileUploadService.validateFileType("config.xml"));
        assertTrue(fileUploadService.validateFileType("data.json"));
        
        assertFalse(fileUploadService.validateFileType("malware.exe"));
        assertFalse(fileUploadService.validateFileType("script.sh"));
        assertFalse(fileUploadService.validateFileType(null));
    }

    @Test
    void testValidateFileTypeWithEmptyAllowedExtensions() {
        // Arrange
        when(uploadConfig.getAllowedExtensions()).thenReturn(Arrays.asList());

        // Act & Assert - Should allow all files when no restrictions configured
        assertTrue(fileUploadService.validateFileType("any.file"));
        assertTrue(fileUploadService.validateFileType("test.exe"));
    }

    @Test
    void testValidateFileTypeWithNullAllowedExtensions() {
        // Arrange
        when(uploadConfig.getAllowedExtensions()).thenReturn(null);

        // Act & Assert - Should allow all files when no restrictions configured
        assertTrue(fileUploadService.validateFileType("any.file"));
        assertTrue(fileUploadService.validateFileType("test.exe"));
    }

    @Test
    void testCaseInsensitiveFileTypeValidation() {
        // Act & Assert
        assertTrue(fileUploadService.validateFileType("TEST.TXT"));
        assertTrue(fileUploadService.validateFileType("Script.SQL"));
        assertTrue(fileUploadService.validateFileType("CONFIG.XML"));
        
        assertFalse(fileUploadService.validateFileType("MALWARE.EXE"));
    }
}