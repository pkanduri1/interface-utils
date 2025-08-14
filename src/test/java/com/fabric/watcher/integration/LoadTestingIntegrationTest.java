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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Load testing integration tests for the file watcher service.
 * Tests performance, scalability, and resource usage under various load conditions.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "file-watcher.watch-configs.sql-scripts.polling-interval=100",
    "file-watcher.watch-configs.sqlloader-logs.polling-interval=100",
    "file-watcher.watch-configs.sqlloader-logs.enabled=true",
    "spring.datasource.hikari.maximum-pool-size=20",
    "spring.datasource.hikari.minimum-idle=5"
})
class LoadTestingIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestingIntegrationTest.class);

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

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("load-test");
        sqlScriptsDir = tempDir.resolve("sql-scripts");
        sqlLoaderLogsDir = tempDir.resolve("sqlloader-logs");

        Files.createDirectories(sqlScriptsDir);
        Files.createDirectories(sqlScriptsDir.resolve("completed"));
        Files.createDirectories(sqlScriptsDir.resolve("error"));
        Files.createDirectories(sqlLoaderLogsDir);
        Files.createDirectories(sqlLoaderLogsDir.resolve("completed"));
        Files.createDirectories(sqlLoaderLogsDir.resolve("error"));

        updateWatchConfigPaths();
        logger.info("Load testing setup completed with temp directory: {}", tempDir);
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
     * Test high-volume SQL file processing
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void testHighVolumeSqlProcessing() throws IOException, InterruptedException {
        logger.info("Starting high-volume SQL processing load test");

        int numberOfFiles = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch creationLatch = new CountDownLatch(numberOfFiles);
        AtomicLong totalCreationTime = new AtomicLong(0);
        AtomicInteger filesCreated = new AtomicInteger(0);

        long testStartTime = System.currentTimeMillis();

        // Create files in batches to simulate realistic load patterns
        IntStream.range(1, numberOfFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    long fileCreationStart = System.currentTimeMillis();
                    
                    String sqlContent = generateComplexSqlScript(i);
                    Path sqlFile = sqlScriptsDir.resolve(String.format("load_test_%04d.sql", i));
                    Files.write(sqlFile, sqlContent.getBytes());
                    
                    long fileCreationTime = System.currentTimeMillis() - fileCreationStart;
                    totalCreationTime.addAndGet(fileCreationTime);
                    filesCreated.incrementAndGet();
                    creationLatch.countDown();
                    
                    // Add small random delay to simulate realistic file arrival patterns
                    Thread.sleep((long) (Math.random() * 50));
                } catch (Exception e) {
                    logger.error("Failed to create load test file", e);
                }
            });
        });

        // Wait for all files to be created
        assertThat(creationLatch.await(30, TimeUnit.SECONDS)).isTrue();
        long creationPhaseTime = System.currentTimeMillis() - testStartTime;

        logger.info("Created {} files in {} ms (avg: {} ms per file)", 
            filesCreated.get(), creationPhaseTime, totalCreationTime.get() / filesCreated.get());

        // Start file watcher and measure processing time
        long processingStartTime = System.currentTimeMillis();
        fileWatcherService.startWatching();

        // Monitor processing progress
        AtomicInteger lastReportedCount = new AtomicInteger(0);
        ScheduledExecutorService progressMonitor = Executors.newSingleThreadScheduledExecutor();
        progressMonitor.scheduleAtFixedRate(() -> {
            try {
                long remaining = Files.list(sqlScriptsDir)
                    .filter(path -> path.getFileName().toString().startsWith("load_test_"))
                    .count();
                int processed = numberOfFiles - (int) remaining;
                
                if (processed != lastReportedCount.get()) {
                    logger.info("Progress: {}/{} files processed ({:.1f}%)", 
                        processed, numberOfFiles, (processed * 100.0) / numberOfFiles);
                    lastReportedCount.set(processed);
                }
            } catch (IOException e) {
                logger.warn("Error monitoring progress", e);
            }
        }, 5, 5, TimeUnit.SECONDS);

        // Wait for all files to be processed
        await().atMost(Duration.ofMinutes(3))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                try {
                    return Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("load_test_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        progressMonitor.shutdown();
        long processingTime = System.currentTimeMillis() - processingStartTime;
        long totalTestTime = System.currentTimeMillis() - testStartTime;

        // Verify all files were processed successfully
        long completedFiles = Files.list(sqlScriptsDir.resolve("completed"))
            .filter(path -> path.getFileName().toString().startsWith("load_test_"))
            .count();
        assertThat(completedFiles).isEqualTo(numberOfFiles);

        // Verify database state
        verifyHighVolumeProcessingResults(numberOfFiles);

        // Analyze performance metrics
        ProcessingStatistics stats = metricsService.getStatistics("sql-scripts");
        double throughput = (numberOfFiles * 1000.0) / processingTime;
        
        logger.info("High-volume SQL processing load test results:");
        logger.info("  Files processed: {}", numberOfFiles);
        logger.info("  Creation time: {} ms", creationPhaseTime);
        logger.info("  Processing time: {} ms", processingTime);
        logger.info("  Total test time: {} ms", totalTestTime);
        logger.info("  Throughput: {:.2f} files/second", throughput);
        logger.info("  Average processing time per file: {:.2f} ms", (double) processingTime / numberOfFiles);
        logger.info("  Successful executions: {}", stats.getSuccessfulExecutions());
        logger.info("  Failed executions: {}", stats.getFailedExecutions());

        // Performance assertions
        assertThat(throughput).isGreaterThan(1.0); // At least 1 file per second
        assertThat(stats.getFailedExecutions()).isEqualTo(0); // No failures expected
        assertThat(stats.getSuccessfulExecutions()).isGreaterThanOrEqualTo(numberOfFiles);

        executor.shutdown();
        logger.info("High-volume SQL processing load test completed successfully");
    }

    /**
     * Test sustained load over extended period
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void testSustainedLoadProcessing() throws IOException, InterruptedException {
        logger.info("Starting sustained load processing test");

        int batchSize = 20;
        int numberOfBatches = 10;
        int delayBetweenBatches = 2000; // 2 seconds
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Long> batchProcessingTimes = new ArrayList<>();
        AtomicInteger totalFilesCreated = new AtomicInteger(0);

        // Start file watcher
        fileWatcherService.startWatching();

        long testStartTime = System.currentTimeMillis();

        // Process batches over time
        for (int batch = 1; batch <= numberOfBatches; batch++) {
            final int batchNumber = batch;
            long batchStartTime = System.currentTimeMillis();
            
            logger.info("Starting batch {} of {}", batchNumber, numberOfBatches);
            
            CountDownLatch batchLatch = new CountDownLatch(batchSize);
            
            // Create batch of files
            IntStream.range(1, batchSize + 1).forEach(i -> {
                executor.submit(() -> {
                    try {
                        int fileId = (batchNumber - 1) * batchSize + i;
                        String sqlContent = generateMediumComplexitySqlScript(fileId);
                        Path sqlFile = sqlScriptsDir.resolve(String.format("sustained_%03d_%03d.sql", batchNumber, i));
                        Files.write(sqlFile, sqlContent.getBytes());
                        totalFilesCreated.incrementAndGet();
                        batchLatch.countDown();
                    } catch (IOException e) {
                        logger.error("Failed to create sustained load test file", e);
                    }
                });
            });

            // Wait for batch creation
            assertThat(batchLatch.await(10, TimeUnit.SECONDS)).isTrue();

            // Wait for batch to be processed
            final int expectedProcessedFiles = batchNumber * batchSize;
            await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    try {
                        long completedFiles = Files.list(sqlScriptsDir.resolve("completed"))
                            .filter(path -> path.getFileName().toString().startsWith("sustained_"))
                            .count();
                        return completedFiles >= expectedProcessedFiles;
                    } catch (IOException e) {
                        return false;
                    }
                });

            long batchProcessingTime = System.currentTimeMillis() - batchStartTime;
            batchProcessingTimes.add(batchProcessingTime);
            
            logger.info("Batch {} completed in {} ms", batchNumber, batchProcessingTime);

            // Delay before next batch (except for last batch)
            if (batch < numberOfBatches) {
                Thread.sleep(delayBetweenBatches);
            }
        }

        long totalTestTime = System.currentTimeMillis() - testStartTime;

        // Verify all files were processed
        int totalExpectedFiles = numberOfBatches * batchSize;
        long totalCompletedFiles = Files.list(sqlScriptsDir.resolve("completed"))
            .filter(path -> path.getFileName().toString().startsWith("sustained_"))
            .count();
        assertThat(totalCompletedFiles).isEqualTo(totalExpectedFiles);

        // Analyze sustained load performance
        double avgBatchTime = batchProcessingTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long maxBatchTime = batchProcessingTimes.stream().mapToLong(Long::longValue).max().orElse(0L);
        long minBatchTime = batchProcessingTimes.stream().mapToLong(Long::longValue).min().orElse(0L);
        
        logger.info("Sustained load processing test results:");
        logger.info("  Total files processed: {}", totalExpectedFiles);
        logger.info("  Total test time: {} ms", totalTestTime);
        logger.info("  Average batch processing time: {:.2f} ms", avgBatchTime);
        logger.info("  Min batch processing time: {} ms", minBatchTime);
        logger.info("  Max batch processing time: {} ms", maxBatchTime);
        logger.info("  Overall throughput: {:.2f} files/second", (totalExpectedFiles * 1000.0) / totalTestTime);

        // Performance consistency assertions
        assertThat(maxBatchTime - minBatchTime).isLessThan((long)(avgBatchTime * 2)); // Reasonable consistency
        assertThat(avgBatchTime).isLessThan(15000); // Average batch should complete within 15 seconds

        executor.shutdown();
        logger.info("Sustained load processing test completed successfully");
    }

    /**
     * Test memory usage under load
     */
    @Test
    void testMemoryUsageUnderLoad() throws IOException, InterruptedException {
        logger.info("Starting memory usage under load test");

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        int numberOfFiles = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch creationLatch = new CountDownLatch(numberOfFiles);
        List<Long> memorySnapshots = new ArrayList<>();

        // Start memory monitoring
        ScheduledExecutorService memoryMonitor = Executors.newSingleThreadScheduledExecutor();
        memoryMonitor.scheduleAtFixedRate(() -> {
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            memorySnapshots.add(usedMemory);
            logger.debug("Memory usage: {} MB", usedMemory / (1024 * 1024));
        }, 0, 1, TimeUnit.SECONDS);

        // Create files with large content to test memory handling
        IntStream.range(1, numberOfFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String sqlContent = generateLargeSqlScript(i);
                    Path sqlFile = sqlScriptsDir.resolve(String.format("memory_test_%03d.sql", i));
                    Files.write(sqlFile, sqlContent.getBytes());
                    creationLatch.countDown();
                } catch (IOException e) {
                    logger.error("Failed to create memory test file", e);
                }
            });
        });

        // Wait for file creation
        assertThat(creationLatch.await(20, TimeUnit.SECONDS)).isTrue();

        // Start file watcher
        fileWatcherService.startWatching();

        // Wait for processing to complete
        await().atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                try {
                    return Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("memory_test_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        memoryMonitor.shutdown();

        // Analyze memory usage
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = memorySnapshots.stream().mapToLong(Long::longValue).max().orElse(0L);
        long avgMemory = (long) memorySnapshots.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long memoryIncrease = finalMemory - initialMemory;

        logger.info("Memory usage under load test results:");
        logger.info("  Initial memory: {} MB", initialMemory / (1024 * 1024));
        logger.info("  Final memory: {} MB", finalMemory / (1024 * 1024));
        logger.info("  Max memory during test: {} MB", maxMemory / (1024 * 1024));
        logger.info("  Average memory during test: {} MB", avgMemory / (1024 * 1024));
        logger.info("  Memory increase: {} MB", memoryIncrease / (1024 * 1024));

        // Memory usage assertions
        assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024); // Less than 100MB increase
        assertThat(maxMemory).isLessThan(500 * 1024 * 1024); // Less than 500MB max usage

        executor.shutdown();
        logger.info("Memory usage under load test completed successfully");
    }

    /**
     * Test concurrent processing with multiple processors
     */
    @Test
    void testMultiProcessorConcurrentLoad() throws IOException, InterruptedException {
        logger.info("Starting multi-processor concurrent load test");

        int sqlFiles = 30;
        int logFiles = 20;
        ExecutorService executor = Executors.newFixedThreadPool(15);
        CountDownLatch sqlLatch = new CountDownLatch(sqlFiles);
        CountDownLatch logLatch = new CountDownLatch(logFiles);

        long testStartTime = System.currentTimeMillis();

        // Create SQL files
        IntStream.range(1, sqlFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String sqlContent = generateMediumComplexitySqlScript(i);
                    Path sqlFile = sqlScriptsDir.resolve(String.format("multi_sql_%03d.sql", i));
                    Files.write(sqlFile, sqlContent.getBytes());
                    sqlLatch.countDown();
                } catch (IOException e) {
                    logger.error("Failed to create multi-processor SQL file", e);
                }
            });
        });

        // Create SQL*Loader log files
        IntStream.range(1, logFiles + 1).forEach(i -> {
            executor.submit(() -> {
                try {
                    String logContent = generateSqlLoaderLogContent(i);
                    Path logFile = sqlLoaderLogsDir.resolve(String.format("multi_log_%03d.log", i));
                    Files.write(logFile, logContent.getBytes());
                    logLatch.countDown();
                } catch (IOException e) {
                    logger.error("Failed to create multi-processor log file", e);
                }
            });
        });

        // Wait for all files to be created
        assertThat(sqlLatch.await(15, TimeUnit.SECONDS)).isTrue();
        assertThat(logLatch.await(15, TimeUnit.SECONDS)).isTrue();

        // Start file watcher
        fileWatcherService.startWatching();

        // Wait for all SQL files to be processed
        await().atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                try {
                    return Files.list(sqlScriptsDir)
                        .filter(path -> path.getFileName().toString().startsWith("multi_sql_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        // Wait for all log files to be processed
        await().atMost(Duration.ofMinutes(2))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                try {
                    return Files.list(sqlLoaderLogsDir)
                        .filter(path -> path.getFileName().toString().startsWith("multi_log_"))
                        .count() == 0;
                } catch (IOException e) {
                    return false;
                }
            });

        long totalProcessingTime = System.currentTimeMillis() - testStartTime;

        // Verify processing results
        for (int i = 1; i <= sqlFiles; i++) {
            String tableName = "multi_processor_" + i;
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Integer.class);
            assertThat(count).isGreaterThan(0);
        }

        // Verify audit records for log files
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sqlloader_audit WHERE log_filename LIKE 'multi_log_%'", 
            Integer.class);
        assertThat(auditCount).isEqualTo(logFiles);

        // Analyze multi-processor performance
        ProcessingStatistics sqlStats = metricsService.getStatistics("sql-scripts");
        ProcessingStatistics logStats = metricsService.getStatistics("sqlloader-logs");

        logger.info("Multi-processor concurrent load test results:");
        logger.info("  SQL files processed: {}", sqlFiles);
        logger.info("  Log files processed: {}", logFiles);
        logger.info("  Total processing time: {} ms", totalProcessingTime);
        logger.info("  Combined throughput: {:.2f} files/second", 
            ((sqlFiles + logFiles) * 1000.0) / totalProcessingTime);
        logger.info("  SQL processor stats: {} successful, {} failed", 
            sqlStats.getSuccessfulExecutions(), sqlStats.getFailedExecutions());
        logger.info("  Log processor stats: {} successful, {} failed", 
            logStats.getSuccessfulExecutions(), logStats.getFailedExecutions());

        executor.shutdown();
        logger.info("Multi-processor concurrent load test completed successfully");
    }

    private String generateComplexSqlScript(int fileId) {
        return String.format("""
            -- Complex SQL script for load testing - File %d
            CREATE TABLE IF NOT EXISTS load_test_table_%d (
                id INTEGER PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                email VARCHAR(255) UNIQUE,
                age INTEGER CHECK (age >= 0 AND age <= 150),
                salary DECIMAL(10,2),
                department_id INTEGER,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                status VARCHAR(20) DEFAULT 'ACTIVE'
            );
            
            CREATE INDEX IF NOT EXISTS idx_load_test_%d_name ON load_test_table_%d(name);
            CREATE INDEX IF NOT EXISTS idx_load_test_%d_email ON load_test_table_%d(email);
            CREATE INDEX IF NOT EXISTS idx_load_test_%d_dept ON load_test_table_%d(department_id);
            
            INSERT INTO load_test_table_%d (id, name, email, age, salary, department_id) VALUES 
                (%d, 'Employee %d', 'emp%d@company.com', %d, %d.00, %d),
                (%d, 'Manager %d', 'mgr%d@company.com', %d, %d.00, %d),
                (%d, 'Director %d', 'dir%d@company.com', %d, %d.00, %d);
            
            UPDATE load_test_table_%d SET updated_at = CURRENT_TIMESTAMP WHERE id = %d;
            
            -- Add some complexity with conditional logic
            INSERT INTO load_test_table_%d (id, name, email, age, salary, department_id)
            SELECT %d, 'Generated %d', 'gen%d@company.com', %d, %d.00, %d
            WHERE NOT EXISTS (SELECT 1 FROM load_test_table_%d WHERE id = %d);
            """, 
            fileId, fileId, fileId, fileId, fileId, fileId, fileId, fileId, fileId,
            fileId * 10, fileId, fileId, 25 + (fileId % 40), 50000 + (fileId * 1000), fileId % 5 + 1,
            fileId * 10 + 1, fileId, fileId, 35 + (fileId % 30), 75000 + (fileId * 1500), fileId % 5 + 1,
            fileId * 10 + 2, fileId, fileId, 45 + (fileId % 20), 100000 + (fileId * 2000), fileId % 5 + 1,
            fileId, fileId * 10,
            fileId, fileId * 10 + 3, fileId, fileId, 30 + (fileId % 35), 60000 + (fileId * 1200), fileId % 5 + 1,
            fileId, fileId * 10 + 3);
    }

    private String generateMediumComplexitySqlScript(int fileId) {
        return String.format("""
            CREATE TABLE IF NOT EXISTS multi_processor_%d (
                id INTEGER PRIMARY KEY,
                data VARCHAR(500),
                processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            INSERT INTO multi_processor_%d (id, data) VALUES 
                (%d, 'Multi-processor test data %d - %s'),
                (%d, 'Additional data %d - %s');
            """, 
            fileId, fileId, 
            fileId * 100, fileId, LocalDateTime.now().toString(),
            fileId * 100 + 1, fileId, LocalDateTime.now().toString());
    }

    private String generateLargeSqlScript(int fileId) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CREATE TABLE IF NOT EXISTS memory_test_%d (\n", fileId));
        sb.append("    id INTEGER PRIMARY KEY,\n");
        sb.append("    large_text TEXT,\n");
        sb.append("    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n");
        sb.append(");\n\n");

        // Generate large INSERT statements
        for (int i = 1; i <= 10; i++) {
            sb.append(String.format("INSERT INTO memory_test_%d (id, large_text) VALUES (%d, '%s');\n",
                fileId, fileId * 100 + i, "Large text content ".repeat(100)));
        }

        return sb.toString();
    }

    private String generateSqlLoaderLogContent(int fileId) {
        return String.format("""
            SQL*Loader: Release 19.0.0.0.0 - Production on Mon Jan 1 12:00:00 2024
            
            Control File:   multi_load_%d.ctl
            Data File:      multi_load_%d.dat
            Bad File:       multi_load_%d.bad
            Discard File:   none specified
            
            Table MULTI_LOAD_TABLE_%d, loaded from every logical record.
            Insert option in effect for this table: INSERT
            
            Table MULTI_LOAD_TABLE_%d:
              %d Rows successfully loaded.
              0 Rows not loaded due to data errors.
              0 Rows not loaded because all WHEN clauses were failed.
              0 Rows not loaded because all fields were null.
            
            Run began on Mon Jan 01 12:00:00 2024
            Run ended on Mon Jan 01 12:00:%02d 2024
            
            Elapsed time was:     00:00:%02d.%02d
            CPU time was:         00:00:01.%02d
            """, 
            fileId, fileId, fileId, fileId, fileId, fileId * 25,
            fileId % 60, fileId % 60, fileId % 100, fileId % 100);
    }

    private void verifyHighVolumeProcessingResults(int numberOfFiles) {
        for (int i = 1; i <= numberOfFiles; i++) {
            String tableName = "load_test_table_" + i;
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Integer.class);
            assertThat(count).isGreaterThan(0);
        }
    }

    private void updateWatchConfigPaths() {
        WatchConfigProperties sqlConfig = properties.getWatchConfigs().get("sql-scripts");
        if (sqlConfig != null) {
            sqlConfig.setWatchFolder(sqlScriptsDir.toString());
            sqlConfig.setCompletedFolder(sqlScriptsDir.resolve("completed").toString());
            sqlConfig.setErrorFolder(sqlScriptsDir.resolve("error").toString());
        }

        WatchConfigProperties logConfig = properties.getWatchConfigs().get("sqlloader-logs");
        if (logConfig != null) {
            logConfig.setWatchFolder(sqlLoaderLogsDir.toString());
            logConfig.setCompletedFolder(sqlLoaderLogsDir.resolve("completed").toString());
            logConfig.setErrorFolder(sqlLoaderLogsDir.resolve("error").toString());
        }
    }
}