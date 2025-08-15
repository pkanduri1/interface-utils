package com.fabric.watcher.integration;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.ContentSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Comprehensive integration tests for Archive Search API combining security,
 * environment detection, access control, and Swagger integration.
 * 
 * This test class provides end-to-end testing of all security and environment
 * features working together in realistic scenarios.
 * 
 * Requirements: 6.1, 6.2, 6.4, 6.5, 7.1, 7.2, 8.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "spring.profiles.active=test",
    "springdoc.api-docs.enabled=true",
    "springdoc.swagger-ui.enabled=true"
})
class ArchiveSearchComprehensiveIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArchiveSearchProperties archiveSearchProperties;

    @LocalServerPort
    private int port;

    @TempDir
    Path tempDir;

    private Path allowedDir;
    private Path excludedDir;
    private Path unauthorizedDir;
    private Path testFile;
    private Path archiveFile;
    private Path largeFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create comprehensive test directory structure
        allowedDir = tempDir.resolve("allowed");
        excludedDir = allowedDir.resolve("excluded");
        unauthorizedDir = tempDir.resolve("unauthorized");
        
        Files.createDirectories(allowedDir);
        Files.createDirectories(excludedDir);
        Files.createDirectories(unauthorizedDir);

        // Create test files
        testFile = allowedDir.resolve("test.txt");
        Files.write(testFile, "This is test content for comprehensive testing".getBytes());

        // Create a test archive
        archiveFile = allowedDir.resolve("test.zip");
        createTestArchive(archiveFile);

        // Create a large file for size limit testing
        largeFile = allowedDir.resolve("large.txt");
        byte[] largeContent = new byte[1024 * 1024 + 1]; // 1MB + 1 byte
        Arrays.fill(largeContent, (byte) 'L');
        Files.write(largeFile, largeContent);

        // Update archive search properties
        archiveSearchProperties.setAllowedPaths(Arrays.asList(allowedDir.toString()));
        archiveSearchProperties.setExcludedPaths(Arrays.asList(excludedDir.toString()));
        archiveSearchProperties.setMaxFileSize(1024 * 1024L); // 1MB limit
    }

    /**
     * Test complete security workflow with valid operations
     * Requirements: 6.1, 6.2
     */
    @Test
    void testCompleteSecurityWorkflowWithValidOperations() throws Exception {
        // Test file search in allowed directory
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(2)); // test.txt and large.txt

        // Test file download from allowed directory
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", testFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.txt\""))
                .andExpect(content().string("This is test content for comprehensive testing"));

        // Test content search in allowed file
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(testFile.toString());
        request.setSearchTerm("comprehensive");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(1));
    }

    /**
     * Test complete security workflow with malicious operations
     * Requirements: 6.1, 6.2
     */
    @Test
    void testCompleteSecurityWorkflowWithMaliciousOperations() throws Exception {
        String[] maliciousInputs = {
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32",
            "%2e%2e/etc/passwd",
            "/allowed/../../../etc/passwd"
        };

        for (String maliciousPath : maliciousInputs) {
            // Test all endpoints reject malicious input
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", maliciousPath)
                    .param("pattern", "*.txt"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));

            mockMvc.perform(get("/api/v1/archive/download")
                    .param("filePath", maliciousPath))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));

            ContentSearchRequest request = new ContentSearchRequest();
            request.setFilePath(maliciousPath);
            request.setSearchTerm("test");

            mockMvc.perform(post("/api/v1/archive/content-search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));
        }
    }

    /**
     * Test environment detection and API availability
     * Requirements: 7.1, 7.2
     */
    @Test
    void testEnvironmentDetectionAndApiAvailability() throws Exception {
        // Test that API is available in test environment
        mockMvc.perform(get("/api/v1/archive/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.environment").value("test"));

        // Test that all endpoints are accessible
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", testFile.toString()))
                .andExpect(status().isOk());
    }

    /**
     * Test Swagger UI integration in non-production environment
     * Requirements: 8.4
     */
    @Test
    void testSwaggerUiIntegrationInNonProduction() throws Exception {
        // Test Swagger UI accessibility
        String swaggerUrl = "http://localhost:" + port + "/swagger-ui.html";
        ResponseEntity<String> response = restTemplate.getForEntity(swaggerUrl, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test API documentation includes archive search endpoints
        mockMvc.perform(get("/api-docs/archive-search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/archive/search']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/archive/download']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/archive/content-search']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/archive/status']").exists());
    }

    /**
     * Test concurrent operations with security validation
     * Requirements: 6.4
     */
    @Test
    void testConcurrentOperationsWithSecurityValidation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int numberOfOperations = 20;
        
        CompletableFuture<Boolean>[] futures = new CompletableFuture[numberOfOperations];
        
        // Mix of valid and invalid operations
        for (int i = 0; i < numberOfOperations; i++) {
            final int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    String path = (index % 2 == 0) ? allowedDir.toString() : "../../../etc/passwd";
                    String url = "http://localhost:" + port + "/api/v1/archive/search?path=" + 
                                path + "&pattern=*.txt";
                    
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                    
                    if (index % 2 == 0) {
                        // Valid operations should succeed
                        return response.getStatusCode() == HttpStatus.OK;
                    } else {
                        // Invalid operations should be rejected
                        return response.getStatusCode() == HttpStatus.FORBIDDEN;
                    }
                } catch (Exception e) {
                    return false;
                }
            }, executor);
        }
        
        // Wait for all operations to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        
        // Verify results
        for (CompletableFuture<Boolean> future : futures) {
            assertThat(future.get()).isTrue();
        }
        
        executor.shutdown();
    }

    /**
     * Test archive processing with security validation
     * Requirements: 6.1, 6.2
     */
    @Test
    void testArchiveProcessingWithSecurityValidation() throws Exception {
        // Test search within archive
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*.zip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(1));

        // Test download of archive file
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", archiveFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.zip\""));
    }

    /**
     * Test file size limits with security context
     * Requirements: 6.3, 6.4
     */
    @Test
    void testFileSizeLimitsWithSecurityContext() throws Exception {
        // Test that large file download is rejected
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", largeFile.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));

        // Test that large file content search is rejected
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(largeFile.toString());
        request.setSearchTerm("L");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));
    }

    /**
     * Test excluded directory access control
     * Requirements: 6.1, 6.2
     */
    @Test
    void testExcludedDirectoryAccessControl() throws Exception {
        // Create file in excluded directory
        Path excludedFile = excludedDir.resolve("secret.txt");
        Files.write(excludedFile, "This is secret content".getBytes());

        // Test that access to excluded directory is denied
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", excludedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        // Test that download from excluded directory is denied
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", excludedFile.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));
    }

    /**
     * Test comprehensive error handling and logging
     * Requirements: 6.5
     */
    @Test
    void testComprehensiveErrorHandlingAndLogging() throws Exception {
        // Test various error scenarios to ensure proper logging
        
        // Path traversal attempt
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "../../../etc/passwd")
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden());

        // File not found
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", allowedDir.resolve("nonexistent.txt").toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ARCH002"));

        // Invalid parameters
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "")
                .param("pattern", "*.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));

        // Unsupported file format for content search
        Path binaryFile = allowedDir.resolve("binary.bin");
        Files.write(binaryFile, new byte[]{0x00, 0x01, 0x02, 0x03});

        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(binaryFile.toString());
        request.setSearchTerm("test");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));
    }

    /**
     * Test API status endpoint comprehensive information
     * Requirements: 7.1, 8.1
     */
    @Test
    void testApiStatusEndpointComprehensiveInformation() throws Exception {
        mockMvc.perform(get("/api/v1/archive/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.environment").value("test"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.supportedArchiveTypes").isArray())
                .andExpect(jsonPath("$.maxFileSize").exists())
                .andExpect(jsonPath("$.maxSearchResults").exists())
                .andExpect(jsonPath("$.allowedPaths").isArray())
                .andExpect(jsonPath("$.excludedPaths").isArray());
    }

    /**
     * Test input validation edge cases
     * Requirements: 6.2
     */
    @Test
    void testInputValidationEdgeCases() throws Exception {
        // Test extremely long paths
        String longPath = "a".repeat(1001);
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", longPath)
                .param("pattern", "*.txt"))
                .andExpect(status().isBadRequest());

        // Test null byte injection
        String pathWithNullByte = allowedDir.toString() + "\u0000../../../etc/passwd";
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", pathWithNullByte)
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden());

        // Test special characters in patterns
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*<script>alert('xss')</script>*"))
                .andExpect(status().isOk()); // Should be sanitized and processed safely
    }

    private void createTestArchive(Path archivePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            // Add a text file to the archive
            ZipEntry entry = new ZipEntry("archived-file.txt");
            zos.putNextEntry(entry);
            zos.write("This is content inside the archive".getBytes());
            zos.closeEntry();
            
            // Add another file
            entry = new ZipEntry("data/nested-file.txt");
            zos.putNextEntry(entry);
            zos.write("This is nested content in the archive".getBytes());
            zos.closeEntry();
        }
    }
}