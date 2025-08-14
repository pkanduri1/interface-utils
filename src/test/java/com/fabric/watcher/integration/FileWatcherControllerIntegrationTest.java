package com.fabric.watcher.integration;

import com.fabric.watcher.config.ConfigurationService;
import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingStatistics;
import com.fabric.watcher.service.FileWatcherService;
import com.fabric.watcher.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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
 * Integration tests for FileWatcherController with full Spring Boot context.
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class FileWatcherControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileWatcherService fileWatcherService;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private ConfigurationService configurationService;

    private MockMvc mockMvc;
    private WatchConfig testConfig;
    private ProcessingStatistics testStatistics;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Create test configuration
        testConfig = new WatchConfig();
        testConfig.setName("integration-test-config");
        testConfig.setProcessorType("sql-script");
        testConfig.setWatchFolder(Paths.get("/test/integration/watch"));
        testConfig.setCompletedFolder(Paths.get("/test/integration/completed"));
        testConfig.setErrorFolder(Paths.get("/test/integration/error"));
        testConfig.setFilePatterns(Arrays.asList("*.sql", "*.ddl"));
        testConfig.setPollingInterval(3000L);
        testConfig.setEnabled(true);

        // Create test statistics
        testStatistics = new ProcessingStatistics("integration-test-config", "sql-script");
        testStatistics.incrementTotal();
        testStatistics.incrementTotal();
        testStatistics.incrementSuccess();
        testStatistics.incrementFailure();
        testStatistics.setCurrentStatus("SUCCESS");
        testStatistics.setLastProcessingTime(LocalDateTime.now().minusMinutes(5));
    }

    @Test
    void fullWorkflow_ShouldHandleCompleteConfigurationLifecycle() throws Exception {
        // Setup initial state
        when(configurationService.getAllConfigurations()).thenReturn(Arrays.asList(testConfig));
        when(configurationService.getEnabledConfigurations()).thenReturn(Arrays.asList(testConfig));
        when(configurationService.getConfiguration("integration-test-config")).thenReturn(testConfig);
        when(configurationService.getConfigurationCount()).thenReturn(1);
        when(configurationService.getEnabledConfigurationCount()).thenReturn(1);
        
        when(fileWatcherService.isRunning()).thenReturn(true);
        Map<String, String> watchStatus = new HashMap<>();
        watchStatus.put("integration-test-config", "RUNNING");
        when(fileWatcherService.getWatchStatus()).thenReturn(watchStatus);
        
        ConcurrentMap<String, ProcessingStatistics> allStats = new ConcurrentHashMap<>();
        allStats.put("integration-test-config", testStatistics);
        when(metricsService.getAllStatistics()).thenReturn(allStats);
        when(metricsService.getStatistics("integration-test-config")).thenReturn(testStatistics);
        
        Map<String, Double> metricsSummary = new HashMap<>();
        metricsSummary.put("files.processed", 15.0);
        metricsSummary.put("files.success", 12.0);
        metricsSummary.put("files.failed", 3.0);
        metricsSummary.put("processing.duration.mean", 250.5);
        when(metricsService.getMetricsSummary()).thenReturn(metricsSummary);

        // 1. Get initial service status
        mockMvc.perform(get("/api/file-watcher/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceRunning").value(true))
                .andExpect(jsonPath("$.totalConfigurations").value(1))
                .andExpect(jsonPath("$.watchStatus.integration-test-config").value("RUNNING"))
                .andExpect(jsonPath("$.overallSuccessRate").exists());

        // 2. Get all configurations
        mockMvc.perform(get("/api/file-watcher/configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.enabledCount").value(1))
                .andExpect(jsonPath("$.configurations[0].name").value("integration-test-config"))
                .andExpect(jsonPath("$.configurations[0].processorType").value("sql-script"));

        // 3. Get specific configuration
        mockMvc.perform(get("/api/file-watcher/configurations/integration-test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("integration-test-config"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.currentStatus").value("RUNNING"));

        // 4. Get processing history for all configurations
        mockMvc.perform(get("/api/file-watcher/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.integration-test-config").exists())
                .andExpect(jsonPath("$.metrics['files.processed']").value(15.0))
                .andExpect(jsonPath("$.summary.totalProcessed").exists())
                .andExpect(jsonPath("$.summary.successRate").exists());

        // 5. Get processing history for specific configuration
        mockMvc.perform(get("/api/file-watcher/history/integration-test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configName").value("integration-test-config"))
                .andExpect(jsonPath("$.statistics.totalFilesProcessed").exists())
                .andExpect(jsonPath("$.configuration.name").value("integration-test-config"));

        // 6. Pause the configuration
        when(fileWatcherService.pauseWatching("integration-test-config")).thenReturn(true);
        
        mockMvc.perform(post("/api/file-watcher/pause/integration-test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.action").value("pause"));

        // 7. Resume the configuration
        when(fileWatcherService.resumeWatching("integration-test-config")).thenReturn(true);
        
        mockMvc.perform(post("/api/file-watcher/resume/integration-test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.action").value("resume"));

        // Verify all service interactions
        verify(fileWatcherService, atLeastOnce()).isRunning();
        verify(fileWatcherService, atLeastOnce()).getWatchStatus();
        verify(fileWatcherService).pauseWatching("integration-test-config");
        verify(fileWatcherService).resumeWatching("integration-test-config");
        verify(metricsService, atLeastOnce()).getAllStatistics();
        verify(metricsService, atLeastOnce()).getStatistics("integration-test-config");
        verify(configurationService, atLeastOnce()).getConfiguration("integration-test-config");
    }

    @Test
    void dynamicConfigurationManagement_ShouldAddAndRemoveConfigurations() throws Exception {
        // Setup for adding new configuration
        when(configurationService.getConfiguration("new-dynamic-config")).thenReturn(null);

        // 1. Add new configuration
        Map<String, Object> newConfigRequest = new HashMap<>();
        newConfigRequest.put("name", "new-dynamic-config");
        newConfigRequest.put("processorType", "sql-script");
        newConfigRequest.put("watchFolder", "/test/dynamic/watch");
        newConfigRequest.put("completedFolder", "/test/dynamic/completed");
        newConfigRequest.put("errorFolder", "/test/dynamic/error");
        newConfigRequest.put("filePatterns", Arrays.asList("*.sql"));
        newConfigRequest.put("pollingInterval", 4000L);
        newConfigRequest.put("enabled", true);

        mockMvc.perform(post("/api/file-watcher/configurations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newConfigRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.configName").value("new-dynamic-config"))
                .andExpect(jsonPath("$.message").value("Configuration added successfully"));

        // Setup for removing configuration
        WatchConfig dynamicConfig = new WatchConfig();
        dynamicConfig.setName("new-dynamic-config");
        dynamicConfig.setProcessorType("sql-script");
        when(configurationService.getConfiguration("new-dynamic-config")).thenReturn(dynamicConfig);

        // 2. Remove the configuration
        mockMvc.perform(delete("/api/file-watcher/configurations/new-dynamic-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.configName").value("new-dynamic-config"))
                .andExpect(jsonPath("$.message").value("Configuration removed successfully"));

        // Verify service interactions
        verify(fileWatcherService).registerWatchConfig(any(WatchConfig.class));
        verify(fileWatcherService).unregisterWatchConfig("new-dynamic-config");
    }

    @Test
    void errorHandling_ShouldHandleVariousErrorScenarios() throws Exception {
        // 1. Test service error in status endpoint
        when(fileWatcherService.isRunning()).thenThrow(new RuntimeException("Service unavailable"));

        mockMvc.perform(get("/api/file-watcher/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to retrieve status: Service unavailable"))
                .andExpect(jsonPath("$.correlationId").exists());

        // 2. Test configuration not found scenarios
        when(configurationService.getConfiguration("nonexistent-config")).thenReturn(null);

        mockMvc.perform(get("/api/file-watcher/configurations/nonexistent-config"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/file-watcher/pause/nonexistent-config"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/file-watcher/resume/nonexistent-config"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/file-watcher/history/nonexistent-config"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/file-watcher/configurations/nonexistent-config"))
                .andExpect(status().isNotFound());

        // 3. Test invalid configuration creation
        Map<String, Object> invalidConfigRequest = new HashMap<>();
        invalidConfigRequest.put("processorType", "sql-script");
        // Missing required name field

        mockMvc.perform(post("/api/file-watcher/configurations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidConfigRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Configuration name is required"));

        // 4. Test duplicate configuration creation
        when(configurationService.getConfiguration("integration-test-config")).thenReturn(testConfig);

        Map<String, Object> duplicateConfigRequest = new HashMap<>();
        duplicateConfigRequest.put("name", "integration-test-config");
        duplicateConfigRequest.put("processorType", "sql-script");
        duplicateConfigRequest.put("watchFolder", "/test/duplicate");

        mockMvc.perform(post("/api/file-watcher/configurations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateConfigRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Configuration with name 'integration-test-config' already exists"));
    }

    @Test
    void pauseResumeOperations_ShouldHandleFailureScenarios() throws Exception {
        // Setup configuration exists
        when(configurationService.getConfiguration("integration-test-config")).thenReturn(testConfig);

        // 1. Test pause operation failure
        when(fileWatcherService.pauseWatching("integration-test-config")).thenReturn(false);

        mockMvc.perform(post("/api/file-watcher/pause/integration-test-config"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Configuration not active or already paused"));

        // 2. Test resume operation failure
        when(fileWatcherService.resumeWatching("integration-test-config")).thenReturn(false);

        mockMvc.perform(post("/api/file-watcher/resume/integration-test-config"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Configuration not found or already running"));

        // 3. Test service exception during pause
        when(fileWatcherService.pauseWatching("integration-test-config"))
                .thenThrow(new RuntimeException("Pause operation failed"));

        mockMvc.perform(post("/api/file-watcher/pause/integration-test-config"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Failed to pause watching: Pause operation failed"));

        // 4. Test service exception during resume
        when(fileWatcherService.resumeWatching("integration-test-config"))
                .thenThrow(new RuntimeException("Resume operation failed"));

        mockMvc.perform(post("/api/file-watcher/resume/integration-test-config"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Failed to resume watching: Resume operation failed"));
    }

    @Test
    void processingHistoryEndpoints_ShouldHandleEmptyAndNullStatistics() throws Exception {
        // 1. Test empty statistics for all configurations
        ConcurrentMap<String, ProcessingStatistics> emptyStats = new ConcurrentHashMap<>();
        when(metricsService.getAllStatistics()).thenReturn(emptyStats);
        when(metricsService.getMetricsSummary()).thenReturn(new HashMap<>());

        mockMvc.perform(get("/api/file-watcher/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics").exists())
                .andExpect(jsonPath("$.summary.totalProcessed").value(0))
                .andExpect(jsonPath("$.summary.successRate").value(0.0));

        // 2. Test null statistics for specific configuration
        when(configurationService.getConfiguration("integration-test-config")).thenReturn(testConfig);
        when(metricsService.getStatistics("integration-test-config")).thenReturn(null);
        when(fileWatcherService.getWatchStatus()).thenReturn(new HashMap<>());

        mockMvc.perform(get("/api/file-watcher/history/integration-test-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configName").value("integration-test-config"))
                .andExpect(jsonPath("$.statistics").exists())
                .andExpect(jsonPath("$.configuration.name").value("integration-test-config"));
    }

    @Test
    void responseFormat_ShouldIncludeRequiredFields() throws Exception {
        // Setup basic mocks
        when(fileWatcherService.isRunning()).thenReturn(true);
        when(fileWatcherService.getWatchStatus()).thenReturn(new HashMap<>());
        when(metricsService.getAllStatistics()).thenReturn(new ConcurrentHashMap<>());
        when(configurationService.getConfigurationCount()).thenReturn(0);
        when(configurationService.getEnabledConfigurationCount()).thenReturn(0);

        // Test that all responses include required fields
        mockMvc.perform(get("/api/file-watcher/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.serviceRunning").exists())
                .andExpect(jsonPath("$.totalConfigurations").exists())
                .andExpect(jsonPath("$.enabledConfigurations").exists())
                .andExpect(jsonPath("$.activeWatchers").exists())
                .andExpect(jsonPath("$.overallSuccessRate").exists());

        when(metricsService.getMetricsSummary()).thenReturn(new HashMap<>());

        mockMvc.perform(get("/api/file-watcher/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.statistics").exists())
                .andExpect(jsonPath("$.metrics").exists())
                .andExpect(jsonPath("$.summary").exists());

        when(configurationService.getAllConfigurations()).thenReturn(new ArrayList<>());
        when(configurationService.getEnabledConfigurations()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/file-watcher/configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.totalCount").exists())
                .andExpect(jsonPath("$.enabledCount").exists())
                .andExpect(jsonPath("$.configurations").exists());
    }
}