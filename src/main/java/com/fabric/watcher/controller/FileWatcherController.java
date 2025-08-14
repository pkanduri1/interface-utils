package com.fabric.watcher.controller;

import com.fabric.watcher.config.ConfigurationService;
import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import com.fabric.watcher.util.CorrelationIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * REST controller for monitoring and controlling the file watcher service.
 * Provides endpoints for status monitoring, configuration management, and service control.
 */
@RestController
@RequestMapping("/api/file-watcher")
public class FileWatcherController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileWatcherController.class);
    
    private final FileWatcherService fileWatcherService;
    private final MetricsService metricsService;
    private final ConfigurationService configurationService;
    
    public FileWatcherController(FileWatcherService fileWatcherService,
                               MetricsService metricsService,
                               ConfigurationService configurationService) {
        this.fileWatcherService = fileWatcherService;
        this.metricsService = metricsService;
        this.configurationService = configurationService;
    }
    
    /**
     * Get overall service status and statistics for all watch configurations.
     * @return comprehensive service status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting comprehensive file watcher service status");
            
            Map<String, Object> status = new HashMap<>();
            status.put("correlationId", correlationId);
            status.put("timestamp", LocalDateTime.now());
            status.put("serviceRunning", fileWatcherService.isRunning());
            
            // Get watch status for all configurations
            Map<String, String> watchStatus = fileWatcherService.getWatchStatus();
            status.put("watchStatus", watchStatus);
            
            // Get statistics for all configurations
            ConcurrentMap<String, ProcessingStatistics> allStats = metricsService.getAllStatistics();
            status.put("statistics", allStats);
            
            // Summary information
            status.put("totalConfigurations", configurationService.getConfigurationCount());
            status.put("enabledConfigurations", configurationService.getEnabledConfigurationCount());
            status.put("activeWatchers", watchStatus.size());
            
            // Calculate overall success rate
            double overallSuccessRate = calculateOverallSuccessRate(allStats);
            status.put("overallSuccessRate", overallSuccessRate);
            
            logger.info("Service status retrieved successfully for {} configurations", watchStatus.size());
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error retrieving service status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve status: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            errorResponse.put("timestamp", LocalDateTime.now());
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Pause file watching for a specific configuration.
     * @param configName the configuration name
     * @return operation result
     */
    @PostMapping("/pause/{configName}")
    public ResponseEntity<Map<String, Object>> pauseWatching(@PathVariable String configName) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Pausing file watching for configuration: {}", configName);
            
            // Validate configuration exists
            WatchConfig config = configurationService.getConfiguration(configName);
            if (config == null) {
                logger.warn("Configuration '{}' not found", configName);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Configuration not found: " + configName);
                errorResponse.put("correlationId", correlationId);
                return ResponseEntity.notFound().build();
            }
            
            boolean success = fileWatcherService.pauseWatching(configName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("configName", configName);
            response.put("action", "pause");
            response.put("correlationId", correlationId);
            response.put("timestamp", LocalDateTime.now());
            
            if (success) {
                logger.info("Successfully paused file watching for configuration: {}", configName);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Failed to pause file watching for configuration: {}", configName);
                response.put("error", "Configuration not active or already paused");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error pausing file watching for configuration: {}", configName, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to pause watching: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Resume file watching for a specific configuration.
     * @param configName the configuration name
     * @return operation result
     */
    @PostMapping("/resume/{configName}")
    public ResponseEntity<Map<String, Object>> resumeWatching(@PathVariable String configName) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Resuming file watching for configuration: {}", configName);
            
            // Validate configuration exists
            WatchConfig config = configurationService.getConfiguration(configName);
            if (config == null) {
                logger.warn("Configuration '{}' not found", configName);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Configuration not found: " + configName);
                errorResponse.put("correlationId", correlationId);
                return ResponseEntity.notFound().build();
            }
            
            boolean success = fileWatcherService.resumeWatching(configName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("configName", configName);
            response.put("action", "resume");
            response.put("correlationId", correlationId);
            response.put("timestamp", LocalDateTime.now());
            
            if (success) {
                logger.info("Successfully resumed file watching for configuration: {}", configName);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Failed to resume file watching for configuration: {}", configName);
                response.put("error", "Configuration not found or already running");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error resuming file watching for configuration: {}", configName, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to resume watching: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get processing history and statistics for all configurations.
     * @return processing history and statistics
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getProcessingHistory() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting processing history for all configurations");
            
            Map<String, Object> history = new HashMap<>();
            history.put("correlationId", correlationId);
            history.put("timestamp", LocalDateTime.now());
            
            // Get all statistics
            ConcurrentMap<String, ProcessingStatistics> allStats = metricsService.getAllStatistics();
            history.put("statistics", allStats);
            
            // Get metrics summary
            Map<String, Double> metricsSummary = metricsService.getMetricsSummary();
            history.put("metrics", metricsSummary);
            
            // Calculate summary information
            Map<String, Object> summary = calculateHistorySummary(allStats);
            history.put("summary", summary);
            
            logger.info("Retrieved processing history for {} configurations", allStats.size());
            return ResponseEntity.ok(history);
            
        } catch (Exception e) {
            logger.error("Error retrieving processing history", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve processing history: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get processing history and statistics for a specific configuration.
     * @param configName the configuration name
     * @return processing history and statistics for the configuration
     */
    @GetMapping("/history/{configName}")
    public ResponseEntity<Map<String, Object>> getProcessingHistory(@PathVariable String configName) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting processing history for configuration: {}", configName);
            
            // Validate configuration exists
            WatchConfig config = configurationService.getConfiguration(configName);
            if (config == null) {
                logger.warn("Configuration '{}' not found", configName);
                return ResponseEntity.notFound().build();
            }
            
            ProcessingStatistics statistics = metricsService.getStatistics(configName);
            if (statistics == null) {
                logger.warn("No statistics found for configuration: {}", configName);
                // Return empty statistics for valid configuration
                statistics = new ProcessingStatistics(configName, config.getProcessorType());
            }
            
            Map<String, Object> history = new HashMap<>();
            history.put("correlationId", correlationId);
            history.put("timestamp", LocalDateTime.now());
            history.put("configName", configName);
            history.put("statistics", statistics);
            
            // Add configuration details
            Map<String, Object> configDetails = new HashMap<>();
            configDetails.put("name", config.getName());
            configDetails.put("processorType", config.getProcessorType());
            configDetails.put("watchFolder", config.getWatchFolder().toString());
            configDetails.put("enabled", config.isEnabled());
            configDetails.put("pollingInterval", config.getPollingInterval());
            configDetails.put("filePatterns", config.getFilePatterns());
            history.put("configuration", configDetails);
            
            // Add current watch status
            Map<String, String> watchStatus = fileWatcherService.getWatchStatus();
            history.put("currentStatus", watchStatus.getOrDefault(configName, "UNKNOWN"));
            
            logger.info("Retrieved processing history for configuration: {}", configName);
            return ResponseEntity.ok(history);
            
        } catch (Exception e) {
            logger.error("Error retrieving processing history for configuration: {}", configName, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve processing history: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get all watch configurations.
     * @return list of all watch configurations
     */
    @GetMapping("/configurations")
    public ResponseEntity<Map<String, Object>> getConfigurations() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting all watch configurations");
            
            List<WatchConfig> allConfigs = configurationService.getAllConfigurations();
            List<WatchConfig> enabledConfigs = configurationService.getEnabledConfigurations();
            
            Map<String, Object> response = new HashMap<>();
            response.put("correlationId", correlationId);
            response.put("timestamp", LocalDateTime.now());
            response.put("totalCount", allConfigs.size());
            response.put("enabledCount", enabledConfigs.size());
            
            // Convert configurations to response format
            List<Map<String, Object>> configList = new ArrayList<>();
            for (WatchConfig config : allConfigs) {
                Map<String, Object> configMap = new HashMap<>();
                configMap.put("name", config.getName());
                configMap.put("processorType", config.getProcessorType());
                configMap.put("watchFolder", config.getWatchFolder().toString());
                configMap.put("completedFolder", config.getCompletedFolder().toString());
                configMap.put("errorFolder", config.getErrorFolder().toString());
                configMap.put("filePatterns", config.getFilePatterns());
                configMap.put("pollingInterval", config.getPollingInterval());
                configMap.put("enabled", config.isEnabled());
                
                // Add current status
                Map<String, String> watchStatus = fileWatcherService.getWatchStatus();
                configMap.put("currentStatus", watchStatus.getOrDefault(config.getName(), "UNKNOWN"));
                
                configList.add(configMap);
            }
            
            response.put("configurations", configList);
            
            logger.info("Retrieved {} configurations ({} enabled)", allConfigs.size(), enabledConfigs.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving configurations", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve configurations: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get a specific watch configuration.
     * @param configName the configuration name
     * @return the watch configuration
     */
    @GetMapping("/configurations/{configName}")
    public ResponseEntity<Map<String, Object>> getConfiguration(@PathVariable String configName) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting configuration: {}", configName);
            
            WatchConfig config = configurationService.getConfiguration(configName);
            if (config == null) {
                logger.warn("Configuration '{}' not found", configName);
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("correlationId", correlationId);
            response.put("timestamp", LocalDateTime.now());
            response.put("name", config.getName());
            response.put("processorType", config.getProcessorType());
            response.put("watchFolder", config.getWatchFolder().toString());
            response.put("completedFolder", config.getCompletedFolder().toString());
            response.put("errorFolder", config.getErrorFolder().toString());
            response.put("filePatterns", config.getFilePatterns());
            response.put("pollingInterval", config.getPollingInterval());
            response.put("enabled", config.isEnabled());
            
            // Add current status and statistics
            Map<String, String> watchStatus = fileWatcherService.getWatchStatus();
            response.put("currentStatus", watchStatus.getOrDefault(configName, "UNKNOWN"));
            
            ProcessingStatistics statistics = metricsService.getStatistics(configName);
            response.put("statistics", statistics);
            
            logger.info("Retrieved configuration: {}", configName);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving configuration: {}", configName, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve configuration: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Add a new watch configuration dynamically.
     * @param configRequest the configuration request
     * @return operation result
     */
    @PostMapping("/configurations")
    public ResponseEntity<Map<String, Object>> addWatchConfig(@RequestBody Map<String, Object> configRequest) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Adding new watch configuration");
            
            // Extract configuration details from request
            String name = (String) configRequest.get("name");
            String processorType = (String) configRequest.get("processorType");
            String watchFolder = (String) configRequest.get("watchFolder");
            
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse(
                    "Configuration name is required", correlationId));
            }
            
            if (processorType == null || processorType.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse(
                    "Processor type is required", correlationId));
            }
            
            if (watchFolder == null || watchFolder.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse(
                    "Watch folder is required", correlationId));
            }
            
            // Check if configuration already exists
            if (configurationService.getConfiguration(name) != null) {
                return ResponseEntity.badRequest().body(createErrorResponse(
                    "Configuration with name '" + name + "' already exists", correlationId));
            }
            
            // Create WatchConfig object
            WatchConfig newConfig = createWatchConfigFromRequest(configRequest);
            
            // Register the new configuration
            fileWatcherService.registerWatchConfig(newConfig);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration added successfully");
            response.put("configName", name);
            response.put("correlationId", correlationId);
            response.put("timestamp", LocalDateTime.now());
            
            logger.info("Successfully added new watch configuration: {}", name);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error adding watch configuration", e);
            return ResponseEntity.internalServerError().body(createErrorResponse(
                "Failed to add configuration: " + e.getMessage(), correlationId));
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Remove a watch configuration dynamically.
     * @param configName the configuration name to remove
     * @return operation result
     */
    @DeleteMapping("/configurations/{configName}")
    public ResponseEntity<Map<String, Object>> removeWatchConfig(@PathVariable String configName) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Removing watch configuration: {}", configName);
            
            // Validate configuration exists
            WatchConfig config = configurationService.getConfiguration(configName);
            if (config == null) {
                logger.warn("Configuration '{}' not found", configName);
                return ResponseEntity.notFound().build();
            }
            
            // Unregister the configuration
            fileWatcherService.unregisterWatchConfig(configName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration removed successfully");
            response.put("configName", configName);
            response.put("correlationId", correlationId);
            response.put("timestamp", LocalDateTime.now());
            
            logger.info("Successfully removed watch configuration: {}", configName);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error removing watch configuration: {}", configName, e);
            return ResponseEntity.internalServerError().body(createErrorResponse(
                "Failed to remove configuration: " + e.getMessage(), correlationId));
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    // Helper methods
    
    private double calculateOverallSuccessRate(ConcurrentMap<String, ProcessingStatistics> allStats) {
        if (allStats.isEmpty()) {
            return 0.0;
        }
        
        long totalProcessed = 0;
        long totalSuccess = 0;
        
        for (ProcessingStatistics stats : allStats.values()) {
            totalProcessed += stats.getTotalFilesProcessed();
            totalSuccess += stats.getSuccessfulExecutions();
        }
        
        return totalProcessed > 0 ? (double) totalSuccess / totalProcessed * 100.0 : 0.0;
    }
    
    private Map<String, Object> calculateHistorySummary(ConcurrentMap<String, ProcessingStatistics> allStats) {
        Map<String, Object> summary = new HashMap<>();
        
        long totalProcessed = 0;
        long totalSuccess = 0;
        long totalFailed = 0;
        LocalDateTime lastProcessingTime = null;
        
        for (ProcessingStatistics stats : allStats.values()) {
            totalProcessed += stats.getTotalFilesProcessed();
            totalSuccess += stats.getSuccessfulExecutions();
            totalFailed += stats.getFailedExecutions();
            
            if (stats.getLastProcessingTime() != null) {
                if (lastProcessingTime == null || stats.getLastProcessingTime().isAfter(lastProcessingTime)) {
                    lastProcessingTime = stats.getLastProcessingTime();
                }
            }
        }
        
        summary.put("totalProcessed", totalProcessed);
        summary.put("totalSuccess", totalSuccess);
        summary.put("totalFailed", totalFailed);
        summary.put("successRate", totalProcessed > 0 ? (double) totalSuccess / totalProcessed * 100.0 : 0.0);
        summary.put("lastProcessingTime", lastProcessingTime);
        summary.put("activeConfigurations", allStats.size());
        
        return summary;
    }
    
    private WatchConfig createWatchConfigFromRequest(Map<String, Object> request) {
        // This is a simplified implementation - in a real scenario, you'd want more robust validation
        // and potentially integrate with the ConfigurationService for proper validation
        
        WatchConfig config = new WatchConfig();
        config.setName((String) request.get("name"));
        config.setProcessorType((String) request.get("processorType"));
        config.setWatchFolder(java.nio.file.Paths.get((String) request.get("watchFolder")));
        
        // Set optional fields with defaults
        String completedFolder = (String) request.getOrDefault("completedFolder", 
            request.get("watchFolder") + "/completed");
        config.setCompletedFolder(java.nio.file.Paths.get(completedFolder));
        
        String errorFolder = (String) request.getOrDefault("errorFolder", 
            request.get("watchFolder") + "/error");
        config.setErrorFolder(java.nio.file.Paths.get(errorFolder));
        
        @SuppressWarnings("unchecked")
        List<String> filePatterns = (List<String>) request.getOrDefault("filePatterns", 
            Arrays.asList("*"));
        config.setFilePatterns(filePatterns);
        
        Long pollingInterval = ((Number) request.getOrDefault("pollingInterval", 5000L)).longValue();
        config.setPollingInterval(pollingInterval);
        
        Boolean enabled = (Boolean) request.getOrDefault("enabled", true);
        config.setEnabled(enabled);
        
        return config;
    }
    
    private Map<String, Object> createErrorResponse(String message, String correlationId) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", message);
        errorResponse.put("correlationId", correlationId);
        errorResponse.put("timestamp", LocalDateTime.now());
        return errorResponse;
    }
}