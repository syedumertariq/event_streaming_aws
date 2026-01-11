package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Data model for throughput performance metrics.
 * Represents events per second and peak throughput measurements.
 */
public record ThroughputMetrics(
    @JsonProperty("eventsPerSecond")
    double eventsPerSecond,
    
    @JsonProperty("peakThroughput")
    double peakThroughput,
    
    @JsonProperty("measurementTime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime measurementTime
) {
    
    /**
     * Gets throughput efficiency as a percentage of peak throughput.
     */
    public double getThroughputEfficiency() {
        return peakThroughput > 0 ? (eventsPerSecond / peakThroughput) * 100.0 : 0.0;
    }
    
    /**
     * Checks if current throughput is considered healthy (above 50% of peak).
     */
    public boolean isHealthy() {
        return getThroughputEfficiency() > 50.0;
    }
    
    /**
     * Gets a human-readable description of the throughput status.
     */
    public String getStatusDescription() {
        double efficiency = getThroughputEfficiency();
        if (efficiency > 80.0) {
            return "Excellent throughput performance";
        } else if (efficiency > 50.0) {
            return "Good throughput performance";
        } else if (efficiency > 20.0) {
            return "Moderate throughput performance";
        } else {
            return "Low throughput performance";
        }
    }
}