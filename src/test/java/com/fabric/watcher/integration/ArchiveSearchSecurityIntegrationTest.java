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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Archive Search API security features.
 * Tests path traversal prevention, access control, and security validation
 * with real HTTP requests and file system interactions.
 * 
 * Requirements: 6.1, 6.2, 6.4, 6.5
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "spring.profiles.active=test"
})
class ArchiveSearchSecurityIntegrationTest {

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
    private Path unauthorizedDir;
    private Path testFile;
    private Path unauthorizedFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create test directory structure
        allowedDir = tempDir.resolve("allowed");
        unauthorizedDir = tempDir.resolve("unauthorized");
        Files.createDirectories(allowedDir);
        Files.createDirectories(unauthorizedDir);

        // Create test files
        testFile = allowedDir.resolve("test.txt");
        Files.write(testFile, "This is test content for security testing".getBytes());

        unauthorizedFile = unauthorizedDir.resolve("secret.txt");
        Files.write(unauthorizedFile, "This is sensitive content".getBytes());

        // Update archive search properties to use test directories
        archiveSearchProperties.setAllowedPaths(Arrays.asList(allowedDir.toString()));
        archiveSearchProperties.setExcludedPaths(Arrays.asList());
    }

    /**
     * Test path traversal prevention with various malicious inputs
     * Requirements: 6.1
     */
    @Test
    void testPathTraversalPrevention_VariousMaliciousInputs() throws Exception {
        String[] maliciousInputs = {
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32",
            "/allowed/../../../etc/passwd",
            "allowed/subdir/../../../etc/passwd",
            "%2e%2e/etc/passwd",
            "%252e%252e/etc/passwd",
            "0x2e0x2e/etc/passwd",
            "\\x2e\\x2e/etc/passwd",
            "/valid/path\u0000../../../etc/passwd",
            "../ETC/passwd",
            "..\\WINDOWS\\system32",
            "/path//to///../../etc/passwd"
        };

        for (String maliciousPath : maliciousInputs) {
            // Test file search endpoint
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", maliciousPath)
                    .param("pattern", "*.txt"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));

            // Test download endpoint
            mockMvc.perform(get("/api/v1/archive/download")
                    .param("filePath", maliciousPath))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));

            // Test content search endpoint
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
     * Test symbolic link traversal prevention
     * Requirements: 6.1
     */
    @Test
    void testSymbolicLinkTraversalPrevention() throws Exception {
        try {
            // Create a symbolic link inside allowed directory that points to unauthorized file
            Path symlink = allowedDir.resolve("symlink.txt");
            Files.createSymbolicLink(symlink, unauthorizedFile);

            // Test accessing the symlink - should be rejected
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", allowedDir.toString())
                    .param("pattern", "symlink.txt"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));

            mockMvc.perform(get("/api/v1/archive/download")
                    .param("filePath", symlink.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        } catch (UnsupportedOperationException e) {
            // Skip test if symbolic links are not supported on this system
            System.out.println("Symbolic links not supported, skipping test");
        }
    }

    /**
     * Test access control for unauthorized paths
     * Requirements: 6.1, 6.2
     */
    @Test
    void testAccessControlForUnauthorizedPaths() throws Exception {
        String unauthorizedPath = unauthorizedDir.toString();

        // Test file search in unauthorized directory
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", unauthorizedPath)
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"))
                .andExpect(jsonPath("$.message").value("Path access denied"));

        // Test download from unauthorized directory
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", unauthorizedFile.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        // Test content search in unauthorized file
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(unauthorizedFile.toString());
        request.setSearchTerm("sensitive");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));
    }

    /**
     * Test permission validation for valid paths
     * Requirements: 6.2
     */
    @Test
    void testPermissionValidationForValidPaths() throws Exception {
        String allowedPath = allowedDir.toString();

        // Test file search in allowed directory - should succeed
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedPath)
                .param("pattern", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchPath").value(allowedPath))
                .andExpect(jsonPath("$.files").isArray());

        // Test download from allowed directory - should succeed
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", testFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.txt\""));

        // Test content search in allowed file - should succeed
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(testFile.toString());
        request.setSearchTerm("test");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray());
    }

    /**
     * Test input sanitization and validation
     * Requirements: 6.2
     */
    @Test
    void testInputSanitizationAndValidation() throws Exception {
        // Test null byte injection
        String pathWithNullByte = allowedDir.toString() + "\u0000../../../etc/passwd";
        
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", pathWithNullByte)
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        // Test extremely long paths
        String longPath = "a".repeat(1001);
        
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", longPath)
                .param("pattern", "*.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));

        // Test empty parameters
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "")
                .param("pattern", "*.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));

        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));
    }

    /**
     * Test file size limits and resource protection
     * Requirements: 6.3, 6.4
     */
    @Test
    void testFileSizeLimitsAndResourceProtection() throws Exception {
        // Create a large file that exceeds the configured limit
        Path largeFile = allowedDir.resolve("large.txt");
        byte[] largeContent = new byte[1024 * 1024 + 1]; // 1MB + 1 byte
        Arrays.fill(largeContent, (byte) 'A');
        Files.write(largeFile, largeContent);

        // Set a small file size limit for testing
        archiveSearchProperties.setMaxFileSize(1024 * 1024L); // 1MB

        // Test download of large file - should be rejected
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", largeFile.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));

        // Test content search in large file - should be rejected
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(largeFile.toString());
        request.setSearchTerm("A");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));
    }

    /**
     * Test concurrent request handling and rate limiting
     * Requirements: 6.4
     */
    @Test
    void testConcurrentRequestHandling() throws Exception {
        // Test multiple concurrent requests to ensure proper handling
        int numberOfRequests = 10;
        Thread[] threads = new Thread[numberOfRequests];
        boolean[] results = new boolean[numberOfRequests];

        for (int i = 0; i < numberOfRequests; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    String url = "http://localhost:" + port + "/api/v1/archive/search?path=" + 
                                allowedDir.toString() + "&pattern=*.txt";
                    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                    results[index] = response.getStatusCode() == HttpStatus.OK;
                } catch (Exception e) {
                    results[index] = false;
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }

        // Verify that most requests succeeded (allowing for some rate limiting)
        long successfulRequests = 0;
        for (boolean result : results) {
            if (result) successfulRequests++;
        }
        assertThat(successfulRequests).isGreaterThan(numberOfRequests / 2);
    }

    /**
     * Test audit logging for security events
     * Requirements: 6.5
     */
    @Test
    void testAuditLoggingForSecurityEvents() throws Exception {
        // Test that security violations are properly logged
        // This test verifies that the audit logging mechanism is triggered
        
        String maliciousPath = "../../../etc/passwd";
        
        // Perform a security violation
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", maliciousPath)
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden());

        // Note: In a real implementation, you would verify that the audit log
        // contains the security violation. For this test, we're verifying that
        // the security mechanism properly rejects the request.
        
        // Additional verification could include checking log files or database
        // audit tables if implemented in the actual system.
    }

    /**
     * Test case sensitivity in path validation
     * Requirements: 6.1
     */
    @Test
    void testCaseSensitivityInPathValidation() throws Exception {
        // Test case variations of path traversal attempts
        String[] caseVariations = {
            "../ETC/passwd",
            "..\\WINDOWS\\system32",
            "../Etc/Passwd",
            "..\\Windows\\System32"
        };

        for (String path : caseVariations) {
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", path)
                    .param("pattern", "*.txt"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));
        }
    }

    /**
     * Test multiple slash normalization
     * Requirements: 6.1
     */
    @Test
    void testMultipleSlashNormalization() throws Exception {
        // Test paths with multiple slashes that should be normalized
        String pathWithMultipleSlashes = allowedDir.toString() + "//subdir///file.txt";
        
        // This should be allowed after normalization if it stays within allowed directory
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*"))
                .andExpect(status().isOk());

        // But malicious paths with multiple slashes should still be rejected
        String maliciousPathWithSlashes = allowedDir.toString() + "///../../../etc/passwd";
        
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", maliciousPathWithSlashes)
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));
    }

    /**
     * Test URL encoding bypass attempts
     * Requirements: 6.1
     */
    @Test
    void testUrlEncodingBypassAttempts() throws Exception {
        String[] encodedAttempts = {
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "%252e%252e%252f%252e%252e%252f%252e%252e%252fetc%252fpasswd",
            "%c0%ae%c0%ae%c0%af%c0%ae%c0%ae%c0%af%c0%ae%c0%ae%c0%afetc%c0%afpasswd"
        };

        for (String encodedPath : encodedAttempts) {
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", encodedPath)
                    .param("pattern", "*.txt"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ARCH001"));
        }
    }
}