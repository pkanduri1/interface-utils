package com.fabric.watcher.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility class for managing correlation IDs in logging context.
 */
public class CorrelationIdUtil {
    
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String FILE_NAME_KEY = "fileName";
    private static final String PROCESSOR_TYPE_KEY = "processorType";
    
    private CorrelationIdUtil() {
        // Utility class
    }
    
    /**
     * Generate and set a new correlation ID in the MDC.
     * @return the generated correlation ID
     */
    public static String generateAndSet() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        return correlationId;
    }
    
    /**
     * Set a specific correlation ID in the MDC.
     * @param correlationId the correlation ID to set
     */
    public static void set(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }
    
    /**
     * Get the current correlation ID from the MDC.
     * @return the current correlation ID, or null if not set
     */
    public static String get() {
        return MDC.get(CORRELATION_ID_KEY);
    }
    
    /**
     * Set the file name in the MDC for logging context.
     * @param fileName the file name being processed
     */
    public static void setFileName(String fileName) {
        MDC.put(FILE_NAME_KEY, fileName);
    }
    
    /**
     * Set the processor type in the MDC for logging context.
     * @param processorType the processor type being used
     */
    public static void setProcessorType(String processorType) {
        MDC.put(PROCESSOR_TYPE_KEY, processorType);
    }
    
    /**
     * Clear all MDC values.
     */
    public static void clear() {
        MDC.clear();
    }
    
    /**
     * Clear only the correlation ID from MDC.
     */
    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}