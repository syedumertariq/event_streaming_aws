package com.eventstreaming.controller;

import com.eventstreaming.service.EventJournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for querying the event journal to see persisted events.
 */
@RestController
@RequestMapping("/api/event-journal")
public class EventJournalController {
    
    @Autowired
    private EventJournalService eventJournalService;
    
    /**
     * Get all events for a specific user from the event journal.
     */
    @GetMapping("/user/{userId}/events")
    public ResponseEntity<Map<String, Object>> getUserEvents(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") long fromSequenceNr,
            @RequestParam(defaultValue = "100") int maxEvents) {
        
        try {
            Map<String, Object> result = eventJournalService.getUserEvents(userId, fromSequenceNr, maxEvents);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve events: " + e.getMessage()));
        }
    }
    
    /**
     * Get current state for a specific user.
     */
    @GetMapping("/user/{userId}/state")
    public ResponseEntity<Map<String, Object>> getUserState(@PathVariable String userId) {
        try {
            Map<String, Object> result = eventJournalService.getUserState(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve user state: " + e.getMessage()));
        }
    }
    
    /**
     * Get all persistence IDs (users) that have events.
     */
    @GetMapping("/persistence-ids")
    public ResponseEntity<Map<String, Object>> getAllPersistenceIds() {
        try {
            List<String> persistenceIds = eventJournalService.getAllPersistenceIds();
            return ResponseEntity.ok(Map.of(
                "persistenceIds", persistenceIds,
                "count", persistenceIds.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve persistence IDs: " + e.getMessage()));
        }
    }
    
    /**
     * Get journal statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getJournalStats() {
        try {
            Map<String, Object> stats = eventJournalService.getJournalStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve journal stats: " + e.getMessage()));
        }
    }
}