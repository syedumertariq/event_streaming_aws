package com.eventstreaming.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Memory-only enhanced UserAggregations that extends the existing model
 * with Everest fields stored only in memory (not persisted to database).
 * 
 * This allows integration testing without database schema changes.
 * The Everest fields are loaded on-demand and cached in actor memory.
 */
public class MemoryOnlyUserAggregations extends UserAggregations {

    // Memory-only Everest fields (NOT PERSISTED)
    @JsonIgnore  // Don't serialize to database
    private final transient EverestMetadata everestMetadata;

    /**
     * Container for all Everest-specific data that stays in memory only
     */
    public static class EverestMetadata {
        // Activity Filters
        private Instant lastSignupDate;
        private Instant firstSignupDate;
        private Instant lastClickDate;
        private Instant lastOpenDate;
        private long emailDropCount;
        private long emailOpenCount;
        private List<String> openedCampaigns = new ArrayList<>();

        // User Attributes
        private Integer age;
        private String state;
        private String zipcode;
        private String gender;
        private String occupation;
        private String educationLevel;
        private LocalDate birthDate;
        private String employmentStatus;

        // System Attributes
        private String listSource;
        private String latestEventType;
        private String deviceType;

        // Source Attributes
        private String inquiryVertical;
        private String inquiryPublisher;

        // Domain Filters
        private String emailDomain;
        private String emailDomainGroup;

        // Mailable Filters
        private long disqualifiedCount;
        private Boolean doNotEmail;

        // Education Attributes
        private String areaOfInterest;
        private String degreeOfInterest;

        // Processing Metadata
        private Map<String, Object> processingMetadata = new HashMap<>();
        private Instant lastEverestSync;
        private String runStatus;

        // Getters and setters
        public Instant getLastSignupDate() { return lastSignupDate; }
        public void setLastSignupDate(Instant lastSignupDate) { this.lastSignupDate = lastSignupDate; }

        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public String getEmailDomain() { return emailDomain; }
        public void setEmailDomain(String emailDomain) { this.emailDomain = emailDomain; }

        public String getInquiryVertical() { return inquiryVertical; }
        public void setInquiryVertical(String inquiryVertical) { this.inquiryVertical = inquiryVertical; }

        public long getDisqualifiedCount() { return disqualifiedCount; }
        public void setDisqualifiedCount(long disqualifiedCount) { this.disqualifiedCount = disqualifiedCount; }

        public Boolean getDoNotEmail() { return doNotEmail; }
        public void setDoNotEmail(Boolean doNotEmail) { this.doNotEmail = doNotEmail; }

        public String getAreaOfInterest() { return areaOfInterest; }
        public void setAreaOfInterest(String areaOfInterest) { this.areaOfInterest = areaOfInterest; }

        public Map<String, Object> getProcessingMetadata() { return new HashMap<>(processingMetadata); }
        public void setProcessingMetadata(Map<String, Object> processingMetadata) { 
            this.processingMetadata = processingMetadata != null ? new HashMap<>(processingMetadata) : new HashMap<>(); 
        }

        public Instant getLastEverestSync() { return lastEverestSync; }
        public void setLastEverestSync(Instant lastEverestSync) { this.lastEverestSync = lastEverestSync; }

        // Utility methods
        public boolean isRecentlyActive() {
            return lastSignupDate != null && lastSignupDate.isAfter(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
        }

        public boolean isMailable() {
            return doNotEmail == null || (!doNotEmail && disqualifiedCount < 3);
        }

        public String getUserSegment() {
            if (areaOfInterest != null) return "Education";
            if (inquiryVertical != null && inquiryVertical.contains("Auto")) return "Auto";
            return "General";
        }
    }

    @JsonCreator
    public MemoryOnlyUserAggregations(
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

        super(userId, emailOpens, emailClicks, smsReplies, smsDeliveries,
              callsCompleted, callsMissed, lastActivity, emailOpenRate,
              smsResponseRate, callCompletionRate, contactableStatus,
              currentGraphNode, lastWalkerProcessing, walkerProcessingCount);

        // Initialize empty Everest metadata
        this.everestMetadata = new EverestMetadata();
    }

    // Constructor with Everest metadata
    public MemoryOnlyUserAggregations(UserAggregations base, EverestMetadata everestMetadata) {
        super(base.getUserId(), base.getEmailOpens(), base.getEmailClicks(),
              base.getSmsReplies(), base.getSmsDeliveries(), base.getCallsCompleted(),
              base.getCallsMissed(), base.getLastActivity(), base.getEmailOpenRate(),
              base.getSmsResponseRate(), base.getCallCompletionRate(),
              base.getContactableStatus(), base.getCurrentGraphNode(),
              base.getLastWalkerProcessing(), base.getWalkerProcessingCount());

        this.everestMetadata = everestMetadata != null ? everestMetadata : new EverestMetadata();
    }

    // Default constructor for new users
    public MemoryOnlyUserAggregations(String userId) {
        super(userId);
        this.everestMetadata = new EverestMetadata();
    }

    // Everest field access methods
    public EverestMetadata getEverestMetadata() {
        return everestMetadata;
    }

    // Convenience methods for common Everest fields
    public Integer getEverestAge() {
        return everestMetadata.getAge();
    }

    public String getEverestState() {
        return everestMetadata.getState();
    }

    public String getEverestEmailDomain() {
        return everestMetadata.getEmailDomain();
    }

    public String getEverestInquiryVertical() {
        return everestMetadata.getInquiryVertical();
    }

    public boolean isEverestMailable() {
        return everestMetadata.isMailable();
    }

    public String getEverestUserSegment() {
        return everestMetadata.getUserSegment();
    }

    public boolean hasEverestData() {
        return everestMetadata.getLastEverestSync() != null;
    }

    @Override
    public boolean isRecentlyActive() {
        // Check both Pekko and Everest activity
        return super.isRecentlyActive() || everestMetadata.isRecentlyActive();
    }

    @Override
    public String toString() {
        return String.format("MemoryOnlyUserAggregations{userId='%s', totalEvents=%d, " +
                           "everestAge=%s, everestState='%s', everestSegment='%s', " +
                           "hasEverestData=%s, contactableStatus=%s}",
                           getUserId(), getTotalEvents(), getEverestAge(), 
                           getEverestState(), getEverestUserSegment(), 
                           hasEverestData(), getContactableStatus());
    }
}
