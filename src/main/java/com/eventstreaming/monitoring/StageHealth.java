package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Data model representing the health status of a specific pipeline stage.
 * Contains health indicators, failure counts, and status timing information.
 */
public record StageHealth(
    @JsonProperty("stage")
    PipelineStage stage,
    
    @JsonProperty("isHealthy")
    boolean isHealthy,
    
    @JsonProperty("consecutiveFailures")
    int consecutiveFailures,
    
    @JsonProperty("lastUpdated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime lastUpdated
) {
    
    /**
     * Gets the health status as a descriptive string.
     */
    public String getHealthStatus() {
        if (isHealthy) {
            return "Healthy";
        } else if (consecutiveFailures >= 10) {
            return "Critical";
        } else if (consecutiveFailures >= 5) {
            return "Degraded";
        } else {
            return "Warning";
        }
    }
    
    /**
     * Gets the health status color for UI display.
     */
    public String getHealthColor() {
        return switch (getHealthStatus()) {
            case "Healthy" -> "#28a745";    // Green
            case "Warning" -> "#ffc107";    // Yellow
            case "Degraded" -> "#fd7e14";   // Orange
            case "Critical" -> "#dc3545";   // Red
            default -> "#6c757d";           // Gray
        };
    }
    
    /**
     * Gets the health score as a percentage (100% = healthy, 0% = critical).
     */
    public double getHealthScore() {
        if (isHealthy) {
            return 100.0;
        } else {
            // Decrease score based on consecutive failures
            double penalty = Math.min(consecutiveFailures * 10.0, 90.0);
            return Math.max(10.0, 100.0 - penalty);
        }
    }
    
    /**
     * Checks if this stage requires immediate attention.
     */
    public boolean requiresAttention() {
        return !isHealthy && consecutiveFailures >= 5;
    }
    
    /**
     * Gets a user-friendly description of the current health status.
     */
    public String getHealthDescription() {
        if (isHealthy) {
            return stage.getDisplayName() + " is operating normally";
        } else {
            return switch (getHealthStatus()) {
                case "Warning" -> stage.getDisplayName() + " has experienced " + consecutiveFailures + " recent failures";
                case "Degraded" -> stage.getDisplayName() + " is experiencing performance issues (" + consecutiveFailures + " failures)";
                case "Critical" -> stage.getDisplayName() + " is in critical state with " + consecutiveFailures + " consecutive failures";
                default -> stage.getDisplayName() + " status unknown";
            };
        }
    }
    
    /**
     * Gets the time since last health update in minutes.
     */
    public long getMinutesSinceUpdate() {
        return java.time.Duration.between(lastUpdated, LocalDateTime.now()).toMinutes();
    }
    
    /**
     * Checks if the health data is stale (not updated in the last 10 minutes).
     */
    public boolean isStale() {
        return getMinutesSinceUpdate() > 10;
    }
    
    /**
     * Gets the trend direction based on consecutive failures.
     */
    public HealthTrend getTrend() {
        if (isHealthy) {
            return HealthTrend.IMPROVING;
        } else if (consecutiveFailures <= 2) {
            return HealthTrend.STABLE;
        } else {
            return HealthTrend.DECLINING;
        }
    }
    
    /**
     * Gets a formatted timestamp for display.
     */
    public String getFormattedLastUpdated() {
        return lastUpdated.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    
    /**
     * Creates a new StageHealth with updated failure count.
     */
    public StageHealth withFailureCount(int newFailureCount) {
        boolean newHealthy = newFailureCount < 5;
        return new StageHealth(stage, newHealthy, newFailureCount, LocalDateTime.now());
    }
    
    /**
     * Creates a new StageHealth marking it as healthy (resets failure count).
     */
    public StageHealth asHealthy() {
        return new StageHealth(stage, true, 0, LocalDateTime.now());
    }
    
    /**
     * Enumeration for health trend directions.
     */
    public enum HealthTrend {
        IMPROVING("Improving", "#28a745"),
        STABLE("Stable", "#17a2b8"),
        DECLINING("Declining", "#dc3545");
        
        private final String displayName;
        private final String color;
        
        HealthTrend(String displayName, String color) {
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