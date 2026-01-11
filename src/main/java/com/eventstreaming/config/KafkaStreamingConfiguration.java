package com.eventstreaming.config;

import com.eventstreaming.streams.EventStreamingService;
import com.eventstreaming.controller.StreamHealthController;
import com.eventstreaming.monitoring.StreamProcessingMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Conditional configuration for Kafka streaming components.
 * Creates NoOp implementations when Kafka is disabled (AWS environment).
 */
@Configuration
public class KafkaStreamingConfiguration {
    
    /**
     * NoOp EventStreamingService for when Kafka is disabled.
     */
    @Bean
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = false)
    @Primary
    public EventStreamingService noOpEventStreamingService() {
        return new NoOpEventStreamingService();
    }
    
    /**
     * NoOp StreamHealthController for when Kafka is disabled.
     */
    @Bean
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = false)
    @Primary
    public StreamHealthController noOpStreamHealthController(EventStreamingService eventStreamingService) {
        return new NoOpStreamHealthController(eventStreamingService);
    }
    
    /**
     * NoOp StreamProcessingMetrics for when Kafka is disabled.
     */
    @Bean
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = false)
    @Primary
    public StreamProcessingMetrics noOpStreamProcessingMetrics() {
        return new NoOpStreamProcessingMetrics();
    }
    
    /**
     * NoOp implementation of EventStreamingService.
     */
    public static class NoOpEventStreamingService extends EventStreamingService {
        
        public NoOpEventStreamingService() {
            super(null, null); // No dependencies needed for NoOp
        }
        
        @Override
        public synchronized void startEventProcessing() {
            // NoOp - Kafka disabled
        }
        
        @Override
        public synchronized CompletionStage<org.apache.pekko.Done> stopEventProcessing() {
            return CompletableFuture.completedFuture(org.apache.pekko.Done.getInstance());
        }
        
        @Override
        public CompletionStage<org.apache.pekko.Done> restartEventProcessing() {
            return CompletableFuture.completedFuture(org.apache.pekko.Done.getInstance());
        }
        
        @Override
        public StreamingStatus getStatus() {
            return StreamingStatus.STOPPED;
        }
        
        @Override
        public boolean isRunning() {
            return false;
        }
        
        @Override
        public boolean isShuttingDown() {
            return false;
        }
        
        @Override
        public StreamHealthInfo getHealthInfo() {
            return new StreamHealthInfo(false, false, false, false);
        }
        
        @Override
        public PipelineInfo getPipelineInfo() {
            return new PipelineInfo(
                StreamingStatus.STOPPED,
                false,
                false, // Kafka disabled
                false,
                "Kafka streaming disabled for AWS deployment"
            );
        }
    }
    
    /**
     * NoOp implementation of StreamHealthController.
     */
    public static class NoOpStreamHealthController extends StreamHealthController {
        
        public NoOpStreamHealthController(EventStreamingService eventStreamingService) {
            super(eventStreamingService, new NoOpStreamProcessingMetrics());
        }
        
        @Override
        public ResponseEntity<Map<String, Object>> getStreamHealth() {
            Map<String, Object> response = Map.of(
                "status", "DISABLED",
                "message", "Kafka streaming is disabled for AWS deployment",
                "reason", "No Kafka cluster available in AWS environment",
                "isRunning", false,
                "isShuttingDown", false,
                "hasStreamControl", false,
                "streamIsActive", false,
                "timestamp", Instant.now()
            );
            
            return ResponseEntity.ok(response);
        }
        
        @Override
        public ResponseEntity<Map<String, Object>> restartStream() {
            Map<String, Object> response = Map.of(
                "message", "Kafka streaming is disabled - restart not available",
                "timestamp", Instant.now()
            );
            return ResponseEntity.ok(response);
        }
        
        @Override
        public ResponseEntity<Map<String, Object>> stopStream() {
            Map<String, Object> response = Map.of(
                "message", "Kafka streaming is disabled - already stopped",
                "timestamp", Instant.now()
            );
            return ResponseEntity.ok(response);
        }
        
        @Override
        public ResponseEntity<Map<String, Object>> startStream() {
            Map<String, Object> response = Map.of(
                "message", "Kafka streaming is disabled - start not available",
                "timestamp", Instant.now()
            );
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * NoOp implementation of StreamProcessingMetrics.
     */
    public static class NoOpStreamProcessingMetrics extends StreamProcessingMetrics {
        
        public NoOpStreamProcessingMetrics() {
            // Call parent constructor with null values - parent handles null gracefully
            super(null, null);
        }
        
        @Override
        public MetricsSummary getMetricsSummary() {
            return new MetricsSummary(
                0L, // eventsProcessed
                0L, // eventsFailed
                0L, // eventsRetried
                0L, // deserializationErrors
                0L, // sequenceValidationErrors
                0L, // actorTimeouts
                0L, // activeStreams
                0L, // bufferSize
                0L, // backpressureEvents
                0.0, // avgProcessingTimeMs
                0.0  // avgActorAskTimeMs
            );
        }
    }
}