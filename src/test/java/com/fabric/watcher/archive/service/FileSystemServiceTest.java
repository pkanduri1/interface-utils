package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.FileInfo;
import com.fabric.watcher.archive.model.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FileSystemService.
 */
class FileSystemServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ArchiveSearchProperties properties;

    private FileSystemService fileSystemService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up default mock behavior
        when(properties.getMaxDirectoryDepth()).thenReturn(10);
        when(properties.getMaxFileSize()).thenReturn(100L * 1024 * 1024); // 100MB
        when(properties.getMaxSearchResults()).thenReturn(100);
        when(properties.getSupportedArchiveTypes()).thenReturn(Arrays.asList("zip", "tar", "tar.gz", "jar"));
        
        fileSystemService = new FileSystemService(properties);
    }

    @Test
    void testScanDirectory_WithMatchingFiles() throws IOException {
        // Create test files
        Path file1 = tempDir.resolve("test1.txt");
        Path file2 = tempDir.resolve("test2.txt");
        Path file3 = tempDir.resolve("other.log");
        
        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        // Test wildcard pattern matching
        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "test*.txt");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("test1.txt")));
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("test2.txt")));
        assertFalse(results.stream().anyMatch(f -> f.getFileName().equals("other.log")));
    }

    @Test
    void testScanDirectory_WithQuestionMarkWildcard() throws IOException {
        // Create test files
        Path file1 = tempDir.resolve("test1.txt");
        Path file2 = tempDir.resolve("test2.txt");
        Path file3 = tempDir.resolve("test10.txt");
        
        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        // Test single character wildcard
        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "test?.txt");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("test1.txt")));
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("test2.txt")));
        assertFalse(results.stream().anyMatch(f -> f.getFileName().equals("test10.txt")));
    }

    @Test
    void testScanDirectory_WithSubdirectories() throws IOException {
        // Create subdirectory structure
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        
        Path file1 = tempDir.resolve("root.txt");
        Path file2 = subDir.resolve("sub.txt");
        
        Files.createFile(file1);
        Files.createFile(file2);

        // Test recursive scanning
        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "*.txt");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("root.txt")));
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("sub.txt")));
    }

    @Test
    void testScanDirectory_WithDepthLimit() throws IOException {
        // Create deep directory structure
        Path level1 = tempDir.resolve("level1");
        Path level2 = level1.resolve("level2");
        Files.createDirectories(level2);
        
        Path file1 = tempDir.resolve("root.txt");
        Path file2 = level1.resolve("level1.txt");
        Path file3 = level2.resolve("level2.txt");
        
        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        // Set depth limit to 1
        when(properties.getMaxDirectoryDepth()).thenReturn(1);

        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "*.txt");

        // Should only find files at root and level1, not level2
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("root.txt")));
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("level1.txt")));
        assertFalse(results.stream().anyMatch(f -> f.getFileName().equals("level2.txt")));
    }

    @Test
    void testScanDirectory_WithFileSizeLimit() throws IOException {
        // Create a test file
        Path file1 = tempDir.resolve("small.txt");
        Files.createFile(file1);
        Files.write(file1, "small content".getBytes());

        // Set very small file size limit
        when(properties.getMaxFileSize()).thenReturn(5L);

        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "*.txt");

        // File should be excluded due to size limit
        assertEquals(0, results.size());
    }

    @Test
    void testScanDirectory_WithResultsLimit() throws IOException {
        // Create multiple test files
        for (int i = 1; i <= 10; i++) {
            Path file = tempDir.resolve("test" + i + ".txt");
            Files.createFile(file);
        }

        // Set results limit to 5
        when(properties.getMaxSearchResults()).thenReturn(5);

        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "*.txt");

        assertEquals(5, results.size());
    }

    @Test
    void testScanDirectory_NonExistentDirectory() throws IOException {
        Path nonExistent = tempDir.resolve("nonexistent");

        List<FileInfo> results = fileSystemService.scanDirectory(nonExistent, "*.txt");

        assertTrue(results.isEmpty());
    }

    @Test
    void testScanDirectory_NullDirectory() {
        assertThrows(IllegalArgumentException.class, () -> {
            fileSystemService.scanDirectory(null, "*.txt");
        });
    }

    @Test
    void testScanDirectory_NullPattern() {
        assertThrows(IllegalArgumentException.class, () -> {
            fileSystemService.scanDirectory(tempDir, null);
        });
    }

    @Test
    void testScanDirectory_EmptyPattern() {
        assertThrows(IllegalArgumentException.class, () -> {
            fileSystemService.scanDirectory(tempDir, "");
        });
    }

    @Test
    void testScanDirectory_FileInfoProperties() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.createFile(file);
        Files.write(file, "test content".getBytes());

        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "test.txt");

        assertEquals(1, results.size());
        FileInfo fileInfo = results.get(0);
        
        assertEquals("test.txt", fileInfo.getFileName());
        assertEquals(file.toString(), fileInfo.getFullPath());
        assertEquals("test.txt", fileInfo.getRelativePath());
        assertEquals(FileType.REGULAR, fileInfo.getType());
        assertNull(fileInfo.getArchivePath());
        assertTrue(fileInfo.getSize() > 0);
        assertNotNull(fileInfo.getLastModified());
    }

    @Test
    void testIsValidPath_ValidPath() throws IOException {
        Path validFile = tempDir.resolve("valid.txt");
        Files.createFile(validFile);

        assertTrue(fileSystemService.isValidPath(validFile.toString()));
    }

    @Test
    void testIsValidPath_NonExistentPath() {
        Path nonExistent = tempDir.resolve("nonexistent.txt");

        assertFalse(fileSystemService.isValidPath(nonExistent.toString()));
    }

    @Test
    void testIsValidPath_NullPath() {
        assertFalse(fileSystemService.isValidPath(null));
    }

    @Test
    void testIsValidPath_EmptyPath() {
        assertFalse(fileSystemService.isValidPath(""));
    }

    @Test
    void testIsValidPath_InvalidPathFormat() {
        // Test with invalid characters (this may be platform-specific)
        assertFalse(fileSystemService.isValidPath("\0invalid"));
    }

    @Test
    void testGetFileMetadata_ValidFile() throws IOException {
        Path file = tempDir.resolve("metadata.txt");
        Files.createFile(file);
        Files.write(file, "metadata test".getBytes());

        FileSystemService.FileMetadata metadata = fileSystemService.getFileMetadata(file);

        assertEquals("metadata.txt", metadata.getFileName());
        assertEquals(file.toString(), metadata.getFullPath());
        assertTrue(metadata.getSize() > 0);
        assertNotNull(metadata.getLastModified());
        assertTrue(metadata.isRegularFile());
        assertFalse(metadata.isDirectory());
        assertTrue(metadata.isReadable());
    }

    @Test
    void testGetFileMetadata_Directory() throws IOException {
        Path dir = tempDir.resolve("testdir");
        Files.createDirectory(dir);

        FileSystemService.FileMetadata metadata = fileSystemService.getFileMetadata(dir);

        assertEquals("testdir", metadata.getFileName());
        assertFalse(metadata.isRegularFile());
        assertTrue(metadata.isDirectory());
    }

    @Test
    void testGetFileMetadata_NonExistentFile() {
        Path nonExistent = tempDir.resolve("nonexistent.txt");

        assertThrows(IOException.class, () -> {
            fileSystemService.getFileMetadata(nonExistent);
        });
    }

    @Test
    void testGetFileMetadata_NullFile() {
        assertThrows(IllegalArgumentException.class, () -> {
            fileSystemService.getFileMetadata(null);
        });
    }

    @Test
    void testIsArchiveFile_ZipFile() {
        Path zipFile = Paths.get("test.zip");
        assertTrue(fileSystemService.isArchiveFile(zipFile));
    }

    @Test
    void testIsArchiveFile_TarFile() {
        Path tarFile = Paths.get("test.tar");
        assertTrue(fileSystemService.isArchiveFile(tarFile));
    }

    @Test
    void testIsArchiveFile_TarGzFile() {
        Path tarGzFile = Paths.get("test.tar.gz");
        assertTrue(fileSystemService.isArchiveFile(tarGzFile));
    }

    @Test
    void testIsArchiveFile_JarFile() {
        Path jarFile = Paths.get("test.jar");
        assertTrue(fileSystemService.isArchiveFile(jarFile));
    }

    @Test
    void testIsArchiveFile_RegularFile() {
        Path txtFile = Paths.get("test.txt");
        assertFalse(fileSystemService.isArchiveFile(txtFile));
    }

    @Test
    void testIsArchiveFile_CaseInsensitive() {
        Path zipFile = Paths.get("TEST.ZIP");
        assertTrue(fileSystemService.isArchiveFile(zipFile));
    }

    @Test
    void testIsArchiveFile_NullFile() {
        assertFalse(fileSystemService.isArchiveFile(null));
    }

    @Test
    void testWildcardPatternMatching_ComplexPattern() throws IOException {
        // Create test files with various names
        Path file1 = tempDir.resolve("app-1.2.3.jar");
        Path file2 = tempDir.resolve("app-2.0.0.jar");
        Path file3 = tempDir.resolve("lib-1.0.jar");
        Path file4 = tempDir.resolve("app.war");
        
        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);
        Files.createFile(file4);

        // Test complex wildcard pattern
        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "app-*.jar");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("app-1.2.3.jar")));
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("app-2.0.0.jar")));
        assertFalse(results.stream().anyMatch(f -> f.getFileName().equals("lib-1.0.jar")));
        assertFalse(results.stream().anyMatch(f -> f.getFileName().equals("app.war")));
    }

    @Test
    void testWildcardPatternMatching_SpecialCharacters() throws IOException {
        // Create files with special characters in names
        Path file1 = tempDir.resolve("test[1].txt");
        Path file2 = tempDir.resolve("test(2).txt");
        Path file3 = tempDir.resolve("test{3}.txt");
        
        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        // Test that special regex characters are properly escaped
        List<FileInfo> results = fileSystemService.scanDirectory(tempDir, "test[1].txt");

        assertEquals(1, results.size());
        assertEquals("test[1].txt", results.get(0).getFileName());
    }
}