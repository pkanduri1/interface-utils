package com.fabric.watcher.integration;

import com.fabric.watcher.archive.model.AuthRequest;
import com.fabric.watcher.archive.service.ArchiveSearchAuditService;
import com.fabric.watcher.archive.service.LdapAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for LDAP authentication flow and session management.
 * Tests authentication endpoints, session handling, and concurrent user scenarios.
 * 
 * Requirements: 8.1, 9.1, 10.1, 10.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "spring.profiles.active=test",
    "archive.search.ldap.url=ldap://test-server:389",
    "archive.search.ldap.base-dn=dc=test,dc=com",
    "archive.search.ldap.user-search-base=ou=users",
    "archive.search.ldap.user-search-filter=(sAMAccountName={0})",
    "archive.search.audit.log-file=/tmp/test-audit.log"
})
class ArchiveSearchAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LdapAuthenticationService ldapAuthenticationService;

    @Autowired
    private ArchiveSearchAuditService auditService;

    @TempDir
    Path tempDir;

    private Path auditLogFile;

    @BeforeEach
    void setUp() throws IOException {
        // Set up audit log file
        auditLogFile = tempDir.resolve("test-audit.log");
        Files.createFile(auditLogFile);

        // Reset mocks
        reset(ldapAuthenticationService);
    }

    /**
     * Test successful LDAP authentication flow
     * Requirements: 8.1
     */
    @Test
    void testSuccessfulLdapAuthenticationFlow() throws Exception {
        // Mock successful authentication
        when(ldapAuthenticationService.authenticate(eq("testuser"), eq("password123")))
                .thenReturn(createSuccessfulAuthResult("testuser"));

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId("testuser");
        authRequest.setPassword("password123");

        // Test login endpoint
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").value("testuser"))
                .andExpect(jsonPath("$.expiresIn").exists())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseContent).get("token").asText();

        // Test session validation with valid token
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.userId").value("testuser"));

        // Verify authentication was called
        verify(ldapAuthenticationService).authenticate("testuser", "password123");
    }

    /**
     * Test failed LDAP authentication scenarios
     * Requirements: 8.1
     */
    @Test
    void testFailedLdapAuthenticationScenarios() throws Exception {
        // Test with invalid credentials
        when(ldapAuthenticationService.authenticate(eq("testuser"), eq("wrongpassword")))
                .thenReturn(createFailedAuthResult("Invalid credentials"));

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId("testuser");
        authRequest.setPassword("wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH001"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        // Test with non-existent user
        when(ldapAuthenticationService.authenticate(eq("nonexistent"), eq("password")))
                .thenReturn(createFailedAuthResult("User not found"));

        authRequest.setUserId("nonexistent");
        authRequest.setPassword("password");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User not found"));

        // Test with empty credentials
        authRequest.setUserId("");
        authRequest.setPassword("");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH002"));

        // Test with null credentials
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test rate limiting for failed authentication attempts
     * Requirements: 8.1
     */
    @Test
    void testRateLimitingForFailedAuthentication() throws Exception {
        when(ldapAuthenticationService.authenticate(eq("testuser"), anyString()))
                .thenReturn(createFailedAuthResult("Invalid credentials"));

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId("testuser");
        authRequest.setPassword("wrongpassword");

        // Make multiple failed attempts
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(authRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // Next attempt should be rate limited
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AUTH003"))
                .andExpect(jsonPath("$.message").value("Too many failed login attempts. Please try again later."));
    }

    /**
     * Test session timeout and token expiration
     * Requirements: 8.1
     */
    @Test
    void testSessionTimeoutAndTokenExpiration() throws Exception {
        // Mock successful authentication
        when(ldapAuthenticationService.authenticate(eq("testuser"), eq("password123")))
                .thenReturn(createSuccessfulAuthResult("testuser"));

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId("testuser");
        authRequest.setPassword("password123");

        // Login and get token
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();

        // Test immediate validation (should work)
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // Test with expired token (simulate by using invalid token)
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH004"))
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    /**
     * Test logout functionality
     * Requirements: 8.1
     */
    @Test
    void testLogoutFunctionality() throws Exception {
        // Mock successful authentication
        when(ldapAuthenticationService.authenticate(eq("testuser"), eq("password123")))
                .thenReturn(createSuccessfulAuthResult("testuser"));

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId("testuser");
        authRequest.setPassword("password123");

        // Login and get token
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();

        // Test logout
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        // Test that token is invalidated after logout
        mockMvc.perform(get("/api/v1/auth/validate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH004"));
    }

    /**
     * Test concurrent user authentication scenarios
     * Requirements: 8.1, 10.4
     */
    @Test
    void testConcurrentUserAuthenticationScenarios() throws Exception {
        // Mock successful authentication for multiple users
        when(ldapAuthenticationService.authenticate(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String userId = invocation.getArgument(0);
                    return createSuccessfulAuthResult(userId);
                });

        ExecutorService executor = Executors.newFixedThreadPool(10);
        int numberOfUsers = 20;
        
        CompletableFuture<Boolean>[] futures = new CompletableFuture[numberOfUsers];
        
        // Simulate concurrent login attempts from different users
        for (int i = 0; i < numberOfUsers; i++) {
            final int userIndex = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    AuthRequest authRequest = new AuthRequest();
                    authRequest.setUserId("user" + userIndex);
                    authRequest.setPassword("password" + userIndex);

                    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(authRequest)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.token").exists())
                            .andExpect(jsonPath("$.userId").value("user" + userIndex))
                            .andReturn();

                    // Validate the token immediately
                    String token = objectMapper.readTree(result.getResponse().getContentAsString())
                            .get("token").asText();

                    mockMvc.perform(get("/api/v1/auth/validate")
                            .header("Authorization", "Bearer " + token))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.valid").value(true));

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
     * Test authentication with different user roles and permissions
     * Requirements: 8.1
     */
    @Test
    void testAuthenticationWithDifferentUserRolesAndPermissions() throws Exception {
        // Test admin user
        when(ldapAuthenticationService.authenticate(eq("admin"), eq("adminpass")))
                .thenReturn(createSuccessfulAuthResultWithRoles("admin", "admin", "file.read", "file.upload", "file.download"));

        AuthRequest adminRequest = new AuthRequest();
        adminRequest.setUserId("admin");
        adminRequest.setPassword("adminpass");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("admin"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItems("admin", "file.read", "file.upload", "file.download")));

        // Test regular user
        when(ldapAuthenticationService.authenticate(eq("user"), eq("userpass")))
                .thenReturn(createSuccessfulAuthResultWithRoles("user", "file.read", "file.download"));

        AuthRequest userRequest = new AuthRequest();
        userRequest.setUserId("user");
        userRequest.setPassword("userpass");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItems("file.read", "file.download")));
    }

    /**
     * Test authentication error handling and recovery
     * Requirements: 8.1
     */
    @Test
    void testAuthenticationErrorHandlingAndRecovery() throws Exception {
        // Test LDAP service unavailable
        when(ldapAuthenticationService.authenticate(anyString(), anyString()))
                .thenThrow(new RuntimeException("LDAP service unavailable"));

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUserId("testuser");
        authRequest.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("AUTH005"))
                .andExpect(jsonPath("$.message").value("Authentication service temporarily unavailable"));

        // Test recovery - service becomes available again
        when(ldapAuthenticationService.authenticate(eq("testuser"), eq("password123")))
                .thenReturn(createSuccessfulAuthResult("testuser"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("testuser"));
    }

    /**
     * Test malformed authentication requests
     * Requirements: 8.1
     */
    @Test
    void testMalformedAuthenticationRequests() throws Exception {
        // Test with malformed JSON
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .andExpect(status().isBadRequest());

        // Test with missing content type
        mockMvc.perform(post("/api/v1/auth/login")
                .content("{\"userId\":\"test\",\"password\":\"pass\"}"))
                .andExpect(status().isUnsupportedMediaType());

        // Test with SQL injection attempt in userId
        AuthRequest maliciousRequest = new AuthRequest();
        maliciousRequest.setUserId("'; DROP TABLE users; --");
        maliciousRequest.setPassword("password");

        when(ldapAuthenticationService.authenticate(anyString(), anyString()))
                .thenReturn(createFailedAuthResult("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maliciousRequest)))
                .andExpect(status().isUnauthorized());

        // Verify the malicious input was passed to the service (which should handle it safely)
        verify(ldapAuthenticationService).authenticate("'; DROP TABLE users; --", "password");
    }

    /**
     * Helper method to create successful authentication result
     */
    private com.fabric.watcher.archive.model.AuthenticationResult createSuccessfulAuthResult(String userId) {
        com.fabric.watcher.archive.model.UserDetails userDetails = new com.fabric.watcher.archive.model.UserDetails();
        userDetails.setUserId(userId);
        userDetails.setDisplayName("Test User " + userId);
        userDetails.setEmail(userId + "@test.com");
        userDetails.setRoles(java.util.Arrays.asList("file.read", "file.download"));
        
        return new com.fabric.watcher.archive.model.AuthenticationResult(userId, userDetails);
    }

    /**
     * Helper method to create successful authentication result with specific roles
     */
    private com.fabric.watcher.archive.model.AuthenticationResult createSuccessfulAuthResultWithRoles(String userId, String... roles) {
        com.fabric.watcher.archive.model.UserDetails userDetails = new com.fabric.watcher.archive.model.UserDetails();
        userDetails.setUserId(userId);
        userDetails.setDisplayName("Test User " + userId);
        userDetails.setEmail(userId + "@test.com");
        userDetails.setRoles(java.util.Arrays.asList(roles));
        
        return new com.fabric.watcher.archive.model.AuthenticationResult(userId, userDetails);
    }

    /**
     * Helper method to create failed authentication result
     */
    private com.fabric.watcher.archive.model.AuthenticationResult createFailedAuthResult(String errorMessage) {
        return new com.fabric.watcher.archive.model.AuthenticationResult(errorMessage);
    }
}