package com.eventstreaming.kafka;

/**
 * Exception thrown when there are issues with Kafka configuration loading or validation.
 * This exception is used to indicate problems such as missing configuration files,
 * invalid property values, or missing required properties.
 */
public class KafkaConfigurationException extends Exception {
    
    /**
     * Constructs a new KafkaConfigurationException with the specified detail message.
     * 
     * @param message the detail message explaining the configuration error
     */
    public KafkaConfigurationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new KafkaConfigurationException with the specified detail message and cause.
     * 
     * @param message the detail message explaining the configuration error
     * @param cause the cause of the configuration error
     */
    public KafkaConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new KafkaConfigurationException with the specified cause.
     * 
     * @param cause the cause of the configuration error
     */
    public KafkaConfigurationException(Throwable cause) {
        super(cause);
    }
}