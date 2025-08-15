package com.fabric.watcher.archive.health;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.service.ArchiveSearchMetricsService;
import com.fabric.watcher.archive.service.ArchiveSearchService;
import com.fabric.watcher.archive.security.EnvironmentGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveSearchHealthIndicatorTest {

    @Mock
    private ArchiveSearchProperties properties;

    @Mock
    private ArchiveSearchService archiveSearchService;

    @Mock
    private ArchiveSearchMetricsService metricsService;

    @Mock
    private EnvironmentGuard environmentGuard;

    private ArchiveSearchHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new ArchiveSearchHealthIndicator(
                properties, archiveSearchService, metricsService, environmentGuard);
    }

    @Test
    void shouldReturnHealthyStatusWhenAllChecksPass() {
        // Given
        when(properties.isEnabled()).thenReturn(true);
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(true);
        when(properties.getSearchTimeoutSeconds()).thenReturn(30);
        when(properties.getMaxFileSize()).thenReturn(100 * 1024 * 1024);
        when(properties.getMaxSearchResults()).thenReturn(100);
        when(properties.getSupportedArchiveTypes()).thenReturn(Arrays.asList("zip", "tar"));
        when(properties.getAllowedPaths()).thenReturn(Arrays.asList("/tmp"));
        when(properties.getExcludedPaths()).thenReturn(Arrays.asList());
        when(archiveSearchService.getApiStatus()).thenReturn(new HashMap<>());
        when(archiveSearchService.isPathAllowed("/tmp")).thenReturn(true);
        when(metricsService.getMetricsSummary()).thenReturn(new HashMap<>());
        when(metricsService.getSuccessRates()).thenReturn(new HashMap<>());

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("enabled");
        assertThat(health.getDetails()).containsKey("environment");
        assertThat(health.getDetails()).containsKey("configuration");
        assertThat(health.getDetails()).containsKey("operations");
        assertThat(health.getDetails()).containsKey("metrics");
        assertThat(health.getDetails()).containsKey("timestamp");
    }

    @Test
    void shouldReturnUnhealthyStatusWhenServiceDisabled() {
        // Given
        when(properties.isEnabled()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("issues");
        assertThat(health.getDetails().get("issues").toString()).contains("Service is disabled");
    }

    @Test
    void shouldReturnUnhealthyStatusInProductionEnvironment() {
        // Given
        when(properties.isEnabled()).thenReturn(true);
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("issues");
        assertThat(health.getDetails().get("issues").toString()).contains("Running in production environment");
    }

    @Test
    void shouldReturnUnhealthyStatusWithInvalidConfiguration() {
        // Given
        when(properties.isEnabled()).thenReturn(true);
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(true);
        when(properties.getSearchTimeoutSeconds()).thenReturn(-1); // Invalid timeout
        when(properties.getMaxFileSize()).thenReturn(100 * 1024 * 1024);
        when(properties.getMaxSearchResults()).thenReturn(100);
        when(properties.getSupportedArchiveTypes()).thenReturn(Arrays.asList("zip", "tar"));
        when(properties.getAllowedPaths()).thenReturn(Arrays.asList("/tmp"));
        when(properties.getExcludedPaths()).thenReturn(Arrays.asList());
        when(archiveSearchService.getApiStatus()).thenReturn(new HashMap<>());
        when(archiveSearchService.isPathAllowed("/tmp")).thenReturn(true);
        when(metricsService.getMetricsSummary()).thenReturn(new HashMap<>());
        when(metricsService.getSuccessRates()).thenReturn(new HashMap<>());

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("issues");
        assertThat(health.getDetails().get("issues").toString()).contains("Configuration issues");
    }

    @Test
    void shouldReturnUnhealthyStatusWithHighErrorRate() {
        // Given
        when(properties.isEnabled()).thenReturn(true);
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(true);
        when(properties.getSearchTimeoutSeconds()).thenReturn(30);
        when(properties.getMaxFileSize()).thenReturn(100 * 1024 * 1024);
        when(properties.getMaxSearchResults()).thenReturn(100);
        when(properties.getSupportedArchiveTypes()).thenReturn(Arrays.asList("zip", "tar"));
        when(properties.getAllowedPaths()).thenReturn(Arrays.asList("/tmp"));
        when(properties.getExcludedPaths()).thenReturn(Arrays.asList());
        when(archiveSearchService.getApiStatus()).thenReturn(new HashMap<>());
        when(archiveSearchService.isPathAllowed("/tmp")).thenReturn(true);

        // High error rate metrics
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("file_search.requests", 100.0);
        metrics.put("content_search.requests", 50.0);
        metrics.put("download.requests", 50.0);
        metrics.put("file_search.failure", 30.0); // 15% error rate
        metrics.put("content_search.failure", 10.0); // 20% error rate
        metrics.put("download.failure", 5.0); // 10% error rate
        // Total error rate: 45/200 = 22.5%

        when(metricsService.getMetricsSummary()).thenReturn(metrics);
        when(metricsService.getSuccessRates()).thenReturn(new HashMap<>());

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("issues");
        assertThat(health.getDetails().get("issues").toString()).contains("High error rate");
        assertThat(health.getDetails()).containsKey("errorRate");
    }

    @Test
    void shouldIncludeSecurityAlertWhenViolationsDetected() {
        // Given
        when(properties.isEnabled()).thenReturn(true);
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(true);
        when(properties.getSearchTimeoutSeconds()).thenReturn(30);
        when(properties.getMaxFileSize()).thenReturn(100 * 1024 * 1024);
        when(properties.getMaxSearchResults()).thenReturn(100);
        when(properties.getSupportedArchiveTypes()).thenReturn(Arrays.asList("zip", "tar"));
        when(properties.getAllowedPaths()).thenReturn(Arrays.asList("/tmp"));
        when(properties.getExcludedPaths()).thenReturn(Arrays.asList());
        when(archiveSearchService.getApiStatus()).thenReturn(new HashMap<>());
        when(archiveSearchService.isPathAllowed("/tmp")).thenReturn(true);

        Map<String, Double> metrics = new HashMap<>();
        metrics.put("security.violations", 5.0);
        when(metricsService.getMetricsSummary()).thenReturn(metrics);
        when(metricsService.getSuccessRates()).thenReturn(new HashMap<>());

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getDetails()).containsKey("securityAlert");
        assertThat(health.getDetails().get("securityAlert").toString()).contains("Security violations detected: 5.0");
    }

    @Test
    void shouldHandleExceptionGracefully() {
        // Given
        when(properties.isEnabled()).thenThrow(new RuntimeException("Configuration error"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails()).containsKey("exception");
        assertThat(health.getDetails().get("error")).isEqualTo("Configuration error");
        assertThat(health.getDetails().get("exception")).isEqualTo("RuntimeException");
    }

    @Test
    void shouldGetHealthSummary() {
        // Given
        when(properties.isEnabled()).thenReturn(true);
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(true);

        Map<String, Double> metrics = new HashMap<>();
        metrics.put("file_search.requests", 10.0);
        metrics.put("content_search.requests", 5.0);
        metrics.put("download.requests", 3.0);
        metrics.put("security.violations", 1.0);

        Map<String, Double> successRates = new HashMap<>();
        successRates.put("file_search.success_rate", 95.0);

        when(metricsService.getMetricsSummary()).thenReturn(metrics);
        when(metricsService.getSuccessRates()).thenReturn(successRates);

        // When
        Map<String, Object> summary = healthIndicator.getHealthSummary();

        // Then
        assertThat(summary).containsKey("status");
        assertThat(summary).containsKey("enabled");
        assertThat(summary).containsKey("environment");
        assertThat(summary).containsKey("totalRequests");
        assertThat(summary).containsKey("successRate");
        assertThat(summary).containsKey("securityViolations");
        assertThat(summary.get("totalRequests")).isEqualTo(18.0); // 10 + 5 + 3
        assertThat(summary.get("securityViolations")).isEqualTo(1.0);
    }

    @Test
    void shouldHandleHealthSummaryException() {
        // Given
        when(properties.isEnabled()).thenThrow(new RuntimeException("Error"));

        // When
        Map<String, Object> summary = healthIndicator.getHealthSummary();

        // Then
        assertThat(summary).containsKey("status");
        assertThat(summary).containsKey("error");
        assertThat(summary.get("status")).isEqualTo("ERROR");
        assertThat(summary.get("error")).isEqualTo("Error");
    }
}