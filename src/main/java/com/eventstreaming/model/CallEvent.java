package com.eventstreaming.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Call communication event
 */
public class CallEvent extends CommunicationEvent {
    
    private String phoneNumber;
    private Duration callDuration;
    private String callDirection; // INBOUND, OUTBOUND
    private String callResult; // COMPLETED, MISSED, BUSY, NO_ANSWER
    
    public CallEvent() {
        super();
    }
    
    @JsonCreator
    public CallEvent(
            @JsonProperty("userId") String userId,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") EventType eventType,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("callDuration") Duration callDuration,
            @JsonProperty("callDirection") String callDirection,
            @JsonProperty("callResult") String callResult,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        
        super(userId, eventId, eventType, timestamp, "call-center", metadata);
        this.phoneNumber = phoneNumber;
        this.callDuration = callDuration;
        this.callDirection = callDirection;
        this.callResult = callResult;
    }
    
    // Convenience constructor for simple events
    public CallEvent(String userId, String eventId, EventType eventType, Instant timestamp) {
        this(userId, eventId, eventType, timestamp, null, null, null, null, Map.of());
    }
    
    // Getters and setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public Duration getCallDuration() { return callDuration; }
    public void setCallDuration(Duration callDuration) { this.callDuration = callDuration; }
    
    public String getCallDirection() { return callDirection; }
    public void setCallDirection(String callDirection) { this.callDirection = callDirection; }
    
    public String getCallResult() { return callResult; }
    public void setCallResult(String callResult) { this.callResult = callResult; }
    
    @Override
    public boolean isValid() {
        return super.isValid() && 
               (getEventType() == EventType.CALL_COMPLETED || getEventType() == EventType.CALL_MISSED);
    }
}