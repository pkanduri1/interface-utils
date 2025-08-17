package com.fabric.watcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test application configuration for integration tests.
 * This provides the @SpringBootApplication annotation needed for Spring Boot tests.
 */
@SpringBootApplication
public class TestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}