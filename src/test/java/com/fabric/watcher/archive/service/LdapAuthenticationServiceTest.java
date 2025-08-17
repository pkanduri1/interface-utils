package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.model.AuthenticationResult;
import com.fabric.watcher.archive.model.UserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LdapAuthenticationService.
 */
@ExtendWith(MockitoExtension.class)
class LdapAuthenticationServiceTest {

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private LdapContextSource contextSource;

    private ArchiveSearchProperties properties;
    private LdapAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        properties = createTestProperties();
        
        // We'll need to mock the LDAP template creation since we can't easily test the real LDAP connection
        // In a real test environment, you might use an embedded LDAP server
    }

    @Test
    void testAuthenticateWithValidCredentials() {
        // Given
        String userId = "testuser";
        String password = "testpass";
        String userDn = "CN=Test User,OU=Users,DC=company,DC=com";
        
        // Create a service with mocked LDAP template
        authenticationService = createMockedService();
        
        // Mock finding user DN
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(userDn))
            .thenReturn(Collections.singletonList(createTestUserDetails()));

        // Mock successful authentication
        try (MockedStatic<LdapContextSource> mockedStatic = mockStatic(LdapContextSource.class)) {
            LdapContextSource authContextSource = mock(LdapContextSource.class);
            mockedStatic.when(LdapContextSource::new).thenReturn(authContextSource);
            doNothing().when(authContextSource).setUrl(anyString());
            doNothing().when(authContextSource).setUserDn(anyString());
            doNothing().when(authContextSource).setPassword(anyString());
            doNothing().when(authContextSource).afterPropertiesSet();
            when(authContextSource.getContext(anyString(), anyString())).thenReturn(null);

            // When
            AuthenticationResult result = authenticationService.authenticate(userId, password);

            // Then
            assertTrue(result.isSuccess());
            assertEquals(userId, result.getUserId());
            assertNotNull(result.getUserDetails());
            assertNull(result.getErrorMessage());
        }
    }

    @Test
    void testAuthenticateWithInvalidCredentials() {
        // Given
        String userId = "testuser";
        String password = "wrongpass";
        String userDn = "CN=Test User,OU=Users,DC=company,DC=com";
        
        authenticationService = createMockedService();
        
        // Mock finding user DN
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(userDn));

        // Mock failed authentication
        try (MockedStatic<LdapContextSource> mockedStatic = mockStatic(LdapContextSource.class)) {
            LdapContextSource authContextSource = mock(LdapContextSource.class);
            mockedStatic.when(LdapContextSource::new).thenReturn(authContextSource);
            doNothing().when(authContextSource).setUrl(anyString());
            doNothing().when(authContextSource).setUserDn(anyString());
            doNothing().when(authContextSource).setPassword(anyString());
            doNothing().when(authContextSource).afterPropertiesSet();
            when(authContextSource.getContext(anyString(), anyString()))
                .thenThrow(new RuntimeException("Authentication failed"));

            // When
            AuthenticationResult result = authenticationService.authenticate(userId, password);

            // Then
            assertFalse(result.isSuccess());
            assertNull(result.getUserId());
            assertNull(result.getUserDetails());
            assertEquals("Invalid credentials", result.getErrorMessage());
        }
    }

    @Test
    void testAuthenticateWithEmptyUserId() {
        // Given
        authenticationService = createMockedService();

        // When
        AuthenticationResult result = authenticationService.authenticate("", "password");

        // Then
        assertFalse(result.isSuccess());
        assertEquals("User ID cannot be empty", result.getErrorMessage());
    }

    @Test
    void testAuthenticateWithNullUserId() {
        // Given
        authenticationService = createMockedService();

        // When
        AuthenticationResult result = authenticationService.authenticate(null, "password");

        // Then
        assertFalse(result.isSuccess());
        assertEquals("User ID cannot be empty", result.getErrorMessage());
    }

    @Test
    void testAuthenticateWithEmptyPassword() {
        // Given
        authenticationService = createMockedService();

        // When
        AuthenticationResult result = authenticationService.authenticate("testuser", "");

        // Then
        assertFalse(result.isSuccess());
        assertEquals("Password cannot be empty", result.getErrorMessage());
    }

    @Test
    void testAuthenticateWithNullPassword() {
        // Given
        authenticationService = createMockedService();

        // When
        AuthenticationResult result = authenticationService.authenticate("testuser", null);

        // Then
        assertFalse(result.isSuccess());
        assertEquals("Password cannot be empty", result.getErrorMessage());
    }

    @Test
    void testAuthenticateUserNotFound() {
        // Given
        String userId = "nonexistentuser";
        String password = "password";
        
        authenticationService = createMockedService();
        
        // Mock user not found
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.emptyList());

        // When
        AuthenticationResult result = authenticationService.authenticate(userId, password);

        // Then
        assertFalse(result.isSuccess());
        assertEquals("Invalid credentials", result.getErrorMessage());
    }

    @Test
    void testGetUserDetailsSuccess() {
        // Given
        String userId = "testuser";
        UserDetails expectedDetails = createTestUserDetails();
        
        authenticationService = createMockedService();
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(expectedDetails));

        // When
        UserDetails result = authenticationService.getUserDetails(userId);

        // Then
        assertNotNull(result);
        assertEquals(expectedDetails.getUserId(), result.getUserId());
        assertEquals(expectedDetails.getDisplayName(), result.getDisplayName());
        assertEquals(expectedDetails.getEmail(), result.getEmail());
    }

    @Test
    void testGetUserDetailsNotFound() {
        // Given
        String userId = "nonexistentuser";
        
        authenticationService = createMockedService();
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.emptyList());

        // When
        UserDetails result = authenticationService.getUserDetails(userId);

        // Then
        assertNull(result);
    }

    @Test
    void testGetUserDetailsWithEmptyUserId() {
        // Given
        authenticationService = createMockedService();

        // When
        UserDetails result = authenticationService.getUserDetails("");

        // Then
        assertNull(result);
    }

    @Test
    void testGetUserDetailsWithNullUserId() {
        // Given
        authenticationService = createMockedService();

        // When
        UserDetails result = authenticationService.getUserDetails(null);

        // Then
        assertNull(result);
    }

    @Test
    void testGetUserDetailsCaching() {
        // Given
        String userId = "testuser";
        UserDetails expectedDetails = createTestUserDetails();
        
        authenticationService = createMockedService();
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(expectedDetails));

        // When - call twice
        UserDetails result1 = authenticationService.getUserDetails(userId);
        UserDetails result2 = authenticationService.getUserDetails(userId);

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1.getUserId(), result2.getUserId());
        
        // Verify LDAP was only called once (second call should use cache)
        verify(ldapTemplate, times(1)).search(anyString(), anyString(), any(AttributesMapper.class));
    }

    @Test
    void testIsUserAuthorizedWithValidRole() {
        // Given
        String userId = "testuser";
        String operation = "file.read";
        UserDetails userDetails = createTestUserDetails();
        userDetails.setRoles(Arrays.asList("file.read", "file.download"));
        
        authenticationService = createMockedService();
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(userDetails));

        // When
        boolean result = authenticationService.isUserAuthorized(userId, operation);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsUserAuthorizedWithAdminRole() {
        // Given
        String userId = "testuser";
        String operation = "file.upload";
        UserDetails userDetails = createTestUserDetails();
        userDetails.setRoles(Collections.singletonList("admin"));
        
        authenticationService = createMockedService();
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(userDetails));

        // When
        boolean result = authenticationService.isUserAuthorized(userId, operation);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsUserAuthorizedWithoutRequiredRole() {
        // Given
        String userId = "testuser";
        String operation = "file.upload";
        UserDetails userDetails = createTestUserDetails();
        userDetails.setRoles(Collections.singletonList("file.read"));
        
        authenticationService = createMockedService();
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(userDetails));

        // When
        boolean result = authenticationService.isUserAuthorized(userId, operation);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsUserAuthorizedWithNullUserId() {
        // Given
        authenticationService = createMockedService();

        // When
        boolean result = authenticationService.isUserAuthorized(null, "file.read");

        // Then
        assertFalse(result);
    }

    @Test
    void testIsUserAuthorizedWithNullOperation() {
        // Given
        authenticationService = createMockedService();

        // When
        boolean result = authenticationService.isUserAuthorized("testuser", null);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsUserAuthorizedUserNotFound() {
        // Given
        String userId = "nonexistentuser";
        String operation = "file.read";
        
        authenticationService = createMockedService();
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.emptyList());

        // When
        boolean result = authenticationService.isUserAuthorized(userId, operation);

        // Then
        assertFalse(result);
    }

    @Test
    void testClearUserCache() {
        // Given
        authenticationService = createMockedService();
        
        // Add something to cache first
        UserDetails userDetails = createTestUserDetails();
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(userDetails));
        
        authenticationService.getUserDetails("testuser");
        assertEquals(1, authenticationService.getCacheSize());

        // When
        authenticationService.clearUserCache();

        // Then
        assertEquals(0, authenticationService.getCacheSize());
    }

    @Test
    void testGetCacheSize() {
        // Given
        authenticationService = createMockedService();
        assertEquals(0, authenticationService.getCacheSize());

        // When - add user to cache
        UserDetails userDetails = createTestUserDetails();
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenReturn(Collections.singletonList(userDetails));
        
        authenticationService.getUserDetails("testuser");

        // Then
        assertEquals(1, authenticationService.getCacheSize());
    }

    @Test
    void testUserDetailsAttributesMapper() throws NamingException {
        // Given
        Attributes attrs = new BasicAttributes();
        attrs.put(new BasicAttribute("sAMAccountName", "testuser"));
        attrs.put(new BasicAttribute("displayName", "Test User"));
        attrs.put(new BasicAttribute("mail", "test@company.com"));
        attrs.put(new BasicAttribute("department", "IT"));
        
        Attribute memberOfAttr = new BasicAttribute("memberOf");
        memberOfAttr.add("CN=Administrators,OU=Groups,DC=company,DC=com");
        memberOfAttr.add("CN=Developers,OU=Groups,DC=company,DC=com");
        attrs.put(memberOfAttr);

        // Create the mapper (we need to access the inner class)
        authenticationService = createMockedService();
        
        // We can't directly test the private inner class, but we can test the overall functionality
        // through the getUserDetails method which uses the mapper
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
            .thenAnswer(invocation -> {
                AttributesMapper<UserDetails> mapper = invocation.getArgument(2);
                return Collections.singletonList(mapper.mapFromAttributes(attrs));
            });

        // When
        UserDetails result = authenticationService.getUserDetails("testuser");

        // Then
        assertNotNull(result);
        assertEquals("testuser", result.getUserId());
        assertEquals("Test User", result.getDisplayName());
        assertEquals("test@company.com", result.getEmail());
        assertEquals("IT", result.getDepartment());
        assertEquals(Arrays.asList("Administrators", "Developers"), result.getGroups());
        assertTrue(result.getRoles().contains("admin"));
        assertTrue(result.getRoles().contains("file.read"));
    }

    private ArchiveSearchProperties createTestProperties() {
        ArchiveSearchProperties props = new ArchiveSearchProperties();
        props.setEnabled(true);
        
        ArchiveSearchProperties.LdapConfig ldapConfig = new ArchiveSearchProperties.LdapConfig();
        ldapConfig.setUrl("ldap://test-server:389");
        ldapConfig.setBaseDn("dc=company,dc=com");
        ldapConfig.setUserSearchBase("ou=users");
        ldapConfig.setUserSearchFilter("(sAMAccountName={0})");
        ldapConfig.setConnectionTimeout(5000);
        ldapConfig.setReadTimeout(10000);
        
        props.setLdap(ldapConfig);
        return props;
    }

    private UserDetails createTestUserDetails() {
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId("testuser");
        userDetails.setDisplayName("Test User");
        userDetails.setEmail("test@company.com");
        userDetails.setDepartment("IT");
        userDetails.setGroups(Arrays.asList("Users", "Developers"));
        userDetails.setRoles(Arrays.asList("file.read", "file.download"));
        return userDetails;
    }

    private LdapAuthenticationService createMockedService() {
        // Create a service that uses our mocked LDAP template
        LdapAuthenticationService service = new LdapAuthenticationService(properties) {
            @Override
            protected LdapTemplate createLdapTemplate() {
                return ldapTemplate;
            }
        };
        return service;
    }
}