package com.fabric.watcher.integration;

import com.fabric.watcher.config.FileWatcherProperties;
import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.config.WatchConfigProperties;
import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests focused on concurrent file processing scenarios.
 * Tests various concurrent processing patterns, race conditions, and performance under load.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "file-watcher.watch-configs.sql-scripts.polling-interval=500",
    "file-watcher.watch-configs.sqlloader-logs.polling-interval=500",
    "file-watcher.watch-configs.sqlloader-logs.enabled=true"
})
class ConcurrentProcessingIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentProcessingIntegrationTest.class);

    @Autowired
    private FileWatcherService fileWatcherService;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private FileWatcherProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Path tempDir;
    private Path sqlScriptsDir;
    private Path sqlLoaderLogsDir;
    private Path completedDir;
    private Path errorDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("concurrent-test");
        sqlScriptsDir = tempDir.resolve("sql-scripts");
        sqlLoaderLogsDir = tempDir.resolve("sqlloader-logs");
        completedDir = sqlScriptsDir.resolve("completed");
        errorDir = sqlScriptsDir.resolve("error");

        Files.createDirectories(sqlScriptsDir);
        Files.createDirectories(sqlLoaderLogsDir);
        Files.createDirectories(completedDir);
        Files.createDirectories(errorDir);
        Files.createDirectories(sqlLoaderLogsDir.resolve("completed"));
        Files.createDirectories(sqlLoaderLogsDir.resolve("error"));

        updateWatchConfigPaths();
        logger.info("Concurrent test setup completed with temp directory: {}", tempDir);
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
     * Test concurrent processing of multiple SQL files with different complexities
     */
    @Test
    void testConcurrentSqlProcessing() throws IOException, InterruptedException {
        logger.info("Starting concurrent SQL processing test");

        int numberOfFiles = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfFiles);
        CountDownLatch creationLatch = new CountDownLatch(numberOfFiles);
        AtomicInteger fileCounter = new AtomicInteger(0);

        // Create files with varying complexity concurrently
        IntStream.range(1, numberOfFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    int fileNum = fileCounter.incrementAndGet();
                    String sqlContent;

                    // Create different types of SQL scripts
                    if (fileNum % 4 == 0) {
                        // Complex script with multiple operations
                        sqlContent = String.format("""
                            CREATE TABLE IF NOT EXISTS complex_table_%d (
                                id INTEGER PRIMARY KEY,
                                name VARCHAR(100) NOT NULL,
                                email VARCHAR(255) UNIQUE,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            );
                            
                            CREATE INDEX IF NOT EXISTS idx_complex_%d_name ON complex_table_%d(name);
                            CREATE INDEX IF NOT EXISTS idx_complex_%d_email ON complex_table_%d(email);
                            
                            INSERT INTO complex_table_%d (id, name, email) VALUES 
                                (%d, 'User %d', 'user%d@test.com'),
                                (%d, 'Admin %d', 'admin%d@test.com');
                            
                            UPDATE complex_table_%d SET updated_at = CURRENT_TIMESTAMP WHERE id = %d;
                            """, fileNum, fileNum, fileNum, fileNum, fileNum, fileNum, 
                            fileNum * 10, fileNum, fileNum, fileNum * 10 + 1, fileNum, fileNum, 
                            fileNum, fileNum * 10);
                    } else if (fileNum % 3 == 0) {
                        // DDL script
                        sqlContent = String.format("""
                            CREATE TABLE IF NOT EXISTS ddl_table_%d (
                                id INTEGER PRIMARY KEY,
                                data VARCHAR(500),
                                status VARCHAR(20) DEFAULT 'ACTIVE'
                            );
                            
                            ALTER TABLE ddl_table_%d ADD COLUMN IF NOT EXISTS description TEXT;
                            """, fileNum, fileNum);
                    } else {
                        // Simple DML script
                        sqlContent = String.format("""
                            CREATE TABLE IF NOT EXISTS simple_table_%d (
                                id INTEGER PRIMARY KEY,
                                value VARCHAR(100)
                            );
                            
                            INSERT INTO simple_table_%d (id, value) VALUES (%d, 'Concurrent Value %d');
                            """, fileNum, fileNum, fileNum, fileNum);
                    }

                    Path sqlFile = sqlScriptsDir.resolve(String.format("concurrent_%03d.sql", fileNum));
                    Files.write(sqlFile, sqlContent.getBytes());
                    creationLatch.countDown();
                    logger.debug("Created concurrent SQL file: {}", sqlFile.getFileName());
                } catch (IOException e) {
                    logger.error("Failed to create concurrent SQL file", e);
                }
            });
        });

        // Wait for all files to be created
        assertThat(creationLatch.await(10, TimeUnit.SECONDS)).isTrue();

        // Start file watcher
        fileWatcherService.startWatching();

        // Wait for all files to be processed
        await().atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                try {
                    return Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("concurrent_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        // Verify all files were processed successfully
        long completedFiles = Files.list(completedDir)
            .filter(path -> path.getFileName().toString().startsWith("concurrent_"))
            .count();
        assertThat(completedFiles).isEqualTo(numberOfFiles);

        // Verify database state
        verifyDatabaseState(numberOfFiles);

        // Verify metrics
        ProcessingStatistics stats = metricsService.getStatistics("sql-scripts");
        assertThat(stats.getTotalFilesProcessed()).isGreaterThanOrEqualTo(numberOfFiles);
        assertThat(stats.getSuccessfulExecutions()).isGreaterThanOrEqualTo(numberOfFiles);

        executor.shutdown();
        logger.info("Concurrent SQL processing test completed successfully");
    }

    /**
     * Test concurrent processing of mixed file types (SQL and SQL*Loader logs)
     */
    @Test
    void testConcurrentMixedFileProcessing() throws IOException, InterruptedException {
        logger.info("Starting concurrent mixed file processing test");

        int sqlFiles = 10;
        int logFiles = 8;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch sqlLatch = new CountDownLatch(sqlFiles);
        CountDownLatch logLatch = new CountDownLatch(logFiles);

        // Create SQL files concurrently
        IntStream.range(1, sqlFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String sqlContent = String.format("""
                        CREATE TABLE IF NOT EXISTS mixed_sql_%d (
                            id INTEGER PRIMARY KEY,
                            data VARCHAR(200),
                            processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                        INSERT INTO mixed_sql_%d (id, data) VALUES (%d, 'Mixed processing test %d');
                        """, i, i, i, i);

                    Path sqlFile = sqlScriptsDir.resolve(String.format("mixed_sql_%d.sql", i));
                    Files.write(sqlFile, sqlContent.getBytes());
                    sqlLatch.countDown();
                } catch (IOException e) {
                    logger.error("Failed to create mixed SQL file", e);
                }
            });
        });

        // Create SQL*Loader log files concurrently
        IntStream.range(1, logFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String logContent = String.format("""
                        SQL*Loader: Release 19.0.0.0.0 - Production on Mon Jan 1 12:00:00 2024
                        
                        Control File:   mixed_load_%d.ctl
                        Data File:      mixed_load_%d.dat
                        Bad File:       mixed_load_%d.bad
                        Discard File:   none specified
                        
                        Table MIXED_TABLE_%d, loaded from every logical record.
                        Insert option in effect for this table: INSERT
                        
                        Table MIXED_TABLE_%d:
                          %d Rows successfully loaded.
                          0 Rows not loaded due to data errors.
                          0 Rows not loaded because all WHEN clauses were failed.
                          0 Rows not loaded because all fields were null.
                        
                        Run began on Mon Jan 01 12:00:00 2024
                        Run ended on Mon Jan 01 12:00:0%d 2024
                        
                        Elapsed time was:     00:00:0%d.%02d
                        CPU time was:         00:00:01.%02d
                        """, i, i, i, i, i, i * 50, i % 10, i % 10, i % 100, i % 100);

                    Path logFile = sqlLoaderLogsDir.resolve(String.format("mixed_load_%d.log", i));
                    Files.write(logFile, logContent.getBytes());
                    logLatch.countDown();
                } catch (IOException e) {
                    logger.error("Failed to create mixed log file", e);
                }
            });
        });

        // Wait for all files to be created
        assertThat(sqlLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(logLatch.await(10, TimeUnit.SECONDS)).isTrue();

        // Start file watcher
        fileWatcherService.startWatching();

        // Wait for all SQL files to be processed
        await().atMost(Duration.ofSeconds(45))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                try {
                    return Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("mixed_sql_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        // Wait for all log files to be processed
        await().atMost(Duration.ofSeconds(45))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                try {
                    return Files.list(sqlLoaderLogsDir)
                        .filter(path -> path.getFileName().toString().startsWith("mixed_load_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        // Verify SQL files were processed
        for (int i = 1; i <= sqlFiles; i++) {
            String tableName = "mixed_sql_" + i;
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Integer.class);
            assertThat(count).isEqualTo(1);
        }

        // Verify log files were processed (audit records created)
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sqlloader_audit WHERE log_filename LIKE 'mixed_load_%'", 
            Integer.class);
        assertThat(auditCount).isEqualTo(logFiles);

        executor.shutdown();
        logger.info("Concurrent mixed file processing test completed successfully");
    }

    /**
     * Test race conditions and file locking scenarios
     */
    @Test
    void testRaceConditionsAndFileLocking() throws IOException, InterruptedException {
        logger.info("Starting race conditions and file locking test");

        int numberOfThreads = 5;
        int filesPerThread = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);
        List<Future<Integer>> futures = new ArrayList<>();

        // Start file watcher first
        fileWatcherService.startWatching();

        // Create multiple threads that create files simultaneously
        for (int threadId = 1; threadId <= numberOfThreads; threadId++) {
            final int tId = threadId;
            Future<Integer> future = executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    int filesCreated = 0;
                    for (int fileId = 1; fileId <= filesPerThread; fileId++) {
                        String sqlContent = String.format("""
                            CREATE TABLE IF NOT EXISTS race_test_%d_%d (
                                id INTEGER PRIMARY KEY,
                                thread_id INTEGER,
                                file_id INTEGER,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            );
                            INSERT INTO race_test_%d_%d (id, thread_id, file_id) VALUES (%d, %d, %d);
                            """, tId, fileId, tId, fileId, (tId * 100) + fileId, tId, fileId);

                        Path sqlFile = sqlScriptsDir.resolve(String.format("race_%d_%d.sql", tId, fileId));
                        Files.write(sqlFile, sqlContent.getBytes());
                        filesCreated++;
                        
                        // Small delay to create some overlap
                        Thread.sleep(50);
                    }
                    
                    completionLatch.countDown();
                    return filesCreated;
                } catch (Exception e) {
                    logger.error("Error in race condition test thread", e);
                    completionLatch.countDown();
                    return 0;
                }
            });
            futures.add(future);
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete file creation
        assertThat(completionLatch.await(15, TimeUnit.SECONDS)).isTrue();

        // Verify all files were created
        int totalExpectedFiles = numberOfThreads * filesPerThread;
        int totalFilesCreated = futures.stream()
            .mapToInt(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    return 0;
                }
            })
            .sum();
        assertThat(totalFilesCreated).isEqualTo(totalExpectedFiles);

        // Wait for all files to be processed
        await().atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> {
                try {
                    return Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("race_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        // Verify all files were processed successfully
        long completedFiles = Files.list(completedDir)
            .filter(path -> path.getFileName().toString().startsWith("race_"))
            .count();
        assertThat(completedFiles).isEqualTo(totalExpectedFiles);

        // Verify database integrity
        for (int threadId = 1; threadId <= numberOfThreads; threadId++) {
            for (int fileId = 1; fileId <= filesPerThread; fileId++) {
                String tableName = String.format("race_test_%d_%d", threadId, fileId);
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName, Integer.class);
                assertThat(count).isEqualTo(1);
            }
        }

        executor.shutdown();
        logger.info("Race conditions and file locking test completed successfully");
    }

    /**
     * Test performance under high concurrent load
     */
    @Test
    void testHighConcurrentLoad() throws IOException, InterruptedException {
        logger.info("Starting high concurrent load test");

        int numberOfFiles = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch creationLatch = new CountDownLatch(numberOfFiles);
        long startTime = System.currentTimeMillis();

        // Create many files rapidly
        IntStream.range(1, numberOfFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String sqlContent = String.format("""
                        CREATE TABLE IF NOT EXISTS load_table_%d (
                            id INTEGER PRIMARY KEY,
                            batch_id INTEGER,
                            data VARCHAR(1000),
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                        INSERT INTO load_table_%d (id, batch_id, data) VALUES 
                            (%d, %d, 'High load test data for file %d - %s');
                        """, i, i, i, i / 10, i, "x".repeat(100));

                    Path sqlFile = sqlScriptsDir.resolve(String.format("load_%04d.sql", i));
                    Files.write(sqlFile, sqlContent.getBytes());
                    creationLatch.countDown();
                } catch (IOException e) {
                    logger.error("Failed to create high load test file", e);
                }
            });
        });

        // Wait for all files to be created
        assertThat(creationLatch.await(15, TimeUnit.SECONDS)).isTrue();
        long creationTime = System.currentTimeMillis() - startTime;

        // Start file watcher
        long processingStartTime = System.currentTimeMillis();
        fileWatcherService.startWatching();

        // Wait for all files to be processed
        await().atMost(Duration.ofSeconds(120))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                try {
                    long remaining = Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("load_"))
                        .count();
                    logger.debug("Files remaining to process: {}", remaining);
                    return remaining == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        long processingTime = System.currentTimeMillis() - processingStartTime;
        long totalTime = System.currentTimeMillis() - startTime;

        // Verify all files were processed
        long completedFiles = Files.list(completedDir)
            .filter(path -> path.getFileName().toString().startsWith("load_"))
            .count();
        assertThat(completedFiles).isEqualTo(numberOfFiles);

        // Verify database state
        for (int i = 1; i <= numberOfFiles; i++) {
            String tableName = "load_table_" + i;
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Integer.class);
            assertThat(count).isEqualTo(1);
        }

        // Log performance metrics
        logger.info("High concurrent load test performance:");
        logger.info("  Files created: {} in {} ms", numberOfFiles, creationTime);
        logger.info("  Files processed: {} in {} ms", numberOfFiles, processingTime);
        logger.info("  Total time: {} ms", totalTime);
        logger.info("  Average processing time per file: {} ms", processingTime / numberOfFiles);
        logger.info("  Throughput: {} files/second", (numberOfFiles * 1000.0) / processingTime);

        // Verify performance is reasonable (should process at least 1 file per second on average)
        double throughput = (numberOfFiles * 1000.0) / processingTime;
        assertThat(throughput).isGreaterThan(0.5); // At least 0.5 files per second

        executor.shutdown();
        logger.info("High concurrent load test completed successfully");
    }

    private void verifyDatabaseState(int numberOfFiles) {
        for (int i = 1; i <= numberOfFiles; i++) {
            if (i % 4 == 0) {
                // Complex table
                String tableName = "complex_table_" + i;
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName, Integer.class);
                assertThat(count).isEqualTo(2);
            } else if (i % 3 == 0) {
                // DDL table (just verify it exists)
                String tableName = "ddl_table_" + i;
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName, Integer.class);
                assertThat(count).isGreaterThanOrEqualTo(0);
            } else {
                // Simple table
                String tableName = "simple_table_" + i;
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName, Integer.class);
                assertThat(count).isEqualTo(1);
            }
        }
    }

    private void updateWatchConfigPaths() {
        WatchConfigProperties sqlConfig = properties.getWatchConfigs().get("sql-scripts");
        if (sqlConfig != null) {
            sqlConfig.setWatchFolder(sqlScriptsDir.toString());
            sqlConfig.setCompletedFolder(completedDir.toString());
            sqlConfig.setErrorFolder(errorDir.toString());
        }

        WatchConfigProperties logConfig = properties.getWatchConfigs().get("sqlloader-logs");
        if (logConfig != null) {
            logConfig.setWatchFolder(sqlLoaderLogsDir.toString());
            logConfig.setCompletedFolder(sqlLoaderLogsDir.resolve("completed").toString());
            logConfig.setErrorFolder(sqlLoaderLogsDir.resolve("error").toString());
        }
    }
}