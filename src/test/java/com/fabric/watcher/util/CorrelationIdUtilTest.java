package com.fabric.watcher.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CorrelationIdUtil.
 */
class CorrelationIdUtilTest {
    
    @AfterEach
    void cleanup() {
        CorrelationIdUtil.clear();
    }
    
    @Test
    void testGenerateAndSet() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        assertNotNull(correlationId);
        assertFalse(correlationId.isEmpty());
        assertEquals(correlationId, CorrelationIdUtil.get());
        assertEquals(correlationId, MDC.get("correlationId"));
    }
    
    @Test
    void testSetAndGet() {
        String testId = "test-correlation-id";
        CorrelationIdUtil.set(testId);
        
        assertEquals(testId, CorrelationIdUtil.get());
        assertEquals(testId, MDC.get("correlationId"));
    }
    
    @Test
    void testSetFileName() {
        String fileName = "test-file.sql";
        CorrelationIdUtil.setFileName(fileName);
        
        assertEquals(fileName, MDC.get("fileName"));
    }
    
    @Test
    void testSetProcessorType() {
        String processorType = "sql-script";
        CorrelationIdUtil.setProcessorType(processorType);
        
        assertEquals(processorType, MDC.get("processorType"));
    }
    
    @Test
    void testClear() {
        CorrelationIdUtil.generateAndSet();
        CorrelationIdUtil.setFileName("test.sql");
        CorrelationIdUtil.setProcessorType("sql-script");
        
        // Verify values are set
        assertNotNull(CorrelationIdUtil.get());
        assertNotNull(MDC.get("fileName"));
        assertNotNull(MDC.get("processorType"));
        
        CorrelationIdUtil.clear();
        
        // Verify all values are cleared
        assertNull(CorrelationIdUtil.get());
        assertNull(MDC.get("fileName"));
        assertNull(MDC.get("processorType"));
    }
    
    @Test
    void testClearCorrelationId() {
        CorrelationIdUtil.generateAndSet();
        CorrelationIdUtil.setFileName("test.sql");
        
        assertNotNull(CorrelationIdUtil.get());
        assertNotNull(MDC.get("fileName"));
        
        CorrelationIdUtil.clearCorrelationId();
        
        assertNull(CorrelationIdUtil.get());
        assertNotNull(MDC.get("fileName")); // Should still be set
    }
    
    @Test
    void testGetWhenNotSet() {
        assertNull(CorrelationIdUtil.get());
    }
}