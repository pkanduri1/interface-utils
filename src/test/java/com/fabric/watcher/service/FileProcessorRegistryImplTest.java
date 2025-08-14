package com.fabric.watcher.service;

import com.fabric.watcher.config.WatchConfig;
import com.fabric.watcher.model.ProcessingResult;
import com.fabric.watcher.model.ProcessingResult.ExecutionStatus;
import com.fabric.watcher.processor.FileProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileProcessorRegistryImplTest {

    @Mock
    private FileProcessor mockProcessor;
    
    @Mock
    private ApplicationContext applicationContext;

    private FileProcessorRegistryImpl registry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        registry = new FileProcessorRegistryImpl();
        registry.applicationContext = applicationContext;
    }

    @Test
    void shouldRegisterProcessor() {
        // Given
        String processorType = "test-processor";

        // When
        registry.registerProcessor(processorType, mockProcessor);

        // Then
        assertEquals(mockProcessor, registry.getProcessor(processorType));
        assertTrue(registry.hasProcessor(processorType));
    }

    @Test
    void shouldThrowExceptionForNullProcessorType() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
                    () -> registry.registerProcessor(null, mockProcessor));
    }

    @Test
    void shouldThrowExceptionForEmptyProcessorType() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
                    () -> registry.registerProcessor("", mockProcessor));
        assertThrows(IllegalArgumentException.class, 
                    () -> registry.registerProcessor("   ", mockProcessor));
    }

    @Test
    void shouldThrowExceptionForNullProcessor() {
        // When & Then
        assertThrows(IllegalArgumentException.class, 
                    () -> registry.registerProcessor("test", null));
    }

    @Test
    void shouldReturnNullForUnregisteredProcessor() {
        // When
        FileProcessor result = registry.getProcessor("nonexistent");

        // Then
        assertNull(result);
        assertFalse(registry.hasProcessor("nonexistent"));
    }

    @Test
    void shouldUnregisterProcessor() {
        // Given
        String processorType = "test-processor";
        registry.registerProcessor(processorType, mockProcessor);

        // When
        FileProcessor unregistered = registry.unregisterProcessor(processorType);

        // Then
        assertEquals(mockProcessor, unregistered);
        assertNull(registry.getProcessor(processorType));
        assertFalse(registry.hasProcessor(processorType));
    }

    @Test
    void shouldReturnNullWhenUnregisteringNonexistentProcessor() {
        // When
        FileProcessor result = registry.unregisterProcessor("nonexistent");

        // Then
        assertNull(result);
    }

    @Test
    void shouldGetAllProcessors() {
        // Given
        FileProcessor processor1 = mock(FileProcessor.class);
        FileProcessor processor2 = mock(FileProcessor.class);
        
        registry.registerProcessor("type1", processor1);
        registry.registerProcessor("type2", processor2);

        // When
        Map<String, FileProcessor> allProcessors = registry.getAllProcessors();

        // Then
        assertEquals(2, allProcessors.size());
        assertEquals(processor1, allProcessors.get("type1"));
        assertEquals(processor2, allProcessors.get("type2"));
    }

    @Test
    void shouldProcessFileSuccessfully() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig("sql-processor");
        ProcessingResult expectedResult = new ProcessingResult("test.sql", "sql-processor", ExecutionStatus.SUCCESS);
        
        when(mockProcessor.supports(config)).thenReturn(true);
        when(mockProcessor.processFile(testFile, config)).thenReturn(expectedResult);
        
        registry.registerProcessor("sql-processor", mockProcessor);

        // When
        ProcessingResult result = registry.processFile(testFile, config);

        // Then
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertEquals("test.sql", result.getFilename());
        assertEquals("sql-processor", result.getProcessorType());
        assertNotNull(result.getExecutionTime());
        assertTrue(result.getExecutionDurationMs() >= 0);
        
        verify(mockProcessor).supports(config);
        verify(mockProcessor).processFile(testFile, config);
    }

    @Test
    void shouldHandleProcessorNotFound() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig("nonexistent-processor");

        // When
        ProcessingResult result = registry.processFile(testFile, config);

        // Then
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertEquals("test.sql", result.getFilename());
        assertEquals("nonexistent-processor", result.getProcessorType());
        assertTrue(result.getErrorMessage().contains("No processor found for type"));
    }

    @Test
    void shouldHandleProcessorNotSupported() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig("sql-processor");
        
        when(mockProcessor.supports(config)).thenReturn(false);
        
        registry.registerProcessor("sql-processor", mockProcessor);

        // When
        ProcessingResult result = registry.processFile(testFile, config);

        // Then
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertEquals("test.sql", result.getFilename());
        assertEquals("sql-processor", result.getProcessorType());
        assertTrue(result.getErrorMessage().contains("does not support configuration"));
        
        verify(mockProcessor).supports(config);
        verify(mockProcessor, never()).processFile(any(), any());
    }

    @Test
    void shouldHandleProcessorException() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig("sql-processor");
        
        when(mockProcessor.supports(config)).thenReturn(true);
        when(mockProcessor.processFile(testFile, config)).thenThrow(new RuntimeException("Test exception"));
        
        registry.registerProcessor("sql-processor", mockProcessor);

        // When
        ProcessingResult result = registry.processFile(testFile, config);

        // Then
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertEquals("test.sql", result.getFilename());
        assertEquals("sql-processor", result.getProcessorType());
        assertTrue(result.getErrorMessage().contains("Unexpected error processing file"));
    }

    @Test
    void shouldHandleNullFile() {
        // Given
        WatchConfig config = createWatchConfig("sql-processor");

        // When & Then
        assertThrows(IllegalArgumentException.class, 
                    () -> registry.processFile(null, config));
    }

    @Test
    void shouldHandleNullConfig() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());

        // When & Then
        assertThrows(IllegalArgumentException.class, 
                    () -> registry.processFile(testFile, null));
    }

    @Test
    void shouldHandleNullProcessorType() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig(null);

        // When
        ProcessingResult result = registry.processFile(testFile, config);

        // Then
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains("No processor type specified"));
    }

    @Test
    void shouldHandleEmptyProcessorType() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig("");

        // When
        ProcessingResult result = registry.processFile(testFile, config);

        // Then
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains("No processor type specified"));
    }

    @Test
    void shouldAutoDiscoverProcessorsOnInitialization() {
        // Given
        FileProcessor processor1 = mock(FileProcessor.class);
        FileProcessor processor2 = mock(FileProcessor.class);
        
        when(processor1.getProcessorType()).thenReturn("sql-processor");
        when(processor2.getProcessorType()).thenReturn("log-processor");
        
        Map<String, FileProcessor> processorBeans = new HashMap<>();
        processorBeans.put("sqlProcessor", processor1);
        processorBeans.put("logProcessor", processor2);
        
        when(applicationContext.getBeansOfType(FileProcessor.class)).thenReturn(processorBeans);

        // When
        registry.initializeProcessors();

        // Then
        assertTrue(registry.hasProcessor("sql-processor"));
        assertTrue(registry.hasProcessor("log-processor"));
        assertEquals(processor1, registry.getProcessor("sql-processor"));
        assertEquals(processor2, registry.getProcessor("log-processor"));
        
        Map<String, FileProcessorRegistryImpl.ProcessorMetadata> metadata = registry.getProcessorMetadata();
        assertEquals(2, metadata.size());
        assertTrue(metadata.containsKey("sql-processor"));
        assertTrue(metadata.containsKey("log-processor"));
    }
    
    @Test
    void shouldSkipProcessorsWithNullOrEmptyType() {
        // Given
        FileProcessor processor1 = mock(FileProcessor.class);
        FileProcessor processor2 = mock(FileProcessor.class);
        
        when(processor1.getProcessorType()).thenReturn(null);
        when(processor2.getProcessorType()).thenReturn("");
        
        Map<String, FileProcessor> processorBeans = new HashMap<>();
        processorBeans.put("nullProcessor", processor1);
        processorBeans.put("emptyProcessor", processor2);
        
        when(applicationContext.getBeansOfType(FileProcessor.class)).thenReturn(processorBeans);

        // When
        registry.initializeProcessors();

        // Then
        assertEquals(0, registry.getAllProcessors().size());
        assertEquals(0, registry.getProcessorMetadata().size());
    }
    
    @Test
    void shouldGetAvailableProcessorTypes() {
        // Given
        registry.registerProcessor("sql-processor", mockProcessor);
        registry.registerProcessor("log-processor", mock(FileProcessor.class));

        // When
        String availableTypes = registry.getAvailableProcessorTypes();

        // Then
        assertTrue(availableTypes.contains("sql-processor"));
        assertTrue(availableTypes.contains("log-processor"));
    }
    
    @Test
    void shouldReturnNoneForNoAvailableProcessors() {
        // When
        String availableTypes = registry.getAvailableProcessorTypes();

        // Then
        assertEquals("none", availableTypes);
    }
    
    @Test
    void shouldTrackProcessorHealth() {
        // Given
        registry.registerProcessor("sql-processor", mockProcessor);

        // When & Then
        assertTrue(registry.isProcessorHealthy("sql-processor"));
        assertFalse(registry.isProcessorHealthy("nonexistent"));
    }
    
    @Test
    void shouldResetProcessorHealth() {
        // Given
        registry.registerProcessor("sql-processor", mockProcessor);
        
        // Simulate failure to mark as unhealthy
        FileProcessorRegistryImpl.ProcessorMetadata metadata = registry.getProcessorMetadata().get("sql-processor");
        metadata.setHealthy(false);
        metadata.setLastError("Test error");

        // When
        boolean reset = registry.resetProcessorHealth("sql-processor");

        // Then
        assertTrue(reset);
        assertTrue(registry.isProcessorHealthy("sql-processor"));
        assertNull(metadata.getLastError());
    }
    
    @Test
    void shouldReturnFalseWhenResettingNonexistentProcessor() {
        // When
        boolean reset = registry.resetProcessorHealth("nonexistent");

        // Then
        assertFalse(reset);
    }
    
    @Test
    void shouldGetSupportingProcessors() throws IOException {
        // Given
        FileProcessor processor1 = mock(FileProcessor.class);
        FileProcessor processor2 = mock(FileProcessor.class);
        
        WatchConfig config = createWatchConfig("test-type");
        
        when(processor1.supports(config)).thenReturn(true);
        when(processor2.supports(config)).thenReturn(false);
        
        registry.registerProcessor("supporting-processor", processor1);
        registry.registerProcessor("non-supporting-processor", processor2);

        // When
        List<String> supportingProcessors = registry.getSupportingProcessors(config);

        // Then
        assertEquals(1, supportingProcessors.size());
        assertTrue(supportingProcessors.contains("supporting-processor"));
        assertFalse(supportingProcessors.contains("non-supporting-processor"));
    }
    
    @Test
    void shouldMarkProcessorUnhealthyAfterConsecutiveFailures() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig("sql-processor");
        ProcessingResult failureResult = new ProcessingResult("test.sql", "sql-processor", ExecutionStatus.FAILURE, "Test failure");
        
        when(mockProcessor.supports(config)).thenReturn(true);
        when(mockProcessor.processFile(testFile, config)).thenReturn(failureResult);
        
        registry.registerProcessor("sql-processor", mockProcessor);

        // When - simulate multiple failures
        for (int i = 0; i < 6; i++) {
            registry.processFile(testFile, config);
        }

        // Then
        assertFalse(registry.isProcessorHealthy("sql-processor"));
        
        FileProcessorRegistryImpl.ProcessorMetadata metadata = registry.getProcessorMetadata().get("sql-processor");
        assertFalse(metadata.isHealthy());
        assertEquals(6, metadata.getConsecutiveFailures());
        assertEquals(6, metadata.getFailureCount());
    }
    
    @Test
    void shouldResetConsecutiveFailuresOnSuccess() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig("sql-processor");
        ProcessingResult failureResult = new ProcessingResult("test.sql", "sql-processor", ExecutionStatus.FAILURE, "Test failure");
        ProcessingResult successResult = new ProcessingResult("test.sql", "sql-processor", ExecutionStatus.SUCCESS);
        
        when(mockProcessor.supports(config)).thenReturn(true);
        when(mockProcessor.processFile(testFile, config))
            .thenReturn(failureResult)
            .thenReturn(failureResult)
            .thenReturn(successResult);
        
        registry.registerProcessor("sql-processor", mockProcessor);

        // When - simulate failures followed by success
        registry.processFile(testFile, config);
        registry.processFile(testFile, config);
        registry.processFile(testFile, config);

        // Then
        assertTrue(registry.isProcessorHealthy("sql-processor"));
        
        FileProcessorRegistryImpl.ProcessorMetadata metadata = registry.getProcessorMetadata().get("sql-processor");
        assertTrue(metadata.isHealthy());
        assertEquals(0, metadata.getConsecutiveFailures());
        assertEquals(2, metadata.getFailureCount());
        assertEquals(1, metadata.getSuccessCount());
    }
    
    @Test
    void shouldHandleUnhealthyProcessor() throws IOException {
        // Given
        File testFile = tempDir.resolve("test.sql").toFile();
        Files.createFile(testFile.toPath());
        
        WatchConfig config = createWatchConfig("sql-processor");
        
        registry.registerProcessor("sql-processor", mockProcessor);
        
        // Mark processor as unhealthy
        FileProcessorRegistryImpl.ProcessorMetadata metadata = registry.getProcessorMetadata().get("sql-processor");
        metadata.setHealthy(false);
        metadata.setLastError("Previous error");

        // When
        ProcessingResult result = registry.processFile(testFile, config);

        // Then
        assertEquals(ExecutionStatus.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains("marked as unhealthy"));
        
        // Verify processor was not called
        verify(mockProcessor, never()).supports(any());
        verify(mockProcessor, never()).processFile(any(), any());
    }

    private WatchConfig createWatchConfig(String processorType) {
        WatchConfig config = new WatchConfig();
        config.setName("test-config");
        config.setProcessorType(processorType);
        config.setWatchFolder(tempDir);
        config.setEnabled(true);
        return config;
    }
}