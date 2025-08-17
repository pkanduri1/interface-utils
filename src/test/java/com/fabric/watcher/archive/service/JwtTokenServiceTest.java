package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.exception.ArchiveSearchException.ErrorCode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtTokenService.
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    @Mock
    private ArchiveSearchProperties properties;

    @Mock
    private ArchiveSearchProperties.SecurityConfig securityConfig;

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        when(properties.getSecurity()).thenReturn(securityConfig);
        when(securityConfig.getSessionTimeoutMinutes()).thenReturn(30);
        
        jwtTokenService = new JwtTokenService(properties);
    }

    @Test
    void testGenerateToken() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read", "file.upload");

        // Act
        String token = jwtTokenService.generateToken(userId, permissions);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."));
    }

    @Test
    void testGenerateTokenWithEmptyUserId() {
        // Arrange
        String userId = "";
        List<String> permissions = Arrays.asList("file.read");

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class,
            () -> jwtTokenService.generateToken(userId, permissions));
        
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("User ID cannot be empty", exception.getMessage());
    }

    @Test
    void testGenerateTokenWithNullUserId() {
        // Arrange
        String userId = null;
        List<String> permissions = Arrays.asList("file.read");

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class,
            () -> jwtTokenService.generateToken(userId, permissions));
        
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("User ID cannot be empty", exception.getMessage());
    }

    @Test
    void testValidateToken() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read", "file.upload");
        String token = jwtTokenService.generateToken(userId, permissions);

        // Act
        Claims claims = jwtTokenService.validateToken(token);

        // Assert
        assertNotNull(claims);
        assertEquals(userId, claims.getSubject());
        assertEquals(userId, claims.get("userId", String.class));
        assertEquals("archive-search-api", claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void testValidateTokenWithEmptyToken() {
        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class,
            () -> jwtTokenService.validateToken(""));
        
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
        assertEquals("Token cannot be empty", exception.getMessage());
    }

    @Test
    void testValidateTokenWithNullToken() {
        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class,
            () -> jwtTokenService.validateToken(null));
        
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
        assertEquals("Token cannot be empty", exception.getMessage());
    }

    @Test
    void testValidateTokenWithInvalidToken() {
        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class,
            () -> jwtTokenService.validateToken("invalid.token.here"));
        
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void testGetUserIdFromToken() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read");
        String token = jwtTokenService.generateToken(userId, permissions);

        // Act
        String extractedUserId = jwtTokenService.getUserIdFromToken(token);

        // Assert
        assertEquals(userId, extractedUserId);
    }

    @Test
    void testGetPermissionsFromToken() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read", "file.upload");
        String token = jwtTokenService.generateToken(userId, permissions);

        // Act
        List<String> extractedPermissions = jwtTokenService.getPermissionsFromToken(token);

        // Assert
        assertEquals(permissions, extractedPermissions);
    }

    @Test
    void testGetRemainingTime() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read");
        String token = jwtTokenService.generateToken(userId, permissions);

        // Act
        Long remainingTime = jwtTokenService.getRemainingTime(token);

        // Assert
        assertNotNull(remainingTime);
        assertTrue(remainingTime > 0);
        assertTrue(remainingTime <= 1800); // 30 minutes in seconds
    }

    @Test
    void testGetRemainingTimeWithInvalidToken() {
        // Act
        Long remainingTime = jwtTokenService.getRemainingTime("invalid.token");

        // Assert
        assertNull(remainingTime);
    }

    @Test
    void testIsTokenExpired() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read");
        String token = jwtTokenService.generateToken(userId, permissions);

        // Act
        boolean isExpired = jwtTokenService.isTokenExpired(token);

        // Assert
        assertFalse(isExpired);
    }

    @Test
    void testIsTokenExpiredWithInvalidToken() {
        // Act
        boolean isExpired = jwtTokenService.isTokenExpired("invalid.token");

        // Assert
        assertTrue(isExpired);
    }

    @Test
    void testExtractTokenFromHeader() {
        // Arrange
        String token = "jwt-token-123";
        String authHeader = "Bearer " + token;

        // Act
        String extractedToken = jwtTokenService.extractTokenFromHeader(authHeader);

        // Assert
        assertEquals(token, extractedToken);
    }

    @Test
    void testExtractTokenFromHeaderWithoutBearer() {
        // Arrange
        String authHeader = "jwt-token-123";

        // Act
        String extractedToken = jwtTokenService.extractTokenFromHeader(authHeader);

        // Assert
        assertNull(extractedToken);
    }

    @Test
    void testExtractTokenFromHeaderWithNull() {
        // Act
        String extractedToken = jwtTokenService.extractTokenFromHeader(null);

        // Assert
        assertNull(extractedToken);
    }

    @Test
    void testExtractTokenFromHeaderWithEmptyString() {
        // Act
        String extractedToken = jwtTokenService.extractTokenFromHeader("");

        // Assert
        assertNull(extractedToken);
    }

    @Test
    void testGetTokenExpirationMinutes() {
        // Act
        long expirationMinutes = jwtTokenService.getTokenExpirationMinutes();

        // Assert
        assertEquals(30, expirationMinutes);
    }

    @Test
    void testGetTokenExpirationSeconds() {
        // Act
        long expirationSeconds = jwtTokenService.getTokenExpirationSeconds();

        // Assert
        assertEquals(1800, expirationSeconds); // 30 minutes * 60 seconds
    }

    @Test
    void testTokenWithNullSecurityConfig() {
        // Arrange
        when(properties.getSecurity()).thenReturn(null);
        JwtTokenService serviceWithNullConfig = new JwtTokenService(properties);

        // Act
        long expirationMinutes = serviceWithNullConfig.getTokenExpirationMinutes();

        // Assert
        assertEquals(30, expirationMinutes); // Should use default value
    }

    @Test
    void testGenerateAndValidateTokenRoundTrip() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read", "file.upload", "admin");

        // Act
        String token = jwtTokenService.generateToken(userId, permissions);
        Claims claims = jwtTokenService.validateToken(token);
        String extractedUserId = jwtTokenService.getUserIdFromToken(token);
        List<String> extractedPermissions = jwtTokenService.getPermissionsFromToken(token);
        Long remainingTime = jwtTokenService.getRemainingTime(token);
        boolean isExpired = jwtTokenService.isTokenExpired(token);

        // Assert
        assertNotNull(token);
        assertNotNull(claims);
        assertEquals(userId, extractedUserId);
        assertEquals(permissions, extractedPermissions);
        assertNotNull(remainingTime);
        assertTrue(remainingTime > 0);
        assertFalse(isExpired);
        assertEquals(userId, claims.getSubject());
        assertEquals("archive-search-api", claims.getIssuer());
    }

    @Test
    void testTokenValidationWithDifferentService() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read");
        String token = jwtTokenService.generateToken(userId, permissions);

        // Create a new service instance (different secret key)
        JwtTokenService differentService = new JwtTokenService(properties);

        // Act & Assert
        ArchiveSearchException exception = assertThrows(ArchiveSearchException.class,
            () -> differentService.validateToken(token));
        
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void testGenerateTokenWithEmptyPermissions() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = Arrays.asList();

        // Act
        String token = jwtTokenService.generateToken(userId, permissions);

        // Assert
        assertNotNull(token);
        
        List<String> extractedPermissions = jwtTokenService.getPermissionsFromToken(token);
        assertEquals(permissions, extractedPermissions);
        assertTrue(extractedPermissions.isEmpty());
    }

    @Test
    void testGenerateTokenWithNullPermissions() {
        // Arrange
        String userId = "testuser";
        List<String> permissions = null;

        // Act
        String token = jwtTokenService.generateToken(userId, permissions);

        // Assert
        assertNotNull(token);
        
        List<String> extractedPermissions = jwtTokenService.getPermissionsFromToken(token);
        assertNull(extractedPermissions);
    }
}