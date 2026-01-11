package com.eventstreaming.migration;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a legacy communication event record from MySQL database
 */
public class LegacyEventRecord {
    private Long id;
    private String userId;
    private String eventType;
    private Instant timestamp;
    private String channel;
    private Map<String, Object> eventData;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public LegacyEventRecord() {}

    public LegacyEventRecord(Long id, String userId, String eventType, Instant timestamp, 
                           String channel, Map<String, Object> eventData, String status,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.channel = channel;
        this.eventData = eventData;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public Map<String, Object> getEventData() { return eventData; }
    public void setEventData(Map<String, Object> eventData) { this.eventData = eventData; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "LegacyEventRecord{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", timestamp=" + timestamp +
                ", channel='" + channel + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}