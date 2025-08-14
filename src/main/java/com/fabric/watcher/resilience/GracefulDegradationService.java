package com.fabric.watcher.resilience;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for handling graceful degradation when external systems
 * (like database) are unavailable.
 */
@Service
public class GracefulDegradationService {
    
    private static final Logger logger = LoggerFactory.getLogger(GracefulDegradationService.class);
    
    private final MetricsService metricsService;
    private final CircuitBreakerService circuitBreakerService;
    
    // Track degradation state for different components
    private final ConcurrentMap<String, DegradationState> degradationStates = new ConcurrentHashMap<>();
    
    // Global degradation mode
    private final AtomicBoolean globalDegradationMode = new AtomicBoolean(false);
    
    @Autowired
    public GracefulDegradationService(MetricsService metricsService, 
                                    CircuitBreakerService circuitBreakerService) {
        this.metricsService = metricsService;
        this.circuitBreakerService = circuitBreakerService;
    }
    
    /**
     * Check if the system should operate in degraded mode for a specific component.
     * 
     * @param component the component to check (database, filesystem, etc.)
     * @return true if the component is in degraded mode
     */
    public boolean isInDegradedMode(String component) {
        DegradationState state = degradationStates.get(component);
        return state != null && state.isDegraded();
    }
    
    /**
     * Check if the system is in global degradation mode.
     * 
     * @return true if in global degradation mode
     */
    public boolean isInGlobalDegradationMode() {
        return globalDegradationMode.get();
    }
    
    /**
     * Enter degraded mode for a specific component.
     * 
     * @param component the component entering degraded mode
     * @param reason the reason for degradation
     */
    public void enterDegradedMode(String component, String reason) {
        DegradationState state = degradationStates.computeIfAbsent(component, 
            k -> new DegradationState(component));
        
        if (!state.isDegraded()) {
            state.enterDegradedMode(reason);
            logger.warn("Component '{}' entered degraded mode: {}", component, reason);
            
            // Check if we should enter global degradation mode
            if ("database".equals(component)) {
                globalDegradationMode.set(true);
                logger.warn("System entered global degradation mode due to database unavailability");
            }
            
            metricsService.incrementCounter("file_watcher.degradation.entered", 
                "component", component);
        }
    }
    
    /**
     * Exit degraded mode for a specific component.
     * 
     * @param component the component exiting degraded mode
     */
    public void exitDegradedMode(String component) {
        DegradationState state = degradationStates.get(component);
        
        if (state != null && state.isDegraded()) {
            state.exitDegradedMode();
            logger.info("Component '{}' exited degraded mode after {} seconds", 
                       component, state.getDegradationDurationSeconds());
            
            // Check if we should exit global degradation mode
            if ("database".equals(component)) {
                globalDegradationMode.set(false);
                logger.info("System exited global degradation mode");
            }
            
            metricsService.incrementCounter("file_watcher.degradation.exited", 
                "component", component);
        }
    }
    
