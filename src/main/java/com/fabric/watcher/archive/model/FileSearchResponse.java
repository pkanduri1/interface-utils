package com.fabric.watcher.archive.model;

import java.util.List;

/**
 * Response model for file search operations containing the list of matching files
 * and metadata about the search operation.
 */
public class FileSearchResponse {

    /**
     * List of files matching the search criteria
     */
    private List<FileInfo> files;

    /**
     * Total number of files found
     */
    private int totalCount;

    /**
     * Path that was searched
     */
    private String searchPath;

    /**
     * Pattern used for searching
     */
    private String searchPattern;

    /**
     * Time taken for the search operation in milliseconds
     */
    private long searchTimeMs;

    // Default constructor
    public FileSearchResponse() {
    }

    // Full constructor
    public FileSearchResponse(List<FileInfo> files, int totalCount, String searchPath, 
                             String searchPattern, long searchTimeMs) {
        this.files = files;
        this.totalCount = totalCount;
        this.searchPath = searchPath;
        this.searchPattern = searchPattern;
        this.searchTimeMs = searchTimeMs;
    }

    // Getters and setters
    public List<FileInfo> getFiles() {
        return files;
    }

    public void setFiles(List<FileInfo> files) {
        this.files = files;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public String getSearchPath() {
        return searchPath;
    }

    public void setSearchPath(String searchPath) {
        this.searchPath = searchPath;
    }

    public String getSearchPattern() {
        return searchPattern;
    }

    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
    }

    public long getSearchTimeMs() {
        return searchTimeMs;
    }

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
}