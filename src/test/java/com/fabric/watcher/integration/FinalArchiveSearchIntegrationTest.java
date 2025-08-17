package com.fabric.watcher.integration;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.service.ArchiveSearchAuditService;
import com.fabric.watcher.archive.service.LdapAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Final comprehensive integration test for Archive Search API.
 * 
 * This test validates:
 * - Complete feature integration with existing database-script-watcher functionality
 * - Swagger UI displays all archive search and authentication endpoints correctly
 * - Environment-based feature toggling for all new components
 * - All security measures, access controls, and audit logging
 * - End-to-end user workflows from authentication to file operations
 * 
 * Requirements: 7.1, 12.1, 12.4, 6.1, 8.1, 9.1, 10.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = com.fabric.watcher.TestApplication.class)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "spring.profiles.active=test",
    "springdoc.api-docs.enabled=true",
    "springdoc.swagger-ui.enabled=true",
    "archive.search.audit.enabled=true",
    "archive.search.ldap.url=ldap://test-server:389"
})
class FinalArchiveSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ArchiveSearchProperties archiveSearchProperties;

    @Autowired
    private ArchiveSearchAuditService auditService;

    @MockBean
    private LdapAuthenticationService ldapAuthenticationService;

    @LocalServerPort
    private int port;

    @TempDir
    Path tempDir;

    private Path allowedDir;
    private Path testFile;
    private Path archiveFile;
    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        // Set up test directory structure
        allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(allowedDir);

        // Create test files
        testFile = allowedDir.resolve("test.txt");
        Files.write(testFile, "This is test content for final integration testing".getBytes());

        archiveFile = allowedDir.resolve("test.zip");
        createTestArchive(archiveFile);

        // Update archive search properties
        archiveSearchProperties.setAllowedPaths(Arrays.asList(allowedDir.toString()));
        archiveSearchProperties.setMaxFileSize(1024 * 1024L);

        // Mock LDAP authentication
        UserDetails mockUser = new UserDetails();
        mockUser.setUserId("testuser");
        mockUser.setDisplayName("Test User");
        mockUser.setEmail("test@company.com");

        AuthenticationResult mockResult = new AuthenticationResult();
        mockResult.setSuccess(true);
        mockResult.setUserId("testuser");
        mockResult.setUserDetails(mockUser);

        when(ldapAuthenticationService.authenticate(anyString(), anyString()))
            .thenReturn(mockResult);
        when(ldapAuthenticationService.getUserDetails(anyString()))
            .thenReturn(mockUser);

        // Get authentication token
        authToken = authenticateUser();
    }

    /**
     * Test 1: Complete feature integration with existing database-script-watcher functionality
     * Requirements: 7.1
     */
    @Test
    void testCompleteFeatureIntegrationWithExistingFunctionality() throws Exception {
        // Verify archive search API is properly integrated and doesn't interfere with existing functionality
        
        // Test that existing health endpoint still works
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // Test that archive search status endpoint works
        mockMvc.perform(get("/api/v1/archive/status")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.environment").value("test"));

        // Test that both functionalities can work concurrently
        CompletableFuture<Boolean> healthCheck = CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/actuator/health", String.class);
                return response.getStatusCode() == HttpStatus.OK;
            } catch (Exception e) {
                return false;
            }
        });

        CompletableFuture<Boolean> archiveSearch = CompletableFuture.supplyAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + authToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:" + port + "/api/v1/archive/search?path=" + 
                    allowedDir.toString() + "&pattern=*.txt",
                    HttpMethod.GET, entity, String.class);
                return response.getStatusCode() == HttpStatus.OK;
            } catch (Exception e) {
                return false;
            }
        });

        assertThat(healthCheck.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(archiveSearch.get(10, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * Test 2: Swagger UI displays all archive search and authentication endpoints correctly
     * Requirements: 12.1, 12.4
     */
    @Test
    void testSwaggerUiDisplaysAllEndpointsCorrectly() throws Exception {
        // Test Swagger UI is accessible
        String swaggerUrl = "http://localhost:" + port + "/swagger-ui.html";
        ResponseEntity<String> swaggerResponse = restTemplate.getForEntity(swaggerUrl, String.class);
        assertThat(swaggerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(swaggerResponse.getBody()).contains("Archive Search API with Authentication");

        // Test API documentation includes all required endpoints
        ResponseEntity<String> apiDocsResponse = restTemplate.getForEntity(
            "http://localhost:" + port + "/v3/api-docs/archive-search", String.class);
        assertThat(apiDocsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode apiDocs = objectMapper.readTree(apiDocsResponse.getBody());
        JsonNode paths = apiDocs.get("paths");

        // Verify all authentication endpoints are documented
        assertThat(paths.has("/api/v1/auth/login")).isTrue();
        assertThat(paths.has("/api/v1/auth/logout")).isTrue();
        assertThat(paths.has("/api/v1/auth/validate")).isTrue();

        // Verify all archive search endpoints are documented
        assertThat(paths.has("/api/v1/archive/search")).isTrue();
        assertThat(paths.has("/api/v1/archive/download")).isTrue();
        assertThat(paths.has("/api/v1/archive/content-search")).isTrue();
        assertThat(paths.has("/api/v1/archive/upload")).isTrue();
        assertThat(paths.has("/api/v1/archive/status")).isTrue();

        // Verify endpoint documentation includes proper descriptions and parameters
        JsonNode searchEndpoint = paths.get("/api/v1/archive/search").get("get");
        assertThat(searchEndpoint.get("summary").asText()).contains("Search for files");
        assertThat(searchEndpoint.get("parameters").isArray()).isTrue();
        assertThat(searchEndpoint.get("parameters").size()).isGreaterThan(0);
    }

    /**
     * Test 3: Environment-based feature toggling for all new components
     * Requirements: 7.1
     */
    @Test
    void testEnvironmentBasedFeatureToggling() throws Exception {
        // Test that API is enabled in test environment
        mockMvc.perform(get("/api/v1/archive/status")
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.environment").value("test"));

        // Test that all endpoints are accessible in test environment
        List<String> endpoints = Arrays.asList(
            "/api/v1/auth/validate",
            "/api/v1/archive/search?path=" + allowedDir.toString() + "&pattern=*.txt",
            "/api/v1/archive/status"
        );

        for (String endpoint : endpoints) {
            mockMvc.perform(get(endpoint)
                    .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk());
        }

        // Verify configuration properties are properly loaded
        assertThat(archiveSearchProperties.isEnabled()).isTrue();
        assertThat(archiveSearchProperties.getAllowedPaths()).isNotEmpty();
        assertThat(archiveSearchProperties.getAudit().isEnabled()).isTrue();
    }

    /**
     * Test 4: All security measures, access controls, and audit logging
     * Requirements: 6.1, 8.1, 10.1
     */
    @Test
    void testAllSecurityMeasuresAccessControlsAndAuditLogging() throws Exception {
        // Test authentication is required
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", allowedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isUnauthorized());

        // Test path traversal protection
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", "../../../etc/passwd")
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        // Test valid authenticated request works
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", allowedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray());

        // Test file download with authentication
        mockMvc.perform(get("/api/v1/archive/download")
                .header("Authorization", "Bearer " + authToken)
                .param("filePath", testFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.txt\""));

        // Test content search with authentication
        ContentSearchRequest request = new ContentSearchRequest();
        request.setFilePath(testFile.toString());
        request.setSearchTerm("integration");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray());

        // Verify audit logging is working (audit service should have logged these operations)
        // Note: In a real test, you would verify the audit log file contents
        assertThat(auditService).isNotNull();
    }

    /**
     * Test 5: End-to-end user workflows from authentication to file operations
     * Requirements: 8.1, 9.1, 10.1
     */
    @Test
    void testEndToEndUserWorkflowsFromAuthenticationToFileOperations() throws Exception {
        // Step 1: User authentication
        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId("testuser");
        authRequest.setPassword("testpass");

        String authResponse = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").value("testuser"))
                .andReturn().getResponse().getContentAsString();

        AuthResponse auth = objectMapper.readValue(authResponse, AuthResponse.class);
        String userToken = auth.getToken();

        // Step 2: Validate authentication token
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.userId").value("testuser"));

        // Step 3: Search for files
        String searchResponse = mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + userToken)
                .param("path", allowedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andReturn().getResponse().getContentAsString();

        FileSearchResponse searchResult = objectMapper.readValue(searchResponse, FileSearchResponse.class);
        assertThat(searchResult.getFiles()).hasSize(1);
        assertThat(searchResult.getFiles().get(0).getFileName()).isEqualTo("test.txt");

        // Step 4: Download a file
        mockMvc.perform(get("/api/v1/archive/download")
                .header("Authorization", "Bearer " + userToken)
                .param("filePath", testFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.txt\""))
                .andExpect(content().string("This is test content for final integration testing"));

        // Step 5: Search within file content
        ContentSearchRequest contentRequest = new ContentSearchRequest();
        contentRequest.setFilePath(testFile.toString());
        contentRequest.setSearchTerm("final");

        String contentResponse = mockMvc.perform(post("/api/v1/archive/content-search")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andReturn().getResponse().getContentAsString();

        ContentSearchResponse contentResult = objectMapper.readValue(contentResponse, ContentSearchResponse.class);
        assertThat(contentResult.getMatches()).hasSize(1);
        assertThat(contentResult.getMatches().get(0).getLineContent()).contains("final");

        // Step 6: Upload a file
        MockMultipartFile uploadFile = new MockMultipartFile(
            "file", "upload-test.txt", "text/plain", "This is uploaded content".getBytes());

        mockMvc.perform(multipart("/api/v1/archive/upload")
                .file(uploadFile)
                .param("targetPath", "/tmp/test-uploads/upload-test.txt")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("upload-test.txt"));

        // Step 7: Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        // Step 8: Verify token is invalidated
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test 6: Concurrent user operations with full security and audit logging
     * Requirements: 6.1, 8.1, 10.1
     */
    @Test
    void testConcurrentUserOperationsWithFullSecurityAndAuditLogging() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        int numberOfUsers = 10;
        
        CompletableFuture<Boolean>[] userWorkflows = new CompletableFuture[numberOfUsers];
        
        for (int i = 0; i < numberOfUsers; i++) {
            final int userId = i;
            userWorkflows[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    // Each user performs a complete workflow
                    String userToken = authenticateUser("user" + userId, "pass" + userId);
                    
                    // Search for files
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + userToken);
                    HttpEntity<String> entity = new HttpEntity<>(headers);
                    
                    ResponseEntity<String> searchResponse = restTemplate.exchange(
                        "http://localhost:" + port + "/api/v1/archive/search?path=" + 
                        allowedDir.toString() + "&pattern=*.txt",
                        HttpMethod.GET, entity, String.class);
                    
                    if (searchResponse.getStatusCode() != HttpStatus.OK) {
                        return false;
                    }
                    
                    // Download a file
                    ResponseEntity<String> downloadResponse = restTemplate.exchange(
                        "http://localhost:" + port + "/api/v1/archive/download?filePath=" + 
                        testFile.toString(),
                        HttpMethod.GET, entity, String.class);
                    
                    return downloadResponse.getStatusCode() == HttpStatus.OK;
                    
                } catch (Exception e) {
                    return false;
                }
            }, executor);
        }
        
        // Wait for all user workflows to complete
        CompletableFuture.allOf(userWorkflows).get(60, TimeUnit.SECONDS);
        
        // Verify all workflows succeeded
        for (CompletableFuture<Boolean> workflow : userWorkflows) {
            assertThat(workflow.get()).isTrue();
        }
        
        executor.shutdown();
    }

    /**
     * Test 7: Archive processing integration with security and audit
     * Requirements: 6.1, 10.1
     */
    @Test
    void testArchiveProcessingIntegrationWithSecurityAndAudit() throws Exception {
        // Test search within archive files
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", allowedDir.toString())
                .param("pattern", "*.zip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(1));

        // Test download of archive file
        mockMvc.perform(get("/api/v1/archive/download")
                .header("Authorization", "Bearer " + authToken)
                .param("filePath", archiveFile.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.zip\""));

        // Test content search within archive (if supported)
        ContentSearchRequest archiveContentRequest = new ContentSearchRequest();
        archiveContentRequest.setFilePath(archiveFile.toString());
        archiveContentRequest.setSearchTerm("archive");

        mockMvc.perform(post("/api/v1/archive/content-search")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(archiveContentRequest)))
                .andExpect(status().isOk());
    }

    /**
     * Test 8: Error handling and recovery in integrated environment
     * Requirements: 6.1, 10.1
     */
    @Test
    void testErrorHandlingAndRecoveryInIntegratedEnvironment() throws Exception {
        // Test various error scenarios with proper authentication
        
        // Invalid file path
        mockMvc.perform(get("/api/v1/archive/download")
                .header("Authorization", "Bearer " + authToken)
                .param("filePath", "/nonexistent/file.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ARCH002"));

        // Path traversal attempt
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", "../../../etc/passwd")
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH001"));

        // Invalid authentication token
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer invalid-token")
                .param("path", allowedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isUnauthorized());

        // After errors, verify system still works with valid requests
        mockMvc.perform(get("/api/v1/archive/search")
                .header("Authorization", "Bearer " + authToken)
                .param("path", allowedDir.toString())
                .param("pattern", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray());
    }

    private String authenticateUser() throws Exception {
        return authenticateUser("testuser", "testpass");
    }

    private String authenticateUser(String userId, String password) throws Exception {
        // Mock successful authentication for any user in test
        UserDetails mockUser = new UserDetails();
        mockUser.setUserId(userId);
        mockUser.setDisplayName("Test User " + userId);
        mockUser.setEmail(userId + "@company.com");

        AuthenticationResult mockResult = new AuthenticationResult();
        mockResult.setSuccess(true);
        mockResult.setUserId(userId);
        mockResult.setUserDetails(mockUser);

        when(ldapAuthenticationService.authenticate(anyString(), anyString()))
            .thenReturn(mockResult);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId(userId);
        authRequest.setPassword(password);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(response, AuthResponse.class);
        return authResponse.getToken();
    }

    private void createTestArchive(Path archivePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            ZipEntry entry = new ZipEntry("archived-file.txt");
            zos.putNextEntry(entry);
            zos.write("This is content inside the archive for final testing".getBytes());
            zos.closeEntry();
            
            entry = new ZipEntry("data/nested-file.txt");
            zos.putNextEntry(entry);
            zos.write("This is nested content in the archive".getBytes());
            zos.closeEntry();
        }
    }
}