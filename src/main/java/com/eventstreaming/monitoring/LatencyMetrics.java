package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

/**
 * Data model for latency performance metrics.
 * Represents processing latency measurements for the Kafka → Pekko → H2 pipeline.
 */
public record LatencyMetrics(
    @JsonProperty("averageLatency")
    Duration averageLatency,
    
    @JsonProperty("p95Latency")
    Duration p95Latency,
    
    @JsonProperty("maxLatency")
    Duration maxLatency
) {
    
    /**
     * Gets average latency in milliseconds.
     */
    public long getAverageLatencyMs() {
        return averageLatency.toMillis();
    }
    
    /**
     * Gets P95 latency in milliseconds.
     */
    public long getP95LatencyMs() {
        return p95Latency.toMillis();
    }
    
    /**
     * Gets maximum latency in milliseconds.
     */
    public long getMaxLatencyMs() {
        return maxLatency.toMillis();
    }
    
    /**
     * Checks if latency is considered healthy (average < 1000ms, P95 < 2000ms).
     */
    public boolean isHealthy() {
        return getAverageLatencyMs() < 1000 && getP95LatencyMs() < 2000;
    }
    
    /**
     * Gets latency performance level.
     */
    public LatencyLevel getLatencyLevel() {
        long avgMs = getAverageLatencyMs();
        if (avgMs < 100) {
            return LatencyLevel.EXCELLENT;
        } else if (avgMs < 500) {
            return LatencyLevel.GOOD;
        } else if (avgMs < 1000) {
            return LatencyLevel.ACCEPTABLE;
        } else if (avgMs < 2000) {
            return LatencyLevel.POOR;
        } else {
            return LatencyLevel.CRITICAL;
        }
    }
    
    /**
     * Gets a human-readable description of the latency status.
     */
    public String getStatusDescription() {
        return switch (getLatencyLevel()) {
            case EXCELLENT -> "Excellent response times";
            case GOOD -> "Good response times";
            case ACCEPTABLE -> "Acceptable response times";
            case POOR -> "Poor response times - optimization needed";
            case CRITICAL -> "Critical response times - immediate attention required";
        };
    }
    
    /**
     * Enumeration for latency performance levels.
     */
    public enum LatencyLevel {
        EXCELLENT, GOOD, ACCEPTABLE, POOR, CRITICAL
    }
}