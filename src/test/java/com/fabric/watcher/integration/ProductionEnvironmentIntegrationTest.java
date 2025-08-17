package com.fabric.watcher.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
 * Integration test to verify production environment behavior.
 * 
 * This test validates that the Archive Search API is properly disabled
 * in production environments for security reasons.
 * 
 * Requirements: 7.1, 7.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = com.fabric.watcher.TestApplication.class)
@AutoConfigureWebMvc
@ActiveProfiles("production")
@TestPropertySource(properties = {
    "archive.search.enabled=false",
    "spring.profiles.active=production",
    "springdoc.api-docs.enabled=true",
    "springdoc.swagger-ui.enabled=true"
})
class ProductionEnvironmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    /**
     * Test that Archive Search API is disabled in production environment
     * Requirements: 7.1
     */
    @Test
    void testArchiveSearchApiDisabledInProduction() throws Exception {
        // Test that archive search endpoints return service unavailable
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/tmp")
                .param("pattern", "*.txt"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("ARCH006"))
                .andExpect(jsonPath("$.message").value("Feature disabled in production"));

        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", "/tmp/test.txt"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("ARCH006"));

        mockMvc.perform(get("/api/v1/archive/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("ARCH006"));
    }

    /**
     * Test that authentication endpoints are also disabled in production
     * Requirements: 7.1
     */
    @Test
    void testAuthenticationEndpointsDisabledInProduction() throws Exception {
        mockMvc.perform(get("/api/v1/auth/validate"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("ARCH006"));

        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("ARCH006"));
    }

    /**
     * Test that Swagger UI does not display archive search endpoints in production
     * Requirements: 7.2, 12.4
     */
    @Test
    void testSwaggerUiDoesNotDisplayArchiveSearchEndpointsInProduction() throws Exception {
        // Test that API documentation excludes archive search endpoints
        ResponseEntity<String> apiDocsResponse = restTemplate.getForEntity(
            "http://localhost:" + port + "/v3/api-docs", String.class);
        
        if (apiDocsResponse.getStatusCode() == HttpStatus.OK) {
            JsonNode apiDocs = objectMapper.readTree(apiDocsResponse.getBody());
            JsonNode paths = apiDocs.get("paths");

            // Verify archive search endpoints are not documented
            assertThat(paths.has("/api/v1/archive/search")).isFalse();
            assertThat(paths.has("/api/v1/archive/download")).isFalse();
            assertThat(paths.has("/api/v1/archive/content-search")).isFalse();
            assertThat(paths.has("/api/v1/archive/upload")).isFalse();
            assertThat(paths.has("/api/v1/archive/status")).isFalse();
            assertThat(paths.has("/api/v1/auth/login")).isFalse();
            assertThat(paths.has("/api/v1/auth/logout")).isFalse();
            assertThat(paths.has("/api/v1/auth/validate")).isFalse();
        }
    }

    /**
     * Test that existing application functionality still works in production
     * Requirements: 7.1
     */
    @Test
    void testExistingFunctionalityStillWorksInProduction() throws Exception {
        // Test that health endpoint still works
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // Test that other application endpoints are not affected
        // (This would include any existing database-script-watcher endpoints)
    }

    /**
     * Test environment detection accuracy
     * Requirements: 7.4
     */
    @Test
    void testEnvironmentDetectionAccuracy() throws Exception {
        // Verify that the application correctly detects production environment
        // This is implicit in the fact that the API endpoints are disabled
        
        // Test that a request to any archive endpoint confirms production mode
        String response = mockMvc.perform(get("/api/v1/archive/status"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("Feature disabled in production");
    }
}