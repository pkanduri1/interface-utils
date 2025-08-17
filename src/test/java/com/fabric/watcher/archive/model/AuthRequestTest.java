package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuthRequest}.
 */
class AuthRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testDefaultConstructor() {
        AuthRequest request = new AuthRequest();
        assertNull(request.getUserId());
        assertNull(request.getPassword());
    }

    @Test
    void testParameterizedConstructor() {
        String userId = "john.doe";
        String password = "password123";
        
        AuthRequest request = new AuthRequest(userId, password);
        
        assertEquals(userId, request.getUserId());
        assertEquals(password, request.getPassword());
    }

    @Test
    void testGettersAndSetters() {
        AuthRequest request = new AuthRequest();
        String userId = "jane.smith";
        String password = "secret456";
        
        request.setUserId(userId);
        request.setPassword(password);
        
        assertEquals(userId, request.getUserId());
        assertEquals(password, request.getPassword());
    }

    @Test
    void testValidRequest() {
        AuthRequest request = new AuthRequest("john.doe", "password123");
        
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        
        assertTrue(violations.isEmpty());
    }

    @Test
    void testBlankUserId() {
        AuthRequest request = new AuthRequest("", "password123");
        
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("User ID cannot be blank")));
    }

    @Test
    void testNullUserId() {
        AuthRequest request = new AuthRequest(null, "password123");
        
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("User ID cannot be blank")));
    }

    @Test
    void testBlankPassword() {
        AuthRequest request = new AuthRequest("john.doe", "");
        
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Password cannot be blank")));
    }

    @Test
    void testNullPassword() {
        AuthRequest request = new AuthRequest("john.doe", null);
        
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Password cannot be blank")));
    }

    @Test
    void testUserIdTooLong() {
        String longUserId = "a".repeat(101); // 101 characters
        AuthRequest request = new AuthRequest(longUserId, "password123");
        
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("User ID must be between 1 and 100 characters")));
    }

    @Test
    void testPasswordTooLong() {
        String longPassword = "a".repeat(256); // 256 characters
        AuthRequest request = new AuthRequest("john.doe", longPassword);
        
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Password must be between 1 and 255 characters")));
    }

    @Test
    void testToString() {
        AuthRequest request = new AuthRequest("john.doe", "password123");
        
        String toString = request.toString();
        
        assertTrue(toString.contains("john.doe"));
        assertTrue(toString.contains("[PROTECTED]"));
        assertFalse(toString.contains("password123"));
    }

    @Test
    void testEquals() {
        AuthRequest request1 = new AuthRequest("john.doe", "password123");
        AuthRequest request2 = new AuthRequest("john.doe", "password123");
        AuthRequest request3 = new AuthRequest("jane.smith", "password123");
        AuthRequest request4 = new AuthRequest("john.doe", "different");
        
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
        assertNotEquals(request1, request4);
        assertNotEquals(request1, null);
        assertNotEquals(request1, "not an AuthRequest");
    }

    @Test
    void testEqualsWithNullFields() {
        AuthRequest request1 = new AuthRequest(null, null);
        AuthRequest request2 = new AuthRequest(null, null);
        AuthRequest request3 = new AuthRequest("john.doe", null);
        AuthRequest request4 = new AuthRequest(null, "password");
        
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
        assertNotEquals(request1, request4);
    }

    @Test
    void testHashCode() {
        AuthRequest request1 = new AuthRequest("john.doe", "password123");
        AuthRequest request2 = new AuthRequest("john.doe", "password123");
        AuthRequest request3 = new AuthRequest("jane.smith", "password123");
        
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1.hashCode(), request3.hashCode());
    }

    @Test
    void testHashCodeWithNullFields() {
        AuthRequest request1 = new AuthRequest(null, null);
        AuthRequest request2 = new AuthRequest(null, null);
        
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void testEqualsSameInstance() {
        AuthRequest request = new AuthRequest("john.doe", "password123");
        
        assertEquals(request, request);
    }
}