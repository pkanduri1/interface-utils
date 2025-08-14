package com.fabric.watcher.archive.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentGuardTest {
    
    @Mock
    private Environment environment;
    
    private EnvironmentGuard environmentGuard;
    
    // Store original system properties and environment variables for cleanup
    private Map<String, String> originalSystemProperties;
    private String originalNodeEnv;
    
    @BeforeEach
    void setUp() {
        // Store original values for cleanup
        originalSystemProperties = new HashMap<>();
        originalSystemProperties.put("spring.profiles.active", System.getProperty("spring.profiles.active"));
        originalSystemProperties.put("environment", System.getProperty("environment"));
        
        environmentGuard = new EnvironmentGuard(environment);
    }
    
    @AfterEach
    void tearDown() {
        // Restore original system properties
        for (Map.Entry<String, String> entry : originalSystemProperties.entrySet()) {
            if (entry.getValue() != null) {
                System.setProperty(entry.getKey(), entry.getValue());
            } else {
                System.clearProperty(entry.getKey());
            }
        }
    }
    
    @Test
    void testIsProductionEnvironment_ProductionProfile_ReturnsTrue() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        
        assertTrue(environmentGuard.isProductionEnvironment());
        assertFalse(environmentGuard.isNonProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_ProductionProfileVariations_ReturnsTrue() {
        // Test different production profile names
        String[] productionProfiles = {"prod", "production", "live", "prd"};
        
        for (String profile : productionProfiles) {
            when(environment.getActiveProfiles()).thenReturn(new String[]{profile});
            assertTrue(environmentGuard.isProductionEnvironment(), 
                "Should detect production for profile: " + profile);
        }
    }
    
    @Test
    void testIsProductionEnvironment_CaseInsensitiveProfiles_ReturnsTrue() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"PROD"});
        
        assertTrue(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_MultipleProfilesWithProduction_ReturnsTrue() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "prod", "test"});
        
        assertTrue(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_DevelopmentProfile_ReturnsFalse() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        
        assertFalse(environmentGuard.isProductionEnvironment());
        assertTrue(environmentGuard.isNonProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_NoActiveProfiles_ChecksDefaultProfiles() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{"prod"});
        
        assertTrue(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_NoProfilesAtAll_ReturnsFalse() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{});
        
        assertFalse(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_SystemPropertyProfile_ReturnsTrue() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{});
        
        System.setProperty("spring.profiles.active", "prod,other");
        
        assertTrue(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_SystemPropertyEnvironment_ReturnsTrue() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{});
        
        System.setProperty("environment", "production");
        
        assertTrue(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testValidateEnvironment_ProductionWithArchiveSearchEnabled_ThrowsException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(environmentGuard, "archiveSearchEnabled", true);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> environmentGuard.validateEnvironment());
        
        assertTrue(exception.getMessage().contains("Archive search functionality is enabled in a production environment"));
    }
    
    @Test
    void testValidateEnvironment_ProductionWithArchiveSearchDisabled_NoException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(environmentGuard, "archiveSearchEnabled", false);
        
        assertDoesNotThrow(() -> environmentGuard.validateEnvironment());
    }
    
    @Test
    void testValidateEnvironment_NonProductionWithArchiveSearchEnabled_NoException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        ReflectionTestUtils.setField(environmentGuard, "archiveSearchEnabled", true);
        
        assertDoesNotThrow(() -> environmentGuard.validateEnvironment());
    }
    
    @Test
    void testValidateEnvironment_NonProductionWithArchiveSearchDisabled_NoException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        ReflectionTestUtils.setField(environmentGuard, "archiveSearchEnabled", false);
        
        assertDoesNotThrow(() -> environmentGuard.validateEnvironment());
    }
    
    @Test
    void testGetEnvironmentDescription_ReturnsDetailedDescription() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "test"});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{"default"});
        ReflectionTestUtils.setField(environmentGuard, "archiveSearchEnabled", true);
        
        String description = environmentGuard.getEnvironmentDescription();
        
        assertNotNull(description);
        assertTrue(description.contains("Active Profiles"));
        assertTrue(description.contains("Default Profiles"));
        assertTrue(description.contains("Archive Search Enabled"));
        assertTrue(description.contains("Is Production"));
    }
    
    @Test
    void testIsProductionEnvironment_WithMixedCaseProfiles_ReturnsTrue() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"Dev", "PROD", "Test"});
        
        assertTrue(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_WithSpacesInSystemProperty_ReturnsTrue() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{});
        
        System.setProperty("spring.profiles.active", " prod , dev ");
        
        assertTrue(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_EmptySystemProperty_ReturnsFalse() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{});
        
        System.setProperty("spring.profiles.active", "");
        System.setProperty("environment", "");
        
        assertFalse(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_OnlyNonProductionProfiles_ReturnsFalse() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "test", "staging"});
        
        assertFalse(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testValidateEnvironment_ExceptionMessageContainsDetails() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "monitoring"});
        ReflectionTestUtils.setField(environmentGuard, "archiveSearchEnabled", true);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> environmentGuard.validateEnvironment());
        
        String message = exception.getMessage();
        assertTrue(message.contains("Active profiles"));
        assertTrue(message.contains("Environment variables checked"));
        assertTrue(message.contains("development, testing, or staging environments"));
    }
    
    @Test
    void testIsProductionEnvironment_NullProfiles_ReturnsFalse() {
        when(environment.getActiveProfiles()).thenReturn(null);
        when(environment.getDefaultProfiles()).thenReturn(null);
        
        assertFalse(environmentGuard.isProductionEnvironment());
    }
    
    @Test
    void testIsProductionEnvironment_SystemPropertyWithNullValue_ReturnsFalse() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{});
        
        // Clear the system property to ensure it's null
        System.clearProperty("spring.profiles.active");
        System.clearProperty("environment");
        
        assertFalse(environmentGuard.isProductionEnvironment());
    }
}