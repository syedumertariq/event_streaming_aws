package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Data model representing the current status of the entire event processing pipeline.
 * Contains event counts, error counts, throughput metrics, and health information for all stages.
 */
public record PipelineStatus(
    @JsonProperty("kafkaEventsReceived")
    long kafkaEventsReceived,
    
    @JsonProperty("pekkoEventsProcessed")
    long pekkoEventsProcessed,
    
    @JsonProperty("h2EventsPersisted")
    long h2EventsPersisted,
    
    @JsonProperty("kafkaErrors")
    long kafkaErrors,
    
    @JsonProperty("pekkoErrors")
    long pekkoErrors,
    
    @JsonProperty("h2Errors")
    long h2Errors,
    
    @JsonProperty("kafkaThroughput")
    double kafkaThroughput,
    
    @JsonProperty("pekkoThroughput")
    double pekkoThroughput,
    
    @JsonProperty("h2Throughput")
    double h2Throughput,
    
    @JsonProperty("kafkaHealth")
    StageHealth kafkaHealth,
    
    @JsonProperty("pekkoHealth")
    StageHealth pekkoHealth,
    
    @JsonProperty("h2Health")
    StageHealth h2Health,
    
    @JsonProperty("pipelineEfficiency")
    double pipelineEfficiency,
    
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp
) {
    
    /**
     * Gets the total number of events that have entered the pipeline.
     */
    public long getTotalEventsReceived() {
        return kafkaEventsReceived;
    }
    
    /**
     * Gets the total number of events that have completed the pipeline.
     */
    public long getTotalEventsCompleted() {
        return h2EventsPersisted;
    }
    
    /**
     * Gets the total number of errors across all pipeline stages.
     */
    public long getTotalErrors() {
        return kafkaErrors + pekkoErrors + h2Errors;
    }
    
    /**
     * Gets the overall pipeline success rate as a percentage.
     */
    public double getSuccessRate() {
        long totalEvents = getTotalEventsReceived();
        if (totalEvents == 0) {
            return 100.0;
        }
        long successfulEvents = getTotalEventsCompleted();
        return (double) successfulEvents / totalEvents * 100.0;
    }
    
    /**
     * Gets the overall pipeline error rate as a percentage.
     */
    public double getErrorRate() {
        long totalEvents = getTotalEventsReceived();
        if (totalEvents == 0) {
            return 0.0;
        }
        long totalErrors = getTotalErrors();
        return (double) totalErrors / totalEvents * 100.0;
    }
    
    /**
     * Checks if the entire pipeline is healthy (all stages healthy and low error rate).
     */
    public boolean isPipelineHealthy() {
        return kafkaHealth.isHealthy() && 
               pekkoHealth.isHealthy() && 
               h2Health.isHealthy() && 
               getErrorRate() < 5.0; // Less than 5% error rate
    }
    
    /**
     * Gets the pipeline health status as a descriptive string.
     */
    public String getPipelineHealthStatus() {
        if (isPipelineHealthy()) {
            return "Healthy";
        } else if (getErrorRate() > 20.0) {
            return "Critical";
        } else if (getErrorRate() > 10.0) {
            return "Degraded";
        } else {
            return "Warning";
        }
    }
    
    /**
     * Gets the bottleneck stage (stage with lowest throughput).
     */
    public PipelineStage getBottleneckStage() {
        double minThroughput = Math.min(kafkaThroughput, Math.min(pekkoThroughput, h2Throughput));
        
        if (minThroughput == kafkaThroughput) {
            return PipelineStage.KAFKA;
        } else if (minThroughput == pekkoThroughput) {
            return PipelineStage.PEKKO;
        } else {
            return PipelineStage.H2;
        }
    }
    
    /**
     * Gets the average throughput across all pipeline stages.
     */
    public double getAverageThroughput() {
        return (kafkaThroughput + pekkoThroughput + h2Throughput) / 3.0;
    }
    
    /**
     * Gets events currently in progress (received but not yet persisted).
     */
    public long getEventsInProgress() {
        return Math.max(0, kafkaEventsReceived - h2EventsPersisted);
    }
    
    /**
     * Gets the pipeline utilization as a percentage (how much of the pipeline capacity is being used).
     */
    public double getPipelineUtilization() {
        // Simplified calculation based on throughput vs theoretical maximum
        double maxThroughput = 1000.0; // events per minute (configurable)
        double currentThroughput = getAverageThroughput();
        return Math.min(100.0, (currentThroughput / maxThroughput) * 100.0);
    }
}