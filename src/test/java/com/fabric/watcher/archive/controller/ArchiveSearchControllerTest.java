package com.fabric.watcher.archive.controller;

import com.fabric.watcher.archive.exception.ArchiveSearchException;
import com.fabric.watcher.archive.model.*;
import com.fabric.watcher.archive.service.ArchiveSearchAuditService;
import com.fabric.watcher.archive.service.FileUploadService;
import com.fabric.watcher.archive.service.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ArchiveSearchController.
 */
@ExtendWith(MockitoExtension.class)
class ArchiveSearchControllerTest {

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private ArchiveSearchAuditService auditService;

    private ArchiveSearchController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String VALID_TOKEN = "Bearer valid-jwt-token";
    private static final String USER_ID = "testuser";
    private static final List<String> USER_PERMISSIONS = Arrays.asList("FILE_UPLOAD", "FILE_SEARCH");

    @BeforeEach
    void setUp() {
        controller = new ArchiveSearchController(fileUploadService, jwtTokenService, auditService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testSearchFiles_Success() throws Exception {
        // Arrange
        when(jwtTokenService.extractTokenFromHeader(VALID_TOKEN)).thenReturn("valid-jwt-token");
        when(jwtTokenService.getUserIdFromToken("valid-jwt-token")).thenReturn(USER_ID);
        when(jwtTokenService.getPermissionsFromToken("valid-jwt-token")).thenReturn(USER_PERMISSIONS);

        // Act & Assert
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data/archives")
                .param("pattern", "*.log")
                .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.searchPath").value("/data/archives"))
                .andExpect(jsonPath("$.searchPattern").value("*.log"));

        verify(auditService).logFileSearch(eq(USER_ID), eq("*.log"), eq("/data/archives"), eq(0), anyString(), isNull());
    }

    @Test
    void testSearchFiles_NoAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data/archives")
                .param("pattern", "*.log"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSearchFiles_InvalidToken() throws Exception {
        // Arrange
        when(jwtTokenService.extractTokenFromHeader(VALID_TOKEN)).thenReturn("invalid-token");
        when(jwtTokenService.getUserIdFromToken("invalid-token"))
                .thenThrow(new ArchiveSearchException(ArchiveSearchException.ErrorCode.INVALID_TOKEN, "Invalid token"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/archive/search")
                .param("path", "/data/archives")
                .param("pattern", "*.log")
                .header("Authorization", VALID_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDownloadFile_NotImplemented() throws Exception {
        // Arrange
        when(jwtTokenService.extractTokenFromHeader(VALID_TOKEN)).thenReturn("valid-jwt-token");
        when(jwtTokenService.getUserIdFromToken("valid-jwt-token")).thenReturn(USER_ID);
        when(jwtTokenService.getPermissionsFromToken("valid-jwt-token")).thenReturn(USER_PERMISSIONS);

        // Act & Assert
        mockMvc.perform(get("/api/v1/archive/download")
                .param("filePath", "/data/archives/test.log")
                .header("Authorization", VALID_TOKEN))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void testSearchContent_Success() throws Exception {
        // Arrange
        when(jwtTokenService.extractTokenFromHeader(VALID_TOKEN)).thenReturn("valid-jwt-token");
        when(jwtTokenService.getUserIdFromToken("valid-jwt-token")).thenReturn(USER_ID);
        when(jwtTokenService.getPermissionsFromToken("valid-jwt-token")).thenReturn(USER_PERMISSIONS);

        ContentSearchRequest request = new ContentSearchRequest("/data/archives/test.log", "ERROR");

        // Act & Assert
        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.totalMatches").value(0))
                .andExpect(jsonPath("$.truncated").value(false));

        verify(auditService).logContentSearch(eq(USER_ID), eq("ERROR"), eq("/data/archives/test.log"), eq(0), anyString(), isNull());
    }

    @Test
    void testUploadFile_Success() throws Exception {
        // Arrange
        when(jwtTokenService.extractTokenFromHeader(VALID_TOKEN)).thenReturn("valid-jwt-token");
        when(jwtTokenService.getUserIdFromToken("valid-jwt-token")).thenReturn(USER_ID);
        when(jwtTokenService.getPermissionsFromToken("valid-jwt-token")).thenReturn(USER_PERMISSIONS);

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
        UploadResponse expectedResponse = new UploadResponse("test.txt", "/opt/uploads/test.txt", 12L, USER_ID);
        
        when(fileUploadService.uploadFile(any(UploadRequest.class), eq(USER_ID), anyString(), anyString(), anyString()))
                .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/archive/upload")
                .file(file)
                .param("targetPath", "/opt/uploads/test.txt")
                .param("overwrite", "false")
                .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("test.txt"))
                .andExpect(jsonPath("$.targetPath").value("/opt/uploads/test.txt"))
                .andExpect(jsonPath("$.uploadedBy").value(USER_ID));

        verify(fileUploadService).uploadFile(any(UploadRequest.class), eq(USER_ID), anyString(), anyString(), anyString());
    }

    @Test
    void testUploadFile_NoAuthentication() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/archive/upload")
                .file(file)
                .param("targetPath", "/opt/uploads/test.txt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUploadFile_UploadServiceException() throws Exception {
        // Arrange
        when(jwtTokenService.extractTokenFromHeader(VALID_TOKEN)).thenReturn("valid-jwt-token");
        when(jwtTokenService.getUserIdFromToken("valid-jwt-token")).thenReturn(USER_ID);
        when(jwtTokenService.getPermissionsFromToken("valid-jwt-token")).thenReturn(USER_PERMISSIONS);

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
        
        when(fileUploadService.uploadFile(any(UploadRequest.class), eq(USER_ID), anyString(), anyString(), anyString()))
                .thenThrow(new ArchiveSearchException(ArchiveSearchException.ErrorCode.FILE_TOO_LARGE, "File too large"));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/archive/upload")
                .file(file)
                .param("targetPath", "/opt/uploads/test.txt")
                .header("Authorization", VALID_TOKEN))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void testContentSearch_InvalidRequest() throws Exception {
        // Arrange
        when(jwtTokenService.extractTokenFromHeader(VALID_TOKEN)).thenReturn("valid-jwt-token");
        when(jwtTokenService.getUserIdFromToken("valid-jwt-token")).thenReturn(USER_ID);
        when(jwtTokenService.getPermissionsFromToken("valid-jwt-token")).thenReturn(USER_PERMISSIONS);

        ContentSearchRequest request = new ContentSearchRequest("", ""); // Invalid request

        // Act & Assert
        mockMvc.perform(post("/api/v1/archive/content-search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", VALID_TOKEN))
                .andExpect(status().isBadRequest());
    }
}