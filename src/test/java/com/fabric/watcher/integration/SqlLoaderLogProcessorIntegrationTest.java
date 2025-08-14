package com.fabric.watcher.integration;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.processor.SqlLoaderLogProcessor;
import com.fabric.watcher.service.DatabaseExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SqlLoaderLogProcessor with real database operations.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SqlLoaderLogProcessorIntegrationTest {

    @Autowired
    private SqlLoaderLogProcessor processor;

    @Autowired
    private DatabaseExecutor databaseExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private WatchConfig watchConfig;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create temporary directory for test files
        tempDir = Files.createTempDirectory("sqlloader-test");
        
        // Setup watch configuration
        watchConfig = new WatchConfig();
        watchConfig.setProcessorType("sqlloader-log");
        watchConfig.setWatchFolder(tempDir);
        
        // Create audit table if it doesn't exist
        createAuditTableIfNotExists();
    }

    @Test
    void testProcessSuccessfulLogFile() throws IOException {
        // Copy sample log file to temp directory
        File logFile = copyResourceToTemp("sample-logs/successful-load.log", "successful-load.log");

        // Process the file
        ProcessingResult result = processor.processFile(logFile, watchConfig);

        // Verify processing result
        assertNotNull(result);
        assertEquals("successful-load.log", result.getFilename());
        assertEquals("sqlloader-log", result.getProcessorType());
        assertEquals(ProcessingResult.ExecutionStatus.SUCCESS, result.getStatus());
        assertNull(result.getErrorMessage());

        // Verify metadata
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals(1000L, metadata.get("recordsLoaded"));
        assertEquals(5L, metadata.get("recordsRejected"));
        assertEquals("EMPLOYEES", metadata.get("tableName"));
        assertEquals("COMPLETED_WITH_ERRORS", metadata.get("loadStatus"));

        // Verify database record was created
        List<Map<String, Object>> auditRecords = jdbcTemplate.queryForList(
            "SELECT * FROM sqlloader_audit WHERE log_filename = ?", "successful-load.log");
        
        assertEquals(1, auditRecords.size());
        Map<String, Object> auditRecord = auditRecords.get(0);
        assertEquals("successful-load.log", auditRecord.get("log_filename"));
        assertEquals("employees.ctl", auditRecord.get("control_filename"));
        assertEquals("employees.dat", auditRecord.get("data_filename"));
        assertEquals("EMPLOYEES", auditRecord.get("table_name"));
        assertEquals(1000L, auditRecord.get("records_loaded"));
        assertEquals(5L, auditRecord.get("records_rejected"));
        assertEquals(1005L, auditRecord.get("total_records"));
        assertEquals("COMPLETED_WITH_ERRORS", auditRecord.get("load_status"));
        assertNotNull(auditRecord.get("load_start_time"));
        assertNotNull(auditRecord.get("load_end_time"));
    }

    @Test
    void testProcessErrorLogFile() throws IOException {
        // Copy sample log file to temp directory
        File logFile = copyResourceToTemp("sample-logs/error-load.log", "error-load.log");

        // Process the file
        ProcessingResult result = processor.processFile(logFile, watchConfig);

        // Verify processing result
        assertNotNull(result);
        assertEquals("error-load.log", result.getFilename());
        assertEquals("sqlloader-log", result.getProcessorType());
        assertEquals(ProcessingResult.ExecutionStatus.SUCCESS, result.getStatus());

        // Verify metadata
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals(0L, metadata.get("recordsLoaded"));
        assertEquals(0L, metadata.get("recordsRejected"));
        assertEquals("ERROR", metadata.get("loadStatus"));

        // Verify database record was created
        List<Map<String, Object>> auditRecords = jdbcTemplate.queryForList(
            "SELECT * FROM sqlloader_audit WHERE log_filename = ?", "error-load.log");
        
        assertEquals(1, auditRecords.size());
        Map<String, Object> auditRecord = auditRecords.get(0);
        assertEquals("error-load.log", auditRecord.get("log_filename"));
        assertEquals("problem.ctl", auditRecord.get("control_filename"));
        assertEquals("problem.dat", auditRecord.get("data_filename"));
        assertEquals(0L, auditRecord.get("records_loaded"));
        assertEquals(0L, auditRecord.get("records_rejected"));
        assertEquals("ERROR", auditRecord.get("load_status"));
        assertNotNull(auditRecord.get("error_details"));
        assertTrue(((String) auditRecord.get("error_details")).contains("Unable to open file"));
    }

    @Test
    void testProcessPerfectLogFile() throws IOException {
        // Copy sample log file to temp directory
        File logFile = copyResourceToTemp("sample-logs/perfect-load.log", "perfect-load.log");

        // Process the file
        ProcessingResult result = processor.processFile(logFile, watchConfig);

        // Verify processing result
        assertNotNull(result);
        assertEquals("perfect-load.log", result.getFilename());
        assertEquals("sqlloader-log", result.getProcessorType());
        assertEquals(ProcessingResult.ExecutionStatus.SUCCESS, result.getStatus());

        // Verify metadata
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals(500L, metadata.get("recordsLoaded"));
        assertEquals(0L, metadata.get("recordsRejected"));
        assertEquals("CUSTOMERS", metadata.get("tableName"));
        assertEquals("SUCCESS", metadata.get("loadStatus"));

        // Verify database record was created
        List<Map<String, Object>> auditRecords = jdbcTemplate.queryForList(
            "SELECT * FROM sqlloader_audit WHERE log_filename = ?", "perfect-load.log");
        
        assertEquals(1, auditRecords.size());
        Map<String, Object> auditRecord = auditRecords.get(0);
        assertEquals("perfect-load.log", auditRecord.get("log_filename"));
        assertEquals("perfect.ctl", auditRecord.get("control_filename"));
        assertEquals("perfect.dat", auditRecord.get("data_filename"));
        assertEquals("CUSTOMERS", auditRecord.get("table_name"));
        assertEquals(500L, auditRecord.get("records_loaded"));
        assertEquals(0L, auditRecord.get("records_rejected"));
        assertEquals(500L, auditRecord.get("total_records"));
        assertEquals("SUCCESS", auditRecord.get("load_status"));
        assertNull(auditRecord.get("error_details"));
    }

    @Test
    void testProcessorSupportsConfiguration() {
        assertTrue(processor.supports(watchConfig));
        
        // Test with different processor type
        watchConfig.setProcessorType("sql-script");
        assertFalse(processor.supports(watchConfig));
    }

    @Test
    void testGetProcessorType() {
        assertEquals("sqlloader-log", processor.getProcessorType());
    }

    @Test
    void testMultipleLogProcessing() throws IOException {
        // Process multiple log files
        File logFile1 = copyResourceToTemp("sample-logs/successful-load.log", "load1.log");
        File logFile2 = copyResourceToTemp("sample-logs/perfect-load.log", "load2.log");

        // Process both files
        ProcessingResult result1 = processor.processFile(logFile1, watchConfig);
        ProcessingResult result2 = processor.processFile(logFile2, watchConfig);

        // Verify both were processed successfully
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());

        // Verify both records exist in database
        List<Map<String, Object>> auditRecords = jdbcTemplate.queryForList(
            "SELECT * FROM sqlloader_audit ORDER BY log_filename");
        
        assertEquals(2, auditRecords.size());
        assertEquals("load1.log", auditRecords.get(0).get("log_filename"));
        assertEquals("load2.log", auditRecords.get(1).get("log_filename"));
    }

    private File copyResourceToTemp(String resourcePath, String fileName) throws IOException {
        Path sourcePath = Path.of("src/test/resources/" + resourcePath);
        Path targetPath = tempDir.resolve(fileName);
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath.toFile();
    }

    private void createAuditTableIfNotExists() {
        try {
            // Check if table exists
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sqlloader_audit", Integer.class);
        } catch (Exception e) {
            // Table doesn't exist, create it
            String createTableSql = """
                CREATE TABLE sqlloader_audit (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    log_filename VARCHAR(255) NOT NULL,
                    control_filename VARCHAR(255),
                    data_filename VARCHAR(255),
                    table_name VARCHAR(128),
                    load_start_time TIMESTAMP,
                    load_end_time TIMESTAMP,
                    records_loaded BIGINT DEFAULT 0,
                    records_rejected BIGINT DEFAULT 0,
                    total_records BIGINT DEFAULT 0,
                    load_status VARCHAR(50) NOT NULL,
                    error_details CLOB,
                    audit_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            
            jdbcTemplate.execute(createTableSql);
            
            // Create indexes
            jdbcTemplate.execute("CREATE INDEX idx_sqlloader_audit_log_filename ON sqlloader_audit(log_filename)");
            jdbcTemplate.execute("CREATE INDEX idx_sqlloader_audit_table_name ON sqlloader_audit(table_name)");
            jdbcTemplate.execute("CREATE INDEX idx_sqlloader_audit_load_status ON sqlloader_audit(load_status)");
        }
    }
}