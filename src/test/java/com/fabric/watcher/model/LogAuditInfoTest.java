package com.fabric.watcher.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LogAuditInfo model.
 */
class LogAuditInfoTest {

    @Test
    void testDefaultConstructor() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        
        assertNotNull(auditInfo.getAuditTimestamp());
        assertNull(auditInfo.getLogFilename());
        assertEquals(0, auditInfo.getRecordsLoaded());
        assertEquals(0, auditInfo.getRecordsRejected());
        assertEquals(0, auditInfo.getTotalRecords());
    }

    @Test
    void testConstructorWithFilename() {
        String filename = "test_load.log";
        LogAuditInfo auditInfo = new LogAuditInfo(filename);
        
        assertEquals(filename, auditInfo.getLogFilename());
        assertNotNull(auditInfo.getAuditTimestamp());
    }

    @Test
    void testIsLoadSuccessful_WithSuccessStatus() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        auditInfo.setLoadStatus("SUCCESS");
        
        assertTrue(auditInfo.isLoadSuccessful());
    }

    @Test
    void testIsLoadSuccessful_WithCompletedStatusAndNoRejections() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        auditInfo.setLoadStatus("COMPLETED");
        auditInfo.setRecordsRejected(0);
        
        assertTrue(auditInfo.isLoadSuccessful());
    }

    @Test
    void testIsLoadSuccessful_WithCompletedStatusAndRejections() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        auditInfo.setLoadStatus("COMPLETED");
        auditInfo.setRecordsRejected(5);
        
        assertFalse(auditInfo.isLoadSuccessful());
    }

    @Test
    void testIsLoadSuccessful_WithErrorStatus() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        auditInfo.setLoadStatus("ERROR");
        
        assertFalse(auditInfo.isLoadSuccessful());
    }

    @Test
    void testGetLoadDurationMs_WithValidTimes() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        LocalDateTime startTime = LocalDateTime.of(2023, 1, 1, 10, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2023, 1, 1, 10, 5, 30);
        
        auditInfo.setLoadStartTime(startTime);
        auditInfo.setLoadEndTime(endTime);
        
        long expectedDuration = 5 * 60 * 1000 + 30 * 1000; // 5 minutes 30 seconds in ms
        assertEquals(expectedDuration, auditInfo.getLoadDurationMs());
    }

    @Test
    void testGetLoadDurationMs_WithNullStartTime() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        auditInfo.setLoadEndTime(LocalDateTime.now());
        
        assertEquals(-1, auditInfo.getLoadDurationMs());
    }

    @Test
    void testGetLoadDurationMs_WithNullEndTime() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        auditInfo.setLoadStartTime(LocalDateTime.now());
        
        assertEquals(-1, auditInfo.getLoadDurationMs());
    }

    @Test
    void testGetLoadDurationMs_WithNullTimes() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        
        assertEquals(-1, auditInfo.getLoadDurationMs());
    }

    @Test
    void testSettersAndGetters() {
        LogAuditInfo auditInfo = new LogAuditInfo();
        
        // Test all setters and getters
        auditInfo.setLogFilename("test.log");
        assertEquals("test.log", auditInfo.getLogFilename());
        
        auditInfo.setControlFilename("test.ctl");
        assertEquals("test.ctl", auditInfo.getControlFilename());
        
        auditInfo.setDataFilename("test.dat");
        assertEquals("test.dat", auditInfo.getDataFilename());
        
        auditInfo.setTableName("TEST_TABLE");
        assertEquals("TEST_TABLE", auditInfo.getTableName());
        
        auditInfo.setRecordsLoaded(1000);
        assertEquals(1000, auditInfo.getRecordsLoaded());
        
        auditInfo.setRecordsRejected(5);
        assertEquals(5, auditInfo.getRecordsRejected());
        
        auditInfo.setTotalRecords(1005);
        assertEquals(1005, auditInfo.getTotalRecords());
        
        auditInfo.setLoadStatus("SUCCESS");
        assertEquals("SUCCESS", auditInfo.getLoadStatus());
        
        auditInfo.setErrorDetails("Some error");
        assertEquals("Some error", auditInfo.getErrorDetails());
        
        LocalDateTime testTime = LocalDateTime.now();
        auditInfo.setLoadStartTime(testTime);
        assertEquals(testTime, auditInfo.getLoadStartTime());
        
        auditInfo.setLoadEndTime(testTime);
        assertEquals(testTime, auditInfo.getLoadEndTime());
        
        auditInfo.setAuditTimestamp(testTime);
        assertEquals(testTime, auditInfo.getAuditTimestamp());
    }

    @Test
    void testToString() {
        LogAuditInfo auditInfo = new LogAuditInfo("test.log");
        auditInfo.setControlFilename("test.ctl");
        auditInfo.setDataFilename("test.dat");
        auditInfo.setTableName("TEST_TABLE");
        auditInfo.setLoadStatus("SUCCESS");
        auditInfo.setRecordsLoaded(1000);
        auditInfo.setRecordsRejected(0);
        
        String result = auditInfo.toString();
        
        assertTrue(result.contains("test.log"));
        assertTrue(result.contains("test.ctl"));
        assertTrue(result.contains("test.dat"));
        assertTrue(result.contains("TEST_TABLE"));
        assertTrue(result.contains("SUCCESS"));
        assertTrue(result.contains("1000"));
        assertTrue(result.contains("0"));
    }
}