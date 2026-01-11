package com.eventstreaming.service;

import com.eventstreaming.model.MemoryOnlyUserAggregations;
import com.eventstreaming.model.UserAggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for loading Everest data into memory-only aggregations.
 */
@Service
public class EverestDataLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(EverestDataLoaderService.class);
    
    private final Map<String, MemoryOnlyUserAggregations.EverestMetadata> everestCache = new ConcurrentHashMap<>();

    /**
     * Enhances existing UserAggregations with Everest data loaded from memory/cache.
     */
    public MemoryOnlyUserAggregations enhanceWithEverestData(UserAggregations baseAggregations) {
        String userId = baseAggregations.getUserId();
        
        // Check cache first
        MemoryOnlyUserAggregations.EverestMetadata cachedMetadata = everestCache.get(userId);
        if (cachedMetadata != null && isCacheValid(cachedMetadata)) {
            logger.debug("Using cached Everest data for user: {}", userId);
            return new MemoryOnlyUserAggregations(baseAggregations, cachedMetadata);
        }

        // Load fresh Everest data
        MemoryOnlyUserAggregations.EverestMetadata metadata = loadEverestDataForUser(userId);
        
        // Cache the metadata
        everestCache.put(userId, metadata);
        
        logger.debug("Enhanced user {} with Everest data", userId);
        return new MemoryOnlyUserAggregations(baseAggregations, metadata);
    }

    /**
     * Loads Everest data for a specific user.
     * TODO: Replace with actual Everest data source.
     */
    private MemoryOnlyUserAggregations.EverestMetadata loadEverestDataForUser(String userId) {
        MemoryOnlyUserAggregations.EverestMetadata metadata = new MemoryOnlyUserAggregations.EverestMetadata();
        
        try {
            // Mock data - replace with actual Everest data loading
            int userHash = userId.hashCode();
            metadata.setAge(25 + (Math.abs(userHash) % 50));
            metadata.setState(getRandomState(userHash));
            metadata.setEmailDomain(getRandomEmailDomain(userHash));
            metadata.setInquiryVertical(getRandomVertical(userHash));
            metadata.setDisqualifiedCount(Math.abs(userHash) % 5);
            metadata.setDoNotEmail((userHash % 10) < 2);  // 20% do not email
            metadata.setAreaOfInterest(getRandomAreaOfInterest(userHash));
            metadata.setLastEverestSync(Instant.now());
            
        } catch (Exception e) {
            logger.warn("Failed to load Everest data for user: {}", userId, e);
        }
        
        return metadata;
    }

    private String getRandomState(int hash) {
        String[] states = {"CA", "TX", "FL", "NY", "PA", "IL", "OH", "GA", "NC", "MI"};
        return states[Math.abs(hash) % states.length];
    }

    private String getRandomEmailDomain(int hash) {
        String[] domains = {"gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "aol.com"};
        return domains[Math.abs(hash) % domains.length];
    }

    private String getRandomVertical(int hash) {
        String[] verticals = {"Education", "Insurance", "Finance", "Healthcare", "Auto", "Real Estate"};
        return verticals[Math.abs(hash) % verticals.length];
    }

    private String getRandomAreaOfInterest(int hash) {
        String[] areas = {"Business", "Healthcare", "Technology", "Education", "Arts", null};
        return areas[Math.abs(hash) % areas.length];
    }

    /**
     * Checks if cached metadata is still valid (60 minute TTL).
     */
    private boolean isCacheValid(MemoryOnlyUserAggregations.EverestMetadata metadata) {
        if (metadata.getLastEverestSync() == null) {
            return false;
        }
        
        Instant expiry = metadata.getLastEverestSync().plus(java.time.Duration.ofMinutes(60));
        return Instant.now().isBefore(expiry);
    }

    /**
     * Clears the Everest cache.
     */
    public void clearCache() {
        everestCache.clear();
        logger.info("Cleared Everest data cache");
    }

    /**
     * Gets cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", everestCache.size());
        stats.put("cacheTtlMinutes", 60);
        return stats;
    }
}
