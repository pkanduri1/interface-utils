package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.FileInfo;
import com.fabric.watcher.archive.model.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Service for handling archive files including ZIP, TAR, TAR.GZ, and JAR formats.
 * 
 * <p>This service provides functionality to detect archive files, extract their contents,
 * and search for files within archives using wildcard patterns. It supports streaming
 * for efficient processing of large archive files.</p>
 * 
 * @since 1.0
 */
@Service
public class ArchiveHandlerService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveHandlerService.class);

    private final ArchiveSearchProperties properties;

    @Autowired
    public ArchiveHandlerService(ArchiveSearchProperties properties) {
        this.properties = properties;
    }

    /**
     * Detects if a file is a supported archive format.
     * 
     * @param file the file to check
     * @return true if the file is a supported archive format, false otherwise
     */
    public boolean isArchiveFile(Path file) {
        if (file == null || !Files.exists(file)) {
            return false;
        }

        String fileName = file.getFileName().toString().toLowerCase();
        
        return properties.getSupportedArchiveTypes().stream()
                .anyMatch(type -> fileName.endsWith("." + type.toLowerCase()));
    }

    /**
     * Lists all files within an archive that match the specified pattern.
     * 
     * @param archivePath the path to the archive file
     * @param pattern the wildcard pattern to match (supports * and ?)
     * @return list of FileInfo objects for matching files within the archive
     * @throws IOException if archive processing fails
     * @throws IllegalArgumentException if archive path or pattern is invalid
     */
    public List<FileInfo> listArchiveContents(Path archivePath, String pattern) throws IOException {
        if (archivePath == null) {
            throw new IllegalArgumentException("Archive path cannot be null");
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty");
        }

        logger.debug("Listing archive contents: {} with pattern: {}", archivePath, pattern);

        if (!Files.exists(archivePath)) {
            logger.warn("Archive file does not exist: {}", archivePath);
            return new ArrayList<>();
        }

        if (!isArchiveFile(archivePath)) {
            logger.warn("File is not a supported archive format: {}", archivePath);
            return new ArrayList<>();
        }

        // Check file size limit
        long fileSize = Files.size(archivePath);
        if (fileSize > properties.getMaxFileSize()) {
            logger.warn("Archive file exceeds size limit: {} (size: {})", archivePath, fileSize);
            return new ArrayList<>();
        }

        List<FileInfo> results = new ArrayList<>();
        Pattern regexPattern = convertWildcardToRegex(pattern);
        String fileName = archivePath.getFileName().toString().toLowerCase();

        try {
            if (fileName.endsWith(".zip")) {
                results = processZipArchive(archivePath, regexPattern);
            } else if (fileName.endsWith(".jar")) {
                results = processJarArchive(archivePath, regexPattern);
            } else if (fileName.endsWith(".tar")) {
                results = processTarArchive(archivePath, regexPattern, false);
            } else if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
                results = processTarArchive(archivePath, regexPattern, true);
            }
        } catch (IOException e) {
            logger.error("Error processing archive: {}", archivePath, e);
            throw e;
        }

        logger.debug("Found {} files matching pattern '{}' in archive: {}", results.size(), pattern, archivePath);
        return results;
    }

    /**
     * Extracts a specific file from an archive as an InputStream.
     * 
     * @param archivePath the path to the archive file
     * @param entryPath the path of the file within the archive
     * @return InputStream for the extracted file
     * @throws IOException if extraction fails or file is not found
     */
    public InputStream extractFileFromArchive(Path archivePath, String entryPath) throws IOException {
        if (archivePath == null) {
            throw new IllegalArgumentException("Archive path cannot be null");
        }
        if (entryPath == null || entryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Entry path cannot be null or empty");
        }

        logger.debug("Extracting file from archive: {} -> {}", archivePath, entryPath);

        if (!Files.exists(archivePath)) {
            throw new IOException("Archive file does not exist: " + archivePath);
        }

        if (!isArchiveFile(archivePath)) {
            throw new IOException("File is not a supported archive format: " + archivePath);
        }

        String fileName = archivePath.getFileName().toString().toLowerCase();

        try {
            if (fileName.endsWith(".zip")) {
                return extractFromZipArchive(archivePath, entryPath);
            } else if (fileName.endsWith(".jar")) {
                return extractFromJarArchive(archivePath, entryPath);
            } else if (fileName.endsWith(".tar")) {
                return extractFromTarArchive(archivePath, entryPath, false);
            } else if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
                return extractFromTarArchive(archivePath, entryPath, true);
            }
        } catch (IOException e) {
            logger.error("Error extracting file from archive: {} -> {}", archivePath, entryPath, e);
            throw e;
        }

        throw new IOException("Unsupported archive format: " + fileName);
    }

    /**
     * Gets the archive format type based on file extension.
     * 
     * @param archivePath the path to the archive file
     * @return the archive format type as a string
     */
    public String getArchiveFormat(Path archivePath) {
        if (archivePath == null) {
            return "unknown";
        }

        String fileName = archivePath.getFileName().toString().toLowerCase();
        
        if (fileName.endsWith(".zip")) {
            return "zip";
        } else if (fileName.endsWith(".jar")) {
            return "jar";
        } else if (fileName.endsWith(".tar")) {
            return "tar";
        } else if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            return "tar.gz";
        }
        
        return "unknown";
    }

    /**
     * Processes a ZIP archive and returns matching files.
     */
    private List<FileInfo> processZipArchive(Path archivePath, Pattern pattern) throws IOException {
        List<FileInfo> results = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             ZipInputStream zis = new ZipInputStream(fis)) {
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null && results.size() < properties.getMaxSearchResults()) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    String fileName = getFileNameFromPath(entryName);
                    
                    if (pattern.matcher(fileName).matches()) {
                        FileInfo fileInfo = createArchiveFileInfo(entry.getName(), entry.getSize(), 
                                entry.getLastModifiedTime() != null ? 
                                LocalDateTime.ofInstant(entry.getLastModifiedTime().toInstant(), ZoneId.systemDefault()) : 
                                LocalDateTime.now(), 
                                archivePath.toString());
                        results.add(fileInfo);
                    }
                }
                zis.closeEntry();
            }
        }
        
        return results;
    }

    /**
     * Processes a JAR archive and returns matching files.
     */
    private List<FileInfo> processJarArchive(Path archivePath, Pattern pattern) throws IOException {
        List<FileInfo> results = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             JarInputStream jis = new JarInputStream(fis)) {
            
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null && results.size() < properties.getMaxSearchResults()) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    String fileName = getFileNameFromPath(entryName);
                    
                    if (pattern.matcher(fileName).matches()) {
                        FileInfo fileInfo = createArchiveFileInfo(entry.getName(), entry.getSize(), 
                                entry.getLastModifiedTime() != null ? 
                                LocalDateTime.ofInstant(entry.getLastModifiedTime().toInstant(), ZoneId.systemDefault()) : 
                                LocalDateTime.now(), 
                                archivePath.toString());
                        results.add(fileInfo);
                    }
                }
                jis.closeEntry();
            }
        }
        
        return results;
    }

    /**
     * Processes a TAR archive (with optional GZIP compression) and returns matching files.
     */
    private List<FileInfo> processTarArchive(Path archivePath, Pattern pattern, boolean isGzipped) throws IOException {
        List<FileInfo> results = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             InputStream inputStream = isGzipped ? new GzipCompressorInputStream(fis) : fis;
             TarArchiveInputStream tis = new TarArchiveInputStream(inputStream)) {
            
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null && results.size() < properties.getMaxSearchResults()) {
                if (entry.isFile()) {
                    String entryName = entry.getName();
                    String fileName = getFileNameFromPath(entryName);
                    
                    if (pattern.matcher(fileName).matches()) {
                        FileInfo fileInfo = createArchiveFileInfo(entry.getName(), entry.getSize(), 
                                entry.getLastModifiedDate() != null ? 
                                LocalDateTime.ofInstant(entry.getLastModifiedDate().toInstant(), ZoneId.systemDefault()) : 
                                LocalDateTime.now(), 
                                archivePath.toString());
                        results.add(fileInfo);
                    }
                }
            }
        }
        
        return results;
    }

    /**
     * Extracts a file from a ZIP archive.
     */
    private InputStream extractFromZipArchive(Path archivePath, String entryPath) throws IOException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(archivePath.toFile()));
        
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.getName().equals(entryPath) && !entry.isDirectory()) {
                // Read the entire entry content into a byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                zis.close();
                return new ByteArrayInputStream(baos.toByteArray());
            }
            zis.closeEntry();
        }
        
        zis.close();
        throw new IOException("Entry not found in archive: " + entryPath);
    }

    /**
     * Extracts a file from a JAR archive.
     */
    private InputStream extractFromJarArchive(Path archivePath, String entryPath) throws IOException {
        JarInputStream jis = new JarInputStream(new FileInputStream(archivePath.toFile()));
        
        JarEntry entry;
        while ((entry = jis.getNextJarEntry()) != null) {
            if (entry.getName().equals(entryPath) && !entry.isDirectory()) {
                // Read the entire entry content into a byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = jis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                jis.close();
                return new ByteArrayInputStream(baos.toByteArray());
            }
            jis.closeEntry();
        }
        
        jis.close();
        throw new IOException("Entry not found in archive: " + entryPath);
    }

    /**
     * Extracts a file from a TAR archive.
     */
    private InputStream extractFromTarArchive(Path archivePath, String entryPath, boolean isGzipped) throws IOException {
        FileInputStream fis = new FileInputStream(archivePath.toFile());
        InputStream inputStream = isGzipped ? new GzipCompressorInputStream(fis) : fis;
        TarArchiveInputStream tis = new TarArchiveInputStream(inputStream);
        
        TarArchiveEntry entry;
        while ((entry = tis.getNextTarEntry()) != null) {
            if (entry.getName().equals(entryPath) && entry.isFile()) {
                // Read the entire entry content into a byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = tis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                tis.close();
                return new ByteArrayInputStream(baos.toByteArray());
            }
        }
        
        tis.close();
        throw new IOException("Entry not found in archive: " + entryPath);
    }

    /**
     * Creates a FileInfo object for an archive entry.
     */
    private FileInfo createArchiveFileInfo(String entryPath, long size, LocalDateTime lastModified, String archivePath) {
        String fileName = getFileNameFromPath(entryPath);
        return new FileInfo(fileName, entryPath, entryPath, size, lastModified, archivePath);
    }

    /**
     * Extracts the file name from a full path.
     */
    private String getFileNameFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        
        // Handle both forward and backward slashes
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Converts a wildcard pattern to a regular expression.
     */
    private Pattern convertWildcardToRegex(String wildcardPattern) {
        // Escape special regex characters except * and ?
        String escaped = wildcardPattern
                .replace("\\", "\\\\")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace(".", "\\.")
                .replace("|", "\\|")
                .replace("+", "\\+")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}");

        // Convert wildcards to regex
        String regex = escaped
                .replace("*", ".*")  // * matches any sequence of characters
                .replace("?", ".");   // ? matches any single character

        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }


}