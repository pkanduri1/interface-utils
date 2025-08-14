package com.fabric.watcher.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of processing a file through a FileProcessor.
 * Contains execution details, status, and any metadata from processing.
 */
public class ProcessingResult {
    
    /**
     * Execution status enumeration.
     */
    public enum ExecutionStatus {
        SUCCESS,
        FAILURE,
        SKIPPED
    }
    
    private String filename;
    private String processorType;
    private LocalDateTime executionTime;
    private ExecutionStatus status;
    private String errorMessage;
    private long executionDurationMs;
    private Map<String, Object> metadata;

    public ProcessingResult() {
    }

    public ProcessingResult(String filename, String processorType, ExecutionStatus status) {
        this.filename = filename;
        this.processorType = processorType;
        this.status = status;
        this.executionTime = LocalDateTime.now();
    }

    public ProcessingResult(String filename, String processorType, ExecutionStatus status, 
                           String errorMessage) {
        this(filename, processorType, status);
        this.errorMessage = errorMessage;
    }

    // Getters and setters
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getProcessorType() {
        return processorType;
    }

    public void setProcessorType(String processorType) {
        this.processorType = processorType;
    }

    public LocalDateTime getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(LocalDateTime executionTime) {
        this.executionTime = executionTime;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public void setExecutionDurationMs(long executionDurationMs) {
        this.executionDurationMs = executionDurationMs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS;
    }

    public boolean isFailure() {
        return status == ExecutionStatus.FAILURE;
    }

    @Override
    public String toString() {
        return "ProcessingResult{" +
                "filename='" + filename + '\'' +
                ", processorType='" + processorType + '\'' +
                ", status=" + status +
                ", executionDurationMs=" + executionDurationMs +
                '}';
    }
}