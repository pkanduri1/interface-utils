package com.fabric.watcher.archive.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * Environment guard component that validates the application environment
 * and ensures archive search functionality is only enabled in non-production environments.
 */
@Component
@ConditionalOnProperty(name = "archive.search.enabled", havingValue = "true")
public class EnvironmentGuard {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentGuard.class);
    
    // Production environment indicators
    private static final List<String> PRODUCTION_PROFILES = Arrays.asList(
        "prod", "production", "live", "prd"
    );
    
    private static final List<String> PRODUCTION_ENVIRONMENT_VARIABLES = Arrays.asList(
        "PRODUCTION", "PROD", "LIVE"
    );
    
    private final Environment environment;
    
    @Value("${spring.profiles.active:}")
    private String activeProfiles;
    
    @Value("${archive.search.enabled:false}")
    private boolean archiveSearchEnabled;
    
    public EnvironmentGuard(Environment environment) {
        this.environment = environment;
    }
    
    /**
     * Validates the environment configuration on application startup.
     * Throws an exception if archive search is enabled in a production environment.
     */
    @PostConstruct
    public void validateEnvironment() {
        logger.info("Validating environment for archive search functionality...");
        
        boolean isProduction = isProductionEnvironment();
        
        if (isProduction && archiveSearchEnabled) {
            String errorMessage = "Archive search functionality is enabled in a production environment. " +
                "This feature should only be enabled in development, testing, or staging environments. " +
                "Active profiles: " + Arrays.toString(environment.getActiveProfiles()) + 
                ", Environment variables checked: " + PRODUCTION_ENVIRONMENT_VARIABLES;
            
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
        
        if (isProduction) {
            logger.info("Production environment detected. Archive search functionality is properly disabled.");
        } else {
            logger.info("Non-production environment detected. Archive search functionality is enabled.");
            logger.debug("Active profiles: {}", Arrays.toString(environment.getActiveProfiles()));
            logger.debug("Archive search enabled: {}", archiveSearchEnabled);
        }
    }
    
    /**
     * Checks if the current environment is a non-production environment.
     * 
     * @return true if running in non-production environment, false otherwise
     */
    public boolean isNonProductionEnvironment() {
        return !isProductionEnvironment();
    }
    
    /**
     * Checks if the current environment is a production environment.
     * 
     * @return true if running in production environment, false otherwise
     */
    public boolean isProductionEnvironment() {
        // Check Spring profiles
        if (hasProductionProfile()) {
            logger.debug("Production environment detected via Spring profiles");
            return true;
        }
        
        // Check environment variables
        if (hasProductionEnvironmentVariable()) {
            logger.debug("Production environment detected via environment variables");
            return true;
        }
        
        // Check system properties
        if (hasProductionSystemProperty()) {
            logger.debug("Production environment detected via system properties");
            return true;
        }
        
        // Default to non-production if no indicators found
        logger.debug("No production environment indicators found - assuming non-production");
        return false;
    }
    
    /**
     * Gets the current environment description for logging and debugging.
     * 
     * @return a string describing the current environment
     */
    public String getEnvironmentDescription() {
        StringBuilder description = new StringBuilder();
        description.append("Environment Details: ");
        description.append("Active Profiles: ").append(Arrays.toString(environment.getActiveProfiles()));
        description.append(", Default Profiles: ").append(Arrays.toString(environment.getDefaultProfiles()));
        description.append(", Archive Search Enabled: ").append(archiveSearchEnabled);
        description.append(", Is Production: ").append(isProductionEnvironment());
        
        return description.toString();
    }
    
    /**
     * Checks if any active Spring profiles indicate a production environment.
     * 
     * @return true if production profiles are active, false otherwise
     */
    private boolean hasProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        
        if (activeProfiles == null || activeProfiles.length == 0) {
            // Check default profiles if no active profiles
            activeProfiles = environment.getDefaultProfiles();
        }
        
        if (activeProfiles == null) {
            return false;
        }
        
        for (String profile : activeProfiles) {
            if (profile != null && PRODUCTION_PROFILES.contains(profile.toLowerCase())) {
                logger.debug("Production profile detected: {}", profile);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if any environment variables indicate a production environment.
     * 
     * @return true if production environment variables are found, false otherwise
     */
    private boolean hasProductionEnvironmentVariable() {
        for (String envVar : PRODUCTION_ENVIRONMENT_VARIABLES) {
            String value = System.getenv(envVar);
            if (value != null && ("true".equalsIgnoreCase(value) || "1".equals(value))) {
                logger.debug("Production environment variable detected: {}={}", envVar, value);
                return true;
            }
        }
        
        // Check for NODE_ENV (common in many applications)
        String nodeEnv = System.getenv("NODE_ENV");
        if ("production".equalsIgnoreCase(nodeEnv)) {
            logger.debug("Production NODE_ENV detected: {}", nodeEnv);
            return true;
        }
        
        // Check for ENVIRONMENT variable
        String environment = System.getenv("ENVIRONMENT");
        if (environment != null && PRODUCTION_PROFILES.contains(environment.toLowerCase())) {
            logger.debug("Production ENVIRONMENT variable detected: {}", environment);
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if any system properties indicate a production environment.
     * 
     * @return true if production system properties are found, false otherwise
     */
    private boolean hasProductionSystemProperty() {
        // Check spring.profiles.active system property
        String profilesProperty = System.getProperty("spring.profiles.active");
        if (profilesProperty != null) {
            String[] profiles = profilesProperty.split(",");
            for (String profile : profiles) {
                if (PRODUCTION_PROFILES.contains(profile.trim().toLowerCase())) {
                    logger.debug("Production system property profile detected: {}", profile);
                    return true;
                }
            }
        }
        
        // Check environment system property
        String envProperty = System.getProperty("environment");
        if (envProperty != null && PRODUCTION_PROFILES.contains(envProperty.toLowerCase())) {
            logger.debug("Production environment system property detected: {}", envProperty);
            return true;
        }
        
        return false;
    }
}