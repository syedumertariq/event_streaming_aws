package com.eventstreaming.service;

import com.eventstreaming.cluster.PersistentUserActor;
import com.eventstreaming.cluster.UserActorEvent;
import com.eventstreaming.model.CommunicationEvent;
import com.eventstreaming.model.UserEvent;
import com.eventstreaming.persistence.H2EventJournal;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Service for interacting with PersistentUserActor instances.
 * Handles conversion between CommunicationEvent and UserEvent models.
 */
@Service
public class PersistentUserService {
    
    private static final Logger logger = LoggerFactory.getLogger(PersistentUserService.class);
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(10);
    
    @Autowired
    private ActorSystem<?> actorSystem;
    
    @Autowired
    private ClusterSharding clusterSharding;
    
    @Autowired(required = false)
    private H2EventJournal h2EventJournal;
    
    @Autowired
    private Environment environment;
    
    private long sequenceCounter = 1L;
    
    /**
     * Process a CommunicationEvent by converting it to UserEvent and sending to PersistentUserActor.
     */
    public CompletionStage<PersistentUserActor.ProcessUserEventResponse> processEvent(CommunicationEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                new PersistentUserActor.ProcessUserEventResponse(
                    "unknown", "unknown", false, "Invalid event or userId", LocalDateTime.now()
                )
            );
        }
        
        try {
            // Convert CommunicationEvent to UserEvent
            UserEvent userEvent = convertToUserEvent(event);
            
            // Get the persistent user actor
            EntityRef<PersistentUserActor.Command> userActor = 
                clusterSharding.entityRefFor(PersistentUserActor.ENTITY_TYPE_KEY, event.getUserId());
            
            logger.info("Processing event for userId: {} via PersistentUserActor", event.getUserId());
            
            // Send the event to the persistent actor
            CompletionStage<PersistentUserActor.ProcessUserEventResponse> actorResponse = 
                AskPattern.ask(userActor, 
                    (ActorRef<PersistentUserActor.ProcessUserEventResponse> replyTo) -> 
                        new PersistentUserActor.ProcessUserEvent(userEvent, replyTo),
                    ASK_TIMEOUT, 
                    actorSystem.scheduler())
                    .exceptionally(throwable -> {
                        logger.error("Failed to process event for userId: {}", event.getUserId(), throwable);
                        return new PersistentUserActor.ProcessUserEventResponse(
                            event.getUserId(), userEvent.getEventId(), false, 
                            "Processing failed: " + throwable.getMessage(), LocalDateTime.now()
                        );
                    });
            
            // Also store in H2 if using isolated profile and H2 is available
            boolean isIsolatedProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("isolated");
            if (isIsolatedProfile && h2EventJournal != null) {
                try {
                    String persistenceId = PersistentUserActor.ENTITY_TYPE_KEY.name() + "|" + event.getUserId();
                    UserActorEvent.UserEventProcessed h2Event = UserActorEvent.UserEventProcessed.from(userEvent);
                    
                    long sequenceNr = getNextSequenceNr(persistenceId);
                    h2EventJournal.persistEvent(persistenceId, sequenceNr, h2Event);
                    
                    logger.debug("Also stored event in H2 for isolated profile: {} seq={}", persistenceId, sequenceNr);
                } catch (Exception e) {
                    logger.warn("Failed to store event in H2 (non-critical): {}", e.getMessage());
                }
            }
            
            return actorResponse;
                
        } catch (Exception e) {
            logger.error("Error processing event for userId: {}", event.getUserId(), e);
            return CompletableFuture.completedFuture(
                new PersistentUserActor.ProcessUserEventResponse(
                    event.getUserId(), "unknown", false, 
                    "Error: " + e.getMessage(), LocalDateTime.now()
                )
            );
        }
    }
    
    /**
     * Process multiple events in parallel.
     */
    public CompletionStage<Void> processEvents(List<CommunicationEvent> events) {
        List<CompletionStage<PersistentUserActor.ProcessUserEventResponse>> futures = 
            events.stream()
                .map(this::processEvent)
                .toList();
        
        return CompletableFuture.allOf(
            futures.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new)
        ).thenRun(() -> {
            logger.info("Processed {} events", events.size());
        });
    }
    
    /**
     * Get user statistics from the persistent actor.
     */
    public CompletionStage<PersistentUserActor.UserStatsResponse> getUserStats(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            EntityRef<PersistentUserActor.Command> userActor = 
                clusterSharding.entityRefFor(PersistentUserActor.ENTITY_TYPE_KEY, userId);
            
            logger.info("Getting stats for userId: {} via PersistentUserActor", userId);
            
            return AskPattern.ask(userActor, 
                PersistentUserActor.GetUserStats::new,
                ASK_TIMEOUT, 
                actorSystem.scheduler())
                .exceptionally(throwable -> {
                    logger.error("Failed to get stats for userId: {}", userId, throwable);
                    return null;
                });
                
        } catch (Exception e) {
            logger.error("Error getting stats for userId: {}", userId, e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Convert CommunicationEvent to UserEvent.
     */
    private UserEvent convertToUserEvent(CommunicationEvent event) {
        UserEvent userEvent = new UserEvent();
        userEvent.setUserId(event.getUserId());
        userEvent.setEventType(event.getEventType() != null ? event.getEventType().toString() : "COMMUNICATION");
        userEvent.setContactId(1L); // Default contact ID since CommunicationEvent doesn't have this field
        userEvent.setEventId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString());
        userEvent.setSource("CommunicationEvent");
        
        // Convert Instant to LocalDateTime
        if (event.getTimestamp() != null) {
            userEvent.setTimestamp(LocalDateTime.ofInstant(event.getTimestamp(), java.time.ZoneId.systemDefault()));
        } else {
            userEvent.setTimestamp(LocalDateTime.now());
        }
        
        return userEvent;
    }
    
    /**
     * Get the next sequence number for a persistence ID.
     */
    private synchronized long getNextSequenceNr(String persistenceId) {
        try {
            if (h2EventJournal != null) {
                long currentMax = h2EventJournal.getHighestSequenceNr(persistenceId);
                return currentMax + 1;
            }
            return sequenceCounter++;
        } catch (Exception e) {
            logger.warn("Failed to get sequence number for {}, using counter: {}", persistenceId, e.getMessage());
            return sequenceCounter++;
        }
    }
}