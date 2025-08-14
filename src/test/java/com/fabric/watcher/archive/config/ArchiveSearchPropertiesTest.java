package com.fabric.watcher.archive.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ArchiveSearchProperties}.
 */
@SpringBootTest(classes = ArchiveSearchPropertiesTest.TestConfiguration.class)
@TestPropertySource(properties = {
    "archive.search.enabled=true",
    "archive.search.allowed-paths[0]=./test/path1",
    "archive.search.allowed-paths[1]=./test/path2",
    "archive.search.excluded-paths[0]=./test/excluded",
    "archive.search.max-file-size=52428800",
    "archive.search.max-search-results=50",
    "archive.search.search-timeout-seconds=15",
    "archive.search.supported-archive-types[0]=zip",
    "archive.search.supported-archive-types[1]=tar",
    "archive.search.max-concurrent-operations=3",
    "archive.search.audit-logging-enabled=false",
    "archive.search.max-directory-depth=5"
})
class ArchiveSearchPropertiesTest {

    @Autowired
    private ArchiveSearchProperties properties;

    @Test
    void shouldLoadConfigurationProperties() {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getAllowedPaths()).containsExactly("./test/path1", "./test/path2");
        assertThat(properties.getExcludedPaths()).containsExactly("./test/excluded");
        assertThat(properties.getMaxFileSize()).isEqualTo(52428800L);
        assertThat(properties.getMaxSearchResults()).isEqualTo(50);
        assertThat(properties.getSearchTimeoutSeconds()).isEqualTo(15);
        assertThat(properties.getSupportedArchiveTypes()).containsExactly("zip", "tar");
        assertThat(properties.getMaxConcurrentOperations()).isEqualTo(3);
        assertThat(properties.isAuditLoggingEnabled()).isFalse();
        assertThat(properties.getMaxDirectoryDepth()).isEqualTo(5);
    }

    @Test
    void shouldHaveDefaultValues() {
        ArchiveSearchProperties defaultProperties = new ArchiveSearchProperties();
        
        assertThat(defaultProperties.isEnabled()).isFalse();
        assertThat(defaultProperties.getAllowedPaths()).isEmpty();
        assertThat(defaultProperties.getExcludedPaths()).isEmpty();
        assertThat(defaultProperties.getMaxFileSize()).isEqualTo(100L * 1024 * 1024);
        assertThat(defaultProperties.getMaxSearchResults()).isEqualTo(100);
        assertThat(defaultProperties.getSearchTimeoutSeconds()).isEqualTo(30);
        assertThat(defaultProperties.getSupportedArchiveTypes()).containsExactly("zip", "tar", "tar.gz", "jar");
        assertThat(defaultProperties.getMaxConcurrentOperations()).isEqualTo(5);
        assertThat(defaultProperties.isAuditLoggingEnabled()).isTrue();
        assertThat(defaultProperties.getMaxDirectoryDepth()).isEqualTo(10);
    }

    @Test
    void shouldSetAndGetAllProperties() {
        ArchiveSearchProperties testProperties = new ArchiveSearchProperties();
        
        testProperties.setEnabled(true);
        testProperties.setAllowedPaths(Arrays.asList("/path1", "/path2"));
        testProperties.setExcludedPaths(Arrays.asList("/excluded"));
        testProperties.setMaxFileSize(1024L);
        testProperties.setMaxSearchResults(25);
        testProperties.setSearchTimeoutSeconds(60);
        testProperties.setSupportedArchiveTypes(Arrays.asList("zip"));
        testProperties.setMaxConcurrentOperations(10);
        testProperties.setAuditLoggingEnabled(false);
        testProperties.setMaxDirectoryDepth(20);

        assertThat(testProperties.isEnabled()).isTrue();
        assertThat(testProperties.getAllowedPaths()).containsExactly("/path1", "/path2");
        assertThat(testProperties.getExcludedPaths()).containsExactly("/excluded");
        assertThat(testProperties.getMaxFileSize()).isEqualTo(1024L);
        assertThat(testProperties.getMaxSearchResults()).isEqualTo(25);
        assertThat(testProperties.getSearchTimeoutSeconds()).isEqualTo(60);
        assertThat(testProperties.getSupportedArchiveTypes()).containsExactly("zip");
        assertThat(testProperties.getMaxConcurrentOperations()).isEqualTo(10);
        assertThat(testProperties.isAuditLoggingEnabled()).isFalse();
        assertThat(testProperties.getMaxDirectoryDepth()).isEqualTo(20);
    }

    @Test
    void shouldGenerateToString() {
        String toString = properties.toString();
        
        assertThat(toString).contains("ArchiveSearchProperties{");
        assertThat(toString).contains("enabled=true");
        assertThat(toString).contains("allowedPaths=[./test/path1, ./test/path2]");
        assertThat(toString).contains("maxFileSize=52428800");
    }

    @EnableConfigurationProperties(ArchiveSearchProperties.class)
    static class TestConfiguration {
    }
}