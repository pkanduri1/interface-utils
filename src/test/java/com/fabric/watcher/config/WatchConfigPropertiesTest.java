package com.fabric.watcher.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WatchConfigPropertiesTest {

    @Test
    void testWatchConfigPropertiesDefaults() {
        WatchConfigProperties props = new WatchConfigProperties();
        
        assertNull(props.getName());
        assertNull(props.getProcessorType());
        assertNull(props.getWatchFolder());
        assertNull(props.getCompletedFolder());
        assertNull(props.getErrorFolder());
        assertTrue(props.getFilePatterns().isEmpty());
        assertEquals(5000L, props.getPollingInterval());
        assertTrue(props.isEnabled());
        assertNotNull(props.getProcessorSpecificConfig());
        assertTrue(props.getProcessorSpecificConfig().isEmpty());
    }

    @Test
    void testWatchConfigPropertiesSettersAndGetters() {
        WatchConfigProperties props = new WatchConfigProperties();
        
        props.setName("Test Config");
        props.setProcessorType("sql-script");
        props.setWatchFolder("./test-watch");
        props.setCompletedFolder("./test-completed");
        props.setErrorFolder("./test-error");
        props.setFilePatterns(List.of("*.sql", "*.txt"));
        props.setPollingInterval(3000L);
        props.setEnabled(false);
        
        Map<String, Object> processorConfig = new HashMap<>();
        processorConfig.put("timeout", 30000);
        props.setProcessorSpecificConfig(processorConfig);
        
        assertEquals("Test Config", props.getName());
        assertEquals("sql-script", props.getProcessorType());
        assertEquals("./test-watch", props.getWatchFolder());
        assertEquals("./test-completed", props.getCompletedFolder());
        assertEquals("./test-error", props.getErrorFolder());
        assertEquals(List.of("*.sql", "*.txt"), props.getFilePatterns());
        assertEquals(3000L, props.getPollingInterval());
        assertFalse(props.isEnabled());
        assertEquals(30000, props.getProcessorSpecificConfig().get("timeout"));
    }

    @Test
    void testToWatchConfigWithAllProperties() {
        WatchConfigProperties props = new WatchConfigProperties();
        props.setName("SQL Processor");
        props.setProcessorType("sql-script");
        props.setWatchFolder("./sql-watch");
        props.setCompletedFolder("./sql-completed");
        props.setErrorFolder("./sql-error");
        props.setFilePatterns(List.of("*.sql"));
        props.setPollingInterval(2000L);
        props.setEnabled(true);
        
        Map<String, Object> processorConfig = new HashMap<>();
        processorConfig.put("batchSize", 100);
        props.setProcessorSpecificConfig(processorConfig);
        
        WatchConfig config = props.toWatchConfig();
        
        assertEquals("SQL Processor", config.getName());
        assertEquals("sql-script", config.getProcessorType());
        assertEquals("./sql-watch", config.getWatchFolder().toString());
        assertEquals("./sql-completed", config.getCompletedFolder().toString());
        assertEquals("./sql-error", config.getErrorFolder().toString());
        assertEquals(List.of("*.sql"), config.getFilePatterns());
        assertEquals(2000L, config.getPollingInterval());
        assertTrue(config.isEnabled());
        assertEquals(100, config.getProcessorSpecificConfig().get("batchSize"));
    }

    @Test
    void testToWatchConfigWithDefaultFolders() {
        WatchConfigProperties props = new WatchConfigProperties();
        props.setName("Test Config");
        props.setProcessorType("test-processor");
        props.setWatchFolder("./test-watch");
        // Not setting completed and error folders - should use defaults
        props.setFilePatterns(List.of("*.txt"));
        
        WatchConfig config = props.toWatchConfig();
        
        assertEquals("./test-watch", config.getWatchFolder().toString());
        assertEquals("./test-watch/completed", config.getCompletedFolder().toString());
        assertEquals("./test-watch/error", config.getErrorFolder().toString());
    }

    @Test
    void testToWatchConfigWithEmptyFolders() {
        WatchConfigProperties props = new WatchConfigProperties();
        props.setName("Test Config");
        props.setProcessorType("test-processor");
        props.setWatchFolder("./test-watch");
        props.setCompletedFolder(""); // Empty string should use default
        props.setErrorFolder("   "); // Whitespace should use default
        props.setFilePatterns(List.of("*.txt"));
        
        WatchConfig config = props.toWatchConfig();
        
        assertEquals("./test-watch/completed", config.getCompletedFolder().toString());
        assertEquals("./test-watch/error", config.getErrorFolder().toString());
    }

    @Test
    void testToWatchConfigCreatesNewCollections() {
        WatchConfigProperties props = new WatchConfigProperties();
        props.setName("Test Config");
        props.setProcessorType("test-processor");
        props.setWatchFolder("./test-watch");
        
        // Use mutable list for testing
        List<String> filePatterns = new ArrayList<>();
        filePatterns.add("*.txt");
        props.setFilePatterns(filePatterns);
        
        Map<String, Object> originalConfig = new HashMap<>();
        originalConfig.put("key", "value");
        props.setProcessorSpecificConfig(originalConfig);
        
        WatchConfig config = props.toWatchConfig();
        
        // Modify original collections
        filePatterns.add("*.log");
        originalConfig.put("key2", "value2");
        
        // Config should not be affected
        assertEquals(1, config.getFilePatterns().size());
        assertEquals(1, config.getProcessorSpecificConfig().size());
    }

    @Test
    void testToString() {
        WatchConfigProperties props = new WatchConfigProperties();
        props.setName("Test Config");
        props.setProcessorType("test-processor");
        props.setWatchFolder("./test-watch");
        props.setEnabled(true);
        
        String toString = props.toString();
        assertTrue(toString.contains("Test Config"));
        assertTrue(toString.contains("test-processor"));
        assertTrue(toString.contains("./test-watch"));
        assertTrue(toString.contains("enabled=true"));
    }
}