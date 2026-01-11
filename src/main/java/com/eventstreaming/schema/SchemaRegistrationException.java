package com.eventstreaming.schema;

/**
 * Exception thrown when schema registration fails.
 */
public class SchemaRegistrationException extends Exception {
    
    public SchemaRegistrationException(String message) {
        super(message);
    }
    
    public SchemaRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}