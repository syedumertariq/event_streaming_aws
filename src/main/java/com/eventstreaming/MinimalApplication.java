package com.eventstreaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Event Streaming Application for development testing.
 * Excludes Redis and Kafka auto-configuration to avoid dependencies.
 */
@SpringBootApplication
public class MinimalApplication {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "minimal");
        SpringApplication.run(MinimalApplication.class, args);
    }
}