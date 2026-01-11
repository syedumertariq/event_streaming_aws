package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration representing the different stages in the event processing pipeline.
 * Each stage represents a major component in the Kafka → Pekko → H2 flow.
 */
public enum PipelineStage {
    
    /**
     * Kafka stage - where events are initially received and queued.
     */
    KAFKA("kafka", "Kafka Message Queue", "Events received from external sources"),
    
    /**
     * Pekko stage - where events are processed by actors and business logic.
     */
    PEKKO("pekko", "Pekko Actor System", "Events processed by actor system"),
    
    /**
     * H2 stage - where events are persisted to the database.
     */
    H2("h2", "H2 Database", "Events persisted to database");
    
    private final String code;
    private final String displayName;
    private final String description;
    
    PipelineStage(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Gets the stage code for API and configuration usage.
     */
    @JsonValue
    public String getCode() {
        return code;
    }
    
    /**
     * Gets the human-readable display name.
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the stage description.
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the next stage in the pipeline, or null if this is the final stage.
     */
    public PipelineStage getNextStage() {
        return switch (this) {
            case KAFKA -> PEKKO;
            case PEKKO -> H2;
            case H2 -> null;
        };
    }
    
    /**
     * Gets the previous stage in the pipeline, or null if this is the first stage.
     */
    public PipelineStage getPreviousStage() {
        return switch (this) {
            case KAFKA -> null;
            case PEKKO -> KAFKA;
            case H2 -> PEKKO;
        };
    }
    
    /**
     * Checks if this is the first stage in the pipeline.
     */
    public boolean isFirstStage() {
        return this == KAFKA;
    }
    
    /**
     * Checks if this is the final stage in the pipeline.
     */
    public boolean isFinalStage() {
        return this == H2;
    }
    
    /**
     * Gets the stage order (0-based index).
     */
    public int getOrder() {
        return switch (this) {
            case KAFKA -> 0;
            case PEKKO -> 1;
            case H2 -> 2;
        };
    }
    
    /**
     * Creates a PipelineStage from its code.
     */
    public static PipelineStage fromCode(String code) {
        for (PipelineStage stage : values()) {
            if (stage.code.equalsIgnoreCase(code)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown pipeline stage code: " + code);
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}