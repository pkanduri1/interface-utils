package com.fabric.watcher.archive.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Model representing a search match within file content.
 * 
 * <p>This class contains information about a specific match found during
 * content search, including line number, content, and match position.</p>
 * 
 * @since 1.0
 */
@Schema(description = "A search match within file content")
public class SearchMatch {

    /**
     * The line number where the match was found.
     */
    @Schema(description = "The line number where the match was found", example = "42")
    @JsonProperty("lineNumber")
    private int lineNumber;

    /**
     * The content of the line containing the match.
     */
    @Schema(description = "The content of the line containing the match", 
            example = "2023-08-15 10:30:45 ERROR Failed to process request")
    @JsonProperty("lineContent")
    private String lineContent;

    /**
     * The starting column position of the match.
     */
    @Schema(description = "The starting column position of the match", example = "17")
    @JsonProperty("columnStart")
    private int columnStart;

    /**
     * The ending column position of the match.
     */
    @Schema(description = "The ending column position of the match", example = "22")
    @JsonProperty("columnEnd")
    private int columnEnd;

    /**
     * Default constructor.
     */
    public SearchMatch() {
    }

    /**
     * Constructor with all parameters.
     *
     * @param lineNumber  the line number
     * @param lineContent the line content
     * @param columnStart the starting column position
     * @param columnEnd   the ending column position
     */
    public SearchMatch(int lineNumber, String lineContent, int columnStart, int columnEnd) {
        this.lineNumber = lineNumber;
        this.lineContent = lineContent;
        this.columnStart = columnStart;
        this.columnEnd = columnEnd;
    }

    /**
     * Gets the line number.
     *
     * @return the line number
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Sets the line number.
     *
     * @param lineNumber the line number to set
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Gets the line content.
     *
     * @return the line content
     */
    public String getLineContent() {
        return lineContent;
    }

    /**
     * Sets the line content.
     *
     * @param lineContent the line content to set
     */
    public void setLineContent(String lineContent) {
        this.lineContent = lineContent;
    }

    /**
     * Gets the starting column position.
     *
     * @return the starting column position
     */
    public int getColumnStart() {
        return columnStart;
    }

    /**
     * Sets the starting column position.
     *
     * @param columnStart the starting column position to set
     */
    public void setColumnStart(int columnStart) {
        this.columnStart = columnStart;
    }

    /**
     * Gets the ending column position.
     *
     * @return the ending column position
     */
    public int getColumnEnd() {
        return columnEnd;
    }

    /**
     * Sets the ending column position.
     *
     * @param columnEnd the ending column position to set
     */
    public void setColumnEnd(int columnEnd) {
        this.columnEnd = columnEnd;
    }

    @Override
    public String toString() {
        return "SearchMatch{" +
                "lineNumber=" + lineNumber +
                ", lineContent='" + lineContent + '\'' +
                ", columnStart=" + columnStart +
                ", columnEnd=" + columnEnd +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchMatch that = (SearchMatch) o;
        return lineNumber == that.lineNumber &&
                columnStart == that.columnStart &&
                columnEnd == that.columnEnd &&
                Objects.equals(lineContent, that.lineContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineNumber, lineContent, columnStart, columnEnd);
    }
}