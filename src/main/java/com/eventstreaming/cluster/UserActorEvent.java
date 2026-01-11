package com.eventstreaming.cluster;

import com.eventstreaming.model.UserEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base class for all UserActor events that will be persisted to the event journal.
 * Implements Serializable for Pekko cluster communication.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserActorEvent.UserEventProcessed.class, name = "UserEventProcessed"),
    @JsonSubTypes.Type(value = UserActorEvent.UserEventCountUpdated.class, name = "UserEventCountUpdated")
})
public abstract class UserActorEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String userId;
    private final LocalDateTime timestamp;
    
    protected UserActorEvent(String userId, LocalDateTime timestamp) {
        this.userId = userId;
        this.timestamp = timestamp;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Event indicating that a UserEvent was processed by this UserActor.
     */
    public static class UserEventProcessed extends UserActorEvent {
        private final String eventType;
        private final Long contactId;
        private final String eventId;
        private final String source;
        
        @JsonCreator
        public UserEventProcessed(
                @JsonProperty("userId") String userId,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("contactId") Long contactId,
                @JsonProperty("eventId") String eventId,
                @JsonProperty("source") String source,
                @JsonProperty("timestamp") LocalDateTime timestamp) {
            super(userId, timestamp);
            this.eventType = eventType;
            this.contactId = contactId;
            this.eventId = eventId;
            this.source = source;
        }
        
        public static UserEventProcessed from(UserEvent userEvent) {
            return new UserEventProcessed(
                userEvent.getUserId(),
                userEvent.getEventType(),
                userEvent.getContactId(),
                userEvent.getEventId(),
                userEvent.getSource(),
                userEvent.getTimestamp() != null ? userEvent.getTimestamp() : LocalDateTime.now()
            );
        }
        
        public String getEventType() { return eventType; }
        public Long getContactId() { return contactId; }
        public String getEventId() { return eventId; }
        public String getSource() { return source; }
        
        @Override
        public String toString() {
            return "UserEventProcessed{" +
                   "userId='" + getUserId() + '\'' +
                   ", eventType='" + eventType + '\'' +
                   ", contactId=" + contactId +
                   ", eventId='" + eventId + '\'' +
                   ", source='" + source + '\'' +
                   ", timestamp=" + getTimestamp() +
                   '}';
        }
    }
    
    /**
     * Event indicating that the event count for a specific event type was updated.
     */
    public static class UserEventCountUpdated extends UserActorEvent {
        private final String eventType;
        private final Long newCount;
        private final Long previousCount;
        
        @JsonCreator
        public UserEventCountUpdated(
                @JsonProperty("userId") String userId,
                @JsonProperty("eventType") String eventType,
                @JsonProperty("newCount") Long newCount,
                @JsonProperty("previousCount") Long previousCount,
                @JsonProperty("timestamp") LocalDateTime timestamp) {
            super(userId, timestamp);
            this.eventType = eventType;
            this.newCount = newCount;
            this.previousCount = previousCount;
        }
        
        public String getEventType() { return eventType; }
        public Long getNewCount() { return newCount; }
        public Long getPreviousCount() { return previousCount; }
        
        @Override
        public String toString() {
            return "UserEventCountUpdated{" +
                   "userId='" + getUserId() + '\'' +
                   ", eventType='" + eventType + '\'' +
                   ", newCount=" + newCount +
                   ", previousCount=" + previousCount +
                   ", timestamp=" + getTimestamp() +
                   '}';
        }
    }
}