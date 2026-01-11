package com.eventstreaming;

import com.eventstreaming.actor.UserActor;
import com.eventstreaming.actor.UserActorRegistry;
import com.eventstreaming.dto.ContactableUpdate;
import com.eventstreaming.model.UserAggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Real-time User Aggregation Controller using Pekko Typed Actors
 * Provides Walker integration and real-time event processing
 */
@RestController
@RequestMapping("/api/v1/users")
public class WorkingUserController {

    private static final Logger log = LoggerFactory.getLogger(WorkingUserController.class);
    
    private final UserActorRegistry userActorRegistry;

    @Autowired
    public WorkingUserController(UserActorRegistry userActorRegistry) {
        this.userActorRegistry = userActorRegistry;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        int activeActors = userActorRegistry.getActiveActorCount();
        String status = String.format("Pekko User Controller is working! Active actors: %d", activeActors);
        return ResponseEntity.ok(status);
    }

    /**
     * Get user aggregations with real-time Pekko actor processing
     */
    @GetMapping("/{userId}/aggregations")
    public CompletableFuture<ResponseEntity<UserAggregations>> getUserAggregations(@PathVariable String userId) {
        log.debug("Getting real-time aggregations for user: {}", userId);

        try {
            return userActorRegistry.askUserActor(
                    userId,
                    replyTo -> new UserActor.GetAggregations(replyTo),
                    Duration.ofSeconds(5),
                    UserAggregations.class
            ).thenApply(aggregations -> {
                log.debug("Retrieved aggregations for user {}: {}", userId, aggregations);
                return ResponseEntity.ok(aggregations);
            }).exceptionally(throwable -> {
                // Check for actor termination errors
                if (isActorTerminationError(throwable)) {
                    log.warn("Actor for user {} was terminated during request, returning service unavailable", userId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "2")
                        .body(null);
                }
                
                // Check for timeout errors
                if (throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
                    log.warn("Request timeout for user {}, returning service unavailable", userId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "1")
                        .body(null);
                }
                
                log.error("Failed to get aggregations for user: " + userId, throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }).toCompletableFuture();
        } catch (Exception e) {
            log.error("Error getting aggregations for user: " + userId, e);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        }
    }

    /**
     * Get real-time aggregations (bypasses cache)
     */
    @GetMapping("/{userId}/aggregations/realtime")
    public CompletableFuture<ResponseEntity<UserAggregations>> getRealtimeAggregations(@PathVariable String userId) {
        log.debug("Getting real-time aggregations for user: {}", userId);
        
        return getUserAggregations(userId);
    }

    /**
     * Walker integration - Update contactable status
     */
    @PostMapping("/{userId}/aggregations/contactable")
    public CompletableFuture<ResponseEntity<String>> updateContactableStatus(
            @PathVariable String userId,
            @RequestBody ContactableUpdate update) {

        log.info("Updating contactable status for user {}: {}", userId, update);

        try {
            if (update.getContactableStatus() == null) {
                return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Missing contactable status"));
            }

            return userActorRegistry.askUserActor(
                    userId,
                    replyTo -> new UserActor.UpdateContactable(
                            update.getContactableStatus(),
                            update.getGraphNode(),
                            update.getProcessingTimestamp(),
                            replyTo),
                    Duration.ofSeconds(5),
                    UserActor.UpdateContactableResponse.class
            ).thenApply(result -> {
                if (result instanceof UserActor.UpdateContactableResponse.Success) {
                    log.info("Successfully updated contactable status for user: {}", userId);
                    return ResponseEntity.ok("Contactable status updated successfully");
                } else if (result instanceof UserActor.UpdateContactableResponse.Failed failed) {
                    log.error("Failed to update contactable status for user {}: {}", userId, failed.reason());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to update contactable status: " + failed.reason());
                } else {
                    log.error("Unexpected response type for user {}: {}", userId, result.getClass());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Unexpected error updating contactable status");
                }
            }).exceptionally(throwable -> {
                // Check for actor termination errors
                if (isActorTerminationError(throwable)) {
                    log.warn("Actor for user {} was terminated during contactable status update, returning service unavailable", userId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "2")
                        .body("Actor temporarily unavailable, please retry");
                }
                
                // Check for timeout errors
                if (throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
                    log.warn("Request timeout for user {} during contactable status update, returning service unavailable", userId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "1")
                        .body("Request timeout, please retry");
                }
                
                log.error("Failed to update contactable status for user: " + userId, throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating contactable status: " + throwable.getMessage());
            }).toCompletableFuture();

        } catch (Exception e) {
            log.error("Error updating contactable status for user: " + userId, e);
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating contactable status: " + e.getMessage()));
        }
    }

    /**
     * Get actor statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ActorStats> getStats() {
        ActorStats stats = new ActorStats(userActorRegistry.getActiveActorCount());
        return ResponseEntity.ok(stats);
    }

    /**
     * Checks if the throwable indicates an actor termination error.
     */
    private boolean isActorTerminationError(Throwable throwable) {
        if (throwable == null) return false;
        
        String message = throwable.getMessage();
        if (message != null && message.contains("had already been terminated")) {
            return true;
        }
        
        Throwable cause = throwable.getCause();
        if (cause instanceof java.util.concurrent.TimeoutException) {
            String causeMessage = cause.getMessage();
            return causeMessage != null && causeMessage.contains("had already been terminated");
        }
        
        return false;
    }

    // Stats DTO
    public record ActorStats(int activeActorCount) {}
}