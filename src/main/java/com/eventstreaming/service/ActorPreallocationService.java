package com.eventstreaming.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Set;

/**
 * DISABLED: Actor Pre-allocation Service
 * 
 * This service has been disabled because:
 * 1. PersistentUserActor with snapshots recovers in ~100-500ms (very fast)
 * 2. 6-hour passivation timeout prevents frequent cold starts
 * 3. Pre-allocation wastes memory on actors that might not be used
 * 4. Remember entities ensure actors restart where they left off
 * 
 * For most use cases, the natural actor lifecycle is sufficient.
 * Only consider re-enabling for:
 * - Extremely latency-sensitive VIP users
 * - Special events with predictable traffic spikes
 */
@Service
public class ActorPreallocationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorPreallocationService.class);
    
    public ActorPreallocationService() {
        logger.info("ActorPreallocationService initialized but DISABLED - relying on natural actor lifecycle");
    }
    
    // All pre-allocation methods are disabled
    
    public void preallocateBasedOnPrediction(Set<String> predictedActiveUsers) {
        logger.debug("Pre-allocation request ignored - service disabled. Predicted users: {}", predictedActiveUsers.size());
    }
    
    public void manualPreallocation(Set<String> userIds) {
        logger.debug("Manual pre-allocation request ignored - service disabled. Users: {}", userIds.size());
    }
    
    public PreallocationStats getStats() {
        return new PreallocationStats(0, LocalTime.now(), "DISABLED - Using natural actor lifecycle");
    }
    
    public boolean isPreallocated(String userId) {
        return false; // No pre-allocation
    }
    
    public record PreallocationStats(int currentlyTracked, LocalTime lastUpdate, String status) {}
}