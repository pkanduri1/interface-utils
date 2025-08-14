package com.fabric.watcher.service;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.processor.FileProcessor;

import java.io.File;

/**
 * Registry for managing file processors and routing files to appropriate processors.
 */
public interface FileProcessorRegistry {
    
    /**
     * Register a file processor.
     * 
     * @param type the processor type identifier
     * @param processor the processor implementation
     */
    void registerProcessor(String type, FileProcessor processor);
    
    /**
     * Get a processor for the given type.
     * 
     * @param type the processor type identifier
     * @return the processor, or null if not found
     */
    FileProcessor getProcessor(String type);
    
    /**
     * Process a file using the appropriate processor based on configuration.
     * 
     * @param file the file to process
     * @param config the watch configuration
     * @return result of processing
     */
    ProcessingResult processFile(File file, WatchConfig config);
}