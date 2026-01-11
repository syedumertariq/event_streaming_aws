package com.eventstreaming.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * State of a UserActor that tracks event counts by event type.
 * This state is built from persisted events and can be snapshotted.
 * Implements Serializable for Pekko cluster communication.
 */
public class UserActorState implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String userId;
    private final Map<String, Long> eventTypeCounts;
    private final Long totalEvents;
    private final LocalDateTime lastUpdated;
    private final LocalDateTime firstEventTime;
    private final LocalDateTime lastEventTime;
    
    // Default constructor for empty state
    public UserActorState(String userId) {
        this(userId, new HashMap<>(), 0L, LocalDateTime.now(), null, null);
    }
    
    @JsonCreator
    public UserActorState(
            @JsonProperty("userId") String userId,
            @JsonProperty("eventTypeCounts") Map<String, Long> eventTypeCounts,
            @JsonProperty("totalEvents") Long totalEvents,
            @JsonProperty("lastUpdated") LocalDateTime lastUpdated,
            @JsonProperty("firstEventTime") LocalDateTime firstEventTime,
            @JsonProperty("lastEventTime") LocalDateTime lastEventTime) {
        this.userId = userId;
        this.eventTypeCounts = eventTypeCounts != null ? new HashMap<>(eventTypeCounts) : new HashMap<>();
        this.totalEvents = totalEvents != null ? totalEvents : 0L;
        this.lastUpdated = lastUpdated != null ? lastUpdated : LocalDateTime.now();
        this.firstEventTime = firstEventTime;
        this.lastEventTime = lastEventTime;
    }
    
    /**
     * Applies a UserEventProcessed event to create a new state.
     */
    public UserActorState applyEvent(UserActorEvent.UserEventProcessed event) {
        Map<String, Long> newCounts = new HashMap<>(eventTypeCounts);
        newCounts.merge(event.getEventType(), 1L, Long::sum);
        
        LocalDateTime eventTime = event.getTimestamp();
        LocalDateTime newFirstEventTime = firstEventTime;
        LocalDateTime newLastEventTime = lastEventTime;
        
        if (eventTime != null) {
            if (newFirstEventTime == null || eventTime.isBefore(newFirstEventTime)) {
                newFirstEventTime = eventTime;
            }
            if (newLastEventTime == null || eventTime.isAfter(newLastEventTime)) {
                newLastEventTime = eventTime;
            }
        }
        
        return new UserActorState(
            userId,
            newCounts,
            totalEvents + 1,
            LocalDateTime.now(),
            newFirstEventTime,
            newLastEventTime
        );
    }
    
    /**
     * Gets the count for a specific event type.
     */
    public Long getCountForEventType(String eventType) {
        return eventTypeCounts.getOrDefault(eventType, 0L);
    }
    
    /**
     * Checks if this state has any events.
     */
    public boolean hasEvents() {
        return totalEvents > 0;
    }
    
    // Getters
    public String getUserId() { return userId; }
    public Map<String, Long> getEventTypeCounts() { return new HashMap<>(eventTypeCounts); }
    public Long getTotalEvents() { return totalEvents; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public LocalDateTime getFirstEventTime() { return firstEventTime; }
    public LocalDateTime getLastEventTime() { return lastEventTime; }
    
    @Override
    public String toString() {
        return "UserActorState{" +
               "userId='" + userId + '\'' +
               ", eventTypeCounts=" + eventTypeCounts +
               ", totalEvents=" + totalEvents +
               ", lastUpdated=" + lastUpdated +
               '}';
    }
}