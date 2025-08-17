package com.fabric.watcher.archive.controller;

import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.exception.ArchiveSearchException.ErrorCode;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.service.ArchiveSearchAuditService;
import com.fabric.watcher.archive.service.AuthenticationRateLimitService;
import com.fabric.watcher.archive.service.JwtTokenService;
import com.fabric.watcher.archive.service.LdapAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthenticationController.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

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
    private HttpSession httpSession;

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
    }

    @Test
    void testAuthenticateSuccess() {
        // Arrange
        String userId = "testuser";
        String password = "testpass";
        String ipAddress = "192.168.1.1";
        String token = "jwt-token-123";
        List<String> roles = Arrays.asList("file.read", "file.upload");
        
        AuthRequest request = new AuthRequest(userId, password);
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(userId);
        userDetails.setRoles(roles);
        
        AuthenticationResult authResult = new AuthenticationResult(userId, userDetails);
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, password)).thenReturn(authResult);
        when(jwtTokenService.generateToken(userId, roles)).thenReturn(token);
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(3600L);

        // Act
        ResponseEntity<AuthResponse> response = controller.authenticate(request, httpRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(token, response.getBody().getToken());
        assertEquals(userId, response.getBody().getUserId());
        assertEquals(3600L, response.getBody().getExpiresIn());
        assertEquals(roles, response.getBody().getPermissions());

        verify(rateLimitService).recordSuccessfulAttempt(userId, ipAddress);
        verify(auditService).logAuthentication(userId, true, ipAddress, "Authentication successful");
    }

    @Test
    void testAuthenticateFailure() {
        // Arrange
        String userId = "testuser";
        String password = "wrongpass";
        String ipAddress = "192.168.1.1";
        
        AuthRequest request = new AuthRequest(userId, password);
        AuthenticationResult authResult = new AuthenticationResult("Invalid credentials");
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, password)).thenReturn(authResult);

        // Act
        ResponseEntity<AuthResponse> response = controller.authenticate(request, httpRequest);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getToken());

        verify(rateLimitService).recordFailedAttempt(userId, ipAddress);
        verify(auditService).logAuthentication(userId, false, ipAddress, "Invalid credentials");
    }

    @Test
    void testAuthenticateUserLockedOut() {
        // Arrange
        String userId = "testuser";
        String password = "testpass";
        String ipAddress = "192.168.1.1";
        
        AuthRequest request = new AuthRequest(userId, password);
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(true);
        when(rateLimitService.getRemainingLockoutTime(userId, ipAddress)).thenReturn(900L);

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, 
            () -> controller.authenticate(request, httpRequest));
        
        assertEquals(ErrorCode.USER_LOCKED_OUT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("900 seconds"));

        verify(auditService).logAuthentication(userId, false, ipAddress, 
                                             "Account locked due to failed attempts");
        verify(ldapAuthenticationService, never()).authenticate(anyString(), anyString());
    }

    @Test
    void testAuthenticateWithXForwardedForHeader() {
        // Arrange
        String userId = "testuser";
        String password = "testpass";
        String forwardedIp = "10.0.0.1";
        String token = "jwt-token-123";
        List<String> roles = Arrays.asList("file.read");
        
        AuthRequest request = new AuthRequest(userId, password);
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(userId);
        userDetails.setRoles(roles);
        
        AuthenticationResult authResult = new AuthenticationResult(userId, userDetails);
        
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(forwardedIp + ", 192.168.1.1");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
        when(rateLimitService.isUserLockedOut(userId, forwardedIp)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, password)).thenReturn(authResult);
        when(jwtTokenService.generateToken(userId, roles)).thenReturn(token);
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(3600L);

        // Act
        ResponseEntity<AuthResponse> response = controller.authenticate(request, httpRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rateLimitService).recordSuccessfulAttempt(userId, forwardedIp);
    }

    @Test
    void testLogoutSuccess() {
        // Arrange
        String token = "jwt-token-123";
        String userId = "testuser";
        String ipAddress = "192.168.1.1";
        
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(httpRequest.getSession(false)).thenReturn(httpSession);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + token)).thenReturn(token);
        when(jwtTokenService.getUserIdFromToken(token)).thenReturn(userId);

        // Act
        ResponseEntity<Void> response = controller.logout(httpRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(httpSession).invalidate();
        verify(auditService).logAuthentication(userId, true, ipAddress, "Logout successful");
    }

    @Test
    void testLogoutWithoutToken() {
        // Arrange
        String ipAddress = "192.168.1.1";
        
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(jwtTokenService.extractTokenFromHeader(null)).thenReturn(null);

        // Act
        ResponseEntity<Void> response = controller.logout(httpRequest);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(httpSession, never()).invalidate();
        verify(auditService, never()).logAuthentication(anyString(), anyBoolean(), anyString(), anyString());
    }

    @Test
    void testLogoutWithInvalidToken() {
        // Arrange
        String token = "invalid-token";
        String ipAddress = "192.168.1.1";
        
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + token)).thenReturn(token);
        when(jwtTokenService.getUserIdFromToken(token))
            .thenThrow(new ArchiveSearchException(ErrorCode.INVALID_TOKEN, "Invalid token"));

        // Act
        ResponseEntity<Void> response = controller.logout(httpRequest);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testValidateSessionSuccess() {
        // Arrange
        String token = "jwt-token-123";
        String userId = "testuser";
        String ipAddress = "192.168.1.1";
        Long remainingTime = 1800L;
        
        // First authenticate to add token to active sessions
        AuthRequest authRequest = new AuthRequest(userId, "password");
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(userId);
        userDetails.setRoles(Arrays.asList("file.read"));
        AuthenticationResult authResult = new AuthenticationResult(userId, userDetails);
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, "password")).thenReturn(authResult);
        when(jwtTokenService.generateToken(userId, userDetails.getRoles())).thenReturn(token);
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(3600L);
        
        controller.authenticate(authRequest, httpRequest);
        
        // Now test validation
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + token)).thenReturn(token);
        when(jwtTokenService.getUserIdFromToken(token)).thenReturn(userId);
        when(jwtTokenService.getRemainingTime(token)).thenReturn(remainingTime);

        // Act
        ResponseEntity<ValidationResponse> response = controller.validateSession(httpRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isValid());
        assertEquals(userId, response.getBody().getUserId());
        assertEquals(remainingTime, response.getBody().getRemainingTime());
    }

    @Test
    void testValidateSessionWithoutToken() {
        // Arrange
        String ipAddress = "192.168.1.1";
        
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(jwtTokenService.extractTokenFromHeader(null)).thenReturn(null);

        // Act
        ResponseEntity<ValidationResponse> response = controller.validateSession(httpRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isValid());
        assertEquals("No authentication token provided", response.getBody().getMessage());
    }

    @Test
    void testValidateSessionExpiredToken() {
        // Arrange
        String token = "expired-token";
        String userId = "testuser";
        String ipAddress = "192.168.1.1";
        
        // First authenticate to add token to active sessions
        AuthRequest authRequest = new AuthRequest(userId, "password");
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(userId);
        userDetails.setRoles(Arrays.asList("file.read"));
        AuthenticationResult authResult = new AuthenticationResult(userId, userDetails);
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, "password")).thenReturn(authResult);
        when(jwtTokenService.generateToken(userId, userDetails.getRoles())).thenReturn(token);
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(3600L);
        
        controller.authenticate(authRequest, httpRequest);
        
        // Now test validation with expired token
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + token)).thenReturn(token);
        when(jwtTokenService.getUserIdFromToken(token)).thenReturn(userId);
        when(jwtTokenService.getRemainingTime(token)).thenReturn(0L);

        // Act
        ResponseEntity<ValidationResponse> response = controller.validateSession(httpRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isValid());
        assertEquals("Token expired", response.getBody().getMessage());
        
        // Verify token was removed from active sessions
        assertEquals(0, controller.getActiveSessionCount());
    }

    @Test
    void testValidateSessionNotInActiveSessions() {
        // Arrange
        String token = "unknown-token";
        String ipAddress = "192.168.1.1";
        
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(jwtTokenService.extractTokenFromHeader("Bearer " + token)).thenReturn(token);

        // Act
        ResponseEntity<ValidationResponse> response = controller.validateSession(httpRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isValid());
        assertEquals("Session not found or expired", response.getBody().getMessage());
    }

    @Test
    void testGetAuthenticationStats() {
        // Act
        ResponseEntity<Object> response = controller.getAuthenticationStats();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        verify(rateLimitService).getTrackedRecordCount();
        verify(rateLimitService).getMaxLoginAttempts();
        verify(rateLimitService).getLockoutDurationMinutes();
        verify(jwtTokenService).getTokenExpirationMinutes();
    }

    @Test
    void testAuthenticateWithJwtTokenGenerationError() {
        // Arrange
        String userId = "testuser";
        String password = "testpass";
        String ipAddress = "192.168.1.1";
        List<String> roles = Arrays.asList("file.read");
        
        AuthRequest request = new AuthRequest(userId, password);
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(userId);
        userDetails.setRoles(roles);
        
        AuthenticationResult authResult = new AuthenticationResult(userId, userDetails);
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, password)).thenReturn(authResult);
        when(jwtTokenService.generateToken(userId, roles))
            .thenThrow(new ArchiveSearchException(ErrorCode.TOKEN_GENERATION_ERROR, "Token generation failed"));

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, 
            () -> controller.authenticate(request, httpRequest));
        
        assertEquals(ErrorCode.TOKEN_GENERATION_ERROR, exception.getErrorCode());
        verify(rateLimitService).recordSuccessfulAttempt(userId, ipAddress);
    }

    @Test
    void testAuthenticateWithUnexpectedException() {
        // Arrange
        String userId = "testuser";
        String password = "testpass";
        String ipAddress = "192.168.1.1";
        
        AuthRequest request = new AuthRequest(userId, password);
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, password))
            .thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class, 
            () -> controller.authenticate(request, httpRequest));
        
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        assertEquals("Authentication service temporarily unavailable", exception.getMessage());
        
        verify(auditService).logAuthentication(userId, false, ipAddress, "System error during authentication");
    }

    @Test
    void testClearAllSessions() {
        // Arrange - authenticate a user to create a session
        String userId = "testuser";
        String password = "testpass";
        String ipAddress = "192.168.1.1";
        String token = "jwt-token-123";
        List<String> roles = Arrays.asList("file.read");
        
        AuthRequest request = new AuthRequest(userId, password);
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(userId);
        userDetails.setRoles(roles);
        
        AuthenticationResult authResult = new AuthenticationResult(userId, userDetails);
        
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);
        when(rateLimitService.isUserLockedOut(userId, ipAddress)).thenReturn(false);
        when(ldapAuthenticationService.authenticate(userId, password)).thenReturn(authResult);
        when(jwtTokenService.generateToken(userId, roles)).thenReturn(token);
        when(jwtTokenService.getTokenExpirationSeconds()).thenReturn(3600L);

        controller.authenticate(request, httpRequest);
        assertEquals(1, controller.getActiveSessionCount());

        // Act
        controller.clearAllSessions();

        // Assert
        assertEquals(0, controller.getActiveSessionCount());
    }
}