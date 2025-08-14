package com.fabric.watcher.model;

import com.fabric.watcher.model.ProcessingResult.ExecutionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessingResultTest {

    @Test
    void testProcessingResultCreation() {
        ProcessingResult result = new ProcessingResult("test.sql", "sql-script", ExecutionStatus.SUCCESS);
        
        assertEquals("test.sql", result.getFilename());
        assertEquals("sql-script", result.getProcessorType());
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertNotNull(result.getExecutionTime());
    }

    @Test
    void testProcessingResultWithError() {
        ProcessingResult result = new ProcessingResult("test.sql", "sql-script", 
                                                     ExecutionStatus.FAILURE, "SQL syntax error");
        
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertEquals("SQL syntax error", result.getErrorMessage());
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
    }
}