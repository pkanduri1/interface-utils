package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UploadResponse}.
 */
class UploadResponseTest {

    @Test
    void testDefaultConstructor() {
        UploadResponse response = new UploadResponse();
        
        assertFalse(response.isSuccess());
        assertNull(response.getFileName());
        assertNull(response.getTargetPath());
        assertEquals(0L, response.getFileSize());
        assertNull(response.getMessage());
        assertNull(response.getUploadedAt());
        assertNull(response.getUploadedBy());
    }

    @Test
    void testSuccessfulUploadConstructor() {
        String fileName = "config.properties";
        String targetPath = "/opt/uploads/config.properties";
        long fileSize = 1024L;
        String uploadedBy = "john.doe";
        
        UploadResponse response = new UploadResponse(fileName, targetPath, fileSize, uploadedBy);
        
        assertTrue(response.isSuccess());
        assertEquals(fileName, response.getFileName());
        assertEquals(targetPath, response.getTargetPath());
        assertEquals(fileSize, response.getFileSize());
        assertEquals(uploadedBy, response.getUploadedBy());
        assertEquals("File uploaded successfully", response.getMessage());
        assertNotNull(response.getUploadedAt());
        assertTrue(response.getUploadedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void testFailedUploadConstructor() {
        String fileName = "config.properties";
        String message = "Upload failed: File too large";
        
        UploadResponse response = new UploadResponse(fileName, message);
        
        assertFalse(response.isSuccess());
        assertEquals(fileName, response.getFileName());
        assertEquals(message, response.getMessage());
        assertNull(response.getTargetPath());
        assertEquals(0L, response.getFileSize());
        assertNull(response.getUploadedBy());
        assertNotNull(response.getUploadedAt());
        assertTrue(response.getUploadedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void testFullParametersConstructor() {
        boolean success = true;
        String fileName = "test.txt";
        String targetPath = "/opt/uploads/test.txt";
        long fileSize = 2048L;
        String message = "Custom success message";
        LocalDateTime uploadedAt = LocalDateTime.now().minusMinutes(5);
        String uploadedBy = "jane.smith";
        
        UploadResponse response = new UploadResponse(success, fileName, targetPath, fileSize, 
                                                   message, uploadedAt, uploadedBy);
        
        assertEquals(success, response.isSuccess());
        assertEquals(fileName, response.getFileName());
        assertEquals(targetPath, response.getTargetPath());
        assertEquals(fileSize, response.getFileSize());
        assertEquals(message, response.getMessage());
        assertEquals(uploadedAt, response.getUploadedAt());
        assertEquals(uploadedBy, response.getUploadedBy());
    }

    @Test
    void testGettersAndSetters() {
        UploadResponse response = new UploadResponse();
        boolean success = true;
        String fileName = "data.xml";
        String targetPath = "/opt/uploads/data.xml";
        long fileSize = 4096L;
        String message = "Upload completed";
        LocalDateTime uploadedAt = LocalDateTime.now();
        String uploadedBy = "test.user";
        
        response.setSuccess(success);
        response.setFileName(fileName);
        response.setTargetPath(targetPath);
        response.setFileSize(fileSize);
        response.setMessage(message);
        response.setUploadedAt(uploadedAt);
        response.setUploadedBy(uploadedBy);
        
        assertEquals(success, response.isSuccess());
        assertEquals(fileName, response.getFileName());
        assertEquals(targetPath, response.getTargetPath());
        assertEquals(fileSize, response.getFileSize());
        assertEquals(message, response.getMessage());
        assertEquals(uploadedAt, response.getUploadedAt());
        assertEquals(uploadedBy, response.getUploadedBy());
    }

    @Test
    void testToString() {
        String fileName = "config.properties";
        String targetPath = "/opt/uploads/config.properties";
        long fileSize = 1024L;
        String uploadedBy = "john.doe";
        
        UploadResponse response = new UploadResponse(fileName, targetPath, fileSize, uploadedBy);
        String toString = response.toString();
        
        assertTrue(toString.contains("success=true"));
        assertTrue(toString.contains("fileName='config.properties'"));
        assertTrue(toString.contains("targetPath='/opt/uploads/config.properties'"));
        assertTrue(toString.contains("fileSize=1024"));
        assertTrue(toString.contains("uploadedBy='john.doe'"));
        assertTrue(toString.contains("message='File uploaded successfully'"));
    }

    @Test
    void testEquals() {
        String fileName = "test.txt";
        String targetPath = "/opt/uploads/test.txt";
        long fileSize = 1024L;
        String message = "Success";
        LocalDateTime uploadedAt = LocalDateTime.now();
        String uploadedBy = "user";
        
        UploadResponse response1 = new UploadResponse(true, fileName, targetPath, fileSize, 
                                                    message, uploadedAt, uploadedBy);
        UploadResponse response2 = new UploadResponse(true, fileName, targetPath, fileSize, 
                                                    message, uploadedAt, uploadedBy);
        UploadResponse response3 = new UploadResponse(false, fileName, targetPath, fileSize, 
                                                    message, uploadedAt, uploadedBy);
        UploadResponse response4 = new UploadResponse(true, "different.txt", targetPath, fileSize, 
                                                    message, uploadedAt, uploadedBy);
        UploadResponse response5 = new UploadResponse(true, fileName, "/different/path", fileSize, 
                                                    message, uploadedAt, uploadedBy);
        UploadResponse response6 = new UploadResponse(true, fileName, targetPath, 2048L, 
                                                    message, uploadedAt, uploadedBy);
        UploadResponse response7 = new UploadResponse(true, fileName, targetPath, fileSize, 
                                                    "Different message", uploadedAt, uploadedBy);
        UploadResponse response8 = new UploadResponse(true, fileName, targetPath, fileSize, 
                                                    message, uploadedAt.plusMinutes(1), uploadedBy);
        UploadResponse response9 = new UploadResponse(true, fileName, targetPath, fileSize, 
                                                    message, uploadedAt, "different.user");
        
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
        assertNotEquals(response1, response4);
        assertNotEquals(response1, response5);
        assertNotEquals(response1, response6);
        assertNotEquals(response1, response7);
        assertNotEquals(response1, response8);
        assertNotEquals(response1, response9);
        assertNotEquals(response1, null);
        assertNotEquals(response1, "not an UploadResponse");
    }

    @Test
    void testEqualsWithNullFields() {
        UploadResponse response1 = new UploadResponse(false, null, null, 0L, null, null, null);
        UploadResponse response2 = new UploadResponse(false, null, null, 0L, null, null, null);
        UploadResponse response3 = new UploadResponse(false, "file", null, 0L, null, null, null);
        
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
    }

    @Test
    void testHashCode() {
        String fileName = "test.txt";
        String targetPath = "/opt/uploads/test.txt";
        long fileSize = 1024L;
        String message = "Success";
        LocalDateTime uploadedAt = LocalDateTime.now();
        String uploadedBy = "user";
        
        UploadResponse response1 = new UploadResponse(true, fileName, targetPath, fileSize, 
                                                    message, uploadedAt, uploadedBy);
        UploadResponse response2 = new UploadResponse(true, fileName, targetPath, fileSize, 
                                                    message, uploadedAt, uploadedBy);
        UploadResponse response3 = new UploadResponse(false, fileName, targetPath, fileSize, 
                                                    message, uploadedAt, uploadedBy);
        
        assertEquals(response1.hashCode(), response2.hashCode());
        assertNotEquals(response1.hashCode(), response3.hashCode());
    }

    @Test
    void testHashCodeWithNullFields() {
        UploadResponse response1 = new UploadResponse(false, null, null, 0L, null, null, null);
        UploadResponse response2 = new UploadResponse(false, null, null, 0L, null, null, null);
        
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testEqualsSameInstance() {
        UploadResponse response = new UploadResponse("file.txt", "Upload failed");
        
        assertEquals(response, response);
    }

    @Test
    void testSuccessfulUploadHasDefaultMessage() {
        UploadResponse response = new UploadResponse("file.txt", "/path", 1024L, "user");
        
        assertTrue(response.isSuccess());
        assertEquals("File uploaded successfully", response.getMessage());
    }

    @Test
    void testFailedUploadHasNoTargetPathOrUser() {
        UploadResponse response = new UploadResponse("file.txt", "Error message");
        
        assertFalse(response.isSuccess());
        assertNull(response.getTargetPath());
        assertNull(response.getUploadedBy());
        assertEquals(0L, response.getFileSize());
    }

    @Test
    void testWithZeroFileSize() {
        UploadResponse response = new UploadResponse("empty.txt", "/path", 0L, "user");
        
        assertEquals(0L, response.getFileSize());
    }

    @Test
    void testWithNegativeFileSize() {
        UploadResponse response = new UploadResponse();
        response.setFileSize(-1L);
        
        assertEquals(-1L, response.getFileSize());
    }
}