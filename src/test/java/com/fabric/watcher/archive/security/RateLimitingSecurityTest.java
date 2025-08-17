package com.fabric.watcher.archive.security;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.service.AuthenticationRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive security tests for authentication rate limiting functionality.
 * Tests various rate limiting scenarios, edge cases, and security vulnerabilities.
 * 
 * Requirements: 8.3, 11.3
 */
@ExtendWith(MockitoExtension.class)
class RateLimitingSecurityTest {

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

    /**
     * Test rate limiting with various attack patterns
     * Requirements: 8.3, 11.3
     */
    @Test
    void testRateLimitingAttackPatterns() {
        String targetUser = "targetuser";
        String attackerIp = "10.0.0.1";

        // Test 1: Basic brute force attack
        assertFalse(rateLimitService.isUserLockedOut(targetUser, attackerIp));
        
        // First attempt
        rateLimitService.recordFailedAttempt(targetUser, attackerIp);
        assertEquals(1, rateLimitService.getFailedAttemptCount(targetUser, attackerIp));
        assertFalse(rateLimitService.isUserLockedOut(targetUser, attackerIp));

        // Second attempt
        rateLimitService.recordFailedAttempt(targetUser, attackerIp);
        assertEquals(2, rateLimitService.getFailedAttemptCount(targetUser, attackerIp));
        assertFalse(rateLimitService.isUserLockedOut(targetUser, attackerIp));

        // Third attempt - should trigger lockout
        rateLimitService.recordFailedAttempt(targetUser, attackerIp);
        assertEquals(3, rateLimitService.getFailedAttemptCount(targetUser, attackerIp));
        assertTrue(rateLimitService.isUserLockedOut(targetUser, attackerIp));

        // Fourth attempt - should still be locked
        rateLimitService.recordFailedAttempt(targetUser, attackerIp);
        assertEquals(4, rateLimitService.getFailedAttemptCount(targetUser, attackerIp));
        assertTrue(rateLimitService.isUserLockedOut(targetUser, attackerIp));

        // Test 2: Distributed attack from multiple IPs
        String[] attackerIps = {"10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5"};
        
        for (String ip : attackerIps) {
            // Each IP can make attempts independently
            assertFalse(rateLimitService.isUserLockedOut(targetUser, ip));
            
            for (int i = 0; i < 3; i++) {
                rateLimitService.recordFailedAttempt(targetUser, ip);
            }
            
            assertTrue(rateLimitService.isUserLockedOut(targetUser, ip));
            assertEquals(3, rateLimitService.getFailedAttemptCount(targetUser, ip));
        }

        // Original IP should still be locked
        assertTrue(rateLimitService.isUserLockedOut(targetUser, attackerIp));

        // Test 3: Attack against multiple users from same IP
        String[] targetUsers = {"user1", "user2", "user3", "user4"};
        String singleAttackerIp = "192.168.1.100";
        
        for (String user : targetUsers) {
            assertFalse(rateLimitService.isUserLockedOut(user, singleAttackerIp));
            
            for (int i = 0; i < 3; i++) {
                rateLimitService.recordFailedAttempt(user, singleAttackerIp);
            }
            
            assertTrue(rateLimitService.isUserLockedOut(user, singleAttackerIp));
        }

        // Test 4: Mixed successful and failed attempts
        String mixedUser = "mixeduser";
        String mixedIp = "172.16.0.1";
        
        // Two failed attempts
        rateLimitService.recordFailedAttempt(mixedUser, mixedIp);
        rateLimitService.recordFailedAttempt(mixedUser, mixedIp);
        assertEquals(2, rateLimitService.getFailedAttemptCount(mixedUser, mixedIp));
        assertFalse(rateLimitService.isUserLockedOut(mixedUser, mixedIp));
        
        // Successful attempt should reset counter
        rateLimitService.recordSuccessfulAttempt(mixedUser, mixedIp);
        assertEquals(0, rateLimitService.getFailedAttemptCount(mixedUser, mixedIp));
        assertFalse(rateLimitService.isUserLockedOut(mixedUser, mixedIp));
        
        // Can now make failed attempts again
        rateLimitService.recordFailedAttempt(mixedUser, mixedIp);
        assertEquals(1, rateLimitService.getFailedAttemptCount(mixedUser, mixedIp));
        assertFalse(rateLimitService.isUserLockedOut(mixedUser, mixedIp));
    }

