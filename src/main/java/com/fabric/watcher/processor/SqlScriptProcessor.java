package com.fabric.watcher.processor;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingResult.ExecutionStatus;
import com.fabric.watcher.service.DatabaseExecutor;
import com.fabric.watcher.service.DatabaseExecutor.SqlExecutionResult;
import com.fabric.watcher.util.CorrelationIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Processor for SQL script files.
 * Validates SQL syntax, categorizes statements, and prepares them for execution.
 */
@Component
public class SqlScriptProcessor implements FileProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlScriptProcessor.class);
    
    private static final String PROCESSOR_TYPE = "sql-script";
    
    @Autowired
    private DatabaseExecutor databaseExecutor;
    
    // SQL statement type patterns
    private static final Pattern DDL_PATTERN = Pattern.compile(
        "^\\s*(CREATE|ALTER|DROP|TRUNCATE)\\s+", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private static final Pattern DML_PATTERN = Pattern.compile(
        "^\\s*(INSERT|UPDATE|DELETE|MERGE)\\s+", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private static final Pattern SINGLE_LINE_COMMENT_PATTERN = Pattern.compile(
        "--.*$", 
        Pattern.MULTILINE
    );
    
    private static final Pattern MULTI_LINE_COMMENT_PATTERN = Pattern.compile(
        "/\\*.*?\\*/", 
        Pattern.DOTALL
    );
    
    @Override
    public boolean supports(WatchConfig config) {
        return PROCESSOR_TYPE.equals(config.getProcessorType());
    }
    
    @Override
    public ProcessingResult processFile(File file, WatchConfig config) {
        // Correlation ID should already be set by FileWatcherService
        logger.info("Processing SQL script file: {}", file.getName());
        
        long startTime = System.currentTimeMillis();
        ProcessingResult result = new ProcessingResult(
            file.getName(), 
            PROCESSOR_TYPE, 
            ExecutionStatus.FAILURE  // Default to failure, will be updated on success
        );
        result.setExecutionTime(LocalDateTime.now());
        
        try {
            // Validate file
            if (!isValidSqlFile(file)) {
                String error = "Invalid SQL file: " + file.getName();
                logger.error(error);
                result.setStatus(ExecutionStatus.FAILURE);
                result.setErrorMessage(error);
                return result;
            }
            
            // Read and parse SQL content
            String sqlContent = readFileContent(file);
            List<String> statements = parseStatements(sqlContent);
            
            if (statements.isEmpty()) {
                String error = "No valid SQL statements found in file: " + file.getName();
                logger.warn(error);
                result.setStatus(ExecutionStatus.SKIPPED);
                result.setErrorMessage(error);
                return result;
            }
            
            // Categorize statements
            Map<String, Object> metadata = categorizeStatements(statements);
            result.setMetadata(metadata);
            
            // Execute SQL statements using DatabaseExecutor
            SqlExecutionResult executionResult = databaseExecutor.executeScript(file, statements);
            
            if (executionResult.isSuccess()) {
                logger.info("Successfully executed SQL file: {} with {} statements in {}ms", 
                           file.getName(), statements.size(), executionResult.getExecutionTimeMs());
                result.setStatus(ExecutionStatus.SUCCESS);
                
                // Add execution details to metadata
                metadata.put("executionTimeMs", executionResult.getExecutionTimeMs());
                metadata.put("successfulStatements", executionResult.getSuccessfulStatements());
            } else {
                logger.error("Failed to execute SQL file: {} - {}", 
                           file.getName(), executionResult.getErrorMessage());
                result.setStatus(ExecutionStatus.FAILURE);
                result.setErrorMessage("SQL execution failed: " + executionResult.getErrorMessage());
                
                // Add failure details to metadata
                metadata.put("executionTimeMs", executionResult.getExecutionTimeMs());
                metadata.put("successfulStatements", executionResult.getSuccessfulStatements());
                metadata.put("failedStatement", executionResult.getFailedStatement());
            }
            
        } catch (Exception e) {
            logger.error("Error processing SQL file: {}", file.getName(), e);
            result.setStatus(ExecutionStatus.FAILURE);
            result.setErrorMessage("Processing error: " + e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            result.setExecutionDurationMs(duration);
        }
        
        return result;
    }
    
    @Override
    public String getProcessorType() {
        return PROCESSOR_TYPE;
    }
    
    /**
     * Validate if the file is a valid SQL file.
     */
    private boolean isValidSqlFile(File file) {
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        
        if (!file.getName().toLowerCase().endsWith(".sql")) {
            return false;
        }
        
        if (file.length() == 0) {
            logger.warn("SQL file is empty: {}", file.getName());
            return false;
        }
        
        return true;
    }
    
    /**
     * Read the content of the SQL file.
     */
    private String readFileContent(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }
    
    /**
     * Parse SQL content into individual statements.
     * Handles multi-statement files and removes comments.
     */
    public List<String> parseStatements(String sqlContent) {
        if (sqlContent == null || sqlContent.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // Remove comments
        String cleanedContent = removeComments(sqlContent);
        
        // Split by semicolon, but be careful with string literals
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();
        boolean inStringLiteral = false;
        char stringDelimiter = 0;
        
        for (int i = 0; i < cleanedContent.length(); i++) {
            char c = cleanedContent.charAt(i);
            
            if (!inStringLiteral && (c == '\'' || c == '"')) {
                inStringLiteral = true;
                stringDelimiter = c;
                currentStatement.append(c);
            } else if (inStringLiteral && c == stringDelimiter) {
                // Check for escaped quotes
                if (i + 1 < cleanedContent.length() && cleanedContent.charAt(i + 1) == stringDelimiter) {
                    currentStatement.append(c).append(stringDelimiter);
                    i++; // Skip the next character
                } else {
                    inStringLiteral = false;
                    currentStatement.append(c);
                }
            } else if (!inStringLiteral && c == ';') {
                String statement = currentStatement.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                currentStatement = new StringBuilder();
            } else {
                currentStatement.append(c);
            }
        }
        
        // Add the last statement if it doesn't end with semicolon
        String lastStatement = currentStatement.toString().trim();
        if (!lastStatement.isEmpty()) {
            statements.add(lastStatement);
        }
        
        return statements;
    }
    
    /**
     * Remove SQL comments from content while preserving string literals.
     */
    private String removeComments(String content) {
        // First remove multi-line comments
        String result = MULTI_LINE_COMMENT_PATTERN.matcher(content).replaceAll(" ");
        
        // Then remove single-line comments
        result = SINGLE_LINE_COMMENT_PATTERN.matcher(result).replaceAll("");
        
        return result;
    }
    
    /**
     * Categorize SQL statements into DDL, DML, and other types.
     */
    private Map<String, Object> categorizeStatements(List<String> statements) {
        Map<String, Object> metadata = new HashMap<>();
        
        int ddlCount = 0;
        int dmlCount = 0;
        int otherCount = 0;
        
        List<String> ddlStatements = new ArrayList<>();
        List<String> dmlStatements = new ArrayList<>();
        List<String> otherStatements = new ArrayList<>();
        
        for (String statement : statements) {
            String trimmedStatement = statement.trim();
            
            if (DDL_PATTERN.matcher(trimmedStatement).find()) {
                ddlCount++;
                ddlStatements.add(statement);
            } else if (DML_PATTERN.matcher(trimmedStatement).find()) {
                dmlCount++;
                dmlStatements.add(statement);
            } else {
                otherCount++;
                otherStatements.add(statement);
            }
        }
        
        metadata.put("totalStatements", statements.size());
        metadata.put("ddlCount", ddlCount);
        metadata.put("dmlCount", dmlCount);
        metadata.put("otherCount", otherCount);
        metadata.put("statements", statements);
        metadata.put("ddlStatements", ddlStatements);
        metadata.put("dmlStatements", dmlStatements);
        metadata.put("otherStatements", otherStatements);
        
        return metadata;
    }
    
    /**
     * Validate SQL syntax (basic validation).
     */
    public boolean validateScript(File sqlFile) {
        try {
            String content = readFileContent(sqlFile);
            List<String> statements = parseStatements(content);
            
            // Basic validation - check for balanced parentheses and quotes
            for (String statement : statements) {
                if (!isValidStatement(statement)) {
                    return false;
                }
            }
            
            return !statements.isEmpty();
            
        } catch (Exception e) {
            logger.error("Error validating SQL script: {}", sqlFile.getName(), e);
            return false;
        }
    }
    
    /**
     * Basic validation for individual SQL statements.
     */
    private boolean isValidStatement(String statement) {
        if (statement == null || statement.trim().isEmpty()) {
            return false;
        }
        
        // Check for balanced parentheses
        int parenthesesCount = 0;
        boolean inStringLiteral = false;
        char stringDelimiter = 0;
        
        for (char c : statement.toCharArray()) {
            if (!inStringLiteral && (c == '\'' || c == '"')) {
                inStringLiteral = true;
                stringDelimiter = c;
            } else if (inStringLiteral && c == stringDelimiter) {
                inStringLiteral = false;
            } else if (!inStringLiteral) {
                if (c == '(') {
                    parenthesesCount++;
                } else if (c == ')') {
                    parenthesesCount--;
                    if (parenthesesCount < 0) {
                        return false; // More closing than opening parentheses
                    }
                }
            }
        }
        
        return parenthesesCount == 0; // Balanced parentheses
    }
}