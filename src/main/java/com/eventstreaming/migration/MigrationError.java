package com.eventstreaming.migration;

import java.time.Instant;

/**
 * Represents an error that occurred during migration
 */
public class MigrationError {
    
    public enum ErrorType {
        TRANSFORMATION_ERROR,
        ACTOR_PROCESSING_ERROR,
        DATABASE_ERROR,
        GENERAL_ERROR
    }
    
    private final ErrorType errorType;
    private final String recordId;
    private final String userId;
    private final String errorMessage;
    private final String context;
    private final Instant timestamp;
    
    public MigrationError(ErrorType errorType, String recordId, String userId, 
                         String errorMessage, String context, Instant timestamp) {
        this.errorType = errorType;
        this.recordId = recordId;
        this.userId = userId;
        this.errorMessage = errorMessage;
        this.context = context;
        this.timestamp = timestamp;
    }
    
    // Getters
    public ErrorType getErrorType() { return errorType; }
    public String getRecordId() { return recordId; }
    public String getUserId() { return userId; }
    public String getErrorMessage() { return errorMessage; }
    public String getContext() { return context; }
    public Instant getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("MigrationError{type=%s, recordId='%s', userId='%s', message='%s', timestamp=%s}", 
                           errorType, recordId, userId, errorMessage, timestamp);
    }
}