    /**
     * Handle file processing when database is unavailable.
     * Files are moved to a special queue folder for later processing.
     * 
     * @param file the file to handle
     * @param config the watch configuration
     * @return true if file was successfully queued for later processing
     */
    public boolean handleDatabaseUnavailable(File file, WatchConfig config) {
        try {
            Path queueFolder = getQueueFolder(config);
            Files.createDirectories(queueFolder);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String queuedFileName = timestamp + "_" + file.getName();
            Path queuedFile = queueFolder.resolve(queuedFileName);
            
            Files.move(file.toPath(), queuedFile, StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("Queued file '{}' for later processing due to database unavailability: {}", 
                       file.getName(), queuedFile);
            
            metricsService.incrementCounter("file_watcher.files.queued", 
                "config", config.getName(), "reason", "database_unavailable");
            
            return true;
            
        } catch (IOException e) {
            logger.error("Failed to queue file '{}' for later processing", file.getName(), e);
            metricsService.incrementCounter("file_watcher.files.queue_failed", 
                "config", config.getName());
            return false;
        }
    }
    
    /**
     * Process queued files when database becomes available again.
     * 
     * @param config the watch configuration
     * @return number of files processed from queue
     */
    public int processQueuedFiles(WatchConfig config) {
        if (isInDegradedMode("database")) {
            logger.debug("Database still in degraded mode, skipping queue processing");
            return 0;
        }
        
        Path queueFolder = getQueueFolder(config);
        if (!Files.exists(queueFolder)) {
            return 0;
        }
        
        int processedCount = 0;
        
        try {
            File[] queuedFiles = queueFolder.toFile().listFiles();
            if (queuedFiles == null || queuedFiles.length == 0) {
                return 0;
            }
            
            logger.info("Processing {} queued files for configuration '{}'", 
                       queuedFiles.length, config.getName());
            
            for (File queuedFile : queuedFiles) {
                if (processQueuedFile(queuedFile, config)) {
                    processedCount++;
                }
            }
            
            logger.info("Processed {} queued files for configuration '{}'", 
                       processedCount, config.getName());
            
        } catch (Exception e) {
            logger.error("Error processing queued files for configuration '{}'", 
                        config.getName(), e);
        }
        
        return processedCount;
    }
    
    /**
     * Process a single queued file.
     * 
     * @param queuedFile the queued file
     * @param config the watch configuration
     * @return true if successfully processed
     */
    private boolean processQueuedFile(File queuedFile, WatchConfig config) {
        try {
            // Extract original filename (remove timestamp prefix)
            String originalName = extractOriginalFileName(queuedFile.getName());
            Path watchFolder = config.getWatchFolder();
            Path restoredFile = watchFolder.resolve(originalName);
            
            // Move file back to watch folder for normal processing
            Files.move(queuedFile.toPath(), restoredFile, StandardCopyOption.REPLACE_EXISTING);
            
            logger.debug("Restored queued file '{}' to watch folder for processing", originalName);
            
            metricsService.incrementCounter("file_watcher.files.restored_from_queue", 
                "config", config.getName());
            
            return true;
            
        } catch (IOException e) {
            logger.error("Failed to restore queued file '{}' for processing", 
                        queuedFile.getName(), e);
            
            // Move to error folder if restoration fails
            try {
                Path errorFolder = config.getErrorFolder();
                Files.createDirectories(errorFolder);
                Path errorFile = errorFolder.resolve("queue_restore_failed_" + queuedFile.getName());
                Files.move(queuedFile.toPath(), errorFile, StandardCopyOption.REPLACE_EXISTING);
                
                logger.warn("Moved failed queue restoration file to error folder: {}", errorFile);
                
            } catch (IOException moveError) {
                logger.error("Failed to move queue restoration failure to error folder", moveError);
            }
            
            return false;
        }
    }
    
    /**
     * Get the queue folder for a watch configuration.
     * 
     * @param config the watch configuration
     * @return the queue folder path
     */
    private Path getQueueFolder(WatchConfig config) {
        return config.getWatchFolder().getParent().resolve("queue");
    }
    
    /**
     * Extract original filename from queued filename (remove timestamp prefix).
     * 
     * @param queuedFileName the queued filename
     * @return the original filename
     */
    private String extractOriginalFileName(String queuedFileName) {
        // Format: yyyyMMdd_HHmmss_originalname
        int secondUnderscoreIndex = queuedFileName.indexOf('_', queuedFileName.indexOf('_') + 1);
        if (secondUnderscoreIndex > 0 && secondUnderscoreIndex < queuedFileName.length() - 1) {
            return queuedFileName.substring(secondUnderscoreIndex + 1);
        }
        return queuedFileName; // Fallback to original name if format is unexpected
    }
    
    /**
     * Check system health and update degradation states accordingly.
     */
    public void checkSystemHealth() {
        // Check database health
        if (!circuitBreakerService.isDatabaseAvailable()) {
            enterDegradedMode("database", "Circuit breaker is open");
        } else {
            exitDegradedMode("database");
        }
        
        // Check file system health
        if (!circuitBreakerService.isFileSystemAvailable()) {
            enterDegradedMode("filesystem", "Circuit breaker is open");
        } else {
            exitDegradedMode("filesystem");
        }
        
        // Check external system health
        if (!circuitBreakerService.isExternalSystemAvailable()) {
            enterDegradedMode("external", "Circuit breaker is open");
        } else {
            exitDegradedMode("external");
        }
    }
    
    /**
     * Get degradation status for all components.
     * 
     * @return map of component names to their degradation status
     */
    public ConcurrentMap<String, DegradationState> getDegradationStates() {
        return new ConcurrentHashMap<>(degradationStates);
    }
    
    /**
     * Inner class to track degradation state for a component.
     */
    public static class DegradationState {
        private final String component;
        private volatile boolean degraded = false;
        private volatile LocalDateTime degradationStartTime;
        private volatile String degradationReason;
        
        public DegradationState(String component) {
            this.component = component;
        }
        
        public void enterDegradedMode(String reason) {
            this.degraded = true;
            this.degradationStartTime = LocalDateTime.now();
            this.degradationReason = reason;
        }
        
        public void exitDegradedMode() {
            this.degraded = false;
            this.degradationStartTime = null;
            this.degradationReason = null;
        }
        
        public boolean isDegraded() {
            return degraded;
        }
        
        public String getComponent() {
            return component;
        }
        
        public LocalDateTime getDegradationStartTime() {
            return degradationStartTime;
        }
        
        public String getDegradationReason() {
            return degradationReason;
        }
        
        public long getDegradationDurationSeconds() {
            if (degradationStartTime == null) {
                return 0;
            }
            return java.time.Duration.between(degradationStartTime, LocalDateTime.now()).getSeconds();
        }
    }
}