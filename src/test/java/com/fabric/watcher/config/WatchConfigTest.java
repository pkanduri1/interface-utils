package com.fabric.watcher.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WatchConfigTest {

    @Test
    void testWatchConfigCreation() {
        WatchConfig config = new WatchConfig(
            "SQL Scripts",
            "sql-script",
            Paths.get("./sql-scripts"),
            Paths.get("./sql-scripts/completed"),
            Paths.get("./sql-scripts/error"),
            Arrays.asList("*.sql"),
            5000L,
            true
        );
        
        assertEquals("SQL Scripts", config.getName());
        assertEquals("sql-script", config.getProcessorType());
        assertEquals(Paths.get("./sql-scripts"), config.getWatchFolder());
        assertEquals(Arrays.asList("*.sql"), config.getFilePatterns());
        assertEquals(5000L, config.getPollingInterval());
        assertTrue(config.isEnabled());
    }

    @Test
    void testWatchConfigDefaults() {
        WatchConfig config = new WatchConfig();
        
        assertNull(config.getName());
        assertNull(config.getProcessorType());
        assertNull(config.getWatchFolder());
        assertEquals(0L, config.getPollingInterval());
        assertFalse(config.isEnabled());
    }

    @Test
    void testWatchConfigSettersAndGetters() {
        WatchConfig config = new WatchConfig();
        
        config.setName("Test Config");
        config.setProcessorType("test-processor");
        config.setWatchFolder(Paths.get("/test/watch"));
        config.setCompletedFolder(Paths.get("/test/completed"));
        config.setErrorFolder(Paths.get("/test/error"));
        config.setFilePatterns(List.of("*.txt", "*.log"));
        config.setPollingInterval(3000L);
        config.setEnabled(true);
        
        Map<String, Object> processorConfig = new HashMap<>();
        processorConfig.put("key1", "value1");
        config.setProcessorSpecificConfig(processorConfig);
        
        assertEquals("Test Config", config.getName());
        assertEquals("test-processor", config.getProcessorType());
        assertEquals(Paths.get("/test/watch"), config.getWatchFolder());
        assertEquals(Paths.get("/test/completed"), config.getCompletedFolder());
        assertEquals(Paths.get("/test/error"), config.getErrorFolder());
        assertEquals(List.of("*.txt", "*.log"), config.getFilePatterns());
        assertEquals(3000L, config.getPollingInterval());
        assertTrue(config.isEnabled());
        assertEquals("value1", config.getProcessorSpecificConfig().get("key1"));
    }

    @Test
    void testWatchConfigToString() {
        WatchConfig config = new WatchConfig();
        config.setName("Test Config");
        config.setProcessorType("test-processor");
        config.setWatchFolder(Paths.get("/test/watch"));
        config.setEnabled(true);
        
        String toString = config.toString();
        assertTrue(toString.contains("Test Config"));
        assertTrue(toString.contains("test-processor"));
        assertTrue(toString.contains("/test/watch"));
        assertTrue(toString.contains("enabled=true"));
    }
}