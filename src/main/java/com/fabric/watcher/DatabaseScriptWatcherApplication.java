package com.fabric.watcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DatabaseScriptWatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatabaseScriptWatcherApplication.class, args);
    }
}