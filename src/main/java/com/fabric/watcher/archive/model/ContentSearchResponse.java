package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * Response model for content search operations.
 * 
 * <p>This class represents the response from a content search operation,
 * containing the matching lines and search metadata.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Response for content search operations")
public class ContentSearchResponse {

    /**
     * List of search matches found in the file.
     */
    @Schema(description = "List of search matches found in the file")
    @JsonProperty("matches")
    private List<SearchMatch> matches;

    /**
     * Total number of matches found.
     */
    @Schema(description = "Total number of matches found", example = "15")
    @JsonProperty("totalMatches")
    private int totalMatches;

    /**
     * Whether the results were truncated due to limit.
     */
    @Schema(description = "Whether the results were truncated due to limit", example = "false")
    @JsonProperty("truncated")
    private boolean truncated;

    /**
     * Suggestion to download the complete file if truncated.
     */
    @Schema(description = "Suggestion to download the complete file if truncated", 
            example = "Results truncated. Download the complete file to see all matches.")
    @JsonProperty("downloadSuggestion")
    private String downloadSuggestion;

    /**
     * Time taken for the search operation in milliseconds.
     */
    @Schema(description = "Time taken for the search operation in milliseconds", example = "75")
    @JsonProperty("searchTimeMs")
    private long searchTimeMs;

    /**
     * Default constructor.
     */
    public ContentSearchResponse() {
    }

    /**
     * Constructor with all parameters.
     *
     * @param matches            the list of search matches
     * @param totalMatches       the total number of matches
     * @param truncated          whether results were truncated
     * @param downloadSuggestion the download suggestion
     * @param searchTimeMs       the search time in milliseconds
     */
    public ContentSearchResponse(List<SearchMatch> matches, int totalMatches, boolean truncated, 
                                String downloadSuggestion, long searchTimeMs) {
        this.matches = matches;
        this.totalMatches = totalMatches;
        this.truncated = truncated;
        this.downloadSuggestion = downloadSuggestion;
        this.searchTimeMs = searchTimeMs;
    }

    /**
     * Gets the list of matches.
     *
     * @return the list of matches
     */
    public List<SearchMatch> getMatches() {
        return matches;
    }

    /**
     * Sets the list of matches.
     *
     * @param matches the list of matches to set
     */
    public void setMatches(List<SearchMatch> matches) {
        this.matches = matches;
    }

    /**
     * Gets the total number of matches.
     *
     * @return the total number of matches
     */
    public int getTotalMatches() {
        return totalMatches;
    }

    /**
     * Sets the total number of matches.
     *
     * @param totalMatches the total number of matches to set
     */
    public void setTotalMatches(int totalMatches) {
        this.totalMatches = totalMatches;
    }

    /**
     * Gets whether results were truncated.
     *
     * @return true if truncated, false otherwise
     */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * Sets whether results were truncated.
     *
     * @param truncated true if truncated, false otherwise
     */
    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    /**
     * Gets the download suggestion.
     *
     * @return the download suggestion
     */
    public String getDownloadSuggestion() {
        return downloadSuggestion;
    }

    /**
     * Sets the download suggestion.
     *
     * @param downloadSuggestion the download suggestion to set
     */
    public void setDownloadSuggestion(String downloadSuggestion) {
        this.downloadSuggestion = downloadSuggestion;
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
        return "ContentSearchResponse{" +
                "matches=" + matches +
                ", totalMatches=" + totalMatches +
                ", truncated=" + truncated +
                ", downloadSuggestion='" + downloadSuggestion + '\'' +
                ", searchTimeMs=" + searchTimeMs +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentSearchResponse that = (ContentSearchResponse) o;
        return totalMatches == that.totalMatches &&
                truncated == that.truncated &&
                searchTimeMs == that.searchTimeMs &&
                Objects.equals(matches, that.matches) &&
                Objects.equals(downloadSuggestion, that.downloadSuggestion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matches, totalMatches, truncated, downloadSuggestion, searchTimeMs);
    }
}