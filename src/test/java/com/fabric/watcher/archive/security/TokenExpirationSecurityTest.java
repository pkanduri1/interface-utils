package com.fabric.watcher.archive.security;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive security tests for JWT token expiration and validation.
 * Tests various token expiration scenarios, edge cases, and security vulnerabilities.
 * 
 * Requirements: 8.2, 8.3, 11.1
 */
@ExtendWith(MockitoExtension.class)
class TokenExpirationSecurityTest {

    @Mock
    private ArchiveSearchProperties properties;

    @Mock
    private ArchiveSearchProperties.SecurityConfig securityConfig;

    private JwtTokenService jwtTokenService;
    private SecretKey testSecretKey;

    @BeforeEach
    void setUp() {
        when(properties.getSecurity()).thenReturn(securityConfig);
        when(securityConfig.getSessionTimeoutMinutes()).thenReturn(30);
        
        jwtTokenService = new JwtTokenService(properties);
        
        // Create a test secret key for manual token creation
        testSecretKey = Keys.hmacShaKeyFor("test-secret-key-for-jwt-token-generation-minimum-256-bits".getBytes());
    }

    /**
     * Test token expiration validation with various time scenarios
     * Requirements: 8.2, 8.3
     */
    @Test
    void testTokenExpirationValidation() {
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read", "file.upload");

        // Test 1: Fresh token (just created)
        String freshToken = jwtTokenService.generateToken(userId, permissions);
        assertFalse(jwtTokenService.isTokenExpired(freshToken));
        
        Long remainingTime = jwtTokenService.getRemainingTime(freshToken);
        assertNotNull(remainingTime);
        assertTrue(remainingTime > 1790); // Should be close to 30 minutes (1800 seconds)
        assertTrue(remainingTime <= 1800);

        // Test 2: Token with custom expiration (1 minute)
        when(securityConfig.getSessionTimeoutMinutes()).thenReturn(1);
        JwtTokenService shortExpiryService = new JwtTokenService(properties);
        
        String shortToken = shortExpiryService.generateToken(userId, permissions);
        Long shortRemainingTime = shortExpiryService.getRemainingTime(shortToken);
        assertNotNull(shortRemainingTime);
        assertTrue(shortRemainingTime <= 60); // Should be 1 minute or less

        // Test 3: Manually create expired token
        String expiredToken = createExpiredToken(userId, permissions);
        assertTrue(jwtTokenService.isTokenExpired(expiredToken));
        
        Long expiredRemainingTime = jwtTokenService.getRemainingTime(expiredToken);
        assertTrue(expiredRemainingTime == null || expiredRemainingTime <= 0);

        // Test 4: Token expiring in 1 second
        String almostExpiredToken = createTokenWithCustomExpiry(userId, permissions, 1);
        assertFalse(jwtTokenService.isTokenExpired(almostExpiredToken));
        
        // Wait for token to expire
        try {
            Thread.sleep(1100); // Wait 1.1 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertTrue(jwtTokenService.isTokenExpired(almostExpiredToken));

        // Test 5: Token with future expiration (1 hour)
        String longToken = createTokenWithCustomExpiry(userId, permissions, 3600);
        assertFalse(jwtTokenService.isTokenExpired(longToken));
        
        Long longRemainingTime = jwtTokenService.getRemainingTime(longToken);
        assertNotNull(longRemainingTime);
        assertTrue(longRemainingTime > 3590); // Should be close to 1 hour
    }

    /**
     * Test token validation with malformed and tampered tokens
     * Requirements: 8.2, 11.1
     */
    @Test
    void testTokenValidationSecurity() {
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read");

        // Test 1: Valid token validation
        String validToken = jwtTokenService.generateToken(userId, permissions);
        Claims validClaims = jwtTokenService.validateToken(validToken);
        assertNotNull(validClaims);
        assertEquals(userId, validClaims.getSubject());

        // Test 2: Tampered token (modified payload)
        String[] tokenParts = validToken.split("\\.");
        String tamperedPayload = tokenParts[1].replace('a', 'b'); // Modify one character
        String tamperedToken = tokenParts[0] + "." + tamperedPayload + "." + tokenParts[2];
        
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken(tamperedToken));

        // Test 3: Tampered signature
        String tamperedSignature = tokenParts[2].replace('a', 'b'); // Modify signature
        String tamperedSigToken = tokenParts[0] + "." + tokenParts[1] + "." + tamperedSignature;
        
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken(tamperedSigToken));

