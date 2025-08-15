package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.security.SecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for ArchiveSearchService.
 */
@ExtendWith(MockitoExtension.class)
class ArchiveSearchServiceTest {

    @Mock
    private ArchiveSearchProperties properties;

    @Mock
    private SecurityValidator securityValidator;

    @Mock
    private FileSystemService fileSystemService;

    @Mock
    private ArchiveHandlerService archiveHandlerService;

    @Mock
    private ContentSearchService contentSearchService;

    private ArchiveSearchService archiveSearchService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Use lenient stubbing to avoid unnecessary stubbing errors
        lenient().when(properties.getSearchTimeoutSeconds()).thenReturn(30);
        lenient().when(properties.getMaxSearchResults()).thenReturn(100);
        lenient().when(properties.getMaxFileSize()).thenReturn(100 * 1024 * 1024L); // 100MB

        archiveSearchService = new ArchiveSearchService(
                properties,
                securityValidator,
                fileSystemService,
                archiveHandlerService,
                contentSearchService
        );
    }

    @Test
    void testSearchFiles_Success() throws Exception {
        // Arrange
        String searchPath = tempDir.toString();
        String pattern = "*.txt";
        
        when(securityValidator.isPathAllowed(searchPath)).thenReturn(true);
        
        FileInfo regularFile = new FileInfo("test.txt", "/path/test.txt", "test.txt", 
                1024L, LocalDateTime.now());
        FileInfo archiveFile = new FileInfo("config.txt", "app.zip/config.txt", "config.txt", 
                512L, LocalDateTime.now(), "app.zip");
        
        when(fileSystemService.scanDirectory(any(Path.class), eq(pattern)))
                .thenReturn(Arrays.asList(regularFile));
        
        // Mock archive search
        FileInfo archiveEntry = new FileInfo("archive.zip", "/path/archive.zip", "archive.zip", 
                2048L, LocalDateTime.now());
        when(fileSystemService.scanDirectory(any(Path.class), eq("*")))
                .thenReturn(Arrays.asList(archiveEntry));
        when(archiveHandlerService.isArchiveFile(any(Path.class))).thenReturn(true);
        when(securityValidator.isFileAccessible(any(Path.class))).thenReturn(true);
        when(archiveHandlerService.listArchiveContents(any(Path.class), eq(pattern)))
                .thenReturn(Arrays.asList(archiveFile));

        // Act
        FileSearchResponse response = archiveSearchService.searchFiles(searchPath, pattern);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getTotalCount());
        assertEquals(searchPath, response.getSearchPath());
        assertEquals(pattern, response.getSearchPattern());
        assertTrue(response.getSearchTimeMs() >= 0);
        
        List<FileInfo> files = response.getFiles();
        assertEquals(2, files.size());
        assertTrue(files.contains(regularFile));
        assertTrue(files.contains(archiveFile));
        
        verify(securityValidator).isPathAllowed(searchPath);
        verify(fileSystemService).scanDirectory(any(Path.class), eq(pattern));
        verify(archiveHandlerService).listArchiveContents(any(Path.class), eq(pattern));
    }

    @Test
    void testSearchFiles_PathNotAllowed() {
        // Arrange
        String searchPath = "/forbidden/path";
        String pattern = "*.txt";
        
        when(securityValidator.isPathAllowed(searchPath)).thenReturn(false);

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            archiveSearchService.searchFiles(searchPath, pattern);
        });
        
        assertEquals("Access denied to path: " + searchPath, exception.getMessage());
        verify(securityValidator).isPathAllowed(searchPath);
        verifyNoInteractions(fileSystemService);
    }

    @Test
    void testSearchFiles_NullPath() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            archiveSearchService.searchFiles(null, "*.txt");
        });
        
        assertEquals("Search path cannot be null or empty", exception.getMessage());
    }

    @Test
    void testSearchFiles_EmptyPattern() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            archiveSearchService.searchFiles("/path", "");
        });
        
        assertEquals("Search pattern cannot be null or empty", exception.getMessage());
    }

    @Test
    void testSearchFiles_NonExistentPath() throws Exception {
        // Arrange
        String searchPath = "/nonexistent/path";
        String pattern = "*.txt";
        
        when(securityValidator.isPathAllowed(searchPath)).thenReturn(true);

        // Act
        FileSearchResponse response = archiveSearchService.searchFiles(searchPath, pattern);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertTrue(response.getFiles().isEmpty());
        assertEquals(searchPath, response.getSearchPath());
        assertEquals(pattern, response.getSearchPattern());
    }

    @Test
    void testSearchFiles_LimitResults() throws Exception {
        // Arrange
        String searchPath = tempDir.toString();
        String pattern = "*.txt";
        
        when(properties.getMaxSearchResults()).thenReturn(2);
        when(securityValidator.isPathAllowed(searchPath)).thenReturn(true);
        
        FileInfo file1 = new FileInfo("test1.txt", "/path/test1.txt", "test1.txt", 
                1024L, LocalDateTime.now());
        FileInfo file2 = new FileInfo("test2.txt", "/path/test2.txt", "test2.txt", 
                1024L, LocalDateTime.now());
        FileInfo file3 = new FileInfo("test3.txt", "/path/test3.txt", "test3.txt", 
                1024L, LocalDateTime.now());
        
        when(fileSystemService.scanDirectory(any(Path.class), eq(pattern)))
                .thenReturn(Arrays.asList(file1, file2, file3));
        when(fileSystemService.scanDirectory(any(Path.class), eq("*")))
                .thenReturn(Collections.emptyList());

        // Act
        FileSearchResponse response = archiveSearchService.searchFiles(searchPath, pattern);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getTotalCount()); // Limited to 2
        assertEquals(2, response.getFiles().size());
    }

    @Test
    void testDownloadFile_RegularFile() throws Exception {
        // Arrange
        Path testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "test content".getBytes());
        String filePath = testFile.toString();
        
        when(securityValidator.isPathAllowed(filePath)).thenReturn(true);
        when(securityValidator.isFileAccessible(testFile)).thenReturn(true);

        // Act
        InputStream result = archiveSearchService.downloadFile(filePath);

        // Assert
        assertNotNull(result);
        verify(securityValidator).isPathAllowed(filePath);
        verify(securityValidator).isFileAccessible(testFile);
        
        result.close();
    }

    @Test
    void testDownloadFile_ArchiveEntry() throws Exception {
        // Arrange
        String filePath = "/path/archive.zip::entry.txt";
        String archivePath = "/path/archive.zip";
        String entryPath = "entry.txt";
        
        when(securityValidator.isPathAllowed(archivePath)).thenReturn(true);
        when(securityValidator.isFileAccessible(any(Path.class))).thenReturn(true);
        when(archiveHandlerService.isArchiveFile(any(Path.class))).thenReturn(true);
        
        InputStream mockStream = new ByteArrayInputStream("archive content".getBytes());
        when(archiveHandlerService.extractFileFromArchive(any(Path.class), eq(entryPath)))
                .thenReturn(mockStream);

        // Act
        InputStream result = archiveSearchService.downloadFile(filePath);

        // Assert
        assertNotNull(result);
        assertEquals(mockStream, result);
        verify(securityValidator).isPathAllowed(archivePath);
        verify(archiveHandlerService).extractFileFromArchive(any(Path.class), eq(entryPath));
    }

    @Test
    void testDownloadFile_AccessDenied() {
        // Arrange
        String filePath = "/forbidden/file.txt";
        
        when(securityValidator.isPathAllowed(filePath)).thenReturn(false);

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            archiveSearchService.downloadFile(filePath);
        });
        
        assertEquals("Access denied to file: " + filePath, exception.getMessage());
    }

    @Test
    void testDownloadFile_NullPath() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            archiveSearchService.downloadFile(null);
        });
        
        assertEquals("File path cannot be null or empty", exception.getMessage());
    }

    @Test
    void testDownloadFile_InvalidArchiveEntryFormat() {
        // Arrange
        String filePath = "/path/archive.zip:invalid:format";
        
        // This should be treated as a regular file path, not archive entry
        when(securityValidator.isPathAllowed(filePath)).thenReturn(false);

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            archiveSearchService.downloadFile(filePath);
        });
        
        assertEquals("Access denied to file: " + filePath, exception.getMessage());
    }

    @Test
    void testSearchContent_RegularFile() throws Exception {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest("/path/test.txt", "search term");
        
        when(securityValidator.isPathAllowed(request.getFilePath())).thenReturn(true);
        when(securityValidator.isFileAccessible(any(Path.class))).thenReturn(true);
        when(contentSearchService.isTextFile(any(Path.class))).thenReturn(true);
        
        ContentSearchResponse expectedResponse = new ContentSearchResponse(
                Collections.emptyList(), 0, 100L);
        when(contentSearchService.searchInFile(request)).thenReturn(expectedResponse);

        // Act
        ContentSearchResponse result = archiveSearchService.searchContent(request);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(securityValidator).isPathAllowed(request.getFilePath());
        verify(contentSearchService).searchInFile(request);
    }

    @Test
    void testSearchContent_ArchiveEntry() throws Exception {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest("/path/archive.zip::entry.txt", "search term");
        String archivePath = "/path/archive.zip";
        String entryPath = "entry.txt";
        
        when(securityValidator.isPathAllowed(archivePath)).thenReturn(true);
        when(securityValidator.isFileAccessible(any(Path.class))).thenReturn(true);
        when(archiveHandlerService.isArchiveFile(any(Path.class))).thenReturn(true);
        
        ContentSearchResponse expectedResponse = new ContentSearchResponse(
                Collections.emptyList(), 0, 100L);
        when(contentSearchService.searchInArchiveFile(any(Path.class), eq(entryPath), eq(request)))
                .thenReturn(expectedResponse);

        // Act
        ContentSearchResponse result = archiveSearchService.searchContent(request);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(securityValidator).isPathAllowed(archivePath);
        verify(contentSearchService).searchInArchiveFile(any(Path.class), eq(entryPath), eq(request));
    }

    @Test
    void testSearchContent_NullRequest() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            archiveSearchService.searchContent(null);
        });
        
        assertEquals("Content search request cannot be null", exception.getMessage());
    }

    @Test
    void testSearchContent_NullFilePath() {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest();
        request.setSearchTerm("search term");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            archiveSearchService.searchContent(request);
        });
        
        assertEquals("File path cannot be null or empty", exception.getMessage());
    }

    @Test
    void testSearchContent_NullSearchTerm() {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath("/path/test.txt");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            archiveSearchService.searchContent(request);
        });
        
        assertEquals("Search term cannot be null or empty", exception.getMessage());
    }

    @Test
    void testSearchContent_AccessDenied() {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest("/forbidden/file.txt", "search term");
        
        when(securityValidator.isPathAllowed(request.getFilePath())).thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            archiveSearchService.searchContent(request);
        });
        
        assertTrue(exception.getCause() instanceof SecurityException);
        assertEquals("Access denied to file: " + request.getFilePath(), exception.getCause().getMessage());
    }

    @Test
    void testSearchContent_NonTextFile() {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest("/path/binary.exe", "search term");
        
        when(securityValidator.isPathAllowed(request.getFilePath())).thenReturn(true);
        when(securityValidator.isFileAccessible(any(Path.class))).thenReturn(true);
        when(contentSearchService.isTextFile(any(Path.class))).thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            archiveSearchService.searchContent(request);
        });
        
        assertTrue(exception.getCause() instanceof IOException);
        assertTrue(exception.getCause().getMessage().contains("File does not appear to be a text file"));
    }

    @Test
    void testIsPathAllowed() {
        // Arrange
        String path = "/test/path";
        when(securityValidator.isPathAllowed(path)).thenReturn(true);

        // Act
        boolean result = archiveSearchService.isPathAllowed(path);

        // Assert
        assertTrue(result);
        verify(securityValidator).isPathAllowed(path);
    }

    @Test
    void testSearchFiles_IOException() throws Exception {
        // Arrange
        String searchPath = tempDir.toString();
        String pattern = "*.txt";
        
        when(securityValidator.isPathAllowed(searchPath)).thenReturn(true);
        when(fileSystemService.scanDirectory(any(Path.class), eq(pattern)))
                .thenThrow(new IOException("File system error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            archiveSearchService.searchFiles(searchPath, pattern);
        });
        
        assertTrue(exception.getCause() instanceof IOException);
        assertEquals("File system error", exception.getCause().getMessage());
    }

    @Test
    void testSearchFiles_ArchiveProcessingError() throws Exception {
        // Arrange
        String searchPath = tempDir.toString();
        String pattern = "*.txt";
        
        when(securityValidator.isPathAllowed(searchPath)).thenReturn(true);
        when(fileSystemService.scanDirectory(any(Path.class), eq(pattern)))
                .thenReturn(Collections.emptyList());
        
        // Mock archive file that causes error
        FileInfo archiveEntry = new FileInfo("corrupt.zip", "/path/corrupt.zip", "corrupt.zip", 
                2048L, LocalDateTime.now());
        when(fileSystemService.scanDirectory(any(Path.class), eq("*")))
                .thenReturn(Arrays.asList(archiveEntry));
        when(archiveHandlerService.isArchiveFile(any(Path.class))).thenReturn(true);
        when(securityValidator.isFileAccessible(any(Path.class))).thenReturn(true);
        when(archiveHandlerService.listArchiveContents(any(Path.class), eq(pattern)))
                .thenThrow(new IOException("Corrupt archive"));

        // Act - should not throw exception, just log warning and continue
        FileSearchResponse response = archiveSearchService.searchFiles(searchPath, pattern);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getTotalCount()); // No results due to archive error
    }

    @Test
    void testShutdown() {
        // Act
        archiveSearchService.shutdown();

        // Assert - no exception should be thrown
        // The executor service should be shut down gracefully
    }

    @Test
    void testSearchFiles_WithTimeout() throws Exception {
        // Arrange
        when(properties.getSearchTimeoutSeconds()).thenReturn(1); // Very short timeout
        
        String searchPath = tempDir.toString();
        String pattern = "*.txt";
        
        when(securityValidator.isPathAllowed(searchPath)).thenReturn(true);
        
        // Mock a slow operation
        when(fileSystemService.scanDirectory(any(Path.class), eq(pattern)))
                .thenAnswer(invocation -> {
                    Thread.sleep(2000); // Sleep longer than timeout
                    return Collections.emptyList();
                });

        // Act & Assert
        TimeoutException exception = assertThrows(TimeoutException.class, () -> {
            archiveSearchService.searchFiles(searchPath, pattern);
        });
        
        assertTrue(exception.getMessage().contains("timed out"));
    }
}