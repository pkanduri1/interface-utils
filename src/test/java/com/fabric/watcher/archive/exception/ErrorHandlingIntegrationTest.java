package com.fabric.watcher.archive.exception;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for error handling in archive search API.
 * Tests end-to-end error scenarios and HTTP response mapping.
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class ErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ArchiveSearchProperties archiveSearchProperties;

    @Test
    void testFileNotFoundError() throws Exception {
        // Given
        when(archiveSearchProperties.isEnabled()).thenReturn(true);
        when(archiveSearchProperties.getAllowedPaths()).thenReturn(java.util.List.of("/tmp"));

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/tmp/nonexistent")
                .param("pattern", "*.txt")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Verify error response structure
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        
        assertEquals("ARCH002", errorResponse.getErrorCode());
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp());
        assertEquals("/api/v1/archive/search", errorResponse.getPath());
    }

    @Test
    void testInvalidParameterError() throws Exception {
        // Given
        when(archiveSearchProperties.isEnabled()).thenReturn(true);

        // When & Then - missing required parameter
        MvcResult result = mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/tmp")
                // Missing pattern parameter
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Verify error response structure
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        
        assertEquals("ARCH007", errorResponse.getErrorCode());
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testPathTraversalSecurityError() throws Exception {
        // Given
        when(archiveSearchProperties.isEnabled()).thenReturn(true);
        when(archiveSearchProperties.getAllowedPaths()).thenReturn(java.util.List.of("/tmp"));

        // When & Then - attempt path traversal
        MvcResult result = mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/tmp/../etc")
                .param("pattern", "passwd")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Verify error response structure
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        
        assertTrue(errorResponse.getErrorCode().equals("ARCH001") || 
                  errorResponse.getErrorCode().equals("ARCH009"));
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testEnvironmentRestrictedError() throws Exception {
        // Given - API disabled
        when(archiveSearchProperties.isEnabled()).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/tmp")
                .param("pattern", "*.txt")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()); // 404 because endpoint is not available
    }

    @Test
    void testContentSearchValidationError() throws Exception {
        // Given
        when(archiveSearchProperties.isEnabled()).thenReturn(true);

        // When & Then - invalid content search request
        String invalidRequest = "{ \"filePath\": \"\", \"searchTerm\": \"\" }";
        
        MvcResult result = mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Verify error response structure
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        
        assertEquals("ARCH007", errorResponse.getErrorCode());
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getDetails());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testDownloadFileNotFoundError() throws Exception {
        // Given
        when(archiveSearchProperties.isEnabled()).thenReturn(true);
        when(archiveSearchProperties.getAllowedPaths()).thenReturn(java.util.List.of("/tmp"));

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", "/tmp/nonexistent.txt")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Verify error response structure
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        
        assertEquals("ARCH002", errorResponse.getErrorCode());
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp());
        assertEquals("/api/v1/archive/download", errorResponse.getPath());
    }

    @Test
    void testErrorResponseJsonSerialization() throws Exception {
        // Given
        when(archiveSearchProperties.isEnabled()).thenReturn(true);

        // When
        MvcResult result = mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/nonexistent")
                .param("pattern", "*.txt")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists())
                .andReturn();

        // Verify JSON structure
        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("\"errorCode\""));
        assertTrue(responseBody.contains("\"message\""));
        assertTrue(responseBody.contains("\"timestamp\""));
        assertTrue(responseBody.contains("\"path\""));
    }

    @Test
    void testConcurrentErrorHandling() throws Exception {
        // Given
        when(archiveSearchProperties.isEnabled()).thenReturn(true);

        // When - simulate concurrent requests that will fail
        Thread[] threads = new Thread[5];
        Exception[] exceptions = new Exception[5];
        
        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    mockMvc.perform(get("/api/v1/archive/search")
                            .param("path", "/nonexistent" + index)
                            .param("pattern", "*.txt")
                            .contentType(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNotFound());
                } catch (Exception e) {
                    exceptions[index] = e;
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - verify no exceptions occurred during concurrent processing
        for (Exception exception : exceptions) {
            assertNull(exception, "Concurrent error handling should not throw exceptions");
        }
    }
}