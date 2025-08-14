package com.fabric.watcher.service;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingResult.ExecutionStatus;
import com.fabric.watcher.processor.FileProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of FileProcessorRegistry that manages file processors
 * and routes files to appropriate processors based on configuration.
 * 
 * Features:
 * - Auto-discovery of FileProcessor beans from Spring context
 * - Dynamic processor registration and lifecycle management
 * - Enhanced fallback handling for unsupported file types
 * - Processor health monitoring and validation
 */
@Service
public class FileProcessorRegistryImpl implements FileProcessorRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(FileProcessorRegistryImpl.class);
    
    private final Map<String, FileProcessor> processors = new ConcurrentHashMap<>();
    private final Map<String, ProcessorMetadata> processorMetadata = new ConcurrentHashMap<>();
    
    @Autowired
    ApplicationContext applicationContext;
    
    /**
     * Auto-discover and register all FileProcessor beans from Spring context.
     */
    @PostConstruct
    public void initializeProcessors() {
        logger.info("Initializing file processor registry...");
        
        // Auto-discover FileProcessor beans from Spring context
        Map<String, FileProcessor> processorBeans = applicationContext.getBeansOfType(FileProcessor.class);
        
        for (Map.Entry<String, FileProcessor> entry : processorBeans.entrySet()) {
            FileProcessor processor = entry.getValue();
            String processorType = processor.getProcessorType();
            
            if (processorType != null && !processorType.trim().isEmpty()) {
                registerProcessor(processorType, processor);
                
                // Store metadata for lifecycle management
                ProcessorMetadata metadata = new ProcessorMetadata(
                    entry.getKey(), // Spring bean name
                    processor.getClass().getSimpleName(),
                    LocalDateTime.now(),
                    true // healthy by default
                );
                processorMetadata.put(processorType, metadata);
            } else {
                logger.warn("FileProcessor bean '{}' has null or empty processor type, skipping registration", 
                           entry.getKey());
            }
        }
        
        logger.info("Initialized file processor registry with {} processors: {}", 
                   processors.size(), processors.keySet());
    }
    
    /**
     * Cleanup processors on shutdown.
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down file processor registry...");
        
        // Perform any necessary cleanup for processors
        for (Map.Entry<String, FileProcessor> entry : processors.entrySet()) {
            try {
                // If processors implement any cleanup interface, call it here
                logger.debug("Cleaning up processor: {}", entry.getKey());
            } catch (Exception e) {
                logger.warn("Error cleaning up processor '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        
        processors.clear();
        processorMetadata.clear();
        logger.info("File processor registry shutdown complete");
    }
    
    @Override
    public void registerProcessor(String type, FileProcessor processor) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Processor type cannot be null or empty");
        }
        if (processor == null) {
            throw new IllegalArgumentException("Processor cannot be null");
        }
        
        // Check if processor type is already registered
        if (processors.containsKey(type)) {
            logger.warn("Processor type '{}' is already registered, replacing with new implementation", type);
        }
        
        processors.put(type, processor);
        
        // Update or create metadata
        ProcessorMetadata metadata = processorMetadata.getOrDefault(type, 
            new ProcessorMetadata("manual", processor.getClass().getSimpleName(), LocalDateTime.now(), true));
        metadata.setLastRegistered(LocalDateTime.now());
        metadata.setHealthy(true);
        processorMetadata.put(type, metadata);
        
        logger.info("Registered file processor: {} -> {}", type, processor.getClass().getSimpleName());
    }
    
    @Override
    public FileProcessor getProcessor(String type) {
        return processors.get(type);
    }
    
    @Override
    public ProcessingResult processFile(File file, WatchConfig config) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("WatchConfig cannot be null");
        }
        
        String processorType = config.getProcessorType();
        if (processorType == null || processorType.trim().isEmpty()) {
            String errorMsg = String.format("No processor type specified in configuration '%s'. " +
                "Available processors: %s", config.getName(), getAvailableProcessorTypes());
            logger.error(errorMsg);
            return createFailureResult(file.getName(), "unknown", errorMsg);
        }
        
        FileProcessor processor = getProcessor(processorType);
        if (processor == null) {
            String errorMsg = String.format("No processor found for type '%s'. " +
                "Available processors: %s. File will be moved to error folder.", 
                processorType, getAvailableProcessorTypes());
            logger.error(errorMsg);
            return createFailureResult(file.getName(), processorType, errorMsg);
        }
        
        // Check processor health before using it
        ProcessorMetadata metadata = processorMetadata.get(processorType);
        if (metadata != null && !metadata.isHealthy()) {
            String errorMsg = String.format("Processor '%s' is marked as unhealthy. " +
                "Last error: %s", processorType, metadata.getLastError());
            logger.error(errorMsg);
            return createFailureResult(file.getName(), processorType, errorMsg);
        }
        
        if (!processor.supports(config)) {
            String errorMsg = String.format("Processor '%s' does not support configuration '%s'. " +
                "This may indicate a configuration mismatch.", processorType, config.getName());
            logger.error(errorMsg);
            return createFailureResult(file.getName(), processorType, errorMsg);
        }
        
        try {
            logger.debug("Processing file '{}' with processor '{}'", file.getName(), processorType);
            long startTime = System.currentTimeMillis();
            
            ProcessingResult result = processor.processFile(file, config);
            
            long endTime = System.currentTimeMillis();
            result.setExecutionDurationMs(endTime - startTime);
            result.setExecutionTime(LocalDateTime.now());
            
            // Update processor health based on result
            if (metadata != null) {
                if (result.isSuccess()) {
                    metadata.setHealthy(true);
                    metadata.setLastError(null);
                    metadata.incrementSuccessCount();
                } else {
                    metadata.incrementFailureCount();
                    metadata.setLastError(result.getErrorMessage());
                    
                    // Mark as unhealthy if too many consecutive failures
                    if (metadata.getConsecutiveFailures() > 5) {
                        metadata.setHealthy(false);
                        logger.warn("Marking processor '{}' as unhealthy due to {} consecutive failures", 
                                   processorType, metadata.getConsecutiveFailures());
                    }
                }
            }
            
            if (result.isSuccess()) {
                logger.debug("Successfully processed file '{}' in {}ms", 
                           file.getName(), result.getExecutionDurationMs());
            } else {
                logger.warn("Failed to process file '{}': {}", 
                           file.getName(), result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            String errorMsg = "Unexpected error processing file: " + e.getMessage();
            logger.error("Error processing file '{}' with processor '{}': {}", 
                        file.getName(), processorType, e.getMessage(), e);
            
            // Update processor health on exception
            if (metadata != null) {
                metadata.incrementFailureCount();
                metadata.setLastError(errorMsg);
                if (metadata.getConsecutiveFailures() > 5) {
                    metadata.setHealthy(false);
                }
            }
            
            return createFailureResult(file.getName(), processorType, errorMsg);
        }
    }
    
    /**
     * Get all registered processor types.
     * 
     * @return map of processor types to their implementations
     */
    public Map<String, FileProcessor> getAllProcessors() {
        return new ConcurrentHashMap<>(processors);
    }
    
    /**
     * Check if a processor is registered for the given type.
     * 
     * @param type the processor type
     * @return true if a processor is registered for this type
     */
    public boolean hasProcessor(String type) {
        return processors.containsKey(type);
    }
    
    /**
     * Unregister a processor.
     * 
     * @param type the processor type to unregister
     * @return the unregistered processor, or null if none was registered
     */
    public FileProcessor unregisterProcessor(String type) {
        FileProcessor removed = processors.remove(type);
        ProcessorMetadata metadata = processorMetadata.remove(type);
        
        if (removed != null) {
            logger.info("Unregistered file processor: {}", type);
        }
        return removed;
    }
    
    /**
     * Get available processor types as a comma-separated string.
     * 
     * @return string of available processor types
     */
    public String getAvailableProcessorTypes() {
        Set<String> types = processors.keySet();
        return types.isEmpty() ? "none" : String.join(", ", types);
    }
    
    /**
     * Get processor metadata for monitoring and health checks.
     * 
     * @return map of processor types to their metadata
     */
    public Map<String, ProcessorMetadata> getProcessorMetadata() {
        return new ConcurrentHashMap<>(processorMetadata);
    }
    
    /**
     * Check if a processor is healthy and available for processing.
     * 
     * @param type the processor type
     * @return true if processor exists and is healthy
     */
    public boolean isProcessorHealthy(String type) {
        ProcessorMetadata metadata = processorMetadata.get(type);
        return metadata != null && metadata.isHealthy() && processors.containsKey(type);
    }
    
    /**
     * Reset processor health status (useful for recovery scenarios).
     * 
     * @param type the processor type
     * @return true if processor was found and reset
     */
    public boolean resetProcessorHealth(String type) {
        ProcessorMetadata metadata = processorMetadata.get(type);
        if (metadata != null) {
            metadata.setHealthy(true);
            metadata.setLastError(null);
            metadata.resetFailureCount();
            logger.info("Reset health status for processor: {}", type);
            return true;
        }
        return false;
    }
    
    /**
     * Get processors that support a specific configuration.
     * 
     * @param config the watch configuration
     * @return list of processor types that support this configuration
     */
    public List<String> getSupportingProcessors(WatchConfig config) {
        return processors.entrySet().stream()
            .filter(entry -> entry.getValue().supports(config))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Create a standardized failure result.
     */
    private ProcessingResult createFailureResult(String filename, String processorType, String errorMessage) {
        ProcessingResult result = new ProcessingResult(filename, processorType, ExecutionStatus.FAILURE, errorMessage);
        result.setExecutionTime(LocalDateTime.now());
        result.setExecutionDurationMs(0);
        return result;
    }
    
    /**
     * Metadata for tracking processor lifecycle and health.
     */
    public static class ProcessorMetadata {
        private final String beanName;
        private final String className;
        private LocalDateTime lastRegistered;
        private boolean healthy;
        private String lastError;
        private long successCount;
        private long failureCount;
        private long consecutiveFailures;
        
        public ProcessorMetadata(String beanName, String className, LocalDateTime lastRegistered, boolean healthy) {
            this.beanName = beanName;
            this.className = className;
            this.lastRegistered = lastRegistered;
            this.healthy = healthy;
            this.successCount = 0;
            this.failureCount = 0;
            this.consecutiveFailures = 0;
        }
        
        // Getters and setters
        public String getBeanName() { return beanName; }
        public String getClassName() { return className; }
        public LocalDateTime getLastRegistered() { return lastRegistered; }
        public void setLastRegistered(LocalDateTime lastRegistered) { this.lastRegistered = lastRegistered; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
        public long getSuccessCount() { return successCount; }
        public long getFailureCount() { return failureCount; }
        public long getConsecutiveFailures() { return consecutiveFailures; }
        
        public void incrementSuccessCount() {
            this.successCount++;
            this.consecutiveFailures = 0; // Reset consecutive failures on success
        }
        
        public void incrementFailureCount() {
            this.failureCount++;
            this.consecutiveFailures++;
        }
        
        public void resetFailureCount() {
            this.consecutiveFailures = 0;
        }
        
        @Override
        public String toString() {
            return String.format("ProcessorMetadata{beanName='%s', className='%s', healthy=%s, " +
                "successCount=%d, failureCount=%d, consecutiveFailures=%d}", 
                beanName, className, healthy, successCount, failureCount, consecutiveFailures);
        }
    }
}