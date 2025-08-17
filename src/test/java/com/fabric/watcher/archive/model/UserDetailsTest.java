package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UserDetails}.
 */
class UserDetailsTest {

    @Test
    void testDefaultConstructor() {
        UserDetails userDetails = new UserDetails();
        
        assertNull(userDetails.getUserId());
        assertNull(userDetails.getDisplayName());
        assertNull(userDetails.getEmail());
        assertNull(userDetails.getDepartment());
        assertNull(userDetails.getGroups());
        assertNull(userDetails.getRoles());
    }

    @Test
    void testParameterizedConstructor() {
        String userId = "john.doe";
        String displayName = "John Doe";
        String email = "john.doe@company.com";
        String department = "IT";
        List<String> groups = Arrays.asList("Administrators", "Developers");
        List<String> roles = Arrays.asList("file.read", "file.upload");
        
        UserDetails userDetails = new UserDetails(userId, displayName, email, department, groups, roles);
        
        assertEquals(userId, userDetails.getUserId());
        assertEquals(displayName, userDetails.getDisplayName());
        assertEquals(email, userDetails.getEmail());
        assertEquals(department, userDetails.getDepartment());
        assertEquals(groups, userDetails.getGroups());
        assertEquals(roles, userDetails.getRoles());
    }

    @Test
    void testGettersAndSetters() {
        UserDetails userDetails = new UserDetails();
        String userId = "jane.smith";
        String displayName = "Jane Smith";
        String email = "jane.smith@company.com";
        String department = "HR";
        List<String> groups = Arrays.asList("Managers", "HR Staff");
        List<String> roles = Arrays.asList("user.manage", "report.view");
        
        userDetails.setUserId(userId);
        userDetails.setDisplayName(displayName);
        userDetails.setEmail(email);
        userDetails.setDepartment(department);
        userDetails.setGroups(groups);
        userDetails.setRoles(roles);
        
        assertEquals(userId, userDetails.getUserId());
        assertEquals(displayName, userDetails.getDisplayName());
        assertEquals(email, userDetails.getEmail());
        assertEquals(department, userDetails.getDepartment());
        assertEquals(groups, userDetails.getGroups());
        assertEquals(roles, userDetails.getRoles());
    }

    @Test
    void testToString() {
        String userId = "john.doe";
        String displayName = "John Doe";
        String email = "john.doe@company.com";
        String department = "IT";
        List<String> groups = Arrays.asList("Administrators", "Developers");
        List<String> roles = Arrays.asList("file.read", "file.upload");
        
        UserDetails userDetails = new UserDetails(userId, displayName, email, department, groups, roles);
        String toString = userDetails.toString();
        
        assertTrue(toString.contains("john.doe"));
        assertTrue(toString.contains("John Doe"));
        assertTrue(toString.contains("john.doe@company.com"));
        assertTrue(toString.contains("IT"));
        assertTrue(toString.contains("Administrators"));
        assertTrue(toString.contains("file.read"));
    }

    @Test
    void testEquals() {
        String userId = "john.doe";
        String displayName = "John Doe";
        String email = "john.doe@company.com";
        String department = "IT";
        List<String> groups = Arrays.asList("Administrators", "Developers");
        List<String> roles = Arrays.asList("file.read", "file.upload");
        
        UserDetails userDetails1 = new UserDetails(userId, displayName, email, department, groups, roles);
        UserDetails userDetails2 = new UserDetails(userId, displayName, email, department, groups, roles);
        UserDetails userDetails3 = new UserDetails("different", displayName, email, department, groups, roles);
        UserDetails userDetails4 = new UserDetails(userId, "Different Name", email, department, groups, roles);
        UserDetails userDetails5 = new UserDetails(userId, displayName, "different@email.com", department, groups, roles);
        UserDetails userDetails6 = new UserDetails(userId, displayName, email, "Different Dept", groups, roles);
        UserDetails userDetails7 = new UserDetails(userId, displayName, email, department, Arrays.asList("Different"), roles);
        UserDetails userDetails8 = new UserDetails(userId, displayName, email, department, groups, Arrays.asList("different.role"));
        
        assertEquals(userDetails1, userDetails2);
        assertNotEquals(userDetails1, userDetails3);
        assertNotEquals(userDetails1, userDetails4);
        assertNotEquals(userDetails1, userDetails5);
        assertNotEquals(userDetails1, userDetails6);
        assertNotEquals(userDetails1, userDetails7);
        assertNotEquals(userDetails1, userDetails8);
        assertNotEquals(userDetails1, null);
        assertNotEquals(userDetails1, "not a UserDetails");
    }

    @Test
    void testEqualsWithNullFields() {
        UserDetails userDetails1 = new UserDetails(null, null, null, null, null, null);
        UserDetails userDetails2 = new UserDetails(null, null, null, null, null, null);
        UserDetails userDetails3 = new UserDetails("user", null, null, null, null, null);
        
        assertEquals(userDetails1, userDetails2);
        assertNotEquals(userDetails1, userDetails3);
    }

    @Test
    void testHashCode() {
        String userId = "john.doe";
        String displayName = "John Doe";
        String email = "john.doe@company.com";
        String department = "IT";
        List<String> groups = Arrays.asList("Administrators", "Developers");
        List<String> roles = Arrays.asList("file.read", "file.upload");
        
        UserDetails userDetails1 = new UserDetails(userId, displayName, email, department, groups, roles);
        UserDetails userDetails2 = new UserDetails(userId, displayName, email, department, groups, roles);
        UserDetails userDetails3 = new UserDetails("different", displayName, email, department, groups, roles);
        
        assertEquals(userDetails1.hashCode(), userDetails2.hashCode());
        assertNotEquals(userDetails1.hashCode(), userDetails3.hashCode());
    }

    @Test
    void testHashCodeWithNullFields() {
        UserDetails userDetails1 = new UserDetails(null, null, null, null, null, null);
        UserDetails userDetails2 = new UserDetails(null, null, null, null, null, null);
        
        assertEquals(userDetails1.hashCode(), userDetails2.hashCode());
    }

    @Test
    void testEqualsSameInstance() {
        UserDetails userDetails = new UserDetails("user", "User", "user@company.com", 
                                                 "Dept", Arrays.asList("Group"), Arrays.asList("Role"));
        
        assertEquals(userDetails, userDetails);
    }

    @Test
    void testWithEmptyCollections() {
        UserDetails userDetails = new UserDetails("user", "User", "user@company.com", 
                                                 "Dept", Arrays.asList(), Arrays.asList());
        
        assertNotNull(userDetails.getGroups());
        assertNotNull(userDetails.getRoles());
        assertTrue(userDetails.getGroups().isEmpty());
        assertTrue(userDetails.getRoles().isEmpty());
    }

    @Test
    void testWithSingleItemCollections() {
        List<String> groups = Arrays.asList("SingleGroup");
        List<String> roles = Arrays.asList("single.role");
        
        UserDetails userDetails = new UserDetails("user", "User", "user@company.com", 
                                                 "Dept", groups, roles);
        
        assertEquals(1, userDetails.getGroups().size());
        assertEquals(1, userDetails.getRoles().size());
        assertEquals("SingleGroup", userDetails.getGroups().get(0));
        assertEquals("single.role", userDetails.getRoles().get(0));
    }
}