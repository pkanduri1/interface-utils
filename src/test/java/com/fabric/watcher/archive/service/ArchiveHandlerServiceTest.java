package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.FileInfo;
import com.fabric.watcher.archive.model.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ArchiveHandlerService.
 */
class ArchiveHandlerServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ArchiveSearchProperties properties;

    private ArchiveHandlerService archiveHandlerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up default mock behavior
        when(properties.getMaxFileSize()).thenReturn(100L * 1024 * 1024); // 100MB
        when(properties.getMaxSearchResults()).thenReturn(100);
        when(properties.getSupportedArchiveTypes()).thenReturn(Arrays.asList("zip", "tar", "tar.gz", "jar"));
        
        archiveHandlerService = new ArchiveHandlerService(properties);
    }

    @Test
    void testIsArchiveFile_ZipFile() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        Files.createFile(zipFile);

        assertTrue(archiveHandlerService.isArchiveFile(zipFile));
    }

    @Test
    void testIsArchiveFile_JarFile() throws IOException {
        Path jarFile = tempDir.resolve("test.jar");
        Files.createFile(jarFile);

        assertTrue(archiveHandlerService.isArchiveFile(jarFile));
    }

    @Test
    void testIsArchiveFile_TarFile() throws IOException {
        Path tarFile = tempDir.resolve("test.tar");
        Files.createFile(tarFile);

        assertTrue(archiveHandlerService.isArchiveFile(tarFile));
    }

    @Test
    void testIsArchiveFile_TarGzFile() throws IOException {
        Path tarGzFile = tempDir.resolve("test.tar.gz");
        Files.createFile(tarGzFile);

        assertTrue(archiveHandlerService.isArchiveFile(tarGzFile));
    }

    @Test
    void testIsArchiveFile_RegularFile() throws IOException {
        Path txtFile = tempDir.resolve("test.txt");
        Files.createFile(txtFile);

        assertFalse(archiveHandlerService.isArchiveFile(txtFile));
    }

    @Test
    void testIsArchiveFile_NonExistentFile() {
        Path nonExistent = tempDir.resolve("nonexistent.zip");

        assertFalse(archiveHandlerService.isArchiveFile(nonExistent));
    }

    @Test
    void testIsArchiveFile_NullFile() {
        assertFalse(archiveHandlerService.isArchiveFile(null));
    }

    @Test
    void testGetArchiveFormat_ZipFile() {
        Path zipFile = tempDir.resolve("test.zip");
        assertEquals("zip", archiveHandlerService.getArchiveFormat(zipFile));
    }

    @Test
    void testGetArchiveFormat_JarFile() {
        Path jarFile = tempDir.resolve("test.jar");
        assertEquals("jar", archiveHandlerService.getArchiveFormat(jarFile));
    }

    @Test
    void testGetArchiveFormat_TarFile() {
        Path tarFile = tempDir.resolve("test.tar");
        assertEquals("tar", archiveHandlerService.getArchiveFormat(tarFile));
    }

    @Test
    void testGetArchiveFormat_TarGzFile() {
        Path tarGzFile = tempDir.resolve("test.tar.gz");
        assertEquals("tar.gz", archiveHandlerService.getArchiveFormat(tarGzFile));
    }

    @Test
    void testGetArchiveFormat_UnknownFile() {
        Path unknownFile = tempDir.resolve("test.unknown");
        assertEquals("unknown", archiveHandlerService.getArchiveFormat(unknownFile));
    }

    @Test
    void testGetArchiveFormat_NullFile() {
        assertEquals("unknown", archiveHandlerService.getArchiveFormat(null));
    }

    @Test
    void testListArchiveContents_ZipArchive() throws IOException {
        // Create a test ZIP file
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        List<FileInfo> results = archiveHandlerService.listArchiveContents(zipFile, "*.txt");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("file1.txt")));
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("file2.txt")));
        
        // Verify FileInfo properties
        FileInfo fileInfo = results.get(0);
        assertEquals(FileType.ARCHIVE_ENTRY, fileInfo.getType());
        assertEquals(zipFile.toString(), fileInfo.getArchivePath());
        assertNotNull(fileInfo.getLastModified());
    }

    @Test
    void testListArchiveContents_JarArchive() throws IOException {
        // Create a test JAR file
        Path jarFile = tempDir.resolve("test.jar");
        createTestJarFile(jarFile);

        List<FileInfo> results = archiveHandlerService.listArchiveContents(jarFile, "*.class");

        assertEquals(1, results.size());
        assertEquals("Test.class", results.get(0).getFileName());
        assertEquals(FileType.ARCHIVE_ENTRY, results.get(0).getType());
    }

    @Test
    void testListArchiveContents_TarArchive() throws IOException {
        // Create a test TAR file
        Path tarFile = tempDir.resolve("test.tar");
        createTestTarFile(tarFile, false);

        List<FileInfo> results = archiveHandlerService.listArchiveContents(tarFile, "*.txt");

        assertEquals(1, results.size());
        assertEquals("document.txt", results.get(0).getFileName());
        assertEquals(FileType.ARCHIVE_ENTRY, results.get(0).getType());
    }

    @Test
    void testListArchiveContents_TarGzArchive() throws IOException {
        // Create a test TAR.GZ file
        Path tarGzFile = tempDir.resolve("test.tar.gz");
        createTestTarFile(tarGzFile, true);

        List<FileInfo> results = archiveHandlerService.listArchiveContents(tarGzFile, "*.txt");

        assertEquals(1, results.size());
        assertEquals("document.txt", results.get(0).getFileName());
        assertEquals(FileType.ARCHIVE_ENTRY, results.get(0).getType());
    }

    @Test
    void testListArchiveContents_WithWildcardPattern() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        List<FileInfo> results = archiveHandlerService.listArchiveContents(zipFile, "file?.txt");

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("file1.txt")));
        assertTrue(results.stream().anyMatch(f -> f.getFileName().equals("file2.txt")));
    }

    @Test
    void testListArchiveContents_NoMatches() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        List<FileInfo> results = archiveHandlerService.listArchiveContents(zipFile, "*.pdf");

        assertTrue(results.isEmpty());
    }

    @Test
    void testListArchiveContents_NonExistentArchive() throws IOException {
        Path nonExistent = tempDir.resolve("nonexistent.zip");

        List<FileInfo> results = archiveHandlerService.listArchiveContents(nonExistent, "*.txt");

        assertTrue(results.isEmpty());
    }

    @Test
    void testListArchiveContents_NullArchivePath() {
        assertThrows(IllegalArgumentException.class, () -> {
            archiveHandlerService.listArchiveContents(null, "*.txt");
        });
    }

    @Test
    void testListArchiveContents_NullPattern() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        assertThrows(IllegalArgumentException.class, () -> {
            archiveHandlerService.listArchiveContents(zipFile, null);
        });
    }

    @Test
    void testListArchiveContents_EmptyPattern() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        assertThrows(IllegalArgumentException.class, () -> {
            archiveHandlerService.listArchiveContents(zipFile, "");
        });
    }

    @Test
    void testListArchiveContents_UnsupportedArchive() throws IOException {
        Path txtFile = tempDir.resolve("test.txt");
        Files.write(txtFile, "test content".getBytes());

        List<FileInfo> results = archiveHandlerService.listArchiveContents(txtFile, "*.txt");

        assertTrue(results.isEmpty());
    }

    @Test
    void testListArchiveContents_FileSizeLimit() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        // Set a very small file size limit
        when(properties.getMaxFileSize()).thenReturn(10L);

        List<FileInfo> results = archiveHandlerService.listArchiveContents(zipFile, "*.txt");

        assertTrue(results.isEmpty());
    }

    @Test
    void testListArchiveContents_ResultsLimit() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createLargeTestZipFile(zipFile);

        // Set a small results limit
        when(properties.getMaxSearchResults()).thenReturn(2);

        List<FileInfo> results = archiveHandlerService.listArchiveContents(zipFile, "*.txt");

        assertEquals(2, results.size());
    }

    @Test
    void testExtractFileFromArchive_ZipFile() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        try (InputStream is = archiveHandlerService.extractFileFromArchive(zipFile, "file1.txt");
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            is.transferTo(baos);
            String content = baos.toString();
            assertEquals("Content of file1", content);
        }
    }

    @Test
    void testExtractFileFromArchive_JarFile() throws IOException {
        Path jarFile = tempDir.resolve("test.jar");
        createTestJarFile(jarFile);

        try (InputStream is = archiveHandlerService.extractFileFromArchive(jarFile, "Test.class");
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            is.transferTo(baos);
            String content = baos.toString();
            assertEquals("Test class content", content);
        }
    }

    @Test
    void testExtractFileFromArchive_TarFile() throws IOException {
        Path tarFile = tempDir.resolve("test.tar");
        createTestTarFile(tarFile, false);

        try (InputStream is = archiveHandlerService.extractFileFromArchive(tarFile, "document.txt");
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            is.transferTo(baos);
            String content = baos.toString();
            assertEquals("Document content", content);
        }
    }

    @Test
    void testExtractFileFromArchive_TarGzFile() throws IOException {
        Path tarGzFile = tempDir.resolve("test.tar.gz");
        createTestTarFile(tarGzFile, true);

        try (InputStream is = archiveHandlerService.extractFileFromArchive(tarGzFile, "document.txt");
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            is.transferTo(baos);
            String content = baos.toString();
            assertEquals("Document content", content);
        }
    }

    @Test
    void testExtractFileFromArchive_FileNotFound() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        assertThrows(IOException.class, () -> {
            archiveHandlerService.extractFileFromArchive(zipFile, "nonexistent.txt");
        });
    }

    @Test
    void testExtractFileFromArchive_NullArchivePath() {
        assertThrows(IllegalArgumentException.class, () -> {
            archiveHandlerService.extractFileFromArchive(null, "file.txt");
        });
    }

    @Test
    void testExtractFileFromArchive_NullEntryPath() throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        createTestZipFile(zipFile);

        assertThrows(IllegalArgumentException.class, () -> {
            archiveHandlerService.extractFileFromArchive(zipFile, null);
        });
    }

    @Test
    void testExtractFileFromArchive_NonExistentArchive() {
        Path nonExistent = tempDir.resolve("nonexistent.zip");

        assertThrows(IOException.class, () -> {
            archiveHandlerService.extractFileFromArchive(nonExistent, "file.txt");
        });
    }

    @Test
    void testExtractFileFromArchive_UnsupportedArchive() throws IOException {
        Path txtFile = tempDir.resolve("test.txt");
        Files.write(txtFile, "test content".getBytes());

        assertThrows(IOException.class, () -> {
            archiveHandlerService.extractFileFromArchive(txtFile, "file.txt");
        });
    }

    // Helper methods to create test archive files

    private void createTestZipFile(Path zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            // Add file1.txt
            ZipEntry entry1 = new ZipEntry("file1.txt");
            zos.putNextEntry(entry1);
            zos.write("Content of file1".getBytes());
            zos.closeEntry();
            
            // Add file2.txt
            ZipEntry entry2 = new ZipEntry("file2.txt");
            zos.putNextEntry(entry2);
            zos.write("Content of file2".getBytes());
            zos.closeEntry();
            
            // Add a non-matching file
            ZipEntry entry3 = new ZipEntry("readme.md");
            zos.putNextEntry(entry3);
            zos.write("Readme content".getBytes());
            zos.closeEntry();
        }
    }

    private void createLargeTestZipFile(Path zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            // Create 10 test files
            for (int i = 1; i <= 10; i++) {
                ZipEntry entry = new ZipEntry("file" + i + ".txt");
                zos.putNextEntry(entry);
                zos.write(("Content of file" + i).getBytes());
                zos.closeEntry();
            }
        }
    }

    private void createTestJarFile(Path jarFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(jarFile.toFile());
             JarOutputStream jos = new JarOutputStream(fos)) {
            
            // Add Test.class
            JarEntry entry1 = new JarEntry("Test.class");
            jos.putNextEntry(entry1);
            jos.write("Test class content".getBytes());
            jos.closeEntry();
            
            // Add a text file
            JarEntry entry2 = new JarEntry("config.properties");
            jos.putNextEntry(entry2);
            jos.write("config=value".getBytes());
            jos.closeEntry();
        }
    }

    private void createTestTarFile(Path tarFile, boolean gzipped) throws IOException {
        FileOutputStream fos = new FileOutputStream(tarFile.toFile());
        OutputStream outputStream = gzipped ? new GzipCompressorOutputStream(fos) : fos;
        
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(outputStream)) {
            // Add document.txt
            TarArchiveEntry entry = new TarArchiveEntry("document.txt");
            byte[] content = "Document content".getBytes();
            entry.setSize(content.length);
            tos.putArchiveEntry(entry);
            tos.write(content);
            tos.closeArchiveEntry();
        }
    }
}