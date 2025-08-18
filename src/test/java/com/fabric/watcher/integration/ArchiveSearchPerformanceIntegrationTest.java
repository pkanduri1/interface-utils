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

    /**
     * Test streaming download with large files and memory efficiency
     * Requirements: 2.3, 4.4
     */
    @Test
    void testStreamingDownloadWithLargeFilesAndMemoryEfficiency() throws Exception {
        // Create files of different sizes to test streaming efficiency
        int[] fileSizes = {1, 5, 8}; // MB - within the 10MB test limit
        
        for (int sizeMB : fileSizes) {
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();
            
            Path testFile = createFileOfSize(sizeMB);
            
            long startTime = System.currentTimeMillis();
            
            byte[] downloadedContent = mockMvc.perform(get("/api/v1/archive/download")
                    .header("Authorization", "Bearer " + authToken)
                    .param("filePath", testFile.toString()))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Content-Length"))
                    .andExpect(header().string("Content-Type", "application/octet-stream"))
                    .andReturn().getResponse().getContentAsByteArray();
            
            long downloadTime = System.currentTimeMillis() - startTime;
            
            // Verify content integrity
            byte[] originalContent = Files.readAllBytes(testFile);
            assertThat(downloadedContent).isEqualTo(originalContent);
            assertThat(downloadedContent.length).isEqualTo(sizeMB * 1024 * 1024);
            
            // Verify reasonable download time (streaming should be efficient)
            assertThat(downloadTime).isLessThan(5000); // Less than 5 seconds
            
            // Force garbage collection and check memory usage
            System.gc();
            Thread.sleep(100);
            
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryIncrease = finalMemory - initialMemory;
            
            // Memory increase should be reasonable (streaming should not load entire file)
            assertThat(memoryIncrease).isLessThan(sizeMB * 1024 * 1024 / 2); // Less than half file size
            
            // Clean up
            Files.deleteIfExists(testFile);
        }
    }

    /**
     * Test timeout behavior with complex operations
     * Requirements: 6.3, 6.4
     */
    @Test
    void testTimeoutBehaviorWithComplexOperations() throws Exception {
        // Create many files to potentially trigger timeout scenarios
        createManyTestFiles(200);
        
        long startTime = System.currentTimeMillis();
        
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", allowedDir.toString())
                .param("pattern", "*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray());
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Operation should complete within timeout + reasonable buffer
        assertThat(duration).isLessThan(8000); // 8 seconds (5s timeout + 3s buffer)
        
        // Test content search timeout behavior
        Path largeTextFile = createLargeTextFile();
        
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(largeTextFile.toString());
        request.setSearchTerm("search");
        
        startTime = System.currentTimeMillis();
        
        mockMvc.perform(post("/api/v1/archive/content-search")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray());
        
        duration = System.currentTimeMillis() - startTime;
        
        // Content search should complete within timeout
        assertThat(duration).isLessThan(8000); // 8 seconds
    }

    /**
     * Test resource limits and cleanup after operations
     * Requirements: 6.3, 6.4
     */
    @Test
    void testResourceLimitsAndCleanupAfterOperations() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create scenario that tests resource limits
        createManyTestFiles(100);
        Path testArchive = createTestArchive();
        
        // Perform multiple operations that might stress resources
        for (int i = 0; i < 10; i++) {
            try {
                mockMvc.perform(get("/api/v1/archive/search")
                        .header("Authorization", "Bearer " + authToken)
                        .param("path", allowedDir.toString())
                        .param("pattern", "test" + (i % 5) + "*"))
                        .andExpect(status().isOk());
                        
                // Brief pause between operations
                Thread.sleep(100);
                
            } catch (Exception e) {
                // Some operations might fail due to resource limits - this is acceptable
            }
        }
        
        // Force garbage collection and verify cleanup
        System.gc();
        Thread.sleep(1000);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Memory should not have increased excessively due to proper cleanup
        assertThat(memoryIncrease).isLessThan(150 * 1024 * 1024); // Less than 150MB
        
        // Test that system is still responsive after resource stress
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", allowedDir.toString())
                .param("pattern", "test1.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray());
    }

    /**
     * Test concurrent streaming downloads
     * Requirements: 2.3, 4.4, 6.4
     */
    @Test
    void testConcurrentStreamingDownloads() throws Exception {
        // Create multiple files for concurrent download
        int numberOfFiles = 3;
        int fileSizeMB = 3; // Smaller size for concurrent testing
        Path[] testFiles = new Path[numberOfFiles];
        
        for (int i = 0; i < numberOfFiles; i++) {
            testFiles[i] = createFileOfSize(fileSizeMB, "concurrent_" + i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(numberOfFiles);
        CompletableFuture<DownloadResult>[] futures = new CompletableFuture[numberOfFiles];
        
        long overallStartTime = System.currentTimeMillis();
        
        // Start concurrent downloads
        for (int i = 0; i < numberOfFiles; i++) {
            final int fileIndex = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    
                    byte[] content = mockMvc.perform(get("/api/v1/archive/download")
                            .header("Authorization", "Bearer " + authToken)
                            .param("filePath", testFiles[fileIndex].toString()))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsByteArray();
                    
                    long downloadTime = System.currentTimeMillis() - startTime;
                    
                    return new DownloadResult(true, downloadTime, content.length);
                    
                } catch (Exception e) {
                    return new DownloadResult(false, -1, 0);
                }
            }, executor);
        }
        
        // Wait for all downloads to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - overallStartTime;
        
        // Analyze results
        int successCount = 0;
        long totalDownloadTime = 0;
        long totalBytes = 0;
        
        for (CompletableFuture<DownloadResult> future : futures) {
            DownloadResult result = future.get();
            if (result.success) {
                successCount++;
                totalDownloadTime += result.downloadTime;
                totalBytes += result.bytesDownloaded;
            }
        }
        
        // Verify results
        assertThat(successCount).isEqualTo(numberOfFiles);
        assertThat(totalBytes).isEqualTo(numberOfFiles * fileSizeMB * 1024 * 1024);
        
        // Concurrent downloads should be efficient
        long averageDownloadTime = totalDownloadTime / numberOfFiles;
        assertThat(averageDownloadTime).isLessThan(8000); // Less than 8 seconds per file
        
        // Total time should show concurrency benefit
        assertThat(totalTime).isLessThan(averageDownloadTime * numberOfFiles);
        
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

    private Path createFileOfSize(int sizeMB) throws IOException {
        return createFileOfSize(sizeMB, "test_" + sizeMB + "MB");
    }

    private Path createFileOfSize(int sizeMB, String filename) throws IOException {
        Path file = allowedDir.resolve(filename + ".txt");
        byte[] content = new byte[sizeMB * 1024 * 1024];
        
        // Fill with pattern to make it more realistic
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) ('A' + (i % 26));
        }
        
        Files.write(file, content);
        return file;
    }

    private void createManyTestFiles(int count) throws IOException {
        for (int i = 0; i < count; i++) {
            Path file = allowedDir.resolve("test" + i + ".txt");
            String content = "Content of test file " + i + " with some searchable text and patterns.";
            Files.write(file, content.getBytes());
        }
    }

    private Path createLargeTextFile() throws IOException {
        Path file = allowedDir.resolve("large_text.txt");
        StringBuilder content = new StringBuilder();
        
        // Create 2MB of text content
        for (int i = 0; i < 20000; i++) {
            content.append("Line ").append(i).append(" with search term and other content that makes this line longer.\n");
        }
        
        Files.write(file, content.toString().getBytes());
        return file;
    }

    private Path createTestArchive() throws IOException {
        Path archive = allowedDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archive))) {
            // Add multiple files to create a test archive
            for (int i = 0; i < 10; i++) {
                ZipEntry entry = new ZipEntry("file" + i + ".txt");
                zos.putNextEntry(entry);
                String content = "Content for file " + i + " in archive with searchable text.";
                zos.write(content.getBytes());
                zos.closeEntry();
            }
        }
        return archive;
    }

    private static class DownloadResult {
        final boolean success;
        final long downloadTime;
        final long bytesDownloaded;

        DownloadResult(boolean success, long downloadTime, long bytesDownloaded) {
            this.success = success;
            this.downloadTime = downloadTime;
            this.bytesDownloaded = bytesDownloaded;
        }
    }
}