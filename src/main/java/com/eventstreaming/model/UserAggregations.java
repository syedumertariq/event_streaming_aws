package com.eventstreaming.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * User aggregations model containing communication metrics and Walker-specific data
 */
@JsonDeserialize(builder = UserAggregations.Builder.class)
public class UserAggregations {
    
    private final String userId;
    private final long emailOpens;
    private final long emailClicks;
    private final long smsReplies;
    private final long smsDeliveries;
    private final long callsCompleted;
    private final long callsMissed;
    private final Instant lastActivity;
    private final double emailOpenRate;
    private final double smsResponseRate;
    private final double callCompletionRate;
    
    // Walker-specific aggregations
    private final ContactableStatus contactableStatus;
    private final String currentGraphNode;
    private final Instant lastWalkerProcessing;
    private final long walkerProcessingCount;
    
    @JsonCreator
    public UserAggregations(
            @JsonProperty("userId") String userId,
            @JsonProperty("emailOpens") long emailOpens,
            @JsonProperty("emailClicks") long emailClicks,
            @JsonProperty("smsReplies") long smsReplies,
            @JsonProperty("smsDeliveries") long smsDeliveries,
            @JsonProperty("callsCompleted") long callsCompleted,
            @JsonProperty("callsMissed") long callsMissed,
            @JsonProperty("lastActivity") Instant lastActivity,
            @JsonProperty("emailOpenRate") double emailOpenRate,
            @JsonProperty("smsResponseRate") double smsResponseRate,
            @JsonProperty("callCompletionRate") double callCompletionRate,
            @JsonProperty("contactableStatus") ContactableStatus contactableStatus,
            @JsonProperty("currentGraphNode") String currentGraphNode,
            @JsonProperty("lastWalkerProcessing") Instant lastWalkerProcessing,
            @JsonProperty("walkerProcessingCount") long walkerProcessingCount) {
        
        this.userId = userId;
        this.emailOpens = emailOpens;
        this.emailClicks = emailClicks;
        this.smsReplies = smsReplies;
        this.smsDeliveries = smsDeliveries;
        this.callsCompleted = callsCompleted;
        this.callsMissed = callsMissed;
        this.lastActivity = lastActivity;
        this.emailOpenRate = emailOpenRate;
        this.smsResponseRate = smsResponseRate;
        this.callCompletionRate = callCompletionRate;
        this.contactableStatus = contactableStatus;
        this.currentGraphNode = currentGraphNode;
        this.lastWalkerProcessing = lastWalkerProcessing;
        this.walkerProcessingCount = walkerProcessingCount;
    }
    
    // Default constructor for new users
    public UserAggregations(String userId) {
        this(userId, 0, 0, 0, 0, 0, 0, Instant.now(), 0.0, 0.0, 0.0,
             ContactableStatus.PENDING_REVIEW, null, null, 0);
    }
    
    // Getters
    public String getUserId() { return userId; }
    public long getEmailOpens() { return emailOpens; }
    public long getEmailClicks() { return emailClicks; }
    public long getSmsReplies() { return smsReplies; }
    public long getSmsDeliveries() { return smsDeliveries; }
    public long getCallsCompleted() { return callsCompleted; }
    public long getCallsMissed() { return callsMissed; }
    public Instant getLastActivity() { return lastActivity; }
    public double getEmailOpenRate() { return emailOpenRate; }
    public double getSmsResponseRate() { return smsResponseRate; }
    public double getCallCompletionRate() { return callCompletionRate; }
    public ContactableStatus getContactableStatus() { return contactableStatus; }
    public String getCurrentGraphNode() { return currentGraphNode; }
    public Instant getLastWalkerProcessing() { return lastWalkerProcessing; }
    public long getWalkerProcessingCount() { return walkerProcessingCount; }
    
    // Builder pattern for immutable updates
    public Builder toBuilder() {
        return new Builder()
            .userId(userId)
            .emailOpens(emailOpens)
            .emailClicks(emailClicks)
            .smsReplies(smsReplies)
            .smsDeliveries(smsDeliveries)
            .callsCompleted(callsCompleted)
            .callsMissed(callsMissed)
            .lastActivity(lastActivity)
            .emailOpenRate(emailOpenRate)
            .smsResponseRate(smsResponseRate)
            .callCompletionRate(callCompletionRate)
            .contactableStatus(contactableStatus)
            .currentGraphNode(currentGraphNode)
            .lastWalkerProcessing(lastWalkerProcessing)
            .walkerProcessingCount(walkerProcessingCount);
    }
    
    // Increment methods for event processing
    public UserAggregations incrementEmailOpens() {
        long newOpens = emailOpens + 1;
        return toBuilder()
            .emailOpens(newOpens)
            .lastActivity(Instant.now())
            .emailOpenRate(calculateEmailOpenRate(newOpens, smsDeliveries))
            .build();
    }
    
    public UserAggregations incrementEmailClicks() {
        return toBuilder()
            .emailClicks(emailClicks + 1)
            .lastActivity(Instant.now())
            .build();
    }
    
    public UserAggregations incrementSmsReplies() {
        long newReplies = smsReplies + 1;
        return toBuilder()
            .smsReplies(newReplies)
            .lastActivity(Instant.now())
            .smsResponseRate(calculateSmsResponseRate(newReplies, smsDeliveries))
            .build();
    }
    
