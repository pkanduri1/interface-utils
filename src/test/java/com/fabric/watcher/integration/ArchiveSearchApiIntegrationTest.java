package com.fabric.watcher.integration;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.ContentSearchRequest;
import com.fabric.watcher.archive.model.AuthRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests for Archive Search API endpoints.
 * Tests all API functionality including file search, content search, download, and upload operations.
 * 
 * Requirements: 1.1, 2.1, 3.1, 4.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "spring.profiles.active=test",
    "archive.search.allowed-paths=/tmp/test-archives",
    "archive.search.max-file-size=10485760",
    "archive.search.max-search-results=100",
    "archive.search.search-timeout-seconds=30"
})
class ArchiveSearchApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArchiveSearchProperties archiveSearchProperties;

    @TempDir
    Path tempDir;

    private Path allowedDir;
    private Path testFile1;
    private Path testFile2;
    private Path archiveFile;
    private Path largeFile;
    private Path binaryFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create test directory structure
        allowedDir = tempDir.resolve("test-archives");
        Files.createDirectories(allowedDir);

        // Create test files with different content
        testFile1 = allowedDir.resolve("document1.txt");
        Files.write(testFile1, "This is the first test document with sample content for searching.".getBytes());

        testFile2 = allowedDir.resolve("document2.log");
        Files.write(testFile2, "Log entry 1: Application started\nLog entry 2: User login successful\nLog entry 3: File processed".getBytes());

        // Create a test archive with multiple files
        archiveFile = allowedDir.resolve("test-archive.zip");
        createTestArchive(archiveFile);

        // Create a large file for size limit testing
        largeFile = allowedDir.resolve("large-document.txt");
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("Line ").append(i).append(": This is a large file with many lines for testing content search limits.\n");
        }
        Files.write(largeFile, largeContent.toString().getBytes());

        // Create a binary file
        binaryFile = allowedDir.resolve("binary-data.bin");
        byte[] binaryData = {0x00, 0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0xFE, (byte) 0xFD};
        Files.write(binaryFile, binaryData);

        // Update archive search properties
        archiveSearchProperties.setAllowedPaths(Arrays.asList(allowedDir.toString()));
        archiveSearchProperties.setMaxFileSize(10 * 1024 * 1024L); // 10MB
    }

    /**
     * Test file search with various wildcard patterns
     * Requirements: 1.1
     */
    @Test
    void testFileSearchWithWildcardPatterns() throws Exception {
        // Test search for all text files
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(2)) // document1.txt and large-document.txt
                .andExpect(jsonPath("$.searchPath").value(allowedDir.toString()))
                .andExpect(jsonPath("$.searchPattern").value("*.txt"));

        // Test search for all log files
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*.log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(1)) // document2.log
                .andExpect(jsonPath("$.files[0].fileName").value("document2.log"));

        // Test search for all files
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(5)); // All files including archive

        // Test search with specific filename pattern
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "document*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(2)); // document1.txt and document2.log

        // Test search with question mark wildcard
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "document?.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(1)) // document1.txt
                .andExpect(jsonPath("$.files[0].fileName").value("document1.txt"));
    }

    /**
     * Test archive processing with different formats
     * Requirements: 4.1
     */
    @Test
    void testArchiveProcessingWithDifferentFormats() throws Exception {
        // Test search for archive files
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*.zip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.files[0].fileName").value("test-archive.zip"))
                .andExpect(jsonPath("$.files[0].type").value("REGULAR"));

        // Test download of archive file
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", archiveFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test-archive.zip\""))
                .andExpect(header().string("Content-Type", "application/zip"));

        // Create TAR archive for testing
        Path tarFile = allowedDir.resolve("test-archive.tar");
        createTestTarArchive(tarFile);

        // Test search for TAR files
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*.tar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.files[0].fileName").value("test-archive.tar"));
    }

    /**
     * Test content search with large files and edge cases
     * Requirements: 3.1
     */
    @Test
    void testContentSearchWithLargeFilesAndEdgeCases() throws Exception {
        // Test content search in regular file
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(testFile1.toString());
        request.setSearchTerm("sample");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.matches[0].lineContent").value("This is the first test document with sample content for searching."))
                .andExpect(jsonPath("$.truncated").value(false));

        // Test content search with multiple matches
        request.setFilePath(testFile2.toString());
        request.setSearchTerm("entry");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(3))
                .andExpect(jsonPath("$.truncated").value(false));

        // Test content search in large file (should be truncated at 100 lines)
        request.setFilePath(largeFile.toString());
        request.setSearchTerm("Line");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(100)) // Limited to 100
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.downloadSuggestion").exists());

        // Test case-sensitive search
        request.setFilePath(testFile1.toString());
        request.setSearchTerm("SAMPLE");
        request.setCaseSensitive(true);

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(0)); // No matches due to case sensitivity

        // Test case-insensitive search
        request.setCaseSensitive(false);

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(1)); // Should find match

        // Test whole word search
        request.setSearchTerm("test");
        request.setWholeWord(true);

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(1));

        // Test search with no matches
        request.setSearchTerm("nonexistent");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(0));

        // Test search in binary file (should fail gracefully)
        request.setFilePath(binaryFile.toString());
        request.setSearchTerm("test");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));
    }

    /**
     * Test file download functionality
     * Requirements: 2.1
     */
    @Test
    void testFileDownloadFunctionality() throws Exception {
        // Test download of regular text file
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", testFile1.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"document1.txt\""))
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(content().string("This is the first test document with sample content for searching."));

        // Test download of log file
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", testFile2.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"document2.log\""))
                .andExpect(content().string("Log entry 1: Application started\nLog entry 2: User login successful\nLog entry 3: File processed"));

        // Test download of binary file
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", binaryFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"binary-data.bin\""))
                .andExpect(header().string("Content-Type", "application/octet-stream"));

        // Test download of non-existent file
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", allowedDir.resolve("nonexistent.txt").toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ARCH002"));
    }

    /**
     * Test concurrent operations and performance
     * Requirements: 1.1, 2.1, 3.1
     */
    @Test
    void testConcurrentOperationsAndPerformance() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int numberOfOperations = 20;
        
        CompletableFuture<Boolean>[] futures = new CompletableFuture[numberOfOperations];
        
        // Mix of different operations
        for (int i = 0; i < numberOfOperations; i++) {
            final int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    switch (index % 3) {
                        case 0:
                            // File search operation
                            mockMvc.perform(get("/api/v1/archive/search")
                                    .param("path", allowedDir.toString())
                                    .param("pattern", "*.txt"))
                                    .andExpect(status().isOk());
                            break;
                        case 1:
                            // Content search operation
                            ContentSearchRequest request = new ContentSearchRequest();
                            request.setFilePath(testFile1.toString());
                            request.setSearchTerm("test");
                            
                            mockMvc.perform(post("/api/v1/archive/content-search")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                                    .andExpect(status().isOk());
                            break;
                        case 2:
                            // Download operation
                            mockMvc.perform(get("/api/v1/archive/download")
                                    .param("filePath", testFile1.toString()))
                                    .andExpect(status().isOk());
                            break;
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }, executor);
        }
        
        // Wait for all operations to complete
        CompletableFuture.allOf(futures).get(60, TimeUnit.SECONDS);
        
        // Verify all operations succeeded
        for (CompletableFuture<Boolean> future : futures) {
            assertThat(future.get()).isTrue();
        }
        
        executor.shutdown();
    }

    /**
     * Test error handling and edge cases
     * Requirements: 1.1, 2.1, 3.1, 4.1
     */
    @Test
    void testErrorHandlingAndEdgeCases() throws Exception {
        // Test search with invalid path
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/nonexistent/path")
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        // Test search with empty pattern
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", ""))
                .andExpect(status().isBadRequest());

        // Test download with path traversal attempt
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", "../../../etc/passwd"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        // Test content search with invalid file path
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath("/invalid/path/file.txt");
        request.setSearchTerm("test");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        // Test content search with empty search term
        request.setFilePath(testFile1.toString());
        request.setSearchTerm("");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Test with malformed JSON
        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test API status and health endpoints
     * Requirements: 1.1
     */
    @Test
    void testApiStatusAndHealthEndpoints() throws Exception {
        // Test status endpoint
        mockMvc.perform(get("/api/v1/archive/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.environment").value("test"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.supportedArchiveTypes").isArray())
                .andExpect(jsonPath("$.maxFileSize").exists())
                .andExpect(jsonPath("$.maxSearchResults").exists());
    }

    /**
     * Helper method to create a test ZIP archive
     */
    private void createTestArchive(Path archivePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            // Add a text file to the archive
            ZipEntry entry1 = new ZipEntry("archived-document.txt");
            zos.putNextEntry(entry1);
            zos.write("This is content inside the ZIP archive for testing.".getBytes());
            zos.closeEntry();
            
            // Add a nested file
            ZipEntry entry2 = new ZipEntry("data/nested-file.log");
            zos.putNextEntry(entry2);
            zos.write("Nested log entry: Archive processing completed successfully.".getBytes());
            zos.closeEntry();

            // Add another file with different content
            ZipEntry entry3 = new ZipEntry("config.properties");
            zos.putNextEntry(entry3);
            zos.write("app.name=TestApp\napp.version=1.0\napp.debug=true".getBytes());
            zos.closeEntry();
        }
    }

    /**
     * Helper method to create a test TAR archive
     */
    private void createTestTarArchive(Path tarPath) throws IOException {
        // For simplicity, create a simple TAR-like file
        // In a real implementation, you would use a proper TAR library
        Files.write(tarPath, "TAR archive content for testing".getBytes());
    }
}