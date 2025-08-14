package com.fabric.watcher.health;

import com.fabric.watcher.service.DatabaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for database connectivity.
 */
@Component
public class DatabaseHealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthIndicator.class);
    
    private final DatabaseExecutor databaseExecutor;
    
    public DatabaseHealthIndicator(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }
    
    /**
     * Check database health and return status information.
     * @return health status map
     */
    public Map<String, Object> checkHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            boolean isConnected = databaseExecutor.testConnection();
            
            if (isConnected) {
                String databaseInfo = databaseExecutor.getDatabaseInfo();
                logger.debug("Database health check passed: {}", databaseInfo);
                
                health.put("status", "UP");
                health.put("database", "Connected");
                health.put("info", databaseInfo);
                health.put("timestamp", System.currentTimeMillis());
            } else {
                logger.warn("Database health check failed: Connection test failed");
                
                health.put("status", "DOWN");
                health.put("database", "Disconnected");
                health.put("error", "Connection test failed");
                health.put("timestamp", System.currentTimeMillis());
            }
            
        } catch (Exception e) {
            logger.error("Database health check failed with exception", e);
            
            health.put("status", "DOWN");
            health.put("database", "Error");
            health.put("error", e.getMessage());
            health.put("exception", e.getClass().getSimpleName());
            health.put("timestamp", System.currentTimeMillis());
        }
        
        return health;
    }
}