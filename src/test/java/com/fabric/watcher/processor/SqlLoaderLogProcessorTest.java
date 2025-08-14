package com.fabric.watcher.processor;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.LogAuditInfo;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.service.DatabaseExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqlLoaderLogProcessor.
 */
@ExtendWith(MockitoExtension.class)
class SqlLoaderLogProcessorTest {

    @Mock
    private DatabaseExecutor databaseExecutor;

    @InjectMocks
    private SqlLoaderLogProcessor processor;

    @TempDir
    Path tempDir;

    private WatchConfig watchConfig;

    @BeforeEach
    void setUp() {
        watchConfig = new WatchConfig();
        watchConfig.setProcessorType("sqlloader-log");
        watchConfig.setWatchFolder(tempDir);
    }

    @Test
    void testSupports_WithCorrectProcessorType() {
        assertTrue(processor.supports(watchConfig));
    }

    @Test
    void testSupports_WithIncorrectProcessorType() {
        watchConfig.setProcessorType("sql-script");
        assertFalse(processor.supports(watchConfig));
    }

    @Test
    void testGetProcessorType() {
        assertEquals("sqlloader-log", processor.getProcessorType());
    }

    @Test
    void testProcessFile_Success() throws IOException {
        // Create a sample log file
        File logFile = createSampleLogFile("sample_success.log", createSuccessfulLogContent());

        // Process the file
        ProcessingResult result = processor.processFile(logFile, watchConfig);

        // Verify results
        assertNotNull(result);
        assertEquals("sample_success.log", result.getFilename());
        assertEquals("sqlloader-log", result.getProcessorType());
        assertEquals(ProcessingResult.ExecutionStatus.SUCCESS, result.getStatus());
        assertTrue(result.getExecutionDurationMs() >= 0);

        // Verify metadata
        assertNotNull(result.getMetadata());
        assertEquals(1000L, result.getMetadata().get("recordsLoaded"));
        assertEquals(5L, result.getMetadata().get("recordsRejected"));
        assertEquals("EMPLOYEES", result.getMetadata().get("tableName"));
        assertEquals("COMPLETED_WITH_ERRORS", result.getMetadata().get("loadStatus"));
    }

    @Test
    void testProcessFile_WithIOException() throws IOException {
        // Create a file that doesn't exist
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.log");

        // Process the file
        ProcessingResult result = processor.processFile(nonExistentFile, watchConfig);

        // Verify failure result
        assertNotNull(result);
        assertEquals("nonexistent.log", result.getFilename());
        assertEquals("sqlloader-log", result.getProcessorType());
        assertEquals(ProcessingResult.ExecutionStatus.FAILURE, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getExecutionDurationMs() >= 0);
    }

    @Test
    void testParseLogFile_SuccessfulLoad() throws IOException {
        File logFile = createSampleLogFile("success.log", createSuccessfulLogContent());

        LogAuditInfo auditInfo = processor.parseLogFile(logFile);

        assertNotNull(auditInfo);
        assertEquals("success.log", auditInfo.getLogFilename());
        assertEquals("employees.ctl", auditInfo.getControlFilename());
        assertEquals("employees.dat", auditInfo.getDataFilename());
        assertEquals("EMPLOYEES", auditInfo.getTableName());
        assertEquals(1000, auditInfo.getRecordsLoaded());
        assertEquals(5, auditInfo.getRecordsRejected());
        assertEquals(1005, auditInfo.getTotalRecords());
        assertEquals("COMPLETED_WITH_ERRORS", auditInfo.getLoadStatus());
        assertNotNull(auditInfo.getLoadStartTime());
        assertNotNull(auditInfo.getLoadEndTime());
    }

    @Test
    void testParseLogFile_WithErrors() throws IOException {
        File logFile = createSampleLogFile("error.log", createErrorLogContent());

        LogAuditInfo auditInfo = processor.parseLogFile(logFile);

        assertNotNull(auditInfo);
        assertEquals("error.log", auditInfo.getLogFilename());
        assertEquals("ERROR", auditInfo.getLoadStatus());
        assertNotNull(auditInfo.getErrorDetails());
        assertTrue(auditInfo.getErrorDetails().contains("Invalid number format"));
    }

