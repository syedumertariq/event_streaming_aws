package com.eventstreaming.enrichment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents an enriched user profile with ML-generated engagement metrics
 * and behavioral insights computed by Spark batch jobs.
 */
public class EnrichedUserProfile {

    private final String userId;
    private final long totalEvents;
    private final double emailOpenRate;
    private final double emailClickThroughRate;
    private final double smsResponseRate;
    private final double callCompletionRate;
    private final double engagementScore;
    private final double activityScore;
    private final double frequencyScore;
    private final double overallEngagementScore;
    private final String engagementTier;
    private final int daysSinceLastActivity;
    private final int activeDays;
    private final Instant enrichmentTimestamp;
    private final Instant lastActivity;
    private final double avgEventsPerDay;

    @JsonCreator
    public EnrichedUserProfile(
            @JsonProperty("userId") String userId,
            @JsonProperty("totalEvents") long totalEvents,
            @JsonProperty("emailOpenRate") double emailOpenRate,
            @JsonProperty("emailClickThroughRate") double emailClickThroughRate,
            @JsonProperty("smsResponseRate") double smsResponseRate,
            @JsonProperty("callCompletionRate") double callCompletionRate,
            @JsonProperty("engagementScore") double engagementScore,
            @JsonProperty("activityScore") double activityScore,
            @JsonProperty("frequencyScore") double frequencyScore,
            @JsonProperty("overallEngagementScore") double overallEngagementScore,
            @JsonProperty("engagementTier") String engagementTier,
            @JsonProperty("daysSinceLastActivity") int daysSinceLastActivity,
            @JsonProperty("activeDays") int activeDays,
            @JsonProperty("enrichmentTimestamp") Instant enrichmentTimestamp,
            @JsonProperty("lastActivity") Instant lastActivity,
            @JsonProperty("avgEventsPerDay") double avgEventsPerDay) {
        this.userId = userId;
        this.totalEvents = totalEvents;
        this.emailOpenRate = emailOpenRate;
        this.emailClickThroughRate = emailClickThroughRate;
        this.smsResponseRate = smsResponseRate;
        this.callCompletionRate = callCompletionRate;
        this.engagementScore = engagementScore;
        this.activityScore = activityScore;
        this.frequencyScore = frequencyScore;
        this.overallEngagementScore = overallEngagementScore;
        this.engagementTier = engagementTier;
        this.daysSinceLastActivity = daysSinceLastActivity;
        this.activeDays = activeDays;
        this.enrichmentTimestamp = enrichmentTimestamp;
        this.lastActivity = lastActivity;
        this.avgEventsPerDay = avgEventsPerDay;
    }

    // Getters
    public String getUserId() { return userId; }
    public long getTotalEvents() { return totalEvents; }
    public double getEmailOpenRate() { return emailOpenRate; }
    public double getEmailClickThroughRate() { return emailClickThroughRate; }
    public double getSmsResponseRate() { return smsResponseRate; }
    public double getCallCompletionRate() { return callCompletionRate; }
    public double getEngagementScore() { return engagementScore; }
    public double getActivityScore() { return activityScore; }
    public double getFrequencyScore() { return frequencyScore; }
    public double getOverallEngagementScore() { return overallEngagementScore; }
    public String getEngagementTier() { return engagementTier; }
    public int getDaysSinceLastActivity() { return daysSinceLastActivity; }
    public int getActiveDays() { return activeDays; }
    public Instant getEnrichmentTimestamp() { return enrichmentTimestamp; }
    public Instant getLastActivity() { return lastActivity; }
    public double getAvgEventsPerDay() { return avgEventsPerDay; }

    /**
     * Checks if the enriched profile is stale and needs refreshing.
     */
    public boolean isStale(Duration maxAge) {
        return enrichmentTimestamp.isBefore(Instant.now().minus(maxAge));
    }

    /**
     * Determines if the user is highly engaged based on enrichment metrics.
     */
    public boolean isHighlyEngaged() {
        return "HIGH".equals(engagementTier) && overallEngagementScore >= 80.0;
    }

    /**
     * Determines if the user is recently active.
     */
    public boolean isRecentlyActive() {
        return daysSinceLastActivity <= 7;
    }

    /**
     * Gets a risk score for communication fatigue (0-100, higher = more risk).
     */
    public double getCommunicationFatigueRisk() {
        // High frequency with low engagement indicates fatigue risk
        if (avgEventsPerDay > 5 && overallEngagementScore < 40) {
            return 80.0;
        } else if (avgEventsPerDay > 10 && overallEngagementScore < 60) {
            return 60.0;
        } else if (daysSinceLastActivity > 30) {
            return 70.0; // Inactive users are risky to contact
        }
        return Math.max(0, 100 - overallEngagementScore);
    }

    /**
     * Creates a default enriched profile for users without batch-computed data.
     */
    public static EnrichedUserProfile createDefault(String userId) {
        return new EnrichedUserProfile(
                userId,
                0L, // totalEvents
                0.0, // emailOpenRate
                0.0, // emailClickThroughRate
                0.0, // smsResponseRate
                0.0, // callCompletionRate
                0.0, // engagementScore
                20.0, // activityScore (low default)
                20.0, // frequencyScore (low default)
                20.0, // overallEngagementScore (low default)
                "INACTIVE", // engagementTier
                999, // daysSinceLastActivity (very high)
                0, // activeDays
                Instant.now(), // enrichmentTimestamp
                Instant.EPOCH, // lastActivity (very old)
                0.0 // avgEventsPerDay
        );
    }

    @Override
    public String toString() {
        return "EnrichedUserProfile{" +
                "userId='" + userId + '\'' +
                ", engagementTier='" + engagementTier + '\'' +
                ", overallEngagementScore=" + overallEngagementScore +
                ", daysSinceLastActivity=" + daysSinceLastActivity +
                ", enrichmentTimestamp=" + enrichmentTimestamp +
                '}';
    }
}