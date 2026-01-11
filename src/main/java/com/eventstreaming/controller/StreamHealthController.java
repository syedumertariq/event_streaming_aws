package com.eventstreaming.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.eventstreaming.monitoring.StreamProcessingMetrics;
import com.eventstreaming.streams.EventStreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for monitoring and managing the event processing stream health.
 */
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/api/stream")
public class StreamHealthController {
    
    private final EventStreamingService eventStreamingService;
    private final StreamProcessingMetrics metrics;
    
    @Autowired
    public StreamHealthController(EventStreamingService eventStreamingService,
                                StreamProcessingMetrics metrics) {
        this.eventStreamingService = eventStreamingService;
        this.metrics = metrics;
    }
    
    /**
     * Gets the current health status of the event processing pipeline.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getStreamHealth() {
        EventStreamingService.StreamHealthInfo healthInfo = eventStreamingService.getHealthInfo();
        
        Map<String, Object> response = Map.of(
            "status", healthInfo.isHealthy() ? "UP" : "DOWN",
            "isRunning", healthInfo.isRunning(),
            "isShuttingDown", healthInfo.isShuttingDown(),
            "hasStreamControl", healthInfo.hasStreamControl(),
            "streamIsActive", healthInfo.streamIsActive(),
            "timestamp", java.time.Instant.now()
        );
        
        return healthInfo.isHealthy() 
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(503).body(response);
    }
    
    /**
     * Restarts the event processing pipeline.
     */
    @PostMapping("/restart")
    public ResponseEntity<Map<String, Object>> restartStream() {
        try {
            eventStreamingService.restartEventProcessing();
            
            Map<String, Object> response = Map.of(
                "message", "Stream restart initiated",
                "timestamp", java.time.Instant.now()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                "error", "Failed to restart stream",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now()
            );
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Stops the event processing pipeline.
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopStream() {
        try {
            eventStreamingService.stopEventProcessing();
            
            Map<String, Object> response = Map.of(
                "message", "Stream stop initiated",
                "timestamp", java.time.Instant.now()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                "error", "Failed to stop stream",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now()
            );
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Starts the event processing pipeline.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startStream() {
        try {
            eventStreamingService.startEventProcessing();
            
            Map<String, Object> response = Map.of(
                "message", "Stream start initiated",
                "timestamp", java.time.Instant.now()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = Map.of(
                "error", "Failed to start stream",
                "message", e.getMessage(),
                "timestamp", java.time.Instant.now()
            );
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Gets detailed metrics about stream processing performance.
     */
    @GetMapping("/metrics")
    public ResponseEntity<StreamProcessingMetrics.MetricsSummary> getStreamMetrics() {
        StreamProcessingMetrics.MetricsSummary metricsSummary = metrics.getMetricsSummary();
        return ResponseEntity.ok(metricsSummary);
    }
}