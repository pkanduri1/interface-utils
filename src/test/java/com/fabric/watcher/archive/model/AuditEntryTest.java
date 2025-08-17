package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuditEntry}.
 */
class AuditEntryTest {

    @Test
    void testDefaultConstructor() {
        LocalDateTime beforeCreation = LocalDateTime.now();
        AuditEntry entry = new AuditEntry();
        LocalDateTime afterCreation = LocalDateTime.now();
        
        assertNotNull(entry.getTimestamp());
        assertTrue(entry.getTimestamp().isAfter(beforeCreation.minusSeconds(1)));
        assertTrue(entry.getTimestamp().isBefore(afterCreation.plusSeconds(1)));
        assertNull(entry.getUserId());
        assertNull(entry.getOperation());
        assertNull(entry.getResource());
        assertFalse(entry.isSuccess());
        assertNull(entry.getDetails());
        assertNull(entry.getIpAddress());
        assertNull(entry.getSessionId());
        assertNull(entry.getUserAgent());
    }

    @Test
    void testRequiredParametersConstructor() {
        String userId = "john.doe";
        String operation = "FILE_UPLOAD";
        String resource = "/opt/uploads/config.properties";
        boolean success = true;
        
        LocalDateTime beforeCreation = LocalDateTime.now();
        AuditEntry entry = new AuditEntry(userId, operation, resource, success);
        LocalDateTime afterCreation = LocalDateTime.now();
        
        assertNotNull(entry.getTimestamp());
        assertTrue(entry.getTimestamp().isAfter(beforeCreation.minusSeconds(1)));
        assertTrue(entry.getTimestamp().isBefore(afterCreation.plusSeconds(1)));
        assertEquals(userId, entry.getUserId());
        assertEquals(operation, entry.getOperation());
        assertEquals(resource, entry.getResource());
        assertEquals(success, entry.isSuccess());
        assertNull(entry.getDetails());
        assertNull(entry.getIpAddress());
        assertNull(entry.getSessionId());
        assertNull(entry.getUserAgent());
    }

    @Test
    void testCommonParametersConstructor() {
        String userId = "jane.smith";
        String operation = "FILE_DOWNLOAD";
        String resource = "/data/archive/file.zip";
        boolean success = false;
        String details = "File not found";
        String ipAddress = "192.168.1.100";
        
        LocalDateTime beforeCreation = LocalDateTime.now();
        AuditEntry entry = new AuditEntry(userId, operation, resource, success, details, ipAddress);
        LocalDateTime afterCreation = LocalDateTime.now();
        
        assertNotNull(entry.getTimestamp());
        assertTrue(entry.getTimestamp().isAfter(beforeCreation.minusSeconds(1)));
        assertTrue(entry.getTimestamp().isBefore(afterCreation.plusSeconds(1)));
        assertEquals(userId, entry.getUserId());
        assertEquals(operation, entry.getOperation());
        assertEquals(resource, entry.getResource());
        assertEquals(success, entry.isSuccess());
        assertEquals(details, entry.getDetails());
        assertEquals(ipAddress, entry.getIpAddress());
        assertNull(entry.getSessionId());
        assertNull(entry.getUserAgent());
    }

    @Test
    void testFullParametersConstructor() {
        LocalDateTime timestamp = LocalDateTime.now().minusMinutes(5);
        String userId = "test.user";
        String operation = "AUTHENTICATION";
        String resource = "login";
        boolean success = true;
        String details = "Successful login";
        String ipAddress = "10.0.0.1";
        String sessionId = "session-123";
        String userAgent = "Mozilla/5.0";
        
        AuditEntry entry = new AuditEntry(timestamp, userId, operation, resource, success, 
                                        details, ipAddress, sessionId, userAgent);
        
        assertEquals(timestamp, entry.getTimestamp());
        assertEquals(userId, entry.getUserId());
        assertEquals(operation, entry.getOperation());
        assertEquals(resource, entry.getResource());
        assertEquals(success, entry.isSuccess());
        assertEquals(details, entry.getDetails());
        assertEquals(ipAddress, entry.getIpAddress());
        assertEquals(sessionId, entry.getSessionId());
        assertEquals(userAgent, entry.getUserAgent());
    }

    @Test
    void testGettersAndSetters() {
        AuditEntry entry = new AuditEntry();
        LocalDateTime timestamp = LocalDateTime.now();
        String userId = "admin";
        String operation = "FILE_SEARCH";
        String resource = "/data/logs";
        boolean success = true;
        String details = "Search completed";
        String ipAddress = "172.16.0.1";
        String sessionId = "session-456";
        String userAgent = "Chrome/91.0";
        
        entry.setTimestamp(timestamp);
        entry.setUserId(userId);
        entry.setOperation(operation);
        entry.setResource(resource);
        entry.setSuccess(success);
        entry.setDetails(details);
        entry.setIpAddress(ipAddress);
        entry.setSessionId(sessionId);
        entry.setUserAgent(userAgent);
        
        assertEquals(timestamp, entry.getTimestamp());
        assertEquals(userId, entry.getUserId());
        assertEquals(operation, entry.getOperation());
        assertEquals(resource, entry.getResource());
        assertEquals(success, entry.isSuccess());
        assertEquals(details, entry.getDetails());
        assertEquals(ipAddress, entry.getIpAddress());
        assertEquals(sessionId, entry.getSessionId());
        assertEquals(userAgent, entry.getUserAgent());
    }