    @Test
    void testParseLogFile_PerfectLoad() throws IOException {
        File logFile = createSampleLogFile("perfect.log", createPerfectLogContent());

        LogAuditInfo auditInfo = processor.parseLogFile(logFile);

        assertNotNull(auditInfo);
        assertEquals("perfect.log", auditInfo.getLogFilename());
        assertEquals(500, auditInfo.getRecordsLoaded());
        assertEquals(0, auditInfo.getRecordsRejected());
        assertEquals("SUCCESS", auditInfo.getLoadStatus());
        assertNull(auditInfo.getErrorDetails());
    }

    @Test
    void testParseLogFile_EmptyFile() throws IOException {
        File logFile = createSampleLogFile("empty.log", "");

        LogAuditInfo auditInfo = processor.parseLogFile(logFile);

        assertNotNull(auditInfo);
        assertEquals("empty.log", auditInfo.getLogFilename());
        assertEquals(0, auditInfo.getRecordsLoaded());
        assertEquals(0, auditInfo.getRecordsRejected());
        assertEquals("SUCCESS", auditInfo.getLoadStatus()); // Default when no errors found
    }

    private File createSampleLogFile(String filename, String content) throws IOException {
        File file = new File(tempDir.toFile(), filename);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    private String createSuccessfulLogContent() {
        return """
                SQL*Loader: Release 19.0.0.0.0 - Production on Mon Jan 15 10:30:00 2024
                
                Copyright (c) 1982, 2019, Oracle and/or its affiliates.  All rights reserved.
                
                Control File:   employees.ctl
                Data File:      employees.dat
                  Bad File:     employees.bad
                  Discard File: none specified
                 
                 (Allow all discards)
                
                Number to load: ALL
                Number to skip: 0
                Errors allowed: 50
                Bind array:     64 rows, maximum of 256000 bytes
                Continuation:    none specified
                Path used:      Conventional
                
                Table EMPLOYEES, loaded from every logical record.
                Insert option in effect for this table: INSERT
                
                   Column Name                  Position   Len  Term Encl Datatype
                ------------------------------ ---------- ----- ---- ---- -----------
                EMPLOYEE_ID                         FIRST     *   ,       CHARACTER            
                FIRST_NAME                           NEXT     *   ,       CHARACTER            
                LAST_NAME                            NEXT     *   ,       CHARACTER            
                EMAIL                                NEXT     *   ,       CHARACTER            
                HIRE_DATE                            NEXT     *   ,       CHARACTER            
                
                Run began on Mon Jan 15 10:30:15 2024
                Run ended on Mon Jan 15 10:32:45 2024
                
                1000 Rows successfully loaded.
                5 Rows not loaded due to data errors.
                0 Rows not loaded because all WHEN clauses were failed.
                0 Rows not loaded because all fields were null.
                
                
                Space allocated for bind array:                 132096 bytes(64 rows)
                Read   buffer bytes: 1048576
                
                Total logical records skipped:          0
                Total logical records read:          1005
                Total logical records rejected:         5
                Total logical records discarded:        0
                
                Elapsed time was:     00:02:30.45
                CPU time was:         00:00:15.23
                """;
    }

    private String createErrorLogContent() {
        return """
                SQL*Loader: Release 19.0.0.0.0 - Production on Mon Jan 15 11:00:00 2024
                
                Control File:   problem.ctl
                Data File:      problem.dat
                
                SQL*Loader-500: Unable to open file (problem.dat)
                SQL*Loader-553: file not found
                SQL*Loader-509: System error: Invalid number format in record 15
                
                Run began on Mon Jan 15 11:00:15 2024
                Run ended on Mon Jan 15 11:00:16 2024
                
                0 Rows successfully loaded.
                0 Rows not loaded due to data errors.
                """;
    }

    private String createPerfectLogContent() {
        return """
                SQL*Loader: Release 19.0.0.0.0 - Production on Mon Jan 15 12:00:00 2024
                
                Control File:   perfect.ctl
                Data File:      perfect.dat
                
                Table CUSTOMERS, loaded from every logical record.
                
                Run began on Mon Jan 15 12:00:15 2024
                Run ended on Mon Jan 15 12:01:00 2024
                
                500 Rows successfully loaded.
                0 Rows not loaded due to data errors.
                0 Rows not loaded because all WHEN clauses were failed.
                0 Rows not loaded because all fields were null.
                
                Total logical records read:            500
                Total logical records rejected:           0
                """;
    }
}