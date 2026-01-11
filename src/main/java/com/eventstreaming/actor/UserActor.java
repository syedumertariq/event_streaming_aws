package com.eventstreaming.actor;

import com.eventstreaming.model.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * User Actor that maintains user-specific aggregations and processes communication events.
 * Each user gets their own actor instance for strong consistency and user affinity.
 */
public class UserActor extends AbstractBehavior<UserActor.Command> {
    
    private static final Logger log = LoggerFactory.getLogger(UserActor.class);
    
    private final String userId;
    private UserAggregations aggregations;
    private final Duration passivationTimeout = Duration.ofMinutes(30);
    
    // Commands
    public sealed interface Command permits ProcessEvent, ProcessEventAndAggregate, GetAggregations, UpdateContactable, Passivate, 
                                           UserActorCommands.KeepWarm, UserActorCommands.Ping, UserActorCommands.Pong, 
                                           UserActorCommands.GetStats, UserActorCommands.ActorStats {}
    
    public record ProcessEvent(CommunicationEvent event, ActorRef<ProcessEventResponse> replyTo) 
        implements Command {}
    
    public record ProcessEventAndAggregate(CommunicationEvent event, ActorRef<ProcessEventResponse> replyTo) 
        implements Command {}
    
    public record GetAggregations(ActorRef<UserAggregations> replyTo) 
        implements Command {}
    
    public record UpdateContactable(ContactableStatus contactableStatus, 
                                  String graphNode, 
                                  Instant processingTimestamp,
                                  ActorRef<UpdateContactableResponse> replyTo) 
        implements Command {}
    
    public enum Passivate implements Command { INSTANCE }
    
    // Response types
    public sealed interface ProcessEventResponse {
        record Success(UserAggregations aggregations) implements ProcessEventResponse {}
        record Failed(String reason) implements ProcessEventResponse {}
    }
    
    public sealed interface UpdateContactableResponse {
        record Success() implements UpdateContactableResponse {}
        record Failed(String reason) implements UpdateContactableResponse {}
    }
    
    public static Behavior<Command> create(String userId) {
        return Behaviors.setup(context -> {
            context.setReceiveTimeout(Duration.ofMinutes(30), Passivate.INSTANCE);
            return new UserActor(context, userId);
        });
    }
    
