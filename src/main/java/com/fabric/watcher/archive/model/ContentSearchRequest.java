package com.fabric.watcher.archive.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request model for content search operations within files.
 * Contains the file path and search parameters.
 */
public class ContentSearchRequest {

    /**
     * Path to the file to search within
     */
    @NotBlank(message = "File path cannot be blank")
    @Size(max = 1000, message = "File path cannot exceed 1000 characters")
    private String filePath;

    /**
     * Text to search for within the file
     */
    @NotBlank(message = "Search term cannot be blank")
    @Size(max = 500, message = "Search term cannot exceed 500 characters")
    private String searchTerm;

    /**
     * Whether the search should be case sensitive (default: false)
     */
    @NotNull
    private Boolean caseSensitive = false;

    /**
     * Whether to match whole words only (default: false)
     */
    @NotNull
    private Boolean wholeWord = false;

    // Default constructor
    public ContentSearchRequest() {
    }

    // Constructor with required fields
    public ContentSearchRequest(String filePath, String searchTerm) {
        this.filePath = filePath;
        this.searchTerm = searchTerm;
        this.caseSensitive = false;
        this.wholeWord = false;
    }

    // Full constructor
    public ContentSearchRequest(String filePath, String searchTerm, Boolean caseSensitive, Boolean wholeWord) {
        this.filePath = filePath;
        this.searchTerm = searchTerm;
        this.caseSensitive = caseSensitive != null ? caseSensitive : false;
        this.wholeWord = wholeWord != null ? wholeWord : false;
    }

    // Getters and setters
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    public Boolean getCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive != null ? caseSensitive : false;
    }

    public Boolean getWholeWord() {
        return wholeWord;
    }

    public void setWholeWord(Boolean wholeWord) {
        this.wholeWord = wholeWord != null ? wholeWord : false;
    }

    @Override
    public String toString() {
        return "ContentSearchRequest{" +
                "filePath='" + filePath + '\'' +
                ", searchTerm='" + searchTerm + '\'' +
                ", caseSensitive=" + caseSensitive +
                ", wholeWord=" + wholeWord +
                '}';
    }
}