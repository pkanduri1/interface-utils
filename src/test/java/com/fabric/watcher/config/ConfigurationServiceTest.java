package com.fabric.watcher.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationServiceTest {

    @TempDir
    Path tempDir;

    private FileWatcherProperties properties;
    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        properties = new FileWatcherProperties();
        configurationService = new ConfigurationService(properties);
    }

    @Test
    void testEmptyConfiguration() {
        // No watch configs defined
        configurationService.validateAndInitialize();
        
        assertEquals(0, configurationService.getConfigurationCount());
        assertEquals(0, configurationService.getEnabledConfigurationCount());
        assertTrue(configurationService.getAllConfigurations().isEmpty());
        assertTrue(configurationService.getEnabledConfigurations().isEmpty());
    }

    @Test
    void testValidSingleConfiguration() {
        // Create a valid configuration
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties sqlConfig = createValidSqlConfig();
        watchConfigs.put("sql-scripts", sqlConfig);
        properties.setWatchConfigs(watchConfigs);
        
        configurationService.validateAndInitialize();
        
        assertEquals(1, configurationService.getConfigurationCount());
        assertEquals(1, configurationService.getEnabledConfigurationCount());
        
        List<WatchConfig> allConfigs = configurationService.getAllConfigurations();
        assertEquals(1, allConfigs.size());
        
        WatchConfig config = allConfigs.get(0);
        assertEquals("SQL Scripts", config.getName());
        assertEquals("sql-script", config.getProcessorType());
        assertTrue(config.isEnabled());
    }

    @Test
    void testMultipleConfigurations() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        
        // Enabled SQL config
        WatchConfigProperties sqlConfig = createValidSqlConfig();
        watchConfigs.put("sql-scripts", sqlConfig);
        
        // Disabled log config
        WatchConfigProperties logConfig = createValidLogConfig();
        logConfig.setEnabled(false);
        watchConfigs.put("sqlloader-logs", logConfig);
        
        properties.setWatchConfigs(watchConfigs);
        configurationService.validateAndInitialize();
        
        assertEquals(2, configurationService.getConfigurationCount());
        assertEquals(1, configurationService.getEnabledConfigurationCount());
        
        List<WatchConfig> enabledConfigs = configurationService.getEnabledConfigurations();
        assertEquals(1, enabledConfigs.size());
        assertEquals("SQL Scripts", enabledConfigs.get(0).getName());
    }

    @Test
    void testConfigurationByName() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties sqlConfig = createValidSqlConfig();
        watchConfigs.put("sql-scripts", sqlConfig);
        properties.setWatchConfigs(watchConfigs);
        
        configurationService.validateAndInitialize();
        
        WatchConfig foundConfig = configurationService.getConfiguration("SQL Scripts");
        assertNotNull(foundConfig);
        assertEquals("SQL Scripts", foundConfig.getName());
        
        WatchConfig notFoundConfig = configurationService.getConfiguration("Non-existent");
        assertNull(notFoundConfig);
    }

    @Test
    void testDefaultFolderCreation() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties sqlConfig = createValidSqlConfig();
        // Use temp directory for testing
        sqlConfig.setWatchFolder(tempDir.resolve("sql-scripts").toString());
        watchConfigs.put("sql-scripts", sqlConfig);
        properties.setWatchConfigs(watchConfigs);
        
        configurationService.validateAndInitialize();
        
        assertEquals(1, configurationService.getConfigurationCount());
        
        WatchConfig config = configurationService.getAllConfigurations().get(0);
        assertTrue(Files.exists(config.getWatchFolder()));
        assertTrue(Files.exists(config.getCompletedFolder()));
        assertTrue(Files.exists(config.getErrorFolder()));
    }

    @Test
    void testConfigurationValidation_MissingProcessorType() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties invalidConfig = new WatchConfigProperties();
        invalidConfig.setName("Invalid Config");
        // Missing processor type
        invalidConfig.setWatchFolder(tempDir.resolve("test").toString());
        invalidConfig.setFilePatterns(List.of("*.txt"));
        watchConfigs.put("invalid", invalidConfig);
        properties.setWatchConfigs(watchConfigs);
        
        configurationService.validateAndInitialize();
        
        // Should have 0 valid configurations due to validation failure
        assertEquals(0, configurationService.getConfigurationCount());
    }

    @Test
    void testConfigurationValidation_MissingWatchFolder() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties invalidConfig = new WatchConfigProperties();
        invalidConfig.setName("Invalid Config");
        invalidConfig.setProcessorType("test-processor");
        // Missing watch folder
        invalidConfig.setFilePatterns(List.of("*.txt"));
        watchConfigs.put("invalid", invalidConfig);
        properties.setWatchConfigs(watchConfigs);
        
        configurationService.validateAndInitialize();
        
        assertEquals(0, configurationService.getConfigurationCount());
    }

    @Test
    void testConfigurationValidation_DefaultFilePatterns() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties config = new WatchConfigProperties();
        config.setName("Test Config");
        config.setProcessorType("test-processor");
        config.setWatchFolder(tempDir.resolve("test").toString());
        // No file patterns - should default to ["*"]
        watchConfigs.put("test", config);
        properties.setWatchConfigs(watchConfigs);
        
        configurationService.validateAndInitialize();
        
        assertEquals(1, configurationService.getConfigurationCount());
        WatchConfig validatedConfig = configurationService.getAllConfigurations().get(0);
        assertEquals(List.of("*"), validatedConfig.getFilePatterns());
    }

    @Test
    void testConfigurationValidation_MinimumPollingInterval() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties config = createValidSqlConfig();
        config.setPollingInterval(500L); // Below minimum
        watchConfigs.put("sql-scripts", config);
        properties.setWatchConfigs(watchConfigs);
        
        configurationService.validateAndInitialize();
        
        assertEquals(1, configurationService.getConfigurationCount());
        WatchConfig validatedConfig = configurationService.getAllConfigurations().get(0);
        assertEquals(1000L, validatedConfig.getPollingInterval()); // Should be corrected to minimum
    }

    @Test
    void testConfigurationValidation_NameFromKey() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        WatchConfigProperties config = createValidSqlConfig();
        config.setName(null); // No name set
        watchConfigs.put("sql-scripts", config);
        properties.setWatchConfigs(watchConfigs);
        
        configurationService.validateAndInitialize();
        
        assertEquals(1, configurationService.getConfigurationCount());
        WatchConfig validatedConfig = configurationService.getAllConfigurations().get(0);
        assertEquals("sql-scripts", validatedConfig.getName()); // Should use key as name
    }

    @Test
    void testGlobalConfiguration() {
        FileWatcherProperties.GlobalConfig globalConfig = new FileWatcherProperties.GlobalConfig();
        globalConfig.setMaxRetryAttempts(5);
        globalConfig.setRetryDelay(2000L);
        properties.setGlobal(globalConfig);
        
        FileWatcherProperties.GlobalConfig retrievedGlobal = configurationService.getGlobalConfig();
        assertEquals(5, retrievedGlobal.getMaxRetryAttempts());
        assertEquals(2000L, retrievedGlobal.getRetryDelay());
    }

    @Test
    void testPartialValidationFailure() {
        Map<String, WatchConfigProperties> watchConfigs = new HashMap<>();
        
        // Valid config
        WatchConfigProperties validConfig = createValidSqlConfig();
        watchConfigs.put("valid", validConfig);
        
        // Invalid config
        WatchConfigProperties invalidConfig = new WatchConfigProperties();
        invalidConfig.setName("Invalid");
        // Missing required fields
        watchConfigs.put("invalid", invalidConfig);
        
        properties.setWatchConfigs(watchConfigs);
        configurationService.validateAndInitialize();
        
        // Should have 1 valid configuration despite 1 invalid
        assertEquals(1, configurationService.getConfigurationCount());
        assertEquals("SQL Scripts", configurationService.getAllConfigurations().get(0).getName());
    }

    private WatchConfigProperties createValidSqlConfig() {
        WatchConfigProperties config = new WatchConfigProperties();
        config.setName("SQL Scripts");
        config.setProcessorType("sql-script");
        config.setWatchFolder(tempDir.resolve("sql-scripts").toString());
        config.setFilePatterns(List.of("*.sql"));
        config.setPollingInterval(5000L);
        config.setEnabled(true);
        return config;
    }

    private WatchConfigProperties createValidLogConfig() {
        WatchConfigProperties config = new WatchConfigProperties();
        config.setName("SQL Loader Logs");
        config.setProcessorType("sqlloader-log");
        config.setWatchFolder(tempDir.resolve("sqlloader-logs").toString());
        config.setFilePatterns(List.of("*.log", "*.ctl"));
        config.setPollingInterval(10000L);
        config.setEnabled(true);
        return config;
    }
}