package com.fabric.watcher.processor;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;

import java.io.File;

/**
 * Interface for processing different types of files.
 * Implementations handle specific file types (SQL scripts, logs, etc.)
 */
public interface FileProcessor {
    
    /**
     * Check if this processor supports the given watch configuration.
     * 
     * @param config the watch configuration
     * @return true if this processor can handle files for this configuration
     */
    boolean supports(WatchConfig config);
    
    /**
     * Process a file according to the watch configuration.
     * 
     * @param file the file to process
     * @param config the watch configuration
     * @return result of processing including status and any error details
     */
    ProcessingResult processFile(File file, WatchConfig config);
    
    /**
     * Get the processor type identifier.
     * 
     * @return unique identifier for this processor type
     */
    String getProcessorType();
}