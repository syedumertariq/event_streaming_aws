package com.eventstreaming.actor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing User Actor instances with user affinity.
 * Ensures each user gets the same actor instance for consistency.
 */
@Component
public class UserActorRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(UserActorRegistry.class);
    
    private final ActorSystem<Void> actorSystem;
    private final ConcurrentHashMap<String, ActorRef<UserActor.Command>> userActors;
    
    @Autowired
    public UserActorRegistry(ActorSystem<Void> actorSystem) {
        try {
            this.actorSystem = actorSystem;
            this.userActors = new ConcurrentHashMap<>();
            log.info("UserActorRegistry initialized successfully with ActorSystem: {}", actorSystem.name());
        } catch (Exception e) {
            log.error("Failed to initialize UserActorRegistry", e);
            throw new RuntimeException("UserActorRegistry initialization failed", e);
        }
    }
    
    /**
     * Gets or creates a User Actor for the specified user ID.
     * Ensures user affinity by always returning the same actor instance for a user.
     */
    public CompletionStage<ActorRef<UserActor.Command>> getOrCreateUserActor(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        ActorRef<UserActor.Command> actor = userActors.computeIfAbsent(userId, this::createUserActor);
        return CompletableFuture.completedFuture(actor);
    }
    
    /**
     * Gets the current number of active User Actors.
     */
    public int getActiveActorCount() {
        return userActors.size();
    }
    
    /**
     * Removes a terminated actor from the registry.
     * This allows for recreation of actors that have been terminated.
     */
    public void removeTerminatedActor(String userId) {
        ActorRef<UserActor.Command> removed = userActors.remove(userId);
        if (removed != null) {
            log.info("Removed terminated actor for user: {}", userId);
        }
    }
    
    /**
     * Checks if an actor exists and is not terminated.
     */
    public boolean isActorActive(String userId) {
        ActorRef<UserActor.Command> actor = userActors.get(userId);
        return actor != null;
    }
    
    /**
     * Sends a message to a User Actor and returns a CompletionStage for the response.
     * This is a convenience method for async communication with User Actors.
     * Includes retry logic for terminated actors.
     */
    public <T> CompletionStage<T> askUserActor(String userId, 
                                             org.apache.pekko.japi.function.Function<ActorRef<T>, UserActor.Command> messageFactory,
                                             Duration timeout,
                                             Class<T> responseClass) {
        
        return getOrCreateUserActor(userId)
            .thenCompose(userActor -> 
                AskPattern.ask(userActor, messageFactory, timeout, actorSystem.scheduler())
                    .exceptionally(throwable -> {
                        // Check if this is an actor termination error
                        if (isActorTerminationError(throwable)) {
                            log.warn("Actor for user {} was terminated, recreating and retrying once", userId);
                            
                            // Remove the terminated actor from cache
                            userActors.remove(userId);
                            
                            // Try once more with a new actor
                            return getOrCreateUserActor(userId)
                                .thenCompose(newUserActor -> 
                                    AskPattern.ask(newUserActor, messageFactory, timeout, actorSystem.scheduler()))
                                .exceptionally(retryThrowable -> {
                                    log.error("Retry also failed for user {}", userId, retryThrowable);
                                    if (retryThrowable instanceof RuntimeException) {
                                        throw (RuntimeException) retryThrowable;
                                    }
                                    throw new RuntimeException("Retry failed", retryThrowable);
                                })
                                .toCompletableFuture()
                                .join();
                        }
                        
                        // For other errors, propagate them
                        if (throwable instanceof RuntimeException) {
                            throw (RuntimeException) throwable;
                        }
                        throw new RuntimeException(throwable);
                    })
            );
    }
    
    /**
     * Checks if the throwable indicates an actor termination error.
     */
    private boolean isActorTerminationError(Throwable throwable) {
        return (throwable.getCause() instanceof java.util.concurrent.TimeoutException &&
                throwable.getMessage() != null &&
                throwable.getMessage().contains("had already been terminated")) ||
               (throwable instanceof java.util.concurrent.TimeoutException &&
                throwable.getMessage() != null &&
                throwable.getMessage().contains("had already been terminated"));
    }
    
    private ActorRef<UserActor.Command> createUserActor(String userId) {
        log.info("Creating new UserActor for user: {}", userId);
        String actorName = "user-" + userId.replaceAll("[^a-zA-Z0-9]", "_");
        try {
            ActorRef<UserActor.Command> actor = actorSystem.systemActorOf(
                UserActor.create(userId), 
                actorName, 
                org.apache.pekko.actor.typed.Props.empty()
            );
            log.info("Successfully created UserActor for user: {} with name: {}", userId, actorName);
            return actor;
        } catch (Exception e) {
            log.error("Failed to create UserActor for user: {} with name: {}", userId, actorName, e);
            throw new RuntimeException("Failed to create UserActor for user: " + userId, e);
        }
    }
}