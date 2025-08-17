package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * Request model for content search operations.
 * 
 * <p>This class represents a request to search for text content within files,
 * including search options and parameters.</p>
 * 
 * @since 1.0
 */
@Schema(description = "Request for searching content within files")
public class ContentSearchRequest {

    /**
     * The file path to search within.
     */
    @Schema(description = "The file path to search within", 
            example = "/data/archives/application.log", required = true)
    @NotBlank(message = "File path cannot be blank")
    @Size(min = 1, max = 500, message = "File path must be between 1 and 500 characters")
    @JsonProperty("filePath")
    private String filePath;

    /**
     * The search term to look for.
     */
    @Schema(description = "The search term to look for", example = "ERROR", required = true)
    @NotBlank(message = "Search term cannot be blank")
    @Size(min = 1, max = 255, message = "Search term must be between 1 and 255 characters")
    @JsonProperty("searchTerm")
    private String searchTerm;

    /**
     * Whether the search should be case sensitive.
     */
    @Schema(description = "Whether the search should be case sensitive", example = "false")
    @JsonProperty("caseSensitive")
    private boolean caseSensitive = false;

    /**
     * Whether to match whole words only.
     */
    @Schema(description = "Whether to match whole words only", example = "false")
    @JsonProperty("wholeWord")
    private boolean wholeWord = false;

    /**
     * Default constructor.
     */
    public ContentSearchRequest() {
    }

    /**
     * Constructor with required parameters.
     *
     * @param filePath   the file path to search
     * @param searchTerm the search term
     */
    public ContentSearchRequest(String filePath, String searchTerm) {
        this.filePath = filePath;
        this.searchTerm = searchTerm;
        this.caseSensitive = false;
        this.wholeWord = false;
    }

    /**
     * Constructor with all parameters.
     *
     * @param filePath      the file path to search
     * @param searchTerm    the search term
     * @param caseSensitive whether to be case sensitive
     * @param wholeWord     whether to match whole words only
     */
    public ContentSearchRequest(String filePath, String searchTerm, boolean caseSensitive, boolean wholeWord) {
        this.filePath = filePath;
        this.searchTerm = searchTerm;
        this.caseSensitive = caseSensitive;
        this.wholeWord = wholeWord;
    }

    /**
     * Gets the file path.
     *
     * @return the file path
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Sets the file path.
     *
     * @param filePath the file path to set
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Gets the search term.
     *
     * @return the search term
     */
    public String getSearchTerm() {
        return searchTerm;
    }

    /**
     * Sets the search term.
     *
     * @param searchTerm the search term to set
     */
    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    /**
     * Gets whether the search is case sensitive.
     *
     * @return true if case sensitive, false otherwise
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * Sets whether the search should be case sensitive.
     *
     * @param caseSensitive true for case sensitive, false otherwise
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Gets whether to match whole words only.
     *
     * @return true if whole word matching, false otherwise
     */
    public boolean isWholeWord() {
        return wholeWord;
    }

    /**
     * Sets whether to match whole words only.
     *
     * @param wholeWord true for whole word matching, false otherwise
     */
    public void setWholeWord(boolean wholeWord) {
        this.wholeWord = wholeWord;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentSearchRequest that = (ContentSearchRequest) o;
        return caseSensitive == that.caseSensitive &&
                wholeWord == that.wholeWord &&
                Objects.equals(filePath, that.filePath) &&
                Objects.equals(searchTerm, that.searchTerm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, searchTerm, caseSensitive, wholeWord);
    }
}