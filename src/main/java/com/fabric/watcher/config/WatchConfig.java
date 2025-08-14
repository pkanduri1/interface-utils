package com.fabric.watcher.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a file watcher instance.
 * Defines what folder to watch, how to process files, and where to move them.
 */
public class WatchConfig {
    
    private String name;
    private String processorType;
    private Path watchFolder;
    private Path completedFolder;
    private Path errorFolder;
    private List<String> filePatterns;
    private long pollingInterval;
    private boolean enabled;
    private Map<String, Object> processorSpecificConfig;

    public WatchConfig() {
    }

    public WatchConfig(String name, String processorType, Path watchFolder, 
                      Path completedFolder, Path errorFolder, List<String> filePatterns, 
                      long pollingInterval, boolean enabled) {
        this.name = name;
        this.processorType = processorType;
        this.watchFolder = watchFolder;
        this.completedFolder = completedFolder;
        this.errorFolder = errorFolder;
        this.filePatterns = filePatterns;
        this.pollingInterval = pollingInterval;
        this.enabled = enabled;
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProcessorType() {
        return processorType;
    }

    public void setProcessorType(String processorType) {
        this.processorType = processorType;
    }

    public Path getWatchFolder() {
        return watchFolder;
    }

    public void setWatchFolder(Path watchFolder) {
        this.watchFolder = watchFolder;
    }

    public Path getCompletedFolder() {
        return completedFolder;
    }

    public void setCompletedFolder(Path completedFolder) {
        this.completedFolder = completedFolder;
    }

    public Path getErrorFolder() {
        return errorFolder;
    }

    public void setErrorFolder(Path errorFolder) {
        this.errorFolder = errorFolder;
    }

    public List<String> getFilePatterns() {
        return filePatterns;
    }

    public void setFilePatterns(List<String> filePatterns) {
        this.filePatterns = filePatterns;
    }

    public long getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(long pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getProcessorSpecificConfig() {
        return processorSpecificConfig;
    }

    public void setProcessorSpecificConfig(Map<String, Object> processorSpecificConfig) {
        this.processorSpecificConfig = processorSpecificConfig;
    }

    @Override
    public String toString() {
        return "WatchConfig{" +
                "name='" + name + '\'' +
                ", processorType='" + processorType + '\'' +
                ", watchFolder=" + watchFolder +
                ", enabled=" + enabled +
                '}';
    }
}