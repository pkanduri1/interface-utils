package com.fabric.watcher.archive.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UploadRequest}.
 */
class UploadRequestTest {

    private Validator validator;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        mockFile = new MockMultipartFile("test.txt", "test.txt", "text/plain", "test content".getBytes());
    }

    @Test
    void testDefaultConstructor() {
        UploadRequest request = new UploadRequest();
        
        assertNull(request.getFile());
        assertNull(request.getTargetPath());
        assertFalse(request.isOverwrite());
        assertNull(request.getDescription());
    }

    @Test
    void testRequiredParametersConstructor() {
        String targetPath = "/opt/uploads/test.txt";
        
        UploadRequest request = new UploadRequest(mockFile, targetPath);
        
        assertEquals(mockFile, request.getFile());
        assertEquals(targetPath, request.getTargetPath());
        assertFalse(request.isOverwrite());
        assertNull(request.getDescription());
    }

    @Test
    void testFullParametersConstructor() {
        String targetPath = "/opt/uploads/test.txt";
        boolean overwrite = true;
        String description = "Test file upload";
        
        UploadRequest request = new UploadRequest(mockFile, targetPath, overwrite, description);
        
        assertEquals(mockFile, request.getFile());
        assertEquals(targetPath, request.getTargetPath());
        assertEquals(overwrite, request.isOverwrite());
        assertEquals(description, request.getDescription());
    }

    @Test
    void testGettersAndSetters() {
        UploadRequest request = new UploadRequest();
        String targetPath = "/opt/uploads/config.properties";
        boolean overwrite = true;
        String description = "Configuration update";
        
        request.setFile(mockFile);
        request.setTargetPath(targetPath);
        request.setOverwrite(overwrite);
        request.setDescription(description);
        
        assertEquals(mockFile, request.getFile());
        assertEquals(targetPath, request.getTargetPath());
        assertEquals(overwrite, request.isOverwrite());
        assertEquals(description, request.getDescription());
    }

    @Test
    void testValidRequest() {
        UploadRequest request = new UploadRequest(mockFile, "/opt/uploads/test.txt");
        
        Set<ConstraintViolation<UploadRequest>> violations = validator.validate(request);
        
        assertTrue(violations.isEmpty());
    }

    @Test
    void testNullFile() {
        UploadRequest request = new UploadRequest(null, "/opt/uploads/test.txt");
        
        Set<ConstraintViolation<UploadRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("File cannot be null")));
    }

    @Test
    void testBlankTargetPath() {
        UploadRequest request = new UploadRequest(mockFile, "");
        
        Set<ConstraintViolation<UploadRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Target path cannot be blank")));
    }

    @Test
    void testNullTargetPath() {
        UploadRequest request = new UploadRequest(mockFile, null);
        
        Set<ConstraintViolation<UploadRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Target path cannot be blank")));
    }

    @Test
    void testTargetPathTooLong() {
        String longPath = "/opt/uploads/" + "a".repeat(500); // Over 500 characters
        UploadRequest request = new UploadRequest(mockFile, longPath);
        
        Set<ConstraintViolation<UploadRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Target path must be between 1 and 500 characters")));
    }

    @Test
    void testDescriptionTooLong() {
        String longDescription = "a".repeat(256); // Over 255 characters
        UploadRequest request = new UploadRequest(mockFile, "/opt/uploads/test.txt", false, longDescription);
        
        Set<ConstraintViolation<UploadRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Description must not exceed 255 characters")));
    }

    @Test
    void testValidDescriptionLength() {
        String validDescription = "a".repeat(255); // Exactly 255 characters
        UploadRequest request = new UploadRequest(mockFile, "/opt/uploads/test.txt", false, validDescription);
        
        Set<ConstraintViolation<UploadRequest>> violations = validator.validate(request);
        
        assertTrue(violations.isEmpty());
    }

    @Test
    void testToString() {
        String targetPath = "/opt/uploads/test.txt";
        String description = "Test upload";
        UploadRequest request = new UploadRequest(mockFile, targetPath, true, description);
        
        String toString = request.toString();
        
        assertTrue(toString.contains("test.txt"));
        assertTrue(toString.contains(targetPath));
        assertTrue(toString.contains("overwrite=true"));
        assertTrue(toString.contains(description));
    }

    @Test
    void testToStringWithNullFile() {
        UploadRequest request = new UploadRequest(null, "/opt/uploads/test.txt");
        
        String toString = request.toString();
        
        assertTrue(toString.contains("file=null"));
    }

    @Test
    void testEquals() {
        String targetPath = "/opt/uploads/test.txt";
        String description = "Test upload";
        
        UploadRequest request1 = new UploadRequest(mockFile, targetPath, true, description);
        UploadRequest request2 = new UploadRequest(mockFile, targetPath, true, description);
        UploadRequest request3 = new UploadRequest(mockFile, "/different/path", true, description);
        UploadRequest request4 = new UploadRequest(mockFile, targetPath, false, description);
        UploadRequest request5 = new UploadRequest(mockFile, targetPath, true, "Different description");
        
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
        assertNotEquals(request1, request4);
        assertNotEquals(request1, request5);
        assertNotEquals(request1, null);
        assertNotEquals(request1, "not an UploadRequest");
    }

    @Test
    void testEqualsWithNullFields() {
        UploadRequest request1 = new UploadRequest(null, null, false, null);
        UploadRequest request2 = new UploadRequest(null, null, false, null);
        UploadRequest request3 = new UploadRequest(mockFile, null, false, null);
        
        assertEquals(request1, request2);
        assertNotEquals(request1, request3);
    }

    @Test
    void testHashCode() {
        String targetPath = "/opt/uploads/test.txt";
        String description = "Test upload";
        
        UploadRequest request1 = new UploadRequest(mockFile, targetPath, true, description);
        UploadRequest request2 = new UploadRequest(mockFile, targetPath, true, description);
        UploadRequest request3 = new UploadRequest(mockFile, "/different/path", true, description);
        
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1.hashCode(), request3.hashCode());
    }

    @Test
    void testHashCodeWithNullFields() {
        UploadRequest request1 = new UploadRequest(null, null, false, null);
        UploadRequest request2 = new UploadRequest(null, null, false, null);
        
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void testEqualsSameInstance() {
        UploadRequest request = new UploadRequest(mockFile, "/opt/uploads/test.txt");
        
        assertEquals(request, request);
    }

    @Test
    void testOverwriteDefaultValue() {
        UploadRequest request = new UploadRequest(mockFile, "/opt/uploads/test.txt");
        
        assertFalse(request.isOverwrite());
    }

    @Test
    void testWithDifferentMultipartFiles() {
        MultipartFile file1 = new MockMultipartFile("file1.txt", "file1.txt", "text/plain", "content1".getBytes());
        MultipartFile file2 = new MockMultipartFile("file2.txt", "file2.txt", "text/plain", "content2".getBytes());
        
        UploadRequest request1 = new UploadRequest(file1, "/opt/uploads/test.txt");
        UploadRequest request2 = new UploadRequest(file2, "/opt/uploads/test.txt");
        
        assertNotEquals(request1, request2);
    }
}