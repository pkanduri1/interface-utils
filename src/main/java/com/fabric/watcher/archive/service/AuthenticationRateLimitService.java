package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for managing authentication rate limiting.
 * 
 * <p>This service tracks failed authentication attempts and implements
 * rate limiting to prevent brute force attacks.</p>
 * 
 * @since 1.0
 */
@Service
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class AuthenticationRateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationRateLimitService.class);

    private final ConcurrentMap<String, FailedAttemptInfo> failedAttempts = new ConcurrentHashMap<>();
    private final int maxLoginAttempts;
    private final int lockoutDurationMinutes;

    /**
     * Constructor for AuthenticationRateLimitService.
     *
     * @param properties the archive search properties containing security configuration
     */
    public AuthenticationRateLimitService(ArchiveSearchProperties properties) {
        ArchiveSearchProperties.SecurityConfig security = properties.getSecurity();
        this.maxLoginAttempts = security != null ? security.getMaxLoginAttempts() : 3;
        this.lockoutDurationMinutes = security != null ? security.getLockoutDurationMinutes() : 15;
        
        logger.info("Authentication rate limiting initialized: {} max attempts, {}min lockout", 
                   maxLoginAttempts, lockoutDurationMinutes);
    }

    /**
     * Records a failed authentication attempt for a user.
     *
     * @param userId the user ID
     * @param ipAddress the IP address of the attempt
     */
    public void recordFailedAttempt(String userId, String ipAddress) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        String key = createKey(userId, ipAddress);
        Instant now = Instant.now();
        
        failedAttempts.compute(key, (k, existing) -> {
            if (existing == null) {
                logger.debug("Recording first failed attempt for user: {} from IP: {}", userId, ipAddress);
                return new FailedAttemptInfo(1, now, now);
            } else {
                // Reset counter if the last attempt was more than lockout duration ago
                if (existing.lastAttempt.plus(lockoutDurationMinutes, ChronoUnit.MINUTES).isBefore(now)) {
                    logger.debug("Resetting failed attempt counter for user: {} from IP: {}", userId, ipAddress);
                    return new FailedAttemptInfo(1, now, now);
                } else {
                    int newCount = existing.attemptCount + 1;
                    logger.debug("Recording failed attempt #{} for user: {} from IP: {}", newCount, userId, ipAddress);
                    return new FailedAttemptInfo(newCount, existing.firstAttempt, now);
                }
            }
        });

        // Log warning if user is now locked out
        if (isUserLockedOut(userId, ipAddress)) {
            logger.warn("User {} from IP {} is now locked out after {} failed attempts", 
                       userId, ipAddress, maxLoginAttempts);
        }
    }

    /**
     * Records a successful authentication attempt, clearing any failed attempts.
     *
     * @param userId the user ID
     * @param ipAddress the IP address of the attempt
     */
    public void recordSuccessfulAttempt(String userId, String ipAddress) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        String key = createKey(userId, ipAddress);
        FailedAttemptInfo removed = failedAttempts.remove(key);
        
        if (removed != null) {
            logger.debug("Cleared failed attempt record for user: {} from IP: {} (had {} attempts)", 
                        userId, ipAddress, removed.attemptCount);
        }
    }

    /**
     * Checks if a user is currently locked out due to too many failed attempts.
     *
     * @param userId the user ID
     * @param ipAddress the IP address
     * @return true if the user is locked out, false otherwise
     */
    public boolean isUserLockedOut(String userId, String ipAddress) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        String key = createKey(userId, ipAddress);
        FailedAttemptInfo info = failedAttempts.get(key);
        
        if (info == null) {
            return false;
        }

        // Check if lockout period has expired
        Instant lockoutExpiry = info.lastAttempt.plus(lockoutDurationMinutes, ChronoUnit.MINUTES);
        if (lockoutExpiry.isBefore(Instant.now())) {
            // Lockout has expired, remove the record
            failedAttempts.remove(key);
            logger.debug("Lockout expired for user: {} from IP: {}", userId, ipAddress);
            return false;
        }

        boolean lockedOut = info.attemptCount >= maxLoginAttempts;
        if (lockedOut) {
            logger.debug("User {} from IP {} is locked out ({} attempts, expires at {})", 
                        userId, ipAddress, info.attemptCount, lockoutExpiry);
        }
        
        return lockedOut;
    }

    /**
     * Gets the number of failed attempts for a user.
     *
     * @param userId the user ID
     * @param ipAddress the IP address
     * @return the number of failed attempts
     */
    public int getFailedAttemptCount(String userId, String ipAddress) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }

        String key = createKey(userId, ipAddress);
        FailedAttemptInfo info = failedAttempts.get(key);
        return info != null ? info.attemptCount : 0;
    }

    /**
     * Gets the remaining lockout time in seconds for a user.
     *
     * @param userId the user ID
     * @param ipAddress the IP address
     * @return the remaining lockout time in seconds, or 0 if not locked out
     */
    public long getRemainingLockoutTime(String userId, String ipAddress) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }

        String key = createKey(userId, ipAddress);
        FailedAttemptInfo info = failedAttempts.get(key);
        
        if (info == null || info.attemptCount < maxLoginAttempts) {
            return 0;
        }

        Instant lockoutExpiry = info.lastAttempt.plus(lockoutDurationMinutes, ChronoUnit.MINUTES);
        long remainingSeconds = ChronoUnit.SECONDS.between(Instant.now(), lockoutExpiry);
        
        return Math.max(0, remainingSeconds);
    }

    /**
     * Clears all failed attempt records (for testing or administrative purposes).
     */
    public void clearAllFailedAttempts() {
        int count = failedAttempts.size();
        failedAttempts.clear();
        logger.info("Cleared {} failed attempt records", count);
    }

    /**
     * Gets the current number of tracked failed attempt records.
     *
     * @return the number of tracked records
     */
    public int getTrackedRecordCount() {
        return failedAttempts.size();
    }

    /**
     * Gets the maximum number of login attempts allowed.
     *
     * @return the maximum login attempts
     */
    public int getMaxLoginAttempts() {
        return maxLoginAttempts;
    }

    /**
     * Gets the lockout duration in minutes.
     *
     * @return the lockout duration in minutes
     */
    public int getLockoutDurationMinutes() {
        return lockoutDurationMinutes;
    }

    /**
     * Creates a unique key for tracking failed attempts.
     *
     * @param userId the user ID
     * @param ipAddress the IP address
     * @return the unique key
     */
    private String createKey(String userId, String ipAddress) {
        return userId + ":" + (ipAddress != null ? ipAddress : "unknown");
    }

    /**
     * Internal class to track failed attempt information.
     */
    private static class FailedAttemptInfo {
        final int attemptCount;
        final Instant firstAttempt;
        final Instant lastAttempt;

        FailedAttemptInfo(int attemptCount, Instant firstAttempt, Instant lastAttempt) {
            this.attemptCount = attemptCount;
            this.firstAttempt = firstAttempt;
            this.lastAttempt = lastAttempt;
        }
    }
}