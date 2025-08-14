package com.fabric.watcher.model;

import java.time.LocalDateTime;

/**
 * Statistics for file processing operations.
 */
public class ProcessingStatistics {
    
    private String configName;
    private String processorType;
    private long totalFilesProcessed;
    private long successfulExecutions;
    private long failedExecutions;
    private LocalDateTime lastProcessingTime;
    private String currentStatus;

    public ProcessingStatistics() {
    }

    public ProcessingStatistics(String configName, String processorType) {
        this.configName = configName;
        this.processorType = processorType;
        this.totalFilesProcessed = 0;
        this.successfulExecutions = 0;
        this.failedExecutions = 0;
        this.currentStatus = "IDLE";
    }

    // Getters and setters
    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getProcessorType() {
        return processorType;
    }

    public void setProcessorType(String processorType) {
        this.processorType = processorType;
    }

    public long getTotalFilesProcessed() {
        return totalFilesProcessed;
    }

    public void setTotalFilesProcessed(long totalFilesProcessed) {
        this.totalFilesProcessed = totalFilesProcessed;
    }

    public long getSuccessfulExecutions() {
        return successfulExecutions;
    }

    public void setSuccessfulExecutions(long successfulExecutions) {
        this.successfulExecutions = successfulExecutions;
    }

    public long getFailedExecutions() {
        return failedExecutions;
    }

    public void setFailedExecutions(long failedExecutions) {
        this.failedExecutions = failedExecutions;
    }

    public LocalDateTime getLastProcessingTime() {
        return lastProcessingTime;
    }

    public void setLastProcessingTime(LocalDateTime lastProcessingTime) {
        this.lastProcessingTime = lastProcessingTime;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public void incrementTotal() {
        this.totalFilesProcessed++;
    }

    public void incrementSuccess() {
        this.successfulExecutions++;
        this.lastProcessingTime = LocalDateTime.now();
    }

    public void incrementFailure() {
        this.failedExecutions++;
        this.lastProcessingTime = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "ProcessingStatistics{" +
                "configName='" + configName + '\'' +
                ", processorType='" + processorType + '\'' +
                ", totalFilesProcessed=" + totalFilesProcessed +
                ", successfulExecutions=" + successfulExecutions +
                ", failedExecutions=" + failedExecutions +
                '}';
    }
}