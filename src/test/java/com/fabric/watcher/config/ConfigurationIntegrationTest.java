package com.fabric.watcher.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for configuration validation and service integration.
 */
class ConfigurationIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testEndToEndConfigurationFlow() {
        // Create properties manually (simulating what Spring Boot would do)
        FileWatcherProperties properties = new FileWatcherProperties();
        
        // Set global config
        FileWatcherProperties.GlobalConfig globalConfig = new FileWatcherProperties.GlobalConfig();
        globalConfig.setMaxRetryAttempts(5);
        globalConfig.setRetryDelay(2000L);
        properties.setGlobal(globalConfig);
        
        // Create watch config
        WatchConfigProperties sqlConfig = new WatchConfigProperties();
        sqlConfig.setName("SQL Processor");
        sqlConfig.setProcessorType("sql-script");
        sqlConfig.setWatchFolder(tempDir.resolve("test-sql").toString());
        sqlConfig.setFilePatterns(List.of("*.sql"));
        sqlConfig.setPollingInterval(3000L);
        sqlConfig.setEnabled(true);
        
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        watchConfigs.put("sql-scripts", sqlConfig);
        properties.setWatchConfigs(watchConfigs);
        
        // Create and initialize configuration service
        ConfigurationService configService = new ConfigurationService(properties);
        configService.validateAndInitialize();
        
        // Verify the complete flow
        assertEquals(1, configService.getConfigurationCount());
        assertEquals(1, configService.getEnabledConfigurationCount());
        
        WatchConfig config = configService.getAllConfigurations().get(0);
        assertEquals("SQL Processor", config.getName());
        assertEquals("sql-script", config.getProcessorType());
        assertEquals(List.of("*.sql"), config.getFilePatterns());
        assertEquals(3000L, config.getPollingInterval());
        assertTrue(config.isEnabled());
        
        // Verify folders were created and defaults applied
        assertNotNull(config.getWatchFolder());
        assertNotNull(config.getCompletedFolder());
        assertNotNull(config.getErrorFolder());
        assertTrue(config.getCompletedFolder().toString().endsWith("completed"));
        assertTrue(config.getErrorFolder().toString().endsWith("error"));
        
        // Verify global config
        assertEquals(5, configService.getGlobalConfig().getMaxRetryAttempts());
        assertEquals(2000L, configService.getGlobalConfig().getRetryDelay());
    }

    @Test
    void testConfigurationWithDefaults() {
        FileWatcherProperties properties = new FileWatcherProperties();
        
        // Create minimal config to test defaults
        WatchConfigProperties sqlConfig = new WatchConfigProperties();
        sqlConfig.setProcessorType("sql-script");
        sqlConfig.setWatchFolder(tempDir.resolve("sql-scripts").toString());
        sqlConfig.setFilePatterns(List.of("*.sql"));
        // Not setting name, completed/error folders, polling interval, enabled - should use defaults
        
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        watchConfigs.put("sql-scripts", sqlConfig);
        properties.setWatchConfigs(watchConfigs);
        
        ConfigurationService configService = new ConfigurationService(properties);
        configService.validateAndInitialize();
        
        assertEquals(1, configService.getConfigurationCount());
        
        WatchConfig config = configService.getAllConfigurations().get(0);
        assertEquals("sql-scripts", config.getName()); // Should use key as name
        assertEquals(5000L, config.getPollingInterval()); // Default
        assertTrue(config.isEnabled()); // Default
        
        // Verify global defaults
        assertEquals(3, configService.getGlobalConfig().getMaxRetryAttempts());
        assertEquals(1000L, configService.getGlobalConfig().getRetryDelay());
    }

    @Test
    void testMultipleConfigurationsIntegration() {
        FileWatcherProperties properties = new FileWatcherProperties();
        
        // SQL Scripts config
        WatchConfigProperties sqlConfig = new WatchConfigProperties();
        sqlConfig.setName("SQL Scripts");
        sqlConfig.setProcessorType("sql-script");
        sqlConfig.setWatchFolder(tempDir.resolve("sql-scripts").toString());
        sqlConfig.setFilePatterns(List.of("*.sql"));
        sqlConfig.setEnabled(true);
        
        // SQL Loader config (disabled)
        WatchConfigProperties logConfig = new WatchConfigProperties();
        logConfig.setName("SQL Loader Logs");
        logConfig.setProcessorType("sqlloader-log");
        logConfig.setWatchFolder(tempDir.resolve("sqlloader-logs").toString());
        logConfig.setFilePatterns(List.of("*.log", "*.ctl"));
        logConfig.setEnabled(false);
        
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        watchConfigs.put("sql-scripts", sqlConfig);
        watchConfigs.put("sqlloader-logs", logConfig);
        properties.setWatchConfigs(watchConfigs);
        
        ConfigurationService configService = new ConfigurationService(properties);
        configService.validateAndInitialize();
        
        assertEquals(2, configService.getConfigurationCount());
        assertEquals(1, configService.getEnabledConfigurationCount());
        
        List<WatchConfig> enabledConfigs = configService.getEnabledConfigurations();
        assertEquals(1, enabledConfigs.size());
        assertEquals("SQL Scripts", enabledConfigs.get(0).getName());
        
        WatchConfig sqlConfigResult = configService.getConfiguration("SQL Scripts");
        assertNotNull(sqlConfigResult);
        assertTrue(sqlConfigResult.isEnabled());
        
        WatchConfig logConfigResult = configService.getConfiguration("SQL Loader Logs");
        assertNotNull(logConfigResult);
        assertFalse(logConfigResult.isEnabled());
    }
}