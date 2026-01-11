package com.eventstreaming.cluster;

import com.eventstreaming.model.UserEvent;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Persistent UserActor that stores events in the event journal.
 * Each user gets their own persistent actor that maintains event counts by type.
 */
public class PersistentUserActor extends EventSourcedBehavior<PersistentUserActor.Command, UserActorEvent, UserActorState> {
    
    private static final Logger logger = LoggerFactory.getLogger(PersistentUserActor.class);
    
    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY = 
        EntityTypeKey.create(Command.class, "PersistentUserActor");
    
    private final String userId;
    
    // Commands
    public interface Command extends Serializable {}
    
    public static final class ProcessUserEvent implements Command {
        public final UserEvent userEvent;
        public final ActorRef<ProcessUserEventResponse> replyTo;
        
        public ProcessUserEvent(UserEvent userEvent, ActorRef<ProcessUserEventResponse> replyTo) {
            this.userEvent = userEvent;
            this.replyTo = replyTo;
        }
    }
    
    public static final class GetUserStats implements Command {
        public final ActorRef<UserStatsResponse> replyTo;
        
        public GetUserStats(ActorRef<UserStatsResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }
    
    // Responses
    public interface Response extends Serializable {}
    
    public static final class ProcessUserEventResponse implements Response {
        public final String userId;
        public final String eventId;
        public final boolean success;
        public final String message;
        public final LocalDateTime processedAt;
        
        public ProcessUserEventResponse(String userId, String eventId, boolean success, String message, LocalDateTime processedAt) {
            this.userId = userId;
            this.eventId = eventId;
            this.success = success;
            this.message = message;
            this.processedAt = processedAt;
        }
    }
    
    public static final class UserStatsResponse implements Response {
        public final String userId;
        public final UserActorState state;
        
        public UserStatsResponse(String userId, UserActorState state) {
            this.userId = userId;
            this.state = state;
        }
    }
    
    public static Behavior<Command> create(String userId) {
        return Behaviors.setup(context -> new PersistentUserActor(context, userId));
    }
    
    private PersistentUserActor(ActorContext<Command> context, String userId) {
        super(PersistenceId.of(ENTITY_TYPE_KEY.name(), userId));
        this.userId = userId;
        logger.info("Creating PersistentUserActor for userId: {}", userId);
    }
    
    @Override
    public UserActorState emptyState() {
        return new UserActorState(userId);
    }
    
    @Override
    public CommandHandler<Command, UserActorEvent, UserActorState> commandHandler() {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(ProcessUserEvent.class, this::onProcessUserEvent)
            .onCommand(GetUserStats.class, this::onGetUserStats)
            .build();
    }
    
    @Override
    public EventHandler<UserActorState, UserActorEvent> eventHandler() {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(UserActorEvent.UserEventProcessed.class, this::onUserEventProcessed)
            .onEvent(UserActorEvent.UserEventCountUpdated.class, (state, event) -> state)
            .build();
    }
    
    private Effect<UserActorEvent, UserActorState> onProcessUserEvent(UserActorState state, ProcessUserEvent command) {
        logger.info("Processing UserEvent for userId: {}, eventType: {}, contactId: {}", 
                   userId, command.userEvent.getEventType(), command.userEvent.getContactId());
        
        // Create the event to persist
        UserActorEvent.UserEventProcessed event = UserActorEvent.UserEventProcessed.from(command.userEvent);
        
        // Persist the event and reply when done
        return Effect()
            .persist(event)
            .thenReply(command.replyTo, newState -> {
                logger.info("Successfully persisted event for userId: {}, totalEvents: {}", 
                           userId, newState.getTotalEvents());
                
                return new ProcessUserEventResponse(
                    userId,
                    command.userEvent.getEventId(),
                    true,
                    "Event processed and persisted successfully",
                    LocalDateTime.now()
                );
            });
    }
    
    private Effect<UserActorEvent, UserActorState> onGetUserStats(UserActorState state, GetUserStats command) {
        logger.info("Getting stats for userId: {}, totalEvents: {}", userId, state.getTotalEvents());
        
        return Effect().reply(command.replyTo, new UserStatsResponse(userId, state));
    }
    
    private UserActorState onUserEventProcessed(UserActorState state, UserActorEvent.UserEventProcessed event) {
        logger.info("Applying UserEventProcessed: userId={}, eventType={}, totalEvents before: {}", 
                   event.getUserId(), event.getEventType(), state.getTotalEvents());
        
        UserActorState newState = state.applyEvent(event);
        
        logger.info("Applied UserEventProcessed: userId={}, eventType={}, totalEvents after: {}", 
                   event.getUserId(), event.getEventType(), newState.getTotalEvents());
        
        return newState;
    }
    
    @Override
    public RetentionCriteria retentionCriteria() {
        // OPTIMIZED: More frequent snapshots for faster recovery
        return RetentionCriteria.snapshotEvery(50, 5)  // Snapshot every 50 events, keep 5
            .withDeleteEventsOnSnapshot();  // Delete old events after snapshot
    }
}