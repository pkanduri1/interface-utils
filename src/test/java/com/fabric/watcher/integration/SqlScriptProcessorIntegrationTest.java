package com.fabric.watcher.integration;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingResult.ExecutionStatus;
import com.fabric.watcher.processor.FileProcessor;
import com.fabric.watcher.processor.SqlScriptProcessor;
import com.fabric.watcher.service.FileProcessorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:integrationtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none"
})
class SqlScriptProcessorIntegrationTest {
    
    @Autowired
    private FileProcessorRegistry fileProcessorRegistry;
    
    @Autowired
    private SqlScriptProcessor sqlScriptProcessor;
    
    private WatchConfig sqlConfig;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        sqlConfig = new WatchConfig();
        sqlConfig.setProcessorType("sql-script");
        sqlConfig.setName("SQL Script Processor Integration Test");
    }
    
    @Test
    void testSqlScriptProcessorIsRegistered() {
        // Verify that the SqlScriptProcessor is registered in the registry
        FileProcessor processor = fileProcessorRegistry.getProcessor("sql-script");
        assertNotNull(processor);
        assertEquals(sqlScriptProcessor, processor);
    }
    
    @Test
    void testEndToEndSqlScriptProcessing() throws IOException {
        // Create a test SQL file with DDL and DML statements
        String sqlContent = """
            CREATE TABLE integration_test (
                id INT PRIMARY KEY,
                name VARCHAR(100),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            INSERT INTO integration_test (id, name) VALUES (1, 'Test Record 1');
            INSERT INTO integration_test (id, name) VALUES (2, 'Test Record 2');
            
            UPDATE integration_test SET name = 'Updated Record' WHERE id = 1;
            """;
        
        File sqlFile = createTempFile("integration_test.sql", sqlContent);
        
        // Process the file through the registry
        ProcessingResult result = fileProcessorRegistry.processFile(sqlFile, sqlConfig);
        
        // Verify the processing was successful
        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("integration_test.sql", result.getFilename());
        assertEquals("sql-script", result.getProcessorType());
        assertNotNull(result.getExecutionTime());
        assertTrue(result.getExecutionDurationMs() >= 0);
        
        // Verify metadata
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals(4, metadata.get("totalStatements"));
        assertEquals(1, metadata.get("ddlCount"));
        assertEquals(3, metadata.get("dmlCount"));
        assertEquals(0, metadata.get("otherCount"));
        
        // Verify execution details
        assertTrue(metadata.containsKey("executionTimeMs"));
        assertTrue(metadata.containsKey("successfulStatements"));
        assertTrue((Long) metadata.get("executionTimeMs") >= 0);
        assertTrue((Integer) metadata.get("successfulStatements") > 0);
    }
    
    @Test
    void testSqlScriptProcessingWithError() throws IOException {
        // Create a SQL file with an invalid statement
        String sqlContent = """
            CREATE TABLE error_test (id INT PRIMARY KEY);
            INSERT INTO error_test VALUES (1);
            INSERT INTO error_test VALUES (1); -- This will cause a duplicate key error
            """;
        
        File sqlFile = createTempFile("error_test.sql", sqlContent);
        
        // Process the file through the registry
        ProcessingResult result = fileProcessorRegistry.processFile(sqlFile, sqlConfig);
        
        // Verify the processing failed appropriately
        assertNotNull(result);
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertEquals("error_test.sql", result.getFilename());
        assertEquals("sql-script", result.getProcessorType());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("SQL execution failed"));
        
        // Verify metadata still contains parsing information
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals(3, metadata.get("totalStatements"));
        assertEquals(1, metadata.get("ddlCount"));
        assertEquals(2, metadata.get("dmlCount"));
        assertEquals(0, metadata.get("otherCount"));
    }
    
    @Test
    void testProcessorSupportsCorrectConfiguration() {
        assertTrue(sqlScriptProcessor.supports(sqlConfig));
        
        WatchConfig otherConfig = new WatchConfig();
        otherConfig.setProcessorType("other-processor");
        assertFalse(sqlScriptProcessor.supports(otherConfig));
    }
    
    @Test
    void testProcessorType() {
        assertEquals("sql-script", sqlScriptProcessor.getProcessorType());
    }
    
    private File createTempFile(String filename, String content) throws IOException {
        Path filePath = tempDir.resolve(filename);
        Files.write(filePath, content.getBytes());
        return filePath.toFile();
    }
}