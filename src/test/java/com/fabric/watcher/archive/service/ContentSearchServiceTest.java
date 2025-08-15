package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.ContentSearchRequest;
import com.fabric.watcher.archive.model.ContentSearchResponse;
import com.fabric.watcher.archive.model.SearchMatch;
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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ContentSearchService.
 */
@ExtendWith(MockitoExtension.class)
class ContentSearchServiceTest {

    @Mock
    private ArchiveHandlerService archiveHandlerService;

    private ArchiveSearchProperties properties;
    private ContentSearchService contentSearchService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new ArchiveSearchProperties();
        properties.setMaxSearchResults(100);
        properties.setSearchTimeoutSeconds(30);
        properties.setMaxFileSize(100 * 1024 * 1024); // 100MB
        
        contentSearchService = new ContentSearchService(properties, archiveHandlerService);
    }

    @Test
    void testSearchInFile_BasicSearch() throws IOException {
        // Arrange
        String content = "Hello world\nThis is a test\nHello again\nEnd of file";
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "Hello");

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getTotalMatches());
        assertEquals(2, response.getMatches().size());
        assertFalse(response.isTruncated());
        assertNull(response.getDownloadSuggestion());
        
        SearchMatch firstMatch = response.getMatches().get(0);
        assertEquals(1, firstMatch.getLineNumber());
        assertEquals("Hello world", firstMatch.getLineContent());
        assertEquals(0, firstMatch.getColumnStart());
        assertEquals(5, firstMatch.getColumnEnd());
        
        SearchMatch secondMatch = response.getMatches().get(1);
        assertEquals(3, secondMatch.getLineNumber());
        assertEquals("Hello again", secondMatch.getLineContent());
        assertEquals(0, secondMatch.getColumnStart());
        assertEquals(5, secondMatch.getColumnEnd());
    }

    @Test
    void testSearchInFile_CaseSensitiveSearch() throws IOException {
        // Arrange
        String content = "Hello world\nhello world\nHELLO WORLD";
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "Hello", true, false);

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalMatches());
        assertEquals(1, response.getMatches().size());
        
        SearchMatch match = response.getMatches().get(0);
        assertEquals(1, match.getLineNumber());
        assertEquals("Hello world", match.getLineContent());
    }

    @Test
    void testSearchInFile_CaseInsensitiveSearch() throws IOException {
        // Arrange
        String content = "Hello world\nhello world\nHELLO WORLD";
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "Hello", false, false);

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(3, response.getTotalMatches());
        assertEquals(3, response.getMatches().size());
    }

    @Test
    void testSearchInFile_WholeWordSearch() throws IOException {
        // Arrange
        String content = "Hello world\nSay hello to everyone\nHelloWorld";
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "Hello", false, true);

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getTotalMatches()); // Should not match "HelloWorld"
        assertEquals(2, response.getMatches().size());
        
        assertEquals(1, response.getMatches().get(0).getLineNumber());
        assertEquals(2, response.getMatches().get(1).getLineNumber());
    }

    @Test
    void testSearchInFile_NoMatches() throws IOException {
        // Arrange
        String content = "This is a test file\nWith some content\nBut no matches";
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "NotFound");

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getTotalMatches());
        assertEquals(0, response.getMatches().size());
        assertFalse(response.isTruncated());
        assertNull(response.getDownloadSuggestion());
    }

    @Test
    void testSearchInFile_TruncatedResults() throws IOException {
        // Arrange
        properties.setMaxSearchResults(2); // Set low limit for testing
        
        String content = "test test test\ntest test\ntest"; // 6 matches total: 3 + 2 + 1
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "test");

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(6, response.getTotalMatches());
        assertEquals(2, response.getMatches().size()); // Truncated to 2
        assertTrue(response.isTruncated());
        assertNotNull(response.getDownloadSuggestion());
        assertTrue(response.getDownloadSuggestion().contains("Results truncated to 2 matches"));
        assertTrue(response.getDownloadSuggestion().contains("all 6 matches"));
    }

    @Test
    void testSearchInFile_FileNotFound() throws IOException {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest("/nonexistent/file.txt", "test");

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getTotalMatches());
        assertEquals(0, response.getMatches().size());
    }

    @Test
    void testSearchInFile_FileTooLarge() throws IOException {
        // Arrange
        properties.setMaxFileSize(10); // Set very low limit
        
        String content = "This content is longer than 10 bytes";
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "test");

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> {
            contentSearchService.searchInFile(request);
        });
        assertTrue(exception.getMessage().contains("File size exceeds maximum allowed"));
    }

    @Test
    void testSearchInFile_NullRequest() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            contentSearchService.searchInFile(null);
        });
        assertTrue(exception.getMessage().contains("Request, file path, and search term cannot be null"));
    }

    @Test
    void testSearchInFile_NullFilePath() {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest();
        request.setSearchTerm("test");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            contentSearchService.searchInFile(request);
        });
        assertTrue(exception.getMessage().contains("Request, file path, and search term cannot be null"));
    }

    @Test
    void testSearchInFile_NullSearchTerm() {
        // Arrange
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath("/some/path");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            contentSearchService.searchInFile(request);
        });
        assertTrue(exception.getMessage().contains("Request, file path, and search term cannot be null"));
    }

    @Test
    void testSearchInArchiveFile_Success() throws IOException {
        // Arrange
        String content = "Hello world\nThis is archived content\nHello again";
        InputStream contentStream = new ByteArrayInputStream(content.getBytes());
        
        Path archivePath = tempDir.resolve("test.zip");
        String entryName = "test.txt";
        ContentSearchRequest request = new ContentSearchRequest("archive://" + entryName, "Hello");
        
        when(archiveHandlerService.extractFileFromArchive(eq(archivePath), eq(entryName)))
            .thenReturn(contentStream);

        // Act
        ContentSearchResponse response = contentSearchService.searchInArchiveFile(archivePath, entryName, request);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getTotalMatches());
        assertEquals(2, response.getMatches().size());
        
        assertEquals(1, response.getMatches().get(0).getLineNumber());
        assertEquals(3, response.getMatches().get(1).getLineNumber());
    }

    @Test
    void testSearchInArchiveFile_EntryNotFound() throws IOException {
        // Arrange
        Path archivePath = tempDir.resolve("test.zip");
        String entryName = "nonexistent.txt";
        ContentSearchRequest request = new ContentSearchRequest("archive://" + entryName, "test");
        
        when(archiveHandlerService.extractFileFromArchive(eq(archivePath), eq(entryName)))
            .thenReturn(null);

        // Act
        ContentSearchResponse response = contentSearchService.searchInArchiveFile(archivePath, entryName, request);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getTotalMatches());
        assertEquals(0, response.getMatches().size());
    }

    @Test
    void testSearchInArchiveFile_NullParameters() {
        // Arrange
        Path archivePath = tempDir.resolve("test.zip");
        String entryName = "test.txt";
        ContentSearchRequest request = new ContentSearchRequest("test", "search");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            contentSearchService.searchInArchiveFile(null, entryName, request);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            contentSearchService.searchInArchiveFile(archivePath, null, request);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            contentSearchService.searchInArchiveFile(archivePath, entryName, null);
        });
    }

    @Test
    void testIsTextFile_TextFile() throws IOException {
        // Arrange
        String content = "This is a text file\nWith multiple lines\nAnd normal text content";
        Path testFile = createTestFile("test.txt", content);

        // Act
        boolean result = contentSearchService.isTextFile(testFile);

        // Assert
        assertTrue(result);
    }

    @Test
    void testIsTextFile_BinaryFile() throws IOException {
        // Arrange
        byte[] binaryContent = {0x00, 0x01, 0x02, 0x03, 0x00, 0x05}; // Contains null bytes
        Path testFile = tempDir.resolve("binary.bin");
        Files.write(testFile, binaryContent);

        // Act
        boolean result = contentSearchService.isTextFile(testFile);

        // Assert
        assertFalse(result);
    }

    @Test
    void testIsTextFile_EmptyFile() throws IOException {
        // Arrange
        Path testFile = createTestFile("empty.txt", "");

        // Act
        boolean result = contentSearchService.isTextFile(testFile);

        // Assert
        assertTrue(result); // Empty file is considered text
    }

    @Test
    void testIsTextFile_NonExistentFile() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");

        // Act
        boolean result = contentSearchService.isTextFile(nonExistentFile);

        // Assert
        assertFalse(result);
    }

    @Test
    void testSearchInFile_MultipleMatchesPerLine() throws IOException {
        // Arrange
        String content = "test test test\nother line\ntest again test";
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "test");

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(5, response.getTotalMatches()); // 3 + 0 + 2
        assertEquals(5, response.getMatches().size());
        
        // Check first line matches
        assertEquals(1, response.getMatches().get(0).getLineNumber());
        assertEquals(0, response.getMatches().get(0).getColumnStart());
        assertEquals(4, response.getMatches().get(0).getColumnEnd());
        
        assertEquals(1, response.getMatches().get(1).getLineNumber());
        assertEquals(5, response.getMatches().get(1).getColumnStart());
        assertEquals(9, response.getMatches().get(1).getColumnEnd());
        
        assertEquals(1, response.getMatches().get(2).getLineNumber());
        assertEquals(10, response.getMatches().get(2).getColumnStart());
        assertEquals(14, response.getMatches().get(2).getColumnEnd());
        
        // Check third line matches
        assertEquals(3, response.getMatches().get(3).getLineNumber());
        assertEquals(3, response.getMatches().get(4).getLineNumber());
    }

    @Test
    void testSearchInFile_SpecialCharacters() throws IOException {
        // Arrange
        String content = "Special chars: [test] (test) {test}\nRegex chars: test* test+ test?";
        Path testFile = createTestFile("test.txt", content);
        
        ContentSearchRequest request = new ContentSearchRequest(testFile.toString(), "[test]");

        // Act
        ContentSearchResponse response = contentSearchService.searchInFile(request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalMatches());
        assertEquals(1, response.getMatches().size());
        
        SearchMatch match = response.getMatches().get(0);
        assertEquals(1, match.getLineNumber());
        assertTrue(match.getLineContent().contains("[test]"));
    }

    /**
     * Helper method to create a test file with the given content.
     */
    private Path createTestFile(String fileName, String content) throws IOException {
        Path testFile = tempDir.resolve(fileName);
        Files.write(testFile, content.getBytes());
        return testFile;
    }
}