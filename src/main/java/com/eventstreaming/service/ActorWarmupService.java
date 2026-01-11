package com.eventstreaming.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DISABLED: Actor Warmup Service
 * 
 * This service has been disabled because:
 * 1. PersistentUserActor recovers fast with snapshots (~100-500ms)
 * 2. 6-hour passivation timeout prevents frequent restarts
 * 3. Keep-warm logic adds unnecessary complexity and memory pressure
 * 4. Pekko's built-in optimizations are sufficient
 * 
 * If you need to re-enable for specific use cases, consider:
 * - Only for truly critical VIP users
 * - Monitor memory usage carefully
 * - Ensure proper cleanup mechanisms
 */
@Service
public class ActorWarmupService {
    
    private static final Logger logger = LoggerFactory.getLogger(ActorWarmupService.class);
    
    public ActorWarmupService() {
        logger.info("ActorWarmupService initialized but DISABLED - using Pekko's built-in optimizations");
    }
    
    // All warmup methods are disabled - Pekko handles optimization internally
    
    public WarmupStats getWarmupStats() {
        return new WarmupStats(0, 0, 0, "DISABLED - Using Pekko built-in optimizations");
    }
    
    public record WarmupStats(int hotUsersTracked, long totalUsers, long registryHotUsers, String status) {}
}