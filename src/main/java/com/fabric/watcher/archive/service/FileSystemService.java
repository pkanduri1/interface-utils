package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.FileInfo;
import com.fabric.watcher.archive.model.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service for file system operations including directory scanning and file metadata extraction.
 * 
 * <p>This service provides functionality to scan directories for files matching wildcard patterns,
 * extract file metadata, and validate file paths according to security constraints.</p>
 * 
 * @since 1.0
 */
@Service
public class FileSystemService {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    private final ArchiveSearchProperties properties;

    @Autowired
    public FileSystemService(ArchiveSearchProperties properties) {
        this.properties = properties;
    }

    /**
     * Scans a directory for files matching the specified pattern.
     * 
     * @param directory the directory to scan
     * @param pattern the wildcard pattern to match (supports * and ?)
     * @return list of FileInfo objects for matching files
     * @throws IOException if directory scanning fails
     * @throws IllegalArgumentException if directory is invalid or pattern is null
     */
    public List<FileInfo> scanDirectory(Path directory, String pattern) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty");
        }

        logger.debug("Scanning directory: {} with pattern: {}", directory, pattern);

        if (!Files.exists(directory)) {
            logger.warn("Directory does not exist: {}", directory);
            return new ArrayList<>();
        }

        if (!Files.isDirectory(directory)) {
            logger.warn("Path is not a directory: {}", directory);
            return new ArrayList<>();
        }

        List<FileInfo> results = new ArrayList<>();
        Pattern regexPattern = convertWildcardToRegex(pattern);
        
        try {
            Files.walkFileTree(directory, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Calculate depth relative to the starting directory
                    int depth = directory.relativize(dir).getNameCount();
                    if (depth > properties.getMaxDirectoryDepth()) {
                        logger.debug("Skipping directory due to depth limit: {} (depth: {})", dir, depth);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        String fileName = file.getFileName().toString();
                        if (regexPattern.matcher(fileName).matches()) {
                            if (attrs.size() <= properties.getMaxFileSize()) {
                                FileInfo fileInfo = createFileInfo(file, directory, attrs);
                                results.add(fileInfo);
                                
                                if (results.size() >= properties.getMaxSearchResults()) {
                                    logger.debug("Reached maximum search results limit: {}", properties.getMaxSearchResults());
                                    return FileVisitResult.TERMINATE;
                                }
                            } else {
                                logger.debug("Skipping file due to size limit: {} (size: {})", file, attrs.size());
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error processing file: {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    logger.warn("Failed to visit file: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (exc != null) {
                        logger.warn("Error visiting directory: {}", dir, exc);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error("Error scanning directory: {}", directory, e);
            throw e;
        }

        logger.debug("Found {} files matching pattern '{}' in directory: {}", results.size(), pattern, directory);
        return results;
    }

    /**
     * Validates if a path is valid and accessible.
     * 
     * @param path the path to validate
     * @return true if the path is valid and accessible, false otherwise
     */
    public boolean isValidPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }

        try {
            Path normalizedPath = Paths.get(path).normalize();
            
            // Check if path exists and is readable
            if (!Files.exists(normalizedPath)) {
                logger.debug("Path does not exist: {}", path);
                return false;
            }

            if (!Files.isReadable(normalizedPath)) {
                logger.debug("Path is not readable: {}", path);
                return false;
            }

            return true;
        } catch (InvalidPathException e) {
            logger.debug("Invalid path format: {}", path, e);
            return false;
        } catch (Exception e) {
            logger.warn("Error validating path: {}", path, e);
            return false;
        }
    }

    /**
     * Extracts metadata for a specific file.
     * 
     * @param file the file to extract metadata from
     * @return FileMetadata object containing file information
     * @throws IOException if file metadata cannot be read
     */
    public FileMetadata getFileMetadata(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        if (!Files.exists(file)) {
            throw new IOException("File does not exist: " + file);
        }

        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            
            return new FileMetadata(
                file.getFileName().toString(),
                file.toString(),
                attrs.size(),
                LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()),
                attrs.isRegularFile(),
                attrs.isDirectory(),
                Files.isReadable(file),
                Files.isWritable(file)
            );
        } catch (IOException e) {
            logger.error("Error reading file metadata: {}", file, e);
            throw e;
        }
    }

    /**
     * Checks if a file is an archive based on its extension.
     * 
     * @param file the file to check
     * @return true if the file is a supported archive type, false otherwise
     */
    public boolean isArchiveFile(Path file) {
        if (file == null) {
            return false;
        }

        String fileName = file.getFileName().toString().toLowerCase();
        
        return properties.getSupportedArchiveTypes().stream()
                .anyMatch(type -> fileName.endsWith("." + type.toLowerCase()));
    }

    /**
     * Creates a FileInfo object from a file path and its attributes.
     * 
     * @param file the file path
     * @param baseDirectory the base directory for calculating relative path
     * @param attrs the file attributes
     * @return FileInfo object
     */
    private FileInfo createFileInfo(Path file, Path baseDirectory, BasicFileAttributes attrs) {
        String fileName = file.getFileName().toString();
        String fullPath = file.toString();
        String relativePath = baseDirectory.relativize(file).toString();
        long size = attrs.size();
        LocalDateTime lastModified = LocalDateTime.ofInstant(
            attrs.lastModifiedTime().toInstant(), 
            ZoneId.systemDefault()
        );

        return new FileInfo(fileName, fullPath, relativePath, size, lastModified);
    }

    /**
     * Converts a wildcard pattern to a regular expression.
     * 
     * @param wildcardPattern the wildcard pattern (supports * and ?)
     * @return compiled Pattern object
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

    /**
     * Inner class representing file metadata.
     */
    public static class FileMetadata {
        private final String fileName;
        private final String fullPath;
        private final long size;
        private final LocalDateTime lastModified;
        private final boolean isRegularFile;
        private final boolean isDirectory;
        private final boolean isReadable;
        private final boolean isWritable;

        public FileMetadata(String fileName, String fullPath, long size, LocalDateTime lastModified,
                           boolean isRegularFile, boolean isDirectory, boolean isReadable, boolean isWritable) {
            this.fileName = fileName;
            this.fullPath = fullPath;
            this.size = size;
            this.lastModified = lastModified;
            this.isRegularFile = isRegularFile;
            this.isDirectory = isDirectory;
            this.isReadable = isReadable;
            this.isWritable = isWritable;
        }

        // Getters
        public String getFileName() { return fileName; }
        public String getFullPath() { return fullPath; }
        public long getSize() { return size; }
        public LocalDateTime getLastModified() { return lastModified; }
        public boolean isRegularFile() { return isRegularFile; }
        public boolean isDirectory() { return isDirectory; }
        public boolean isReadable() { return isReadable; }
        public boolean isWritable() { return isWritable; }

        @Override
        public String toString() {
            return "FileMetadata{" +
                    "fileName='" + fileName + '\'' +
                    ", fullPath='" + fullPath + '\'' +
                    ", size=" + size +
                    ", lastModified=" + lastModified +
                    ", isRegularFile=" + isRegularFile +
                    ", isDirectory=" + isDirectory +
                    ", isReadable=" + isReadable +
                    ", isWritable=" + isWritable +
                    '}';
        }
    }
}