package com.eventstreaming.controller;

import com.eventstreaming.cluster.PersistentUserActor;
import com.eventstreaming.model.UserEvent;
import com.eventstreaming.service.PersistentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for processing UserEvents through persistent actors.
 */
@RestController
@RequestMapping("/api/user-events")
public class UserEventController {
    
    @Autowired
    private PersistentUserService persistentUserService;
    
    /**
     * Process a single UserEvent through the persistent actor system.
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processUserEvent(@RequestBody UserEvent userEvent) {
        try {
            // Set defaults if not provided
            if (userEvent.getEventId() == null) {
                userEvent.setEventId(UUID.randomUUID().toString());
            }
            if (userEvent.getTimestamp() == null) {
                userEvent.setTimestamp(LocalDateTime.now());
            }
            if (userEvent.getSource() == null) {
                userEvent.setSource("UserEventController");
            }
            
            // Convert to EmailEvent (concrete CommunicationEvent) for processing
            com.eventstreaming.model.EventType eventType;
            try {
                eventType = com.eventstreaming.model.EventType.valueOf(userEvent.getEventType().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Default to EMAIL_DELIVERY if the event type is not recognized
                eventType = com.eventstreaming.model.EventType.EMAIL_DELIVERY;
            }
            
            com.eventstreaming.model.EmailEvent commEvent = 
                new com.eventstreaming.model.EmailEvent(
                    userEvent.getUserId(),
                    userEvent.getEventId(),
                    eventType,
                    userEvent.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant()
                );
            
            // Process through persistent actor
            CompletableFuture<PersistentUserActor.ProcessUserEventResponse> future = 
                persistentUserService.processEvent(commEvent).toCompletableFuture();
            
            PersistentUserActor.ProcessUserEventResponse response = future.get();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", response.success);
            result.put("message", response.message);
            result.put("userId", response.userId);
            result.put("eventId", response.eventId);
            result.put("processedAt", response.processedAt);
            result.put("originalEvent", userEvent);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to process event: " + e.getMessage());
            error.put("userId", userEvent.getUserId());
            error.put("eventId", userEvent.getEventId());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Process multiple UserEvents in batch.
     */
    @PostMapping("/process-batch")
    public ResponseEntity<Map<String, Object>> processUserEvents(@RequestBody List<UserEvent> userEvents) {
        try {
            // Convert to EmailEvents (concrete CommunicationEvents)
            List<com.eventstreaming.model.CommunicationEvent> commEvents = userEvents.stream()
                .map(userEvent -> {
                    // Set defaults if not provided
                    if (userEvent.getEventId() == null) {
                        userEvent.setEventId(UUID.randomUUID().toString());
                    }
                    if (userEvent.getTimestamp() == null) {
                        userEvent.setTimestamp(LocalDateTime.now());
                    }
                    if (userEvent.getSource() == null) {
                        userEvent.setSource("UserEventController");
                    }
                    
                    // Convert event type safely
                    com.eventstreaming.model.EventType eventType;
                    try {
                        eventType = com.eventstreaming.model.EventType.valueOf(userEvent.getEventType().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        // Default to EMAIL_DELIVERY if the event type is not recognized
                        eventType = com.eventstreaming.model.EventType.EMAIL_DELIVERY;
                    }
                    
                    return (com.eventstreaming.model.CommunicationEvent) new com.eventstreaming.model.EmailEvent(
                        userEvent.getUserId(),
                        userEvent.getEventId(),
                        eventType,
                        userEvent.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant()
                    );
                })
                .toList();
            
            // Process through persistent actors
            CompletableFuture<Void> future = persistentUserService.processEvents(commEvents).toCompletableFuture();
            future.get();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Processed " + userEvents.size() + " events successfully");
            result.put("eventCount", userEvents.size());
            result.put("processedAt", LocalDateTime.now());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to process batch: " + e.getMessage());
            error.put("eventCount", userEvents.size());
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Get user statistics from the persistent actor.
     */
    @GetMapping("/user/{userId}/stats")
    public ResponseEntity<Map<String, Object>> getUserStats(@PathVariable String userId) {
        try {
            CompletableFuture<PersistentUserActor.UserStatsResponse> future = 
                persistentUserService.getUserStats(userId).toCompletableFuture();
            
            PersistentUserActor.UserStatsResponse response = future.get();
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", response.userId);
            result.put("state", response.state);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get user stats: " + e.getMessage());
            error.put("userId", userId);
            
            return ResponseEntity.internalServerError().body(error);
        }
    }
}