    /**
     * Test rate limiting with concurrent attacks
     * Requirements: 8.3, 11.3
     */
    @Test
    void testConcurrentRateLimitingAttacks() throws Exception {
        String targetUser = "concurrentuser";
        String attackerIp = "10.0.0.10";
        int numberOfThreads = 20;
        int attemptsPerThread = 5;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CompletableFuture<Void>[] futures = new CompletableFuture[numberOfThreads];

        // Launch concurrent failed attempts
        for (int i = 0; i < numberOfThreads; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < attemptsPerThread; j++) {
                    rateLimitService.recordFailedAttempt(targetUser, attackerIp);
                    
                    // Small delay to simulate real attack timing
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, executor);
        }

        // Wait for all threads to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

        // Verify user is locked out
        assertTrue(rateLimitService.isUserLockedOut(targetUser, attackerIp));
        
        // Total attempts should be recorded (may be more than threshold due to concurrency)
        int totalAttempts = rateLimitService.getFailedAttemptCount(targetUser, attackerIp);
        assertTrue(totalAttempts >= 3, "Should have at least 3 failed attempts");
        assertTrue(totalAttempts <= numberOfThreads * attemptsPerThread, 
                  "Should not exceed maximum possible attempts");

        // Remaining lockout time should be positive
        long remainingTime = rateLimitService.getRemainingLockoutTime(targetUser, attackerIp);
        assertTrue(remainingTime > 0, "Should have remaining lockout time");
        assertTrue(remainingTime <= 15 * 60, "Should not exceed maximum lockout duration");

