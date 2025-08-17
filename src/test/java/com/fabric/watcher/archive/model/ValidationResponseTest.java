package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationResponse model.
 */
class ValidationResponseTest {

    @Test
    void testDefaultConstructor() {
        // Act
        ValidationResponse response = new ValidationResponse();

        // Assert
        assertFalse(response.isValid());
        assertNull(response.getUserId());
        assertNull(response.getRemainingTime());
        assertNull(response.getMessage());
    }

    @Test
    void testConstructorForValidSession() {
        // Arrange
        String userId = "testuser";
        Long remainingTime = 1800L;

        // Act
        ValidationResponse response = new ValidationResponse(userId, remainingTime);

        // Assert
        assertTrue(response.isValid());
        assertEquals(userId, response.getUserId());
        assertEquals(remainingTime, response.getRemainingTime());
        assertNull(response.getMessage());
    }

    @Test
    void testConstructorForInvalidSession() {
        // Arrange
        String message = "Token expired";

        // Act
        ValidationResponse response = new ValidationResponse(message);

        // Assert
        assertFalse(response.isValid());
        assertNull(response.getUserId());
        assertNull(response.getRemainingTime());
        assertEquals(message, response.getMessage());
    }

    @Test
    void testSettersAndGetters() {
        // Arrange
        ValidationResponse response = new ValidationResponse();
        String userId = "testuser";
        Long remainingTime = 3600L;
        String message = "Session valid";

        // Act
        response.setValid(true);
        response.setUserId(userId);
        response.setRemainingTime(remainingTime);
        response.setMessage(message);

        // Assert
        assertTrue(response.isValid());
        assertEquals(userId, response.getUserId());
        assertEquals(remainingTime, response.getRemainingTime());
        assertEquals(message, response.getMessage());
    }

    @Test
    void testToString() {
        // Arrange
        ValidationResponse response = new ValidationResponse("testuser", 1800L);

        // Act
        String toString = response.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.contains("ValidationResponse"));
        assertTrue(toString.contains("valid=true"));
        assertTrue(toString.contains("userId='testuser'"));
        assertTrue(toString.contains("remainingTime=1800"));
    }

    @Test
    void testToStringWithInvalidSession() {
        // Arrange
        ValidationResponse response = new ValidationResponse("Token expired");

        // Act
        String toString = response.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.contains("ValidationResponse"));
        assertTrue(toString.contains("valid=false"));
        assertTrue(toString.contains("message='Token expired'"));
    }

    @Test
    void testEquals() {
        // Arrange
        ValidationResponse response1 = new ValidationResponse("testuser", 1800L);
        ValidationResponse response2 = new ValidationResponse("testuser", 1800L);
        ValidationResponse response3 = new ValidationResponse("otheruser", 1800L);
        ValidationResponse response4 = new ValidationResponse("Token expired");

        // Act & Assert
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
        assertNotEquals(response1, response4);
        assertNotEquals(response1, null);
        assertNotEquals(response1, "not a ValidationResponse");
    }

    @Test
    void testEqualsWithSameInstance() {
        // Arrange
        ValidationResponse response = new ValidationResponse("testuser", 1800L);

        // Act & Assert
        assertEquals(response, response);
    }

    @Test
    void testEqualsWithNullFields() {
        // Arrange
        ValidationResponse response1 = new ValidationResponse();
        ValidationResponse response2 = new ValidationResponse();
        
        response1.setValid(true);
        response2.setValid(true);

        // Act & Assert
        assertEquals(response1, response2);
    }

    @Test
    void testHashCode() {
        // Arrange
        ValidationResponse response1 = new ValidationResponse("testuser", 1800L);
        ValidationResponse response2 = new ValidationResponse("testuser", 1800L);
        ValidationResponse response3 = new ValidationResponse("otheruser", 1800L);

        // Act & Assert
        assertEquals(response1.hashCode(), response2.hashCode());
        assertNotEquals(response1.hashCode(), response3.hashCode());
    }

    @Test
    void testHashCodeWithNullFields() {
        // Arrange
        ValidationResponse response1 = new ValidationResponse();
        ValidationResponse response2 = new ValidationResponse();

        // Act & Assert
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testEqualsWithDifferentValidStatus() {
        // Arrange
        ValidationResponse response1 = new ValidationResponse("testuser", 1800L);
        ValidationResponse response2 = new ValidationResponse("Token expired");

        // Act & Assert
        assertNotEquals(response1, response2);
    }

    @Test
    void testEqualsWithDifferentRemainingTime() {
        // Arrange
        ValidationResponse response1 = new ValidationResponse("testuser", 1800L);
        ValidationResponse response2 = new ValidationResponse("testuser", 3600L);

        // Act & Assert
        assertNotEquals(response1, response2);
    }

    @Test
    void testEqualsWithDifferentMessage() {
        // Arrange
        ValidationResponse response1 = new ValidationResponse("Token expired");
        ValidationResponse response2 = new ValidationResponse("Session not found");

        // Act & Assert
        assertNotEquals(response1, response2);
    }

    @Test
    void testValidationResponseWithZeroRemainingTime() {
        // Arrange
        String userId = "testuser";
        Long remainingTime = 0L;

        // Act
        ValidationResponse response = new ValidationResponse(userId, remainingTime);

        // Assert
        assertTrue(response.isValid());
        assertEquals(userId, response.getUserId());
        assertEquals(remainingTime, response.getRemainingTime());
    }

    @Test
    void testValidationResponseWithNegativeRemainingTime() {
        // Arrange
        String userId = "testuser";
        Long remainingTime = -100L;

        // Act
        ValidationResponse response = new ValidationResponse(userId, remainingTime);

        // Assert
        assertTrue(response.isValid());
        assertEquals(userId, response.getUserId());
        assertEquals(remainingTime, response.getRemainingTime());
    }

    @Test
    void testValidationResponseWithEmptyMessage() {
        // Arrange
        String message = "";

        // Act
        ValidationResponse response = new ValidationResponse(message);

        // Assert
        assertFalse(response.isValid());
        assertEquals(message, response.getMessage());
    }

    @Test
    void testValidationResponseWithNullMessage() {
        // Arrange
        String message = null;

        // Act
        ValidationResponse response = new ValidationResponse(message);

        // Assert
        assertFalse(response.isValid());
        assertNull(response.getMessage());
    }
}