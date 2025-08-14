package com.fabric.watcher.integration;

import com.fabric.watcher.config.ConfigurationService;
import com.fabric.watcher.config.FileWatcherProperties;
import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.config.WatchConfigProperties;
import com.fabric.watcher.service.FileWatcherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for configuration hot-reloading functionality.
 * Tests dynamic configuration changes without service restart.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "file-watcher.watch-configs.sql-scripts.polling-interval=1000",
    "file-watcher.watch-configs.sqlloader-logs.polling-interval=1000",
    "file-watcher.watch-configs.sqlloader-logs.enabled=true"
})
class ConfigurationHotReloadingIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationHotReloadingIntegrationTest.class);

    @Autowired
    private FileWatcherService fileWatcherService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private FileWatcherProperties properties;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    private Path tempDir;
    private Path sqlScriptsDir;
    private Path newWatchDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("config-reload-test");
        sqlScriptsDir = tempDir.resolve("sql-scripts");
        newWatchDir = tempDir.resolve("new-watch-dir");

        Files.createDirectories(sqlScriptsDir);
        Files.createDirectories(sqlScriptsDir.resolve("completed"));
        Files.createDirectories(sqlScriptsDir.resolve("error"));
        Files.createDirectories(newWatchDir);
        Files.createDirectories(newWatchDir.resolve("completed"));
        Files.createDirectories(newWatchDir.resolve("error"));

        // Update initial configuration
        updateInitialWatchConfigPaths();
        
        logger.info("Configuration hot-reloading test setup completed");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        logger.warn("Failed to delete test file: {}", path, e);
                    }
                });
        }
    }

    /**
     * Test enabling and disabling watch configurations dynamically
     */
    @Test
    void testEnableDisableWatchConfiguration() throws IOException, InterruptedException {
        logger.info("Starting enable/disable watch configuration test");

        // Start with configuration enabled
        fileWatcherService.startWatching();
        assertThat(fileWatcherService.isWatchingEnabled("sql-scripts")).isTrue();

        // Create a test file to verify watching is active
        String sqlContent = """
            CREATE TABLE test_enable_disable (
                id INTEGER PRIMARY KEY,
                status VARCHAR(50)
            );
            INSERT INTO test_enable_disable (id, status) VALUES (1, 'ENABLED');
            """;
        Path testFile = sqlScriptsDir.resolve("test_enable.sql");
        Files.write(testFile, sqlContent.getBytes());

        // Wait for file to be processed
        await().atMost(Duration.ofSeconds(10))
            .until(() -> !Files.exists(testFile));

        // Disable configuration via REST API
        String disableUrl = "http://localhost:" + port + "/api/file-watcher/config/sql-scripts/disable";
        ResponseEntity<String> response = restTemplate.postForEntity(disableUrl, null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify configuration is disabled
        await().atMost(Duration.ofSeconds(5))
            .until(() -> !fileWatcherService.isWatchingEnabled("sql-scripts"));

        // Create another test file - should not be processed
        Path testFile2 = sqlScriptsDir.resolve("test_disabled.sql");
        Files.write(testFile2, sqlContent.getBytes());

        // Wait and verify file is not processed
        Thread.sleep(3000);
        assertThat(Files.exists(testFile2)).isTrue();

        // Re-enable configuration
        String enableUrl = "http://localhost:" + port + "/api/file-watcher/config/sql-scripts/enable";
        response = restTemplate.postForEntity(enableUrl, null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify configuration is enabled
        await().atMost(Duration.ofSeconds(5))
            .until(() -> fileWatcherService.isWatchingEnabled("sql-scripts"));

        // Verify the previously unprocessed file is now processed
        await().atMost(Duration.ofSeconds(10))
            .until(() -> !Files.exists(testFile2));

        logger.info("Enable/disable watch configuration test completed successfully");
    }

    /**
     * Test updating polling intervals dynamically
     */
    @Test
    void testUpdatePollingInterval() {
        logger.info("Starting update polling interval test");

        // Get initial polling interval
        WatchConfigProperties initialConfig = properties.getWatchConfigs().get("sql-scripts");
        long initialInterval = initialConfig.getPollingInterval();
        assertThat(initialInterval).isEqualTo(1000);

        // Update polling interval via configuration service
        Map<String, Object> updates = new HashMap<>();
        updates.put("polling-interval", 2000L);
        
        configurationService.updateWatchConfig("sql-scripts", updates);

        // Verify polling interval was updated
        await().atMost(Duration.ofSeconds(5))
            .until(() -> {
                WatchConfigProperties updatedConfig = properties.getWatchConfigs().get("sql-scripts");
                return updatedConfig.getPollingInterval() == 2000L;
            });

        // Update back to original interval
        updates.put("polling-interval", 500L);
        configurationService.updateWatchConfig("sql-scripts", updates);

        // Verify polling interval was updated again
        await().atMost(Duration.ofSeconds(5))
            .until(() -> {
                WatchConfigProperties updatedConfig = properties.getWatchConfigs().get("sql-scripts");
                return updatedConfig.getPollingInterval() == 500L;
            });

        logger.info("Update polling interval test completed successfully");
    }

    /**
     * Test updating file patterns dynamically
     */
    @Test
    void testUpdateFilePatterns() throws IOException, InterruptedException {
        logger.info("Starting update file patterns test");

        fileWatcherService.startWatching();

        // Create files with different extensions
        String sqlContent = """
            CREATE TABLE test_patterns (id INTEGER PRIMARY KEY, name VARCHAR(100));
            INSERT INTO test_patterns (id, name) VALUES (1, 'Pattern Test');
            """;

        Path sqlFile = sqlScriptsDir.resolve("test.sql");
        Path txtFile = sqlScriptsDir.resolve("test.txt");
        
        Files.write(sqlFile, sqlContent.getBytes());
        Files.write(txtFile, sqlContent.getBytes());

        // Wait for SQL file to be processed (should be processed with default pattern)
        await().atMost(Duration.ofSeconds(10))
            .until(() -> !Files.exists(sqlFile));

        // TXT file should not be processed
        Thread.sleep(2000);
        assertThat(Files.exists(txtFile)).isTrue();

        // Update file patterns to include .txt files
        Map<String, Object> updates = new HashMap<>();
        updates.put("file-patterns", List.of("*.sql", "*.txt"));
        
        configurationService.updateWatchConfig("sql-scripts", updates);

        // Verify file patterns were updated
        await().atMost(Duration.ofSeconds(5))
            .until(() -> {
                WatchConfigProperties updatedConfig = properties.getWatchConfigs().get("sql-scripts");
                return updatedConfig.getFilePatterns().contains("*.txt");
            });

        // Now the TXT file should be processed
        await().atMost(Duration.ofSeconds(10))
            .until(() -> !Files.exists(txtFile));

        logger.info("Update file patterns test completed successfully");
    }

    /**
     * Test updating watch folder paths dynamically
     */
    @Test
    void testUpdateWatchFolderPaths() throws IOException {
        logger.info("Starting update watch folder paths test");

        fileWatcherService.startWatching();

        // Create test file in original directory
        String sqlContent = """
            CREATE TABLE test_folder_change (id INTEGER PRIMARY KEY, location VARCHAR(100));
            INSERT INTO test_folder_change (id, location) VALUES (1, 'Original Folder');
            """;

        Path originalFile = sqlScriptsDir.resolve("original_location.sql");
        Files.write(originalFile, sqlContent.getBytes());

        // Wait for file to be processed
        await().atMost(Duration.ofSeconds(10))
            .until(() -> !Files.exists(originalFile));

        // Update watch folder path
        Map<String, Object> updates = new HashMap<>();
        updates.put("watch-folder", newWatchDir.toString());
        updates.put("completed-folder", newWatchDir.resolve("completed").toString());
        updates.put("error-folder", newWatchDir.resolve("error").toString());
        
        configurationService.updateWatchConfig("sql-scripts", updates);

        // Verify watch folder was updated
        await().atMost(Duration.ofSeconds(5))
            .until(() -> {
                WatchConfigProperties updatedConfig = properties.getWatchConfigs().get("sql-scripts");
                return updatedConfig.getWatchFolder().equals(newWatchDir.toString());
            });

        // Create test file in new directory
        Path newFile = newWatchDir.resolve("new_location.sql");
        Files.write(newFile, sqlContent.getBytes());

        // Wait for file to be processed from new location
        await().atMost(Duration.ofSeconds(10))
            .until(() -> !Files.exists(newFile));

        // Verify file was moved to new completed folder
        long completedFiles = Files.list(newWatchDir.resolve("completed"))
            .filter(path -> path.getFileName().toString().startsWith("new_location"))
            .count();
        assertThat(completedFiles).isEqualTo(1);

        logger.info("Update watch folder paths test completed successfully");
    }

    /**
     * Test adding new watch configuration dynamically
     */
    @Test
    void testAddNewWatchConfiguration() throws IOException {
        logger.info("Starting add new watch configuration test");

        // Create directory for new watch configuration
        Path newConfigDir = tempDir.resolve("dynamic-config");
        Files.createDirectories(newConfigDir);
        Files.createDirectories(newConfigDir.resolve("completed"));
        Files.createDirectories(newConfigDir.resolve("error"));

        // Create new watch configuration
        WatchConfig newConfig = new WatchConfig();
        newConfig.setName("Dynamic Test Config");
        newConfig.setProcessorType("sql-script");
        newConfig.setWatchFolder(newConfigDir);
        newConfig.setCompletedFolder(newConfigDir.resolve("completed"));
        newConfig.setErrorFolder(newConfigDir.resolve("error"));
        newConfig.setFilePatterns(List.of("*.sql"));
        newConfig.setPollingInterval(1000);
        newConfig.setEnabled(true);

        // Add new configuration
        configurationService.addWatchConfig("dynamic-test", newConfig);

        // Verify configuration was added
        await().atMost(Duration.ofSeconds(5))
            .until(() -> properties.getWatchConfigs().containsKey("dynamic-test"));

        // Start watching (should include new configuration)
        fileWatcherService.startWatching();

        // Create test file in new configuration directory
        String sqlContent = """
            CREATE TABLE dynamic_config_test (id INTEGER PRIMARY KEY, config_name VARCHAR(100));
            INSERT INTO dynamic_config_test (id, config_name) VALUES (1, 'Dynamic Test');
            """;

        Path testFile = newConfigDir.resolve("dynamic_test.sql");
        Files.write(testFile, sqlContent.getBytes());

        // Wait for file to be processed
        await().atMost(Duration.ofSeconds(10))
            .until(() -> !Files.exists(testFile));

        // Verify file was processed and moved to completed folder
        long completedFiles = Files.list(newConfigDir.resolve("completed"))
            .filter(path -> path.getFileName().toString().startsWith("dynamic_test"))
            .count();
        assertThat(completedFiles).isEqualTo(1);

        logger.info("Add new watch configuration test completed successfully");
    }

    /**
     * Test removing watch configuration dynamically
     */
    @Test
    void testRemoveWatchConfiguration() throws IOException {
        logger.info("Starting remove watch configuration test");

        fileWatcherService.startWatching();

        // Verify initial configuration exists
        assertThat(properties.getWatchConfigs().containsKey("sqlloader-logs")).isTrue();

        // Remove configuration
        configurationService.removeWatchConfig("sqlloader-logs");

        // Verify configuration was removed
        await().atMost(Duration.ofSeconds(5))
            .until(() -> !properties.getWatchConfigs().containsKey("sqlloader-logs"));

        // Verify service is no longer watching the removed configuration
        assertThat(fileWatcherService.isWatchingEnabled("sqlloader-logs")).isFalse();

        logger.info("Remove watch configuration test completed successfully");
    }

    /**
     * Test configuration validation during hot-reloading
     */
    @Test
    void testConfigurationValidationDuringReload() {
        logger.info("Starting configuration validation during reload test");

        // Try to update with invalid polling interval
        Map<String, Object> invalidUpdates = new HashMap<>();
        invalidUpdates.put("polling-interval", -1000L);

        try {
            configurationService.updateWatchConfig("sql-scripts", invalidUpdates);
            // Should not reach here if validation is working
            assertThat(false).as("Expected validation exception").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("polling interval");
        }

        // Try to update with invalid file patterns
        invalidUpdates.clear();
        invalidUpdates.put("file-patterns", List.of("", null, "*.sql"));

        try {
            configurationService.updateWatchConfig("sql-scripts", invalidUpdates);
            // Should not reach here if validation is working
            assertThat(false).as("Expected validation exception").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("file pattern");
        }

        // Verify original configuration is still intact
        WatchConfigProperties config = properties.getWatchConfigs().get("sql-scripts");
        assertThat(config.getPollingInterval()).isEqualTo(1000);
        assertThat(config.getFilePatterns()).containsExactly("*.sql");

        logger.info("Configuration validation during reload test completed successfully");
    }

    /**
     * Test configuration persistence across service restarts
     */
    @Test
    void testConfigurationPersistenceAcrossRestarts() {
        logger.info("Starting configuration persistence across restarts test");

        // Update configuration
        Map<String, Object> updates = new HashMap<>();
        updates.put("polling-interval", 3000L);
        updates.put("file-patterns", List.of("*.sql", "*.ddl"));
        
        configurationService.updateWatchConfig("sql-scripts", updates);

        // Verify configuration was updated
        await().atMost(Duration.ofSeconds(5))
            .until(() -> {
                WatchConfigProperties updatedConfig = properties.getWatchConfigs().get("sql-scripts");
                return updatedConfig.getPollingInterval() == 3000L &&
                       updatedConfig.getFilePatterns().contains("*.ddl");
            });

        // Simulate service restart by stopping and starting file watcher
        fileWatcherService.stopWatching();
        fileWatcherService.startWatching();

        // Verify configuration persisted
        WatchConfigProperties persistedConfig = properties.getWatchConfigs().get("sql-scripts");
        assertThat(persistedConfig.getPollingInterval()).isEqualTo(3000);
        assertThat(persistedConfig.getFilePatterns()).contains("*.sql", "*.ddl");

        logger.info("Configuration persistence across restarts test completed successfully");
    }

    private void updateInitialWatchConfigPaths() {
        WatchConfigProperties sqlConfig = properties.getWatchConfigs().get("sql-scripts");
        if (sqlConfig != null) {
            sqlConfig.setWatchFolder(sqlScriptsDir.toString());
            sqlConfig.setCompletedFolder(sqlScriptsDir.resolve("completed").toString());
            sqlConfig.setErrorFolder(sqlScriptsDir.resolve("error").toString());
        }
    }
}