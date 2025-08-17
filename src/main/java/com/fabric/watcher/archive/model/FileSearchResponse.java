package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * Response model for file search operations.
 * 
 * <p>This class represents the response from a file search operation,
 * containing the list of matching files and search metadata.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Response for file search operations")
public class FileSearchResponse {

    /**
     * List of files matching the search criteria.
     */
    @Schema(description = "List of files matching the search criteria")
    @JsonProperty("files")
    private List<FileInfo> files;

    /**
     * Total number of files found.
     */
    @Schema(description = "Total number of files found", example = "25")
    @JsonProperty("totalCount")
    private int totalCount;

    /**
     * The search path that was used.
     */
    @Schema(description = "The search path that was used", example = "/data/archives")
    @JsonProperty("searchPath")
    private String searchPath;

    /**
     * The search pattern that was used.
     */
    @Schema(description = "The search pattern that was used", example = "*.log")
    @JsonProperty("searchPattern")
    private String searchPattern;

    /**
     * Time taken for the search operation in milliseconds.
     */
    @Schema(description = "Time taken for the search operation in milliseconds", example = "150")
    @JsonProperty("searchTimeMs")
    private long searchTimeMs;

    /**
     * Default constructor.
     */
    public FileSearchResponse() {
    }

    /**
     * Constructor with all parameters.
     *
     * @param files         the list of matching files
     * @param totalCount    the total count of files
     * @param searchPath    the search path
     * @param searchPattern the search pattern
     * @param searchTimeMs  the search time in milliseconds
     */
    public FileSearchResponse(List<FileInfo> files, int totalCount, String searchPath, 
                             String searchPattern, long searchTimeMs) {
        this.files = files;
        this.totalCount = totalCount;
        this.searchPath = searchPath;
        this.searchPattern = searchPattern;
        this.searchTimeMs = searchTimeMs;
    }

    /**
     * Gets the list of files.
     *
     * @return the list of files
     */
    public List<FileInfo> getFiles() {
        return files;
    }

    /**
     * Sets the list of files.
     *
     * @param files the list of files to set
     */
    public void setFiles(List<FileInfo> files) {
        this.files = files;
    }

    /**
     * Gets the total count.
     *
     * @return the total count
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * Sets the total count.
     *
     * @param totalCount the total count to set
     */
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * Gets the search path.
     *
     * @return the search path
     */
    public String getSearchPath() {
        return searchPath;
    }

    /**
     * Sets the search path.
     *
     * @param searchPath the search path to set
     */
    public void setSearchPath(String searchPath) {
        this.searchPath = searchPath;
    }

    /**
     * Gets the search pattern.
     *
     * @return the search pattern
     */
    public String getSearchPattern() {
        return searchPattern;
    }

    /**
     * Sets the search pattern.
     *
     * @param searchPattern the search pattern to set
     */
    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
    }

    /**
     * Gets the search time in milliseconds.
     *
     * @return the search time in milliseconds
     */
    public long getSearchTimeMs() {
        return searchTimeMs;
    }

    /**
     * Sets the search time in milliseconds.
     *
     * @param searchTimeMs the search time in milliseconds to set
     */
    public void setSearchTimeMs(long searchTimeMs) {
        this.searchTimeMs = searchTimeMs;
    }

    @Override
    public String toString() {
        return "FileSearchResponse{" +
                "files=" + files +
                ", totalCount=" + totalCount +
                ", searchPath='" + searchPath + '\'' +
                ", searchPattern='" + searchPattern + '\'' +
                ", searchTimeMs=" + searchTimeMs +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileSearchResponse that = (FileSearchResponse) o;
        return totalCount == that.totalCount &&
                searchTimeMs == that.searchTimeMs &&
                Objects.equals(files, that.files) &&
                Objects.equals(searchPath, that.searchPath) &&
                Objects.equals(searchPattern, that.searchPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(files, totalCount, searchPath, searchPattern, searchTimeMs);
    }
}