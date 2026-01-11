package com.eventstreaming.service;

import com.eventstreaming.cluster.PersistentUserActor;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Simplified Cluster-Safe User Actor Registry for Persistent Actors
 * 
 * PHILOSOPHY: Let Pekko handle the complexity
 * - No keep-warm logic (persistent actors recover fast with snapshots)
 * - No metadata caching (eliminates stale state issues)
 * - Trust Pekko's built-in optimizations
 * 
 * BENEFITS:
 * - Simpler code = fewer bugs
 * - Lower memory usage
 * - No stale state issues
 * - Better cluster stability
 */
@Service
public class ClusterSafeUserActorRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterSafeUserActorRegistry.class);
    
    private final ClusterSharding sharding;
    
    @Autowired
    public ClusterSafeUserActorRegistry(ActorSystem<?> actorSystem) {
        this.sharding = ClusterSharding.get(actorSystem);
        initializeSharding();
    }
    
    private void initializeSharding() {
        try {
            sharding.init(Entity.of(PersistentUserActor.ENTITY_TYPE_KEY, entityContext -> {
                String userId = entityContext.getEntityId();
                logger.debug("Creating PersistentUserActor for user: {}", userId);
                return PersistentUserActor.create(userId);
            }));
            
            logger.info("Cluster sharding initialized for PersistentUserActor - 6h passivation, snapshots every 50 events");
        } catch (Exception e) {
            logger.error("Failed to initialize cluster sharding", e);
            throw new RuntimeException("Failed to initialize cluster sharding", e);
        }
    }
    
    /**
     * Get a user actor reference.
     * 
     * CLUSTER-SAFE: Always returns fresh EntityRef
     * PERFORMANCE: Pekko handles caching and optimization internally
     * RELIABILITY: No custom caching = no stale state issues
     */
    public EntityRef<PersistentUserActor.Command> getUserActor(String userId) {
        logger.debug("Getting PersistentUserActor for user: {}", userId);
        return sharding.entityRefFor(PersistentUserActor.ENTITY_TYPE_KEY, userId);
    }
    
    /**
     * Get basic registry information
     */
    public RegistryStats getStats() {
        return new RegistryStats(
            "PersistentUserActor",
            "6h passivation with MySQL persistence",
            "Snapshots every 50 events"
        );
    }
    
    public record RegistryStats(String actorType, String passivationConfig, String snapshotConfig) {}
}