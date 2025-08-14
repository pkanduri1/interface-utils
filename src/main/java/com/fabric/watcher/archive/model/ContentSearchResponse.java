package com.fabric.watcher.archive.model;

import java.util.List;

/**
 * Response model for content search operations containing the list of matches
 * and metadata about the search operation.
 */
public class ContentSearchResponse {

    /**
     * List of matches found in the file content
     */
    private List<SearchMatch> matches;

    /**
     * Total number of matches found
     */
    private int totalMatches;

    /**
     * Whether the results were truncated due to limit
     */
    private boolean truncated;

    /**
     * Suggestion message when results are truncated
     */
    private String downloadSuggestion;

    /**
     * Time taken for the search operation in milliseconds
     */
    private long searchTimeMs;

    // Default constructor
    public ContentSearchResponse() {
    }

    // Constructor without truncation
    public ContentSearchResponse(List<SearchMatch> matches, int totalMatches, long searchTimeMs) {
        this.matches = matches;
        this.totalMatches = totalMatches;
        this.truncated = false;
        this.downloadSuggestion = null;
        this.searchTimeMs = searchTimeMs;
    }

    // Full constructor
    public ContentSearchResponse(List<SearchMatch> matches, int totalMatches, boolean truncated, 
                                String downloadSuggestion, long searchTimeMs) {
        this.matches = matches;
        this.totalMatches = totalMatches;
        this.truncated = truncated;
        this.downloadSuggestion = downloadSuggestion;
        this.searchTimeMs = searchTimeMs;
    }

    // Getters and setters
    public List<SearchMatch> getMatches() {
        return matches;
    }

    public void setMatches(List<SearchMatch> matches) {
        this.matches = matches;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public void setTotalMatches(int totalMatches) {
        this.totalMatches = totalMatches;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public String getDownloadSuggestion() {
        return downloadSuggestion;
    }

    public void setDownloadSuggestion(String downloadSuggestion) {
        this.downloadSuggestion = downloadSuggestion;
    }

    public long getSearchTimeMs() {
        return searchTimeMs;
    }

    public void setSearchTimeMs(long searchTimeMs) {
        this.searchTimeMs = searchTimeMs;
    }

    @Override
    public String toString() {
        return "ContentSearchResponse{" +
                "matches=" + matches +
                ", totalMatches=" + totalMatches +
                ", truncated=" + truncated +
                ", downloadSuggestion='" + downloadSuggestion + '\'' +
                ", searchTimeMs=" + searchTimeMs +
                '}';
    }
}