package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

/**
 * Data model representing flow metrics for the event processing pipeline.
 * Contains information about event progression rates and processing times between stages.
 */
public record PipelineFlowMetrics(
    @JsonProperty("kafkaToPekkoFlow")
    double kafkaToPekkoFlow,
    
    @JsonProperty("pekkoToH2Flow")
    double pekkoToH2Flow,
    
    @JsonProperty("endToEndFlow")
    double endToEndFlow,
    
    @JsonProperty("avgKafkaProcessingTime")
    Duration avgKafkaProcessingTime,
    
    @JsonProperty("avgPekkoProcessingTime")
    Duration avgPekkoProcessingTime,
    
    @JsonProperty("avgH2ProcessingTime")
    Duration avgH2ProcessingTime,
    
    @JsonProperty("totalPipelineTime")
    Duration totalPipelineTime,
    
    @JsonProperty("eventsInPeriod")
    long eventsInPeriod
) {
    
    /**
     * Gets the Kafka to Pekko flow rate as a percentage.
     */
    public double getKafkaToPekkoFlowPercent() {
        return Math.min(100.0, Math.max(0.0, kafkaToPekkoFlow));
    }
    
    /**
     * Gets the Pekko to H2 flow rate as a percentage.
     */
    public double getPekkoToH2FlowPercent() {
        return Math.min(100.0, Math.max(0.0, pekkoToH2Flow));
    }
    
    /**
     * Gets the end-to-end flow rate as a percentage.
     */
    public double getEndToEndFlowPercent() {
        return Math.min(100.0, Math.max(0.0, endToEndFlow));
    }
    
    /**
     * Gets the average Kafka processing time in milliseconds.
     */
    public long getAvgKafkaProcessingTimeMs() {
        return avgKafkaProcessingTime.toMillis();
    }
    
    /**
     * Gets the average Pekko processing time in milliseconds.
     */
    public long getAvgPekkoProcessingTimeMs() {
        return avgPekkoProcessingTime.toMillis();
    }
    
    /**
     * Gets the average H2 processing time in milliseconds.
     */
    public long getAvgH2ProcessingTimeMs() {
        return avgH2ProcessingTime.toMillis();
    }
    
    /**
     * Gets the total pipeline processing time in milliseconds.
     */
    public long getTotalPipelineTimeMs() {
        return totalPipelineTime.toMillis();
    }
    
    /**
     * Identifies the slowest stage in the pipeline.
     */
    public PipelineStage getSlowestStage() {
        Duration maxTime = avgKafkaProcessingTime;
        PipelineStage slowestStage = PipelineStage.KAFKA;
        
        if (avgPekkoProcessingTime.compareTo(maxTime) > 0) {
            maxTime = avgPekkoProcessingTime;
            slowestStage = PipelineStage.PEKKO;
        }
        
        if (avgH2ProcessingTime.compareTo(maxTime) > 0) {
            slowestStage = PipelineStage.H2;
        }
        
        return slowestStage;
    }
    
    /**
     * Identifies the fastest stage in the pipeline.
     */
    public PipelineStage getFastestStage() {
        Duration minTime = avgKafkaProcessingTime;
        PipelineStage fastestStage = PipelineStage.KAFKA;
        
        if (avgPekkoProcessingTime.compareTo(minTime) < 0) {
            minTime = avgPekkoProcessingTime;
            fastestStage = PipelineStage.PEKKO;
        }
        
        if (avgH2ProcessingTime.compareTo(minTime) < 0) {
            fastestStage = PipelineStage.H2;
        }
        
        return fastestStage;
    }
    
    /**
     * Gets the flow efficiency rating based on end-to-end flow percentage.
     */
    public FlowEfficiency getFlowEfficiency() {
        double endToEndPercent = getEndToEndFlowPercent();
        
        if (endToEndPercent >= 95.0) {
            return FlowEfficiency.EXCELLENT;
        } else if (endToEndPercent >= 85.0) {
            return FlowEfficiency.GOOD;
        } else if (endToEndPercent >= 70.0) {
            return FlowEfficiency.FAIR;
        } else if (endToEndPercent >= 50.0) {
            return FlowEfficiency.POOR;
        } else {
            return FlowEfficiency.CRITICAL;
        }
    }
    
    /**
     * Gets the processing speed rating based on total pipeline time.
     */
    public ProcessingSpeed getProcessingSpeed() {
        long totalMs = getTotalPipelineTimeMs();
        
        if (totalMs <= 100) {
            return ProcessingSpeed.VERY_FAST;
        } else if (totalMs <= 500) {
            return ProcessingSpeed.FAST;
        } else if (totalMs <= 1000) {
            return ProcessingSpeed.MODERATE;
        } else if (totalMs <= 2000) {
            return ProcessingSpeed.SLOW;
        } else {
            return ProcessingSpeed.VERY_SLOW;
        }
    }
    
    /**
     * Calculates the throughput in events per second based on the period.
     */
    public double getEventsPerSecond() {
        if (eventsInPeriod == 0 || totalPipelineTime.isZero()) {
            return 0.0;
        }
        return eventsInPeriod / (double) totalPipelineTime.getSeconds();
    }
    
    /**
     * Gets a summary description of the pipeline flow performance.
     */
    public String getFlowSummary() {
        FlowEfficiency efficiency = getFlowEfficiency();
        ProcessingSpeed speed = getProcessingSpeed();
        
        return String.format("Flow: %s, Speed: %s (%.1f%% end-to-end, %dms total)", 
            efficiency.getDisplayName(), 
            speed.getDisplayName(),
            getEndToEndFlowPercent(),
            getTotalPipelineTimeMs());
    }
    
    /**
     * Enumeration for flow efficiency ratings.
     */
    public enum FlowEfficiency {
        EXCELLENT("Excellent", "#28a745"),
        GOOD("Good", "#20c997"),
        FAIR("Fair", "#ffc107"),
        POOR("Poor", "#fd7e14"),
        CRITICAL("Critical", "#dc3545");
        
        private final String displayName;
        private final String color;
        
        FlowEfficiency(String displayName, String color) {
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
    
    /**
     * Enumeration for processing speed ratings.
     */
    public enum ProcessingSpeed {
        VERY_FAST("Very Fast", "#28a745"),
        FAST("Fast", "#20c997"),
        MODERATE("Moderate", "#17a2b8"),
        SLOW("Slow", "#ffc107"),
        VERY_SLOW("Very Slow", "#dc3545");
        
        private final String displayName;
        private final String color;
        
        ProcessingSpeed(String displayName, String color) {
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