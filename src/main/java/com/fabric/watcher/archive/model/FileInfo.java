package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Represents information about a file found during archive search operations.
 * This includes both regular files and files within archives.
 */
public class FileInfo {

    /**
     * Name of the file (e.g., "config.properties")
     */
    private String fileName;

    /**
     * Full path to the file (e.g., "/data/archives/app.zip/config/config.properties")
     */
    private String fullPath;

    /**
     * Relative path from the search root (e.g., "config/config.properties")
     */
    private String relativePath;

    /**
     * Size of the file in bytes
     */
    private long size;

    /**
     * Last modified timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastModified;

    /**
     * Type of file (REGULAR or ARCHIVE_ENTRY)
     */
    private FileType type;

    /**
     * Path to the archive containing this file (null for regular files)
     */
    private String archivePath;

    // Default constructor
    public FileInfo() {
    }

    // Constructor for regular files
    public FileInfo(String fileName, String fullPath, String relativePath, long size, 
                   LocalDateTime lastModified) {
        this.fileName = fileName;
        this.fullPath = fullPath;
        this.relativePath = relativePath;
        this.size = size;
        this.lastModified = lastModified;
        this.type = FileType.REGULAR;
        this.archivePath = null;
    }

    // Constructor for archive entries
    public FileInfo(String fileName, String fullPath, String relativePath, long size, 
                   LocalDateTime lastModified, String archivePath) {
        this.fileName = fileName;
        this.fullPath = fullPath;
        this.relativePath = relativePath;
        this.size = size;
        this.lastModified = lastModified;
        this.type = FileType.ARCHIVE_ENTRY;
        this.archivePath = archivePath;
    }

    // Getters and setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public String getArchivePath() {
        return archivePath;
    }

    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "fileName='" + fileName + '\'' +
                ", fullPath='" + fullPath + '\'' +
                ", relativePath='" + relativePath + '\'' +
                ", size=" + size +
                ", lastModified=" + lastModified +
                ", type=" + type +
                ", archivePath='" + archivePath + '\'' +
                '}';
    }
}