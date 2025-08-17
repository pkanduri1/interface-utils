package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.exception.ArchiveSearchException.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Service for JWT token generation and validation.
 * 
 * <p>This service provides methods to generate, validate, and extract information
 * from JWT tokens used for authentication and session management.</p>
 * 
 * @since 1.0
 */
@Service
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class JwtTokenService {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenService.class);
    
    private static final String ISSUER = "archive-search-api";
    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String USER_ID_CLAIM = "userId";
    
    private final SecretKey secretKey;
    private final long tokenExpirationMinutes;

    /**
     * Constructor for JwtTokenService.
     *
     * @param properties the archive search properties containing JWT configuration
     */
    public JwtTokenService(ArchiveSearchProperties properties) {
        // Generate a secure key for JWT signing
        this.secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        this.tokenExpirationMinutes = properties.getSecurity() != null ? 
            properties.getSecurity().getSessionTimeoutMinutes() : 30;
        
        logger.info("JWT Token Service initialized with {}min token expiration", tokenExpirationMinutes);
    }

    /**
     * Generates a JWT token for the authenticated user.
     *
     * @param userId      the user ID
     * @param permissions the list of user permissions
     * @return the generated JWT token
     */
    public String generateToken(String userId, List<String> permissions) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ArchiveSearchException(ErrorCode.INVALID_REQUEST, "User ID cannot be empty");
        }

        try {
            Instant now = Instant.now();
            Instant expiration = now.plus(tokenExpirationMinutes, ChronoUnit.MINUTES);

            String token = Jwts.builder()
                    .setIssuer(ISSUER)
                    .setSubject(userId)
                    .claim(USER_ID_CLAIM, userId)
                    .claim(PERMISSIONS_CLAIM, permissions)
                    .setIssuedAt(Date.from(now))
                    .setExpiration(Date.from(expiration))
                    .signWith(secretKey)
                    .compact();

            logger.debug("Generated JWT token for user: {}", userId);
            return token;

        } catch (Exception e) {
            logger.error("Error generating JWT token for user {}: {}", userId, e.getMessage(), e);
            throw new ArchiveSearchException(ErrorCode.TOKEN_GENERATION_ERROR, 
                                           "Failed to generate authentication token");
        }
    }

    /**
     * Validates a JWT token and returns the claims if valid.
     *
     * @param token the JWT token to validate
     * @return the token claims if valid
     * @throws ArchiveSearchException if the token is invalid or expired
     */
    public Claims validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new ArchiveSearchException(ErrorCode.INVALID_TOKEN, "Token cannot be empty");
        }

        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .requireIssuer(ISSUER)
                    .parseClaimsJws(token)
                    .getBody();

            logger.debug("Successfully validated JWT token for user: {}", claims.getSubject());
            return claims;

        } catch (ExpiredJwtException e) {
            logger.warn("JWT token expired for user: {}", e.getClaims().getSubject());
            throw new ArchiveSearchException(ErrorCode.TOKEN_EXPIRED, "Authentication token has expired");
        } catch (UnsupportedJwtException e) {
            logger.warn("Unsupported JWT token: {}", e.getMessage());
            throw new ArchiveSearchException(ErrorCode.INVALID_TOKEN, "Unsupported token format");
        } catch (MalformedJwtException e) {
            logger.warn("Malformed JWT token: {}", e.getMessage());
            throw new ArchiveSearchException(ErrorCode.INVALID_TOKEN, "Invalid token format");
        } catch (SecurityException | IllegalArgumentException e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
            throw new ArchiveSearchException(ErrorCode.INVALID_TOKEN, "Invalid authentication token");
        }
    }

    /**
     * Extracts the user ID from a JWT token.
     *
     * @param token the JWT token
     * @return the user ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get(USER_ID_CLAIM, String.class);
    }

    /**
     * Extracts the permissions from a JWT token.
     *
     * @param token the JWT token
     * @return the list of permissions
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get(PERMISSIONS_CLAIM, List.class);
    }

    /**
     * Gets the remaining time until token expiration.
     *
     * @param token the JWT token
     * @return the remaining time in seconds, or null if token is invalid
     */
    public Long getRemainingTime(String token) {
        try {
            Claims claims = validateToken(token);
            Date expiration = claims.getExpiration();
            long remainingMs = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remainingMs / 1000); // Convert to seconds
        } catch (Exception e) {
            logger.debug("Could not get remaining time for token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a token is expired.
     *
     * @param token the JWT token
     * @return true if the token is expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            logger.debug("Error checking token expiration: {}", e.getMessage());
            return true; // Treat invalid tokens as expired
        }
    }

    /**
     * Extracts the token from the Authorization header.
     *
     * @param authorizationHeader the Authorization header value
     * @return the JWT token, or null if not found
     */
    public String extractTokenFromHeader(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    /**
     * Gets the token expiration time in minutes.
     *
     * @return the token expiration time in minutes
     */
    public long getTokenExpirationMinutes() {
        return tokenExpirationMinutes;
    }

    /**
     * Gets the token expiration time in seconds.
     *
     * @return the token expiration time in seconds
     */
    public long getTokenExpirationSeconds() {
        return tokenExpirationMinutes * 60;
    }
}