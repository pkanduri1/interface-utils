package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuthResponse}.
 */
class AuthResponseTest {

    @Test
    void testDefaultConstructor() {
        AuthResponse response = new AuthResponse();
        
        assertNull(response.getToken());
        assertNull(response.getUserId());
        assertEquals(0, response.getExpiresIn());
        assertNull(response.getPermissions());
    }

    @Test
    void testParameterizedConstructor() {
        String token = "jwt-token-123";
        String userId = "john.doe";
        long expiresIn = 3600L;
        List<String> permissions = Arrays.asList("file.read", "file.upload");
        
        AuthResponse response = new AuthResponse(token, userId, expiresIn, permissions);
        
        assertEquals(token, response.getToken());
        assertEquals(userId, response.getUserId());
        assertEquals(expiresIn, response.getExpiresIn());
        assertEquals(permissions, response.getPermissions());
    }

    @Test
    void testGettersAndSetters() {
        AuthResponse response = new AuthResponse();
        String token = "jwt-token-456";
        String userId = "jane.smith";
        long expiresIn = 7200L;
        List<String> permissions = Arrays.asList("file.read", "file.download");
        
        response.setToken(token);
        response.setUserId(userId);
        response.setExpiresIn(expiresIn);
        response.setPermissions(permissions);
        
        assertEquals(token, response.getToken());
        assertEquals(userId, response.getUserId());
        assertEquals(expiresIn, response.getExpiresIn());
        assertEquals(permissions, response.getPermissions());
    }

    @Test
    void testToString() {
        String token = "jwt-token-123";
        String userId = "john.doe";
        long expiresIn = 3600L;
        List<String> permissions = Arrays.asList("file.read", "file.upload");
        
        AuthResponse response = new AuthResponse(token, userId, expiresIn, permissions);
        String toString = response.toString();
        
        assertTrue(toString.contains("john.doe"));
        assertTrue(toString.contains("3600"));
        assertTrue(toString.contains("[PROTECTED]"));
        assertTrue(toString.contains("file.read"));
        assertTrue(toString.contains("file.upload"));
        assertFalse(toString.contains("jwt-token-123"));
    }

    @Test
    void testEquals() {
        String token = "jwt-token-123";
        String userId = "john.doe";
        long expiresIn = 3600L;
        List<String> permissions = Arrays.asList("file.read", "file.upload");
        
        AuthResponse response1 = new AuthResponse(token, userId, expiresIn, permissions);
        AuthResponse response2 = new AuthResponse(token, userId, expiresIn, permissions);
        AuthResponse response3 = new AuthResponse("different-token", userId, expiresIn, permissions);
        AuthResponse response4 = new AuthResponse(token, "different-user", expiresIn, permissions);
        AuthResponse response5 = new AuthResponse(token, userId, 7200L, permissions);
        AuthResponse response6 = new AuthResponse(token, userId, expiresIn, Arrays.asList("different.permission"));
        
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
        assertNotEquals(response1, response4);
        assertNotEquals(response1, response5);
        assertNotEquals(response1, response6);
        assertNotEquals(response1, null);
        assertNotEquals(response1, "not an AuthResponse");
    }

    @Test
    void testEqualsWithNullFields() {
        AuthResponse response1 = new AuthResponse(null, null, 0L, null);
        AuthResponse response2 = new AuthResponse(null, null, 0L, null);
        AuthResponse response3 = new AuthResponse("token", null, 0L, null);
        
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
    }

    @Test
    void testHashCode() {
        String token = "jwt-token-123";
        String userId = "john.doe";
        long expiresIn = 3600L;
        List<String> permissions = Arrays.asList("file.read", "file.upload");
        
        AuthResponse response1 = new AuthResponse(token, userId, expiresIn, permissions);
        AuthResponse response2 = new AuthResponse(token, userId, expiresIn, permissions);
        AuthResponse response3 = new AuthResponse("different-token", userId, expiresIn, permissions);
        
        assertEquals(response1.hashCode(), response2.hashCode());
        assertNotEquals(response1.hashCode(), response3.hashCode());
    }

    @Test
    void testHashCodeWithNullFields() {
        AuthResponse response1 = new AuthResponse(null, null, 0L, null);
        AuthResponse response2 = new AuthResponse(null, null, 0L, null);
        
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testEqualsSameInstance() {
        AuthResponse response = new AuthResponse("token", "user", 3600L, Arrays.asList("permission"));
        
        assertEquals(response, response);
    }

    @Test
    void testWithEmptyPermissions() {
        AuthResponse response = new AuthResponse("token", "user", 3600L, Arrays.asList());
        
        assertNotNull(response.getPermissions());
        assertTrue(response.getPermissions().isEmpty());
    }

    @Test
    void testWithZeroExpiresIn() {
        AuthResponse response = new AuthResponse("token", "user", 0L, Arrays.asList("permission"));
        
        assertEquals(0L, response.getExpiresIn());
    }

    @Test
    void testWithNegativeExpiresIn() {
        AuthResponse response = new AuthResponse("token", "user", -1L, Arrays.asList("permission"));
        
        assertEquals(-1L, response.getExpiresIn());
    }
}