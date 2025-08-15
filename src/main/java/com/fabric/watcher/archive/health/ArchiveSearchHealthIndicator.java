package com.fabric.watcher.archive.health;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.service.ArchiveSearchMetricsService;
import com.fabric.watcher.archive.service.ArchiveSearchService;
import com.fabric.watcher.archive.security.EnvironmentGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for the archive search functionality.
 * Provides comprehensive health status including configuration validation,
 * path accessibility, and operational metrics.
 */
@Component
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class ArchiveSearchHealthIndicator implements HealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(ArchiveSearchHealthIndicator.class);
    
    private final ArchiveSearchProperties properties;
    private final ArchiveSearchService archiveSearchService;
    private final ArchiveSearchMetricsService metricsService;
    private final EnvironmentGuard environmentGuard;
    
    public ArchiveSearchHealthIndicator(ArchiveSearchProperties properties,
                                      ArchiveSearchService archiveSearchService,
                                      ArchiveSearchMetricsService metricsService,
                                      EnvironmentGuard environmentGuard) {
        this.properties = properties;
        this.archiveSearchService = archiveSearchService;
        this.metricsService = metricsService;
        this.environmentGuard = environmentGuard;
    }
    
    @Override
    public Health health() {
        try {
            logger.debug("Performing archive search health check");
            
            Map<String, Object> details = new HashMap<>();
            boolean isHealthy = true;
            StringBuilder issues = new StringBuilder();
            
            // Check if service is enabled
            if (!properties.isEnabled()) {
                isHealthy = false;
                issues.append("Service is disabled; ");
                details.put("enabled", false);
            } else {
                details.put("enabled", true);
            }
            
            // Check environment
            try {
                boolean isNonProd = environmentGuard.isNonProductionEnvironment();
                details.put("environment", isNonProd ? "non-production" : "production");
                details.put("environmentValid", isNonProd);
                
                if (!isNonProd) {
                    isHealthy = false;
                    issues.append("Running in production environment; ");
                }
            } catch (Exception e) {
                isHealthy = false;
                issues.append("Environment detection failed; ");
                details.put("environmentError", e.getMessage());
                logger.warn("Environment detection failed during health check", e);
            }
            
            // Check configuration
            Map<String, Object> configHealth = checkConfiguration();
            details.put("configuration", configHealth);
            if (!(Boolean) configHealth.get("valid")) {
                isHealthy = false;
                issues.append("Configuration issues; ");
            }
            
            // Check path accessibility
            Map<String, Object> pathHealth = checkPathAccessibility();
            details.put("paths", pathHealth);
            if (!(Boolean) pathHealth.get("allAccessible")) {
                // Path issues are warnings, not critical failures
                details.put("pathWarnings", pathHealth.get("issues"));
            }
            
            // Add operational metrics
            Map<String, Object> operationalHealth = getOperationalHealth();
            details.put("operations", operationalHealth);
            
            // Add performance metrics
            Map<String, Double> performanceMetrics = metricsService.getMetricsSummary();
            details.put("metrics", performanceMetrics);
            
            // Add success rates
            Map<String, Double> successRates = metricsService.getSuccessRates();
            details.put("successRates", successRates);
            
            // Check for concerning error rates
            double totalRequests = performanceMetrics.getOrDefault("file_search.requests", 0.0) +
                                 performanceMetrics.getOrDefault("content_search.requests", 0.0) +
                                 performanceMetrics.getOrDefault("download.requests", 0.0);
            
            if (totalRequests > 100) { // Only check error rates if we have significant traffic
                double errorRate = (performanceMetrics.getOrDefault("file_search.failure", 0.0) +
                                  performanceMetrics.getOrDefault("content_search.failure", 0.0) +
                                  performanceMetrics.getOrDefault("download.failure", 0.0)) / totalRequests * 100;
                
                details.put("errorRate", errorRate);
                
                if (errorRate > 10.0) { // More than 10% error rate
                    isHealthy = false;
                    issues.append("High error rate (").append(String.format("%.1f", errorRate)).append("%); ");
                }
            }
            
            // Check for security violations
            double securityViolations = performanceMetrics.getOrDefault("security.violations", 0.0);
            if (securityViolations > 0) {
                details.put("securityAlert", "Security violations detected: " + securityViolations);
            }
            
            // Add timestamp
            details.put("timestamp", System.currentTimeMillis());
            details.put("version", "1.0.0");
            
            if (isHealthy) {
                logger.debug("Archive search health check passed");
                return Health.up()
                        .withDetails(details)
                        .build();
            } else {
                String issuesSummary = issues.toString();
                logger.warn("Archive search health check failed: {}", issuesSummary);
                return Health.down()
                        .withDetail("issues", issuesSummary)
                        .withDetails(details)
                        .build();
            }
            
        } catch (Exception e) {
            logger.error("Archive search health check failed with exception", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("exception", e.getClass().getSimpleName())
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
        }
    }
    
    /**
     * Check configuration validity.
     */
    private Map<String, Object> checkConfiguration() {
        Map<String, Object> configHealth = new HashMap<>();
        boolean isValid = true;
        Map<String, String> issues = new HashMap<>();
        
        // Check timeout configuration
        if (properties.getSearchTimeoutSeconds() <= 0) {
            isValid = false;
            issues.put("searchTimeout", "Invalid timeout value: " + properties.getSearchTimeoutSeconds());
        }
        
        // Check max file size
        if (properties.getMaxFileSize() <= 0) {
            isValid = false;
            issues.put("maxFileSize", "Invalid max file size: " + properties.getMaxFileSize());
        }
        
        // Check max search results
        if (properties.getMaxSearchResults() <= 0) {
            isValid = false;
            issues.put("maxSearchResults", "Invalid max search results: " + properties.getMaxSearchResults());
        }
        
        // Check supported archive types
        if (properties.getSupportedArchiveTypes().isEmpty()) {
            isValid = false;
            issues.put("supportedArchiveTypes", "No supported archive types configured");
        }
        
        configHealth.put("valid", isValid);
        configHealth.put("searchTimeoutSeconds", properties.getSearchTimeoutSeconds());
        configHealth.put("maxFileSize", properties.getMaxFileSize());
        configHealth.put("maxSearchResults", properties.getMaxSearchResults());
        configHealth.put("supportedArchiveTypes", properties.getSupportedArchiveTypes());
        
        if (!issues.isEmpty()) {
            configHealth.put("issues", issues);
        }
        
        return configHealth;
    }
    
    /**
     * Check accessibility of configured paths.
     */
    private Map<String, Object> checkPathAccessibility() {
        Map<String, Object> pathHealth = new HashMap<>();
        Map<String, String> accessiblePaths = new HashMap<>();
        Map<String, String> inaccessiblePaths = new HashMap<>();
        
        // Check allowed paths
        for (String allowedPath : properties.getAllowedPaths()) {
            try {
                Path path = Paths.get(allowedPath);
                if (Files.exists(path) && Files.isReadable(path)) {
                    accessiblePaths.put(allowedPath, "accessible");
                } else if (!Files.exists(path)) {
                    inaccessiblePaths.put(allowedPath, "path does not exist");
                } else {
                    inaccessiblePaths.put(allowedPath, "path is not readable");
                }
            } catch (Exception e) {
                inaccessiblePaths.put(allowedPath, "error: " + e.getMessage());
            }
        }
        
        pathHealth.put("allowedPaths", properties.getAllowedPaths().size());
        pathHealth.put("accessiblePaths", accessiblePaths.size());
        pathHealth.put("inaccessiblePaths", inaccessiblePaths.size());
        pathHealth.put("allAccessible", inaccessiblePaths.isEmpty());
        
        if (!accessiblePaths.isEmpty()) {
            pathHealth.put("accessible", accessiblePaths);
        }
        
        if (!inaccessiblePaths.isEmpty()) {
            pathHealth.put("issues", inaccessiblePaths);
        }
        
        return pathHealth;
    }
    
    /**
     * Get operational health information.
     */
    private Map<String, Object> getOperationalHealth() {
        Map<String, Object> operational = new HashMap<>();
        
        try {
            // Test basic service functionality
            Object apiStatus = archiveSearchService.getApiStatus();
            operational.put("apiStatus", "available");
            operational.put("serviceResponsive", true);
            
            // Check if service can validate paths (basic functionality test)
            boolean canValidatePaths = archiveSearchService.isPathAllowed("/tmp");
            operational.put("pathValidationWorking", canValidatePaths);
            
        } catch (Exception e) {
            operational.put("apiStatus", "error");
            operational.put("serviceResponsive", false);
            operational.put("error", e.getMessage());
            logger.warn("Error checking operational health", e);
        }
        
        return operational;
    }
    
    /**
     * Get a summary of the current health status.
     * This method can be called independently for monitoring purposes.
     */
    public Map<String, Object> getHealthSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            Health health = health();
            summary.put("status", health.getStatus().getCode());
            summary.put("enabled", properties.isEnabled());
            summary.put("environment", environmentGuard.isNonProductionEnvironment() ? "non-production" : "production");
            
            // Add key metrics
            Map<String, Double> metrics = metricsService.getMetricsSummary();
            summary.put("totalRequests", 
                metrics.getOrDefault("file_search.requests", 0.0) +
                metrics.getOrDefault("content_search.requests", 0.0) +
                metrics.getOrDefault("download.requests", 0.0));
            
            summary.put("successRate", metricsService.getSuccessRates());
            summary.put("securityViolations", metrics.getOrDefault("security.violations", 0.0));
            
        } catch (Exception e) {
            summary.put("status", "ERROR");
            summary.put("error", e.getMessage());
        }
        
        return summary;
    }
}