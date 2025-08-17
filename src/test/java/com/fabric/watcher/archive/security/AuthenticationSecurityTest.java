package com.fabric.watcher.archive.security;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.controller.AuthenticationController;
import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.service.ArchiveSearchAuditService;
import com.fabric.watcher.archive.service.AuthenticationRateLimitService;
import com.fabric.watcher.archive.service.JwtTokenService;
import com.fabric.watcher.archive.service.LdapAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive security tests for authentication functionality.
 * Tests LDAP authentication security, rate limiting, session management, and authorization.
 * 
 * Requirements: 8.2, 8.3, 11.1, 11.3
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationSecurityTest {

    @Mock
    private LdapAuthenticationService ldapAuthenticationService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private AuthenticationRateLimitService rateLimitService;

    @Mock
    private ArchiveSearchAuditService auditService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private ArchiveSearchProperties properties;

    @Mock
    private ArchiveSearchProperties.SecurityConfig securityConfig;

    private AuthenticationController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        controller = new AuthenticationController(
            ldapAuthenticationService,
            jwtTokenService,
            rateLimitService,
            auditService
        );
        
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();

        // Setup default security configuration
        when(properties.getSecurity()).thenReturn(securityConfig);
        when(securityConfig.getMaxLoginAttempts()).thenReturn(3);
        when(securityConfig.getLockoutDurationMinutes()).thenReturn(15);
        when(securityConfig.getSessionTimeoutMinutes()).thenReturn(30);
    }

    /**
     * Test LDAP authentication with various invalid credential scenarios
     * Requirements: 8.2
     */
    @Test
    void testLdapAuthenticationWithInvalidCredentials() {
        String ipAddress = "192.168.1.1";
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(anyString(), anyString())).thenReturn(false);

        // Test 1: Wrong password
        AuthRequest wrongPasswordRequest = new AuthRequest("validuser", "wrongpassword");
        when(ldapAuthenticationService.authenticate("validuser", "wrongpassword"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        ResponseEntity<AuthResponse> response1 = controller.authenticate(wrongPasswordRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response1.getStatusCode());
        verify(rateLimitService).recordFailedAttempt("validuser", ipAddress);
        verify(auditService).logAuthentication("validuser", false, ipAddress, "Invalid credentials");

        // Test 2: Non-existent user
        AuthRequest nonExistentUserRequest = new AuthRequest("nonexistentuser", "password");
        when(ldapAuthenticationService.authenticate("nonexistentuser", "password"))
                .thenReturn(new AuthenticationResult("User not found"));

        ResponseEntity<AuthResponse> response2 = controller.authenticate(nonExistentUserRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response2.getStatusCode());
        verify(rateLimitService).recordFailedAttempt("nonexistentuser", ipAddress);
        verify(auditService).logAuthentication("nonexistentuser", false, ipAddress, "User not found");

        // Test 3: Empty credentials
        AuthRequest emptyCredsRequest = new AuthRequest("", "");
        ArchiveSearchException exception1 = assertThrows(ArchiveSearchException.class,
                () -> controller.authenticate(emptyCredsRequest, httpRequest));
        assertEquals(ArchiveSearchException.ErrorCode.INVALID_REQUEST, exception1.getErrorCode());

        // Test 4: Null credentials
        AuthRequest nullCredsRequest = new AuthRequest(null, null);
        ArchiveSearchException exception2 = assertThrows(ArchiveSearchException.class,
                () -> controller.authenticate(nullCredsRequest, httpRequest));
        assertEquals(ArchiveSearchException.ErrorCode.INVALID_REQUEST, exception2.getErrorCode());

        // Test 5: SQL injection attempt in username
        AuthRequest sqlInjectionRequest = new AuthRequest("'; DROP TABLE users; --", "password");
        when(ldapAuthenticationService.authenticate("'; DROP TABLE users; --", "password"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        ResponseEntity<AuthResponse> response3 = controller.authenticate(sqlInjectionRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response3.getStatusCode());
        verify(rateLimitService).recordFailedAttempt("'; DROP TABLE users; --", ipAddress);

        // Test 6: LDAP injection attempt
        AuthRequest ldapInjectionRequest = new AuthRequest("user)(|(password=*))", "password");
        when(ldapAuthenticationService.authenticate("user)(|(password=*)", "password"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        ResponseEntity<AuthResponse> response4 = controller.authenticate(ldapInjectionRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response4.getStatusCode());
        verify(rateLimitService).recordFailedAttempt("user)(|(password=*)", ipAddress);

        // Test 7: Very long username (potential buffer overflow)
        String longUsername = "a".repeat(1000);
        AuthRequest longUsernameRequest = new AuthRequest(longUsername, "password");
        when(ldapAuthenticationService.authenticate(longUsername, "password"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        ResponseEntity<AuthResponse> response5 = controller.authenticate(longUsernameRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response5.getStatusCode());
        verify(rateLimitService).recordFailedAttempt(longUsername, ipAddress);

        // Test 8: Special characters in credentials
        AuthRequest specialCharsRequest = new AuthRequest("user@domain.com", "p@ssw0rd!#$%");
        when(ldapAuthenticationService.authenticate("user@domain.com", "p@ssw0rd!#$%"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        ResponseEntity<AuthResponse> response6 = controller.authenticate(specialCharsRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response6.getStatusCode());
        verify(rateLimitService).recordFailedAttempt("user@domain.com", ipAddress);
    }

    /**
     * Test comprehensive rate limiting scenarios for failed authentication attempts
     * Requirements: 8.3, 11.3
     */
    @Test
    void testRateLimitingForFailedAuthenticationAttempts() {
        String userId = "testuser";
        String ipAddress = "192.168.1.1";
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);

        // Setup failed authentication response
        when(ldapAuthenticationService.authenticate(userId, "wrongpassword"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        AuthRequest request = new AuthRequest(userId, "wrongpassword");

        // Test 1: First failed attempt - should not be locked
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        ResponseEntity<AuthResponse> response1 = controller.authenticate(request, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response1.getStatusCode());
        verify(rateLimitService).recordFailedAttempt(userId, ipAddress);

        // Test 2: Second failed attempt - still not locked
        ResponseEntity<AuthResponse> response2 = controller.authenticate(request, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response2.getStatusCode());
        verify(rateLimitService, times(2)).recordFailedAttempt(userId, ipAddress);

        // Test 3: Third failed attempt - still not locked but at threshold
        ResponseEntity<AuthResponse> response3 = controller.authenticate(request, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response3.getStatusCode());
        verify(rateLimitService, times(3)).recordFailedAttempt(userId, ipAddress);

        // Test 4: Fourth attempt - should be locked out
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(true);
        when(rateLimitService.getRemainingLockoutTime(userId, ipAddress)).thenReturn(900L); // 15 minutes

        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class,
                () -> controller.authenticate(request, httpRequest));
        assertEquals(ArchiveSearchException.ErrorCode.USER_LOCKED_OUT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("900 seconds"));
        verify(auditService).logAuthentication(userId, false, ipAddress, "Account locked due to failed attempts");

        // Test 5: Different IP address should not be affected
        String differentIp = "192.168.1.2";
        when(httpRequest.getRemoteAddr()).thenReturn(differentIp);
        when(rateLimitService.isUserLockedOut(userId, differentIp)).thenReturn(false);

        ResponseEntity<AuthResponse> response4 = controller.authenticate(request, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response4.getStatusCode());
        verify(rateLimitService).recordFailedAttempt(userId, differentIp);

        // Test 6: Different user from same IP should not be affected
        String differentUser = "differentuser";
        AuthRequest differentUserRequest = new AuthRequest(differentUser, "wrongpassword");
        when(ldapAuthenticationService.authenticate(differentUser, "wrongpassword"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));
        when(rateLimitService.isUserLockedOut(differentUser, ipAddress)).thenReturn(false);
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);

        ResponseEntity<AuthResponse> response5 = controller.authenticate(differentUserRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response5.getStatusCode());
        verify(rateLimitService).recordFailedAttempt(differentUser, ipAddress);

        // Test 7: Successful authentication should reset failed attempts
        when(rateLimitService.isUserLockedOut(userId, differentIp)).thenReturn(false);
        AuthRequest successRequest = new AuthRequest(userId, "correctpassword");
        UserDetails userDetails = createTestUserDetails(userId);
        when(ldapAuthenticationService.authenticate(userId, "correctpassword"))
                .thenReturn(new AuthenticationResult(userId, userDetails));
        when(jwtTokenService.generateToken(userId, userDetails.getRoles())).thenReturn("valid-token");
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(1800L);
        when(httpRequest.getRemoteAddr()).thenReturn(differentIp);

        ResponseEntity<AuthResponse> successResponse = controller.authenticate(successRequest, httpRequest);
        assertEquals(HttpStatus.OK, successResponse.getStatusCode());
        verify(rateLimitService).recordSuccessfulAttempt(userId, differentIp);
    }

    /**
     * Test session timeout and token expiration handling scenarios
     * Requirements: 8.2, 8.3
     */
    @Test
    void testSessionTimeoutAndTokenExpirationHandling() {
        String userId = "testuser";
        String ipAddress = "192.168.1.1";
        String validToken = "valid-token-123";
        String expiredToken = "expired-token-456";

        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);

        // Test 1: Valid token with remaining time
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + validToken)).thenReturn(validToken);
        when(jwtTokenService.getUserIdFromToken(validToken)).thenReturn(userId);
        when(jwtTokenService.getRemainingTime(validToken)).thenReturn(1800L); // 30 minutes

        // First authenticate to add token to active sessions
        authenticateUser(userId, validToken);

        ResponseEntity<ValidationResponse> validResponse = controller.validateSession(httpRequest);
        assertEquals(HttpStatus.OK, validResponse.getStatusCode());
        assertTrue(validResponse.getBody().isValid());
        assertEquals(userId, validResponse.getBody().getUserId());
        assertEquals(1800L, validResponse.getBody().getRemainingTime());

        // Test 2: Token with very little time remaining (< 5 minutes)
        when(jwtTokenService.getRemainingTime(validToken)).thenReturn(200L); // 3.33 minutes

        ResponseEntity<ValidationResponse> shortTimeResponse = controller.validateSession(httpRequest);
        assertEquals(HttpStatus.OK, shortTimeResponse.getStatusCode());
        assertTrue(shortTimeResponse.getBody().isValid());
        assertTrue(shortTimeResponse.getBody().getMessage().contains("expires soon"));

        // Test 3: Expired token (0 remaining time)
        when(jwtTokenService.getRemainingTime(validToken)).thenReturn(0L);

        ResponseEntity<ValidationResponse> expiredResponse = controller.validateSession(httpRequest);
        assertEquals(HttpStatus.OK, expiredResponse.getStatusCode());
        assertFalse(expiredResponse.getBody().isValid());
        assertEquals("Token expired", expiredResponse.getBody().getMessage());

        // Test 4: Negative remaining time (token expired)
        when(jwtTokenService.getRemainingTime(validToken)).thenReturn(-100L);

        ResponseEntity<ValidationResponse> negativeTimeResponse = controller.validateSession(httpRequest);
        assertEquals(HttpStatus.OK, negativeTimeResponse.getStatusCode());
        assertFalse(negativeTimeResponse.getBody().isValid());
        assertEquals("Token expired", negativeTimeResponse.getBody().getMessage());

        // Test 5: Invalid token format
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + expiredToken)).thenReturn(expiredToken);
        when(jwtTokenService.getUserIdFromToken(expiredToken))
                .thenThrow(new ArchiveSearchException(ArchiveSearchException.ErrorCode.INVALID_TOKEN, "Invalid token"));

        ResponseEntity<ValidationResponse> invalidTokenResponse = controller.validateSession(httpRequest);
        assertEquals(HttpStatus.OK, invalidTokenResponse.getStatusCode());
        assertFalse(invalidTokenResponse.getBody().isValid());
        assertEquals("Invalid token", invalidTokenResponse.getBody().getMessage());

        // Test 6: Token not in active sessions (session expired on server)
        String unknownToken = "unknown-token-789";
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + unknownToken);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + unknownToken)).thenReturn(unknownToken);
        when(jwtTokenService.getUserIdFromToken(unknownToken)).thenReturn(userId);
        when(jwtTokenService.getRemainingTime(unknownToken)).thenReturn(1800L);

        ResponseEntity<ValidationResponse> unknownTokenResponse = controller.validateSession(httpRequest);
        assertEquals(HttpStatus.OK, unknownTokenResponse.getStatusCode());
        assertFalse(unknownTokenResponse.getBody().isValid());
        assertEquals("Session not found or expired", unknownTokenResponse.getBody().getMessage());

        // Test 7: Malformed Authorization header
        when(httpRequest.getHeader("Authorization")).thenReturn("InvalidFormat token123");
        when(jwtTokenService.extractTokenFromHeader("InvalidFormat token123")).thenReturn(null);

        ResponseEntity<ValidationResponse> malformedHeaderResponse = controller.validateSession(httpRequest);
        assertEquals(HttpStatus.OK, malformedHeaderResponse.getStatusCode());
        assertFalse(malformedHeaderResponse.getBody().isValid());
        assertEquals("No authentication token provided", malformedHeaderResponse.getBody().getMessage());

        // Test 8: Missing Authorization header
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(jwtTokenService.extractTokenFromHeader(null)).thenReturn(null);

        ResponseEntity<ValidationResponse> noHeaderResponse = controller.validateSession(httpRequest);
        assertEquals(HttpStatus.OK, noHeaderResponse.getStatusCode());
        assertFalse(noHeaderResponse.getBody().isValid());
        assertEquals("No authentication token provided", noHeaderResponse.getBody().getMessage());
    }

    /**
     * Test authorization for different user roles and operations
     * Requirements: 8.2, 11.1
     */
    @Test
    void testAuthorizationForDifferentUserRolesAndOperations() {
        String ipAddress = "192.168.1.1";
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(anyString(), anyString())).thenReturn(false);

        // Test 1: Admin user with full permissions
        String adminUser = "admin";
        List<String> adminRoles = Arrays.asList("admin", "file.read", "file.upload", "file.download", "system.manage");
        UserDetails adminDetails = createTestUserDetailsWithRoles(adminUser, adminRoles);
        
        when(ldapAuthenticationService.authenticate(adminUser, "adminpass"))
                .thenReturn(new AuthenticationResult(adminUser, adminDetails));
        when(jwtTokenService.generateToken(adminUser, adminRoles)).thenReturn("admin-token");
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(1800L);

        AuthRequest adminRequest = new AuthRequest(adminUser, "adminpass");
        ResponseEntity<AuthResponse> adminResponse = controller.authenticate(adminRequest, httpRequest);
        
        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
        assertEquals(adminUser, adminResponse.getBody().getUserId());
        assertEquals(adminRoles, adminResponse.getBody().getPermissions());
        assertTrue(adminResponse.getBody().getPermissions().contains("admin"));
        assertTrue(adminResponse.getBody().getPermissions().contains("file.upload"));
        assertTrue(adminResponse.getBody().getPermissions().contains("system.manage"));

        // Test 2: Regular user with limited permissions
        String regularUser = "user";
        List<String> userRoles = Arrays.asList("file.read", "file.download");
        UserDetails userDetails = createTestUserDetailsWithRoles(regularUser, userRoles);
        
        when(ldapAuthenticationService.authenticate(regularUser, "userpass"))
                .thenReturn(new AuthenticationResult(regularUser, userDetails));
        when(jwtTokenService.generateToken(regularUser, userRoles)).thenReturn("user-token");

        AuthRequest userRequest = new AuthRequest(regularUser, "userpass");
        ResponseEntity<AuthResponse> userResponse = controller.authenticate(userRequest, httpRequest);
        
        assertEquals(HttpStatus.OK, userResponse.getStatusCode());
        assertEquals(regularUser, userResponse.getBody().getUserId());
        assertEquals(userRoles, userResponse.getBody().getPermissions());
        assertTrue(userResponse.getBody().getPermissions().contains("file.read"));
        assertTrue(userResponse.getBody().getPermissions().contains("file.download"));
        assertFalse(userResponse.getBody().getPermissions().contains("file.upload"));
        assertFalse(userResponse.getBody().getPermissions().contains("admin"));

        // Test 3: Read-only user
        String readOnlyUser = "readonly";
        List<String> readOnlyRoles = Arrays.asList("file.read");
        UserDetails readOnlyDetails = createTestUserDetailsWithRoles(readOnlyUser, readOnlyRoles);
        
        when(ldapAuthenticationService.authenticate(readOnlyUser, "readpass"))
                .thenReturn(new AuthenticationResult(readOnlyUser, readOnlyDetails));
        when(jwtTokenService.generateToken(readOnlyUser, readOnlyRoles)).thenReturn("readonly-token");

        AuthRequest readOnlyRequest = new AuthRequest(readOnlyUser, "readpass");
        ResponseEntity<AuthResponse> readOnlyResponse = controller.authenticate(readOnlyRequest, httpRequest);
        
        assertEquals(HttpStatus.OK, readOnlyResponse.getStatusCode());
        assertEquals(readOnlyUser, readOnlyResponse.getBody().getUserId());
        assertEquals(readOnlyRoles, readOnlyResponse.getBody().getPermissions());
        assertTrue(readOnlyResponse.getBody().getPermissions().contains("file.read"));
        assertFalse(readOnlyResponse.getBody().getPermissions().contains("file.download"));
        assertFalse(readOnlyResponse.getBody().getPermissions().contains("file.upload"));

        // Test 4: User with no roles (should still authenticate but with empty permissions)
        String noRoleUser = "noroles";
        List<String> noRoles = Collections.emptyList();
        UserDetails noRoleDetails = createTestUserDetailsWithRoles(noRoleUser, noRoles);
        
        when(ldapAuthenticationService.authenticate(noRoleUser, "nopass"))
                .thenReturn(new AuthenticationResult(noRoleUser, noRoleDetails));
        when(jwtTokenService.generateToken(noRoleUser, noRoles)).thenReturn("norole-token");

        AuthRequest noRoleRequest = new AuthRequest(noRoleUser, "nopass");
        ResponseEntity<AuthResponse> noRoleResponse = controller.authenticate(noRoleRequest, httpRequest);
        
        assertEquals(HttpStatus.OK, noRoleResponse.getStatusCode());
        assertEquals(noRoleUser, noRoleResponse.getBody().getUserId());
        assertTrue(noRoleResponse.getBody().getPermissions().isEmpty());

        // Test 5: Service account with specific permissions
        String serviceUser = "service-account";
        List<String> serviceRoles = Arrays.asList("file.upload", "system.monitor");
        UserDetails serviceDetails = createTestUserDetailsWithRoles(serviceUser, serviceRoles);
        
        when(ldapAuthenticationService.authenticate(serviceUser, "servicepass"))
                .thenReturn(new AuthenticationResult(serviceUser, serviceDetails));
        when(jwtTokenService.generateToken(serviceUser, serviceRoles)).thenReturn("service-token");

        AuthRequest serviceRequest = new AuthRequest(serviceUser, "servicepass");
        ResponseEntity<AuthResponse> serviceResponse = controller.authenticate(serviceRequest, httpRequest);
        
        assertEquals(HttpStatus.OK, serviceResponse.getStatusCode());
        assertEquals(serviceUser, serviceResponse.getBody().getUserId());
        assertEquals(serviceRoles, serviceResponse.getBody().getPermissions());
        assertTrue(serviceResponse.getBody().getPermissions().contains("file.upload"));
        assertTrue(serviceResponse.getBody().getPermissions().contains("system.monitor"));
        assertFalse(serviceResponse.getBody().getPermissions().contains("file.read"));

        // Test 6: Test LDAP authorization service integration
        when(ldapAuthenticationService.isUserAuthorized(adminUser, "file.upload")).thenReturn(true);
        when(ldapAuthenticationService.isUserAuthorized(regularUser, "file.upload")).thenReturn(false);
        when(ldapAuthenticationService.isUserAuthorized(readOnlyUser, "file.read")).thenReturn(true);
        when(ldapAuthenticationService.isUserAuthorized(readOnlyUser, "file.upload")).thenReturn(false);

        assertTrue(ldapAuthenticationService.isUserAuthorized(adminUser, "file.upload"));
        assertFalse(ldapAuthenticationService.isUserAuthorized(regularUser, "file.upload"));
        assertTrue(ldapAuthenticationService.isUserAuthorized(readOnlyUser, "file.read"));
        assertFalse(ldapAuthenticationService.isUserAuthorized(readOnlyUser, "file.upload"));
    }

    /**
     * Test concurrent authentication attempts and security under load
     * Requirements: 8.3, 11.3
     */
    @Test
    void testConcurrentAuthenticationSecurity() throws Exception {
        String baseUserId = "user";
        String ipAddress = "192.168.1.1";
        int numberOfConcurrentAttempts = 50;
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);

        // Setup failed authentication for all attempts
        when(ldapAuthenticationService.authenticate(anyString(), eq("wrongpassword")))
                .thenReturn(new AuthenticationResult("Invalid credentials"));
        when(rateLimitService.isUserLockedOut(anyString(), anyString())).thenReturn(false);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CompletableFuture<Boolean>[] futures = new CompletableFuture[numberOfConcurrentAttempts];

        // Launch concurrent failed authentication attempts
        for (int i = 0; i < numberOfConcurrentAttempts; i++) {
            final int userIndex = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    String userId = baseUserId + userIndex;
                    AuthRequest request = new AuthRequest(userId, "wrongpassword");
                    ResponseEntity<AuthResponse> response = controller.authenticate(request, httpRequest);
                    return response.getStatusCode() == HttpStatus.UNAUTHORIZED;
                } catch (Exception e) {
                    return false;
                }
            }, executor);
        }

        // Wait for all attempts to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

        // Verify all attempts failed as expected
        for (CompletableFuture<Boolean> future : futures) {
            assertTrue(future.get(), "All concurrent authentication attempts should fail");
        }

        // Verify rate limiting service was called for each attempt
        verify(rateLimitService, times(numberOfConcurrentAttempts)).recordFailedAttempt(anyString(), eq(ipAddress));
        verify(auditService, times(numberOfConcurrentAttempts)).logAuthentication(anyString(), eq(false), eq(ipAddress), eq("Invalid credentials"));

        executor.shutdown();
    }

    /**
     * Test security logging and audit trail for authentication events
     * Requirements: 11.1, 11.3
     */
    @Test
    void testSecurityLoggingAndAuditTrail() {
        String userId = "testuser";
        String ipAddress = "192.168.1.1";
        String maliciousIp = "10.0.0.1";
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(anyString(), anyString())).thenReturn(false);

        // Test 1: Successful authentication logging
        UserDetails userDetails = createTestUserDetails(userId);
        when(ldapAuthenticationService.authenticate(userId, "correctpass"))
                .thenReturn(new AuthenticationResult(userId, userDetails));
        when(jwtTokenService.generateToken(userId, userDetails.getRoles())).thenReturn("valid-token");
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(1800L);

        AuthRequest successRequest = new AuthRequest(userId, "correctpass");
        controller.authenticate(successRequest, httpRequest);

        verify(auditService).logAuthentication(userId, true, ipAddress, "Authentication successful");
        verify(rateLimitService).recordSuccessfulAttempt(userId, ipAddress);

        // Test 2: Failed authentication logging
        when(ldapAuthenticationService.authenticate(userId, "wrongpass"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        AuthRequest failRequest = new AuthRequest(userId, "wrongpass");
        controller.authenticate(failRequest, httpRequest);

        verify(auditService).logAuthentication(userId, false, ipAddress, "Invalid credentials");
        verify(rateLimitService).recordFailedAttempt(userId, ipAddress);

        // Test 3: Account lockout logging
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(true);
        when(rateLimitService.getRemainingLockoutTime(userId, ipAddress)).thenReturn(900L);

        assertThrows(ArchiveSearchException.class, 
                () -> controller.authenticate(failRequest, httpRequest));

        verify(auditService).logAuthentication(userId, false, ipAddress, "Account locked due to failed attempts");

        // Test 4: Suspicious activity logging (multiple IPs)
        when(httpRequest.getRemoteAddr()).thenReturn(maliciousIp);
        when(rateLimitService.isUserLockedOut(userId, maliciousIp)).thenReturn(false);

        controller.authenticate(failRequest, httpRequest);

        verify(auditService).logAuthentication(userId, false, maliciousIp, "Invalid credentials");
        verify(rateLimitService).recordFailedAttempt(userId, maliciousIp);

        // Test 5: System error logging
        when(ldapAuthenticationService.authenticate(userId, "systemerror"))
                .thenThrow(new RuntimeException("LDAP connection failed"));

        AuthRequest errorRequest = new AuthRequest(userId, "systemerror");
        assertThrows(ArchiveSearchException.class, 
                () -> controller.authenticate(errorRequest, httpRequest));

        verify(auditService).logAuthentication(userId, false, maliciousIp, "System error during authentication");

        // Test 6: Logout logging
        String token = "valid-token-123";
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + token)).thenReturn(token);
        when(jwtTokenService.getUserIdFromToken(token)).thenReturn(userId);

        // First authenticate to create session
        authenticateUser(userId, token);

        controller.logout(httpRequest);

        verify(auditService).logAuthentication(userId, true, maliciousIp, "Logout successful");
    }

    /**
     * Test edge cases and boundary conditions in authentication security
     * Requirements: 8.2, 8.3
     */
    @Test
    void testAuthenticationSecurityEdgeCases() {
        String ipAddress = "192.168.1.1";
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);

        // Test 1: Extremely long username (potential DoS)
        String longUsername = "a".repeat(10000);
        when(rateLimitService.isUserLockedOut(longUsername, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(longUsername, "password"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        AuthRequest longUsernameRequest = new AuthRequest(longUsername, "password");
        ResponseEntity<AuthResponse> response1 = controller.authenticate(longUsernameRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response1.getStatusCode());

        // Test 2: Unicode characters in credentials
        String unicodeUsername = "用户名";
        String unicodePassword = "密码";
        when(rateLimitService.isUserLockedOut(unicodeUsername, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(unicodeUsername, unicodePassword))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        AuthRequest unicodeRequest = new AuthRequest(unicodeUsername, unicodePassword);
        ResponseEntity<AuthResponse> response2 = controller.authenticate(unicodeRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response2.getStatusCode());

        // Test 3: Control characters in credentials
        String controlCharUsername = "user\u0000\u0001\u0002";
        when(rateLimitService.isUserLockedOut(controlCharUsername, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(controlCharUsername, "password"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        AuthRequest controlCharRequest = new AuthRequest(controlCharUsername, "password");
        ResponseEntity<AuthResponse> response3 = controller.authenticate(controlCharRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response3.getStatusCode());

        // Test 4: Whitespace-only credentials
        String whitespaceUsername = "   ";
        when(rateLimitService.isUserLockedOut(whitespaceUsername, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(whitespaceUsername, "password"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        AuthRequest whitespaceRequest = new AuthRequest(whitespaceUsername, "password");
        ResponseEntity<AuthResponse> response4 = controller.authenticate(whitespaceRequest, httpRequest);
        assertEquals(HttpStatus.UNAUTHORIZED, response4.getStatusCode());

        // Test 5: Case sensitivity in usernames
        String upperCaseUser = "TESTUSER";
        String lowerCaseUser = "testuser";
        when(rateLimitService.isUserLockedOut(upperCaseUser, ipAddress)).thenReturn(false);
        when(rateLimitService.isUserLockedOut(lowerCaseUser, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(upperCaseUser, "password"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));
        when(ldapAuthenticationService.authenticate(lowerCaseUser, "password"))
                .thenReturn(new AuthenticationResult("Invalid credentials"));

        AuthRequest upperCaseRequest = new AuthRequest(upperCaseUser, "password");
        AuthRequest lowerCaseRequest = new AuthRequest(lowerCaseUser, "password");
        
        controller.authenticate(upperCaseRequest, httpRequest);
        controller.authenticate(lowerCaseRequest, httpRequest);

        // Verify both are treated as separate users for rate limiting
        verify(rateLimitService).recordFailedAttempt(upperCaseUser, ipAddress);
        verify(rateLimitService).recordFailedAttempt(lowerCaseUser, ipAddress);
    }

    // Helper methods

    private UserDetails createTestUserDetails(String userId) {
        return createTestUserDetailsWithRoles(userId, Arrays.asList("file.read", "file.download"));
    }

    private UserDetails createTestUserDetailsWithRoles(String userId, List<String> roles) {
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(userId);
        userDetails.setDisplayName("Test User " + userId);
        userDetails.setEmail(userId + "@test.com");
        userDetails.setDepartment("IT");
        userDetails.setRoles(roles);
        return userDetails;
    }

    private void authenticateUser(String userId, String token) {
        String ipAddress = "192.168.1.1";
        UserDetails userDetails = createTestUserDetails(userId);
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, "password"))
                .thenReturn(new AuthenticationResult(userId, userDetails));
        when(jwtTokenService.generateToken(userId, userDetails.getRoles())).thenReturn(token);
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(1800L);

        AuthRequest request = new AuthRequest(userId, "password");
        controller.authenticate(request, httpRequest);
    }
}