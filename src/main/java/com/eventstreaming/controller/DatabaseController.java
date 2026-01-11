package com.eventstreaming.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for database queries and inspection.
 */
@RestController
@RequestMapping("/api/database")
public class DatabaseController {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Lists all tables in the database.
     */
    @GetMapping("/tables")
    public ResponseEntity<Map<String, Object>> listTables() {
        try {
            List<Map<String, Object>> tables = jdbcTemplate.queryForList("SHOW TABLES");
            
            Map<String, Object> response = new HashMap<>();
            response.put("tables", tables);
            response.put("count", tables.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to list tables: " + e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Gets recent events from the Pekko journal table.
     */
    @GetMapping("/journal")
    public ResponseEntity<Map<String, Object>> getJournalEvents(@RequestParam(defaultValue = "10") int limit) {
        try {
            // Check if journal table exists
            List<Map<String, Object>> tableCheck = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'JOURNAL'"
            );
            
            if (tableCheck.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "JOURNAL table does not exist yet. No events have been persisted.");
                response.put("availableTables", jdbcTemplate.queryForList("SHOW TABLES"));
                response.put("timestamp", System.currentTimeMillis());
                return ResponseEntity.ok(response);
            }
            
            // Get recent journal events
            List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT PERSISTENCE_ID, SEQUENCE_NUMBER, TIMESTAMP, TAGS " +
                "FROM JOURNAL ORDER BY TIMESTAMP DESC LIMIT ?", limit
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("events", events);
            response.put("count", events.size());
            response.put("limit", limit);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to query journal: " + e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Gets events for a specific persistence ID (e.g., UserActor-user123).
     */
    @GetMapping("/journal/{persistenceId}")
    public ResponseEntity<Map<String, Object>> getEventsForPersistenceId(
            @PathVariable String persistenceId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT PERSISTENCE_ID, SEQUENCE_NUMBER, TIMESTAMP, TAGS " +
                "FROM JOURNAL WHERE PERSISTENCE_ID = ? ORDER BY SEQUENCE_NUMBER DESC LIMIT ?", 
                persistenceId, limit
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("persistenceId", persistenceId);
            response.put("events", events);
            response.put("count", events.size());
            response.put("limit", limit);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to query events for " + persistenceId + ": " + e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Gets summary of events by persistence ID.
     */
    @GetMapping("/journal/summary")
    public ResponseEntity<Map<String, Object>> getJournalSummary() {
        try {
            // Check if journal table exists
            List<Map<String, Object>> tableCheck = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'JOURNAL'"
            );
            
            if (tableCheck.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "JOURNAL table does not exist yet. No events have been persisted.");
                response.put("timestamp", System.currentTimeMillis());
                return ResponseEntity.ok(response);
            }
            
            // Get event counts by persistence ID
            List<Map<String, Object>> summary = jdbcTemplate.queryForList(
                "SELECT PERSISTENCE_ID, COUNT(*) as EVENT_COUNT, " +
                "MIN(TIMESTAMP) as FIRST_EVENT, MAX(TIMESTAMP) as LAST_EVENT " +
                "FROM JOURNAL GROUP BY PERSISTENCE_ID ORDER BY EVENT_COUNT DESC"
            );
            
            // Get total event count
            Integer totalEvents = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM JOURNAL", Integer.class);
            
            Map<String, Object> response = new HashMap<>();
            response.put("summary", summary);
            response.put("totalEvents", totalEvents);
            response.put("totalPersistenceIds", summary.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get journal summary: " + e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }
}