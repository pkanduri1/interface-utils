package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuthenticationResult}.
 */
class AuthenticationResultTest {

    @Test
    void testDefaultConstructor() {
        AuthenticationResult result = new AuthenticationResult();
        
        assertFalse(result.isSuccess());
        assertNull(result.getUserId());
        assertNull(result.getErrorMessage());
        assertNull(result.getUserDetails());
    }

    @Test
    void testSuccessfulAuthenticationConstructor() {
        String userId = "john.doe";
        UserDetails userDetails = new UserDetails(userId, "John Doe", "john@company.com", 
                                                 "IT", Arrays.asList("Developers"), Arrays.asList("file.read"));
        
        AuthenticationResult result = new AuthenticationResult(userId, userDetails);
        
        assertTrue(result.isSuccess());
        assertEquals(userId, result.getUserId());
        assertNull(result.getErrorMessage());
        assertEquals(userDetails, result.getUserDetails());
    }

    @Test
    void testFailedAuthenticationConstructor() {
        String errorMessage = "Invalid credentials";
        
        AuthenticationResult result = new AuthenticationResult(errorMessage);
        
        assertFalse(result.isSuccess());
        assertNull(result.getUserId());
        assertEquals(errorMessage, result.getErrorMessage());
        assertNull(result.getUserDetails());
    }

    @Test
    void testFullParameterConstructor() {
        boolean success = true;
        String userId = "jane.smith";
        String errorMessage = null;
        UserDetails userDetails = new UserDetails(userId, "Jane Smith", "jane@company.com", 
                                                 "HR", Arrays.asList("Managers"), Arrays.asList("file.upload"));
        
        AuthenticationResult result = new AuthenticationResult(success, userId, errorMessage, userDetails);
        
        assertEquals(success, result.isSuccess());
        assertEquals(userId, result.getUserId());
        assertEquals(errorMessage, result.getErrorMessage());
        assertEquals(userDetails, result.getUserDetails());
    }

    @Test
    void testGettersAndSetters() {
        AuthenticationResult result = new AuthenticationResult();
        boolean success = true;
        String userId = "test.user";
        String errorMessage = "Test error";
        UserDetails userDetails = new UserDetails(userId, "Test User", "test@company.com", 
                                                 "Test", Arrays.asList("Testers"), Arrays.asList("test.permission"));
        
        result.setSuccess(success);
        result.setUserId(userId);
        result.setErrorMessage(errorMessage);
        result.setUserDetails(userDetails);
        
        assertEquals(success, result.isSuccess());
        assertEquals(userId, result.getUserId());
        assertEquals(errorMessage, result.getErrorMessage());
        assertEquals(userDetails, result.getUserDetails());
    }

    @Test
    void testToString() {
        String userId = "john.doe";
        String errorMessage = "Test error";
        UserDetails userDetails = new UserDetails(userId, "John Doe", "john@company.com", 
                                                 "IT", Arrays.asList("Developers"), Arrays.asList("file.read"));
        
        AuthenticationResult result = new AuthenticationResult(true, userId, errorMessage, userDetails);
        String toString = result.toString();
        
        assertTrue(toString.contains("success=true"));
        assertTrue(toString.contains("userId='john.doe'"));
        assertTrue(toString.contains("errorMessage='Test error'"));
        assertTrue(toString.contains("userDetails="));
    }

    @Test
    void testEquals() {
        String userId = "john.doe";
        String errorMessage = "Error";
        UserDetails userDetails = new UserDetails(userId, "John Doe", "john@company.com", 
                                                 "IT", Arrays.asList("Developers"), Arrays.asList("file.read"));
        
        AuthenticationResult result1 = new AuthenticationResult(true, userId, errorMessage, userDetails);
        AuthenticationResult result2 = new AuthenticationResult(true, userId, errorMessage, userDetails);
        AuthenticationResult result3 = new AuthenticationResult(false, userId, errorMessage, userDetails);
        AuthenticationResult result4 = new AuthenticationResult(true, "different", errorMessage, userDetails);
        AuthenticationResult result5 = new AuthenticationResult(true, userId, "different", userDetails);
        AuthenticationResult result6 = new AuthenticationResult(true, userId, errorMessage, null);
        
        assertEquals(result1, result2);
        assertNotEquals(result1, result3);
        assertNotEquals(result1, result4);
        assertNotEquals(result1, result5);
        assertNotEquals(result1, result6);
        assertNotEquals(result1, null);
        assertNotEquals(result1, "not an AuthenticationResult");
    }

    @Test
    void testEqualsWithNullFields() {
        AuthenticationResult result1 = new AuthenticationResult(false, null, null, null);
        AuthenticationResult result2 = new AuthenticationResult(false, null, null, null);
        AuthenticationResult result3 = new AuthenticationResult(false, "user", null, null);
        
        assertEquals(result1, result2);
        assertNotEquals(result1, result3);
    }

    @Test
    void testHashCode() {
        String userId = "john.doe";
        String errorMessage = "Error";
        UserDetails userDetails = new UserDetails(userId, "John Doe", "john@company.com", 
                                                 "IT", Arrays.asList("Developers"), Arrays.asList("file.read"));
        
        AuthenticationResult result1 = new AuthenticationResult(true, userId, errorMessage, userDetails);
        AuthenticationResult result2 = new AuthenticationResult(true, userId, errorMessage, userDetails);
        AuthenticationResult result3 = new AuthenticationResult(false, userId, errorMessage, userDetails);
        
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1.hashCode(), result3.hashCode());
    }

    @Test
    void testHashCodeWithNullFields() {
        AuthenticationResult result1 = new AuthenticationResult(false, null, null, null);
        AuthenticationResult result2 = new AuthenticationResult(false, null, null, null);
        
        assertEquals(result1.hashCode(), result2.hashCode());
    }

    @Test
    void testEqualsSameInstance() {
        AuthenticationResult result = new AuthenticationResult("Error");
        
        assertEquals(result, result);
    }

    @Test
    void testSuccessfulResultHasNoError() {
        String userId = "john.doe";
        UserDetails userDetails = new UserDetails(userId, "John Doe", "john@company.com", 
                                                 "IT", Arrays.asList("Developers"), Arrays.asList("file.read"));
        
        AuthenticationResult result = new AuthenticationResult(userId, userDetails);
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }

    @Test
    void testFailedResultHasNoUserDetails() {
        AuthenticationResult result = new AuthenticationResult("Authentication failed");
        
        assertFalse(result.isSuccess());
        assertNull(result.getUserDetails());
        assertNull(result.getUserId());
    }
}