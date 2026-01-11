package com.eventstreaming.service;

import com.eventstreaming.model.UserEvent;
import com.eventstreaming.model.UserEventAggregation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for aggregating user events by event type.
 * This service maintains in-memory aggregations and loads historical data from MySQL.
 */
@Service
public class UserEventAggregationService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserEventAggregationService.class);
    
    // In-memory storage for current session aggregations
    private final Map<String, UserEventAggregation> sessionAggregations = new ConcurrentHashMap<>();
    
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Processes a user event and updates aggregations.
     */
    public void processEvent(UserEvent event) {
        if (event == null || event.getUserId() == null || event.getEventType() == null) {
            logger.warn("Invalid event received: {}", event);
            return;
        }
        
        String userId = event.getUserId();
        
        logger.info("Processing event for aggregation: userId={}, eventType={}, contactId={}", 
                   userId, event.getEventType(), event.getContactId());
        
        // Get or create aggregation for user (session only)
        UserEventAggregation aggregation = sessionAggregations.computeIfAbsent(userId, UserEventAggregation::new);
        
        // Add event to aggregation
        aggregation.addEvent(event);
        
        logger.info("Updated session aggregation for user {}: totalEvents={}, eventTypes={}", 
                   userId, aggregation.getTotalEvents(), aggregation.getEventTypeCounts());
    }
    
    /**
     * Gets aggregation for a specific user (includes historical data).
     */
    public UserEventAggregation getUserAggregation(String userId) {
        // Get session aggregation
        UserEventAggregation sessionAgg = sessionAggregations.get(userId);
        
        // Get historical aggregation
        UserEventAggregation historicalAgg = getHistoricalAggregation(userId);
        
        // Merge them
        if (sessionAgg != null && historicalAgg != null) {
            UserEventAggregation combined = new UserEventAggregation(userId);
            combined.merge(historicalAgg);
            combined.merge(sessionAgg);
            return combined;
        } else if (sessionAgg != null) {
            return sessionAgg;
        } else {
            return historicalAgg;
        }
    }
    
    /**
     * Gets all user aggregations (includes historical data).
     */
    public Map<String, UserEventAggregation> getAllAggregations() {
        Map<String, UserEventAggregation> allAggregations = new HashMap<>();
        
        // Load historical aggregations from MySQL
        Map<String, UserEventAggregation> historical = loadHistoricalAggregations();
        allAggregations.putAll(historical);
        
        // Merge with current session aggregations
        for (Map.Entry<String, UserEventAggregation> entry : sessionAggregations.entrySet()) {
            String userId = entry.getKey();
            UserEventAggregation sessionAgg = entry.getValue();
            
            UserEventAggregation combined = allAggregations.computeIfAbsent(userId, UserEventAggregation::new);
            combined.merge(sessionAgg);
        }
        
        logger.debug("Returning {} total aggregations ({} historical, {} session)", 
                    allAggregations.size(), historical.size(), sessionAggregations.size());
        
        return allAggregations;
    }
    
    /**
     * Loads historical aggregations from MySQL event journal.
     */
    private Map<String, UserEventAggregation> loadHistoricalAggregations() {
        Map<String, UserEventAggregation> historical = new HashMap<>();
        
        if (jdbcTemplate == null) {
            logger.debug("JdbcTemplate not available, skipping historical data");
            return historical;
        }
        
        try {
            String sql = "SELECT " +
                "SUBSTRING_INDEX(persistence_id, '|', -1) as user_id, " +
                "event_ser_manifest as event_type, " +
                "COUNT(*) as event_count, " +
                "MAX(write_timestamp) as last_updated " +
                "FROM event_journal " +
                "WHERE persistence_id LIKE 'PersistentUserActor|%' " +
                "GROUP BY user_id, event_ser_manifest " +
                "ORDER BY user_id, event_ser_manifest";
            
            jdbcTemplate.query(sql, (rs) -> {
                String userId = rs.getString("user_id");
                String eventType = rs.getString("event_type");
                long eventCount = rs.getLong("event_count");
                long lastUpdatedMillis = rs.getLong("last_updated");
                
                UserEventAggregation agg = historical.computeIfAbsent(userId, UserEventAggregation::new);
                
                // Add event type count to aggregation
                for (int i = 0; i < eventCount; i++) {
                    UserEvent dummyEvent = new UserEvent();
                    dummyEvent.setUserId(userId);
                    dummyEvent.setEventType(eventType);
                    agg.addEvent(dummyEvent);
                }
                
                if (lastUpdatedMillis > 0) {
                    agg.setLastUpdated(java.time.Instant.ofEpochMilli(lastUpdatedMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                }
            });
            
            logger.info("Loaded {} historical user aggregations from MySQL", historical.size());
            
        } catch (Exception e) {
            logger.warn("Failed to load historical aggregations from MySQL: {}", e.getMessage());
        }
        
        return historical;
    }
    
    /**
     * Gets historical aggregation for a specific user.
     */
    private UserEventAggregation getHistoricalAggregation(String userId) {
        if (jdbcTemplate == null) {
            return null;
        }
        
        try {
            String sql = "SELECT " +
                "event_ser_manifest as event_type, " +
                "COUNT(*) as event_count, " +
                "MAX(write_timestamp) as last_updated " +
                "FROM event_journal " +
                "WHERE persistence_id = ? " +
                "GROUP BY event_ser_manifest";
            
            UserEventAggregation agg = new UserEventAggregation(userId);
            
            jdbcTemplate.query(sql, new Object[]{"PersistentUserActor|" + userId}, (rs) -> {
                String eventType = rs.getString("event_type");
                long eventCount = rs.getLong("event_count");
                long lastUpdatedMillis = rs.getLong("last_updated");
                
                // Add event type count to aggregation
                for (int i = 0; i < eventCount; i++) {
                    UserEvent dummyEvent = new UserEvent();
                    dummyEvent.setUserId(userId);
                    dummyEvent.setEventType(eventType);
                    agg.addEvent(dummyEvent);
                }
                
                if (lastUpdatedMillis > 0) {
                    agg.setLastUpdated(java.time.Instant.ofEpochMilli(lastUpdatedMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                }
            });
            
            return agg.getTotalEvents() > 0 ? agg : null;
            
        } catch (Exception e) {
            logger.warn("Failed to load historical aggregation for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Clears session aggregations only (keeps historical data).
     */
    public void clearAllAggregations() {
        logger.info("Clearing session user event aggregations (historical data preserved)");
        sessionAggregations.clear();
    }
    
    /**
     * Gets the total number of users being tracked (includes historical).
     */
    public int getTotalUsers() {
        return getAllAggregations().size();
    }
    
    /**
     * Gets the total number of events across all users (includes historical).
     */
    public long getTotalEventsAcrossAllUsers() {
        return getAllAggregations().values().stream()
                .mapToLong(UserEventAggregation::getTotalEvents)
                .sum();
    }
}