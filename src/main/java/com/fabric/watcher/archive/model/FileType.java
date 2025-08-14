package com.fabric.watcher.archive.model;

/**
 * Enumeration representing the type of file found during search operations.
 */
public enum FileType {
    
    /**
     * Regular file in the file system
     */
    REGULAR,
    
    /**
     * File entry within an archive
     */
    ARCHIVE_ENTRY
}