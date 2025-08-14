package com.fabric.watcher.health;

import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for the file watcher service.
 */
@Component
public class FileWatcherHealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(FileWatcherHealthIndicator.class);
    
    private final FileWatcherService fileWatcherService;
    private final MetricsService metricsService;
    
    public FileWatcherHealthIndicator(FileWatcherService fileWatcherService, 
                                    MetricsService metricsService) {
        this.fileWatcherService = fileWatcherService;
        this.metricsService = metricsService;
    }
    
    /**
     * Check file watcher service health and return status information.
     * @return health status map
     */
    public Map<String, Object> checkHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            boolean isRunning = fileWatcherService.isRunning();
            Map<String, String> watchStatus = fileWatcherService.getWatchStatus();
            
            if (isRunning) {
                logger.debug("File watcher service health check passed");
                
                health.put("status", "UP");
                health.put("service", "Running");
                health.put("watchConfigurations", watchStatus.size());
                health.put("watchStatus", watchStatus);
                health.put("timestamp", System.currentTimeMillis());
                
                // Add statistics summary
                var allStats = metricsService.getAllStatistics();
                long totalProcessed = allStats.values().stream()
                    .mapToLong(stats -> stats.getTotalFilesProcessed())
                    .sum();
                long totalSuccess = allStats.values().stream()
                    .mapToLong(stats -> stats.getSuccessfulExecutions())
                    .sum();
                long totalFailed = allStats.values().stream()
                    .mapToLong(stats -> stats.getFailedExecutions())
                    .sum();
                
                health.put("totalFilesProcessed", totalProcessed);
                health.put("totalSuccessful", totalSuccess);
                health.put("totalFailed", totalFailed);
                
            } else {
                logger.warn("File watcher service health check failed: Service not running");
                
                health.put("status", "DOWN");
                health.put("service", "Stopped");
                health.put("error", "Service is not running");
                health.put("timestamp", System.currentTimeMillis());
            }
            
        } catch (Exception e) {
            logger.error("File watcher service health check failed with exception", e);
            
            health.put("status", "DOWN");
            health.put("service", "Error");
            health.put("error", e.getMessage());
            health.put("exception", e.getClass().getSimpleName());
            health.put("timestamp", System.currentTimeMillis());
        }
        
        return health;
    }
}