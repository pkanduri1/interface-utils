package com.fabric.watcher.processor;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.LogAuditInfo;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.service.DatabaseExecutor;
import com.fabric.watcher.util.CorrelationIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processor for SQL*Loader log files.
 * Parses log files and extracts audit information for database logging.
 */
@Component
public class SqlLoaderLogProcessor implements FileProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlLoaderLogProcessor.class);
    
    public static final String PROCESSOR_TYPE = "sqlloader-log";
    
    // Common SQL*Loader log patterns
    private static final Pattern CONTROL_FILE_PATTERN = Pattern.compile("Control File:\\s*(.+)");
    private static final Pattern DATA_FILE_PATTERN = Pattern.compile("Data File:\\s*(.+)");
    private static final Pattern TABLE_PATTERN = Pattern.compile("Table\\s+(\\w+)");
    private static final Pattern RECORDS_LOADED_PATTERN = Pattern.compile("(\\d+)\\s+Rows successfully loaded");
    private static final Pattern RECORDS_REJECTED_PATTERN = Pattern.compile("(\\d+)\\s+Rows not loaded due to data errors");
    private static final Pattern TOTAL_RECORDS_PATTERN = Pattern.compile("Total logical records skipped:\\s*(\\d+)");
    private static final Pattern START_TIME_PATTERN = Pattern.compile("Run began on\\s+(.+)");
    private static final Pattern END_TIME_PATTERN = Pattern.compile("Run ended on\\s+(.+)");
    private static final Pattern ERROR_PATTERN = Pattern.compile("SQL\\*Loader-\\d+:\\s*(.+)");
    
    // Date format commonly used in SQL*Loader logs
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
    };
    
    @Autowired
    private DatabaseExecutor databaseExecutor;
    
    @Override
    public boolean supports(WatchConfig config) {
        return PROCESSOR_TYPE.equals(config.getProcessorType());
    }
    
    @Override
    public ProcessingResult processFile(File file, WatchConfig config) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        CorrelationIdUtil.setFileName(file.getName());
        CorrelationIdUtil.setProcessorType(PROCESSOR_TYPE);
        logger.info("[{}] Starting SQL*Loader log processing for file: {}", correlationId, file.getName());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Parse the log file
            LogAuditInfo auditInfo = parseLogFile(file);
            
            // Write audit information to database
            writeAuditRecord(auditInfo);
            
            long duration = System.currentTimeMillis() - startTime;
            
            ProcessingResult result = new ProcessingResult(
                file.getName(), 
                PROCESSOR_TYPE, 
                ProcessingResult.ExecutionStatus.SUCCESS
            );
            result.setExecutionDurationMs(duration);
            
            // Add metadata about the processing
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("recordsLoaded", auditInfo.getRecordsLoaded());
            metadata.put("recordsRejected", auditInfo.getRecordsRejected());
            metadata.put("tableName", auditInfo.getTableName());
            metadata.put("loadStatus", auditInfo.getLoadStatus());
            result.setMetadata(metadata);
            
            logger.info("[{}] Successfully processed SQL*Loader log: {} (Records loaded: {}, Rejected: {})", 
                       correlationId, file.getName(), auditInfo.getRecordsLoaded(), auditInfo.getRecordsRejected());
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            logger.error("[{}] Failed to process SQL*Loader log file: {}", correlationId, file.getName(), e);
            
            ProcessingResult result = new ProcessingResult(
                file.getName(), 
                PROCESSOR_TYPE, 
                ProcessingResult.ExecutionStatus.FAILURE,
                e.getMessage()
            );
            result.setExecutionDurationMs(duration);
            
            return result;
        }
    }
    
    @Override
    public String getProcessorType() {
        return PROCESSOR_TYPE;
    }
    
    /**
     * Parse SQL*Loader log file and extract audit information.
     * 
     * @param logFile the log file to parse
     * @return LogAuditInfo containing extracted information
     * @throws IOException if file cannot be read
     */
    public LogAuditInfo parseLogFile(File logFile) throws IOException {
        LogAuditInfo auditInfo = new LogAuditInfo(logFile.getName());
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            StringBuilder errorDetails = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Parse control file
                Matcher matcher = CONTROL_FILE_PATTERN.matcher(line);
                if (matcher.find()) {
                    auditInfo.setControlFilename(matcher.group(1).trim());
                    continue;
                }
                
                // Parse data file
                matcher = DATA_FILE_PATTERN.matcher(line);
                if (matcher.find()) {
                    auditInfo.setDataFilename(matcher.group(1).trim());
                    continue;
                }
                
                // Parse table name
                matcher = TABLE_PATTERN.matcher(line);
                if (matcher.find()) {
                    auditInfo.setTableName(matcher.group(1).trim());
                    continue;
                }
                
                // Parse records loaded
                matcher = RECORDS_LOADED_PATTERN.matcher(line);
                if (matcher.find()) {
                    auditInfo.setRecordsLoaded(Long.parseLong(matcher.group(1)));
                    continue;
                }
                
                // Parse records rejected
                matcher = RECORDS_REJECTED_PATTERN.matcher(line);
                if (matcher.find()) {
                    auditInfo.setRecordsRejected(Long.parseLong(matcher.group(1)));
                    continue;
                }
                
                // Parse total records
                matcher = TOTAL_RECORDS_PATTERN.matcher(line);
                if (matcher.find()) {
                    auditInfo.setTotalRecords(Long.parseLong(matcher.group(1)));
                    continue;
                }
                
                // Parse start time
                matcher = START_TIME_PATTERN.matcher(line);
                if (matcher.find()) {
                    auditInfo.setLoadStartTime(parseDateTime(matcher.group(1).trim()));
                    continue;
                }
                
                // Parse end time
                matcher = END_TIME_PATTERN.matcher(line);
                if (matcher.find()) {
                    auditInfo.setLoadEndTime(parseDateTime(matcher.group(1).trim()));
                    continue;
                }
                
                // Parse errors
                matcher = ERROR_PATTERN.matcher(line);
                if (matcher.find()) {
                    if (errorDetails.length() > 0) {
                        errorDetails.append("; ");
                    }
                    errorDetails.append(matcher.group(1).trim());
                }
            }
            
            // Set error details if any were found
            if (errorDetails.length() > 0) {
                auditInfo.setErrorDetails(errorDetails.toString());
                auditInfo.setLoadStatus("ERROR");
            } else {
                // Determine load status based on rejected records
                if (auditInfo.getRecordsRejected() > 0) {
                    auditInfo.setLoadStatus("COMPLETED_WITH_ERRORS");
                } else {
                    auditInfo.setLoadStatus("SUCCESS");
                }
            }
            
            // Calculate total records if not explicitly found
            if (auditInfo.getTotalRecords() == 0) {
                auditInfo.setTotalRecords(auditInfo.getRecordsLoaded() + auditInfo.getRecordsRejected());
            }
        }
        
        return auditInfo;
    }
    
    /**
     * Parse date/time string using multiple format patterns.
     * 
     * @param dateTimeStr the date/time string to parse
     * @return LocalDateTime object, or null if parsing fails
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        
        logger.warn("Could not parse date/time: {}", dateTimeStr);
        return null;
    }
    
    /**
     * Write audit record to database.
     * 
     * @param auditInfo the audit information to write
     */
    private void writeAuditRecord(LogAuditInfo auditInfo) {
        try {
            String sql = """
                INSERT INTO sqlloader_audit (
                    log_filename, control_filename, data_filename, table_name,
                    load_start_time, load_end_time, records_loaded, records_rejected,
                    total_records, load_status, error_details, audit_timestamp
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            databaseExecutor.executeUpdate(sql,
                auditInfo.getLogFilename(),
                auditInfo.getControlFilename(),
                auditInfo.getDataFilename(),
                auditInfo.getTableName(),
                auditInfo.getLoadStartTime(),
                auditInfo.getLoadEndTime(),
                auditInfo.getRecordsLoaded(),
                auditInfo.getRecordsRejected(),
                auditInfo.getTotalRecords(),
                auditInfo.getLoadStatus(),
                auditInfo.getErrorDetails(),
                auditInfo.getAuditTimestamp()
            );
            
            logger.info("Successfully wrote audit record for log: {} (Status: {}, Records: {}/{})", 
                       auditInfo.getLogFilename(), auditInfo.getLoadStatus(), 
                       auditInfo.getRecordsLoaded(), auditInfo.getTotalRecords());
                       
        } catch (Exception e) {
            logger.error("Failed to write audit record for log: {}", auditInfo.getLogFilename(), e);
            throw new RuntimeException("Failed to write audit record", e);
        }
    }
}