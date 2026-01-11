package com.eventstreaming.cluster;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import java.io.Serializable;

import com.eventstreaming.model.CommunicationEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cluster-aware User Actor that handles user-specific events in a sharded manner.
 * Each user gets their own actor instance distributed across the cluster.
 */
public class ClusterUserActor extends AbstractBehavior<ClusterUserActor.Command> {

    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY = 
        EntityTypeKey.create(Command.class, "UserActor");

    // Actor state
    private final String userId;
    private final List<CommunicationEvent> events = new ArrayList<>();
    private LocalDateTime lastActivity = LocalDateTime.now();
    private int totalEvents = 0;

    // Commands
    public interface Command extends Serializable {}

    public static final class ProcessEvent implements Command {
        public final CommunicationEvent event;
        public final ActorRef<EventProcessed> replyTo;

        @JsonCreator
        public ProcessEvent(@JsonProperty("event") CommunicationEvent event, 
                          @JsonProperty("replyTo") ActorRef<EventProcessed> replyTo) {
            this.event = event;
            this.replyTo = replyTo;
        }
    }

    public static final class GetUserStats implements Command {
        public final ActorRef<UserStats> replyTo;

        @JsonCreator
        public GetUserStats(@JsonProperty("replyTo") ActorRef<UserStats> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class PassivateUser implements Command {
        public static final PassivateUser INSTANCE = new PassivateUser();
        private PassivateUser() {}
    }

    // Responses
    public static final class EventProcessed implements Serializable {
        public final String userId;
        public final boolean success;
        public final String message;

        @JsonCreator
        public EventProcessed(@JsonProperty("userId") String userId, 
                            @JsonProperty("success") boolean success,
                            @JsonProperty("message") String message) {
            this.userId = userId;
            this.success = success;
            this.message = message;
        }
    }

    public static final class UserStats implements Serializable {
        public final String userId;
        public final int totalEvents;
        public final LocalDateTime lastActivity;
        public final int recentEventsCount;

        @JsonCreator
        public UserStats(@JsonProperty("userId") String userId, 
                        @JsonProperty("totalEvents") int totalEvents,
                        @JsonProperty("lastActivity") LocalDateTime lastActivity,
                        @JsonProperty("recentEventsCount") int recentEventsCount) {
            this.userId = userId;
            this.totalEvents = totalEvents;
            this.lastActivity = lastActivity;
            this.recentEventsCount = recentEventsCount;
        }
    }

    public static Behavior<Command> create(String userId) {
        return Behaviors.setup(context -> {
            context.getLog().info("Starting ClusterUserActor for user: {}", userId);
            
            // Set up passivation timer
            context.setReceiveTimeout(Duration.ofMinutes(30), PassivateUser.INSTANCE);
            
            return new ClusterUserActor(context, userId);
        });
    }

    private ClusterUserActor(ActorContext<Command> context, String userId) {
        super(context);
        this.userId = userId;
        getContext().getLog().info("ClusterUserActor initialized for user: {}", userId);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ProcessEvent.class, this::onProcessEvent)
            .onMessage(GetUserStats.class, this::onGetUserStats)
            .onMessage(PassivateUser.class, this::onPassivateUser)
            .build();
    }

    private Behavior<Command> onProcessEvent(ProcessEvent command) {
        try {
            CommunicationEvent event = command.event;
            
            getContext().getLog().info("🔄 Processing event for user {}: eventId={}, type={}", 
                userId, event.getEventId(), event.getEventType());
            
            // Validate event
            if (event == null) {
                getContext().getLog().warn("❌ Null event received for user {}", userId);
                command.replyTo.tell(new EventProcessed(userId, false, "Null event"));
                return this;
            }
            
            if (!userId.equals(event.getUserId())) {
                getContext().getLog().warn("❌ Event userId {} doesn't match actor userId {}", 
                    event.getUserId(), userId);
                command.replyTo.tell(new EventProcessed(userId, false, 
                    "Event userId mismatch"));
                return this;
            }

            // Process the event
            events.add(event);
            totalEvents++;
            lastActivity = LocalDateTime.now();

            // Keep only recent events in memory (last 100)
            if (events.size() > 100) {
                events.remove(0);
            }

            getContext().getLog().info("✅ Successfully processed event for user {}: {} (total: {})", 
                userId, event.getEventType(), totalEvents);

            command.replyTo.tell(new EventProcessed(userId, true, 
                "Event processed successfully"));

            return this;

        } catch (Exception e) {
            getContext().getLog().error("❌ Error processing event for user {}: {}", 
                userId, e.getMessage(), e);
            command.replyTo.tell(new EventProcessed(userId, false, 
                "Error: " + e.getMessage()));
            return this;
        }
    }

    private Behavior<Command> onGetUserStats(GetUserStats command) {
        try {
            getContext().getLog().info("📊 Getting stats for user {}", userId);
            
            // Count recent events (last hour)
            java.time.Instant oneHourAgo = java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS);
            int recentCount = (int) events.stream()
                .filter(event -> event.getTimestamp().isAfter(oneHourAgo))
                .count();

            UserStats stats = new UserStats(userId, totalEvents, lastActivity, recentCount);
            command.replyTo.tell(stats);

            getContext().getLog().info("✅ Returned stats for user {}: {} total, {} recent, lastActivity={}", 
                userId, totalEvents, recentCount, lastActivity);

            return this;
        } catch (Exception e) {
            getContext().getLog().error("❌ Error getting stats for user {}: {}", 
                userId, e.getMessage(), e);
            // Return empty stats on error
            UserStats emptyStats = new UserStats(userId, 0, LocalDateTime.now(), 0);
            command.replyTo.tell(emptyStats);
            return this;
        }
    }

    private Behavior<Command> onPassivateUser(PassivateUser command) {
        getContext().getLog().info("Passivating ClusterUserActor for user: {}", userId);
        return Behaviors.stopped();
    }
}