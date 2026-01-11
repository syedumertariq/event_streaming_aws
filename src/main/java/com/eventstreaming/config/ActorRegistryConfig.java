package com.eventstreaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Production configuration for Actor Registry
 */
@Configuration
@ConfigurationProperties(prefix = "actor.registry")
public class ActorRegistryConfig {
    
    /**
     * Maximum number of user metadata entries to track
     */
    private int maxMetadataSize = 10000;
    
    /**
     * Number of requests to consider an actor "hot"
     */
    private long hotActorThreshold = 10;
    
    /**
     * Minutes to keep metadata for recently accessed actors
     */
    private int recentAccessMinutes = 30;
    
    /**
     * Hours after which to clean up inactive metadata
     */
    private int metadataCleanupHours = 24;
    
    /**
     * Warmup interval in minutes
     */
    private int warmupIntervalMinutes = 30;
    
    /**
     * Cleanup interval in minutes
     */
    private int cleanupIntervalMinutes = 60;
    
    // Getters and setters
    public int getMaxMetadataSize() {
        return maxMetadataSize;
    }
    
    public void setMaxMetadataSize(int maxMetadataSize) {
        this.maxMetadataSize = maxMetadataSize;
    }
    
    public long getHotActorThreshold() {
        return hotActorThreshold;
    }
    
    public void setHotActorThreshold(long hotActorThreshold) {
        this.hotActorThreshold = hotActorThreshold;
    }
    
    public int getRecentAccessMinutes() {
        return recentAccessMinutes;
    }
    
    public void setRecentAccessMinutes(int recentAccessMinutes) {
        this.recentAccessMinutes = recentAccessMinutes;
    }
    
    public int getMetadataCleanupHours() {
        return metadataCleanupHours;
    }
    
    public void setMetadataCleanupHours(int metadataCleanupHours) {
        this.metadataCleanupHours = metadataCleanupHours;
    }
    
    public int getWarmupIntervalMinutes() {
        return warmupIntervalMinutes;
    }
    
    public void setWarmupIntervalMinutes(int warmupIntervalMinutes) {
        this.warmupIntervalMinutes = warmupIntervalMinutes;
    }
    
    public int getCleanupIntervalMinutes() {
        return cleanupIntervalMinutes;
    }
    
    public void setCleanupIntervalMinutes(int cleanupIntervalMinutes) {
        this.cleanupIntervalMinutes = cleanupIntervalMinutes;
    }
}