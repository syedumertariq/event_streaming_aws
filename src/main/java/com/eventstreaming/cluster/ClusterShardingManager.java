package com.eventstreaming.cluster;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.eventstreaming.model.CommunicationEvent;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * Service for managing cluster sharding operations.
 * Provides high-level API for interacting with sharded user actors.
 * Only active when cluster-related profiles are enabled.
 */
@Service
@Profile({"cluster", "cluster-mysql", "aws-mysql", "default", "test", "isolated", "aws-simple"})
public class ClusterShardingManager {

    private final ClusterSharding sharding;
    private final ActorSystem<?> actorSystem;
    private final Duration askDuration = Duration.ofSeconds(30);

    @Autowired
    public ClusterShardingManager(ClusterSharding sharding, ActorSystem<?> actorSystem) {
        this.sharding = sharding;
        this.actorSystem = actorSystem;
    }

    /**
     * Get a reference to a user actor. The actor will be created if it doesn't exist.
     */
    public EntityRef<ClusterUserActor.Command> getUserActor(String userId) {
        return sharding.entityRefFor(ClusterUserActor.ENTITY_TYPE_KEY, userId);
    }

    /**
     * Process an event for a specific user through the cluster.
     */
    public CompletionStage<ClusterUserActor.EventProcessed> processUserEvent(CommunicationEvent event) {
        try {
            EntityRef<ClusterUserActor.Command> userActor = getUserActor(event.getUserId());
            
            actorSystem.log().debug("Processing event for user {} via actor {}", 
                event.getUserId(), userActor.toString());
            
            return userActor.ask(
                (ActorRef<ClusterUserActor.EventProcessed> replyTo) -> 
                    new ClusterUserActor.ProcessEvent(event, replyTo),
                askDuration
            ).exceptionally(throwable -> {
                actorSystem.log().error("Failed to process event for user {}: {}", 
                    event.getUserId(), throwable.getMessage(), throwable);
                return new ClusterUserActor.EventProcessed(event.getUserId(), false, 
                    "Timeout or communication error: " + throwable.getMessage());
            });
        } catch (Exception e) {
            actorSystem.log().error("Error getting user actor for {}: {}", 
                event.getUserId(), e.getMessage(), e);
            return java.util.concurrent.CompletableFuture.completedFuture(
                new ClusterUserActor.EventProcessed(event.getUserId(), false, 
                    "Error getting user actor: " + e.getMessage()));
        }
    }

    /**
     * Get statistics for a specific user.
     */
    public CompletionStage<ClusterUserActor.UserStats> getUserStats(String userId) {
        try {
            EntityRef<ClusterUserActor.Command> userActor = getUserActor(userId);
            
            actorSystem.log().debug("Getting stats for user {} via actor {}", 
                userId, userActor.toString());
            
            return userActor.ask(
                (ActorRef<ClusterUserActor.UserStats> replyTo) -> 
                    new ClusterUserActor.GetUserStats(replyTo),
                askDuration
            ).exceptionally(throwable -> {
                actorSystem.log().error("Failed to get stats for user {}: {}", 
                    userId, throwable.getMessage(), throwable);
                // Return empty stats instead of null
                return new ClusterUserActor.UserStats(userId, 0, 
                    java.time.LocalDateTime.now(), 0);
            });
        } catch (Exception e) {
            actorSystem.log().error("Error getting user actor for stats {}: {}", 
                userId, e.getMessage(), e);
            return java.util.concurrent.CompletableFuture.completedFuture(
                new ClusterUserActor.UserStats(userId, 0, 
                    java.time.LocalDateTime.now(), 0));
        }
    }

    /**
     * Process multiple events in parallel across the cluster.
     */
    public CompletionStage<Void> processEvents(java.util.List<CommunicationEvent> events) {
        // Group events by user for efficient processing
        java.util.Map<String, java.util.List<CommunicationEvent>> eventsByUser = 
            events.stream().collect(java.util.stream.Collectors.groupingBy(CommunicationEvent::getUserId));

        // Process each user's events
        java.util.List<CompletionStage<Void>> futures = eventsByUser.entrySet().stream()
            .map(entry -> {
                String userId = entry.getKey();
                java.util.List<CommunicationEvent> userEvents = entry.getValue();
                
                return processUserEvents(userId, userEvents);
            })
            .collect(java.util.stream.Collectors.toList());

        // Wait for all to complete
        return java.util.concurrent.CompletableFuture.allOf(
            futures.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(java.util.concurrent.CompletableFuture[]::new)
        );
    }

    private CompletionStage<Void> processUserEvents(String userId, java.util.List<CommunicationEvent> events) {
        EntityRef<ClusterUserActor.Command> userActor = getUserActor(userId);
        
        // Process events sequentially for the same user to maintain order
        CompletionStage<Void> result = java.util.concurrent.CompletableFuture.completedFuture(null);
        
        for (CommunicationEvent event : events) {
            result = result.thenCompose(ignored -> 
                userActor.ask(
                    (ActorRef<ClusterUserActor.EventProcessed> replyTo) -> 
                        new ClusterUserActor.ProcessEvent(event, replyTo),
                    askDuration
                ).thenApply(response -> {
                    ClusterUserActor.EventProcessed eventResponse = (ClusterUserActor.EventProcessed) response;
                    if (!eventResponse.success) {
                        actorSystem.log().warn("Failed to process event for user {}: {}", 
                            userId, eventResponse.message);
                    }
                    return null;
                })
            );
        }
        
        return result;
    }

    /**
     * Get the current cluster sharding instance.
     */
    public ClusterSharding getSharding() {
        return sharding;
    }
}