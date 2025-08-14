package com.fabric.watcher.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing file watcher configurations.
 * Handles validation, default values, and configuration access.
 */
@Service
@EnableConfigurationProperties(FileWatcherProperties.class)
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

    private final FileWatcherProperties properties;
    private List<WatchConfig> validatedConfigs;

    public ConfigurationService(FileWatcherProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validateAndInitialize() {
        logger.info("Initializing file watcher configurations...");
        
        this.validatedConfigs = new ArrayList<>();
        
        if (properties.getWatchConfigs().isEmpty()) {
            logger.warn("No watch configurations found. Service will start but no folders will be monitored.");
            return;
        }

        for (Map.Entry<String, WatchConfigProperties> entry : properties.getWatchConfigs().entrySet()) {
            String configKey = entry.getKey();
            WatchConfigProperties configProps = entry.getValue();
            
            try {
                WatchConfig config = validateAndCreateConfig(configKey, configProps);
                validatedConfigs.add(config);
                logger.info("Successfully validated configuration: {}", config.getName());
            } catch (Exception e) {
                logger.error("Failed to validate configuration '{}': {}", configKey, e.getMessage());
                // Continue with other configurations rather than failing completely
            }
        }
        
        logger.info("Initialized {} valid watch configurations out of {} total", 
                   validatedConfigs.size(), properties.getWatchConfigs().size());
    }

    /**
     * Validates a single watch configuration and creates a WatchConfig object.
     */
    private WatchConfig validateAndCreateConfig(String configKey, WatchConfigProperties configProps) {
        // Set name from key if not explicitly set
        if (configProps.getName() == null || configProps.getName().trim().isEmpty()) {
            configProps.setName(configKey);
        }

        // Validate required fields
        if (configProps.getProcessorType() == null || configProps.getProcessorType().trim().isEmpty()) {
            throw new IllegalArgumentException("Processor type is required for configuration: " + configKey);
        }

        if (configProps.getWatchFolder() == null || configProps.getWatchFolder().trim().isEmpty()) {
            throw new IllegalArgumentException("Watch folder is required for configuration: " + configKey);
        }

        if (configProps.getFilePatterns() == null || configProps.getFilePatterns().isEmpty()) {
            logger.warn("No file patterns specified for configuration '{}', defaulting to ['*']", configKey);
            configProps.setFilePatterns(List.of("*"));
        }

        // Validate polling interval
        if (configProps.getPollingInterval() < 1000) {
            logger.warn("Polling interval for configuration '{}' is less than 1000ms, setting to 1000ms", configKey);
            configProps.setPollingInterval(1000);
        }

        WatchConfig config = configProps.toWatchConfig();
        
        // Validate paths exist or can be created
        validateOrCreateDirectory(config.getWatchFolder(), "watch folder for " + configKey);
        validateOrCreateDirectory(config.getCompletedFolder(), "completed folder for " + configKey);
        validateOrCreateDirectory(config.getErrorFolder(), "error folder for " + configKey);

        return config;
    }

    /**
     * Validates that a directory exists or can be created.
     */
    private void validateOrCreateDirectory(Path directory, String description) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                logger.info("Created directory: {} ({})", directory, description);
            } else if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("Path exists but is not a directory: " + directory);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot create or access " + description + " at " + directory + ": " + e.getMessage());
        }
    }

    /**
     * Returns all validated watch configurations.
     */
    public List<WatchConfig> getAllConfigurations() {
        return new ArrayList<>(validatedConfigs);
    }

    /**
     * Returns all enabled watch configurations.
     */
    public List<WatchConfig> getEnabledConfigurations() {
        return validatedConfigs.stream()
                .filter(WatchConfig::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * Returns a specific configuration by name.
     */
    public WatchConfig getConfiguration(String name) {
        return validatedConfigs.stream()
                .filter(config -> config.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns global configuration settings.
     */
    public FileWatcherProperties.GlobalConfig getGlobalConfig() {
        return properties.getGlobal();
    }

    /**
     * Returns the number of valid configurations.
     */
    public int getConfigurationCount() {
        return validatedConfigs.size();
    }

    /**
     * Returns the number of enabled configurations.
     */
    public int getEnabledConfigurationCount() {
        return (int) validatedConfigs.stream().filter(WatchConfig::isEnabled).count();
    }
    
    /**
     * Update a watch configuration with new values.
     * 
     * @param configName the configuration name
     * @param updates map of property names to new values
     */
    public void updateWatchConfig(String configName, Map<String, Object> updates) {
        WatchConfigProperties configProps = properties.getWatchConfigs().get(configName);
        if (configProps == null) {
            throw new IllegalArgumentException("Configuration not found: " + configName);
        }
        
        // Apply updates with validation
        for (Map.Entry<String, Object> update : updates.entrySet()) {
            String property = update.getKey();
            Object value = update.getValue();
            
            switch (property) {
                case "polling-interval":
                    if (value instanceof Number) {
                        long interval = ((Number) value).longValue();
                        if (interval < 100) {
                            throw new IllegalArgumentException("Polling interval must be at least 100ms");
                        }
                        configProps.setPollingInterval(interval);
                    }
                    break;
                case "file-patterns":
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> patterns = (List<String>) value;
                        if (patterns.stream().anyMatch(p -> p == null || p.trim().isEmpty())) {
                            throw new IllegalArgumentException("File pattern cannot be null or empty");
                        }
                        configProps.setFilePatterns(patterns);
                    }
                    break;
                case "watch-folder":
                    if (value instanceof String) {
                        configProps.setWatchFolder((String) value);
                    }
                    break;
                case "completed-folder":
                    if (value instanceof String) {
                        configProps.setCompletedFolder((String) value);
                    }
                    break;
                case "error-folder":
                    if (value instanceof String) {
                        configProps.setErrorFolder((String) value);
                    }
                    break;
                case "enabled":
                    if (value instanceof Boolean) {
                        configProps.setEnabled((Boolean) value);
                    }
                    break;
                default:
                    logger.warn("Unknown configuration property: {}", property);
            }
        }
        
        // Re-validate and update the configuration
        try {
            WatchConfig updatedConfig = validateAndCreateConfig(configName, configProps);
            // Update in validated configs list
            validatedConfigs.removeIf(config -> config.getName().equals(configName));
            validatedConfigs.add(updatedConfig);
            logger.info("Successfully updated configuration: {}", configName);
        } catch (Exception e) {
            logger.error("Failed to update configuration '{}': {}", configName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Add a new watch configuration.
     * 
     * @param configName the configuration name
     * @param config the watch configuration
     */
    public void addWatchConfig(String configName, WatchConfig config) {
        // Convert WatchConfig to WatchConfigProperties
        WatchConfigProperties configProps = new WatchConfigProperties();
        configProps.setName(config.getName());
        configProps.setProcessorType(config.getProcessorType());
        configProps.setWatchFolder(config.getWatchFolder().toString());
        configProps.setCompletedFolder(config.getCompletedFolder().toString());
        configProps.setErrorFolder(config.getErrorFolder().toString());
        configProps.setFilePatterns(config.getFilePatterns());
        configProps.setPollingInterval(config.getPollingInterval());
        configProps.setEnabled(config.isEnabled());
        
        // Add to properties
        properties.getWatchConfigs().put(configName, configProps);
        
        // Validate and add to validated configs
        try {
            WatchConfig validatedConfig = validateAndCreateConfig(configName, configProps);
            validatedConfigs.add(validatedConfig);
            logger.info("Successfully added new configuration: {}", configName);
        } catch (Exception e) {
            properties.getWatchConfigs().remove(configName);
            logger.error("Failed to add configuration '{}': {}", configName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Remove a watch configuration.
     * 
     * @param configName the configuration name
     */
    public void removeWatchConfig(String configName) {
        properties.getWatchConfigs().remove(configName);
        validatedConfigs.removeIf(config -> config.getName().equals(configName));
        logger.info("Removed configuration: {}", configName);
    }
}