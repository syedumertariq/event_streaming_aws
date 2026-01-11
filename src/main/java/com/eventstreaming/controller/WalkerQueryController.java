package com.eventstreaming.controller;

import com.eventstreaming.actor.UserActorRegistry;
import com.eventstreaming.model.UserAggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST API for Walker to query user aggregations from Pekko actors.
 * Maintains compatibility with Walker's existing Redis caching strategy while replacing database queries.
 * Only loads when Redis profiles are active.
 */
@RestController
@RequestMapping("/api/walker")
@Profile({"redis", "local-redis", "cluster-mysql", "isolated"})
public class WalkerQueryController {
    
    private static final Logger log = LoggerFactory.getLogger(WalkerQueryController.class);
    private static final String REDIS_USER_PREFIX = "user_aggregations:";
    private static final int CACHE_TTL_MINUTES = 30;
    
    private final UserActorRegistry userActorRegistry;
    private final RedisTemplate<String, UserAggregations> redisTemplate;
    
    @Autowired
    public WalkerQueryController(UserActorRegistry userActorRegistry,
                                RedisTemplate<String, UserAggregations> redisTemplate) {
        this.userActorRegistry = userActorRegistry;
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Gets user aggregations directly from UserActor.
     * Primary endpoint for Walker to query latest aggregation data.
     */
    @GetMapping("/users/{userId}/aggregations")
    public CompletableFuture<ResponseEntity<UserAggregations>> getUserAggregations(@PathVariable String userId) {
        log.debug("Walker querying aggregations for user: {}", userId);
        
        return userActorRegistry.askUserActor(
            userId,
            replyTo -> new com.eventstreaming.actor.UserActor.GetAggregations(replyTo),
            Duration.ofSeconds(5),
            UserAggregations.class
        ).thenApply(aggregations -> {
            log.debug("Retrieved aggregations for user {}: total events = {}", 
                     userId, aggregations.getTotalEvents());
            return ResponseEntity.ok(aggregations);
        }).exceptionally(throwable -> {
            log.error("Error retrieving aggregations for user {}", userId, throwable);
            return ResponseEntity.internalServerError().build();
        }).toCompletableFuture();
    }
    
    /**
     * Gets user aggregations with Redis caching integration.
     * Maintains Walker's existing caching strategy while using actors as the source of truth.
     */
    @GetMapping("/users/{userId}/aggregations/cached")
    public CompletableFuture<ResponseEntity<UserAggregations>> getCachedUserAggregations(@PathVariable String userId) {
        log.debug("Walker querying cached aggregations for user: {}", userId);
        
        // Check Redis cache first (Walker's existing pattern)
        String cacheKey = REDIS_USER_PREFIX + userId;
        UserAggregations cachedAggregations = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedAggregations != null) {
            log.debug("Cache hit for user {}: returning cached aggregations", userId);
            return CompletableFuture.completedFuture(ResponseEntity.ok(cachedAggregations));
        }
        
        // Cache miss - query actor and update cache
        log.debug("Cache miss for user {}: querying actor and updating cache", userId);
        
        return userActorRegistry.askUserActor(
            userId,
            replyTo -> new com.eventstreaming.actor.UserActor.GetAggregations(replyTo),
            Duration.ofSeconds(5),
            UserAggregations.class
        ).thenApply(aggregations -> {
            // Update Redis cache with TTL
            redisTemplate.opsForValue().set(cacheKey, aggregations, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            log.debug("Updated cache and retrieved aggregations for user {}: total events = {}", 
                     userId, aggregations.getTotalEvents());
            return ResponseEntity.ok(aggregations);
        }).exceptionally(throwable -> {
            log.error("Error retrieving cached aggregations for user {}", userId, throwable);
            return ResponseEntity.internalServerError().build();
        }).toCompletableFuture();
    }
    
    /**
     * Batch query for multiple users' aggregations.
     * Optimized for Walker's bulk processing scenarios.
     */
    @PostMapping("/users/aggregations/batch")
    public CompletableFuture<ResponseEntity<Map<String, UserAggregations>>> getBatchUserAggregations(
            @RequestBody List<String> userIds) {
        
        log.debug("Walker batch querying aggregations for {} users", userIds.size());
        
        if (userIds.isEmpty()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        }
        
        if (userIds.size() > 100) {
            log.warn("Batch query too large: {} users (max 100)", userIds.size());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        }
        
        // Query all users in parallel
        CompletableFuture<?>[] futures = userIds.stream()
            .map(userId -> userActorRegistry.askUserActor(
                userId,
                replyTo -> new com.eventstreaming.actor.UserActor.GetAggregations(replyTo),
                Duration.ofSeconds(5),
                UserAggregations.class
            ).thenApply(aggregations -> Map.entry(userId, aggregations))
             .exceptionally(throwable -> {
                 log.error("Error querying aggregations for user {} in batch", userId, throwable);
                 return Map.entry(userId, new UserAggregations(userId)); // Return empty aggregations on error
             }))
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures)
            .thenApply(v -> {
                Map<String, UserAggregations> results = new java.util.HashMap<>();
                for (CompletableFuture<?> future : futures) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map.Entry<String, UserAggregations> entry = (Map.Entry<String, UserAggregations>) future.get();
                        results.put(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        log.error("Error processing batch result", e);
                    }
                }
                
                log.debug("Batch query completed: retrieved aggregations for {}/{} users", 
                         results.size(), userIds.size());
                return ResponseEntity.ok(results);
            })
            .exceptionally(throwable -> {
                log.error("Error in batch aggregations query", throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
    
    /**
     * Invalidates Redis cache for a specific user.
     * Useful for Walker to force fresh data retrieval.
     */
    @DeleteMapping("/users/{userId}/cache")
    public ResponseEntity<Void> invalidateUserCache(@PathVariable String userId) {
        log.debug("Walker invalidating cache for user: {}", userId);
        
        try {
            String cacheKey = REDIS_USER_PREFIX + userId;
            Boolean deleted = redisTemplate.delete(cacheKey);
            
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Successfully invalidated cache for user: {}", userId);
            } else {
                log.debug("No cache entry found for user: {}", userId);
            }
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error invalidating cache for user {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Warms up the cache for a specific user.
     * Queries the actor and stores result in Redis for faster subsequent access.
     */
    @PostMapping("/users/{userId}/cache/warm")
    public CompletableFuture<ResponseEntity<Void>> warmUserCache(@PathVariable String userId) {
        log.debug("Walker warming cache for user: {}", userId);
        
        return userActorRegistry.askUserActor(
            userId,
            replyTo -> new com.eventstreaming.actor.UserActor.GetAggregations(replyTo),
            Duration.ofSeconds(5),
            UserAggregations.class
        ).thenApply(aggregations -> {
            // Store in Redis cache
            String cacheKey = REDIS_USER_PREFIX + userId;
            redisTemplate.opsForValue().set(cacheKey, aggregations, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            log.debug("Successfully warmed cache for user {}: total events = {}", 
                     userId, aggregations.getTotalEvents());
            return ResponseEntity.ok().<Void>build();
        }).exceptionally(throwable -> {
            log.error("Error warming cache for user {}", userId, throwable);
            return ResponseEntity.internalServerError().build();
        }).toCompletableFuture();
    }
    
    /**
     * Gets cache statistics for monitoring.
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<CacheStats> getCacheStats() {
        try {
            // Get cache key count (approximate)
            Long keyCount = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> {
                return connection.dbSize();
            });
            
            CacheStats stats = new CacheStats(
                keyCount != null ? keyCount : 0L,
                CACHE_TTL_MINUTES,
                REDIS_USER_PREFIX
            );
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving cache stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Health check endpoint for Walker integration.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthStatus> getHealth() {
        try {
            // Test actor system
            boolean actorSystemHealthy = userActorRegistry != null;
            
            // Test Redis connection
            boolean redisHealthy = false;
            try {
                redisTemplate.opsForValue().get("health-check");
                redisHealthy = true;
            } catch (Exception e) {
                log.warn("Redis health check failed", e);
            }
            
            HealthStatus status = new HealthStatus(
                actorSystemHealthy && redisHealthy,
                actorSystemHealthy,
                redisHealthy,
                System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error in health check", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Cache statistics record.
     */
    public record CacheStats(
        long totalKeys,
        int ttlMinutes,
        String keyPrefix
    ) {}
    
    /**
     * Health status record.
     */
    public record HealthStatus(
        boolean healthy,
        boolean actorSystemHealthy,
        boolean redisHealthy,
        long timestamp
    ) {}
}