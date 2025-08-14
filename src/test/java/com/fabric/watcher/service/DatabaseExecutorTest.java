package com.fabric.watcher.service;

import com.fabric.watcher.service.DatabaseExecutor.SqlExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none"
})
class DatabaseExecutorTest {
    
    @Autowired
    private DatabaseExecutor databaseExecutor;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @BeforeEach
    void setUp() {
        // Clean up any existing test tables
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS test_users");
            jdbcTemplate.execute("DROP TABLE IF EXISTS test_products");
            jdbcTemplate.execute("DROP TABLE IF EXISTS test_orders");
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    @Test
    void testExecuteDDLStatements() {
        List<String> ddlStatements = Arrays.asList(
            "CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100))",
            "CREATE TABLE test_products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2))",
            "ALTER TABLE test_users ADD COLUMN email VARCHAR(255)"
        );
        
        File testFile = new File("test_ddl.sql");
        SqlExecutionResult result = databaseExecutor.executeScript(testFile, ddlStatements);
        
        assertTrue(result.isSuccess());
        assertEquals("test_ddl.sql", result.getFilename());
        assertEquals(3, result.getSuccessfulStatements());
        assertNull(result.getErrorMessage());
        assertTrue(result.getExecutionTimeMs() >= 0);
        
        // Verify tables were created
        Integer userTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'TEST_USERS'", 
            Integer.class
        );
        assertEquals(1, userTableCount);
        
        Integer productTableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'TEST_PRODUCTS'", 
            Integer.class
        );
        assertEquals(1, productTableCount);
    }
    
    @Test
    void testExecuteDMLStatements() {
        // First create the table
        jdbcTemplate.execute("CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100))");
        
        List<String> dmlStatements = Arrays.asList(
            "INSERT INTO test_users (id, name) VALUES (1, 'John Doe')",
            "INSERT INTO test_users (id, name) VALUES (2, 'Jane Smith')",
            "UPDATE test_users SET name = 'John Updated' WHERE id = 1"
        );
        
        File testFile = new File("test_dml.sql");
        SqlExecutionResult result = databaseExecutor.executeScript(testFile, dmlStatements);
        
        assertTrue(result.isSuccess());
        assertEquals(3, result.getSuccessfulStatements());
        assertNull(result.getErrorMessage());
        
        // Verify data was inserted and updated
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_users", Integer.class);
        assertEquals(2, count);
        
        String updatedName = jdbcTemplate.queryForObject(
            "SELECT name FROM test_users WHERE id = 1", String.class
        );
        assertEquals("John Updated", updatedName);
    }
    
    @Test
    void testExecuteMixedStatements() {
        List<String> mixedStatements = Arrays.asList(
            "CREATE TABLE test_orders (id INT PRIMARY KEY, customer_id INT, amount DECIMAL(10,2))",
            "INSERT INTO test_orders (id, customer_id, amount) VALUES (1, 100, 250.00)",
            "ALTER TABLE test_orders ADD COLUMN order_date DATE",
            "UPDATE test_orders SET amount = 300.00 WHERE id = 1"
        );
        
        File testFile = new File("test_mixed.sql");
        SqlExecutionResult result = databaseExecutor.executeScript(testFile, mixedStatements);
        
        assertTrue(result.isSuccess());
        assertEquals(4, result.getSuccessfulStatements());
        assertNull(result.getErrorMessage());
        
        // Verify table was created and data was inserted/updated
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_orders", Integer.class);
        assertEquals(1, count);
        
        Double amount = jdbcTemplate.queryForObject(
            "SELECT amount FROM test_orders WHERE id = 1", Double.class
        );
        assertEquals(300.00, amount, 0.01);
    }
    
    @Test
    void testExecuteEmptyStatements() {
        File testFile = new File("empty.sql");
        SqlExecutionResult result = databaseExecutor.executeScript(testFile, Collections.emptyList());
        
        assertTrue(result.isSuccess());
        assertEquals(0, result.getSuccessfulStatements());
    }
    
    @Test
    void testExecuteDDLWithError() {
        List<String> ddlStatements = Arrays.asList(
            "CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100))",
            "CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100))" // Duplicate table
        );
        
        File testFile = new File("test_ddl_error.sql");
        SqlExecutionResult result = databaseExecutor.executeScript(testFile, ddlStatements);
        
        assertFalse(result.isSuccess());
        assertEquals(1, result.getSuccessfulStatements()); // First statement succeeded
        assertNotNull(result.getErrorMessage());
        assertNotNull(result.getException());
        assertTrue(result.getErrorMessage().contains("DDL execution failed"));
    }
    
    @Test
    void testExecuteDMLWithErrorRollback() {
        // First create the table
        jdbcTemplate.execute("CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100))");
        
        List<String> dmlStatements = Arrays.asList(
            "INSERT INTO test_users (id, name) VALUES (1, 'John Doe')",
            "INSERT INTO test_users (id, name) VALUES (2, 'Jane Smith')",
            "INSERT INTO test_users (id, name) VALUES (1, 'Duplicate ID')" // This will fail
        );
        
        File testFile = new File("test_dml_error.sql");
        SqlExecutionResult result = databaseExecutor.executeScript(testFile, dmlStatements);
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertNotNull(result.getException());
        assertTrue(result.getErrorMessage().contains("DML execution failed"));
        
        // Verify rollback - no data should be inserted
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_users", Integer.class);
        assertEquals(0, count);
    }
    
    @Test
    void testExecuteDDLOnly() {
        String ddlStatement = "CREATE TABLE test_simple (id INT PRIMARY KEY)";
        
        assertDoesNotThrow(() -> {
            databaseExecutor.executeDDL(ddlStatement);
        });
        
        // Verify table was created
        Integer tableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'TEST_SIMPLE'", 
            Integer.class
        );
        assertEquals(1, tableCount);
    }
    
    @Test
    void testExecuteDMLOnly() {
        // First create the table
        jdbcTemplate.execute("CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100))");
        
        String dmlStatement = "INSERT INTO test_users (id, name) VALUES (1, 'Test User')";
        
        assertDoesNotThrow(() -> {
            databaseExecutor.executeDML(dmlStatement);
        });
        
        // Verify data was inserted
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_users", Integer.class);
        assertEquals(1, count);
    }
    
    @Test
    void testTestConnection() {
        assertTrue(databaseExecutor.testConnection());
    }
    
    @Test
    void testGetDatabaseInfo() {
        String dbInfo = databaseExecutor.getDatabaseInfo();
        assertNotNull(dbInfo);
        // H2 database info might not be available in the expected format
        // so we just check it's not null
    }
    
    @Test
    void testSqlExecutionResultCreation() {
        SqlExecutionResult result = new SqlExecutionResult("test.sql");
        
        assertEquals("test.sql", result.getFilename());
        assertFalse(result.isSuccess());
        assertEquals(0, result.getSuccessfulStatements());
        assertNull(result.getErrorMessage());
        assertNull(result.getException());
        assertEquals(0, result.getExecutionTimeMs());
        
        result.setSuccess(true);
        result.setErrorMessage("Test error");
        result.setExecutionTimeMs(1000);
        result.incrementSuccessfulStatements();
        result.incrementSuccessfulStatements();
        
        assertTrue(result.isSuccess());
        assertEquals("Test error", result.getErrorMessage());
        assertEquals(1000, result.getExecutionTimeMs());
        assertEquals(2, result.getSuccessfulStatements());
        
        String toString = result.toString();
        assertTrue(toString.contains("test.sql"));
        assertTrue(toString.contains("success=true"));
        assertTrue(toString.contains("executionTimeMs=1000"));
        assertTrue(toString.contains("successfulStatements=2"));
    }
    
    @Test
    void testExecuteWithInvalidSQL() {
        List<String> invalidStatements = Arrays.asList(
            "INVALID SQL STATEMENT",
            "SELECT * FROM non_existent_table"
        );
        
        File testFile = new File("invalid.sql");
        SqlExecutionResult result = databaseExecutor.executeScript(testFile, invalidStatements);
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertNotNull(result.getException());
        assertEquals(0, result.getSuccessfulStatements());
    }
    
    @Test
    void testExecuteWithMixedValidInvalidStatements() {
        List<String> mixedStatements = Arrays.asList(
            "CREATE TABLE test_mixed_valid (id INT PRIMARY KEY)",
            "INVALID SQL STATEMENT"
        );
        
        File testFile = new File("mixed_valid_invalid.sql");
        SqlExecutionResult result = databaseExecutor.executeScript(testFile, mixedStatements);
        
        assertFalse(result.isSuccess());
        assertEquals(1, result.getSuccessfulStatements()); // First statement succeeded
        assertNotNull(result.getErrorMessage());
        assertNotNull(result.getException());
        
        // Verify first table was created despite second statement failing
        Integer tableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'TEST_MIXED_VALID'", 
            Integer.class
        );
        assertEquals(1, tableCount);
    }
}