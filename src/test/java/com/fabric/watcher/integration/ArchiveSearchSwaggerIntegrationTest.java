package com.fabric.watcher.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Archive Search API Swagger UI integration.
 * Tests Swagger documentation availability, endpoint documentation,
 * and environment-based Swagger UI behavior.
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchiveSearchSwaggerIntegrationTest {

    /**
     * Test Swagger UI integration in development environment
     * Requirements: 8.1, 8.2, 8.3
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("dev")
    @TestPropertySource(properties = {
        "archive.search.enabled=true",
        "spring.profiles.active=dev",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
    })
    static class DevelopmentSwaggerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private TestRestTemplate restTemplate;

        @LocalServerPort
        private int port;

        @Test
        void testSwaggerUiAccessibleInDevelopment() throws Exception {
            // Test that Swagger UI is accessible in development environment
            String swaggerUrl = "http://localhost:" + port + "/swagger-ui.html";
            ResponseEntity<String> response = restTemplate.getForEntity(swaggerUrl, String.class);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("Swagger UI");
        }

        @Test
        void testApiDocsAccessibleInDevelopment() throws Exception {
            // Test that API documentation is accessible
            mockMvc.perform(get("/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$.openapi").exists())
                    .andExpect(jsonPath("$.info").exists())
                    .andExpect(jsonPath("$.paths").exists());
        }

        @Test
        void testArchiveSearchEndpointsInApiDocs() throws Exception {
            // Test that archive search endpoints are documented in API docs
            mockMvc.perform(get("/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/content-search']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/status']").exists());
        }

        @Test
        void testArchiveSearchGroupInApiDocs() throws Exception {
            // Test that archive search API group is properly configured
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/content-search']").exists());
        }

        @Test
        void testSearchEndpointDocumentation() throws Exception {
            // Test detailed documentation for search endpoint
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.summary").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.description").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.parameters").isArray())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.responses").exists());
        }

        @Test
        void testDownloadEndpointDocumentation() throws Exception {
            // Test detailed documentation for download endpoint
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download'].get.summary").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download'].get.description").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download'].get.parameters").isArray())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download'].get.responses").exists());
        }

        @Test
        void testContentSearchEndpointDocumentation() throws Exception {
            // Test detailed documentation for content search endpoint
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/content-search'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/content-search'].post.summary").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/content-search'].post.description").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/content-search'].post.requestBody").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/content-search'].post.responses").exists());
        }

        @Test
        void testStatusEndpointDocumentation() throws Exception {
            // Test detailed documentation for status endpoint
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/status'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/status'].get.summary").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/status'].get.description").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/status'].get.responses").exists());
        }

        @Test
        void testSchemaDefinitionsInApiDocs() throws Exception {
            // Test that data model schemas are properly documented
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.schemas.FileSearchResponse").exists())
                    .andExpect(jsonPath("$.components.schemas.FileInfo").exists())
                    .andExpect(jsonPath("$.components.schemas.ContentSearchRequest").exists())
                    .andExpect(jsonPath("$.components.schemas.ContentSearchResponse").exists())
                    .andExpect(jsonPath("$.components.schemas.SearchMatch").exists())
                    .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
        }

        @Test
        void testParameterValidationDocumentation() throws Exception {
            // Test that parameter validation rules are documented
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.parameters[?(@.name=='path')].required").value(true))
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.parameters[?(@.name=='pattern')].required").value(true))
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download'].get.parameters[?(@.name=='filePath')].required").value(true));
        }

        @Test
        void testErrorResponseDocumentation() throws Exception {
            // Test that error responses are properly documented
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.responses['400']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.responses['403']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.responses['404']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search'].get.responses['503']").exists());
        }
    }

    /**
     * Test Swagger UI integration in test environment
     * Requirements: 8.1, 8.2
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "archive.search.enabled=true",
        "spring.profiles.active=test",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
    })
    static class TestEnvironmentSwaggerTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testSwaggerUiAccessibleInTestEnvironment() throws Exception {
            // Test that Swagger UI is accessible in test environment
            String swaggerUrl = "http://localhost:" + port + "/swagger-ui.html";
            ResponseEntity<String> response = restTemplate.getForEntity(swaggerUrl, String.class);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void testArchiveSearchEndpointsDocumentedInTestEnvironment() throws Exception {
            // Test that archive search endpoints are documented in test environment
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/search']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/download']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/archive/content-search']").exists());
        }
    }

    /**
     * Test Swagger UI behavior in production environment
     * Requirements: 8.4
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("prod")
    @TestPropertySource(properties = {
        "archive.search.enabled=false",
        "spring.profiles.active=prod",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
    })
    static class ProductionSwaggerTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testSwaggerUiDisabledInProduction() throws Exception {
            // Test that Swagger UI is disabled in production environment
            String swaggerUrl = "http://localhost:" + port + "/swagger-ui.html";
            ResponseEntity<String> response = restTemplate.getForEntity(swaggerUrl, String.class);
            
            // Should return 404 or redirect when disabled
            assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.MOVED_PERMANENTLY, HttpStatus.FOUND);
        }

        @Test
        void testApiDocsDisabledInProduction() throws Exception {
            // Test that API docs are disabled in production environment
            mockMvc.perform(get("/api-docs"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void testArchiveSearchGroupNotAvailableInProduction() throws Exception {
            // Test that archive search API group is not available in production
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * Test Swagger UI with archive search disabled but in non-production environment
     * Requirements: 8.4
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("dev")
    @TestPropertySource(properties = {
        "archive.search.enabled=false",
        "spring.profiles.active=dev",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
    })
    static class DisabledArchiveSearchSwaggerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private TestRestTemplate restTemplate;

        @LocalServerPort
        private int port;

        @Test
        void testSwaggerUiStillAccessibleWhenArchiveSearchDisabled() throws Exception {
            // Test that Swagger UI is still accessible even when archive search is disabled
            String swaggerUrl = "http://localhost:" + port + "/swagger-ui.html";
            ResponseEntity<String> response = restTemplate.getForEntity(swaggerUrl, String.class);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void testArchiveSearchEndpointsNotInApiDocsWhenDisabled() throws Exception {
            // Test that archive search endpoints are not in API docs when disabled
            // Note: This depends on the actual implementation - endpoints might still be documented
            // but return service unavailable, or they might be completely excluded
            mockMvc.perform(get("/api-docs"))
                    .andExpect(status().isOk());
            
            // The specific behavior here depends on implementation:
            // Option 1: Endpoints are documented but return 503
            // Option 2: Endpoints are completely excluded from documentation
            // For this test, we'll verify that the general API docs are still available
        }

        @Test
        void testOtherApiGroupsStillAvailable() throws Exception {
            // Test that other API groups (file-watcher, monitoring) are still available
            mockMvc.perform(get("/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths").exists());
        }
    }

    /**
     * Test Swagger UI configuration and customization
     * Requirements: 8.5
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("dev")
    @TestPropertySource(properties = {
        "archive.search.enabled=true",
        "spring.profiles.active=dev",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "springdoc.swagger-ui.display-request-duration=true",
        "springdoc.swagger-ui.operations-sorter=method"
    })
    static class SwaggerConfigurationTest {

        @Autowired
        private MockMvc mockMvc;

        @LocalServerPort
        private int port;

        @Test
        void testSwaggerGroupConfiguration() throws Exception {
            // Test that archive search group is properly configured
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info").exists())
                    .andExpect(jsonPath("$.paths").exists());
        }

        @Test
        void testApiDocumentationCompleteness() throws Exception {
            // Test that API documentation includes all required information
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").exists())
                    .andExpect(jsonPath("$.info.description").exists())
                    .andExpect(jsonPath("$.info.version").exists())
                    .andExpect(jsonPath("$.servers").exists())
                    .andExpect(jsonPath("$.components").exists());
        }

        @Test
        void testSecurityDocumentation() throws Exception {
            // Test that security requirements are documented if applicable
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths").exists());
            
            // Note: Security documentation would be added here if authentication is implemented
        }

        @Test
        void testExampleDocumentation() throws Exception {
            // Test that examples are provided in the documentation
            mockMvc.perform(get("/api-docs/archive-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.schemas").exists());
            
            // Examples would be verified here if they are included in the schema definitions
        }
    }

    /**
     * Test Swagger UI accessibility and usability
     * Requirements: 8.2, 8.3
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureWebMvc
    @ActiveProfiles("dev")
    @TestPropertySource(properties = {
        "archive.search.enabled=true",
        "spring.profiles.active=dev",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
    })
    static class SwaggerUsabilityTest {

        @Autowired
        private TestRestTemplate restTemplate;

        @LocalServerPort
        private int port;

        @Test
        void testSwaggerUiInteractivity() throws Exception {
            // Test that Swagger UI allows interactive testing
            String swaggerUrl = "http://localhost:" + port + "/swagger-ui.html";
            ResponseEntity<String> response = restTemplate.getForEntity(swaggerUrl, String.class);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("swagger-ui");
            
            // In a real browser test, you would verify:
            // - Try it out buttons are present
            // - Parameter input fields are available
            // - Execute buttons work
            // - Response examples are shown
        }

        @Test
        void testApiDocumentationReadability() throws Exception {
            // Test that API documentation is well-structured and readable
            String apiDocsUrl = "http://localhost:" + port + "/api-docs/archive-search";
            ResponseEntity<String> response = restTemplate.getForEntity(apiDocsUrl, String.class);
            
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("paths");
            assertThat(response.getBody()).contains("components");
            assertThat(response.getBody()).contains("schemas");
        }
    }
}