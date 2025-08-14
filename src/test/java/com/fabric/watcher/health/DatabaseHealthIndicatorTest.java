package com.fabric.watcher.health;

import com.fabric.watcher.service.DatabaseExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseHealthIndicatorTest {
    
    @Mock
    private DatabaseExecutor databaseExecutor;
    
    private DatabaseHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new DatabaseHealthIndicator(databaseExecutor);
    }
    
    @Test
    void checkHealth_WhenDatabaseConnected_ShouldReturnUp() {
        // Given
        when(databaseExecutor.testConnection()).thenReturn(true);
        when(databaseExecutor.getDatabaseInfo()).thenReturn("Database: testdb | Version: 1.0");
        
        // When
        Map<String, Object> health = healthIndicator.checkHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("UP");
        assertThat(health.get("database")).isEqualTo("Connected");
        assertThat(health.get("info")).isEqualTo("Database: testdb | Version: 1.0");
        assertThat(health).containsKey("timestamp");
    }
    
    @Test
    void checkHealth_WhenDatabaseDisconnected_ShouldReturnDown() {
        // Given
        when(databaseExecutor.testConnection()).thenReturn(false);
        
        // When
        Map<String, Object> health = healthIndicator.checkHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("DOWN");
        assertThat(health.get("database")).isEqualTo("Disconnected");
        assertThat(health.get("error")).isEqualTo("Connection test failed");
        assertThat(health).containsKey("timestamp");
    }
    
    @Test
    void checkHealth_WhenExceptionThrown_ShouldReturnDown() {
        // Given
        RuntimeException exception = new RuntimeException("Database error");
        when(databaseExecutor.testConnection()).thenThrow(exception);
        
        // When
        Map<String, Object> health = healthIndicator.checkHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("DOWN");
        assertThat(health.get("database")).isEqualTo("Error");
        assertThat(health.get("error")).isEqualTo("Database error");
        assertThat(health.get("exception")).isEqualTo("RuntimeException");
        assertThat(health).containsKey("timestamp");
    }
}