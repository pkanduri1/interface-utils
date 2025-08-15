package com.fabric.watcher.archive.service;

import com.fabric.watcher.archive.config.ArchiveSearchProperties;
import com.fabric.watcher.archive.model.ContentSearchRequest;
import com.fabric.watcher.archive.model.ContentSearchResponse;
import com.fabric.watcher.archive.model.SearchMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service for searching content within files and archive entries.
 * Provides text search functionality with line-by-line matching and result limiting.
 */
@Service
public class ContentSearchService {

    private static final Logger logger = LoggerFactory.getLogger(ContentSearchService.class);
    
    private final ArchiveSearchProperties properties;
    private final ArchiveHandlerService archiveHandlerService;

    public ContentSearchService(ArchiveSearchProperties properties, ArchiveHandlerService archiveHandlerService) {
        this.properties = properties;
        this.archiveHandlerService = archiveHandlerService;
    }

    /**
     * Searches for content within a regular file.
     *
     * @param request the content search request
     * @return the search response with matches
     * @throws IOException if file cannot be read
     * @throws IllegalArgumentException if request is invalid
     */
    public ContentSearchResponse searchInFile(ContentSearchRequest request) throws IOException {
        if (request == null || request.getFilePath() == null || request.getSearchTerm() == null) {
            throw new IllegalArgumentException("Request, file path, and search term cannot be null");
        }

        long startTime = System.currentTimeMillis();
        Path filePath = Paths.get(request.getFilePath());

        logger.debug("Starting content search in file: {} for term: '{}'", 
                    request.getFilePath(), request.getSearchTerm());

        if (!Files.exists(filePath)) {
            logger.warn("File not found: {}", request.getFilePath());
            return new ContentSearchResponse(new ArrayList<>(), 0, 
                    System.currentTimeMillis() - startTime);
        }

        if (!Files.isReadable(filePath)) {
            logger.warn("File not readable: {}", request.getFilePath());
            throw new IOException("File is not readable: " + request.getFilePath());
        }

        // Check file size
        long fileSize = Files.size(filePath);
        if (fileSize > properties.getMaxFileSize()) {
            logger.warn("File too large: {} bytes (max: {})", fileSize, properties.getMaxFileSize());
            throw new IOException("File size exceeds maximum allowed: " + fileSize + " bytes");
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return searchInStream(inputStream, request, startTime);
        }
    }

    /**
     * Searches for content within an archive file entry.
     *
     * @param archivePath path to the archive file
     * @param entryName name of the entry within the archive
     * @param request the content search request
     * @return the search response with matches
     * @throws IOException if archive or entry cannot be read
     */
    public ContentSearchResponse searchInArchiveFile(Path archivePath, String entryName, 
                                                   ContentSearchRequest request) throws IOException {
        if (archivePath == null || entryName == null || request == null) {
            throw new IllegalArgumentException("Archive path, entry name, and request cannot be null");
        }

        long startTime = System.currentTimeMillis();
        
        logger.debug("Starting content search in archive: {} entry: {} for term: '{}'", 
                    archivePath, entryName, request.getSearchTerm());

        try (InputStream inputStream = archiveHandlerService.extractFileFromArchive(archivePath, entryName)) {
            if (inputStream == null) {
                logger.warn("Entry not found in archive: {} -> {}", archivePath, entryName);
                return new ContentSearchResponse(new ArrayList<>(), 0, 
                        System.currentTimeMillis() - startTime);
            }
            
            return searchInStream(inputStream, request, startTime);
        }
    }

