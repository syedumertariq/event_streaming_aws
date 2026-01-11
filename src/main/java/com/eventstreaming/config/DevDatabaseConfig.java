package com.eventstreaming.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Development database configuration.
 * 
 * This configuration ensures that in dev profile, we rely on Spring Boot's
 * auto-configuration for H2 database instead of the custom MySQL configuration.
 */
@Configuration
@Profile("dev")
public class DevDatabaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DevDatabaseConfig.class);
    
    public DevDatabaseConfig() {
        logger.info("Development database configuration loaded - using H2 in-memory database");
        logger.info("MySQL DatabaseConfig should be disabled in dev profile");
    }
}