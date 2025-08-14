package com.fabric.watcher.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileWatcherPropertiesTest {

    @Test
    void testFileWatcherPropertiesDefaults() {
        FileWatcherProperties properties = new FileWatcherProperties();
        
        assertNotNull(properties.getGlobal());
        assertEquals(3, properties.getGlobal().getMaxRetryAttempts());
        assertEquals(1000L, properties.getGlobal().getRetryDelay());
        
        assertNotNull(properties.getWatchConfigs());
        assertTrue(properties.getWatchConfigs().isEmpty());
    }

    @Test
    void testGlobalConfigDefaults() {
        FileWatcherProperties.GlobalConfig globalConfig = new FileWatcherProperties.GlobalConfig();
        
        assertEquals(3, globalConfig.getMaxRetryAttempts());
        assertEquals(1000L, globalConfig.getRetryDelay());
    }

    @Test
    void testGlobalConfigSettersAndGetters() {
        FileWatcherProperties.GlobalConfig globalConfig = new FileWatcherProperties.GlobalConfig();
        
        globalConfig.setMaxRetryAttempts(5);
        globalConfig.setRetryDelay(2000L);
        
        assertEquals(5, globalConfig.getMaxRetryAttempts());
        assertEquals(2000L, globalConfig.getRetryDelay());
    }

    @Test
    void testFileWatcherPropertiesSettersAndGetters() {
        FileWatcherProperties properties = new FileWatcherProperties();
        
        FileWatcherProperties.GlobalConfig globalConfig = new FileWatcherProperties.GlobalConfig();
        globalConfig.setMaxRetryAttempts(5);
        globalConfig.setRetryDelay(2000L);
        properties.setGlobal(globalConfig);
        
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties sqlConfig = new WatchConfigProperties();
        sqlConfig.setName("SQL Config");
        sqlConfig.setProcessorType("sql-script");
        sqlConfig.setWatchFolder("./sql");
        sqlConfig.setFilePatterns(List.of("*.sql"));
        watchConfigs.put("sql-scripts", sqlConfig);
        
        properties.setWatchConfigs(watchConfigs);
        
        assertEquals(5, properties.getGlobal().getMaxRetryAttempts());
        assertEquals(2000L, properties.getGlobal().getRetryDelay());
        assertEquals(1, properties.getWatchConfigs().size());
        assertTrue(properties.getWatchConfigs().containsKey("sql-scripts"));
        assertEquals("SQL Config", properties.getWatchConfigs().get("sql-scripts").getName());
    }

    @Test
    void testMultipleWatchConfigs() {
        FileWatcherProperties properties = new FileWatcherProperties();
        
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        
        // SQL Scripts config
        WatchConfigProperties sqlConfig = new WatchConfigProperties();
        sqlConfig.setName("SQL Scripts");
        sqlConfig.setProcessorType("sql-script");
        sqlConfig.setWatchFolder("./sql-scripts");
        sqlConfig.setFilePatterns(List.of("*.sql"));
        sqlConfig.setPollingInterval(5000L);
        sqlConfig.setEnabled(true);
        watchConfigs.put("sql-scripts", sqlConfig);
        
        // SQL Loader logs config
        WatchConfigProperties logConfig = new WatchConfigProperties();
        logConfig.setName("SQL Loader Logs");
        logConfig.setProcessorType("sqlloader-log");
        logConfig.setWatchFolder("./sqlloader-logs");
        logConfig.setFilePatterns(List.of("*.log", "*.ctl"));
        logConfig.setPollingInterval(10000L);
        logConfig.setEnabled(false);
        watchConfigs.put("sqlloader-logs", logConfig);
        
        properties.setWatchConfigs(watchConfigs);
        
        assertEquals(2, properties.getWatchConfigs().size());
        
        WatchConfigProperties retrievedSqlConfig = properties.getWatchConfigs().get("sql-scripts");
        assertEquals("SQL Scripts", retrievedSqlConfig.getName());
        assertEquals("sql-script", retrievedSqlConfig.getProcessorType());
        assertTrue(retrievedSqlConfig.isEnabled());
        
        WatchConfigProperties retrievedLogConfig = properties.getWatchConfigs().get("sqlloader-logs");
        assertEquals("SQL Loader Logs", retrievedLogConfig.getName());
        assertEquals("sqlloader-log", retrievedLogConfig.getProcessorType());
        assertFalse(retrievedLogConfig.isEnabled());
    }
}