package com.fabric.watcher.archive.controller;

import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.exception.ArchiveSearchException.ErrorCode;
import com.fabric.watcher.archive.model.AuthRequest;
import com.fabric.watcher.archive.model.AuthResponse;
import com.fabric.watcher.archive.model.AuthenticationResult;
import com.fabric.watcher.archive.model.ValidationResponse;
import com.fabric.watcher.archive.service.ArchiveSearchAuditService;
import com.fabric.watcher.archive.service.AuthenticationRateLimitService;
import com.fabric.watcher.archive.service.JwtTokenService;
import com.fabric.watcher.archive.service.LdapAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * REST controller for user authentication operations.
 * 
 * <p>This controller provides endpoints for user login, logout, and session validation
 * using LDAP authentication and JWT token management.</p>
 * 
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
@Tag(name = "Authentication", description = "User authentication and session management")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    private final LdapAuthenticationService ldapAuthenticationService;
    private final JwtTokenService jwtTokenService;
    private final AuthenticationRateLimitService rateLimitService;
    private final ArchiveSearchAuditService auditService;
    
    // In-memory session storage for active tokens
    private final ConcurrentMap<String, String> activeSessions = new ConcurrentHashMap<>();

    /**
     * Constructor for AuthenticationController.
     *
     * @param ldapAuthenticationService the LDAP authentication service
     * @param jwtTokenService          the JWT token service
     * @param rateLimitService         the rate limiting service
     * @param auditService            the audit logging service
     */
    public AuthenticationController(LdapAuthenticationService ldapAuthenticationService,
                                  JwtTokenService jwtTokenService,
                                  AuthenticationRateLimitService rateLimitService,
                                  ArchiveSearchAuditService auditService) {
        this.ldapAuthenticationService = ldapAuthenticationService;
        this.jwtTokenService = jwtTokenService;
        this.rateLimitService = rateLimitService;
        this.auditService = auditService;
        
        logger.info("Authentication Controller initialized");
    }

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @param request the authentication request containing user credentials
     * @param httpRequest the HTTP servlet request for IP address extraction
     * @return ResponseEntity containing authentication response or error
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate user", 
               description = "Authenticates a user against LDAP and returns a JWT token for subsequent requests")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication failed"),
        @ApiResponse(responseCode = "423", description = "Account locked due to too many failed attempts"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "503", description = "Authentication service unavailable")
    })
    public ResponseEntity<AuthResponse> authenticate(
            @Parameter(description = "User credentials", required = true)
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        String userId = request.getUserId();
        
        logger.debug("Authentication attempt for user: {} from IP: {}", userId, ipAddress);

        try {
            // Check if user is locked out due to failed attempts
            if (rateLimitService.isUserLockedOut(userId, ipAddress)) {
                long remainingTime = rateLimitService.getRemainingLockoutTime(userId, ipAddress);
                logger.warn("Authentication blocked for locked out user: {} from IP: {} ({}s remaining)", 
                           userId, ipAddress, remainingTime);
                
                auditService.logAuthentication(userId, false, ipAddress, 
                                             "Account locked due to failed attempts");
                
                throw new ArchiveSearchException(ErrorCode.USER_LOCKED_OUT, 
                    String.format("Account temporarily locked. Try again in %d seconds.", remainingTime));
            }

            // Attempt LDAP authentication
            AuthenticationResult authResult = ldapAuthenticationService.authenticate(
                userId, request.getPassword());

            if (!authResult.isSuccess()) {
                // Record failed attempt
                rateLimitService.recordFailedAttempt(userId, ipAddress);
                
                logger.warn("Authentication failed for user: {} from IP: {} - {}", 
                           userId, ipAddress, authResult.getErrorMessage());
                
                auditService.logAuthentication(userId, false, ipAddress, authResult.getErrorMessage());
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, 0, null));
            }

            // Clear any previous failed attempts
            rateLimitService.recordSuccessfulAttempt(userId, ipAddress);

            // Generate JWT token
            String token = jwtTokenService.generateToken(userId, authResult.getUserDetails().getRoles());
            long expirationSeconds = jwtTokenService.getTokenExpirationSeconds();

            // Store active session
            activeSessions.put(token, userId);

            // Create response
            AuthResponse response = new AuthResponse(
                token, 
                userId, 
                expirationSeconds, 
                authResult.getUserDetails().getRoles()
            );

            logger.info("User successfully authenticated: {} from IP: {}", userId, ipAddress);
            auditService.logAuthentication(userId, true, ipAddress, "Authentication successful");

            return ResponseEntity.ok(response);

        } catch (ArchiveSearchException e) {
            logger.error("Authentication error for user {}: {}", userId, e.getMessage());
            
            if (e.getErrorCode() != ErrorCode.USER_LOCKED_OUT) {
                auditService.logAuthentication(userId, false, ipAddress, e.getMessage());
            }
            
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected authentication error for user {}: {}", userId, e.getMessage(), e);
            auditService.logAuthentication(userId, false, ipAddress, "System error during authentication");
            
            throw new ArchiveSearchException(ErrorCode.AUTHENTICATION_FAILED, 
                                           "Authentication service temporarily unavailable");
        }
    }

    /**
     * Logs out a user by invalidating their session.
     *
     * @param httpRequest the HTTP servlet request containing the authorization header
     * @return ResponseEntity indicating logout success or failure
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout user", 
               description = "Invalidates the user's session and JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing token")
    })
    public ResponseEntity<Void> logout(
            @Parameter(description = "HTTP request with Authorization header", hidden = true)
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            String token = jwtTokenService.extractTokenFromHeader(authHeader);
            
            if (token == null) {
                logger.warn("Logout attempt without valid token from IP: {}", ipAddress);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Validate token and get user ID
            String userId = jwtTokenService.getUserIdFromToken(token);
            
            // Remove from active sessions
            activeSessions.remove(token);
            
            // Invalidate HTTP session if present
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            logger.info("User logged out successfully: {} from IP: {}", userId, ipAddress);
            auditService.logAuthentication(userId, true, ipAddress, "Logout successful");

            return ResponseEntity.ok().build();

        } catch (ArchiveSearchException e) {
            logger.warn("Logout failed from IP {}: {}", ipAddress, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            logger.error("Unexpected logout error from IP {}: {}", ipAddress, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Validates the current user session and token.
     *
     * @param httpRequest the HTTP servlet request containing the authorization header
     * @return ResponseEntity containing validation response
     */
    @GetMapping("/validate")
    @Operation(summary = "Validate session", 
               description = "Validates the current user session and returns session information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session validation result",
                    content = @Content(schema = @Schema(implementation = ValidationResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or missing token")
    })
    public ResponseEntity<ValidationResponse> validateSession(
            @Parameter(description = "HTTP request with Authorization header", hidden = true)
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            String token = jwtTokenService.extractTokenFromHeader(authHeader);
            
            if (token == null) {
                logger.debug("Session validation failed: no token provided from IP: {}", ipAddress);
                return ResponseEntity.ok(new ValidationResponse("No authentication token provided"));
            }

            // Check if token is in active sessions
            if (!activeSessions.containsKey(token)) {
                logger.debug("Session validation failed: token not in active sessions from IP: {}", ipAddress);
                return ResponseEntity.ok(new ValidationResponse("Session not found or expired"));
            }

            // Validate token
            String userId = jwtTokenService.getUserIdFromToken(token);
            Long remainingTime = jwtTokenService.getRemainingTime(token);
            
            if (remainingTime == null || remainingTime <= 0) {
                // Token expired, remove from active sessions
                activeSessions.remove(token);
                logger.debug("Session validation failed: token expired for user {} from IP: {}", userId, ipAddress);
                return ResponseEntity.ok(new ValidationResponse("Token expired"));
            }

            logger.debug("Session validation successful for user: {} from IP: {} ({}s remaining)", 
                        userId, ipAddress, remainingTime);
            
            return ResponseEntity.ok(new ValidationResponse(userId, remainingTime));

        } catch (ArchiveSearchException e) {
            logger.debug("Session validation failed from IP {}: {}", ipAddress, e.getMessage());
            return ResponseEntity.ok(new ValidationResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected session validation error from IP {}: {}", ipAddress, e.getMessage(), e);
            return ResponseEntity.ok(new ValidationResponse("Session validation error"));
        }
    }

    /**
     * Gets session statistics (for monitoring purposes).
     *
     * @return ResponseEntity containing session statistics
     */
    @GetMapping("/stats")
    @Operation(summary = "Get authentication statistics", 
               description = "Returns authentication and session statistics for monitoring")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    public ResponseEntity<Object> getAuthenticationStats() {
        try {
            return ResponseEntity.ok(java.util.Map.of(
                "activeSessions", activeSessions.size(),
                "trackedFailedAttempts", rateLimitService.getTrackedRecordCount(),
                "maxLoginAttempts", rateLimitService.getMaxLoginAttempts(),
                "lockoutDurationMinutes", rateLimitService.getLockoutDurationMinutes(),
                "tokenExpirationMinutes", jwtTokenService.getTokenExpirationMinutes()
            ));
        } catch (Exception e) {
            logger.error("Error retrieving authentication statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Extracts the client IP address from the HTTP request.
     *
     * @param request the HTTP servlet request
     * @return the client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Gets the number of active sessions (for testing purposes).
     *
     * @return the number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Clears all active sessions (for testing purposes).
     */
    public void clearAllSessions() {
        activeSessions.clear();
        logger.info("All active sessions cleared");
    }
}