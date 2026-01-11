package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Data model for a single performance data point used in trend analysis.
 * Represents performance metrics at a specific point in time.
 */
public record PerformanceDataPoint(
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp,
    
    @JsonProperty("throughput")
    double throughput,
    
    @JsonProperty("cpuUsage")
    double cpuUsage,
    
    @JsonProperty("memoryUsage")
    double memoryUsage
) {
    
    /**
     * Checks if this data point represents healthy performance.
     */
    public boolean isHealthy() {
        return cpuUsage < 80.0 && memoryUsage < 85.0 && throughput > 0.0;
    }
    
    /**
     * Gets a performance score (0-100) based on resource usage and throughput.
     */
    public double getPerformanceScore() {
        // Lower resource usage and higher throughput = better score
        double cpuScore = Math.max(0, 100 - cpuUsage);
        double memoryScore = Math.max(0, 100 - memoryUsage);
        double throughputScore = Math.min(100, throughput * 10); // Scale throughput to 0-100
        
        return (cpuScore + memoryScore + throughputScore) / 3.0;
    }
    
    /**
     * Gets performance level based on the performance score.
     */
    public PerformanceLevel getPerformanceLevel() {
        double score = getPerformanceScore();
        if (score >= 80) {
            return PerformanceLevel.EXCELLENT;
        } else if (score >= 60) {
            return PerformanceLevel.GOOD;
        } else if (score >= 40) {
            return PerformanceLevel.FAIR;
        } else if (score >= 20) {
            return PerformanceLevel.POOR;
        } else {
            return PerformanceLevel.CRITICAL;
        }
    }
    
    /**
     * Compares this data point with another to determine performance trend.
     */
    public TrendDirection compareTo(PerformanceDataPoint other) {
        double scoreDiff = this.getPerformanceScore() - other.getPerformanceScore();
        if (Math.abs(scoreDiff) < 5.0) {
            return TrendDirection.STABLE;
        } else if (scoreDiff > 0) {
            return TrendDirection.IMPROVING;
        } else {
            return TrendDirection.DECLINING;
        }
    }
    
    /**
     * Enumeration for performance levels.
     */
    public enum PerformanceLevel {
        EXCELLENT, GOOD, FAIR, POOR, CRITICAL
    }
    
    /**
     * Enumeration for trend directions.
     */
    public enum TrendDirection {
        IMPROVING, STABLE, DECLINING
    }
}