    /**
     * Searches for content within an input stream.
     *
     * @param inputStream the input stream to search
     * @param request the content search request
     * @param startTime the start time for performance measurement
     * @return the search response with matches
     * @throws IOException if stream cannot be read
     */
    private ContentSearchResponse searchInStream(InputStream inputStream, 
                                               ContentSearchRequest request, 
                                               long startTime) throws IOException {
        List<SearchMatch> matches = new ArrayList<>();
        int totalMatches = 0;
        boolean truncated = false;
        
        Pattern searchPattern = createSearchPattern(request);
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            int lineNumber = 1;
            int matchCount = 0;
            boolean reachedLimit = false;
            
            while ((line = reader.readLine()) != null) {
                // Check for timeout
                if (System.currentTimeMillis() - startTime > 
                    TimeUnit.SECONDS.toMillis(properties.getSearchTimeoutSeconds())) {
                    logger.warn("Search operation timed out after {} seconds", 
                               properties.getSearchTimeoutSeconds());
                    break;
                }
                
                List<SearchMatch> lineMatches = findMatchesInLine(line, lineNumber, searchPattern);
                totalMatches += lineMatches.size();
                
                // Add matches up to the limit only if we haven't reached it yet
                if (!reachedLimit) {
                    for (SearchMatch match : lineMatches) {
                        if (matchCount < properties.getMaxSearchResults()) {
                            matches.add(match);
                            matchCount++;
                        } else {
                            truncated = true;
                            reachedLimit = true;
                            break;
                        }
                    }
                }
                
                lineNumber++;
            }
        }
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        String downloadSuggestion = null;
        if (truncated) {
            downloadSuggestion = String.format(
                "Results truncated to %d matches. Download the complete file to see all %d matches.",
                properties.getMaxSearchResults(), totalMatches);
        }
        
        logger.debug("Content search completed in {}ms. Found {} matches (truncated: {})", 
                    searchTime, totalMatches, truncated);
        
        return new ContentSearchResponse(matches, totalMatches, truncated, downloadSuggestion, searchTime);
    }

    /**
     * Creates a search pattern based on the request parameters.
     *
     * @param request the content search request
     * @return the compiled pattern
     * @throws IllegalArgumentException if pattern cannot be compiled
     */
    private Pattern createSearchPattern(ContentSearchRequest request) {
        String searchTerm = request.getSearchTerm();
        
        // Escape special regex characters if not using regex
        searchTerm = Pattern.quote(searchTerm);
        
        // Handle whole word matching
        if (Boolean.TRUE.equals(request.getWholeWord())) {
            searchTerm = "\\b" + searchTerm + "\\b";
        }
        
        int flags = 0;
        if (!Boolean.TRUE.equals(request.getCaseSensitive())) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        
        try {
            return Pattern.compile(searchTerm, flags);
        } catch (PatternSyntaxException e) {
            logger.error("Invalid search pattern: {}", searchTerm, e);
            throw new IllegalArgumentException("Invalid search pattern: " + e.getMessage(), e);
        }
    }

    /**
     * Finds all matches of the search pattern within a single line.
     *
     * @param line the line content to search
     * @param lineNumber the line number (1-based)
     * @param pattern the search pattern
     * @return list of matches found in the line
     */
    private List<SearchMatch> findMatchesInLine(String line, int lineNumber, Pattern pattern) {
        List<SearchMatch> matches = new ArrayList<>();
        
        if (line == null) {
            return matches;
        }
        
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            SearchMatch match = new SearchMatch(
                lineNumber,
                line,
                matcher.start(),
                matcher.end()
            );
            matches.add(match);
        }
        
        return matches;
    }

    /**
     * Checks if a file appears to be a text file based on its content.
     * This is a simple heuristic that checks for binary content.
     *
     * @param filePath the path to the file
     * @return true if the file appears to be text, false otherwise
     */
    public boolean isTextFile(Path filePath) {
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            return false;
        }
        
        try {
            // Read first 1024 bytes to check for binary content
            byte[] buffer = new byte[1024];
            try (InputStream is = Files.newInputStream(filePath)) {
                int bytesRead = is.read(buffer);
                if (bytesRead <= 0) {
                    return true; // Empty file is considered text
                }
                
                // Check for null bytes which typically indicate binary content
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == 0) {
                        return false;
                    }
                }
                
                return true;
            }
        } catch (IOException e) {
            logger.warn("Error checking if file is text: {}", filePath, e);
            return false;
        }
    }
}