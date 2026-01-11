package com.eventstreaming.migration;

/**
 * Exception thrown when event transformation fails during migration
 */
public class EventTransformationException extends RuntimeException {
    
    public EventTransformationException(String message) {
        super(message);
    }
    
    public EventTransformationException(String message, Throwable cause) {
        super(message, cause);
    }
}