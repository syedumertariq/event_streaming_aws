package com.eventstreaming.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents aggregated user event statistics.
 */
public class UserEventAggregation {
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("eventTypeCounts")
    private Map<String, Long> eventTypeCounts = new HashMap<>();
    
    @JsonProperty("totalEvents")
    private Long totalEvents = 0L;
    
    @JsonProperty("lastUpdated")
    private LocalDateTime lastUpdated;
    
    @JsonProperty("firstEventTime")
    private LocalDateTime firstEventTime;
    
    @JsonProperty("lastEventTime")
    private LocalDateTime lastEventTime;
    
    // Default constructor
    public UserEventAggregation() {
        this.lastUpdated = LocalDateTime.now();
    }
    
    // Constructor with userId
    public UserEventAggregation(String userId) {
        this();
        this.userId = userId;
    }
    
    /**
     * Adds an event to the aggregation.
     */
    public void addEvent(UserEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        
        String eventType = event.getEventType();
        eventTypeCounts.merge(eventType, 1L, Long::sum);
        totalEvents++;
        
        LocalDateTime eventTime = event.getTimestamp();
        if (eventTime != null) {
            if (firstEventTime == null || eventTime.isBefore(firstEventTime)) {
                firstEventTime = eventTime;
            }
            if (lastEventTime == null || eventTime.isAfter(lastEventTime)) {
                lastEventTime = eventTime;
            }
        }
        
        lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Gets the count for a specific event type.
     */
    public Long getCountForEventType(String eventType) {
        return eventTypeCounts.getOrDefault(eventType, 0L);
    }
    
    /**
     * Merges another aggregation into this one.
     */
    public void merge(UserEventAggregation other) {
        if (other == null) {
            return;
        }
        
        // Merge event type counts
        for (Map.Entry<String, Long> entry : other.getEventTypeCounts().entrySet()) {
            eventTypeCounts.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        
        // Update total events
        totalEvents = eventTypeCounts.values().stream().mapToLong(Long::longValue).sum();
        
        // Update timestamps
        if (other.getFirstEventTime() != null) {
            if (firstEventTime == null || other.getFirstEventTime().isBefore(firstEventTime)) {
                firstEventTime = other.getFirstEventTime();
            }
        }
        
        if (other.getLastEventTime() != null) {
            if (lastEventTime == null || other.getLastEventTime().isAfter(lastEventTime)) {
                lastEventTime = other.getLastEventTime();
            }
        }
        
        // Update last updated to most recent
        if (other.getLastUpdated() != null) {
            if (lastUpdated == null || other.getLastUpdated().isAfter(lastUpdated)) {
                lastUpdated = other.getLastUpdated();
            }
        }
    }
    
    /**
     * Checks if this aggregation has any events.
     */
    public boolean hasEvents() {
        return totalEvents > 0;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public Map<String, Long> getEventTypeCounts() {
        return eventTypeCounts;
    }
    
    public void setEventTypeCounts(Map<String, Long> eventTypeCounts) {
        this.eventTypeCounts = eventTypeCounts != null ? eventTypeCounts : new HashMap<>();
        // Recalculate total
        this.totalEvents = this.eventTypeCounts.values().stream().mapToLong(Long::longValue).sum();
    }
    
    public Long getTotalEvents() {
        return totalEvents;
    }
    
    public void setTotalEvents(Long totalEvents) {
        this.totalEvents = totalEvents != null ? totalEvents : 0L;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public LocalDateTime getFirstEventTime() {
        return firstEventTime;
    }
    
    public void setFirstEventTime(LocalDateTime firstEventTime) {
        this.firstEventTime = firstEventTime;
    }
    
    public LocalDateTime getLastEventTime() {
        return lastEventTime;
    }
    
    public void setLastEventTime(LocalDateTime lastEventTime) {
        this.lastEventTime = lastEventTime;
    }
    
    @Override
    public String toString() {
        return "UserEventAggregation{" +
               "userId='" + userId + '\'' +
               ", eventTypeCounts=" + eventTypeCounts +
               ", totalEvents=" + totalEvents +
               ", lastUpdated=" + lastUpdated +
               '}';
    }
}