package com.fabric.watcher.integration;

import com.fabric.watcher.config.FileWatcherProperties;
import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.config.WatchConfigProperties;
import com.fabric.watcher.model.ProcessingResult.ExecutionStatus;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Comprehensive end-to-end integration tests for the file watcher service.
 * Tests complete file processing workflows, concurrent processing, configuration hot-reloading,
 * error scenarios, recovery mechanisms, and load testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "file-watcher.watch-configs.sql-scripts.polling-interval=1000",
    "file-watcher.watch-configs.sqlloader-logs.polling-interval=1000",
    "file-watcher.watch-configs.sqlloader-logs.enabled=true"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(EndToEndIntegrationTest.class);

    @Autowired
    private FileWatcherService fileWatcherService;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private FileWatcherProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    private Path tempDir;
    private Path sqlScriptsDir;
    private Path sqlLoaderLogsDir;
    private Path completedDir;
    private Path errorDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create temporary directories for testing
        tempDir = Files.createTempDirectory("e2e-test");
        sqlScriptsDir = tempDir.resolve("sql-scripts");
        sqlLoaderLogsDir = tempDir.resolve("sqlloader-logs");
        completedDir = sqlScriptsDir.resolve("completed");
        errorDir = sqlScriptsDir.resolve("error");

        Files.createDirectories(sqlScriptsDir);
        Files.createDirectories(sqlLoaderLogsDir);
        Files.createDirectories(completedDir);
        Files.createDirectories(errorDir);

        // Update watch configurations to use test directories
        updateWatchConfigPaths();

        logger.info("Test setup completed with temp directory: {}", tempDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up temporary directories
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
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
     * Test complete file processing workflow for SQL scripts
     * Requirements: 1.5, 2.4
     */
    @Test
    @Order(1)
    void testCompleteFileProcessingWorkflow() throws IOException, InterruptedException {
        logger.info("Starting complete file processing workflow test");

        // Create test SQL script
        String sqlContent = """
            CREATE TABLE test_table (
                id INTEGER PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            INSERT INTO test_table (id, name) VALUES (1, 'Test Record 1');
            INSERT INTO test_table (id, name) VALUES (2, 'Test Record 2');
            """;

        Path sqlFile = sqlScriptsDir.resolve("test_script.sql");
        Files.write(sqlFile, sqlContent.getBytes());

        // Start file watcher
        fileWatcherService.startWatching();

        // Wait for file to be processed
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .until(() -> !Files.exists(sqlFile));

        // Verify file was moved to completed folder
        Path completedFile = completedDir.resolve("test_script.sql");
        assertThat(Files.list(completedDir))
            .hasSize(1)
            .anySatisfy(path -> assertThat(path.getFileName().toString()).startsWith("test_script"));

        // Verify database changes
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_table", Integer.class);
        assertThat(count).isEqualTo(2);

        // Verify metrics
        ProcessingStatistics stats = metricsService.getStatistics("sql-scripts");
        assertThat(stats.getTotalFilesProcessed()).isGreaterThan(0);
        assertThat(stats.getSuccessfulExecutions()).isGreaterThan(0);

        logger.info("Complete file processing workflow test completed successfully");
    }

    /**
     * Test multiple concurrent file processing scenarios
     * Requirements: 1.5
     */
    @Test
    @Order(2)
    void testConcurrentFileProcessing() throws IOException, InterruptedException {
        logger.info("Starting concurrent file processing test");

        int numberOfFiles = 5;
        CountDownLatch latch = new CountDownLatch(numberOfFiles);
        ExecutorService executor = Executors.newFixedThreadPool(numberOfFiles);

        // Create multiple SQL files concurrently
        IntStream.range(1, numberOfFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String sqlContent = String.format("""
                        CREATE TABLE IF NOT EXISTS concurrent_test_%d (
                            id INTEGER PRIMARY KEY,
                            value VARCHAR(50)
                        );
                        INSERT INTO concurrent_test_%d (id, value) VALUES (%d, 'Value %d');
                        """, i, i, i, i);

                    Path sqlFile = sqlScriptsDir.resolve(String.format("concurrent_test_%d.sql", i));
                    Files.write(sqlFile, sqlContent.getBytes());
                    latch.countDown();
                    logger.info("Created concurrent test file: {}", sqlFile.getFileName());
                } catch (IOException e) {
                    logger.error("Failed to create concurrent test file", e);
                }
            });
        });

        // Wait for all files to be created
        latch.await(5, TimeUnit.SECONDS);

        // Start file watcher
        fileWatcherService.startWatching();

        // Wait for all files to be processed
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                try {
                    return Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("concurrent_test_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        // Verify all files were processed
        long completedFiles = Files.list(completedDir)
            .filter(path -> path.getFileName().toString().startsWith("concurrent_test_"))
            .count();
        assertThat(completedFiles).isEqualTo(numberOfFiles);

        // Verify database changes
        for (int i = 1; i <= numberOfFiles; i++) {
            String tableName = "concurrent_test_" + i;
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Integer.class);
            assertThat(count).isEqualTo(1);
        }

        executor.shutdown();
        logger.info("Concurrent file processing test completed successfully");
    }

    /**
     * Test configuration hot-reloading functionality
     * Requirements: 6.5
     */
    @Test
    @Order(3)
    void testConfigurationHotReloading() throws IOException {
        logger.info("Starting configuration hot-reloading test");

        // Get initial configuration
        WatchConfigProperties initialConfig = properties.getWatchConfigs().get("sql-scripts");
        assertThat(initialConfig.isEnabled()).isTrue();

        // Test REST API for configuration changes
        String url = "http://localhost:" + port + "/api/file-watcher/config/sql-scripts/disable";
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify configuration was updated
        await().atMost(Duration.ofSeconds(5))
            .until(() -> !fileWatcherService.isWatchingEnabled("sql-scripts"));

        // Test re-enabling
        url = "http://localhost:" + port + "/api/file-watcher/config/sql-scripts/enable";
        response = restTemplate.postForEntity(url, null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify configuration was updated
        await().atMost(Duration.ofSeconds(5))
            .until(() -> fileWatcherService.isWatchingEnabled("sql-scripts"));

        logger.info("Configuration hot-reloading test completed successfully");
    }

    /**
     * Test error scenarios and recovery mechanisms
     * Requirements: 4.4
     */
    @Test
    @Order(4)
    void testErrorScenariosAndRecovery() throws IOException {
        logger.info("Starting error scenarios and recovery test");

        // Create SQL script with syntax error
        String invalidSqlContent = """
            CREATE TABLE invalid_syntax (
                id INTEGER PRIMARY KEY,
                name VARCHAR(100) NOT NULL
            -- Missing closing parenthesis and semicolon
            
            INSERT INTO non_existent_table VALUES (1, 'test');
            """;

        Path invalidSqlFile = sqlScriptsDir.resolve("invalid_script.sql");
        Files.write(invalidSqlFile, invalidSqlContent.getBytes());

        // Start file watcher
        fileWatcherService.startWatching();

        // Wait for file to be processed and moved to error folder
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .until(() -> !Files.exists(invalidSqlFile));

        // Verify file was moved to error folder
        long errorFiles = Files.list(errorDir)
            .filter(path -> path.getFileName().toString().startsWith("invalid_script"))
            .count();
        assertThat(errorFiles).isEqualTo(1);

        // Verify metrics reflect the error
        ProcessingStatistics stats = metricsService.getStatistics("sql-scripts");
        assertThat(stats.getFailedExecutions()).isGreaterThan(0);

        // Test recovery with valid script
        String validSqlContent = """
            CREATE TABLE recovery_test (
                id INTEGER PRIMARY KEY,
                message VARCHAR(100)
            );
            INSERT INTO recovery_test (id, message) VALUES (1, 'Recovery successful');
            """;

        Path validSqlFile = sqlScriptsDir.resolve("recovery_script.sql");
        Files.write(validSqlFile, validSqlContent.getBytes());

        // Wait for valid file to be processed
        await().atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .until(() -> !Files.exists(validSqlFile));

        // Verify recovery
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recovery_test", Integer.class);
        assertThat(count).isEqualTo(1);

        logger.info("Error scenarios and recovery test completed successfully");
    }

    /**
     * Test load testing with multiple files and processors
     * Requirements: 1.5, 2.4
     */
    @Test
    @Order(5)
    void testLoadTestingWithMultipleFilesAndProcessors() throws IOException, InterruptedException {
        logger.info("Starting load testing with multiple files and processors");

        int numberOfSqlFiles = 10;
        int numberOfLogFiles = 5;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch sqlLatch = new CountDownLatch(numberOfSqlFiles);
        CountDownLatch logLatch = new CountDownLatch(numberOfLogFiles);

        // Create multiple SQL files
        IntStream.range(1, numberOfSqlFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String sqlContent = String.format("""
                        CREATE TABLE IF NOT EXISTS load_test_%d (
                            id INTEGER PRIMARY KEY,
                            data VARCHAR(100),
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                        INSERT INTO load_test_%d (id, data) VALUES (%d, 'Load test data %d');
                        """, i, i, i, i);

                    Path sqlFile = sqlScriptsDir.resolve(String.format("load_test_%d.sql", i));
                    Files.write(sqlFile, sqlContent.getBytes());
                    sqlLatch.countDown();
                } catch (IOException e) {
                    logger.error("Failed to create load test SQL file", e);
                }
            });
        });

        // Create multiple SQL*Loader log files
        IntStream.range(1, numberOfLogFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String logContent = String.format("""
                        SQL*Loader: Release 19.0.0.0.0 - Production on Mon Jan 1 12:00:00 2024
                        
                        Control File:   load_test_%d.ctl
                        Data File:      load_test_%d.dat
                        Bad File:       load_test_%d.bad
                        Discard File:   none specified
                        
                        (Allow all discards)
                        
                        Number to load: ALL
                        Number to skip: 0
                        Errors allowed: 50
                        Bind array:     64 rows, maximum of 256000 bytes
                        Continuation:   none specified
                        Path used:      Conventional
                        
                        Table TEST_TABLE, loaded from every logical record.
                        Insert option in effect for this table: INSERT
                        
                           Column Name                  Position   Len  Term Encl Datatype
                        ------------------------------ ---------- ----- ---- ---- -----------
                        ID                                  FIRST     *   ,       CHARACTER
                        NAME                                 NEXT    50   ,       CHARACTER
                        
                        Table TEST_TABLE:
                          %d Rows successfully loaded.
                          0 Rows not loaded due to data errors.
                          0 Rows not loaded because all WHEN clauses were failed.
                          0 Rows not loaded because all fields were null.
                        
                        Run began on Mon Jan 01 12:00:00 2024
                        Run ended on Mon Jan 01 12:00:05 2024
                        
                        Elapsed time was:     00:00:05.12
                        CPU time was:         00:00:01.23
                        """, i, i, i, i * 100);

                    Path logFile = sqlLoaderLogsDir.resolve(String.format("load_test_%d.log", i));
                    Files.write(logFile, logContent.getBytes());
                    logLatch.countDown();
                } catch (IOException e) {
                    logger.error("Failed to create load test log file", e);
                }
            });
        });

        // Wait for all files to be created
        sqlLatch.await(10, TimeUnit.SECONDS);
        logLatch.await(10, TimeUnit.SECONDS);

        // Record start time for performance measurement
        long startTime = System.currentTimeMillis();

        // Start file watcher
        fileWatcherService.startWatching();

        // Wait for all SQL files to be processed
        await().atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                try {
                    return Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("load_test_"))
                        .filter(path -> path.getFileName().toString().endsWith(".sql"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        // Wait for all log files to be processed
        await().atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                try {
                    return Files.list(sqlLoaderLogsDir)
                        .filter(path -> path.getFileName().toString().startsWith("load_test_"))
                        .filter(path -> path.getFileName().toString().endsWith(".log"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        long endTime = System.currentTimeMillis();
        long totalProcessingTime = endTime - startTime;

        // Verify all SQL files were processed successfully
        for (int i = 1; i <= numberOfSqlFiles; i++) {
            String tableName = "load_test_" + i;
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Integer.class);
            assertThat(count).isEqualTo(1);
        }

        // Verify audit records were created for log files
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sqlloader_audit WHERE log_filename LIKE 'load_test_%'", 
            Integer.class);
        assertThat(auditCount).isEqualTo(numberOfLogFiles);

        // Verify performance metrics
        ProcessingStatistics sqlStats = metricsService.getStatistics("sql-scripts");
        ProcessingStatistics logStats = metricsService.getStatistics("sqlloader-logs");

        assertThat(sqlStats.getTotalFilesProcessed()).isGreaterThanOrEqualTo(numberOfSqlFiles);
        assertThat(logStats.getTotalFilesProcessed()).isGreaterThanOrEqualTo(numberOfLogFiles);

        // Log performance results
        logger.info("Load test completed in {} ms", totalProcessingTime);
        logger.info("SQL files processed: {}, Log files processed: {}", 
            numberOfSqlFiles, numberOfLogFiles);
        logger.info("Average processing time per file: {} ms", 
            totalProcessingTime / (numberOfSqlFiles + numberOfLogFiles));

        executor.shutdown();
        logger.info("Load testing completed successfully");
    }

    /**
     * Test system health and monitoring during load
     */
    @Test
    @Order(6)
    void testSystemHealthDuringLoad() {
        logger.info("Starting system health monitoring test");

        // Test health endpoints
        String healthUrl = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<String> healthResponse = restTemplate.getForEntity(healthUrl, String.class);
        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test metrics endpoints
        String metricsUrl = "http://localhost:" + port + "/actuator/metrics";
        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(metricsUrl, String.class);
        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Test custom monitoring endpoints
        String statusUrl = "http://localhost:" + port + "/api/file-watcher/status";
        ResponseEntity<String> statusResponse = restTemplate.getForEntity(statusUrl, String.class);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        logger.info("System health monitoring test completed successfully");
    }

    private void updateWatchConfigPaths() {
        // Update SQL scripts configuration
        WatchConfigProperties sqlConfig = properties.getWatchConfigs().get("sql-scripts");
        if (sqlConfig != null) {
            sqlConfig.setWatchFolder(sqlScriptsDir.toString());
            sqlConfig.setCompletedFolder(completedDir.toString());
            sqlConfig.setErrorFolder(errorDir.toString());
        }

        // Update SQL*Loader logs configuration
        WatchConfigProperties logConfig = properties.getWatchConfigs().get("sqlloader-logs");
        if (logConfig != null) {
            logConfig.setWatchFolder(sqlLoaderLogsDir.toString());
            logConfig.setCompletedFolder(sqlLoaderLogsDir.resolve("completed").toString());
            logConfig.setErrorFolder(sqlLoaderLogsDir.resolve("error").toString());
        }
    }
}