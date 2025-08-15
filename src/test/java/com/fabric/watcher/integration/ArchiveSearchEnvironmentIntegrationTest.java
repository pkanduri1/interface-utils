package com.fabric.watcher.integration;

import com.fabric.watcher.archive.model.ContentSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Archive Search API environment detection and restrictions.
 * Tests production environment detection, API disabling, and environment-based behavior.
 * 
 * Requirements: 7.1, 7.2, 7.4, 7.5
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchiveSearchEnvironmentIntegrationTest {

    /**
     * Test Archive Search API in development environment
     * Requirements: 7.1, 7.2
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("dev")
    @TestPropertySource(properties = {
        "archive.search.enabled=true",
        "spring.profiles.active=dev"
    })
    static class DevelopmentEnvironmentTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @LocalServerPort
        private int port;

        @Test
        void testArchiveSearchApiEnabledInDevelopment() throws Exception {
            // Test that all endpoints are accessible in development environment
            
            // Test search endpoint
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isOk());

            // Test status endpoint
            mockMvc.perform(get("/api/v1/archive/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.environment").exists());

            // Test download endpoint (will fail due to path validation, but should not be disabled)
            mockMvc.perform(get("/api/v1/archive/download")
                    .param("filePath", "/tmp/test.txt"))
                    .andExpect(status().is(not(HttpStatus.SERVICE_UNAVAILABLE.value())));

            // Test content search endpoint
            ContentSearchRequest request = new ContentSearchRequest();
            request.setFilePath("/tmp/test.txt");
            request.setSearchTerm("test");

            mockMvc.perform(post("/api/v1/archive/content-search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().is(not(HttpStatus.SERVICE_UNAVAILABLE.value())));
        }

        @Test
        void testEnvironmentDetectionInDevelopment() throws Exception {
            // Test that environment is correctly detected as non-production
            mockMvc.perform(get("/api/v1/archive/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.environment").value("development"));
        }
    }

    /**
     * Test Archive Search API in test environment
     * Requirements: 7.1, 7.2
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "archive.search.enabled=true",
        "spring.profiles.active=test"
    })
    static class TestEnvironmentTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testArchiveSearchApiEnabledInTestEnvironment() throws Exception {
            // Test that API is enabled in test environment
            mockMvc.perform(get("/api/v1/archive/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true));

            // Test that endpoints are accessible
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Test Archive Search API in staging environment
     * Requirements: 7.1, 7.2
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("staging")
    @TestPropertySource(properties = {
        "archive.search.enabled=true",
        "spring.profiles.active=staging"
    })
    static class StagingEnvironmentTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testArchiveSearchApiEnabledInStagingEnvironment() throws Exception {
            // Test that API is enabled in staging environment
            mockMvc.perform(get("/api/v1/archive/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true));

            // Test that endpoints are accessible
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Test Archive Search API in production environment
     * Requirements: 7.1, 7.4
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("prod")
    @TestPropertySource(properties = {
        "archive.search.enabled=false",
        "spring.profiles.active=prod"
    })
    static class ProductionEnvironmentTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @LocalServerPort
        private int port;

        @Test
        void testArchiveSearchApiDisabledInProduction() throws Exception {
            // Test that all endpoints return SERVICE_UNAVAILABLE in production
            
            // Test search endpoint
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"))
                    .andExpect(jsonPath("$.message").value("Archive search API is disabled in production environment"));

            // Test download endpoint
            mockMvc.perform(get("/api/v1/archive/download")
                    .param("filePath", "/tmp/test.txt"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"));

            // Test content search endpoint
            ContentSearchRequest request = new ContentSearchRequest();
            request.setFilePath("/tmp/test.txt");
            request.setSearchTerm("test");

            mockMvc.perform(post("/api/v1/archive/content-search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"));
        }

        @Test
        void testStatusEndpointInProduction() throws Exception {
            // Status endpoint should still be accessible but show disabled state
            mockMvc.perform(get("/api/v1/archive/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.environment").value("production"))
                    .andExpect(jsonPath("$.message").value("Archive search API is disabled in production environment"));
        }
    }

    /**
     * Test Archive Search API with mixed profiles including production
     * Requirements: 7.1, 7.4
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles({"dev", "prod", "monitoring"})
    @TestPropertySource(properties = {
        "archive.search.enabled=false",
        "spring.profiles.active=dev,prod,monitoring"
    })
    static class MixedProfilesWithProductionTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testArchiveSearchApiDisabledWithMixedProfilesIncludingProduction() throws Exception {
            // When production profile is present among others, API should be disabled
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"));

            // Status should show production environment detected
            mockMvc.perform(get("/api/v1/archive/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.environment").value("production"));
        }
    }

    /**
     * Test Archive Search API with system property environment detection
     * Requirements: 7.1, 7.4
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @TestPropertySource(properties = {
        "archive.search.enabled=false",
        "environment=production"
    })
    static class SystemPropertyEnvironmentTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testEnvironmentDetectionViaSystemProperty() throws Exception {
            // Test that production environment is detected via system property
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"));
        }
    }

    /**
     * Test Archive Search API when explicitly disabled regardless of environment
     * Requirements: 7.5
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("dev")
    @TestPropertySource(properties = {
        "archive.search.enabled=false",
        "spring.profiles.active=dev"
    })
    static class ExplicitlyDisabledTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testArchiveSearchApiExplicitlyDisabled() throws Exception {
            // Even in development, if explicitly disabled, API should not be available
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"));

            // Status should reflect disabled state
            mockMvc.perform(get("/api/v1/archive/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.message").value("Archive search API is explicitly disabled"));
        }
    }

    /**
     * Test environment detection fallback behavior
     * Requirements: 7.4
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @TestPropertySource(properties = {
        "archive.search.enabled=false"
        // No explicit profiles or environment properties
    })
    static class EnvironmentDetectionFallbackTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testEnvironmentDetectionFallback() throws Exception {
            // When environment cannot be determined, should default to safe behavior (disabled)
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"));

            // Status should show unknown/default environment
            mockMvc.perform(get("/api/v1/archive/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
        }
    }

    /**
     * Test case-insensitive production profile detection
     * Requirements: 7.1, 7.4
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("PROD")
    @TestPropertySource(properties = {
        "archive.search.enabled=false",
        "spring.profiles.active=PROD"
    })
    static class CaseInsensitiveProductionTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testCaseInsensitiveProductionProfileDetection() throws Exception {
            // Test that uppercase "PROD" profile is detected as production
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"));
        }
    }

    /**
     * Test various production profile name variations
     * Requirements: 7.1, 7.4
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("production")
    @TestPropertySource(properties = {
        "archive.search.enabled=false",
        "spring.profiles.active=production"
    })
    static class ProductionProfileVariationsTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testProductionProfileVariations() throws Exception {
            // Test that "production" profile is detected as production
            mockMvc.perform(get("/api/v1/archive/search")
                    .param("path", "/tmp")
                    .param("pattern", "*.txt"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("ARCH006"));
        }
    }
}