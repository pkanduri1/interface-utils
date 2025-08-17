package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model representing file information.
 * 
 * <p>This class contains metadata about a file, including its name, path,
 * size, modification time, and type information.</p>
 * 
 * @since 1.0
 */
@Schema(description = "File information including metadata and path details")
public class FileInfo {

    /**
     * File type enumeration.
     */
    public enum FileType {
        REGULAR, ARCHIVE_ENTRY
    }

    /**
     * The file name.
     */
    @Schema(description = "The file name", example = "config.properties")
    @JsonProperty("fileName")
    private String fileName;

    /**
     * The full path to the file.
     */
    @Schema(description = "The full path to the file", example = "/data/archives/config.properties")
    @JsonProperty("fullPath")
    private String fullPath;

    /**
     * The relative path from the search root.
     */
    @Schema(description = "The relative path from the search root", example = "config.properties")
    @JsonProperty("relativePath")
    private String relativePath;

    /**
     * The file size in bytes.
     */
    @Schema(description = "The file size in bytes", example = "1024")
    @JsonProperty("size")
    private long size;

    /**
     * The last modified timestamp.
     */
    @Schema(description = "The last modified timestamp")
    @JsonProperty("lastModified")
    private LocalDateTime lastModified;

    /**
     * The file type.
     */
    @Schema(description = "The file type", example = "REGULAR")
    @JsonProperty("type")
    private FileType type;

    /**
     * The archive path if this is an archive entry.
     */
    @Schema(description = "The archive path if this is an archive entry", example = "/data/archives/backup.zip")
    @JsonProperty("archivePath")
    private String archivePath;

    /**
     * Default constructor.
     */
    public FileInfo() {
    }

    /**
     * Constructor for regular files.
     *
     * @param fileName     the file name
     * @param fullPath     the full path
     * @param relativePath the relative path
     * @param size         the file size
     * @param lastModified the last modified timestamp
     */
    public FileInfo(String fileName, String fullPath, String relativePath, long size, LocalDateTime lastModified) {
        this.fileName = fileName;
        this.fullPath = fullPath;
        this.relativePath = relativePath;
        this.size = size;
        this.lastModified = lastModified;
        this.type = FileType.REGULAR;
        this.archivePath = null;
    }

    /**
     * Constructor for archive entries.
     *
     * @param fileName     the file name
     * @param fullPath     the full path
     * @param relativePath the relative path
     * @param size         the file size
     * @param lastModified the last modified timestamp
     * @param archivePath  the archive path
     */
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

    /**
     * Gets the file name.
     *
     * @return the file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the file name.
     *
     * @param fileName the file name to set
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the full path.
     *
     * @return the full path
     */
    public String getFullPath() {
        return fullPath;
    }

    /**
     * Sets the full path.
     *
     * @param fullPath the full path to set
     */
    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    /**
     * Gets the relative path.
     *
     * @return the relative path
     */
    public String getRelativePath() {
        return relativePath;
    }

    /**
     * Sets the relative path.
     *
     * @param relativePath the relative path to set
     */
    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    /**
     * Gets the file size.
     *
     * @return the file size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets the file size.
     *
     * @param size the file size in bytes to set
     */
    public void setSize(long size) {
        this.size = size;
    }

    /**
     * Gets the last modified timestamp.
     *
     * @return the last modified timestamp
     */
    public LocalDateTime getLastModified() {
        return lastModified;
    }

    /**
     * Sets the last modified timestamp.
     *
     * @param lastModified the last modified timestamp to set
     */
    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * Gets the file type.
     *
     * @return the file type
     */
    public FileType getType() {
        return type;
    }

    /**
     * Sets the file type.
     *
     * @param type the file type to set
     */
    public void setType(FileType type) {
        this.type = type;
    }

    /**
     * Gets the archive path.
     *
     * @return the archive path
     */
    public String getArchivePath() {
        return archivePath;
    }

    /**
     * Sets the archive path.
     *
     * @param archivePath the archive path to set
     */
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileInfo fileInfo = (FileInfo) o;
        return size == fileInfo.size &&
                Objects.equals(fileName, fileInfo.fileName) &&
                Objects.equals(fullPath, fileInfo.fullPath) &&
                Objects.equals(relativePath, fileInfo.relativePath) &&
                Objects.equals(lastModified, fileInfo.lastModified) &&
                type == fileInfo.type &&
                Objects.equals(archivePath, fileInfo.archivePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, fullPath, relativePath, size, lastModified, type, archivePath);
    }
}