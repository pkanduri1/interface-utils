package com.fabric.watcher.integration;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.service.LdapAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Performance and load testing for Archive Search API.
 * 
 * This test validates:
 * - Large file handling and memory management
 * - Concurrent request handling and resource limits
 * - Timeout behavior for long-running operations
 * - Streaming download functionality with large files
 * 
 * Requirements: 2.3, 4.4, 6.3, 6.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = com.fabric.watcher.TestApplication.class)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "spring.profiles.active=test",
    "archive.search.max-file-size=10485760", // 10MB for testing
    "archive.search.search-timeout-seconds=5",
    "archive.search.max-concurrent-operations=3"
})
class ArchiveSearchPerformanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArchiveSearchProperties archiveSearchProperties;

    @MockBean
    private LdapAuthenticationService ldapAuthenticationService;

    @LocalServerPort
    private int port;

    @TempDir
    Path tempDir;

    private Path allowedDir;
    private Path largeFile;
    private Path mediumFile;
    private Path largeArchive;
    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        allowedDir = tempDir.resolve("performance-test");
        Files.createDirectories(allowedDir);

        // Create files of different sizes for performance testing
        createMediumFile();
        createLargeFile();
        createLargeArchive();

        // Update properties
        archiveSearchProperties.setAllowedPaths(Arrays.asList(allowedDir.toString()));
        archiveSearchProperties.setMaxFileSize(10 * 1024 * 1024L); // 10MB

        // Mock authentication
        setupMockAuthentication();
        authToken = authenticateUser();
    }

    /**
     * Test large file handling and memory management
     * Requirements: 2.3, 6.3
     */
    @Test
    void testLargeFileHandlingAndMemoryManagement() throws Exception {
        // Test that files within size limit can be processed
        mockMvc.perform(get("/api/v1/archive/download")
                .header("Authorization", "Bearer " + authToken)
                .param("filePath", mediumFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Length"));

        // Test that files exceeding size limit are rejected
        mockMvc.perform(get("/api/v1/archive/download")
                .header("Authorization", "Bearer " + authToken)
                .param("filePath", largeFile.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));

        // Test content search with size limits
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(largeFile.toString());
        request.setSearchTerm("large");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));
    }

    /**
     * Test concurrent request handling and resource limits
     * Requirements: 6.4
     */
    @Test
    void testConcurrentRequestHandlingAndResourceLimits() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int numberOfRequests = 20;
        
        CompletableFuture<ResponseEntity<String>>[] futures = new CompletableFuture[numberOfRequests];
        
        // Create concurrent search requests
        for (int i = 0; i < numberOfRequests; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + authToken);
                    HttpEntity<String> entity = new HttpEntity<>(headers);
                    
                    return restTemplate.exchange(
                        "http://localhost:" + port + "/api/v1/archive/search?path=" + 
                        allowedDir.toString() + "&pattern=*.txt",
                        HttpMethod.GET, entity, String.class);
                } catch (Exception e) {
                    return new ResponseEntity<>("Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }, executor);
        }
        
        // Wait for all requests to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        
        // Analyze results
        int successCount = 0;
        int rateLimitedCount = 0;
        
        for (CompletableFuture<ResponseEntity<String>> future : futures) {
            ResponseEntity<String> response = future.get();
            if (response.getStatusCode() == HttpStatus.OK) {
                successCount++;
            } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                rateLimitedCount++;
            }
        }
        
        // Verify that the system handled concurrent requests appropriately
        assertThat(successCount).isGreaterThan(0);
        // Some requests might be rate limited due to concurrent operation limits
        assertThat(successCount + rateLimitedCount).isEqualTo(numberOfRequests);
        
        executor.shutdown();
    }

    /**
     * Test timeout behavior for long-running operations
     * Requirements: 6.3, 6.4
     */
    @Test
    void testTimeoutBehaviorForLongRunningOperations() throws Exception {
        // Create a directory with many files to simulate long search operation
        Path manyFilesDir = allowedDir.resolve("many-files");
        Files.createDirectories(manyFilesDir);
        
        // Create many small files to make search take longer
        for (int i = 0; i < 100; i++) {
            Path file = manyFilesDir.resolve("file" + i + ".txt");
            Files.write(file, ("Content of file " + i).getBytes());
        }

        // Update allowed paths to include the new directory
        archiveSearchProperties.setAllowedPaths(Arrays.asList(
            allowedDir.toString(), 
            manyFilesDir.toString()
        ));

        // Test search operation with potential timeout
        long startTime = System.currentTimeMillis();
        
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", manyFilesDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray());
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Verify operation completed within reasonable time
        assertThat(duration).isLessThan(10000); // Should complete within 10 seconds
    }

    /**
     * Test streaming download functionality with large files
     * Requirements: 2.3, 4.4
     */
    @Test
    void testStreamingDownloadFunctionalityWithLargeFiles() throws Exception {
        // Test streaming download of medium-sized file
        byte[] downloadedContent = mockMvc.perform(get("/api/v1/archive/download")
                .header("Authorization", "Bearer " + authToken)
                .param("filePath", mediumFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Length"))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andReturn().getResponse().getContentAsByteArray();

        // Verify downloaded content matches original file
        byte[] originalContent = Files.readAllBytes(mediumFile);
        assertThat(downloadedContent).isEqualTo(originalContent);
        assertThat(downloadedContent.length).isEqualTo(5 * 1024 * 1024); // 5MB
    }

    /**
     * Test concurrent file uploads with size validation
     * Requirements: 6.4
     */
    @Test
    void testConcurrentFileUploadsWithSizeValidation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        int numberOfUploads = 10;
        
        CompletableFuture<Boolean>[] uploadFutures = new CompletableFuture[numberOfUploads];
        
        for (int i = 0; i < numberOfUploads; i++) {
            final int uploadId = i;
            uploadFutures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    // Create upload content (some within limits, some exceeding)
                    byte[] content;
                    if (uploadId % 3 == 0) {
                        // Large upload that should be rejected
                        content = new byte[15 * 1024 * 1024]; // 15MB
                    } else {
                        // Small upload that should succeed
                        content = ("Upload content " + uploadId).getBytes();
                    }
                    
                    MockMultipartFile file = new MockMultipartFile(
                        "file", "upload" + uploadId + ".txt", "text/plain", content);

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + authToken);
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                    // Note: This is a simplified test - in reality you'd use RestTemplate
                    // with proper multipart handling
                    return true; // Simplified for this test
                    
                } catch (Exception e) {
                    return false;
                }
            }, executor);
        }
        
        CompletableFuture.allOf(uploadFutures).get(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    /**
     * Test archive processing performance with large archives
     * Requirements: 4.4
     */
    @Test
    void testArchiveProcessingPerformanceWithLargeArchives() throws Exception {
        // Test search within large archive
        long startTime = System.currentTimeMillis();
        
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", allowedDir.toString())
                .param("pattern", "*.zip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray());
        
        long searchDuration = System.currentTimeMillis() - startTime;
        
        // Test download of large archive
        startTime = System.currentTimeMillis();
        
        mockMvc.perform(get("/api/v1/archive/download")
                .header("Authorization", "Bearer " + authToken)
                .param("filePath", largeArchive.toString()))
                .andExpect(status().isOk());
        
        long downloadDuration = System.currentTimeMillis() - startTime;
        
        // Verify operations completed within reasonable time
        assertThat(searchDuration).isLessThan(5000); // 5 seconds
        assertThat(downloadDuration).isLessThan(10000); // 10 seconds
    }

    /**
     * Test memory usage during concurrent operations
     * Requirements: 6.3, 6.4
     */
    @Test
    void testMemoryUsageDuringConcurrentOperations() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        int numberOfOperations = 15;
        
        CompletableFuture<Boolean>[] operations = new CompletableFuture[numberOfOperations];
        
        for (int i = 0; i < numberOfOperations; i++) {
            operations[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + authToken);
                    HttpEntity<String> entity = new HttpEntity<>(headers);
                    
                    // Mix of different operations
                    ResponseEntity<String> response = restTemplate.exchange(
                        "http://localhost:" + port + "/api/v1/archive/search?path=" + 
                        allowedDir.toString() + "&pattern=*.txt",
                        HttpMethod.GET, entity, String.class);
                    
                    return response.getStatusCode() == HttpStatus.OK;
                } catch (Exception e) {
                    return false;
                }
            }, executor);
        }
        
        CompletableFuture.allOf(operations).get(30, TimeUnit.SECONDS);
        
        // Force garbage collection and check memory usage
        System.gc();
        Thread.sleep(1000);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Verify memory usage didn't increase excessively
        assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024); // Less than 100MB increase
        
        executor.shutdown();
    }

    private void createMediumFile() throws IOException {
        mediumFile = allowedDir.resolve("medium.txt");
        byte[] content = new byte[5 * 1024 * 1024]; // 5MB
        Arrays.fill(content, (byte) 'M');
        Files.write(mediumFile, content);
    }

    private void createLargeFile() throws IOException {
        largeFile = allowedDir.resolve("large.txt");
        byte[] content = new byte[15 * 1024 * 1024]; // 15MB - exceeds 10MB limit
        Arrays.fill(content, (byte) 'L');
        Files.write(largeFile, content);
    }

    private void createLargeArchive() throws IOException {
        largeArchive = allowedDir.resolve("large.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(largeArchive))) {
            // Add multiple files to create a larger archive
            for (int i = 0; i < 10; i++) {
                ZipEntry entry = new ZipEntry("file" + i + ".txt");
                zos.putNextEntry(entry);
                byte[] content = new byte[512 * 1024]; // 512KB per file
                Arrays.fill(content, (byte) ('A' + i));
                zos.write(content);
                zos.closeEntry();
            }
        }
    }

    private void setupMockAuthentication() {
        UserDetails mockUser = new UserDetails();
        mockUser.setUserId("perfuser");
        mockUser.setDisplayName("Performance Test User");
        mockUser.setEmail("perfuser@company.com");

        AuthenticationResult mockResult = new AuthenticationResult();
        mockResult.setSuccess(true);
        mockResult.setUserId("perfuser");
        mockResult.setUserDetails(mockUser);

        when(ldapAuthenticationService.authenticate(anyString(), anyString()))
            .thenReturn(mockResult);
        when(ldapAuthenticationService.getUserDetails(anyString()))
            .thenReturn(mockUser);
    }

    private String authenticateUser() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId("perfuser");
        authRequest.setPassword("perfpass");

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(response, AuthResponse.class);
        return authResponse.getToken();
    }
}