package com.eventstreaming.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Profile;

/**
 * Configuration to load local development properties.
 * This loads the local-config-private.properties file for development.
 */
@Configuration
@Profile({"development", "minimal"})
@PropertySource(value = "file:local-config-private.properties", ignoreResourceNotFound = true)
public class LocalPropertiesConfig {
    // This class just loads the properties file
}