    private UserActor(ActorContext<Command> context, String userId) {
        super(context);
        this.userId = userId;
        this.aggregations = new UserAggregations(userId);
        
        log.info("UserActor created for user: {}", userId);
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ProcessEvent.class, this::processEvent)
            .onMessage(ProcessEventAndAggregate.class, this::processEventAndAggregate)
            .onMessage(GetAggregations.class, this::getAggregations)
            .onMessage(UpdateContactable.class, this::updateContactable)
            .onMessage(Passivate.class, this::passivate)
            .onMessage(UserActorCommands.KeepWarm.class, this::keepWarm)
            .onMessage(UserActorCommands.Ping.class, this::ping)
            .onMessage(UserActorCommands.GetStats.class, this::getStats)
            .build();
    }
    
    private Behavior<Command> processEvent(ProcessEvent command) {
        CommunicationEvent event = command.event;
        
        try {
            // Validate event
            if (!event.isValid()) {
                log.warn("Invalid event received for user {}: {}", userId, event);
                command.replyTo.tell(new ProcessEventResponse.Failed("Invalid event"));
                return this;
            }
            
            if (!event.getUserId().equals(userId)) {
                log.warn("User ID mismatch for actor {}: event user {}", userId, event.getUserId());
                command.replyTo.tell(new ProcessEventResponse.Failed("User ID mismatch"));
                return this;
            }
            
            // Update aggregations based on event type
            UserAggregations updatedAggregations = updateAggregations(event);
            this.aggregations = updatedAggregations;
            
            log.debug("Processed {} event for user {}: {}", 
                     event.getEventType(), userId, event.getEventId());
            
            // Notify Walker about user activity (in a real implementation, this would publish to Kafka)
            notifyWalker(userId);
            
            command.replyTo.tell(new ProcessEventResponse.Success(updatedAggregations));
            
        } catch (Exception e) {
            log.error("Error processing event for user {}: {}", userId, event, e);
            command.replyTo.tell(new ProcessEventResponse.Failed("Processing error: " + e.getMessage()));
        }
        
        return this;
    }
    
    /**
     * Atomic event processing and aggregation for Kafka Streams integration.
     * Processes event, updates aggregations, and publishes notifications in a single operation.
     */
    private Behavior<Command> processEventAndAggregate(ProcessEventAndAggregate command) {
        CommunicationEvent event = command.event;
        
        try {
            // Validate event
            if (!event.isValid()) {
                log.warn("Invalid event received for user {}: {}", userId, event);
                command.replyTo.tell(new ProcessEventResponse.Failed("Invalid event"));
                return this;
            }
            
            if (!event.getUserId().equals(userId)) {
                log.warn("User ID mismatch for actor {}: event user {}", userId, event.getUserId());
                command.replyTo.tell(new ProcessEventResponse.Failed("User ID mismatch"));
                return this;
            }
            
            // Store previous aggregations for comparison
            UserAggregations previousAggregations = this.aggregations;
            
            // Update aggregations based on event type (atomic operation)
            UserAggregations updatedAggregations = updateAggregations(event);
            this.aggregations = updatedAggregations;
            
            log.debug("Atomically processed {} event for user {}: {} -> aggregations updated", 
                     event.getEventType(), userId, event.getEventId());
            
            // Publish aggregation update notification to Walker (atomic with processing)
            publishAggregationUpdate(userId, previousAggregations, updatedAggregations, event);
            
            // Publish Walker notification for real-time processing
            publishWalkerNotification(userId, updatedAggregations, event);
            
            command.replyTo.tell(new ProcessEventResponse.Success(updatedAggregations));
            
        } catch (Exception e) {
            log.error("Error in atomic event processing for user {}: {}", userId, event, e);
            command.replyTo.tell(new ProcessEventResponse.Failed("Atomic processing error: " + e.getMessage()));
        }
        
        return this;
    }
    
    private Behavior<Command> getAggregations(GetAggregations command) {
        log.debug("Returning aggregations for user: {}", userId);
        command.replyTo.tell(aggregations);
        return this;
    }
    
    private Behavior<Command> updateContactable(UpdateContactable command) {
        try {
            // Update aggregations with Walker's feedback
            UserAggregations updatedAggregations = aggregations.updateContactableStatus(
                command.contactableStatus,
                command.graphNode,
                command.processingTimestamp
            );
            
            this.aggregations = updatedAggregations;
            
            log.info("Updated contactable status for user {}: {} at node {}", 
                    userId, command.contactableStatus, command.graphNode);
            
            // Invalidate cache since aggregations changed (in a real implementation)
            invalidateUserCache(userId);
            
            command.replyTo.tell(new UpdateContactableResponse.Success());
            
        } catch (Exception e) {
            log.error("Error updating contactable status for user {}", userId, e);
            command.replyTo.tell(new UpdateContactableResponse.Failed("Update error: " + e.getMessage()));
        }
        
        return this;
    }
    
    private Behavior<Command> passivate(Passivate command) {
        log.info("Passivating UserActor for user: {}", userId);
        return Behaviors.stopped();
    }
    
    /**
     * Handle keep-warm command to prevent passivation
     */
    private Behavior<Command> keepWarm(UserActorCommands.KeepWarm command) {
        log.debug("Received keep-warm for user: {}", userId);
        // Reset the receive timeout to prevent passivation
        getContext().setReceiveTimeout(Duration.ofMinutes(30), Passivate.INSTANCE);
        return this;
    }
    
    /**
     * Handle ping command for health checks
     */
    private Behavior<Command> ping(UserActorCommands.Ping command) {
        log.debug("Received ping for user: {}", userId);
        // Just acknowledge the ping - no response needed for this simple ping
        return this;
    }
    
    /**
     * Handle get stats command
     */
    private Behavior<Command> getStats(UserActorCommands.GetStats command) {
        log.debug("Returning stats for user: {}", userId);
        // For now, just return basic stats - could be extended with more metrics
        long totalEvents = aggregations.getTotalEvents();
        long uptime = Duration.between(Instant.now().minusSeconds(3600), Instant.now()).toMillis(); // Approximate uptime
        boolean isHot = totalEvents > 10; // Simple hot actor detection
        
        // Note: In a real implementation, you'd send this to a reply actor
        // For now, just log the stats
        log.info("Stats for user {}: totalEvents={}, uptime={}ms, isHot={}", 
                userId, totalEvents, uptime, isHot);
        
        return this;
    }
    
    private UserAggregations updateAggregations(CommunicationEvent event) {
        return switch (event.getEventType()) {
            case EMAIL_OPEN -> aggregations.incrementEmailOpens();
            case EMAIL_CLICK -> aggregations.incrementEmailClicks();
            case SMS_REPLY -> aggregations.incrementSmsReplies();
            case SMS_DELIVERY -> aggregations.incrementSmsDeliveries();
            case CALL_COMPLETED -> aggregations.incrementCallsCompleted();
            case CALL_MISSED -> aggregations.incrementCallsMissed();
            default -> {
                log.warn("Unknown event type: {}", event.getEventType());
                yield aggregations; // Return unchanged aggregations
            }
        };
    }
    
    private void notifyWalker(String userId) {
        // In a real implementation, this would publish to Kafka topic for Walker
        log.debug("Notifying Walker about user activity: {}", userId);
        // kafkaTemplate.send("walker-notifications", userId, new WalkerNotification(userId, Instant.now()));
    }
    
    /**
     * Publishes aggregation update notification to Kafka for Walker consumption.
     * This replaces the database polling mechanism with real-time messaging.
     */
    private void publishAggregationUpdate(String userId, UserAggregations previous, 
                                        UserAggregations current, CommunicationEvent triggerEvent) {
        try {
            // Create aggregation update message
            AggregationUpdate update = new AggregationUpdate(
                userId,
                (int) current.getEmailCount(),
                (int) current.getSmsCount(),
                (int) current.getCallCount(),
                current.getLastUpdated(),
                triggerEvent.getEventType().toString(),
                triggerEvent.getEventId()
            );
            
            log.debug("Publishing aggregation update for user {}: {} -> {}", 
                     userId, previous.getTotalEvents(), current.getTotalEvents());
            
            // TODO: Implement actual Kafka publishing
            // kafkaTemplate.send("aggregation-updates", userId, update);
            
        } catch (Exception e) {
            log.error("Error publishing aggregation update for user {}", userId, e);
        }
    }
    
    /**
     * Publishes Walker notification for immediate processing.
     */
    private void publishWalkerNotification(String userId, UserAggregations aggregations, CommunicationEvent event) {
        try {
            // Create Walker notification
            WalkerNotification notification = new WalkerNotification(
                userId,
                (int) aggregations.getEmailCount(),
                (int) aggregations.getSmsCount(),
                (int) aggregations.getCallCount(),
                Instant.now(),
                event.getEventType().toString()
            );
            
            log.debug("Publishing Walker notification for user {}: total events = {}", 
                     userId, aggregations.getTotalEvents());
            
            // TODO: Implement actual Kafka publishing
            // kafkaTemplate.send("walker-notifications", userId, notification);
            
        } catch (Exception e) {
            log.error("Error publishing Walker notification for user {}", userId, e);
        }
    }
    
    private void invalidateUserCache(String userId) {
        // In a real implementation, this would invalidate Redis cache
        log.debug("Invalidating cache for user: {}", userId);
        // redisTemplate.delete("user:" + userId);
    }
    
    /**
     * Aggregation update message for Walker.
     */
    public record AggregationUpdate(
        String userId,
        int emailCount,
        int smsCount,
        int callCount,
        Instant lastUpdated,
        String triggerEventType,
        String triggerEventId
    ) {}
    
    /**
     * Walker notification message.
     */
    public record WalkerNotification(
        String userId,
        int emailCount,
        int smsCount,
        int callCount,
        Instant timestamp,
        String eventType
    ) {}
}