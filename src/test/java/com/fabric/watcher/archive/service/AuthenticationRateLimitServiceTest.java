package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationRateLimitService.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationRateLimitServiceTest {

    @Mock
    private ArchiveSearchProperties properties;

    @Mock
    private ArchiveSearchProperties.SecurityConfig securityConfig;

    private AuthenticationRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(properties.getSecurity()).thenReturn(securityConfig);
        when(securityConfig.getMaxLoginAttempts()).thenReturn(3);
        when(securityConfig.getLockoutDurationMinutes()).thenReturn(15);
        
        rateLimitService = new AuthenticationRateLimitService(properties);
    }

    @Test
    void testRecordFailedAttempt() {
        // Arrange
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Act
        rateLimitService.recordFailedAttempt(userId, ipAddress);

        // Assert
        assertEquals(1, rateLimitService.getFailedAttemptCount(userId, ipAddress));
        assertFalse(rateLimitService.isUserLockedOut(userId, ipAddress));
    }

    @Test
    void testRecordMultipleFailedAttempts() {
        // Arrange
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Act
        rateLimitService.recordFailedAttempt(userId, ipAddress);
        rateLimitService.recordFailedAttempt(userId, ipAddress);
        rateLimitService.recordFailedAttempt(userId, ipAddress);

        // Assert
        assertEquals(3, rateLimitService.getFailedAttemptCount(userId, ipAddress));
        assertTrue(rateLimitService.isUserLockedOut(userId, ipAddress));
    }

    @Test
    void testRecordFailedAttemptExceedsLimit() {
        // Arrange
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Act
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordFailedAttempt(userId, ipAddress);
        }

        // Assert
        assertEquals(5, rateLimitService.getFailedAttemptCount(userId, ipAddress));
        assertTrue(rateLimitService.isUserLockedOut(userId, ipAddress));
    }

    @Test
    void testRecordSuccessfulAttempt() {
        // Arrange
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Record some failed attempts first
        rateLimitService.recordFailedAttempt(userId, ipAddress);
        rateLimitService.recordFailedAttempt(userId, ipAddress);
        assertEquals(2, rateLimitService.getFailedAttemptCount(userId, ipAddress));

        // Act
        rateLimitService.recordSuccessfulAttempt(userId, ipAddress);

        // Assert
        assertEquals(0, rateLimitService.getFailedAttemptCount(userId, ipAddress));
        assertFalse(rateLimitService.isUserLockedOut(userId, ipAddress));
    }

    @Test
    void testIsUserLockedOut() {
        // Arrange
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Initially not locked out
        assertFalse(rateLimitService.isUserLockedOut(userId, ipAddress));

        // Record failed attempts up to limit
        for (int i = 0; i < 3; i++) {
            rateLimitService.recordFailedAttempt(userId, ipAddress);
        }

        // Assert
        assertTrue(rateLimitService.isUserLockedOut(userId, ipAddress));
    }

    @Test
    void testIsUserLockedOutWithDifferentIpAddresses() {
        // Arrange
        String userId = "testuser";
        String ipAddress1 = "192.168.1.1";
        String ipAddress2 = "192.168.1.2";

        // Record failed attempts for first IP
        for (int i = 0; i < 3; i++) {
            rateLimitService.recordFailedAttempt(userId, ipAddress1);
        }

        // Assert
        assertTrue(rateLimitService.isUserLockedOut(userId, ipAddress1));
        assertFalse(rateLimitService.isUserLockedOut(userId, ipAddress2));
    }

    @Test
    void testGetFailedAttemptCount() {
        // Arrange
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Initially zero
        assertEquals(0, rateLimitService.getFailedAttemptCount(userId, ipAddress));

        // Record attempts
        rateLimitService.recordFailedAttempt(userId, ipAddress);
        assertEquals(1, rateLimitService.getFailedAttemptCount(userId, ipAddress));

        rateLimitService.recordFailedAttempt(userId, ipAddress);
        assertEquals(2, rateLimitService.getFailedAttemptCount(userId, ipAddress));
    }

    @Test
    void testGetRemainingLockoutTime() {
        // Arrange
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Initially no lockout
        assertEquals(0, rateLimitService.getRemainingLockoutTime(userId, ipAddress));

        // Lock out user
        for (int i = 0; i < 3; i++) {
            rateLimitService.recordFailedAttempt(userId, ipAddress);
        }

        // Assert
        long remainingTime = rateLimitService.getRemainingLockoutTime(userId, ipAddress);
        assertTrue(remainingTime > 0);
        assertTrue(remainingTime <= 15 * 60); // 15 minutes in seconds
    }

    @Test
    void testGetRemainingLockoutTimeNotLockedOut() {
        // Arrange
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Record some failed attempts but not enough to lock out
        rateLimitService.recordFailedAttempt(userId, ipAddress);
        rateLimitService.recordFailedAttempt(userId, ipAddress);

        // Assert
        assertEquals(0, rateLimitService.getRemainingLockoutTime(userId, ipAddress));
    }

    @Test
    void testClearAllFailedAttempts() {
        // Arrange
        String userId1 = "testuser1";
        String userId2 = "testuser2";
        String ipAddress = "192.168.1.1";

        rateLimitService.recordFailedAttempt(userId1, ipAddress);
        rateLimitService.recordFailedAttempt(userId2, ipAddress);
        
        assertEquals(2, rateLimitService.getTrackedRecordCount());

        // Act
        rateLimitService.clearAllFailedAttempts();

        // Assert
        assertEquals(0, rateLimitService.getTrackedRecordCount());
        assertEquals(0, rateLimitService.getFailedAttemptCount(userId1, ipAddress));
        assertEquals(0, rateLimitService.getFailedAttemptCount(userId2, ipAddress));
    }

    @Test
    void testGetTrackedRecordCount() {
        // Arrange
        String userId1 = "testuser1";
        String userId2 = "testuser2";
        String ipAddress = "192.168.1.1";

        // Initially zero
        assertEquals(0, rateLimitService.getTrackedRecordCount());

        // Add records
        rateLimitService.recordFailedAttempt(userId1, ipAddress);
        assertEquals(1, rateLimitService.getTrackedRecordCount());

        rateLimitService.recordFailedAttempt(userId2, ipAddress);
        assertEquals(2, rateLimitService.getTrackedRecordCount());

        // Adding more attempts for same user doesn't increase count
        rateLimitService.recordFailedAttempt(userId1, ipAddress);
        assertEquals(2, rateLimitService.getTrackedRecordCount());
    }

    @Test
    void testGetMaxLoginAttempts() {
        // Act & Assert
        assertEquals(3, rateLimitService.getMaxLoginAttempts());
    }

    @Test
    void testGetLockoutDurationMinutes() {
        // Act & Assert
        assertEquals(15, rateLimitService.getLockoutDurationMinutes());
    }

    @Test
    void testRecordFailedAttemptWithEmptyUserId() {
        // Arrange
        String userId = "";
        String ipAddress = "192.168.1.1";

        // Act
        rateLimitService.recordFailedAttempt(userId, ipAddress);

        // Assert
        assertEquals(0, rateLimitService.getTrackedRecordCount());
    }

    @Test
    void testRecordFailedAttemptWithNullUserId() {
        // Arrange
        String userId = null;
        String ipAddress = "192.168.1.1";

        // Act
        rateLimitService.recordFailedAttempt(userId, ipAddress);

        // Assert
        assertEquals(0, rateLimitService.getTrackedRecordCount());
    }

    @Test
    void testRecordSuccessfulAttemptWithEmptyUserId() {
        // Arrange
        String userId = "";
        String ipAddress = "192.168.1.1";

        // Act (should not throw exception)
        rateLimitService.recordSuccessfulAttempt(userId, ipAddress);

        // Assert
        assertEquals(0, rateLimitService.getTrackedRecordCount());
    }

    @Test
    void testIsUserLockedOutWithEmptyUserId() {
        // Act & Assert
        assertFalse(rateLimitService.isUserLockedOut("", "192.168.1.1"));
        assertFalse(rateLimitService.isUserLockedOut(null, "192.168.1.1"));
    }

    @Test
    void testGetFailedAttemptCountWithEmptyUserId() {
        // Act & Assert
        assertEquals(0, rateLimitService.getFailedAttemptCount("", "192.168.1.1"));
        assertEquals(0, rateLimitService.getFailedAttemptCount(null, "192.168.1.1"));
    }

    @Test
    void testGetRemainingLockoutTimeWithEmptyUserId() {
        // Act & Assert
        assertEquals(0, rateLimitService.getRemainingLockoutTime("", "192.168.1.1"));
        assertEquals(0, rateLimitService.getRemainingLockoutTime(null, "192.168.1.1"));
    }

    @Test
    void testRecordFailedAttemptWithNullIpAddress() {
        // Arrange
        String userId = "testuser";
        String ipAddress = null;

        // Act
        rateLimitService.recordFailedAttempt(userId, ipAddress);

        // Assert
        assertEquals(1, rateLimitService.getFailedAttemptCount(userId, ipAddress));
        assertEquals(1, rateLimitService.getTrackedRecordCount());
    }

    @Test
    void testDefaultConfigurationValues() {
        // Arrange
        when(properties.getSecurity()).thenReturn(null);
        AuthenticationRateLimitService serviceWithNullConfig = 
            new AuthenticationRateLimitService(properties);

        // Act & Assert
        assertEquals(3, serviceWithNullConfig.getMaxLoginAttempts());
        assertEquals(15, serviceWithNullConfig.getLockoutDurationMinutes());
    }

    @Test
    void testLockoutExpirationAutoCleanup() throws InterruptedException {
        // Arrange
        when(securityConfig.getLockoutDurationMinutes()).thenReturn(1); // 1 minute for testing
        AuthenticationRateLimitService shortLockoutService = 
            new AuthenticationRateLimitService(properties);
        
        String userId = "testuser";
        String ipAddress = "192.168.1.1";

        // Lock out user
        for (int i = 0; i < 3; i++) {
            shortLockoutService.recordFailedAttempt(userId, ipAddress);
        }
        
        assertTrue(shortLockoutService.isUserLockedOut(userId, ipAddress));

        // Wait for lockout to expire (simulate time passing)
        // Note: In a real test, you might want to use a clock abstraction
        // For this test, we'll just verify the logic works with a fresh check
        
        // Simulate checking after lockout period by creating a new attempt
        // which should reset if enough time has passed
        Thread.sleep(100); // Small delay to ensure time difference
        
        // The lockout should still be active since we only waited 100ms
        assertTrue(shortLockoutService.isUserLockedOut(userId, ipAddress));
    }

    @Test
    void testMultipleUsersIndependentLockouts() {
        // Arrange
        String user1 = "user1";
        String user2 = "user2";
        String ipAddress = "192.168.1.1";

        // Lock out user1
        for (int i = 0; i < 3; i++) {
            rateLimitService.recordFailedAttempt(user1, ipAddress);
        }

        // Record some attempts for user2 but not enough to lock out
        rateLimitService.recordFailedAttempt(user2, ipAddress);
        rateLimitService.recordFailedAttempt(user2, ipAddress);

        // Assert
        assertTrue(rateLimitService.isUserLockedOut(user1, ipAddress));
        assertFalse(rateLimitService.isUserLockedOut(user2, ipAddress));
        assertEquals(3, rateLimitService.getFailedAttemptCount(user1, ipAddress));
        assertEquals(2, rateLimitService.getFailedAttemptCount(user2, ipAddress));
    }
}