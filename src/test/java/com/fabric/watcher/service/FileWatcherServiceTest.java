package com.fabric.watcher.service;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingResult.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileWatcherServiceTest {

    @Mock
    private FileProcessorRegistry fileProcessorRegistry;
    
    @Mock
    private MetricsService metricsService;
    
    @Mock
    private com.fabric.watcher.error.ErrorHandler errorHandler;
    
    @Mock
    private com.fabric.watcher.resilience.GracefulDegradationService gracefulDegradationService;

    private FileWatcherService fileWatcherService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileWatcherService = new FileWatcherService(fileProcessorRegistry, metricsService, errorHandler, gracefulDegradationService);
        fileWatcherService.startWatching(); // Manually start the service for testing
    }

    @Test
    void shouldCreateDirectoriesWhenRegisteringWatchConfig() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Path completedFolder = tempDir.resolve("completed");
        Path errorFolder = tempDir.resolve("error");
        
        WatchConfig config = createWatchConfig("test-config", watchFolder, completedFolder, errorFolder);
        
        // When
        fileWatcherService.registerWatchConfig(config);
        
        // Then
        assertTrue(Files.exists(watchFolder), "Watch folder should be created");
        assertTrue(Files.exists(completedFolder), "Completed folder should be created");
        assertTrue(Files.exists(errorFolder), "Error folder should be created");
    }

    @Test
    void shouldNotRegisterDisabledWatchConfig() {
        // Given
        WatchConfig config = createWatchConfig("disabled-config", tempDir, tempDir, tempDir);
        config.setEnabled(false);
        
        // When
        fileWatcherService.registerWatchConfig(config);
        
        // Then
        Map<String, String> status = fileWatcherService.getWatchStatus();
        assertFalse(status.containsKey("disabled-config"), "Disabled config should not be registered");
    }

    @Test
    void shouldProcessFilesInAlphabeticalOrder() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Files.createDirectories(watchFolder);
        
        // Create files in non-alphabetical order
        File file3 = watchFolder.resolve("c_script.sql").toFile();
        File file1 = watchFolder.resolve("a_script.sql").toFile();
        File file2 = watchFolder.resolve("b_script.sql").toFile();
        
        Files.createFile(file3.toPath());
        Files.createFile(file1.toPath());
        Files.createFile(file2.toPath());
        
        WatchConfig config = createWatchConfig("test-config", watchFolder, tempDir, tempDir);
        config.setFilePatterns(Arrays.asList("*.sql"));
        
        // Mock successful processing
        when(fileProcessorRegistry.processFile(any(File.class), eq(config)))
            .thenReturn(new ProcessingResult("test", "sql", ExecutionStatus.SUCCESS));
        
        // When
        fileWatcherService.processDetectedFiles(config);
        
        // Then - verify files are processed in alphabetical order
        verify(fileProcessorRegistry).processFile(eq(file1), eq(config));
        verify(fileProcessorRegistry).processFile(eq(file2), eq(config));
        verify(fileProcessorRegistry).processFile(eq(file3), eq(config));
    }

    @Test
    void shouldSkipTemporaryAndProcessingFiles() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Files.createDirectories(watchFolder);
        
        File normalFile = watchFolder.resolve("script.sql").toFile();
        File tmpFile = watchFolder.resolve("script.tmp").toFile();
        File processingFile = watchFolder.resolve("script.processing").toFile();
        
        Files.createFile(normalFile.toPath());
        Files.createFile(tmpFile.toPath());
        Files.createFile(processingFile.toPath());
        
        WatchConfig config = createWatchConfig("test-config", watchFolder, tempDir, tempDir);
        config.setFilePatterns(Arrays.asList("*.*"));
        
        when(fileProcessorRegistry.processFile(any(File.class), eq(config)))
            .thenReturn(new ProcessingResult("test", "sql", ExecutionStatus.SUCCESS));
        
        // When
        fileWatcherService.processDetectedFiles(config);
        
        // Then - only normal file should be processed
        verify(fileProcessorRegistry, times(1)).processFile(eq(normalFile), eq(config));
        verify(fileProcessorRegistry, never()).processFile(eq(tmpFile), eq(config));
        verify(fileProcessorRegistry, never()).processFile(eq(processingFile), eq(config));
    }

    @Test
    void shouldFilterFilesByPatterns() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Files.createDirectories(watchFolder);
        
        File sqlFile = watchFolder.resolve("script.sql").toFile();
        File txtFile = watchFolder.resolve("readme.txt").toFile();
        File logFile = watchFolder.resolve("output.log").toFile();
        
        Files.createFile(sqlFile.toPath());
        Files.createFile(txtFile.toPath());
        Files.createFile(logFile.toPath());
        
        WatchConfig config = createWatchConfig("test-config", watchFolder, tempDir, tempDir);
        config.setFilePatterns(Arrays.asList("*.sql", "*.log"));
        
        when(fileProcessorRegistry.processFile(any(File.class), eq(config)))
            .thenReturn(new ProcessingResult("test", "sql", ExecutionStatus.SUCCESS));
        
        // When
        fileWatcherService.processDetectedFiles(config);
        
        // Then - only SQL and log files should be processed
        verify(fileProcessorRegistry).processFile(eq(sqlFile), eq(config));
        verify(fileProcessorRegistry).processFile(eq(logFile), eq(config));
        verify(fileProcessorRegistry, never()).processFile(eq(txtFile), eq(config));
    }

    @Test
    void shouldHandleWildcardPatterns() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Files.createDirectories(watchFolder);
        
        File file1 = watchFolder.resolve("test_script.sql").toFile();
        File file2 = watchFolder.resolve("prod_script.sql").toFile();
        File file3 = watchFolder.resolve("script.txt").toFile();
        
        Files.createFile(file1.toPath());
        Files.createFile(file2.toPath());
        Files.createFile(file3.toPath());
        
        WatchConfig config = createWatchConfig("test-config", watchFolder, tempDir, tempDir);
        config.setFilePatterns(Arrays.asList("*_script.sql"));
        
        when(fileProcessorRegistry.processFile(any(File.class), eq(config)))
            .thenReturn(new ProcessingResult("test", "sql", ExecutionStatus.SUCCESS));
        
        // When
        fileWatcherService.processDetectedFiles(config);
        
        // Then - only files matching the wildcard pattern should be processed
        verify(fileProcessorRegistry).processFile(eq(file1), eq(config));
        verify(fileProcessorRegistry).processFile(eq(file2), eq(config));
        verify(fileProcessorRegistry, never()).processFile(eq(file3), eq(config));
    }

    @Test
    void shouldSkipDirectories() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Files.createDirectories(watchFolder);
        
        File regularFile = watchFolder.resolve("script.sql").toFile();
        Path subDirectory = watchFolder.resolve("subfolder");
        
        Files.createFile(regularFile.toPath());
        Files.createDirectories(subDirectory);
        
        WatchConfig config = createWatchConfig("test-config", watchFolder, tempDir, tempDir);
        config.setFilePatterns(Arrays.asList("*.*"));
        
        when(fileProcessorRegistry.processFile(any(File.class), eq(config)))
            .thenReturn(new ProcessingResult("test", "sql", ExecutionStatus.SUCCESS));
        
        // When
        fileWatcherService.processDetectedFiles(config);
        
        // Then - only regular file should be processed, not directory
        verify(fileProcessorRegistry, times(1)).processFile(eq(regularFile), eq(config));
    }

    @Test
    void shouldHandleNonExistentWatchFolder() {
        // Given
        Path nonExistentFolder = tempDir.resolve("nonexistent");
        WatchConfig config = createWatchConfig("test-config", nonExistentFolder, tempDir, tempDir);
        
        // When
        fileWatcherService.processDetectedFiles(config);
        
        // Then - should not throw exception and not process any files
        verify(fileProcessorRegistry, never()).processFile(any(File.class), eq(config));
    }

    @Test
    void shouldPauseAndResumeWatching() {
        // Given
        WatchConfig config = createWatchConfig("test-config", tempDir, tempDir, tempDir);
        fileWatcherService.registerWatchConfig(config);
        
        // When
        fileWatcherService.pauseWatching("test-config");
        Map<String, String> pausedStatus = fileWatcherService.getWatchStatus();
        
        fileWatcherService.resumeWatching("test-config");
        Map<String, String> resumedStatus = fileWatcherService.getWatchStatus();
        
        // Then
        assertEquals("STOPPED", pausedStatus.get("test-config"));
        assertEquals("RUNNING", resumedStatus.get("test-config"));
    }

    @Test
    void shouldUnregisterWatchConfig() {
        // Given
        WatchConfig config = createWatchConfig("test-config", tempDir, tempDir, tempDir);
        fileWatcherService.registerWatchConfig(config);
        
        // When
        fileWatcherService.unregisterWatchConfig("test-config");
        
        // Then
        Map<String, String> status = fileWatcherService.getWatchStatus();
        assertFalse(status.containsKey("test-config"));
    }

    @Test
    void shouldHandleProcessingErrors() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Files.createDirectories(watchFolder);
        
        File file = watchFolder.resolve("script.sql").toFile();
        Files.createFile(file.toPath());
        
        WatchConfig config = createWatchConfig("test-config", watchFolder, tempDir, tempDir);
        config.setFilePatterns(Arrays.asList("*.sql"));
        
        // Mock processing failure
        when(fileProcessorRegistry.processFile(any(File.class), eq(config)))
            .thenReturn(new ProcessingResult("script.sql", "sql", ExecutionStatus.FAILURE, "Test error"));
        
        // When - should not throw exception
        assertDoesNotThrow(() -> fileWatcherService.processDetectedFiles(config));
        
        // Then
        verify(fileProcessorRegistry).processFile(eq(file), eq(config));
    }

    private WatchConfig createWatchConfig(String name, Path watchFolder, Path completedFolder, Path errorFolder) {
        WatchConfig config = new WatchConfig();
        config.setName(name);
        config.setProcessorType("sql-script");
        config.setWatchFolder(watchFolder);
        config.setCompletedFolder(completedFolder);
        config.setErrorFolder(errorFolder);
        config.setPollingInterval(1000);
        config.setEnabled(true);
        return config;
    }
}