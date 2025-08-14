package com.fabric.watcher.config;

import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for a single watch configuration.
 * Used for binding YAML/properties to WatchConfig objects.
 */
@Validated
public class WatchConfigProperties {

    @NotBlank
    private String name;

    @NotBlank
    private String processorType;

    @NotBlank
    private String watchFolder;

    private String completedFolder;

    private String errorFolder;

    @NotEmpty
    private List<String> filePatterns = new ArrayList<>();

    @Min(1000)
    private long pollingInterval = 5000;

    private boolean enabled = true;

    @NotNull
    private Map<String, Object> processorSpecificConfig = new HashMap<>();

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

    public String getWatchFolder() {
        return watchFolder;
    }

    public void setWatchFolder(String watchFolder) {
        this.watchFolder = watchFolder;
    }

    public String getCompletedFolder() {
        return completedFolder;
    }

    public void setCompletedFolder(String completedFolder) {
        this.completedFolder = completedFolder;
    }

    public String getErrorFolder() {
        return errorFolder;
    }

    public void setErrorFolder(String errorFolder) {
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

    /**
     * Converts this properties object to a WatchConfig domain object.
     * Applies default values and validation.
     */
    public WatchConfig toWatchConfig() {
        WatchConfig config = new WatchConfig();
        config.setName(this.name);
        config.setProcessorType(this.processorType);
        config.setWatchFolder(Paths.get(this.watchFolder));
        
        // Set default completed folder if not specified
        if (this.completedFolder != null && !this.completedFolder.trim().isEmpty()) {
            config.setCompletedFolder(Paths.get(this.completedFolder));
        } else {
            config.setCompletedFolder(Paths.get(this.watchFolder, "completed"));
        }
        
        // Set default error folder if not specified
        if (this.errorFolder != null && !this.errorFolder.trim().isEmpty()) {
            config.setErrorFolder(Paths.get(this.errorFolder));
        } else {
            config.setErrorFolder(Paths.get(this.watchFolder, "error"));
        }
        
        config.setFilePatterns(new ArrayList<>(this.filePatterns));
        config.setPollingInterval(this.pollingInterval);
        config.setEnabled(this.enabled);
        config.setProcessorSpecificConfig(new HashMap<>(this.processorSpecificConfig));
        
        return config;
    }

    @Override
    public String toString() {
        return "WatchConfigProperties{" +
                "name='" + name + '\'' +
                ", processorType='" + processorType + '\'' +
                ", watchFolder='" + watchFolder + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}