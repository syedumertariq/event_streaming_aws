package com.eventstreaming.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * SMS communication event
 */
public class SmsEvent extends CommunicationEvent {
    
    private String phoneNumber;
    private String messageContent;
    private String carrierId;
    private String replyContent; // For reply events
    
    public SmsEvent() {
        super();
    }
    
    @JsonCreator
    public SmsEvent(
            @JsonProperty("userId") String userId,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") EventType eventType,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("messageContent") String messageContent,
            @JsonProperty("carrierId") String carrierId,
            @JsonProperty("replyContent") String replyContent,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        
        super(userId, eventId, eventType, timestamp, "sms-gateway", metadata);
        this.phoneNumber = phoneNumber;
        this.messageContent = messageContent;
        this.carrierId = carrierId;
        this.replyContent = replyContent;
    }
    
    // Convenience constructor for simple events
    public SmsEvent(String userId, String eventId, EventType eventType, Instant timestamp) {
        this(userId, eventId, eventType, timestamp, null, null, null, null, Map.of());
    }
    
    // Getters and setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getMessageContent() { return messageContent; }
    public void setMessageContent(String messageContent) { this.messageContent = messageContent; }
    
    public String getCarrierId() { return carrierId; }
    public void setCarrierId(String carrierId) { this.carrierId = carrierId; }
    
    public String getReplyContent() { return replyContent; }
    public void setReplyContent(String replyContent) { this.replyContent = replyContent; }
    
    @Override
    public boolean isValid() {
        return super.isValid() && 
               (getEventType() == EventType.SMS_REPLY || getEventType() == EventType.SMS_DELIVERY);
    }
}