    public UserAggregations incrementSmsDeliveries() {
        long newDeliveries = smsDeliveries + 1;
        return toBuilder()
            .smsDeliveries(newDeliveries)
            .lastActivity(Instant.now())
            .emailOpenRate(calculateEmailOpenRate(emailOpens, newDeliveries))
            .smsResponseRate(calculateSmsResponseRate(smsReplies, newDeliveries))
            .build();
    }
    
    public UserAggregations incrementCallsCompleted() {
        long newCompleted = callsCompleted + 1;
        return toBuilder()
            .callsCompleted(newCompleted)
            .lastActivity(Instant.now())
            .callCompletionRate(calculateCallCompletionRate(newCompleted, callsMissed))
            .build();
    }
    
    public UserAggregations incrementCallsMissed() {
        long newMissed = callsMissed + 1;
        return toBuilder()
            .callsMissed(newMissed)
            .lastActivity(Instant.now())
            .callCompletionRate(calculateCallCompletionRate(callsCompleted, newMissed))
            .build();
    }
    
    public UserAggregations updateContactableStatus(ContactableStatus status, 
                                                  String graphNode, 
                                                  Instant processingTime) {
        return toBuilder()
            .contactableStatus(status)
            .currentGraphNode(graphNode)
            .lastWalkerProcessing(processingTime)
            .walkerProcessingCount(walkerProcessingCount + 1)
            .build();
    }
    
    // Rate calculation methods
    private double calculateEmailOpenRate(long opens, long sent) {
        return sent > 0 ? (double) opens / sent : 0.0;
    }
    
    private double calculateSmsResponseRate(long replies, long sent) {
        return sent > 0 ? (double) replies / sent : 0.0;
    }
    
    private double calculateCallCompletionRate(long completed, long missed) {
        long total = completed + missed;
        return total > 0 ? (double) completed / total : 0.0;
    }
    
    // Utility methods
    public boolean isRecentlyActive() {
        return lastActivity != null && 
               lastActivity.isAfter(Instant.now().minus(7, ChronoUnit.DAYS));
    }
    
    public boolean isHighEngagement() {
        return emailOpenRate > 0.7 || smsResponseRate > 0.5 || callCompletionRate > 0.8;
    }
    
    // Compatibility methods for existing code
    public long getEmailCount() {
        return emailOpens + emailClicks;
    }
    
    public long getSmsCount() {
        return smsReplies + smsDeliveries;
    }
    
    public long getCallCount() {
        return callsCompleted + callsMissed;
    }
    
    public long getTotalEvents() {
        return getEmailCount() + getSmsCount() + getCallCount();
    }
    
    public Instant getLastUpdated() {
        return lastActivity;
    }
    
    @Override
    public String toString() {
        return String.format("UserAggregations{userId='%s', emailOpens=%d, emailClicks=%d, " +
                           "smsReplies=%d, smsDeliveries=%d, callsCompleted=%d, callsMissed=%d, " +
                           "contactableStatus=%s, lastActivity=%s}",
                           userId, emailOpens, emailClicks, smsReplies, smsDeliveries,
                           callsCompleted, callsMissed, contactableStatus, lastActivity);
    }
    
    // Builder class
    public static class Builder {
        private String userId;
        private long emailOpens;
        private long emailClicks;
        private long smsReplies;
        private long smsDeliveries;
        private long callsCompleted;
        private long callsMissed;
        private Instant lastActivity;
        private double emailOpenRate;
        private double smsResponseRate;
        private double callCompletionRate;
        private ContactableStatus contactableStatus;
        private String currentGraphNode;
        private Instant lastWalkerProcessing;
        private long walkerProcessingCount;
        
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder emailOpens(long emailOpens) { this.emailOpens = emailOpens; return this; }
        public Builder emailClicks(long emailClicks) { this.emailClicks = emailClicks; return this; }
        public Builder smsReplies(long smsReplies) { this.smsReplies = smsReplies; return this; }
        public Builder smsDeliveries(long smsDeliveries) { this.smsDeliveries = smsDeliveries; return this; }
        public Builder callsCompleted(long callsCompleted) { this.callsCompleted = callsCompleted; return this; }
        public Builder callsMissed(long callsMissed) { this.callsMissed = callsMissed; return this; }
        public Builder lastActivity(Instant lastActivity) { this.lastActivity = lastActivity; return this; }
        public Builder emailOpenRate(double emailOpenRate) { this.emailOpenRate = emailOpenRate; return this; }
        public Builder smsResponseRate(double smsResponseRate) { this.smsResponseRate = smsResponseRate; return this; }
        public Builder callCompletionRate(double callCompletionRate) { this.callCompletionRate = callCompletionRate; return this; }
        public Builder contactableStatus(ContactableStatus contactableStatus) { this.contactableStatus = contactableStatus; return this; }
        public Builder currentGraphNode(String currentGraphNode) { this.currentGraphNode = currentGraphNode; return this; }
        public Builder lastWalkerProcessing(Instant lastWalkerProcessing) { this.lastWalkerProcessing = lastWalkerProcessing; return this; }
        public Builder walkerProcessingCount(long walkerProcessingCount) { this.walkerProcessingCount = walkerProcessingCount; return this; }
        
        public UserAggregations build() {
            return new UserAggregations(userId, emailOpens, emailClicks, smsReplies, smsDeliveries,
                                      callsCompleted, callsMissed, lastActivity, emailOpenRate,
                                      smsResponseRate, callCompletionRate, contactableStatus,
                                      currentGraphNode, lastWalkerProcessing, walkerProcessingCount);
        }
    }
}