        // Test 4: Token with wrong algorithm (none algorithm attack)
        String noneAlgToken = createTokenWithNoneAlgorithm(userId, permissions);
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken(noneAlgToken));

        // Test 5: Completely invalid token format
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken("not.a.valid.jwt.token"));

        // Test 6: Empty token
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken(""));

        // Test 7: Null token
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken(null));

        // Test 8: Token with only two parts
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken("header.payload"));

        // Test 9: Token with extra parts
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken("header.payload.signature.extra"));
    }

    /**
     * Test token extraction from various Authorization header formats
     * Requirements: 8.2
     */
    @Test
    void testTokenExtractionSecurity() {
        String token = "valid-jwt-token-123";

        // Test 1: Valid Bearer token
        String validHeader = "Bearer " + token;
        assertEquals(token, jwtTokenService.extractTokenFromHeader(validHeader));

        // Test 2: Bearer with extra spaces
        String spacedHeader = "Bearer    " + token;
        assertEquals(token, jwtTokenService.extractTokenFromHeader(spacedHeader));

        // Test 3: Case insensitive Bearer
        String lowerCaseHeader = "bearer " + token;
        assertNull(jwtTokenService.extractTokenFromHeader(lowerCaseHeader)); // Should be case sensitive

        // Test 4: Wrong scheme
        String wrongScheme = "Basic " + token;
        assertNull(jwtTokenService.extractTokenFromHeader(wrongScheme));

        // Test 5: No scheme
        String noScheme = token;
        assertNull(jwtTokenService.extractTokenFromHeader(noScheme));

        // Test 6: Empty header
        assertNull(jwtTokenService.extractTokenFromHeader(""));

        // Test 7: Null header
        assertNull(jwtTokenService.extractTokenFromHeader(null));

        // Test 8: Bearer without token
        String bearerOnly = "Bearer";
        assertNull(jwtTokenService.extractTokenFromHeader(bearerOnly));

        // Test 9: Bearer with empty token
        String bearerEmpty = "Bearer ";
        assertNull(jwtTokenService.extractTokenFromHeader(bearerEmpty));

        // Test 10: Multiple Bearer tokens (potential attack)
        String multipleBearer = "Bearer token1 Bearer token2";
        assertEquals("token1 Bearer token2", jwtTokenService.extractTokenFromHeader(multipleBearer));
    }

    /**
     * Test token security with different user permissions and roles
     * Requirements: 8.2, 11.1
     */
    @Test
    void testTokenPermissionSecurity() {
        String userId = "testuser";

        // Test 1: Token with admin permissions
        List<String> adminPermissions = Arrays.asList("admin", "file.read", "file.upload", "file.delete", "system.manage");
        String adminToken = jwtTokenService.generateToken(userId, adminPermissions);
        
        List<String> extractedAdminPerms = jwtTokenService.getPermissionsFromToken(adminToken);
        assertEquals(adminPermissions, extractedAdminPerms);
        assertTrue(extractedAdminPerms.contains("admin"));
        assertTrue(extractedAdminPerms.contains("system.manage"));

        // Test 2: Token with limited permissions
        List<String> limitedPermissions = Arrays.asList("file.read");
        String limitedToken = jwtTokenService.generateToken(userId, limitedPermissions);
        
        List<String> extractedLimitedPerms = jwtTokenService.getPermissionsFromToken(limitedToken);
        assertEquals(limitedPermissions, extractedLimitedPerms);
        assertFalse(extractedLimitedPerms.contains("admin"));
        assertFalse(extractedLimitedPerms.contains("file.upload"));

        // Test 3: Token with no permissions
        List<String> noPermissions = Arrays.asList();
        String noPermToken = jwtTokenService.generateToken(userId, noPermissions);
        
        List<String> extractedNoPerms = jwtTokenService.getPermissionsFromToken(noPermToken);
        assertEquals(noPermissions, extractedNoPerms);
        assertTrue(extractedNoPerms.isEmpty());

        // Test 4: Token with null permissions
        String nullPermToken = jwtTokenService.generateToken(userId, null);
        List<String> extractedNullPerms = jwtTokenService.getPermissionsFromToken(nullPermToken);
        assertNull(extractedNullPerms);

        // Test 5: Verify permissions cannot be extracted from tampered token
        String[] tokenParts = adminToken.split("\\.");
        String tamperedPayload = tokenParts[1].replace('a', 'b');
        String tamperedToken = tokenParts[0] + "." + tamperedPayload + "." + tokenParts[2];
        
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.getPermissionsFromToken(tamperedToken));

        // Test 6: Verify user ID cannot be extracted from tampered token
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.getUserIdFromToken(tamperedToken));
    }

    /**
     * Test token security under concurrent access scenarios
     * Requirements: 8.3, 11.1
     */
    @Test
    void testTokenConcurrentAccessSecurity() throws InterruptedException {
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read", "file.upload");
        
        // Generate multiple tokens concurrently
        int numberOfThreads = 10;
        String[] tokens = new String[numberOfThreads];
        Thread[] threads = new Thread[numberOfThreads];
        
        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                tokens[index] = jwtTokenService.generateToken(userId + index, permissions);
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all tokens are valid and unique
        for (int i = 0; i < numberOfThreads; i++) {
            assertNotNull(tokens[i]);
            assertFalse(jwtTokenService.isTokenExpired(tokens[i]));
            assertEquals(userId + i, jwtTokenService.getUserIdFromToken(tokens[i]));
            assertEquals(permissions, jwtTokenService.getPermissionsFromToken(tokens[i]));
            
            // Verify tokens are unique
            for (int j = i + 1; j < numberOfThreads; j++) {
                assertNotEquals(tokens[i], tokens[j]);
            }
        }
    }

    /**
     * Test token replay attack prevention
     * Requirements: 8.2, 11.1
     */
    @Test
    void testTokenReplayAttackPrevention() {
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read");
        
        // Generate token
        String originalToken = jwtTokenService.generateToken(userId, permissions);
        
        // Validate token multiple times (should work)
        Claims claims1 = jwtTokenService.validateToken(originalToken);
        Claims claims2 = jwtTokenService.validateToken(originalToken);
        Claims claims3 = jwtTokenService.validateToken(originalToken);
        
        assertNotNull(claims1);
        assertNotNull(claims2);
        assertNotNull(claims3);
        
        // All validations should return the same user ID
        assertEquals(userId, claims1.getSubject());
        assertEquals(userId, claims2.getSubject());
        assertEquals(userId, claims3.getSubject());
        
        // Test with expired token (replay should fail)
        String expiredToken = createExpiredToken(userId, permissions);
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken(expiredToken));
        
        // Multiple attempts with expired token should all fail
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken(expiredToken));
        assertThrows(ArchiveSearchException.class, 
                () -> jwtTokenService.validateToken(expiredToken));
    }

    /**
     * Test token timing attack resistance
     * Requirements: 8.2, 11.1
     */
    @Test
    void testTokenTimingAttackResistance() {
        String userId = "testuser";
        List<String> permissions = Arrays.asList("file.read");
        
        String validToken = jwtTokenService.generateToken(userId, permissions);
        String invalidToken = "invalid.token.here";
        
        // Measure validation time for valid token
        long startTime1 = System.nanoTime();
        try {
            jwtTokenService.validateToken(validToken);
        } catch (Exception e) {
            // Ignore
        }
        long validTokenTime = System.nanoTime() - startTime1;
        
        // Measure validation time for invalid token
        long startTime2 = System.nanoTime();
        try {
            jwtTokenService.validateToken(invalidToken);
        } catch (Exception e) {
            // Expected
        }
        long invalidTokenTime = System.nanoTime() - startTime2;
        
        // Times should be relatively similar (within an order of magnitude)
        // This is a basic check - in practice, you'd want more sophisticated timing analysis
        double ratio = (double) Math.max(validTokenTime, invalidTokenTime) / Math.min(validTokenTime, invalidTokenTime);
        assertTrue(ratio < 100, "Token validation times should not reveal token validity through timing attacks");
    }

    // Helper methods for creating test tokens

    private String createExpiredToken(String userId, List<String> permissions) {
        return createTokenWithCustomExpiry(userId, permissions, -3600); // Expired 1 hour ago
    }

    private String createTokenWithCustomExpiry(String userId, List<String> permissions, int secondsFromNow) {
        Instant now = Instant.now();
        Instant expiry = now.plus(secondsFromNow, ChronoUnit.SECONDS);
        
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer("archive-search-api")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .claim("userId", userId)
                .claim("permissions", permissions)
                .signWith(testSecretKey)
                .compact();
    }

    private String createTokenWithNoneAlgorithm(String userId, List<String> permissions) {
        // This simulates a "none" algorithm attack where the attacker tries to bypass signature verification
        // Modern JWT libraries should reject this, but it's good to test
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer("archive-search-api")
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plus(30, ChronoUnit.MINUTES)))
                .claim("userId", userId)
                .claim("permissions", permissions)
                .compact() + ".none";
    }
}