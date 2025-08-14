package com.fabric.watcher.resilience;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.service.MetricsService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GracefulDegradationServiceTest {
    
    @Mock
    private MetricsService metricsService;
    
    @Mock
    private CircuitBreakerService circuitBreakerService;
    
    private GracefulDegradationService gracefulDegradationService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        gracefulDegradationService = new GracefulDegradationService(metricsService, circuitBreakerService);
    }
    
    @Test
    void testEnterAndExitDegradedMode() {
        // Given
        String component = "database";
        String reason = "Connection timeout";
        
        // Initially not in degraded mode
        assertFalse(gracefulDegradationService.isInDegradedMode(component));
        assertFalse(gracefulDegradationService.isInGlobalDegradationMode());
        
        // When - enter degraded mode
        gracefulDegradationService.enterDegradedMode(component, reason);
        
        // Then
        assertTrue(gracefulDegradationService.isInDegradedMode(component));
        assertTrue(gracefulDegradationService.isInGlobalDegradationMode()); // Database triggers global
        
        verify(metricsService).incrementCounter("file_watcher.degradation.entered", 
            "component", component);
        
        // When - exit degraded mode
        gracefulDegradationService.exitDegradedMode(component);
        
        // Then
        assertFalse(gracefulDegradationService.isInDegradedMode(component));
        assertFalse(gracefulDegradationService.isInGlobalDegradationMode());
        
        verify(metricsService).incrementCounter("file_watcher.degradation.exited", 
            "component", component);
    }
    
    @Test
    void testNonDatabaseComponentDegradation() {
        // Given
        String component = "filesystem";
        String reason = "Disk full";
        
        // When - enter degraded mode for non-database component
        gracefulDegradationService.enterDegradedMode(component, reason);
        
        // Then
        assertTrue(gracefulDegradationService.isInDegradedMode(component));
        assertFalse(gracefulDegradationService.isInGlobalDegradationMode()); // Only database triggers global
    }
    
    @Test
    void testHandleDatabaseUnavailable() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Path completedFolder = tempDir.resolve("completed");
        Path errorFolder = tempDir.resolve("error");
        
        Files.createDirectories(watchFolder);
        
        WatchConfig config = new WatchConfig();
        config.setName("test-config");
        config.setWatchFolder(watchFolder);
        config.setCompletedFolder(completedFolder);
        config.setErrorFolder(errorFolder);
        
        // Create a test file
        Path testFile = watchFolder.resolve("test.sql");
        Files.write(testFile, "SELECT 1;".getBytes());
        File file = testFile.toFile();
        
        // When
        boolean result = gracefulDegradationService.handleDatabaseUnavailable(file, config);
        
        // Then
        assertTrue(result);
        assertFalse(Files.exists(testFile)); // Original file should be moved
        
        // Check queue folder was created and file was moved there
        Path queueFolder = watchFolder.getParent().resolve("queue");
        assertTrue(Files.exists(queueFolder));
        
        File[] queuedFiles = queueFolder.toFile().listFiles();
        assertNotNull(queuedFiles);
        assertEquals(1, queuedFiles.length);
        assertTrue(queuedFiles[0].getName().endsWith("_test.sql"));
        
        verify(metricsService).incrementCounter("file_watcher.files.queued", 
            "config", "test-config", "reason", "database_unavailable");
    }
    
    @Test
    void testProcessQueuedFilesWhenDatabaseAvailable() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Path queueFolder = tempDir.resolve("queue");
        
        Files.createDirectories(watchFolder);
        Files.createDirectories(queueFolder);
        
        WatchConfig config = new WatchConfig();
        config.setName("test-config");
        config.setWatchFolder(watchFolder);
        
        // Create queued files
        Files.write(queueFolder.resolve("20231201_120000_file1.sql"), "SELECT 1;".getBytes());
        Files.write(queueFolder.resolve("20231201_120100_file2.sql"), "SELECT 2;".getBytes());
        
        // Database is not in degraded mode
        assertFalse(gracefulDegradationService.isInDegradedMode("database"));
        
        // When
        int processedCount = gracefulDegradationService.processQueuedFiles(config);
        
        // Then
        assertEquals(2, processedCount);
        
        // Files should be moved back to watch folder
        assertTrue(Files.exists(watchFolder.resolve("file1.sql")));
        assertTrue(Files.exists(watchFolder.resolve("file2.sql")));
        
        // Queue folder should be empty
        File[] remainingFiles = queueFolder.toFile().listFiles();
        assertNotNull(remainingFiles);
        assertEquals(0, remainingFiles.length);
        
        verify(metricsService, times(2)).incrementCounter("file_watcher.files.restored_from_queue", 
            "config", "test-config");
    }
    
    @Test
    void testProcessQueuedFilesWhenDatabaseUnavailable() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        Path queueFolder = tempDir.resolve("queue");
        
        Files.createDirectories(watchFolder);
        Files.createDirectories(queueFolder);
        
        WatchConfig config = new WatchConfig();
        config.setName("test-config");
        config.setWatchFolder(watchFolder);
        
        // Create queued file
        Files.write(queueFolder.resolve("20231201_120000_file1.sql"), "SELECT 1;".getBytes());
        
        // Database is in degraded mode
        gracefulDegradationService.enterDegradedMode("database", "Connection failed");
        
        // When
        int processedCount = gracefulDegradationService.processQueuedFiles(config);
        
        // Then
        assertEquals(0, processedCount); // Should not process when database unavailable
        
        // File should remain in queue
        assertTrue(Files.exists(queueFolder.resolve("20231201_120000_file1.sql")));
    }
    
    @Test
    void testCheckSystemHealth() {
        // Given
        when(circuitBreakerService.isDatabaseAvailable()).thenReturn(false);
        when(circuitBreakerService.isFileSystemAvailable()).thenReturn(true);
        when(circuitBreakerService.isExternalSystemAvailable()).thenReturn(true);
        
        // When
        gracefulDegradationService.checkSystemHealth();
        
        // Then
        assertTrue(gracefulDegradationService.isInDegradedMode("database"));
        assertFalse(gracefulDegradationService.isInDegradedMode("filesystem"));
        assertFalse(gracefulDegradationService.isInDegradedMode("external"));
        
        // When - database becomes available
        when(circuitBreakerService.isDatabaseAvailable()).thenReturn(true);
        gracefulDegradationService.checkSystemHealth();
        
        // Then
        assertFalse(gracefulDegradationService.isInDegradedMode("database"));
    }
    
    @Test
    void testGetDegradationStates() {
        // Given
        gracefulDegradationService.enterDegradedMode("database", "Connection failed");
        gracefulDegradationService.enterDegradedMode("filesystem", "Disk full");
        
        // When
        Map<String, GracefulDegradationService.DegradationState> states = 
            gracefulDegradationService.getDegradationStates();
        
        // Then
        assertEquals(2, states.size());
        assertTrue(states.containsKey("database"));
        assertTrue(states.containsKey("filesystem"));
        
        GracefulDegradationService.DegradationState dbState = states.get("database");
        assertTrue(dbState.isDegraded());
        assertEquals("database", dbState.getComponent());
        assertEquals("Connection failed", dbState.getDegradationReason());
        assertNotNull(dbState.getDegradationStartTime());
    }
    
    @Test
    void testDegradationStateDuration() throws InterruptedException {
        // Given
        gracefulDegradationService.enterDegradedMode("database", "Test");
        
        // Wait a bit
        Thread.sleep(100);
        
        // When
        Map<String, GracefulDegradationService.DegradationState> states = 
            gracefulDegradationService.getDegradationStates();
        
        // Then
        GracefulDegradationService.DegradationState state = states.get("database");
        assertTrue(state.getDegradationDurationSeconds() >= 0);
        
        // When - exit degraded mode
        gracefulDegradationService.exitDegradedMode("database");
        
        // Then
        assertFalse(state.isDegraded());
        assertEquals(0, state.getDegradationDurationSeconds());
    }
    
    @Test
    void testRepeatedEnterDegradedMode() {
        // Given
        String component = "database";
        String reason1 = "First failure";
        String reason2 = "Second failure";
        
        // When - enter degraded mode multiple times
        gracefulDegradationService.enterDegradedMode(component, reason1);
        gracefulDegradationService.enterDegradedMode(component, reason2);
        
        // Then - should only record one entry
        verify(metricsService, times(1)).incrementCounter("file_watcher.degradation.entered", 
            "component", component);
        
        assertTrue(gracefulDegradationService.isInDegradedMode(component));
    }
    
    @Test
    void testExitDegradedModeWhenNotDegraded() {
        // Given
        String component = "database";
        
        // When - try to exit degraded mode when not in it
        gracefulDegradationService.exitDegradedMode(component);
        
        // Then - should not record exit metric
        verify(metricsService, never()).incrementCounter("file_watcher.degradation.exited", 
            "component", component);
    }
    
    @Test
    void testHandleDatabaseUnavailableIOException() throws IOException {
        // Given
        Path watchFolder = tempDir.resolve("watch");
        // Don't create the watch folder to simulate IO error
        
        WatchConfig config = new WatchConfig();
        config.setName("test-config");
        config.setWatchFolder(watchFolder);
        
        File file = new File(watchFolder.toFile(), "test.sql");
        
        // When
        boolean result = gracefulDegradationService.handleDatabaseUnavailable(file, config);
        
        // Then
        assertFalse(result); // Should fail due to IO error
        
        verify(metricsService).incrementCounter("file_watcher.files.queue_failed", 
            "config", "test-config");
    }
}