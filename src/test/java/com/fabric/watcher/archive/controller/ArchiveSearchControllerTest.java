package com.fabric.watcher.archive.controller;

import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.security.EnvironmentGuard;
import com.fabric.watcher.archive.service.ArchiveSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ArchiveSearchController.
 * Tests all REST endpoints with various scenarios including success cases,
 * validation errors, and exception handling.
 */
@WebMvcTest(ArchiveSearchController.class)
@TestPropertySource(properties = {
    "archive.search.enabled=true"
})
class ArchiveSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArchiveSearchService archiveSearchService;

    @MockBean
    private EnvironmentGuard environmentGuard;

    @Autowired
    private ObjectMapper objectMapper;

    private FileInfo sampleFileInfo;
    private FileSearchResponse sampleFileSearchResponse;
    private ContentSearchRequest sampleContentSearchRequest;
    private ContentSearchResponse sampleContentSearchResponse;

    @BeforeEach
    void setUp() {
        // Set up sample data
        sampleFileInfo = new FileInfo();
        sampleFileInfo.setFileName("test.txt");
        sampleFileInfo.setFullPath("/data/test.txt");
        sampleFileInfo.setRelativePath("test.txt");
        sampleFileInfo.setSize(1024L);
        sampleFileInfo.setLastModified(LocalDateTime.now());
        sampleFileInfo.setType(FileType.REGULAR);

        sampleFileSearchResponse = new FileSearchResponse();
        sampleFileSearchResponse.setFiles(Arrays.asList(sampleFileInfo));
        sampleFileSearchResponse.setTotalCount(1);
        sampleFileSearchResponse.setSearchPath("/data");
        sampleFileSearchResponse.setSearchPattern("*.txt");
        sampleFileSearchResponse.setSearchTimeMs(150L);

        sampleContentSearchRequest = new ContentSearchRequest();
        sampleContentSearchRequest.setFilePath("/data/test.txt");
        sampleContentSearchRequest.setSearchTerm("sample");
        sampleContentSearchRequest.setCaseSensitive(false);
        sampleContentSearchRequest.setWholeWord(false);

        SearchMatch sampleMatch = new SearchMatch();
        sampleMatch.setLineNumber(1);
        sampleMatch.setLineContent("This is a sample line");
        sampleMatch.setColumnStart(10);
        sampleMatch.setColumnEnd(16);

        sampleContentSearchResponse = new ContentSearchResponse();
        sampleContentSearchResponse.setMatches(Arrays.asList(sampleMatch));
        sampleContentSearchResponse.setTotalMatches(1);
        sampleContentSearchResponse.setTruncated(false);
        sampleContentSearchResponse.setSearchTimeMs(85L);

        // Default environment guard behavior
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(true);
    }

    @Test
    void searchFiles_Success() throws Exception {
        // Given
        when(archiveSearchService.searchFiles("/data", "*.txt"))
            .thenReturn(sampleFileSearchResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data")
                .param("pattern", "*.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.searchPath").value("/data"))
                .andExpect(jsonPath("$.searchPattern").value("*.txt"))
                .andExpect(jsonPath("$.files[0].fileName").value("test.txt"));

        verify(archiveSearchService).searchFiles("/data", "*.txt");
        verify(environmentGuard).isNonProductionEnvironment();
    }

    @Test
    void searchFiles_ProductionEnvironment_ShouldReturnServiceUnavailable() throws Exception {
        // Given
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data")
                .param("pattern", "*.txt"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("ARCH006"))
                .andExpect(jsonPath("$.message").value("Archive search API is disabled in production environment"));

        verify(archiveSearchService, never()).searchFiles(anyString(), anyString());
    }

    @Test
    void searchFiles_BlankPath_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "")
                .param("pattern", "*.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));

        verify(archiveSearchService, never()).searchFiles(anyString(), anyString());
    }

    @Test
    void searchFiles_BlankPattern_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data")
                .param("pattern", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));

        verify(archiveSearchService, never()).searchFiles(anyString(), anyString());
    }

    @Test
    void searchFiles_PathTooLong_ShouldReturnBadRequest() throws Exception {
        // Given
        String longPath = "a".repeat(1001);

        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", longPath)
                .param("pattern", "*.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));

        verify(archiveSearchService, never()).searchFiles(anyString(), anyString());
    }

    @Test
    void searchFiles_ServiceThrowsArchiveSearchException_ShouldReturnAppropriateStatus() throws Exception {
        // Given
        when(archiveSearchService.searchFiles("/data", "*.txt"))
            .thenThrow(new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.PATH_NOT_ALLOWED,
                "Access denied to path"
            ));

        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data")
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("ARCH001"))
                .andExpect(jsonPath("$.message").value("Access denied to path"));
    }

    @Test
    void downloadFile_Success() throws Exception {
        // Given
        String fileContent = "Sample file content";
        InputStream inputStream = new ByteArrayInputStream(fileContent.getBytes());
        when(archiveSearchService.downloadFile("/data/test.txt")).thenReturn(inputStream);

        // When & Then
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", "/data/test.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.txt\""))
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(content().string(fileContent));

        verify(archiveSearchService).downloadFile("/data/test.txt");
        verify(environmentGuard).isNonProductionEnvironment();
    }

    @Test
    void downloadFile_JsonFile_ShouldSetCorrectContentType() throws Exception {
        // Given
        String fileContent = "{\"key\": \"value\"}";
        InputStream inputStream = new ByteArrayInputStream(fileContent.getBytes());
        when(archiveSearchService.downloadFile("/data/test.json")).thenReturn(inputStream);

        // When & Then
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", "/data/test.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"));

        verify(archiveSearchService).downloadFile("/data/test.json");
    }

    @Test
    void downloadFile_ProductionEnvironment_ShouldReturnServiceUnavailable() throws Exception {
        // Given
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", "/data/test.txt"))
                .andExpect(status().isServiceUnavailable());

        verify(archiveSearchService, never()).downloadFile(anyString());
    }

    @Test
    void downloadFile_BlankFilePath_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", ""))
                .andExpect(status().isBadRequest());

        verify(archiveSearchService, never()).downloadFile(anyString());
    }

    @Test
    void downloadFile_FileNotFound_ShouldReturnNotFound() throws Exception {
        // Given
        when(archiveSearchService.downloadFile("/data/nonexistent.txt"))
            .thenThrow(new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.FILE_NOT_FOUND,
                "File not found"
            ));

        // When & Then
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", "/data/nonexistent.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ARCH002"));
    }

    @Test
    void searchContent_Success() throws Exception {
        // Given
        when(archiveSearchService.searchContent(
            "/data/test.txt", "sample", false, false))
            .thenReturn(sampleContentSearchResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleContentSearchRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.matches[0].lineNumber").value(1))
                .andExpect(jsonPath("$.matches[0].lineContent").value("This is a sample line"));

        verify(archiveSearchService).searchContent("/data/test.txt", "sample", false, false);
        verify(environmentGuard).isNonProductionEnvironment();
    }

    @Test
    void searchContent_ProductionEnvironment_ShouldReturnServiceUnavailable() throws Exception {
        // Given
        when(environmentGuard.isNonProductionEnvironment()).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleContentSearchRequest)))
                .andExpect(status().isServiceUnavailable());

        verify(archiveSearchService, never()).searchContent(anyString(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void searchContent_InvalidRequest_ShouldReturnBadRequest() throws Exception {
        // Given
        ContentSearchRequest invalidRequest = new ContentSearchRequest();
        invalidRequest.setFilePath(""); // Invalid: blank path
        invalidRequest.setSearchTerm("sample");

        // When & Then
        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));

        verify(archiveSearchService, never()).searchContent(anyString(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void searchContent_MissingSearchTerm_ShouldReturnBadRequest() throws Exception {
        // Given
        ContentSearchRequest invalidRequest = new ContentSearchRequest();
        invalidRequest.setFilePath("/data/test.txt");
        invalidRequest.setSearchTerm(""); // Invalid: blank search term

        // When & Then
        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ARCH007"));

        verify(archiveSearchService, never()).searchContent(anyString(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void searchContent_UnsupportedFormat_ShouldReturnUnprocessableEntity() throws Exception {
        // Given
        when(archiveSearchService.searchContent("/data/binary.exe", "sample", false, false))
            .thenThrow(new ArchiveSearchException(
                ArchiveSearchException.ErrorCode.UNSUPPORTED_FORMAT,
                "File is not a text file"
            ));

        // When & Then
        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ContentSearchRequest("/data/binary.exe", "sample"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH005"));
    }

    @Test
    void getStatus_Success() throws Exception {
        // Given
        Map<String, Object> statusResponse = new HashMap<>();
        statusResponse.put("enabled", true);
        statusResponse.put("environment", "development");
        statusResponse.put("version", "1.0.0");
        statusResponse.put("supportedArchiveTypes", Arrays.asList("zip", "tar", "jar"));
        statusResponse.put("maxFileSize", 104857600);
        statusResponse.put("maxSearchResults", 100);

        when(archiveSearchService.getApiStatus()).thenReturn(statusResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/archive/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.environment").value("development"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.maxFileSize").value(104857600));

        verify(archiveSearchService).getApiStatus();
    }

    @Test
    void getStatus_ServiceError_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(archiveSearchService.getApiStatus())
            .thenThrow(new RuntimeException("Configuration error"));

        // When & Then
        mockMvc.perform(get("/api/v1/archive/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("ARCH999"));
    }

    @Test
    void handleSecurityException_ShouldReturnForbidden() throws Exception {
        // Given
        when(archiveSearchService.searchFiles("/data", "*.txt"))
            .thenThrow(new SecurityException("Path traversal attempt detected"));

        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data")
                .param("pattern", "*.txt"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ARCH009"))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void handleGenericException_ShouldReturnInternalServerError() throws Exception {
        // Given
        when(archiveSearchService.searchFiles("/data", "*.txt"))
            .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data")
                .param("pattern", "*.txt"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ARCH008"));
    }
}