    @Test
    void testToString() {
        LocalDateTime timestamp = LocalDateTime.now();
        String userId = "john.doe";
        String operation = "FILE_UPLOAD";
        String resource = "/opt/uploads/test.txt";
        boolean success = true;
        String details = "Upload successful";
        String ipAddress = "192.168.1.50";
        String sessionId = "session-789";
        String userAgent = "Firefox/89.0";
        
        AuditEntry entry = new AuditEntry(timestamp, userId, operation, resource, success, 
                                        details, ipAddress, sessionId, userAgent);
        String toString = entry.toString();
        
        assertTrue(toString.contains("john.doe"));
        assertTrue(toString.contains("FILE_UPLOAD"));
        assertTrue(toString.contains("/opt/uploads/test.txt"));
        assertTrue(toString.contains("success=true"));
        assertTrue(toString.contains("Upload successful"));
        assertTrue(toString.contains("192.168.1.50"));
        assertTrue(toString.contains("session-789"));
        assertTrue(toString.contains("Firefox/89.0"));
    }

    @Test
    void testEquals() {
        LocalDateTime timestamp = LocalDateTime.now();
        String userId = "user";
        String operation = "OPERATION";
        String resource = "resource";
        boolean success = true;
        String details = "details";
        String ipAddress = "ip";
        String sessionId = "session";
        String userAgent = "agent";
        
        AuditEntry entry1 = new AuditEntry(timestamp, userId, operation, resource, success, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry2 = new AuditEntry(timestamp, userId, operation, resource, success, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry3 = new AuditEntry(timestamp.plusMinutes(1), userId, operation, resource, success, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry4 = new AuditEntry(timestamp, "different", operation, resource, success, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry5 = new AuditEntry(timestamp, userId, "DIFFERENT", resource, success, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry6 = new AuditEntry(timestamp, userId, operation, "different", success, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry7 = new AuditEntry(timestamp, userId, operation, resource, false, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry8 = new AuditEntry(timestamp, userId, operation, resource, success, 
                                         "different", ipAddress, sessionId, userAgent);
        AuditEntry entry9 = new AuditEntry(timestamp, userId, operation, resource, success, 
                                         details, "different", sessionId, userAgent);
        AuditEntry entry10 = new AuditEntry(timestamp, userId, operation, resource, success, 
                                          details, ipAddress, "different", userAgent);
        AuditEntry entry11 = new AuditEntry(timestamp, userId, operation, resource, success, 
                                          details, ipAddress, sessionId, "different");
        
        assertEquals(entry1, entry2);
        assertNotEquals(entry1, entry3);
        assertNotEquals(entry1, entry4);
        assertNotEquals(entry1, entry5);
        assertNotEquals(entry1, entry6);
        assertNotEquals(entry1, entry7);
        assertNotEquals(entry1, entry8);
        assertNotEquals(entry1, entry9);
        assertNotEquals(entry1, entry10);
        assertNotEquals(entry1, entry11);
        assertNotEquals(entry1, null);
        assertNotEquals(entry1, "not an AuditEntry");
    }

    @Test
    void testEqualsWithNullFields() {
        AuditEntry entry1 = new AuditEntry(null, null, null, null, false, null, null, null, null);
        AuditEntry entry2 = new AuditEntry(null, null, null, null, false, null, null, null, null);
        AuditEntry entry3 = new AuditEntry(LocalDateTime.now(), null, null, null, false, null, null, null, null);
        
        assertEquals(entry1, entry2);
        assertNotEquals(entry1, entry3);
    }

    @Test
    void testHashCode() {
        LocalDateTime timestamp = LocalDateTime.now();
        String userId = "user";
        String operation = "OPERATION";
        String resource = "resource";
        boolean success = true;
        String details = "details";
        String ipAddress = "ip";
        String sessionId = "session";
        String userAgent = "agent";
        
        AuditEntry entry1 = new AuditEntry(timestamp, userId, operation, resource, success, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry2 = new AuditEntry(timestamp, userId, operation, resource, success, 
                                         details, ipAddress, sessionId, userAgent);
        AuditEntry entry3 = new AuditEntry(timestamp, "different", operation, resource, success, 
                                         details, ipAddress, sessionId, userAgent);
        
        assertEquals(entry1.hashCode(), entry2.hashCode());
        assertNotEquals(entry1.hashCode(), entry3.hashCode());
    }

    @Test
    void testHashCodeWithNullFields() {
        AuditEntry entry1 = new AuditEntry(null, null, null, null, false, null, null, null, null);
        AuditEntry entry2 = new AuditEntry(null, null, null, null, false, null, null, null, null);
        
        assertEquals(entry1.hashCode(), entry2.hashCode());
    }

    @Test
    void testEqualsSameInstance() {
        AuditEntry entry = new AuditEntry("user", "operation", "resource", true);
        
        assertEquals(entry, entry);
    }

    @Test
    void testSuccessfulOperation() {
        AuditEntry entry = new AuditEntry("user", "FILE_UPLOAD", "/path/file.txt", true);
        
        assertTrue(entry.isSuccess());
    }

    @Test
    void testFailedOperation() {
        AuditEntry entry = new AuditEntry("user", "FILE_UPLOAD", "/path/file.txt", false);
        
        assertFalse(entry.isSuccess());
    }

    @Test
    void testTimestampIsSetOnCreation() {
        LocalDateTime before = LocalDateTime.now();
        AuditEntry entry = new AuditEntry("user", "operation", "resource", true);
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(entry.getTimestamp());
        assertTrue(entry.getTimestamp().isAfter(before.minusSeconds(1)));
        assertTrue(entry.getTimestamp().isBefore(after.plusSeconds(1)));
    }
}