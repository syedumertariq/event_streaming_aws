package com.eventstreaming.cluster;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.eventstreaming.model.CommunicationEvent;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * High-level service for user operations in a clustered environment.
 * Integrates with cluster sharding for distributed user management.
 * Only active when cluster-related profiles are enabled.
 */
@Service
@Profile({"cluster", "default", "test", "isolated", "aws-simple"})
public class ClusterUserService {

    private final ClusterShardingManager shardingManager;

    @Autowired
    public ClusterUserService(ClusterShardingManager shardingManager) {
        this.shardingManager = shardingManager;
    }

    /**
     * Process a single communication event for a user.
     */
    public CompletionStage<Boolean> processEvent(CommunicationEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        
        return shardingManager.processUserEvent(event)
            .thenApply(result -> {
                System.out.println("Event processing result for user " + event.getUserId() + ": " + result.success + " - " + result.message);
                return result.success;
            })
            .exceptionally(throwable -> {
                System.err.println("Error processing event for user " + event.getUserId() + ": " + throwable.getMessage());
                throwable.printStackTrace();
                return false;
            });
    }

    /**
     * Process multiple events efficiently across the cluster.
     */
    public CompletionStage<Void> processEvents(List<CommunicationEvent> events) {
        return shardingManager.processEvents(events);
    }

    /**
     * Get user statistics from the cluster.
     */
    public CompletionStage<UserStatistics> getUserStatistics(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        
        return shardingManager.getUserStats(userId)
            .thenApply(stats -> {
                System.out.println("Retrieved stats for user " + userId + ": " + stats.totalEvents + " events");
                return new UserStatistics(
                    stats.userId,
                    stats.totalEvents,
                    stats.lastActivity,
                    stats.recentEventsCount
                );
            })
            .exceptionally(throwable -> {
                System.err.println("Error getting stats for user " + userId + ": " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
    }

    /**
     * Get statistics for multiple users in parallel.
     */
    public CompletionStage<List<UserStatistics>> getUserStatistics(List<String> userIds) {
        List<CompletionStage<UserStatistics>> futures = userIds.stream()
            .map(this::getUserStatistics)
            .collect(java.util.stream.Collectors.toList());

        return java.util.concurrent.CompletableFuture.allOf(
                futures.stream()
                    .map(CompletionStage::toCompletableFuture)
                    .toArray(java.util.concurrent.CompletableFuture[]::new)
            ).thenApply(ignored -> 
                futures.stream()
                    .map(CompletionStage::toCompletableFuture)
                    .map(java.util.concurrent.CompletableFuture::join)
                    .collect(java.util.stream.Collectors.toList())
            );
    }

    /**
     * DTO for user statistics
     */
    public static class UserStatistics {
        public final String userId;
        public final int totalEvents;
        public final java.time.LocalDateTime lastActivity;
        public final int recentEventsCount;

        public UserStatistics(String userId, int totalEvents, 
                            java.time.LocalDateTime lastActivity, int recentEventsCount) {
            this.userId = userId;
            this.totalEvents = totalEvents;
            this.lastActivity = lastActivity;
            this.recentEventsCount = recentEventsCount;
        }

        @Override
        public String toString() {
            return String.format("UserStats{userId='%s', total=%d, recent=%d, lastActivity=%s}", 
                userId, totalEvents, recentEventsCount, lastActivity);
        }
    }
}