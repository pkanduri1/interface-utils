package com.fabric.watcher.controller;

import com.fabric.watcher.health.DatabaseHealthIndicator;
import com.fabric.watcher.health.FileWatcherHealthIndicator;
import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import com.fabric.watcher.util.CorrelationIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * REST controller for monitoring and controlling the file watcher service.
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);
    
    private final FileWatcherService fileWatcherService;
    private final MetricsService metricsService;
    private final DatabaseHealthIndicator databaseHealthIndicator;
    private final FileWatcherHealthIndicator fileWatcherHealthIndicator;
    
    public MonitoringController(FileWatcherService fileWatcherService, 
                              MetricsService metricsService,
                              DatabaseHealthIndicator databaseHealthIndicator,
                              FileWatcherHealthIndicator fileWatcherHealthIndicator) {
        this.fileWatcherService = fileWatcherService;
        this.metricsService = metricsService;
        this.databaseHealthIndicator = databaseHealthIndicator;
        this.fileWatcherHealthIndicator = fileWatcherHealthIndicator;
    }
    
    /**
     * Get overall service status.
     * @return service status information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting service status");
            
            Map<String, Object> status = new HashMap<>();
            status.put("serviceRunning", fileWatcherService.isRunning());
            status.put("correlationId", correlationId);
            status.put("timestamp", System.currentTimeMillis());
            
            // Add basic statistics
            ConcurrentMap<String, ProcessingStatistics> allStats = metricsService.getAllStatistics();
            status.put("totalConfigurations", allStats.size());
            status.put("statistics", allStats);
            
            logger.info("Service status retrieved successfully");
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            logger.error("Error retrieving service status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve status: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get processing statistics for all configurations.
     * @return processing statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, ProcessingStatistics>> getStatistics() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting processing statistics");
            
            ConcurrentMap<String, ProcessingStatistics> statistics = metricsService.getAllStatistics();
            
            logger.info("Retrieved statistics for {} configurations", statistics.size());
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            logger.error("Error retrieving statistics", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get processing statistics for a specific configuration.
     * @param configName the configuration name
     * @return processing statistics for the configuration
     */
    @GetMapping("/statistics/{configName}")
    public ResponseEntity<ProcessingStatistics> getStatistics(@PathVariable String configName) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting statistics for configuration: {}", configName);
            
            ProcessingStatistics statistics = metricsService.getStatistics(configName);
            
            if (statistics == null) {
                logger.warn("No statistics found for configuration: {}", configName);
                return ResponseEntity.notFound().build();
            }
            
            logger.info("Retrieved statistics for configuration: {}", configName);
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            logger.error("Error retrieving statistics for configuration: {}", configName, e);
            return ResponseEntity.internalServerError().build();
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
            
            boolean success = fileWatcherService.pauseWatching(configName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("configName", configName);
            response.put("action", "pause");
            response.put("correlationId", correlationId);
            
            if (success) {
                logger.info("Successfully paused file watching for configuration: {}", configName);
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Failed to pause file watching for configuration: {}", configName);
                response.put("error", "Configuration not found or already paused");
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error pausing file watching for configuration: {}", configName, e);
            Map<String, Object> errorResponse = new HashMap<>();
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
            
            boolean success = fileWatcherService.resumeWatching(configName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("configName", configName);
            response.put("action", "resume");
            response.put("correlationId", correlationId);
            
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
            errorResponse.put("error", "Failed to resume watching: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get metrics summary.
     * @return metrics summary
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Double>> getMetrics() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting metrics summary");
            
            Map<String, Double> metrics = metricsService.getMetricsSummary();
            
            logger.info("Retrieved {} metrics", metrics.size());
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            logger.error("Error retrieving metrics", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get watch status for all configurations.
     * @return watch status information
     */
    @GetMapping("/watch-status")
    public ResponseEntity<Map<String, String>> getWatchStatus() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting watch status for all configurations");
            
            Map<String, String> watchStatus = fileWatcherService.getWatchStatus();
            
            logger.info("Retrieved watch status for {} configurations", watchStatus.size());
            return ResponseEntity.ok(watchStatus);
            
        } catch (Exception e) {
            logger.error("Error retrieving watch status", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Get comprehensive health status including database and service health.
     * @return health status information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        String correlationId = CorrelationIdUtil.generateAndSet();
        
        try {
            logger.info("Getting comprehensive health status");
            
            Map<String, Object> health = new HashMap<>();
            health.put("correlationId", correlationId);
            health.put("timestamp", System.currentTimeMillis());
            
            // Get database health
            Map<String, Object> databaseHealth = databaseHealthIndicator.checkHealth();
            health.put("database", databaseHealth);
            
            // Get file watcher service health
            Map<String, Object> serviceHealth = fileWatcherHealthIndicator.checkHealth();
            health.put("fileWatcher", serviceHealth);
            
            // Determine overall status
            String databaseStatus = (String) databaseHealth.get("status");
            String serviceStatus = (String) serviceHealth.get("status");
            
            if ("UP".equals(databaseStatus) && "UP".equals(serviceStatus)) {
                health.put("status", "UP");
                logger.info("Overall health status: UP");
                return ResponseEntity.ok(health);
            } else {
                health.put("status", "DOWN");
                logger.warn("Overall health status: DOWN (database: {}, service: {})", 
                           databaseStatus, serviceStatus);
                return ResponseEntity.status(503).body(health);
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving health status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "DOWN");
            errorResponse.put("error", "Failed to retrieve health status: " + e.getMessage());
            errorResponse.put("correlationId", correlationId);
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(503).body(errorResponse);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
}