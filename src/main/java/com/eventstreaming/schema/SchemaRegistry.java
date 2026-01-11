package com.eventstreaming.schema;

import java.util.List;
import java.util.Optional;

/**
 * Interface for managing event schemas with support for schema evolution.
 * Provides methods to register, retrieve, and manage schema versions.
 */
public interface SchemaRegistry {
    
    /**
     * Registers a new schema for an event type.
     * 
     * @param eventType the event type name
     * @param schemaContent the schema content
     * @param version the schema version
     * @throws SchemaRegistrationException if registration fails
     */
    void registerSchema(String eventType, String schemaContent, String version) throws SchemaRegistrationException;
    
    /**
     * Gets the latest schema for an event type.
     * 
     * @param eventType the event type name
     * @return the latest schema content, or null if not found
     */
    String getLatestSchema(String eventType);
    
    /**
     * Gets a specific version of schema for an event type.
     * 
     * @param eventType the event type name
     * @param version the schema version
     * @return the schema content, or null if not found
     */
    String getSchema(String eventType, String version);
    
    /**
     * Gets the latest schema version for an event type.
     * 
     * @param eventType the event type name
     * @return the latest version, or null if not found
     */
    String getLatestSchemaVersion(String eventType);
    
    /**
     * Gets all available versions for an event type.
     * 
     * @param eventType the event type name
     * @return list of available versions, ordered from oldest to newest
     */
    List<String> getAvailableVersions(String eventType);
    
    /**
     * Checks if a schema is compatible with the latest version.
     * 
     * @param eventType the event type name
     * @param schemaContent the new schema content
     * @return compatibility result
     */
    CompatibilityResult checkCompatibility(String eventType, String schemaContent);
    
    /**
     * Validates that a schema is well-formed and valid.
     * 
     * @param schemaContent the schema content to validate
     * @param format the schema format
     * @return validation result
     */
    SchemaValidationResult validateSchema(String schemaContent, SchemaFormat format);
    
    /**
     * Gets all registered event types.
     * 
     * @return list of event type names
     */
    List<String> getRegisteredEventTypes();
}