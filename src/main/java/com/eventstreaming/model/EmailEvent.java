package com.eventstreaming.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Email communication event
 */
public class EmailEvent extends CommunicationEvent {
    
    private String emailAddress;
    private String campaignId;
    private String subject;
    private String linkUrl; // For click events
    
    public EmailEvent() {
        super();
    }
    
    @JsonCreator
    public EmailEvent(
            @JsonProperty("userId") String userId,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") EventType eventType,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("source") String source,
            @JsonProperty("emailAddress") String emailAddress,
            @JsonProperty("campaignId") String campaignId,
            @JsonProperty("subject") String subject,
            @JsonProperty("linkUrl") String linkUrl,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        
        super(userId, eventId, eventType, timestamp, source, metadata);
        this.emailAddress = emailAddress;
        this.campaignId = campaignId;
        this.subject = subject;
        this.linkUrl = linkUrl;
    }
    
    // Convenience constructor for simple events
    public EmailEvent(String userId, String eventId, EventType eventType, Instant timestamp) {
        this(userId, eventId, eventType, timestamp, "api", null, null, null, null, Map.of());
    }
    
    // Getters and setters
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
    
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    
    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    
    @Override
    public boolean isValid() {
        return super.isValid() && 
               (getEventType() == EventType.EMAIL_OPEN || getEventType() == EventType.EMAIL_CLICK);
    }
    
    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String userId;
        private String eventId;
        private EventType eventType;
        private Instant timestamp;
        private String source = "api";
        private String emailAddress;
        private String campaignId;
        private String subject;
        private String linkUrl;
        private Map<String, Object> metadata = Map.of();
        
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder eventType(EventType eventType) { this.eventType = eventType; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder emailAddress(String emailAddress) { this.emailAddress = emailAddress; return this; }
        public Builder campaignId(String campaignId) { this.campaignId = campaignId; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder linkUrl(String linkUrl) { this.linkUrl = linkUrl; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        
        public EmailEvent build() {
            return new EmailEvent(userId, eventId, eventType, timestamp, source, 
                                emailAddress, campaignId, subject, linkUrl, metadata);
        }
    }
}