package com.eventstreaming.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a user event in the event streaming system.
 * This model is used to capture events from various sources including Kafka messages.
 * Implements Serializable for Pekko cluster communication.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("eventType")
    private String eventType;
    
    @JsonProperty("contactId")
    private Long contactId;
    
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @JsonProperty("eventData")
    private String eventData;
    
    @JsonProperty("source")
    private String source;
    
    @JsonProperty("partition")
    private Integer partition;
    
    @JsonProperty("offset")
    private Long offset;
    
    @JsonProperty("eventId")
    private String eventId;
    
    // Default constructor
    public UserEvent() {
        this.timestamp = LocalDateTime.now();
    }
    
    // Constructor with basic fields
    public UserEvent(String userId, String eventType, Long contactId) {
        this();
        this.userId = userId;
        this.eventType = eventType;
        this.contactId = contactId;
    }
    
    // Constructor with all fields
    public UserEvent(String userId, String eventType, Long contactId, 
                    LocalDateTime timestamp, String eventData, String source) {
        this.userId = userId;
        this.eventType = eventType;
        this.contactId = contactId;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.eventData = eventData;
        this.source = source;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public Long getContactId() {
        return contactId;
    }
    
    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getEventData() {
        return eventData;
    }
    
    public void setEventData(String eventData) {
        this.eventData = eventData;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public Integer getPartition() {
        return partition;
    }
    
    public void setPartition(Integer partition) {
        this.partition = partition;
    }
    
    public Long getOffset() {
        return offset;
    }
    
    public void setOffset(Long offset) {
        this.offset = offset;
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    // Utility methods
    public boolean isValid() {
        return userId != null && !userId.trim().isEmpty() &&
               eventType != null && !eventType.trim().isEmpty() &&
               contactId != null && contactId > 0;
    }
    
    public String getEventKey() {
        return userId + ":" + eventType + ":" + contactId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserEvent userEvent = (UserEvent) o;
        return Objects.equals(userId, userEvent.userId) &&
               Objects.equals(eventType, userEvent.eventType) &&
               Objects.equals(contactId, userEvent.contactId) &&
               Objects.equals(timestamp, userEvent.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, eventType, contactId, timestamp);
    }
    
    @Override
    public String toString() {
        return "UserEvent{" +
               "userId='" + userId + '\'' +
               ", eventType='" + eventType + '\'' +
               ", contactId=" + contactId +
               ", timestamp=" + timestamp +
               ", source='" + source + '\'' +
               ", partition=" + partition +
               ", offset=" + offset +
               '}';
    }
}