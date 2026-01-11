package com.eventstreaming.controller;

import com.eventstreaming.model.UserEventAggregation;
import com.eventstreaming.service.UserEventAggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for user event aggregations.
 */
@RestController
@RequestMapping("/api/aggregations")
public class UserEventAggregationController {
    
    private final UserEventAggregationService aggregationService;
    
    @Autowired
    public UserEventAggregationController(UserEventAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }
    
    /**
     * Gets aggregation for a specific user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<UserEventAggregation> getUserAggregation(@PathVariable String userId) {
        UserEventAggregation aggregation = aggregationService.getUserAggregation(userId);
        
        if (aggregation == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(aggregation);
    }
    
    /**
     * Gets all user aggregations.
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, UserEventAggregation>> getAllAggregations() {
        Map<String, UserEventAggregation> aggregations = aggregationService.getAllAggregations();
        return ResponseEntity.ok(aggregations);
    }
    
    /**
     * Gets summary statistics.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("totalUsers", aggregationService.getTotalUsers());
        summary.put("totalEvents", aggregationService.getTotalEventsAcrossAllUsers());
        summary.put("timestamp", System.currentTimeMillis());
        
        // Get event type distribution across all users
        Map<String, Long> globalEventTypeCounts = new HashMap<>();
        aggregationService.getAllAggregations().values().forEach(aggregation -> {
            aggregation.getEventTypeCounts().forEach((eventType, count) -> {
                globalEventTypeCounts.merge(eventType, count, Long::sum);
            });
        });
        summary.put("globalEventTypeCounts", globalEventTypeCounts);
        
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Gets recent events across all users (for dashboard).
     */
    @GetMapping("/recent-events")
    public ResponseEntity<java.util.List<Map<String, Object>>> getRecentEvents(
            @RequestParam(defaultValue = "10") int limit) {
        
        java.util.List<Map<String, Object>> recentEvents = new java.util.ArrayList<>();
        
        // Get all aggregations and create individual event entries for each event type
        for (UserEventAggregation aggregation : aggregationService.getAllAggregations().values()) {
            // Create an event entry for each event type the user has
            for (Map.Entry<String, Long> eventTypeEntry : aggregation.getEventTypeCounts().entrySet()) {
                Map<String, Object> eventInfo = new HashMap<>();
                eventInfo.put("userId", aggregation.getUserId());
                eventInfo.put("eventType", eventTypeEntry.getKey()); // Add the missing eventType field
                eventInfo.put("eventCount", eventTypeEntry.getValue());
                eventInfo.put("timestamp", aggregation.getLastUpdated());
                eventInfo.put("lastUpdated", aggregation.getLastUpdated());
                recentEvents.add(eventInfo);
            }
        }
        
        // Sort by last updated (most recent first)
        recentEvents.sort((a, b) -> {
            LocalDateTime timeA = (LocalDateTime) a.get("lastUpdated");
            LocalDateTime timeB = (LocalDateTime) b.get("lastUpdated");
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });
        
        // Limit results
        if (recentEvents.size() > limit) {
            recentEvents = recentEvents.subList(0, limit);
        }
        
        return ResponseEntity.ok(recentEvents);
    }
    
    /**
     * Gets top users by event count (for dashboard).
     */
    @GetMapping("/top-users")
    public ResponseEntity<java.util.List<Map<String, Object>>> getTopUsers(
            @RequestParam(defaultValue = "10") int limit) {
        
        java.util.List<Map<String, Object>> topUsers = new java.util.ArrayList<>();
        
        // Get all aggregations and create user entries
        for (UserEventAggregation aggregation : aggregationService.getAllAggregations().values()) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", aggregation.getUserId());
            userInfo.put("totalEvents", aggregation.getTotalEvents());
            userInfo.put("eventTypeCounts", aggregation.getEventTypeCounts());
            userInfo.put("lastUpdated", aggregation.getLastUpdated());
            topUsers.add(userInfo);
        }
        
        // Sort by total events (highest first)
        topUsers.sort((a, b) -> {
            Long eventsA = (Long) a.get("totalEvents");
            Long eventsB = (Long) b.get("totalEvents");
            if (eventsA == null && eventsB == null) return 0;
            if (eventsA == null) return 1;
            if (eventsB == null) return -1;
            return eventsB.compareTo(eventsA);
        });
        
        // Limit results
        if (topUsers.size() > limit) {
            topUsers = topUsers.subList(0, limit);
        }
        
        return ResponseEntity.ok(topUsers);
    }
    
    /**
     * Clears all aggregations (for testing).
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearAggregations() {
        aggregationService.clearAllAggregations();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "All aggregations cleared");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}