        executor.shutdown();
    }

    /**
     * Test rate limiting edge cases and boundary conditions
     * Requirements: 8.3, 11.3
     */
    @Test
    void testRateLimitingEdgeCases() {
        // Test 1: Empty and null user IDs
        String validIp = "192.168.1.1";
        
        rateLimitService.recordFailedAttempt("", validIp);
        rateLimitService.recordFailedAttempt(null, validIp);
        
        assertEquals(0, rateLimitService.getFailedAttemptCount("", validIp));
        assertEquals(0, rateLimitService.getFailedAttemptCount(null, validIp));
        assertFalse(rateLimitService.isUserLockedOut("", validIp));
        assertFalse(rateLimitService.isUserLockedOut(null, validIp));

        // Test 2: Empty and null IP addresses
        String validUser = "testuser";
        
        rateLimitService.recordFailedAttempt(validUser, "");
        rateLimitService.recordFailedAttempt(validUser, null);
        
        assertEquals(1, rateLimitService.getFailedAttemptCount(validUser, ""));
        assertEquals(1, rateLimitService.getFailedAttemptCount(validUser, null));

        // Test 3: Very long user IDs (potential DoS)
        String longUserId = "a".repeat(1000);
        String normalIp = "192.168.1.2";
        
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordFailedAttempt(longUserId, normalIp);
        }
        
        assertTrue(rateLimitService.isUserLockedOut(longUserId, normalIp));
        assertEquals(5, rateLimitService.getFailedAttemptCount(longUserId, normalIp));

        // Test 4: Special characters in user IDs
        String[] specialUsers = {
            "user@domain.com",
            "user.name",
            "user-name",
            "user_name",
            "user+tag",
            "user with spaces",
            "用户名", // Unicode
            "user\u0000control", // Control characters
            "'; DROP TABLE users; --", // SQL injection attempt
            "user)(|(password=*)" // LDAP injection attempt
        };
        
        for (String specialUser : specialUsers) {
            for (int i = 0; i < 3; i++) {
                rateLimitService.recordFailedAttempt(specialUser, normalIp);
            }
            
            assertTrue(rateLimitService.isUserLockedOut(specialUser, normalIp));
            assertEquals(3, rateLimitService.getFailedAttemptCount(specialUser, normalIp));
        }

        // Test 5: IPv6 addresses
        String[] ipv6Addresses = {
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "2001:db8:85a3::8a2e:370:7334",
            "::1",
            "fe80::1%lo0"
        };
        
        String ipv6User = "ipv6user";
        for (String ipv6 : ipv6Addresses) {
            for (int i = 0; i < 3; i++) {
                rateLimitService.recordFailedAttempt(ipv6User, ipv6);
            }
            
            assertTrue(rateLimitService.isUserLockedOut(ipv6User, ipv6));
        }

        // Test 6: Case sensitivity in user IDs
        String lowerUser = "testuser";
        String upperUser = "TESTUSER";
        String mixedUser = "TestUser";
        String caseIp = "192.168.1.3";
        
        rateLimitService.recordFailedAttempt(lowerUser, caseIp);
        rateLimitService.recordFailedAttempt(upperUser, caseIp);
        rateLimitService.recordFailedAttempt(mixedUser, caseIp);
        
        // Each should be treated as separate users
        assertEquals(1, rateLimitService.getFailedAttemptCount(lowerUser, caseIp));
        assertEquals(1, rateLimitService.getFailedAttemptCount(upperUser, caseIp));
        assertEquals(1, rateLimitService.getFailedAttemptCount(mixedUser, caseIp));
    }

    /**
     * Test rate limiting configuration and customization
     * Requirements: 8.3
     */
    @Test
    void testRateLimitingConfiguration() {
        // Test 1: Custom max attempts (1 attempt)
        when(securityConfig.getMaxLoginAttempts()).thenReturn(1);
        when(securityConfig.getLockoutDurationMinutes()).thenReturn(5);
        
        AuthenticationRateLimitService strictService = new AuthenticationRateLimitService(properties);
        
        String strictUser = "strictuser";
        String strictIp = "192.168.1.10";
        
        assertFalse(strictService.isUserLockedOut(strictUser, strictIp));
        
        strictService.recordFailedAttempt(strictUser, strictIp);
        assertTrue(strictService.isUserLockedOut(strictUser, strictIp));
        assertEquals(1, strictService.getMaxLoginAttempts());
        assertEquals(5, strictService.getLockoutDurationMinutes());

        // Test 2: Very high max attempts (100 attempts)
        when(securityConfig.getMaxLoginAttempts()).thenReturn(100);
        when(securityConfig.getLockoutDurationMinutes()).thenReturn(1);
        
        AuthenticationRateLimitService lenientService = new AuthenticationRateLimitService(properties);
        
        String lenientUser = "lenientuser";
        String lenientIp = "192.168.1.11";
        
        for (int i = 0; i < 99; i++) {
            lenientService.recordFailedAttempt(lenientUser, lenientIp);
            assertFalse(lenientService.isUserLockedOut(lenientUser, lenientIp));
        }
        
        lenientService.recordFailedAttempt(lenientUser, lenientIp);
        assertTrue(lenientService.isUserLockedOut(lenientUser, lenientIp));
        assertEquals(100, lenientService.getMaxLoginAttempts());
        assertEquals(1, lenientService.getLockoutDurationMinutes());

        // Test 3: Zero lockout duration (immediate unlock)
        when(securityConfig.getMaxLoginAttempts()).thenReturn(3);
        when(securityConfig.getLockoutDurationMinutes()).thenReturn(0);
        
        AuthenticationRateLimitService noLockoutService = new AuthenticationRateLimitService(properties);
        
        String noLockoutUser = "nolockoutuser";
        String noLockoutIp = "192.168.1.12";
        
        for (int i = 0; i < 5; i++) {
            noLockoutService.recordFailedAttempt(noLockoutUser, noLockoutIp);
        }
        
        // Should be locked out but with 0 remaining time
        assertTrue(noLockoutService.isUserLockedOut(noLockoutUser, noLockoutIp));
        assertEquals(0, noLockoutService.getRemainingLockoutTime(noLockoutUser, noLockoutIp));

        // Test 4: Null security configuration (should use defaults)
        when(properties.getSecurity()).thenReturn(null);
        
        AuthenticationRateLimitService defaultService = new AuthenticationRateLimitService(properties);
        
        assertEquals(3, defaultService.getMaxLoginAttempts());
        assertEquals(15, defaultService.getLockoutDurationMinutes());
    }

    /**
     * Test rate limiting memory management and cleanup
     * Requirements: 8.3, 11.3
     */
    @Test
    void testRateLimitingMemoryManagement() {
        String baseUser = "memoryuser";
        String baseIp = "192.168.2.";
        
        // Test 1: Create many tracking records
        int numberOfRecords = 1000;
        for (int i = 0; i < numberOfRecords; i++) {
            String user = baseUser + i;
            String ip = baseIp + (i % 255);
            rateLimitService.recordFailedAttempt(user, ip);
        }
        
        assertEquals(numberOfRecords, rateLimitService.getTrackedRecordCount());

        // Test 2: Clear all records
        rateLimitService.clearAllFailedAttempts();
        assertEquals(0, rateLimitService.getTrackedRecordCount());

        // Test 3: Verify cleared records don't affect new attempts
        String clearedUser = baseUser + "0";
        String clearedIp = baseIp + "0";
        
        assertEquals(0, rateLimitService.getFailedAttemptCount(clearedUser, clearedIp));
        assertFalse(rateLimitService.isUserLockedOut(clearedUser, clearedIp));

        // Test 4: Memory usage with repeated operations
        for (int i = 0; i < 100; i++) {
            String user = "repeatuser" + (i % 10); // Reuse 10 users
            String ip = "192.168.3." + (i % 10);   // Reuse 10 IPs
            
            rateLimitService.recordFailedAttempt(user, ip);
            
            if (i % 20 == 0) {
                rateLimitService.recordSuccessfulAttempt(user, ip);
            }
        }
        
        // Should have at most 100 records (10 users * 10 IPs)
        assertTrue(rateLimitService.getTrackedRecordCount() <= 100);
    }

    /**
     * Test rate limiting with time-based scenarios
     * Requirements: 8.3
     */
    @Test
    void testRateLimitingTimeBasedScenarios() {
        String timeUser = "timeuser";
        String timeIp = "192.168.4.1";

        // Test 1: Lockout duration calculation
        for (int i = 0; i < 3; i++) {
            rateLimitService.recordFailedAttempt(timeUser, timeIp);
        }
        
        assertTrue(rateLimitService.isUserLockedOut(timeUser, timeIp));
        
        long remainingTime = rateLimitService.getRemainingLockoutTime(timeUser, timeIp);
        assertTrue(remainingTime > 0);
        assertTrue(remainingTime <= 15 * 60); // Should not exceed 15 minutes

        // Test 2: Multiple lockout time checks
        long firstCheck = rateLimitService.getRemainingLockoutTime(timeUser, timeIp);
        
        try {
            Thread.sleep(100); // Wait 100ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long secondCheck = rateLimitService.getRemainingLockoutTime(timeUser, timeIp);
        assertTrue(secondCheck <= firstCheck, "Remaining time should decrease");

        // Test 3: User not locked out should have 0 remaining time
        String notLockedUser = "notlockeduser";
        assertEquals(0, rateLimitService.getRemainingLockoutTime(notLockedUser, timeIp));

        // Test 4: Successful attempt should reset lockout time
        rateLimitService.recordSuccessfulAttempt(timeUser, timeIp);
        assertEquals(0, rateLimitService.getRemainingLockoutTime(timeUser, timeIp));
        assertFalse(rateLimitService.isUserLockedOut(timeUser, timeIp));
    }

    /**
     * Test rate limiting security against bypass attempts
     * Requirements: 8.3, 11.3
     */
    @Test
    void testRateLimitingBypassPrevention() {
        String bypassUser = "bypassuser";
        
        // Test 1: IP address variations (should not bypass)
        String[] ipVariations = {
            "192.168.1.1",
            "192.168.1.01",  // Leading zero
            "192.168.1.001", // Multiple leading zeros
            "192.168.001.1", // Leading zero in different octet
        };
        
        for (String ip : ipVariations) {
            for (int i = 0; i < 3; i++) {
                rateLimitService.recordFailedAttempt(bypassUser, ip);
            }
            assertTrue(rateLimitService.isUserLockedOut(bypassUser, ip));
        }

        // Test 2: User ID variations (should be treated separately)
        String[] userVariations = {
            "bypassuser",
            "bypassuser ",  // Trailing space
            " bypassuser",  // Leading space
            "BYPASSUSER",   // Different case
            "bypassuser\u0000", // Null character
        };
        
        String consistentIp = "10.0.0.1";
        for (String user : userVariations) {
            // Each variation should be independent
            assertEquals(0, rateLimitService.getFailedAttemptCount(user, consistentIp));
            assertFalse(rateLimitService.isUserLockedOut(user, consistentIp));
        }

        // Test 3: Rapid successive attempts (should not bypass rate limiting)
        String rapidUser = "rapiduser";
        String rapidIp = "172.16.0.1";
        
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            rateLimitService.recordFailedAttempt(rapidUser, rapidIp);
        }
        long endTime = System.currentTimeMillis();
        
        assertTrue(rateLimitService.isUserLockedOut(rapidUser, rapidIp));
        assertTrue(endTime - startTime < 1000, "Rapid attempts should complete quickly");

        // Test 4: Alternating users (should not affect each other)
        String user1 = "alternateuser1";
        String user2 = "alternateuser2";
        String alternateIp = "203.0.113.1";
        
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordFailedAttempt(user1, alternateIp);
            rateLimitService.recordFailedAttempt(user2, alternateIp);
        }
        
        assertTrue(rateLimitService.isUserLockedOut(user1, alternateIp));
        assertTrue(rateLimitService.isUserLockedOut(user2, alternateIp));
        assertEquals(5, rateLimitService.getFailedAttemptCount(user1, alternateIp));
        assertEquals(5, rateLimitService.getFailedAttemptCount(user2, alternateIp));
    }
}