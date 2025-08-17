package com.fabric.watcher.archive.security;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.UserDetails;
import com.fabric.watcher.archive.service.LdapAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

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
 * Comprehensive security tests for user authorization and role-based access control.
 * Tests various authorization scenarios, role mappings, and security vulnerabilities.
 * 
 * Requirements: 8.2, 11.1
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationSecurityTest {

    @Mock
    private LdapTemplate ldapTemplate;

    @Mock
    private ArchiveSearchProperties properties;

    @Mock
    private ArchiveSearchProperties.LdapConfig ldapConfig;

    private LdapAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        when(properties.getLdap()).thenReturn(ldapConfig);
        when(ldapConfig.getUrl()).thenReturn("ldap://test-server:389");
        when(ldapConfig.getBaseDn()).thenReturn("dc=company,dc=com");
        when(ldapConfig.getUserSearchBase()).thenReturn("ou=users");
        when(ldapConfig.getUserSearchFilter()).thenReturn("(sAMAccountName={0})");
        
        authenticationService = createMockedService();
    }

    /**
     * Test authorization for different user roles and operations
     * Requirements: 8.2, 11.1
     */
    @Test
    void testUserRoleBasedAuthorization() {
        // Test 1: Admin user with full permissions
        String adminUser = "admin";
        UserDetails adminDetails = createUserWithGroups(adminUser, Arrays.asList("Administrators", "Domain Admins"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(adminDetails));

        assertTrue(authenticationService.isUserAuthorized(adminUser, "file.read"));
        assertTrue(authenticationService.isUserAuthorized(adminUser, "file.upload"));
        assertTrue(authenticationService.isUserAuthorized(adminUser, "file.download"));
        assertTrue(authenticationService.isUserAuthorized(adminUser, "file.delete"));
        assertTrue(authenticationService.isUserAuthorized(adminUser, "system.manage"));
        assertTrue(authenticationService.isUserAuthorized(adminUser, "admin"));

        // Test 2: Power user with elevated permissions
        String powerUser = "poweruser";
        UserDetails powerDetails = createUserWithGroups(powerUser, Arrays.asList("Power Users", "Developers"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(powerDetails));

        assertTrue(authenticationService.isUserAuthorized(powerUser, "file.read"));
        assertTrue(authenticationService.isUserAuthorized(powerUser, "file.upload"));
        assertTrue(authenticationService.isUserAuthorized(powerUser, "file.download"));
        assertFalse(authenticationService.isUserAuthorized(powerUser, "file.delete"));
        assertFalse(authenticationService.isUserAuthorized(powerUser, "system.manage"));
        assertFalse(authenticationService.isUserAuthorized(powerUser, "admin"));

        // Test 3: Regular user with basic permissions
        String regularUser = "regularuser";
        UserDetails regularDetails = createUserWithGroups(regularUser, Arrays.asList("Users", "Employees"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(regularDetails));

        assertTrue(authenticationService.isUserAuthorized(regularUser, "file.read"));
        assertFalse(authenticationService.isUserAuthorized(regularUser, "file.upload"));
        assertTrue(authenticationService.isUserAuthorized(regularUser, "file.download"));
        assertFalse(authenticationService.isUserAuthorized(regularUser, "file.delete"));
        assertFalse(authenticationService.isUserAuthorized(regularUser, "system.manage"));
        assertFalse(authenticationService.isUserAuthorized(regularUser, "admin"));

        // Test 4: Read-only user with minimal permissions
        String readOnlyUser = "readonlyuser";
        UserDetails readOnlyDetails = createUserWithGroups(readOnlyUser, Arrays.asList("Read Only Users"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(readOnlyDetails));

        assertTrue(authenticationService.isUserAuthorized(readOnlyUser, "file.read"));
        assertFalse(authenticationService.isUserAuthorized(readOnlyUser, "file.upload"));
        assertFalse(authenticationService.isUserAuthorized(readOnlyUser, "file.download"));
        assertFalse(authenticationService.isUserAuthorized(readOnlyUser, "file.delete"));
        assertFalse(authenticationService.isUserAuthorized(readOnlyUser, "system.manage"));
        assertFalse(authenticationService.isUserAuthorized(readOnlyUser, "admin"));

        // Test 5: Service account with specific permissions
        String serviceAccount = "serviceaccount";
        UserDetails serviceDetails = createUserWithGroups(serviceAccount, Arrays.asList("Service Accounts", "Batch Jobs"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(serviceDetails));

        assertTrue(authenticationService.isUserAuthorized(serviceAccount, "file.read"));
        assertTrue(authenticationService.isUserAuthorized(serviceAccount, "file.upload"));
        assertFalse(authenticationService.isUserAuthorized(serviceAccount, "file.download"));
        assertFalse(authenticationService.isUserAuthorized(serviceAccount, "file.delete"));
        assertTrue(authenticationService.isUserAuthorized(serviceAccount, "system.monitor"));
        assertFalse(authenticationService.isUserAuthorized(serviceAccount, "admin"));

        // Test 6: User with no groups (should have minimal access)
        String noGroupUser = "nogroupuser";
        UserDetails noGroupDetails = createUserWithGroups(noGroupUser, Collections.emptyList());
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(noGroupDetails));

        assertFalse(authenticationService.isUserAuthorized(noGroupUser, "file.read"));
        assertFalse(authenticationService.isUserAuthorized(noGroupUser, "file.upload"));
        assertFalse(authenticationService.isUserAuthorized(noGroupUser, "file.download"));
        assertFalse(authenticationService.isUserAuthorized(noGroupUser, "file.delete"));
        assertFalse(authenticationService.isUserAuthorized(noGroupUser, "system.manage"));
        assertFalse(authenticationService.isUserAuthorized(noGroupUser, "admin"));
    }

    /**
     * Test authorization with complex group hierarchies and nested permissions
     * Requirements: 8.2, 11.1
     */
    @Test
    void testComplexGroupHierarchyAuthorization() {
        // Test 1: User with multiple overlapping groups
        String multiGroupUser = "multigroupuser";
        UserDetails multiGroupDetails = createUserWithGroups(multiGroupUser, 
                Arrays.asList("Developers", "Testers", "Power Users", "File Managers"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(multiGroupDetails));

        // Should have permissions from all groups
        assertTrue(authenticationService.isUserAuthorized(multiGroupUser, "file.read"));
        assertTrue(authenticationService.isUserAuthorized(multiGroupUser, "file.upload"));
        assertTrue(authenticationService.isUserAuthorized(multiGroupUser, "file.download"));
        assertTrue(authenticationService.isUserAuthorized(multiGroupUser, "file.delete"));
        assertFalse(authenticationService.isUserAuthorized(multiGroupUser, "system.manage"));
        assertFalse(authenticationService.isUserAuthorized(multiGroupUser, "admin"));

        // Test 2: User with conflicting group permissions (most permissive should win)
        String conflictUser = "conflictuser";
        UserDetails conflictDetails = createUserWithGroups(conflictUser, 
                Arrays.asList("Read Only Users", "Administrators")); // Conflicting permissions
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(conflictDetails));

        // Admin permissions should override read-only
        assertTrue(authenticationService.isUserAuthorized(conflictUser, "file.read"));
        assertTrue(authenticationService.isUserAuthorized(conflictUser, "file.upload"));
        assertTrue(authenticationService.isUserAuthorized(conflictUser, "file.download"));
        assertTrue(authenticationService.isUserAuthorized(conflictUser, "file.delete"));
        assertTrue(authenticationService.isUserAuthorized(conflictUser, "system.manage"));
        assertTrue(authenticationService.isUserAuthorized(conflictUser, "admin"));

        // Test 3: User with department-specific groups
        String deptUser = "deptuser";
        UserDetails deptDetails = createUserWithGroups(deptUser, 
                Arrays.asList("IT Department", "Security Team", "Incident Response"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(deptDetails));

        assertTrue(authenticationService.isUserAuthorized(deptUser, "file.read"));
        assertTrue(authenticationService.isUserAuthorized(deptUser, "file.upload"));
        assertTrue(authenticationService.isUserAuthorized(deptUser, "file.download"));
        assertFalse(authenticationService.isUserAuthorized(deptUser, "file.delete"));
        assertTrue(authenticationService.isUserAuthorized(deptUser, "system.monitor"));
        assertFalse(authenticationService.isUserAuthorized(deptUser, "admin"));

        // Test 4: User with project-specific groups
        String projectUser = "projectuser";
        UserDetails projectDetails = createUserWithGroups(projectUser, 
                Arrays.asList("Project Alpha", "Project Beta", "External Contractors"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(projectDetails));

        assertTrue(authenticationService.isUserAuthorized(projectUser, "file.read"));
        assertFalse(authenticationService.isUserAuthorized(projectUser, "file.upload"));
        assertTrue(authenticationService.isUserAuthorized(projectUser, "file.download"));
        assertFalse(authenticationService.isUserAuthorized(projectUser, "file.delete"));
        assertFalse(authenticationService.isUserAuthorized(projectUser, "system.monitor"));
        assertFalse(authenticationService.isUserAuthorized(projectUser, "admin"));
    }

    /**
     * Test authorization security against privilege escalation attempts
     * Requirements: 8.2, 11.1
     */
    @Test
    void testPrivilegeEscalationPrevention() {
        // Test 1: User attempting to access unauthorized operations
        String limitedUser = "limiteduser";
        UserDetails limitedDetails = createUserWithGroups(limitedUser, Arrays.asList("Users"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(limitedDetails));

        // Should not be able to access admin functions
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "admin"));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "system.manage"));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "file.delete"));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "user.create"));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "config.modify"));

        // Test 2: Malicious operation names (injection attempts)
        String[] maliciousOperations = {
            "admin'; DROP TABLE users; --",
            "file.read OR 1=1",
            "admin\u0000",
            "admin\nfile.delete",
            "admin\\file.delete",
            "../admin",
            "file.read|admin",
            "file.read&admin",
            "$(admin)",
            "`admin`",
            "admin/*comment*/",
            "admin--comment"
        };

        for (String maliciousOp : maliciousOperations) {
            assertFalse(authenticationService.isUserAuthorized(limitedUser, maliciousOp),
                    "Should not authorize malicious operation: " + maliciousOp);
        }

        // Test 3: Case sensitivity in operations (should not bypass security)
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "ADMIN"));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "Admin"));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "aDmIn"));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "FILE.DELETE"));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "system.MANAGE"));

        // Test 4: Unicode and special characters in operations
        String[] specialOperations = {
            "admin\u200B", // Zero-width space
            "admin\uFEFF", // Byte order mark
            "admin\u00A0", // Non-breaking space
            "admin\t",     // Tab character
            "admin\r",     // Carriage return
            "admin\n",     // Line feed
            "admin\u0020", // Regular space
            "admin\u3000", // Ideographic space
            "admin\u2028", // Line separator
            "admin\u2029"  // Paragraph separator
        };

        for (String specialOp : specialOperations) {
            assertFalse(authenticationService.isUserAuthorized(limitedUser, specialOp),
                    "Should not authorize operation with special characters: " + specialOp);
        }

        // Test 5: Very long operation names (potential buffer overflow)
        String longOperation = "admin" + "a".repeat(10000);
        assertFalse(authenticationService.isUserAuthorized(limitedUser, longOperation));

        // Test 6: Empty and null operations
        assertFalse(authenticationService.isUserAuthorized(limitedUser, ""));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, null));
        assertFalse(authenticationService.isUserAuthorized(limitedUser, "   ")); // Whitespace only
    }

    /**
     * Test authorization with edge cases and boundary conditions
     * Requirements: 8.2, 11.1
     */
    @Test
    void testAuthorizationEdgeCases() {
        // Test 1: Non-existent user
        String nonExistentUser = "nonexistentuser";
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.emptyList());

        assertFalse(authenticationService.isUserAuthorized(nonExistentUser, "file.read"));
        assertFalse(authenticationService.isUserAuthorized(nonExistentUser, "admin"));

        // Test 2: User with null or empty user ID
        assertFalse(authenticationService.isUserAuthorized(null, "file.read"));
        assertFalse(authenticationService.isUserAuthorized("", "file.read"));
        assertFalse(authenticationService.isUserAuthorized("   ", "file.read"));

        // Test 3: User with special characters in username
        String[] specialUsernames = {
            "user@domain.com",
            "user.name",
            "user-name",
            "user_name",
            "user+tag",
            "user with spaces",
            "用户名", // Unicode
            "user\u0000control", // Control characters
            "'; DROP TABLE users; --", // SQL injection
            "user)(|(password=*)" // LDAP injection
        };

        for (String specialUsername : specialUsernames) {
            UserDetails specialDetails = createUserWithGroups(specialUsername, Arrays.asList("Users"));
            when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                    .thenReturn(Collections.singletonList(specialDetails));

            assertTrue(authenticationService.isUserAuthorized(specialUsername, "file.read"));
            assertFalse(authenticationService.isUserAuthorized(specialUsername, "admin"));
        }

        // Test 4: User with very long username
        String longUsername = "user" + "a".repeat(1000);
        UserDetails longUserDetails = createUserWithGroups(longUsername, Arrays.asList("Administrators"));
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(longUserDetails));

        assertTrue(authenticationService.isUserAuthorized(longUsername, "admin"));

        // Test 5: User with groups containing special characters
        String specialGroupUser = "specialgroupuser";
        UserDetails specialGroupDetails = createUserWithGroups(specialGroupUser, 
                Arrays.asList("Group with spaces", "Group-with-dashes", "Group_with_underscores", 
                             "Group.with.dots", "Group@domain.com", "Group+tag"));
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(specialGroupDetails));

        // Should still work with special group names
        assertTrue(authenticationService.isUserAuthorized(specialGroupUser, "file.read"));

        // Test 6: User with duplicate groups
        String duplicateGroupUser = "duplicategroupuser";
        UserDetails duplicateGroupDetails = createUserWithGroups(duplicateGroupUser, 
                Arrays.asList("Users", "Users", "Administrators", "Administrators"));
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(duplicateGroupDetails));

        assertTrue(authenticationService.isUserAuthorized(duplicateGroupUser, "admin"));
        assertTrue(authenticationService.isUserAuthorized(duplicateGroupUser, "file.read"));
    }

    /**
     * Test authorization caching and performance
     * Requirements: 8.2, 11.1
     */
    @Test
    void testAuthorizationCachingAndPerformance() {
        String cachedUser = "cacheduser";
        UserDetails cachedDetails = createUserWithGroups(cachedUser, Arrays.asList("Administrators"));
        
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(cachedDetails));

        // First call should hit LDAP
        assertTrue(authenticationService.isUserAuthorized(cachedUser, "admin"));
        verify(ldapTemplate, times(1)).search(anyString(), anyString(), any(AttributesMapper.class));

        // Second call should use cache (no additional LDAP call)
        assertTrue(authenticationService.isUserAuthorized(cachedUser, "file.read"));
        verify(ldapTemplate, times(1)).search(anyString(), anyString(), any(AttributesMapper.class));

        // Third call with different operation should still use cache
        assertTrue(authenticationService.isUserAuthorized(cachedUser, "system.manage"));
        verify(ldapTemplate, times(1)).search(anyString(), anyString(), any(AttributesMapper.class));

        // Test cache size
        assertEquals(1, authenticationService.getCacheSize());

        // Clear cache and verify
        authenticationService.clearUserCache();
        assertEquals(0, authenticationService.getCacheSize());

        // Next call should hit LDAP again
        assertTrue(authenticationService.isUserAuthorized(cachedUser, "admin"));
        verify(ldapTemplate, times(2)).search(anyString(), anyString(), any(AttributesMapper.class));
    }

    /**
     * Test authorization with LDAP attribute mapping security
     * Requirements: 8.2, 11.1
     */
    @Test
    void testLdapAttributeMappingSecurity() throws NamingException {
        String testUser = "testuser";
        
        // Test 1: Normal LDAP attributes
        Attributes normalAttrs = new BasicAttributes();
        normalAttrs.put(new BasicAttribute("sAMAccountName", testUser));
        normalAttrs.put(new BasicAttribute("displayName", "Test User"));
        normalAttrs.put(new BasicAttribute("mail", "test@company.com"));
        normalAttrs.put(new BasicAttribute("department", "IT"));
        
        Attribute memberOfAttr = new BasicAttribute("memberOf");
        memberOfAttr.add("CN=Administrators,OU=Groups,DC=company,DC=com");
        memberOfAttr.add("CN=Users,OU=Groups,DC=company,DC=com");
        normalAttrs.put(memberOfAttr);

        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenAnswer(invocation -> {
                    AttributesMapper<UserDetails> mapper = invocation.getArgument(2);
                    return Collections.singletonList(mapper.mapFromAttributes(normalAttrs));
                });

        assertTrue(authenticationService.isUserAuthorized(testUser, "admin"));

        // Test 2: LDAP attributes with injection attempts
        Attributes maliciousAttrs = new BasicAttributes();
        maliciousAttrs.put(new BasicAttribute("sAMAccountName", testUser));
        maliciousAttrs.put(new BasicAttribute("displayName", "'; DROP TABLE users; --"));
        maliciousAttrs.put(new BasicAttribute("mail", "test@company.com<script>alert('xss')</script>"));
        maliciousAttrs.put(new BasicAttribute("department", "IT\u0000\u0001"));
        
        Attribute maliciousMemberOfAttr = new BasicAttribute("memberOf");
        maliciousMemberOfAttr.add("CN=Administrators'; DROP TABLE groups; --,OU=Groups,DC=company,DC=com");
        maliciousMemberOfAttr.add("CN=Users<script>alert('xss')</script>,OU=Groups,DC=company,DC=com");
        maliciousAttrs.put(maliciousMemberOfAttr);

        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenAnswer(invocation -> {
                    AttributesMapper<UserDetails> mapper = invocation.getArgument(2);
                    return Collections.singletonList(mapper.mapFromAttributes(maliciousAttrs));
                });

        // Should still work but not be vulnerable to injection
        assertTrue(authenticationService.isUserAuthorized(testUser, "admin"));

        // Test 3: LDAP attributes with Unicode and special characters
        Attributes unicodeAttrs = new BasicAttributes();
        unicodeAttrs.put(new BasicAttribute("sAMAccountName", testUser));
        unicodeAttrs.put(new BasicAttribute("displayName", "测试用户"));
        unicodeAttrs.put(new BasicAttribute("mail", "用户@公司.com"));
        unicodeAttrs.put(new BasicAttribute("department", "信息技术"));
        
        Attribute unicodeMemberOfAttr = new BasicAttribute("memberOf");
        unicodeMemberOfAttr.add("CN=管理员,OU=Groups,DC=company,DC=com");
        unicodeMemberOfAttr.add("CN=用户,OU=Groups,DC=company,DC=com");
        unicodeAttrs.put(unicodeMemberOfAttr);

        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenAnswer(invocation -> {
                    AttributesMapper<UserDetails> mapper = invocation.getArgument(2);
                    return Collections.singletonList(mapper.mapFromAttributes(unicodeAttrs));
                });

        // Should handle Unicode characters properly
        assertTrue(authenticationService.isUserAuthorized(testUser, "admin"));
    }

    // Helper methods

    private UserDetails createUserWithGroups(String userId, List<String> groups) {
        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(userId);
        userDetails.setDisplayName("Test User " + userId);
        userDetails.setEmail(userId + "@test.com");
        userDetails.setDepartment("IT");
        userDetails.setGroups(groups);
        
        // Map groups to roles based on business logic
        List<String> roles = mapGroupsToRoles(groups);
        userDetails.setRoles(roles);
        
        return userDetails;
    }

    private List<String> mapGroupsToRoles(List<String> groups) {
        // Simulate the role mapping logic from the actual service
        if (groups.contains("Administrators") || groups.contains("Domain Admins")) {
            return Arrays.asList("admin", "file.read", "file.upload", "file.download", "file.delete", "system.manage");
        } else if (groups.contains("Power Users") || groups.contains("Developers")) {
            return Arrays.asList("file.read", "file.upload", "file.download");
        } else if (groups.contains("Users") || groups.contains("Employees")) {
            return Arrays.asList("file.read", "file.download");
        } else if (groups.contains("Read Only Users")) {
            return Arrays.asList("file.read");
        } else if (groups.contains("Service Accounts") || groups.contains("Batch Jobs")) {
            return Arrays.asList("file.read", "file.upload", "system.monitor");
        } else if (groups.contains("IT Department") || groups.contains("Security Team")) {
            return Arrays.asList("file.read", "file.upload", "file.download", "system.monitor");
        } else if (groups.contains("File Managers")) {
            return Arrays.asList("file.read", "file.upload", "file.download", "file.delete");
        } else if (groups.contains("Project Alpha") || groups.contains("Project Beta") || groups.contains("External Contractors")) {
            return Arrays.asList("file.read", "file.download");
        } else {
            return Collections.emptyList();
        }
    }

    private LdapAuthenticationService createMockedService() {
        return new LdapAuthenticationService(properties) {
            @Override
            protected org.springframework.ldap.core.LdapTemplate createLdapTemplate() {
                return ldapTemplate;
            }
        };
    }
}