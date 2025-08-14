package com.fabric.watcher.service;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.error.ErrorHandler;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.resilience.GracefulDegradationService;
import com.fabric.watcher.util.CorrelationIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Core file watcher service that monitors multiple configured folders
 * and coordinates file processing through appropriate processors.
 */
@Service
public class FileWatcherService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileWatcherService.class);
    
    private final FileProcessorRegistry fileProcessorRegistry;
    private final MetricsService metricsService;
    private final ErrorHandler errorHandler;
    private final GracefulDegradationService gracefulDegradationService;
    private final Map<String, WatcherTask> watcherTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    private final ScheduledExecutorService healthCheckExecutor = Executors.newScheduledThreadPool(1);
    private volatile boolean running = false;
    
    @Autowired
    public FileWatcherService(FileProcessorRegistry fileProcessorRegistry, 
                            MetricsService metricsService,
                            ErrorHandler errorHandler,
                            GracefulDegradationService gracefulDegradationService) {
        this.fileProcessorRegistry = fileProcessorRegistry;
        this.metricsService = metricsService;
        this.errorHandler = errorHandler;
        this.gracefulDegradationService = gracefulDegradationService;
    }
    
    /**
     * Start watching all configured folders.
     */
    @PostConstruct
    public void startWatching() {
        logger.info("Starting FileWatcherService");
        running = true;
        
        // Start health check scheduler
        healthCheckExecutor.scheduleWithFixedDelay(
            this::performHealthCheck,
            30, // Initial delay
            60, // Check every minute
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop watching all folders and cleanup resources.
     */
    @PreDestroy
    public void stopWatching() {
        logger.info("Stopping FileWatcherService");
        running = false;
        
        // Stop all watcher tasks
        watcherTasks.values().forEach(WatcherTask::stop);
        watcherTasks.clear();
        
        // Shutdown executor services
        executorService.shutdown();
        healthCheckExecutor.shutdown();
        
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!healthCheckExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            healthCheckExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Register a new watch configuration and start monitoring.
     * 
     * @param config the watch configuration
     */
    public void registerWatchConfig(WatchConfig config) {
        if (!config.isEnabled()) {
            logger.info("Watch configuration '{}' is disabled, skipping", config.getName());
            return;
        }
        
        logger.info("Registering watch configuration: {}", config.getName());
        
        try {
            // Ensure directories exist
            ensureDirectoriesExist(config);
            
            // Initialize metrics for this configuration
            metricsService.initializeStatistics(config.getName(), config.getProcessorType());
            
            // Create and start watcher task
            WatcherTask watcherTask = new WatcherTask(config);
            watcherTasks.put(config.getName(), watcherTask);
            
            // Schedule the watcher task
            executorService.scheduleWithFixedDelay(
                watcherTask,
                0,
                config.getPollingInterval(),
                TimeUnit.MILLISECONDS
            );
            
            logger.info("Started watching folder '{}' for configuration '{}'", 
                       config.getWatchFolder(), config.getName());
            
        } catch (Exception e) {
            ErrorHandler.ErrorHandlingResult errorResult = errorHandler.handleError(
                "FileWatcherService.registerWatchConfig", e, "Watch configuration registration");
            
            logger.error("Failed to register watch configuration '{}': {} - Category: {}", 
                        config.getName(), errorResult.getMessage(), errorResult.getCategory(), e);
        }
    }
    
    /**
     * Unregister a watch configuration and stop monitoring.
     * 
     * @param configName the name of the configuration to remove
     */
    public void unregisterWatchConfig(String configName) {
        WatcherTask watcherTask = watcherTasks.remove(configName);
        if (watcherTask != null) {
            watcherTask.stop();
            logger.info("Unregistered watch configuration: {}", configName);
        }
    }
    
    /**
     * Process detected files for a specific configuration.
     * 
     * @param config the watch configuration
     */
    public void processDetectedFiles(WatchConfig config) {
        if (!running) {
            return;
        }
        
        try {
            File watchFolder = config.getWatchFolder().toFile();
            if (!watchFolder.exists() || !watchFolder.isDirectory()) {
                logger.warn("Watch folder does not exist or is not a directory: {}", 
                           config.getWatchFolder());
                return;
            }
            
            File[] files = watchFolder.listFiles();
            if (files == null || files.length == 0) {
                return;
            }
            
            // Sort files alphabetically as per requirement 1.2
            Arrays.sort(files, Comparator.comparing(File::getName));
            
            for (File file : files) {
                if (!running) {
                    break;
                }
                
                if (shouldProcessFile(file, config)) {
                    processFile(file, config);
                }
            }
            
        } catch (Exception e) {
            ErrorHandler.ErrorHandlingResult errorResult = errorHandler.handleError(
                "FileWatcherService.processDetectedFiles", e, "File processing for " + config.getName());
            
            logger.error("Error processing files for configuration '{}': {} - Category: {}", 
                        config.getName(), errorResult.getMessage(), errorResult.getCategory(), e);
        }
    }
    
    /**
     * Check if a file should be processed based on configuration.
     * 
     * @param file the file to check
     * @param config the watch configuration
     * @return true if the file should be processed
     */
    private boolean shouldProcessFile(File file, WatchConfig config) {
        // Skip directories
        if (!file.isFile()) {
            return false;
        }
        
        String filename = file.getName();
        
        // Skip temporary and processing files as per requirement 1.4
        if (filename.endsWith(".tmp") || filename.endsWith(".processing")) {
            logger.debug("Skipping temporary/processing file: {}", filename);
            return false;
        }
        
        // Check file patterns
        if (config.getFilePatterns() != null && !config.getFilePatterns().isEmpty()) {
            boolean matches = config.getFilePatterns().stream()
                .anyMatch(pattern -> matchesPattern(filename, pattern));
            
            if (!matches) {
                logger.debug("File '{}' does not match any configured patterns", filename);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if a filename matches a pattern (supports wildcards).
     * 
     * @param filename the filename to check
     * @param pattern the pattern (supports * wildcards)
     * @return true if the filename matches the pattern
     */
    private boolean matchesPattern(String filename, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern.replace(".", "\\.")
                             .replace("*", ".*")
                             .replace("?", ".");
        
        return Pattern.matches(regex, filename);
    }
    
    /**
     * Process a single file.
     * 
     * @param file the file to process
     * @param config the watch configuration
     */
    private void processFile(File file, WatchConfig config) {
        String correlationId = CorrelationIdUtil.generateAndSet();
        CorrelationIdUtil.setFileName(file.getName());
        CorrelationIdUtil.setProcessorType(config.getProcessorType());
        
        try {
            logger.info("Processing file '{}' with configuration '{}'", 
                       file.getName(), config.getName());
            
            // Check if we're in degraded mode and handle accordingly
            if (gracefulDegradationService.isInDegradedMode("database") && 
                "sql-script".equals(config.getProcessorType())) {
                
                logger.warn("Database is in degraded mode, queuing SQL file for later processing: {}", 
                           file.getName());
                
                if (gracefulDegradationService.handleDatabaseUnavailable(file, config)) {
                    logger.info("Successfully queued file '{}' for later processing", file.getName());
                } else {
                    logger.error("Failed to queue file '{}' for later processing", file.getName());
                }
                return;
            }
            
            ProcessingResult result = fileProcessorRegistry.processFile(file, config);
            
            // Add config name to result metadata for metrics
            if (result.getMetadata() == null) {
                result.setMetadata(new HashMap<>());
            }
            result.getMetadata().put("configName", config.getName());
            
            // Record metrics
            metricsService.recordProcessingResult(result);
            
            if (result.isSuccess()) {
                logger.info("Successfully processed file '{}' in {}ms", 
                           file.getName(), result.getExecutionDurationMs());
                
                // Process any queued files if database is back online
                if ("sql-script".equals(config.getProcessorType()) && 
                    !gracefulDegradationService.isInDegradedMode("database")) {
                    int processedFromQueue = gracefulDegradationService.processQueuedFiles(config);
                    if (processedFromQueue > 0) {
                        logger.info("Processed {} queued files for configuration '{}'", 
                                   processedFromQueue, config.getName());
                    }
                }
            } else {
                logger.error("Failed to process file '{}': {}", 
                            file.getName(), result.getErrorMessage());
            }
            
        } catch (Exception e) {
            ErrorHandler.ErrorHandlingResult errorResult = errorHandler.handleError(
                "FileWatcherService.processFile", e, "File processing for " + file.getName());
            
            logger.error("Unexpected error processing file '{}': {} - Category: {}, Strategy: {}", 
                        file.getName(), errorResult.getMessage(), 
                        errorResult.getCategory(), errorResult.getStrategy(), e);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
    
    /**
     * Ensure required directories exist for a watch configuration.
     * 
     * @param config the watch configuration
     * @throws IOException if directories cannot be created
     */
    private void ensureDirectoriesExist(WatchConfig config) throws IOException {
        // Create watch folder if it doesn't exist (requirement 1.3)
        if (!Files.exists(config.getWatchFolder())) {
            Files.createDirectories(config.getWatchFolder());
            logger.info("Created watch folder: {}", config.getWatchFolder());
        }
        
        // Create completed folder if it doesn't exist
        if (config.getCompletedFolder() != null && !Files.exists(config.getCompletedFolder())) {
            Files.createDirectories(config.getCompletedFolder());
            logger.info("Created completed folder: {}", config.getCompletedFolder());
        }
        
        // Create error folder if it doesn't exist
        if (config.getErrorFolder() != null && !Files.exists(config.getErrorFolder())) {
            Files.createDirectories(config.getErrorFolder());
            logger.info("Created error folder: {}", config.getErrorFolder());
        }
    }
    
    /**
     * Get current status of all watch configurations.
     * 
     * @return map of configuration names to their status
     */
    public Map<String, String> getWatchStatus() {
        Map<String, String> status = new HashMap<>();
        watcherTasks.forEach((name, task) -> {
            status.put(name, task.isRunning() ? "RUNNING" : "STOPPED");
        });
        return status;
    }
    
    /**
     * Check if the service is running.
     * 
     * @return true if the service is running
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Pause watching for a specific configuration.
     * 
     * @param configName the configuration name
     * @return true if successfully paused, false if configuration not found
     */
    public boolean pauseWatching(String configName) {
        WatcherTask task = watcherTasks.get(configName);
        if (task != null) {
            task.pause();
            logger.info("Paused watching for configuration: {}", configName);
            return true;
        }
        logger.warn("Configuration '{}' not found for pausing", configName);
        return false;
    }
    
    /**
     * Resume watching for a specific configuration.
     * 
     * @param configName the configuration name
     * @return true if successfully resumed, false if configuration not found
     */
    public boolean resumeWatching(String configName) {
        WatcherTask task = watcherTasks.get(configName);
        if (task != null) {
            task.resume();
            logger.info("Resumed watching for configuration: {}", configName);
            return true;
        }
        logger.warn("Configuration '{}' not found for resuming", configName);
        return false;
    }
    
    /**
     * Check if watching is enabled for a specific configuration.
     * 
     * @param configName the configuration name
     * @return true if watching is enabled and running, false otherwise
     */
    public boolean isWatchingEnabled(String configName) {
        WatcherTask task = watcherTasks.get(configName);
        return task != null && task.isRunning();
    }
    
    /**
     * Perform health check and update degradation states.
     */
    private void performHealthCheck() {
        try {
            gracefulDegradationService.checkSystemHealth();
        } catch (Exception e) {
            logger.error("Error during health check", e);
        }
    }
    
    /**
     * Inner class representing a watcher task for a specific configuration.
     */
    private class WatcherTask implements Runnable {
        private final WatchConfig config;
        private volatile boolean running = true;
        private volatile boolean paused = false;
        
        public WatcherTask(WatchConfig config) {
            this.config = config;
        }
        
        @Override
        public void run() {
            if (!running || paused || !FileWatcherService.this.running) {
                return;
            }
            
            try {
                processDetectedFiles(config);
            } catch (Exception e) {
                ErrorHandler.ErrorHandlingResult errorResult = errorHandler.handleError(
                    "FileWatcherService.WatcherTask", e, "Watcher task for " + config.getName());
                
                logger.error("Error in watcher task for configuration '{}': {} - Category: {}", 
                            config.getName(), errorResult.getMessage(), errorResult.getCategory(), e);
            }
        }
        
        public void stop() {
            running = false;
        }
        
        public void pause() {
            paused = true;
        }
        
        public void resume() {
            paused = false;
        }
        
        public boolean isRunning() {
            return running && !paused;
        }
    }
}