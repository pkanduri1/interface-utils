package com.fabric.watcher.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI configuration for the Database Script Watcher application.
 * Provides comprehensive API documentation for all endpoints.
 */
@Configuration
public class SwaggerConfig {

    @Value("${spring.application.name:database-script-watcher}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Database Script Watcher API")
                        .description("""
                                Comprehensive API for the Database Script Watcher application.
                                
                                This application provides automated file processing capabilities including:
                                - SQL script execution and monitoring
                                - SQL Loader log processing
                                - Archive search and file management (non-production only)
                                - System monitoring and health checks
                                
                                **Security Notice**: The Archive Search API is only available in non-production 
                                environments for security reasons. It will be automatically disabled when 
                                running in production mode.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Development Team")
                                .email("dev-team@fabric.com")
                                .url("https://fabric.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://fabric.com/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("https://dev-api.fabric.com")
                                .description("Development server"),
                        new Server()
                                .url("https://test-api.fabric.com")
                                .description("Test server")
                ));
    }
}