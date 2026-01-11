package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Data model representing an error that occurred in the event processing pipeline.
 * Contains information about the error location, context, and timing.
 */
public record PipelineError(
    @JsonProperty("stage")
    PipelineStage stage,
    
    @JsonProperty("errorMessage")
    String errorMessage,
    
    @JsonProperty("eventType")
    String eventType,
    
    @JsonProperty("userId")
    String userId,
    
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp
) {
    
    /**
     * Gets the error severity based on the stage and error message.
     */
    public ErrorSeverity getSeverity() {
        // Determine severity based on error message keywords
        String lowerMessage = errorMessage.toLowerCase();
        
        if (lowerMessage.contains("timeout") || lowerMessage.contains("connection")) {
            return ErrorSeverity.HIGH;
        } else if (lowerMessage.contains("validation") || lowerMessage.contains("format")) {
            return ErrorSeverity.MEDIUM;
        } else if (lowerMessage.contains("retry") || lowerMessage.contains("temporary")) {
            return ErrorSeverity.LOW;
        } else {
            return ErrorSeverity.MEDIUM; // Default severity
        }
    }
    
    /**
     * Gets a shortened version of the error message for display purposes.
     */
    public String getShortErrorMessage() {
        if (errorMessage.length() <= 50) {
            return errorMessage;
        }
        return errorMessage.substring(0, 47) + "...";
    }
    
    /**
     * Gets a user-friendly description of the error location.
     */
    public String getLocationDescription() {
        return switch (stage) {
            case KAFKA -> "Message queue processing";
            case PEKKO -> "Actor system processing";
            case H2 -> "Database persistence";
        };
    }
    
    /**
     * Checks if this error is recent (within the last 5 minutes).
     */
    public boolean isRecent() {
        return timestamp.isAfter(LocalDateTime.now().minusMinutes(5));
    }
    
    /**
     * Gets the age of this error in minutes.
     */
    public long getAgeInMinutes() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
    }
    
    /**
     * Gets a formatted timestamp for display.
     */
    public String getFormattedTimestamp() {
        return timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    
    /**
     * Checks if this error affects the same user and event type as another error.
     */
    public boolean isSimilarTo(PipelineError other) {
        return this.stage == other.stage &&
               this.eventType.equals(other.eventType) &&
               this.userId.equals(other.userId);
    }
    
    /**
     * Gets a unique identifier for grouping similar errors.
     */
    public String getErrorGroupId() {
        return stage.getCode() + ":" + eventType + ":" + 
               errorMessage.replaceAll("\\d+", "X"); // Replace numbers with X for grouping
    }
    
    /**
     * Enumeration for error severity levels.
     */
    public enum ErrorSeverity {
        LOW("Low", "#28a745"),      // Green
        MEDIUM("Medium", "#ffc107"), // Yellow
        HIGH("High", "#dc3545");     // Red
        
        private final String displayName;
        private final String color;
        
        ErrorSeverity(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getColor() {
            return color;
        }
    }
}