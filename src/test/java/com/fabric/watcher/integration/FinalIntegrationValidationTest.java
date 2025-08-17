package com.fabric.watcher.integration;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple validation test to verify final integration setup works correctly.
 * 
 * This test validates that all components are properly wired and configured
 * for the final integration testing phase.
 * 
 * Requirements: 7.1, 12.1, 12.4, 6.1, 8.1, 9.1, 10.1
 */
@SpringBootTest(classes = com.fabric.watcher.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "spring.profiles.active=test"
})
class FinalIntegrationValidationTest {

    @Autowired
    private ArchiveSearchProperties archiveSearchProperties;

    /**
     * Test that the Spring Boot application context loads successfully
     * with all archive search components properly configured.
     */
    @Test
    void testApplicationContextLoadsWithArchiveSearchComponents() {
        // Verify that the application context loads successfully
        assertThat(archiveSearchProperties).isNotNull();
        
        // Verify that archive search is enabled in test environment
        assertThat(archiveSearchProperties.isEnabled()).isTrue();
        
        // Verify that basic configuration properties are loaded
        assertThat(archiveSearchProperties.getMaxFileSize()).isGreaterThan(0);
        assertThat(archiveSearchProperties.getMaxSearchResults()).isGreaterThan(0);
        assertThat(archiveSearchProperties.getSearchTimeoutSeconds()).isGreaterThan(0);
        
        // Verify that audit configuration is present
        assertThat(archiveSearchProperties.getAudit()).isNotNull();
        
        // Verify that LDAP configuration is present
        assertThat(archiveSearchProperties.getLdap()).isNotNull();
        
        // Verify that upload configuration is present
        assertThat(archiveSearchProperties.getUpload()).isNotNull();
    }

    /**
     * Test environment-based feature toggling configuration
     * Requirements: 7.1
     */
    @Test
    void testEnvironmentBasedFeatureToggling() {
        // In test environment, archive search should be enabled
        assertThat(archiveSearchProperties.isEnabled()).isTrue();
        
        // Verify that security features are properly configured
        assertThat(archiveSearchProperties.isPathTraversalProtection()).isTrue();
        assertThat(archiveSearchProperties.isFileTypeValidation()).isTrue();
        
        // Verify that audit logging is enabled
        assertThat(archiveSearchProperties.getAudit().isEnabled()).isTrue();
    }

    /**
     * Test that all required configuration sections are present
     * Requirements: 8.1, 9.1, 10.1
     */
    @Test
    void testAllRequiredConfigurationSectionsPresent() {
        // Test LDAP configuration
        assertThat(archiveSearchProperties.getLdap().getUrl()).isNotNull();
        assertThat(archiveSearchProperties.getLdap().getBaseDn()).isNotNull();
        assertThat(archiveSearchProperties.getLdap().getUserSearchBase()).isNotNull();
        assertThat(archiveSearchProperties.getLdap().getUserSearchFilter()).isNotNull();
        
        // Test upload configuration
        assertThat(archiveSearchProperties.getUpload().getUploadDirectory()).isNotNull();
        assertThat(archiveSearchProperties.getUpload().getAllowedExtensions()).isNotEmpty();
        assertThat(archiveSearchProperties.getUpload().getMaxUploadSize()).isGreaterThan(0);
        
        // Test audit configuration
        assertThat(archiveSearchProperties.getAudit().getLogFile()).isNotNull();
        assertThat(archiveSearchProperties.getAudit().getMaxFileSize()).isNotNull();
        assertThat(archiveSearchProperties.getAudit().getMaxHistory()).isGreaterThan(0);
    }

    /**
     * Test security configuration validation
     * Requirements: 6.1
     */
    @Test
    void testSecurityConfigurationValidation() {
        // Verify security settings are properly configured
        assertThat(archiveSearchProperties.isAuditLoggingEnabled()).isTrue();
        assertThat(archiveSearchProperties.isPathTraversalProtection()).isTrue();
        assertThat(archiveSearchProperties.isFileTypeValidation()).isTrue();
        
        // Verify that allowed paths are configured (even if empty for test)
        assertThat(archiveSearchProperties.getAllowedPaths()).isNotNull();
        
        // Verify that excluded paths are configured (even if empty for test)
        assertThat(archiveSearchProperties.getExcludedPaths()).isNotNull();
        
        // Verify that supported archive types are configured
        assertThat(archiveSearchProperties.getSupportedArchiveTypes()).isNotEmpty();
        assertThat(archiveSearchProperties.getSupportedArchiveTypes()).contains("zip", "tar", "jar");
    }
}