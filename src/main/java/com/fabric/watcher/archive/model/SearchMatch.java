package com.fabric.watcher.archive.model;

/**
 * Represents a single match found during content search operations.
 * Contains the line information and position details of the match.
 */
public class SearchMatch {

    /**
     * Line number where the match was found (1-based)
     */
    private int lineNumber;

    /**
     * Content of the line containing the match
     */
    private String lineContent;

    /**
     * Starting column position of the match within the line (0-based)
     */
    private int columnStart;

    /**
     * Ending column position of the match within the line (0-based)
     */
    private int columnEnd;

    // Default constructor
    public SearchMatch() {
    }

    // Full constructor
    public SearchMatch(int lineNumber, String lineContent, int columnStart, int columnEnd) {
        this.lineNumber = lineNumber;
        this.lineContent = lineContent;
        this.columnStart = columnStart;
        this.columnEnd = columnEnd;
    }

    // Getters and setters
    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getLineContent() {
        return lineContent;
    }

    public void setLineContent(String lineContent) {
        this.lineContent = lineContent;
    }

    public int getColumnStart() {
        return columnStart;
    }

    public void setColumnStart(int columnStart) {
        this.columnStart = columnStart;
    }

    public int getColumnEnd() {
        return columnEnd;
    }

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
}