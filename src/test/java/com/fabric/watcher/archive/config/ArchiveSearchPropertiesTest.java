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

    @Test
    void shouldHaveDefaultNestedConfigurations() {
        ArchiveSearchProperties defaultProperties = new ArchiveSearchProperties();
        
        assertThat(defaultProperties.getLdap()).isNotNull();
        assertThat(defaultProperties.getUpload()).isNotNull();
        assertThat(defaultProperties.getAudit()).isNotNull();
    }

    @Test
    void shouldSetAndGetNestedConfigurations() {
        ArchiveSearchProperties testProperties = new ArchiveSearchProperties();
        ArchiveSearchProperties.LdapConfig ldapConfig = new ArchiveSearchProperties.LdapConfig();
        ArchiveSearchProperties.UploadConfig uploadConfig = new ArchiveSearchProperties.UploadConfig();
        ArchiveSearchProperties.AuditConfig auditConfig = new ArchiveSearchProperties.AuditConfig();
        
        testProperties.setLdap(ldapConfig);
        testProperties.setUpload(uploadConfig);
        testProperties.setAudit(auditConfig);
        
        assertThat(testProperties.getLdap()).isEqualTo(ldapConfig);
        assertThat(testProperties.getUpload()).isEqualTo(uploadConfig);
        assertThat(testProperties.getAudit()).isEqualTo(auditConfig);
    }

    @Test
    void shouldTestLdapConfigDefaults() {
        ArchiveSearchProperties.LdapConfig ldapConfig = new ArchiveSearchProperties.LdapConfig();
        
        assertThat(ldapConfig.getUrl()).isEqualTo("ldap://localhost:389");
        assertThat(ldapConfig.getBaseDn()).isEqualTo("dc=company,dc=com");
        assertThat(ldapConfig.getUserSearchBase()).isEqualTo("ou=users");
        assertThat(ldapConfig.getUserSearchFilter()).isEqualTo("(sAMAccountName={0})");
        assertThat(ldapConfig.getConnectionTimeout()).isEqualTo(5000);
        assertThat(ldapConfig.getReadTimeout()).isEqualTo(10000);
        assertThat(ldapConfig.isUseSSL()).isFalse();
        assertThat(ldapConfig.getBindDn()).isNull();
        assertThat(ldapConfig.getBindPassword()).isNull();
    }

    @Test
    void shouldSetAndGetLdapConfigProperties() {
        ArchiveSearchProperties.LdapConfig ldapConfig = new ArchiveSearchProperties.LdapConfig();
        
        ldapConfig.setUrl("ldaps://ad.company.com:636");
        ldapConfig.setBaseDn("dc=test,dc=com");
        ldapConfig.setUserSearchBase("ou=employees");
        ldapConfig.setUserSearchFilter("(uid={0})");
        ldapConfig.setConnectionTimeout(10000);
        ldapConfig.setReadTimeout(20000);
        ldapConfig.setUseSSL(true);
        ldapConfig.setBindDn("cn=admin,dc=test,dc=com");
        ldapConfig.setBindPassword("secret");
        
        assertThat(ldapConfig.getUrl()).isEqualTo("ldaps://ad.company.com:636");
        assertThat(ldapConfig.getBaseDn()).isEqualTo("dc=test,dc=com");
        assertThat(ldapConfig.getUserSearchBase()).isEqualTo("ou=employees");
        assertThat(ldapConfig.getUserSearchFilter()).isEqualTo("(uid={0})");
        assertThat(ldapConfig.getConnectionTimeout()).isEqualTo(10000);
        assertThat(ldapConfig.getReadTimeout()).isEqualTo(20000);
        assertThat(ldapConfig.isUseSSL()).isTrue();
        assertThat(ldapConfig.getBindDn()).isEqualTo("cn=admin,dc=test,dc=com");
        assertThat(ldapConfig.getBindPassword()).isEqualTo("secret");
    }

    @Test
    void shouldTestLdapConfigToString() {
        ArchiveSearchProperties.LdapConfig ldapConfig = new ArchiveSearchProperties.LdapConfig();
        ldapConfig.setBindPassword("secret");
        
        String toString = ldapConfig.toString();
        
        assertThat(toString).contains("LdapConfig{");
        assertThat(toString).contains("url='ldap://localhost:389'");
        assertThat(toString).contains("bindPassword='[PROTECTED]'");
        assertThat(toString).doesNotContain("secret");
    }

    @Test
    void shouldTestUploadConfigDefaults() {
        ArchiveSearchProperties.UploadConfig uploadConfig = new ArchiveSearchProperties.UploadConfig();
        
        assertThat(uploadConfig.getUploadDirectory()).isEqualTo("/opt/uploads");
        assertThat(uploadConfig.getAllowedExtensions()).containsExactly(".txt", ".sql", ".xml", ".json", 
                                                                       ".properties", ".yml", ".yaml", ".log");
        assertThat(uploadConfig.getMaxUploadSize()).isEqualTo(100L * 1024 * 1024);
        assertThat(uploadConfig.getTempDirectory()).isEqualTo("/tmp/file-uploads");
        assertThat(uploadConfig.isCreateDirectories()).isTrue();
        assertThat(uploadConfig.isPreserveTimestamps()).isTrue();
        assertThat(uploadConfig.getMaxConcurrentUploads()).isEqualTo(3);
    }

    @Test
    void shouldSetAndGetUploadConfigProperties() {
        ArchiveSearchProperties.UploadConfig uploadConfig = new ArchiveSearchProperties.UploadConfig();
        List<String> extensions = Arrays.asList(".txt", ".csv");
        
        uploadConfig.setUploadDirectory("/custom/uploads");
        uploadConfig.setAllowedExtensions(extensions);
        uploadConfig.setMaxUploadSize(50L * 1024 * 1024);
        uploadConfig.setTempDirectory("/custom/temp");
        uploadConfig.setCreateDirectories(false);
        uploadConfig.setPreserveTimestamps(false);
        uploadConfig.setMaxConcurrentUploads(5);
        
        assertThat(uploadConfig.getUploadDirectory()).isEqualTo("/custom/uploads");
        assertThat(uploadConfig.getAllowedExtensions()).isEqualTo(extensions);
        assertThat(uploadConfig.getMaxUploadSize()).isEqualTo(50L * 1024 * 1024);
        assertThat(uploadConfig.getTempDirectory()).isEqualTo("/custom/temp");
        assertThat(uploadConfig.isCreateDirectories()).isFalse();
        assertThat(uploadConfig.isPreserveTimestamps()).isFalse();
        assertThat(uploadConfig.getMaxConcurrentUploads()).isEqualTo(5);
    }

    @Test
    void shouldTestUploadConfigToString() {
        ArchiveSearchProperties.UploadConfig uploadConfig = new ArchiveSearchProperties.UploadConfig();
        
        String toString = uploadConfig.toString();
        
        assertThat(toString).contains("UploadConfig{");
        assertThat(toString).contains("uploadDirectory='/opt/uploads'");
        assertThat(toString).contains("maxUploadSize=104857600");
        assertThat(toString).contains("createDirectories=true");
    }

    @Test
    void shouldTestAuditConfigDefaults() {
        ArchiveSearchProperties.AuditConfig auditConfig = new ArchiveSearchProperties.AuditConfig();
        
        assertThat(auditConfig.getLogFile()).isEqualTo("/var/log/archive-search/audit.log");
        assertThat(auditConfig.getMaxFileSize()).isEqualTo("10MB");
        assertThat(auditConfig.getMaxHistory()).isEqualTo(30);
        assertThat(auditConfig.isEnabled()).isTrue();
        assertThat(auditConfig.getLogPattern()).isEqualTo("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        assertThat(auditConfig.isLogToConsole()).isFalse();
        assertThat(auditConfig.isCompressRotatedFiles()).isTrue();
    }

    @Test
    void shouldSetAndGetAuditConfigProperties() {
        ArchiveSearchProperties.AuditConfig auditConfig = new ArchiveSearchProperties.AuditConfig();
        
        auditConfig.setLogFile("/custom/audit.log");
        auditConfig.setMaxFileSize("20MB");
        auditConfig.setMaxHistory(60);
        auditConfig.setEnabled(false);
        auditConfig.setLogPattern("%d - %msg%n");
        auditConfig.setLogToConsole(true);
        auditConfig.setCompressRotatedFiles(false);
        
        assertThat(auditConfig.getLogFile()).isEqualTo("/custom/audit.log");
        assertThat(auditConfig.getMaxFileSize()).isEqualTo("20MB");
        assertThat(auditConfig.getMaxHistory()).isEqualTo(60);
        assertThat(auditConfig.isEnabled()).isFalse();
        assertThat(auditConfig.getLogPattern()).isEqualTo("%d - %msg%n");
        assertThat(auditConfig.isLogToConsole()).isTrue();
        assertThat(auditConfig.isCompressRotatedFiles()).isFalse();
    }

    @Test
    void shouldTestAuditConfigToString() {
        ArchiveSearchProperties.AuditConfig auditConfig = new ArchiveSearchProperties.AuditConfig();
        
        String toString = auditConfig.toString();
        
        assertThat(toString).contains("AuditConfig{");
        assertThat(toString).contains("logFile='/var/log/archive-search/audit.log'");
        assertThat(toString).contains("enabled=true");
        assertThat(toString).contains("compressRotatedFiles=true");
    }

    @EnableConfigurationProperties(ArchiveSearchProperties.class)
    static class TestConfiguration {
    }
}