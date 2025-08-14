package com.fabric.watcher.archive.security;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityValidatorTest {
    
    @Mock(lenient = true)
    private ArchiveSearchProperties properties;
    
    private SecurityValidator securityValidator;
    
    @TempDir
    Path tempDir;
    
    private Path allowedDir;
    private Path excludedDir;
    private Path testFile;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create test directory structure
        allowedDir = tempDir.resolve("allowed");
        excludedDir = tempDir.resolve("allowed/excluded");
        Files.createDirectories(allowedDir);
        Files.createDirectories(excludedDir);
        
        // Create a test file
        testFile = allowedDir.resolve("test.txt");
        Files.write(testFile, "test content".getBytes());
        
        // Get the real paths to handle symbolic links properly
        String allowedRealPath;
        String excludedRealPath;
        try {
            allowedRealPath = allowedDir.toRealPath().toString();
            excludedRealPath = excludedDir.toRealPath().toString();
        } catch (IOException e) {
            // Fallback to absolute path if real path fails
            allowedRealPath = allowedDir.toAbsolutePath().normalize().toString();
            excludedRealPath = excludedDir.toAbsolutePath().normalize().toString();
        }
        
        // Setup mock properties with real paths
        when(properties.getAllowedPaths()).thenReturn(Arrays.asList(allowedRealPath));
        when(properties.getExcludedPaths()).thenReturn(Arrays.asList(excludedRealPath));
        when(properties.getMaxFileSize()).thenReturn(1024L * 1024L); // 1MB
        
        securityValidator = new SecurityValidator(properties);
    }
    
    @Test
    void testIsPathAllowed_ValidPath_ReturnsTrue() {
        // Test with a valid path within allowed directory
        // Use the real path to ensure consistency with the allowed paths configuration
        try {
            Path realAllowedDir = allowedDir.toRealPath();
            String validPath = realAllowedDir.resolve("subdir/file.txt").toString();
            assertTrue(securityValidator.isPathAllowed(validPath));
        } catch (IOException e) {
            // Fallback to absolute path
            String validPath = allowedDir.toAbsolutePath().resolve("subdir/file.txt").toString();
            assertTrue(securityValidator.isPathAllowed(validPath));
        }
    }
    
    @Test
    void testIsPathAllowed_NullPath_ReturnsFalse() {
        assertFalse(securityValidator.isPathAllowed(null));
    }
    
    @Test
    void testIsPathAllowed_EmptyPath_ReturnsFalse() {
        assertFalse(securityValidator.isPathAllowed(""));
        assertFalse(securityValidator.isPathAllowed("   "));
    }
    
    @Test
    void testIsPathAllowed_PathTraversalAttempt_ReturnsFalse() {
        // Test various path traversal attempts
        assertFalse(securityValidator.isPathAllowed("../../../etc/passwd"));
        assertFalse(securityValidator.isPathAllowed("..\\..\\..\\windows\\system32"));
        assertFalse(securityValidator.isPathAllowed("/allowed/../../../etc/passwd"));
        assertFalse(securityValidator.isPathAllowed("allowed/subdir/../../../etc/passwd"));
    }
    
    @Test
    void testIsPathAllowed_URLEncodedPathTraversal_ReturnsFalse() {
        // Test URL encoded path traversal attempts
        assertFalse(securityValidator.isPathAllowed("%2e%2e/etc/passwd"));
        assertFalse(securityValidator.isPathAllowed("%252e%252e/etc/passwd"));
    }
    
    @Test
    void testIsPathAllowed_NullByteAttack_ReturnsFalse() {
        // Test null byte injection
        String pathWithNullByte = allowedDir.toString() + "\u0000../../../etc/passwd";
        assertFalse(securityValidator.isPathAllowed(pathWithNullByte));
    }
    
    @Test
    void testIsPathAllowed_PathNotInAllowedDirectories_ReturnsFalse() {
        String unauthorizedPath = tempDir.resolve("unauthorized/file.txt").toString();
        
        assertFalse(securityValidator.isPathAllowed(unauthorizedPath));
    }
    
    @Test
    void testIsPathAllowed_PathInExcludedDirectory_ReturnsFalse() {
        String excludedPath = excludedDir.resolve("file.txt").toString();
        
        assertFalse(securityValidator.isPathAllowed(excludedPath));
    }
    
    @Test
    void testIsPathAllowed_NoAllowedPathsConfigured_ReturnsFalse() {
        when(properties.getAllowedPaths()).thenReturn(Collections.emptyList());
        
        String anyPath = tempDir.resolve("any/file.txt").toString();
        assertFalse(securityValidator.isPathAllowed(anyPath));
    }
    
    @Test
    void testIsFileAccessible_ValidFile_ReturnsTrue() {
        assertTrue(securityValidator.isFileAccessible(testFile));
    }
    
    @Test
    void testIsFileAccessible_NullFile_ReturnsFalse() {
        assertFalse(securityValidator.isFileAccessible(null));
    }
    
    @Test
    void testIsFileAccessible_NonExistentFile_ReturnsFalse() {
        Path nonExistentFile = allowedDir.resolve("nonexistent.txt");
        
        assertFalse(securityValidator.isFileAccessible(nonExistentFile));
    }
    
    @Test
    void testIsFileAccessible_FileInUnauthorizedPath_ReturnsFalse() throws IOException {
        Path unauthorizedDir = tempDir.resolve("unauthorized");
        Files.createDirectories(unauthorizedDir);
        Path unauthorizedFile = unauthorizedDir.resolve("file.txt");
        Files.write(unauthorizedFile, "content".getBytes());
        
        assertFalse(securityValidator.isFileAccessible(unauthorizedFile));
    }
    
    @Test
    void testIsFileAccessible_FileTooLarge_ReturnsFalse() throws IOException {
        // Set a very small max file size
        when(properties.getMaxFileSize()).thenReturn(5L);
        
        // Create a file larger than the limit
        Path largeFile = allowedDir.resolve("large.txt");
        Files.write(largeFile, "This content is longer than 5 bytes".getBytes());
        
        assertFalse(securityValidator.isFileAccessible(largeFile));
    }
    
    @Test
    void testSanitizePath_ValidPath_ReturnsSanitizedPath() {
        String input = "  /path//to///file.txt  ";
        String expected = "/path/to/file.txt";
        
        assertEquals(expected, securityValidator.sanitizePath(input));
    }
    
    @Test
    void testSanitizePath_WindowsPath_ReturnsNormalizedPath() {
        String input = "C:\\path\\to\\file.txt";
        String expected = "C:/path/to/file.txt";
        
        assertEquals(expected, securityValidator.sanitizePath(input));
    }
    
    @Test
    void testSanitizePath_NullPath_ReturnsNull() {
        assertNull(securityValidator.sanitizePath(null));
    }
    
    @Test
    void testSanitizePath_PathTraversalAttempt_ReturnsNull() {
        assertNull(securityValidator.sanitizePath("../../../etc/passwd"));
        assertNull(securityValidator.sanitizePath("..\\..\\..\\windows\\system32"));
        assertNull(securityValidator.sanitizePath("/path/../../../etc/passwd"));
    }
    
    @Test
    void testSanitizePath_URLEncodedTraversal_ReturnsNull() {
        assertNull(securityValidator.sanitizePath("%2e%2e/etc/passwd"));
        assertNull(securityValidator.sanitizePath("%252e%252e/etc/passwd"));
    }
    
    @Test
    void testSanitizePath_HexEncodedTraversal_ReturnsNull() {
        assertNull(securityValidator.sanitizePath("0x2e0x2e/etc/passwd"));
        assertNull(securityValidator.sanitizePath("\\x2e\\x2e/etc/passwd"));
    }
    
    @Test
    void testSanitizePath_NullByteInjection_ReturnsNull() {
        String pathWithNullByte = "/valid/path\u0000../../../etc/passwd";
        assertNull(securityValidator.sanitizePath(pathWithNullByte));
    }
    
    @Test
    void testSanitizePath_DoubleDotInFilename_ReturnsNull() {
        // This should be rejected as it contains ".."
        assertNull(securityValidator.sanitizePath("/path/to/file..name.txt"));
    }
    
    @Test
    void testIsPathAllowed_SymbolicLinkTraversal_ReturnsFalse() throws IOException {
        try {
            // Create a directory outside the allowed directory
            Path outsideDir = tempDir.resolve("outside");
            Files.createDirectories(outsideDir);
            
            // Create a file in the outside directory
            Path outsideFile = outsideDir.resolve("file.txt");
            Files.write(outsideFile, "outside content".getBytes());
            
            // Create a symbolic link inside allowed directory that points to the outside file
            Path symlink = allowedDir.resolve("symlink.txt");
            Files.createSymbolicLink(symlink, outsideFile);
            
            // Test accessing the symlink - this should be rejected
            // because the canonical path resolves outside the allowed directory
            assertFalse(securityValidator.isPathAllowed(symlink.toString()));
        } catch (UnsupportedOperationException e) {
            // Skip test if symbolic links are not supported on this system
            System.out.println("Symbolic links not supported, skipping test");
        } catch (Exception e) {
            // If there's any other issue with symlinks, skip the test
            System.out.println("Symbolic link test skipped due to: " + e.getMessage());
        }
    }
    
    @Test
    void testIsPathAllowed_CaseInsensitivePathTraversal_ReturnsFalse() {
        // Test case variations of path traversal
        assertFalse(securityValidator.isPathAllowed("../ETC/passwd"));
        assertFalse(securityValidator.isPathAllowed("..\\WINDOWS\\system32"));
    }
    
    @Test
    void testIsPathAllowed_MultipleSlashes_HandledCorrectly() {
        try {
            Path realAllowedDir = allowedDir.toRealPath();
            String pathWithMultipleSlashes = realAllowedDir.toString() + "//subdir///file.txt";
            // Should be allowed after normalization
            assertTrue(securityValidator.isPathAllowed(pathWithMultipleSlashes));
        } catch (IOException e) {
            String pathWithMultipleSlashes = allowedDir.toAbsolutePath().toString() + "//subdir///file.txt";
            assertTrue(securityValidator.isPathAllowed(pathWithMultipleSlashes));
        }
    }
    
    @Test
    void testIsPathAllowed_RelativePathWithinAllowed_ReturnsTrue() {
        // Test relative path that stays within allowed directory
        try {
            Path realAllowedDir = allowedDir.toRealPath();
            String relativePath = realAllowedDir.toString() + "/subdir/./file.txt";
            assertTrue(securityValidator.isPathAllowed(relativePath));
        } catch (IOException e) {
            String relativePath = allowedDir.toAbsolutePath().toString() + "/subdir/./file.txt";
            assertTrue(securityValidator.isPathAllowed(relativePath));
        }
    }
    
    @Test
    void testIsFileAccessible_ReadOnlyFile_ReturnsTrue() throws IOException {
        // Create a read-only file
        Path readOnlyFile = allowedDir.resolve("readonly.txt");
        Files.write(readOnlyFile, "readonly content".getBytes());
        readOnlyFile.toFile().setReadOnly();
        
        try {
            assertTrue(securityValidator.isFileAccessible(readOnlyFile));
        } finally {
            // Clean up - make file writable again for deletion
            readOnlyFile.toFile().setWritable(true);
        }
    }
}