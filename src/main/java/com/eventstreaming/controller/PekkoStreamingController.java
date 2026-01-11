package com.eventstreaming.controller;

import com.eventstreaming.kafka.PekkoKafkaStreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for managing Pekko Kafka streaming service.
 */
@RestController
@RequestMapping("/api/pekko-streaming")
@ConditionalOnBean(PekkoKafkaStreamingService.class)
@Profile({"default", "kafka", "streaming"})
public class PekkoStreamingController {
    
    private final PekkoKafkaStreamingService pekkoStreamingService;
    
    @Autowired
    public PekkoStreamingController(PekkoKafkaStreamingService pekkoStreamingService) {
        this.pekkoStreamingService = pekkoStreamingService;
    }
    
    /**
     * Gets the current status of the Pekko streaming service.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        
        boolean isRunning = pekkoStreamingService.isRunning();
        
        response.put("status", isRunning ? "RUNNING" : "STOPPED");
        response.put("isRunning", isRunning);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Starts the Pekko streaming service.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startStreaming() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            pekkoStreamingService.startStreaming();
            
            response.put("status", "SUCCESS");
            response.put("message", "Pekko streaming started successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("message", "Failed to start Pekko streaming: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Stops the Pekko streaming service.
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopStreaming() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            pekkoStreamingService.stopStreaming().toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
            
            response.put("status", "SUCCESS");
            response.put("message", "Pekko streaming stopped successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("message", "Failed to stop Pekko streaming: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }
}