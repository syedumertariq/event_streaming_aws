package com.eventstreaming.controller;

import com.eventstreaming.model.UserEvent;
import com.eventstreaming.service.KafkaPekkoH2IntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for testing Kafka + Pekko + H2 integration.
 * Demonstrates the complete event-driven architecture pipeline.
 */
@RestController
@RequestMapping("/api/kafka-pekko-h2")
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaPekkoH2Controller {
    
    public KafkaPekkoH2Controller() {
        System.out.println("\n" + "⚡".repeat(100));
        System.out.println("⚡⚡⚡ KAFKAPEKKO-H2-CONTROLLER CONSTRUCTOR CALLED ⚡⚡⚡");
        System.out.println("⚡⚡⚡ THIS MEANS THE NEW JAR WITH CONDITIONAL ANNOTATIONS IS BEING USED ⚡⚡⚡");
        System.out.println("⚡⚡⚡ IF YOU SEE THIS, app.kafka.enabled=true OR CONDITIONAL FAILED ⚡⚡⚡");
        System.out.println("⚡".repeat(100) + "\n");
    }
    
    @Autowired
    private KafkaPekkoH2IntegrationService integrationService;
    
    /**
     * Process a single event through the Kafka-Pekko-H2 pipeline.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processStreamingEvent(@RequestBody UserEvent userEvent) {
        try {
            Map<String, Object> result = integrationService.processStreamingEvent(userEvent);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Controller error: " + e.getMessage());
            error.put("pipeline", "kafka-pekko-h2");
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Process multiple events in batch through the pipeline.
     */
    @PostMapping("/process-batch")
    public ResponseEntity<Map<String, Object>> processStreamingEventBatch(@RequestBody List<UserEvent> userEvents) {
        try {
            Map<String, Object> result = integrationService.processStreamingEventBatch(userEvents);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.accepted().body(result); // Partial success
            }
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Batch controller error: " + e.getMessage());
            error.put("pipeline", "kafka-pekko-h2-batch");
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get streaming statistics from the H2 event journal.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStreamingStatistics() {
        try {
            Map<String, Object> stats = integrationService.getStreamingStatistics();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get streaming statistics: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Health check for the integration pipeline.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test the pipeline with a dummy event
            UserEvent testEvent = new UserEvent();
            testEvent.setUserId("health-check-user");
            testEvent.setEventType("HEALTH_CHECK");
            testEvent.setContactId(9999L);
            testEvent.setSource("health-check");
            testEvent.setEventId(UUID.randomUUID().toString());
            testEvent.setTimestamp(LocalDateTime.now());
            
            Map<String, Object> testResult = integrationService.processStreamingEvent(testEvent);
            
            health.put("status", "UP");
            health.put("pipeline", "kafka-pekko-h2");
            health.put("components", Map.of(
                "pekko-actors", "UP",
                "h2-journal", "UP",
                "integration-service", "UP"
            ));
            health.put("testEvent", Map.of(
                "processed", testResult.get("success"),
                "eventId", testEvent.getEventId()
            ));
            health.put("checkedAt", LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("pipeline", "kafka-pekko-h2");
            health.put("error", e.getMessage());
            health.put("checkedAt", LocalDateTime.now());
            
            return ResponseEntity.status(503).body(health);
        }
    }
    
    /**
     * Generate test events for demonstration.
     */
    @PostMapping("/generate-test-events/{count}")
    public ResponseEntity<Map<String, Object>> generateTestEvents(@PathVariable int count) {
        try {
            if (count > 100) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Maximum 100 test events allowed");
                return ResponseEntity.badRequest().body(error);
            }
            
            List<UserEvent> testEvents = new java.util.ArrayList<>();
            
            for (int i = 1; i <= count; i++) {
                UserEvent event = new UserEvent();
                event.setUserId("test-user-" + (i % 5 + 1)); // 5 different users
                event.setEventType(getRandomEventType(i));
                event.setContactId(1000L + i);
                event.setSource("test-generator");
                event.setEventId(UUID.randomUUID().toString());
                event.setTimestamp(LocalDateTime.now().plusSeconds(i));
                
                testEvents.add(event);
            }
            
            Map<String, Object> result = integrationService.processStreamingEventBatch(testEvents);
            result.put("generatedEvents", count);
            result.put("testUsers", 5);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate test events: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Send event to Kafka topics.
     */
    @PostMapping("/send-to-kafka")
    public ResponseEntity<Map<String, Object>> sendEventToKafka(
            @RequestBody UserEvent userEvent,
            @RequestParam(defaultValue = "QuestIncrementerDropperINSCallQueueDev") String topic) {
        try {
            Map<String, Object> result = integrationService.sendEventToKafka(userEvent, topic);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Kafka send error: " + e.getMessage());
            error.put("topic", topic);
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Process event through pipeline AND send to Kafka topics.
     */
    @PostMapping("/process-with-kafka")
    public ResponseEntity<Map<String, Object>> processEventWithKafka(
            @RequestBody UserEvent userEvent,
            @RequestParam(defaultValue = "QuestIncrementerDropperINSCallQueueDev,QuestIncrementerDropperINSDigitalQueueDev") String topics) {
        try {
            List<String> topicList = List.of(topics.split(","));
            Map<String, Object> result = integrationService.processEventWithKafka(userEvent, topicList);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Pipeline + Kafka error: " + e.getMessage());
            error.put("topics", topics);
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get Kafka streaming status.
     */
    @GetMapping("/kafka-status")
    public ResponseEntity<Map<String, Object>> getKafkaStreamingStatus() {
        try {
            Map<String, Object> status = integrationService.getKafkaStreamingStatus();
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get Kafka status: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Test Kafka integration.
     */
    @PostMapping("/test-kafka")
    public ResponseEntity<Map<String, Object>> testKafkaIntegration() {
        try {
            Map<String, Object> result = integrationService.testKafkaIntegration();
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.internalServerError().body(result);
            }
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Kafka test error: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get random event type for test generation.
     */
    private String getRandomEventType(int index) {
        String[] eventTypes = {"LOGIN", "VIEW_PRODUCT", "ADD_TO_CART", "PURCHASE", "LOGOUT"};
        return eventTypes[index % eventTypes.length];
    }
}