package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.AuthenticationResult;
import com.fabric.watcher.archive.model.UserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LdapAuthenticationService.
 * 
 * <p>These tests require a real LDAP server to be available and are only run
 * when the LDAP_INTEGRATION_TEST environment variable is set to 'true'.</p>
 * 
 * <p>To run these tests, set up the following environment variables:</p>
 * <ul>
 *   <li>LDAP_INTEGRATION_TEST=true</li>
 *   <li>LDAP_URL=ldap://your-ldap-server:389</li>
 *   <li>LDAP_BASE_DN=dc=company,dc=com</li>
 *   <li>LDAP_USER_SEARCH_BASE=ou=users</li>
 *   <li>LDAP_BIND_DN=cn=admin,dc=company,dc=com (optional)</li>
 *   <li>LDAP_BIND_PASSWORD=password (optional)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "logging.level.com.fabric.watcher.archive.service.LdapAuthenticationService=DEBUG"
})
@EnabledIfEnvironmentVariable(named = "LDAP_INTEGRATION_TEST", matches = "true")
class LdapAuthenticationServiceIntegrationTest {

    @Autowired
    private LdapAuthenticationService ldapAuthenticationService;

    @Autowired
    private ArchiveSearchProperties properties;

    @Test
    void testServiceIsCreated() {
        // Given/When/Then
        assertNotNull(ldapAuthenticationService);
        assertNotNull(properties);
        assertTrue(properties.isEnabled());
    }

    @Test
    void testAuthenticateWithInvalidUser() {
        // Given
        String invalidUserId = "nonexistent_user_12345";
        String password = "anypassword";

        // When
        AuthenticationResult result = ldapAuthenticationService.authenticate(invalidUserId, password);

        // Then
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNull(result.getUserDetails());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testGetUserDetailsWithInvalidUser() {
        // Given
        String invalidUserId = "nonexistent_user_12345";

        // When
        UserDetails result = ldapAuthenticationService.getUserDetails(invalidUserId);

        // Then
        assertNull(result);
    }

    @Test
    void testIsUserAuthorizedWithInvalidUser() {
        // Given
        String invalidUserId = "nonexistent_user_12345";
        String operation = "file.read";

        // When
        boolean result = ldapAuthenticationService.isUserAuthorized(invalidUserId, operation);

        // Then
        assertFalse(result);
    }

    @Test
    void testCacheOperations() {
        // Given
        int initialCacheSize = ldapAuthenticationService.getCacheSize();

        // When
        ldapAuthenticationService.clearUserCache();
        int cacheSizeAfterClear = ldapAuthenticationService.getCacheSize();

        // Then
        assertEquals(0, cacheSizeAfterClear);
        assertTrue(cacheSizeAfterClear <= initialCacheSize);
    }

    @Test
    void testLdapConfigurationIsLoaded() {
        // Given/When
        ArchiveSearchProperties.LdapConfig ldapConfig = properties.getLdap();

        // Then
        assertNotNull(ldapConfig);
        assertNotNull(ldapConfig.getUrl());
        assertNotNull(ldapConfig.getBaseDn());
        assertNotNull(ldapConfig.getUserSearchBase());
        assertNotNull(ldapConfig.getUserSearchFilter());
        assertTrue(ldapConfig.getConnectionTimeout() > 0);
        assertTrue(ldapConfig.getReadTimeout() > 0);
    }

    /**
     * This test can be enabled if you have a test user in your LDAP server.
     * Update the userId and password to match your test environment.
     */
    // @Test
    void testAuthenticateWithValidUser() {
        // Given
        String testUserId = "testuser"; // Replace with actual test user
        String testPassword = "testpass"; // Replace with actual test password

        // When
        AuthenticationResult result = ldapAuthenticationService.authenticate(testUserId, testPassword);

        // Then
        // Uncomment these assertions if you have a valid test user
        // assertTrue(result.isSuccess());
        // assertEquals(testUserId, result.getUserId());
        // assertNotNull(result.getUserDetails());
        // assertNull(result.getErrorMessage());
    }

    /**
     * This test can be enabled if you have a test user in your LDAP server.
     */
    // @Test
    void testGetUserDetailsWithValidUser() {
        // Given
        String testUserId = "testuser"; // Replace with actual test user

        // When
        UserDetails result = ldapAuthenticationService.getUserDetails(testUserId);

        // Then
        // Uncomment these assertions if you have a valid test user
        // assertNotNull(result);
        // assertEquals(testUserId, result.getUserId());
        // assertNotNull(result.getDisplayName());
    }
}