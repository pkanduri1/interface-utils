package com.fabric.watcher.model;

import java.time.LocalDateTime;

/**
 * Data model for SQL*Loader audit information.
 * Contains details extracted from SQL*Loader log files for audit tracking.
 */
public class LogAuditInfo {
    
    private String logFilename;
    private String controlFilename;
    private String dataFilename;
    private LocalDateTime loadStartTime;
    private LocalDateTime loadEndTime;
    private long recordsLoaded;
    private long recordsRejected;
    private String loadStatus;
    private String errorDetails;
    private String tableName;
    private long totalRecords;
    private LocalDateTime auditTimestamp;

    public LogAuditInfo() {
        this.auditTimestamp = LocalDateTime.now();
    }

    public LogAuditInfo(String logFilename) {
        this();
        this.logFilename = logFilename;
    }

    // Getters and setters
    public String getLogFilename() {
        return logFilename;
    }

    public void setLogFilename(String logFilename) {
        this.logFilename = logFilename;
    }

    public String getControlFilename() {
        return controlFilename;
    }

    public void setControlFilename(String controlFilename) {
        this.controlFilename = controlFilename;
    }

    public String getDataFilename() {
        return dataFilename;
    }

    public void setDataFilename(String dataFilename) {
        this.dataFilename = dataFilename;
    }

    public LocalDateTime getLoadStartTime() {
        return loadStartTime;
    }

    public void setLoadStartTime(LocalDateTime loadStartTime) {
        this.loadStartTime = loadStartTime;
    }

    public LocalDateTime getLoadEndTime() {
        return loadEndTime;
    }

    public void setLoadEndTime(LocalDateTime loadEndTime) {
        this.loadEndTime = loadEndTime;
    }

    public long getRecordsLoaded() {
        return recordsLoaded;
    }

    public void setRecordsLoaded(long recordsLoaded) {
        this.recordsLoaded = recordsLoaded;
    }

    public long getRecordsRejected() {
        return recordsRejected;
    }

    public void setRecordsRejected(long recordsRejected) {
        this.recordsRejected = recordsRejected;
    }

    public String getLoadStatus() {
        return loadStatus;
    }

    public void setLoadStatus(String loadStatus) {
        this.loadStatus = loadStatus;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public LocalDateTime getAuditTimestamp() {
        return auditTimestamp;
    }

    public void setAuditTimestamp(LocalDateTime auditTimestamp) {
        this.auditTimestamp = auditTimestamp;
    }

    /**
     * Check if the load was successful based on status and error details.
     * 
     * @return true if load was successful
     */
    public boolean isLoadSuccessful() {
        return "SUCCESS".equalsIgnoreCase(loadStatus) || 
               ("COMPLETED".equalsIgnoreCase(loadStatus) && recordsRejected == 0);
    }

    /**
     * Get the duration of the load operation in milliseconds.
     * 
     * @return duration in milliseconds, or -1 if start/end times are not available
     */
    public long getLoadDurationMs() {
        if (loadStartTime != null && loadEndTime != null) {
            return java.time.Duration.between(loadStartTime, loadEndTime).toMillis();
        }
        return -1;
    }

    @Override
    public String toString() {
        return "LogAuditInfo{" +
                "logFilename='" + logFilename + '\'' +
                ", controlFilename='" + controlFilename + '\'' +
                ", dataFilename='" + dataFilename + '\'' +
                ", loadStatus='" + loadStatus + '\'' +
                ", recordsLoaded=" + recordsLoaded +
                ", recordsRejected=" + recordsRejected +
                ", tableName='" + tableName + '\'' +
                '}';
    }
}