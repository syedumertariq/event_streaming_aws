package com.eventstreaming.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.Map;

/**
 * Base class for all communication events
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmailEvent.class, name = "EMAIL"),
    @JsonSubTypes.Type(value = SmsEvent.class, name = "SMS"),
    @JsonSubTypes.Type(value = CallEvent.class, name = "CALL")
})
public abstract class CommunicationEvent {
    
    private String userId;
    private String eventId;
    private Instant timestamp;
    private EventType eventType;
    private String source;
    private Map<String, Object> metadata;
    
    protected CommunicationEvent() {}
    
    protected CommunicationEvent(String userId, String eventId, EventType eventType, 
                               Instant timestamp, String source, Map<String, Object> metadata) {
        this.userId = userId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.source = source;
        this.metadata = metadata;
    }
    
    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    // Validation
    public boolean isValid() {
        return userId != null && !userId.trim().isEmpty() &&
               eventId != null && !eventId.trim().isEmpty() &&
               timestamp != null &&
               eventType != null;
    }
    
    @Override
    public String toString() {
        return String.format("%s{userId='%s', eventId='%s', eventType=%s, timestamp=%s}",
                           getClass().getSimpleName(), userId, eventId, eventType, timestamp);
    }
}