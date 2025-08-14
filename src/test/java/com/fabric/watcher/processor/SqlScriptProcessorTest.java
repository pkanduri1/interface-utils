package com.fabric.watcher.processor;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingResult.ExecutionStatus;
import com.fabric.watcher.service.DatabaseExecutor;
import com.fabric.watcher.service.DatabaseExecutor.SqlExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class SqlScriptProcessorTest {
    
    @Mock
    private DatabaseExecutor databaseExecutor;
    
    @InjectMocks
    private SqlScriptProcessor processor;
    
    private WatchConfig sqlConfig;
    private WatchConfig nonSqlConfig;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        sqlConfig = new WatchConfig();
        sqlConfig.setProcessorType("sql-script");
        sqlConfig.setName("SQL Script Processor");
        
        nonSqlConfig = new WatchConfig();
        nonSqlConfig.setProcessorType("other-processor");
        nonSqlConfig.setName("Other Processor");
        
        // Default mock behavior for successful execution
        SqlExecutionResult defaultSuccessResult = new SqlExecutionResult("default.sql");
        defaultSuccessResult.setSuccess(true);
        defaultSuccessResult.setExecutionTimeMs(50);
        defaultSuccessResult.incrementSuccessfulStatements();
        
        when(databaseExecutor.executeScript(any(File.class), anyList())).thenReturn(defaultSuccessResult);
    }
    
    @Test
    void testSupports() {
        assertTrue(processor.supports(sqlConfig));
        assertFalse(processor.supports(nonSqlConfig));
    }
    
    @Test
    void testGetProcessorType() {
        assertEquals("sql-script", processor.getProcessorType());
    }
    
    @Test
    void testProcessValidSqlFile() throws IOException {
        // Create a test SQL file
        String sqlContent = """
            -- This is a comment
            CREATE TABLE test_table (
                id INT PRIMARY KEY,
                name VARCHAR(100)
            );
            
            INSERT INTO test_table (id, name) VALUES (1, 'Test');
            UPDATE test_table SET name = 'Updated' WHERE id = 1;
            """;
        
        File sqlFile = createTempFile("test.sql", sqlContent);
        
        // Mock successful database execution
        SqlExecutionResult mockResult = new SqlExecutionResult("test.sql");
        mockResult.setSuccess(true);
        mockResult.setExecutionTimeMs(100);
        mockResult.incrementSuccessfulStatements();
        mockResult.incrementSuccessfulStatements();
        mockResult.incrementSuccessfulStatements();
        
        when(databaseExecutor.executeScript(any(File.class), anyList())).thenReturn(mockResult);
        
        ProcessingResult result = processor.processFile(sqlFile, sqlConfig);
        
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test.sql", result.getFilename());
        assertEquals("sql-script", result.getProcessorType());
        assertNotNull(result.getExecutionTime());
        assertTrue(result.getExecutionDurationMs() >= 0);
        
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals(3, metadata.get("totalStatements"));
        assertEquals(1, metadata.get("ddlCount"));
        assertEquals(2, metadata.get("dmlCount"));
        assertEquals(0, metadata.get("otherCount"));
        assertEquals(100L, metadata.get("executionTimeMs"));
        assertEquals(3, metadata.get("successfulStatements"));
    }
    
    @Test
    void testProcessEmptyFile() throws IOException {
        File emptyFile = createTempFile("empty.sql", "");
        
        ProcessingResult result = processor.processFile(emptyFile, sqlConfig);
        
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Invalid SQL file"));
    }
    
    @Test
    void testProcessFileWithOnlyComments() throws IOException {
        String sqlContent = """
            -- This is just a comment
            /* This is a block comment */
            -- Another comment
            """;
        
        File sqlFile = createTempFile("comments.sql", sqlContent);
        
        ProcessingResult result = processor.processFile(sqlFile, sqlConfig);
        
        assertEquals(ExecutionStatus.SKIPPED, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("No valid SQL statements"));
    }
    
    @Test
    void testProcessValidSqlFileWithExecutionFailure() throws IOException {
        // Create a test SQL file
        String sqlContent = """
            CREATE TABLE test_table (id INT PRIMARY KEY);
            INSERT INTO test_table VALUES (1);
            """;
        
        File sqlFile = createTempFile("test_fail.sql", sqlContent);
        
        // Mock failed database execution
        SqlExecutionResult mockResult = new SqlExecutionResult("test_fail.sql");
        mockResult.setSuccess(false);
        mockResult.setExecutionTimeMs(50);
        mockResult.setErrorMessage("Database connection failed");
        mockResult.incrementSuccessfulStatements(); // First statement succeeded
        
        when(databaseExecutor.executeScript(any(File.class), anyList())).thenReturn(mockResult);
        
        ProcessingResult result = processor.processFile(sqlFile, sqlConfig);
        
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertEquals("test_fail.sql", result.getFilename());
        assertEquals("sql-script", result.getProcessorType());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("SQL execution failed"));
        
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertEquals(2, metadata.get("totalStatements"));
        assertEquals(50L, metadata.get("executionTimeMs"));
        assertEquals(1, metadata.get("successfulStatements"));
    }
    
    @Test
    void testProcessNonExistentFile() {
        File nonExistentFile = new File(tempDir.toFile(), "nonexistent.sql");
        
        ProcessingResult result = processor.processFile(nonExistentFile, sqlConfig);
        
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertNotNull(result.getErrorMessage());
    }
    
    @Test
    void testProcessNonSqlFile() throws IOException {
        File txtFile = createTempFile("test.txt", "This is not SQL");
        
        ProcessingResult result = processor.processFile(txtFile, sqlConfig);
        
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Invalid SQL file"));
    }
    
    @Test
    void testParseStatements() {
        String sqlContent = """
            CREATE TABLE users (id INT, name VARCHAR(50));
            INSERT INTO users VALUES (1, 'John');
            UPDATE users SET name = 'Jane' WHERE id = 1;
            """;
        
        List<String> statements = processor.parseStatements(sqlContent);
        
        assertEquals(3, statements.size());
        assertTrue(statements.get(0).trim().startsWith("CREATE TABLE"));
        assertTrue(statements.get(1).trim().startsWith("INSERT INTO"));
        assertTrue(statements.get(2).trim().startsWith("UPDATE users"));
    }
    
    @Test
    void testParseStatementsWithComments() {
        String sqlContent = """
            -- Create users table
            CREATE TABLE users (id INT, name VARCHAR(50));
            /* Insert test data */
            INSERT INTO users VALUES (1, 'John');
            """;
        
        List<String> statements = processor.parseStatements(sqlContent);
        
        assertEquals(2, statements.size());
        assertFalse(statements.get(0).contains("--"));
        assertFalse(statements.get(1).contains("/*"));
    }
    
    @Test
    void testParseStatementsWithStringLiterals() {
        String sqlContent = """
            INSERT INTO test VALUES (1, 'Text with ; semicolon');
            INSERT INTO test VALUES (2, "Another ; semicolon");
            """;
        
        List<String> statements = processor.parseStatements(sqlContent);
        
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("'Text with ; semicolon'"));
        assertTrue(statements.get(1).contains("\"Another ; semicolon\""));
    }
    
    @Test
    void testParseStatementsWithEscapedQuotes() {
        String sqlContent = """
            INSERT INTO test VALUES (1, 'Text with '' escaped quote');
            INSERT INTO test VALUES (2, "Text with "" escaped quote");
            """;
        
        List<String> statements = processor.parseStatements(sqlContent);
        
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("'Text with '' escaped quote'"));
        assertTrue(statements.get(1).contains("\"Text with \"\" escaped quote\""));
    }
    
    @Test
    void testParseStatementsWithoutSemicolon() {
        String sqlContent = "SELECT * FROM users";
        
        List<String> statements = processor.parseStatements(sqlContent);
        
        assertEquals(1, statements.size());
        assertEquals("SELECT * FROM users", statements.get(0));
    }
    
    @Test
    void testParseEmptyContent() {
        List<String> statements = processor.parseStatements("");
        assertTrue(statements.isEmpty());
        
        statements = processor.parseStatements(null);
        assertTrue(statements.isEmpty());
        
        statements = processor.parseStatements("   ");
        assertTrue(statements.isEmpty());
    }
    
    @Test
    void testValidateScript() throws IOException {
        // Valid SQL file
        String validSql = """
            CREATE TABLE test (id INT);
            INSERT INTO test VALUES (1);
            """;
        File validFile = createTempFile("valid.sql", validSql);
        assertTrue(processor.validateScript(validFile));
        
        // Invalid SQL file (unbalanced parentheses)
        String invalidSql = "CREATE TABLE test (id INT;";
        File invalidFile = createTempFile("invalid.sql", invalidSql);
        assertFalse(processor.validateScript(invalidFile));
        
        // Empty file
        File emptyFile = createTempFile("empty.sql", "");
        assertFalse(processor.validateScript(emptyFile));
    }
    
    @Test
    void testDDLStatementCategorization() throws IOException {
        String ddlContent = """
            CREATE TABLE users (id INT);
            ALTER TABLE users ADD COLUMN name VARCHAR(50);
            DROP TABLE temp_table;
            TRUNCATE TABLE logs;
            """;
        
        File ddlFile = createTempFile("ddl.sql", ddlContent);
        ProcessingResult result = processor.processFile(ddlFile, sqlConfig);
        
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        Map<String, Object> metadata = result.getMetadata();
        assertEquals(4, metadata.get("ddlCount"));
        assertEquals(0, metadata.get("dmlCount"));
        assertEquals(0, metadata.get("otherCount"));
    }
    
    @Test
    void testDMLStatementCategorization() throws IOException {
        String dmlContent = """
            INSERT INTO users VALUES (1, 'John');
            UPDATE users SET name = 'Jane' WHERE id = 1;
            DELETE FROM users WHERE id = 2;
            MERGE INTO target USING source ON (target.id = source.id);
            """;
        
        File dmlFile = createTempFile("dml.sql", dmlContent);
        ProcessingResult result = processor.processFile(dmlFile, sqlConfig);
        
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        Map<String, Object> metadata = result.getMetadata();
        assertEquals(0, metadata.get("ddlCount"));
        assertEquals(4, metadata.get("dmlCount"));
        assertEquals(0, metadata.get("otherCount"));
    }
    
    @Test
    void testMixedStatementCategorization() throws IOException {
        String mixedContent = """
            CREATE TABLE test (id INT);
            INSERT INTO test VALUES (1);
            SELECT * FROM test;
            GRANT SELECT ON test TO user1;
            """;
        
        File mixedFile = createTempFile("mixed.sql", mixedContent);
        ProcessingResult result = processor.processFile(mixedFile, sqlConfig);
        
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        Map<String, Object> metadata = result.getMetadata();
        assertEquals(1, metadata.get("ddlCount"));
        assertEquals(1, metadata.get("dmlCount"));
        assertEquals(2, metadata.get("otherCount")); // SELECT and GRANT
    }
    
    @Test
    void testCaseInsensitiveStatementCategorization() throws IOException {
        String caseInsensitiveContent = """
            create table test (id int);
            INSERT into test values (1);
            Update test set id = 2;
            """;
        
        File caseFile = createTempFile("case.sql", caseInsensitiveContent);
        ProcessingResult result = processor.processFile(caseFile, sqlConfig);
        
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        Map<String, Object> metadata = result.getMetadata();
        assertEquals(1, metadata.get("ddlCount"));
        assertEquals(2, metadata.get("dmlCount"));
        assertEquals(0, metadata.get("otherCount"));
    }
    
    private File createTempFile(String filename, String content) throws IOException {
        Path filePath = tempDir.resolve(filename);
        Files.write(filePath, content.getBytes());
        return filePath.toFile();
    }
}