package com.fabric.watcher.controller;

import com.fabric.watcher.config.ConfigurationService;
import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for FileWatcherController REST API endpoints.
 */
@WebMvcTest(FileWatcherController.class)
class FileWatcherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileWatcherService fileWatcherService;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private ConfigurationService configurationService;

    private WatchConfig testConfig;
    private ProcessingStatistics testStatistics;

    @BeforeEach
    void setUp() {
        // Create test configuration
        testConfig = new WatchConfig();
        testConfig.setName("test-config");
        testConfig.setProcessorType("sql-script");
        testConfig.setWatchFolder(Paths.get("/test/watch"));
        testConfig.setCompletedFolder(Paths.get("/test/completed"));
        testConfig.setErrorFolder(Paths.get("/test/error"));
        testConfig.setFilePatterns(Arrays.asList("*.sql"));
        testConfig.setPollingInterval(5000L);
        testConfig.setEnabled(true);

        // Create test statistics
        testStatistics = new ProcessingStatistics("test-config", "sql-script");
        testStatistics.incrementTotal();
        testStatistics.incrementSuccess();
        testStatistics.setCurrentStatus("SUCCESS");
        testStatistics.setLastProcessingTime(LocalDateTime.now());
    }

    @Test
    void getStatus_ShouldReturnServiceStatus() throws Exception {
        // Arrange
        when(fileWatcherService.isRunning()).thenReturn(true);
        
        Map<String, String> watchStatus = new HashMap<>();
        watchStatus.put("test-config", "RUNNING");
        when(fileWatcherService.getWatchStatus()).thenReturn(watchStatus);
        
        ConcurrentMap<String, ProcessingStatistics> allStats = new ConcurrentHashMap<>();
        allStats.put("test-config", testStatistics);
        when(metricsService.getAllStatistics()).thenReturn(allStats);
        
        when(configurationService.getConfigurationCount()).thenReturn(1);
        when(configurationService.getEnabledConfigurationCount()).thenReturn(1);

        // Act & Assert
        mockMvc.perform(get("/api/file-watcher/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceRunning").value(true))
                .andExpect(jsonPath("$.totalConfigurations").value(1))
                .andExpect(jsonPath("$.enabledConfigurations").value(1))
                .andExpect(jsonPath("$.activeWatchers").value(1))
                .andExpect(jsonPath("$.watchStatus.test-config").value("RUNNING"))
                .andExpect(jsonPath("$.statistics.test-config").exists())
                .andExpect(jsonPath("$.overallSuccessRate").exists())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(fileWatcherService).isRunning();
        verify(fileWatcherService).getWatchStatus();
        verify(metricsService).getAllStatistics();
        verify(configurationService).getConfigurationCount();
        verify(configurationService).getEnabledConfigurationCount();
    }

    @Test
    void getStatus_ShouldHandleServiceError() throws Exception {
        // Arrange
        when(fileWatcherService.isRunning()).thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        mockMvc.perform(get("/api/file-watcher/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to retrieve status: Service error"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void pauseWatching_ShouldPauseConfiguration() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("test-config")).thenReturn(testConfig);
        when(fileWatcherService.pauseWatching("test-config")).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/file-watcher/pause/test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.configName").value("test-config"))
                .andExpect(jsonPath("$.action").value("pause"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(configurationService).getConfiguration("test-config");
        verify(fileWatcherService).pauseWatching("test-config");
    }

    @Test
    void pauseWatching_ShouldReturnNotFoundForInvalidConfig() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("invalid-config")).thenReturn(null);

        // Act & Assert
        mockMvc.perform(post("/api/file-watcher/pause/invalid-config"))
                .andExpect(status().isNotFound());

        verify(configurationService).getConfiguration("invalid-config");
        verify(fileWatcherService, never()).pauseWatching(anyString());
    }

    @Test
    void pauseWatching_ShouldReturnBadRequestWhenPauseFails() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("test-config")).thenReturn(testConfig);
        when(fileWatcherService.pauseWatching("test-config")).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/file-watcher/pause/test-config"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Configuration not active or already paused"));
    }

    @Test
    void resumeWatching_ShouldResumeConfiguration() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("test-config")).thenReturn(testConfig);
        when(fileWatcherService.resumeWatching("test-config")).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/file-watcher/resume/test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.configName").value("test-config"))
                .andExpect(jsonPath("$.action").value("resume"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(configurationService).getConfiguration("test-config");
        verify(fileWatcherService).resumeWatching("test-config");
    }

    @Test
    void resumeWatching_ShouldReturnNotFoundForInvalidConfig() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("invalid-config")).thenReturn(null);

        // Act & Assert
        mockMvc.perform(post("/api/file-watcher/resume/invalid-config"))
                .andExpect(status().isNotFound());

        verify(configurationService).getConfiguration("invalid-config");
        verify(fileWatcherService, never()).resumeWatching(anyString());
    }

    @Test
    void getProcessingHistory_ShouldReturnHistoryForAllConfigurations() throws Exception {
        // Arrange
        ConcurrentMap<String, ProcessingStatistics> allStats = new ConcurrentHashMap<>();
        allStats.put("test-config", testStatistics);
        when(metricsService.getAllStatistics()).thenReturn(allStats);

        Map<String, Double> metricsSummary = new HashMap<>();
        metricsSummary.put("files.processed", 10.0);
        metricsSummary.put("files.success", 8.0);
        metricsSummary.put("files.failed", 2.0);
        when(metricsService.getMetricsSummary()).thenReturn(metricsSummary);

        // Act & Assert
        mockMvc.perform(get("/api/file-watcher/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.test-config").exists())
                .andExpect(jsonPath("$.metrics['files.processed']").value(10.0))
                .andExpect(jsonPath("$.summary.totalProcessed").exists())
                .andExpect(jsonPath("$.summary.totalSuccess").exists())
                .andExpect(jsonPath("$.summary.successRate").exists())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(metricsService).getAllStatistics();
        verify(metricsService).getMetricsSummary();
    }

    @Test
    void getProcessingHistoryForConfig_ShouldReturnHistoryForSpecificConfiguration() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("test-config")).thenReturn(testConfig);
        when(metricsService.getStatistics("test-config")).thenReturn(testStatistics);
        
        Map<String, String> watchStatus = new HashMap<>();
        watchStatus.put("test-config", "RUNNING");
        when(fileWatcherService.getWatchStatus()).thenReturn(watchStatus);

        // Act & Assert
        mockMvc.perform(get("/api/file-watcher/history/test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configName").value("test-config"))
                .andExpect(jsonPath("$.statistics").exists())
                .andExpect(jsonPath("$.configuration.name").value("test-config"))
                .andExpect(jsonPath("$.configuration.processorType").value("sql-script"))
                .andExpect(jsonPath("$.currentStatus").value("RUNNING"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(configurationService).getConfiguration("test-config");
        verify(metricsService).getStatistics("test-config");
        verify(fileWatcherService).getWatchStatus();
    }

    @Test
    void getProcessingHistoryForConfig_ShouldReturnNotFoundForInvalidConfig() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("invalid-config")).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/file-watcher/history/invalid-config"))
                .andExpect(status().isNotFound());

        verify(configurationService).getConfiguration("invalid-config");
        verify(metricsService, never()).getStatistics(anyString());
    }

    @Test
    void getConfigurations_ShouldReturnAllConfigurations() throws Exception {
        // Arrange
        List<WatchConfig> allConfigs = Arrays.asList(testConfig);
        List<WatchConfig> enabledConfigs = Arrays.asList(testConfig);
        
        when(configurationService.getAllConfigurations()).thenReturn(allConfigs);
        when(configurationService.getEnabledConfigurations()).thenReturn(enabledConfigs);
        
        Map<String, String> watchStatus = new HashMap<>();
        watchStatus.put("test-config", "RUNNING");
        when(fileWatcherService.getWatchStatus()).thenReturn(watchStatus);

        // Act & Assert
        mockMvc.perform(get("/api/file-watcher/configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.enabledCount").value(1))
                .andExpect(jsonPath("$.configurations").isArray())
                .andExpect(jsonPath("$.configurations[0].name").value("test-config"))
                .andExpect(jsonPath("$.configurations[0].processorType").value("sql-script"))
                .andExpect(jsonPath("$.configurations[0].enabled").value(true))
                .andExpect(jsonPath("$.configurations[0].currentStatus").value("RUNNING"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(configurationService).getAllConfigurations();
        verify(configurationService).getEnabledConfigurations();
        verify(fileWatcherService).getWatchStatus();
    }

    @Test
    void getConfiguration_ShouldReturnSpecificConfiguration() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("test-config")).thenReturn(testConfig);
        when(metricsService.getStatistics("test-config")).thenReturn(testStatistics);
        
        Map<String, String> watchStatus = new HashMap<>();
        watchStatus.put("test-config", "RUNNING");
        when(fileWatcherService.getWatchStatus()).thenReturn(watchStatus);

        // Act & Assert
        mockMvc.perform(get("/api/file-watcher/configurations/test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("test-config"))
                .andExpect(jsonPath("$.processorType").value("sql-script"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.currentStatus").value("RUNNING"))
                .andExpect(jsonPath("$.statistics").exists())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(configurationService).getConfiguration("test-config");
        verify(metricsService).getStatistics("test-config");
        verify(fileWatcherService).getWatchStatus();
    }

    @Test
    void getConfiguration_ShouldReturnNotFoundForInvalidConfig() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("invalid-config")).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/file-watcher/configurations/invalid-config"))
                .andExpect(status().isNotFound());

        verify(configurationService).getConfiguration("invalid-config");
    }

    @Test
    void addWatchConfig_ShouldAddNewConfiguration() throws Exception {
        // Arrange
        Map<String, Object> configRequest = new HashMap<>();
        configRequest.put("name", "new-config");
        configRequest.put("processorType", "sql-script");
        configRequest.put("watchFolder", "/test/new-watch");
        configRequest.put("completedFolder", "/test/new-completed");
        configRequest.put("errorFolder", "/test/new-error");
        configRequest.put("filePatterns", Arrays.asList("*.sql"));
        configRequest.put("pollingInterval", 5000L);
        configRequest.put("enabled", true);

        when(configurationService.getConfiguration("new-config")).thenReturn(null);

        // Act & Assert
        mockMvc.perform(post("/api/file-watcher/configurations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(configRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Configuration added successfully"))
                .andExpect(jsonPath("$.configName").value("new-config"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(configurationService).getConfiguration("new-config");
        verify(fileWatcherService).registerWatchConfig(any(WatchConfig.class));
    }

    @Test
    void addWatchConfig_ShouldReturnBadRequestForMissingName() throws Exception {
        // Arrange
        Map<String, Object> configRequest = new HashMap<>();
        configRequest.put("processorType", "sql-script");
        configRequest.put("watchFolder", "/test/new-watch");

        // Act & Assert
        mockMvc.perform(post("/api/file-watcher/configurations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(configRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Configuration name is required"));

        verify(fileWatcherService, never()).registerWatchConfig(any(WatchConfig.class));
    }

    @Test
    void addWatchConfig_ShouldReturnBadRequestForExistingConfiguration() throws Exception {
        // Arrange
        Map<String, Object> configRequest = new HashMap<>();
        configRequest.put("name", "test-config");
        configRequest.put("processorType", "sql-script");
        configRequest.put("watchFolder", "/test/watch");

        when(configurationService.getConfiguration("test-config")).thenReturn(testConfig);

        // Act & Assert
        mockMvc.perform(post("/api/file-watcher/configurations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(configRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Configuration with name 'test-config' already exists"));

        verify(configurationService).getConfiguration("test-config");
        verify(fileWatcherService, never()).registerWatchConfig(any(WatchConfig.class));
    }

    @Test
    void removeWatchConfig_ShouldRemoveConfiguration() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("test-config")).thenReturn(testConfig);

        // Act & Assert
        mockMvc.perform(delete("/api/file-watcher/configurations/test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Configuration removed successfully"))
                .andExpect(jsonPath("$.configName").value("test-config"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(configurationService).getConfiguration("test-config");
        verify(fileWatcherService).unregisterWatchConfig("test-config");
    }

    @Test
    void removeWatchConfig_ShouldReturnNotFoundForInvalidConfig() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("invalid-config")).thenReturn(null);

        // Act & Assert
        mockMvc.perform(delete("/api/file-watcher/configurations/invalid-config"))
                .andExpect(status().isNotFound());

        verify(configurationService).getConfiguration("invalid-config");
        verify(fileWatcherService, never()).unregisterWatchConfig(anyString());
    }

    @Test
    void removeWatchConfig_ShouldHandleServiceError() throws Exception {
        // Arrange
        when(configurationService.getConfiguration("test-config")).thenReturn(testConfig);
        doThrow(new RuntimeException("Service error")).when(fileWatcherService).unregisterWatchConfig("test-config");

        // Act & Assert
        mockMvc.perform(delete("/api/file-watcher/configurations/test-config"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Failed to remove configuration: Service error"));

        verify(configurationService).getConfiguration("test-config");
        verify(fileWatcherService).unregisterWatchConfig("test-config");
    }
}