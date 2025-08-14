package com.fabric.watcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the file watcher service.
 * Supports multiple watch configurations and global settings.
 */
@ConfigurationProperties(prefix = "file-watcher")
@Validated
public class FileWatcherProperties {

    @NestedConfigurationProperty
    private GlobalConfig global = new GlobalConfig();

    @Valid
    @NotNull
    private Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();

    public GlobalConfig getGlobal() {
        return global;
    }

    public void setGlobal(GlobalConfig global) {
        this.global = global;
    }

    public Map<String, WatchConfigProperties> getWatchConfigs() {
        return watchConfigs;
    }

    public void setWatchConfigs(Map<String, WatchConfigProperties> watchConfigs) {
        this.watchConfigs = watchConfigs;
    }

    /**
     * Global configuration settings that apply to all watch configurations.
     */
    public static class GlobalConfig {
        
        @Min(1)
        private int maxRetryAttempts = 3;
        
        @Min(100)
        private long retryDelay = 1000;

        public int getMaxRetryAttempts() {
            return maxRetryAttempts;
        }

        public void setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
        }

        public long getRetryDelay() {
            return retryDelay;
        }

        public void setRetryDelay(long retryDelay) {
            this.retryDelay = retryDelay;
        }
    }
}