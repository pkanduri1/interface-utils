package com.fabric.watcher.health;

import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileWatcherHealthIndicatorTest {
    
    @Mock
    private FileWatcherService fileWatcherService;
    
    @Mock
    private MetricsService metricsService;
    
    private FileWatcherHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new FileWatcherHealthIndicator(fileWatcherService, metricsService);
    }
    
    @Test
    void checkHealth_WhenServiceRunning_ShouldReturnUp() {
        // Given
        when(fileWatcherService.isRunning()).thenReturn(true);
        
        Map<String, String> watchStatus = new HashMap<>();
        watchStatus.put("sql-scripts", "RUNNING");
        when(fileWatcherService.getWatchStatus()).thenReturn(watchStatus);
        
        ConcurrentMap<String, ProcessingStatistics> stats = new ConcurrentHashMap<>();
        ProcessingStatistics sqlStats = new ProcessingStatistics("sql-scripts", "sql-script");
        sqlStats.incrementTotal();
        sqlStats.incrementSuccess();
        stats.put("sql-scripts", sqlStats);
        when(metricsService.getAllStatistics()).thenReturn(stats);
        
        // When
        Map<String, Object> health = healthIndicator.checkHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("UP");
        assertThat(health.get("service")).isEqualTo("Running");
        assertThat(health.get("watchConfigurations")).isEqualTo(1);
        assertThat(health.get("totalFilesProcessed")).isEqualTo(1L);
        assertThat(health.get("totalSuccessful")).isEqualTo(1L);
        assertThat(health.get("totalFailed")).isEqualTo(0L);
        assertThat(health).containsKey("watchStatus");
        assertThat(health).containsKey("timestamp");
    }
    
    @Test
    void checkHealth_WhenServiceStopped_ShouldReturnDown() {
        // Given
        when(fileWatcherService.isRunning()).thenReturn(false);
        
        // When
        Map<String, Object> health = healthIndicator.checkHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("DOWN");
        assertThat(health.get("service")).isEqualTo("Stopped");
        assertThat(health.get("error")).isEqualTo("Service is not running");
        assertThat(health).containsKey("timestamp");
    }
    
    @Test
    void checkHealth_WhenExceptionThrown_ShouldReturnDown() {
        // Given
        RuntimeException exception = new RuntimeException("Service error");
        when(fileWatcherService.isRunning()).thenThrow(exception);
        
        // When
        Map<String, Object> health = healthIndicator.checkHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("DOWN");
        assertThat(health.get("service")).isEqualTo("Error");
        assertThat(health.get("error")).isEqualTo("Service error");
        assertThat(health.get("exception")).isEqualTo("RuntimeException");
        assertThat(health).containsKey("timestamp");
    }
}