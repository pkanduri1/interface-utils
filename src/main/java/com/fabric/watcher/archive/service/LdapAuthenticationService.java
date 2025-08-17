package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.exception.ArchiveSearchException.ErrorCode;
import com.fabric.watcher.archive.model.AuthenticationResult;
import com.fabric.watcher.archive.model.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for LDAP-based user authentication and authorization.
 * 
 * <p>This service provides methods to authenticate users against Active Directory
 * using LDAP protocol, retrieve user details, and check user authorization.</p>
 * 
 * @since 1.0
 */
@Service
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class LdapAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(LdapAuthenticationService.class);

    private final ArchiveSearchProperties properties;
    private final LdapTemplate ldapTemplate;
    private final ConcurrentMap<String, UserDetails> userCache = new ConcurrentHashMap<>();

    /**
     * Constructor for LdapAuthenticationService.
     *
     * @param properties the archive search properties containing LDAP configuration
     */
    public LdapAuthenticationService(ArchiveSearchProperties properties) {
        this.properties = properties;
        this.ldapTemplate = createLdapTemplate();
        logger.info("LDAP Authentication Service initialized with server: {}", 
                   properties.getLdap().getUrl());
    }

    /**
     * Authenticates a user against LDAP/Active Directory.
     *
     * @param userId   the user ID to authenticate
     * @param password the user's password
     * @return AuthenticationResult containing success status and user details
     */
    public AuthenticationResult authenticate(String userId, String password) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("Authentication attempt with empty user ID");
            return new AuthenticationResult("User ID cannot be empty");
        }

        if (password == null || password.trim().isEmpty()) {
            logger.warn("Authentication attempt with empty password for user: {}", userId);
            return new AuthenticationResult("Password cannot be empty");
        }

        try {
            logger.debug("Attempting LDAP authentication for user: {}", userId);
            
            // First, find the user's DN
            String userDn = findUserDn(userId);
            if (userDn == null) {
                logger.warn("User not found in LDAP: {}", userId);
                return new AuthenticationResult("Invalid credentials");
            }

            // Attempt to authenticate by binding with the user's credentials
            boolean authenticated = authenticateUser(userDn, password);
            if (!authenticated) {
                logger.warn("Authentication failed for user: {}", userId);
                return new AuthenticationResult("Invalid credentials");
            }

            // Retrieve user details
            UserDetails userDetails = getUserDetails(userId);
            if (userDetails == null) {
                logger.warn("Could not retrieve user details for: {}", userId);
                return new AuthenticationResult("Authentication successful but user details unavailable");
            }

            logger.info("User successfully authenticated: {}", userId);
            return new AuthenticationResult(userId, userDetails);

        } catch (Exception e) {
            logger.error("LDAP authentication error for user {}: {}", userId, e.getMessage(), e);
            return new AuthenticationResult("Authentication service temporarily unavailable");
        }
    }

    /**
     * Retrieves detailed information about a user from LDAP.
     *
     * @param userId the user ID
     * @return UserDetails object containing user information, or null if not found
     */
    public UserDetails getUserDetails(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        // Check cache first
        UserDetails cachedDetails = userCache.get(userId);
        if (cachedDetails != null) {
            logger.debug("Retrieved user details from cache for: {}", userId);
            return cachedDetails;
        }

        try {
            logger.debug("Retrieving user details from LDAP for: {}", userId);
            
            Filter filter = new EqualsFilter("sAMAccountName", userId);
            List<UserDetails> users = ldapTemplate.search(
                properties.getLdap().getUserSearchBase(),
                filter.encode(),
                new UserDetailsAttributesMapper()
            );

            if (users.isEmpty()) {
                logger.warn("User not found in LDAP: {}", userId);
                return null;
            }

            UserDetails userDetails = users.get(0);
            
            // Cache the user details for future requests
            userCache.put(userId, userDetails);
            
            logger.debug("Successfully retrieved user details for: {}", userId);
            return userDetails;

        } catch (Exception e) {
            logger.error("Error retrieving user details for {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if a user is authorized to perform a specific operation.
     *
     * @param userId    the user ID
     * @param operation the operation to check authorization for
     * @return true if the user is authorized, false otherwise
     */
    public boolean isUserAuthorized(String userId, String operation) {
        if (userId == null || operation == null) {
            return false;
        }

        try {
            UserDetails userDetails = getUserDetails(userId);
            if (userDetails == null) {
                logger.warn("Cannot check authorization for unknown user: {}", userId);
                return false;
            }

            // Check if user has the required role for the operation
            List<String> userRoles = userDetails.getRoles();
            if (userRoles == null || userRoles.isEmpty()) {
                logger.debug("User {} has no roles assigned", userId);
                return false;
            }

            // For now, we'll use simple role-based authorization
            // This can be extended to support more complex authorization logic
            boolean authorized = switch (operation.toLowerCase()) {
                case "file.search", "file.read" -> 
                    userRoles.contains("file.read") || userRoles.contains("admin");
                case "file.upload", "file.write" -> 
                    userRoles.contains("file.upload") || userRoles.contains("admin");
                case "file.download" -> 
                    userRoles.contains("file.download") || userRoles.contains("admin");
                default -> userRoles.contains("admin");
            };

            logger.debug("Authorization check for user {} operation {}: {}", 
                        userId, operation, authorized);
            return authorized;

        } catch (Exception e) {
            logger.error("Error checking authorization for user {} operation {}: {}", 
                        userId, operation, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Clears the user cache.
     */
    public void clearUserCache() {
        userCache.clear();
        logger.info("User cache cleared");
    }

    /**
     * Gets the current cache size.
     *
     * @return the number of cached user details
     */
    public int getCacheSize() {
        return userCache.size();
    }

    /**
     * Creates and configures the LDAP template.
     *
     * @return configured LdapTemplate
     */
    protected LdapTemplate createLdapTemplate() {
        try {
            LdapContextSource contextSource = new LdapContextSource();
            ArchiveSearchProperties.LdapConfig ldapConfig = properties.getLdap();
            
            contextSource.setUrl(ldapConfig.getUrl());
            contextSource.setBase(ldapConfig.getBaseDn());
            
            if (ldapConfig.getBindDn() != null && !ldapConfig.getBindDn().trim().isEmpty()) {
                contextSource.setUserDn(ldapConfig.getBindDn());
                contextSource.setPassword(ldapConfig.getBindPassword());
            }
            
            // Set connection properties
            contextSource.setPooled(true);
            
            // Configure timeouts
            contextSource.setBaseEnvironmentProperties(java.util.Map.of(
                "com.sun.jndi.ldap.connect.timeout", String.valueOf(ldapConfig.getConnectionTimeout()),
                "com.sun.jndi.ldap.read.timeout", String.valueOf(ldapConfig.getReadTimeout())
            ));
            
            contextSource.afterPropertiesSet();
            
            LdapTemplate template = new LdapTemplate(contextSource);
            template.setIgnorePartialResultException(true);
            
            logger.info("LDAP template created successfully for server: {}", ldapConfig.getUrl());
            return template;
            
        } catch (Exception e) {
            logger.error("Failed to create LDAP template: {}", e.getMessage(), e);
            throw new ArchiveSearchException(ErrorCode.LDAP_CONNECTION_ERROR, 
                                           "Failed to initialize LDAP connection: " + e.getMessage());
        }
    }

    /**
     * Finds the distinguished name (DN) for a user.
     *
     * @param userId the user ID
     * @return the user's DN, or null if not found
     */
    private String findUserDn(String userId) {
        try {
            Filter filter = new EqualsFilter("sAMAccountName", userId);
            List<String> dns = ldapTemplate.search(
                properties.getLdap().getUserSearchBase(),
                filter.encode(),
                (AttributesMapper<String>) attrs -> {
                    try {
                        return attrs.get("distinguishedName").get().toString();
                    } catch (NamingException e) {
                        logger.warn("Could not get DN for user {}: {}", userId, e.getMessage());
                        return null;
                    }
                }
            );
            
            return dns.isEmpty() ? null : dns.get(0);
            
        } catch (Exception e) {
            logger.error("Error finding DN for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Authenticates a user by attempting to bind with their credentials.
     *
     * @param userDn   the user's distinguished name
     * @param password the user's password
     * @return true if authentication successful, false otherwise
     */
    private boolean authenticateUser(String userDn, String password) {
        try {
            LdapContextSource authContextSource = new LdapContextSource();
            authContextSource.setUrl(properties.getLdap().getUrl());
            authContextSource.setUserDn(userDn);
            authContextSource.setPassword(password);
            authContextSource.afterPropertiesSet();
            
            // Try to get the context - if this succeeds, authentication is successful
            authContextSource.getContext(userDn, password);
            return true;
            
        } catch (Exception e) {
            logger.debug("Authentication failed for DN {}: {}", userDn, e.getMessage());
            return false;
        }
    }

    /**
     * AttributesMapper implementation for mapping LDAP attributes to UserDetails.
     */
    private static class UserDetailsAttributesMapper implements AttributesMapper<UserDetails> {
        
        @Override
        public UserDetails mapFromAttributes(Attributes attrs) throws NamingException {
            UserDetails userDetails = new UserDetails();
            
            // Map basic attributes
            if (attrs.get("sAMAccountName") != null) {
                userDetails.setUserId(attrs.get("sAMAccountName").get().toString());
            }
            
            if (attrs.get("displayName") != null) {
                userDetails.setDisplayName(attrs.get("displayName").get().toString());
            }
            
            if (attrs.get("mail") != null) {
                userDetails.setEmail(attrs.get("mail").get().toString());
            }
            
            if (attrs.get("department") != null) {
                userDetails.setDepartment(attrs.get("department").get().toString());
            }
            
            // Map group memberships
            List<String> groups = new ArrayList<>();
            if (attrs.get("memberOf") != null) {
                for (int i = 0; i < attrs.get("memberOf").size(); i++) {
                    String groupDn = attrs.get("memberOf").get(i).toString();
                    // Extract group name from DN (e.g., "CN=Administrators,OU=Groups,DC=company,DC=com")
                    if (groupDn.startsWith("CN=")) {
                        String groupName = groupDn.substring(3, groupDn.indexOf(','));
                        groups.add(groupName);
                    }
                }
            }
            userDetails.setGroups(groups);
            
            // Map roles based on group membership
            List<String> roles = mapGroupsToRoles(groups);
            userDetails.setRoles(roles);
            
            return userDetails;
        }
        
        /**
         * Maps LDAP groups to application roles.
         *
         * @param groups the list of LDAP groups
         * @return the list of application roles
         */
        private List<String> mapGroupsToRoles(List<String> groups) {
            List<String> roles = new ArrayList<>();
            
            if (groups == null || groups.isEmpty()) {
                return roles;
            }
            
            // Map common groups to roles
            for (String group : groups) {
                String groupLower = group.toLowerCase();
                
                if (groupLower.contains("admin")) {
                    roles.add("admin");
                    roles.add("file.read");
                    roles.add("file.upload");
                    roles.add("file.download");
                } else if (groupLower.contains("developer") || groupLower.contains("dev")) {
                    roles.add("file.read");
                    roles.add("file.upload");
                    roles.add("file.download");
                } else if (groupLower.contains("user")) {
                    roles.add("file.read");
                    roles.add("file.download");
                }
            }
            
            // Ensure at least basic read access for authenticated users
            if (roles.isEmpty()) {
                roles.add("file.read");
            }
            
            return roles;
        }
    }
}