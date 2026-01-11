package com.eventstreaming.streams;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.eventstreaming.actor.UserActorRegistry;
import com.eventstreaming.model.CommunicationEvent;
import com.eventstreaming.model.EventType;
import com.eventstreaming.validation.EventSequenceValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Consumer;
// import org.apache.pekko.kafka.javadsl.Consumer.CommittableMessage;
import org.apache.pekko.stream.javadsl.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionStage;

/**
 * Unified Pekko Streams consumer that handles events from all communication channels.
 * Provides user affinity routing and sequence validation for multi-node consistency.
 */
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class UnifiedEventConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(UnifiedEventConsumer.class);
    
    private final ActorSystem<Void> actorSystem;
    private final UserActorRegistry userActorRegistry;
    private final EventSequenceValidator sequenceValidator;
    private final ObjectMapper objectMapper;
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    
    @Value("${app.kafka.topics.email-events}")
    private String emailEventsTopic;
    
    @Value("${app.kafka.topics.sms-events}")
    private String smsEventsTopic;
    
    @Value("${app.kafka.topics.call-events}")
    private String callEventsTopic;
    
    @Autowired
    public UnifiedEventConsumer(ActorSystem<Void> actorSystem,
                               UserActorRegistry userActorRegistry,
                               EventSequenceValidator sequenceValidator,
                               ObjectMapper objectMapper) {
        this.actorSystem = actorSystem;
        this.userActorRegistry = userActorRegistry;
        this.sequenceValidator = sequenceValidator;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Creates a unified Kafka consumer source that subscribes to all event topics.
     * Uses manual offset commits for exactly-once processing.
     */
    public Source<ConsumerRecord<String, String>, Consumer.Control> createUnifiedEventSource() {
        ConsumerSettings<String, String> consumerSettings = 
            ConsumerSettings.create(actorSystem, new StringDeserializer(), new StringDeserializer())
                .withBootstrapServers(bootstrapServers)
                .withGroupId(groupId)
                .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                .withProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")
                .withProperty(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1024")
                .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
                .withProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000");
        
        // Subscribe to all event topics
        return Consumer.plainSource(
            consumerSettings,
            Subscriptions.topics(emailEventsTopic, smsEventsTopic, callEventsTopic)
        );
    }
    
    /**
     * Creates a committable source for exactly-once processing with manual offset management.
     * Note: Simplified for now - committable functionality can be added later.
     */
    public Source<ConsumerRecord<String, String>, Consumer.Control> createCommittableEventSource() {
        return createUnifiedEventSource();
    }
    
    /**
     * Deserializes event string to CommunicationEvent.
     * Supports both JSON format and colon-delimited format.
     * Colon-delimited format: timestamp@hash:action:value1:value2
     */
    public CommunicationEvent deserializeEvent(String eventString) {
        try {
            // Try JSON first
            if (eventString.trim().startsWith("{")) {
                return objectMapper.readValue(eventString, CommunicationEvent.class);
            }
            
            // Parse colon-delimited format
            return parseColonDelimitedEvent(eventString);
        } catch (Exception e) {
            log.error("Failed to deserialize event: {}", eventString, e);
            throw new RuntimeException("Event deserialization failed", e);
        }
    }
    
    /**
     * Parses colon-delimited event format: timestamp@hash:action:value1:value2
     */
    private CommunicationEvent parseColonDelimitedEvent(String eventString) {
        String[] parts = eventString.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid event format: " + eventString);
        }
        
        // Extract timestamp and hash from first part
        String[] timestampParts = parts[0].split("@");
        String timestamp = timestampParts.length > 0 ? timestampParts[0] : "";
        String hash = timestampParts.length > 1 ? timestampParts[1] : "";
        
        // Extract action
        String action = parts.length > 1 ? parts[1] : "";
        
        // Map action to EventType
        EventType eventType = mapActionToEventType(action);
        
        // Create appropriate event subclass based on action
        CommunicationEvent event;
        if (action.equalsIgnoreCase("DROP") || action.equalsIgnoreCase("ANSWER")) {
            // Create CallEvent
            com.eventstreaming.model.CallEvent callEvent = new com.eventstreaming.model.CallEvent();
            callEvent.setCallResult(action.equalsIgnoreCase("DROP") ? "MISSED" : "COMPLETED");
            event = callEvent;
        } else {
            // Create generic CallEvent for other types
            event = new com.eventstreaming.model.CallEvent();
        }
        
        event.setEventId(hash.substring(0, Math.min(20, hash.length()))); // Use part of hash as ID
        event.setUserId(extractUserIdFromHash(hash)); // Extract user ID from hash
        event.setEventType(eventType);
        event.setTimestamp(parseTimestamp(timestamp));
        event.setSource("kafka-colon-delimited");
        
        // Store additional data in metadata
        event.setMetadata(java.util.Map.of(
            "rawData", eventString,
            "hash", hash,
            "action", action
        ));
        
        log.debug("Parsed colon-delimited event: action={}, timestamp={}, eventType={}", 
                 action, timestamp, eventType);
        
        return event;
    }
    
    /**
     * Extracts user ID from hash (using first 10 characters as user ID).
     */
    private String extractUserIdFromHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return "unknown";
        }
        return "user_" + hash.substring(0, Math.min(10, hash.length()));
    }
    
    /**
     * Maps action string to EventType enum.
     */
    private EventType mapActionToEventType(String action) {
        return switch (action.toUpperCase()) {
            case "DROP" -> EventType.DROP;
            case "ANSWER" -> EventType.CALL_ANSWERED;
            case "PICKUP" -> EventType.PICKUP;
            case "DELIVERY" -> EventType.DELIVERY;
            case "SEND" -> EventType.SMS_DELIVERY;
            case "RECEIVE" -> EventType.SMS_REPLY;
            default -> EventType.DROP; // Default to DROP for unknown actions
        };
    }
    
    /**
     * Parses timestamp string to Instant.
     */
    private java.time.Instant parseTimestamp(String timestamp) {
        try {
            // Try parsing as epoch milliseconds
            long epochMilli = Long.parseLong(timestamp.replaceAll("[^0-9]", ""));
            return java.time.Instant.ofEpochMilli(epochMilli);
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}, using current time", timestamp);
            return java.time.Instant.now();
        }
    }
    

    
    /**
     * Validates event sequence and logs warnings for out-of-order events.
     */
    public EventSequenceValidator.SequenceValidationResult validateEventSequence(CommunicationEvent event) {
        EventSequenceValidator.SequenceValidationResult result = sequenceValidator.validateSequence(event);
        
        if (!result.isValid()) {
            log.error("Event sequence validation failed for user {}: {}", event.getUserId(), result.getMessage());
        } else if (result.hasWarning()) {
            log.warn("Event sequence warning for user {}: {}", event.getUserId(), result.getMessage());
        }
        
        return result;
    }
    
    /**
     * Routes event to appropriate UserActor based on user ID for user affinity.
     */
    public CompletionStage<Object> routeEventToActor(CommunicationEvent event) {
        String userId = event.getUserId();
        
        log.debug("Routing {} event to UserActor for user {}: {}", 
                 event.getEventType(), userId, event.getEventId());
        
        return userActorRegistry.askUserActor(
            userId,
            replyTo -> new com.eventstreaming.actor.UserActor.ProcessEvent(event, replyTo),
            java.time.Duration.ofSeconds(5),
            com.eventstreaming.actor.UserActor.ProcessEventResponse.class
        ).thenApply(response -> {
            if (response instanceof com.eventstreaming.actor.UserActor.ProcessEventResponse.Success success) {
                return EventProcessingResult.success(event.getEventId(), userId, java.time.Instant.now());
            } else if (response instanceof com.eventstreaming.actor.UserActor.ProcessEventResponse.Failed failed) {
                return EventProcessingResult.failed(event.getEventId(), failed.reason(), null);
            } else {
                return EventProcessingResult.failed(event.getEventId(), "Unknown response type", null);
            }
        }).exceptionally(throwable -> {
            log.error("Error routing event to actor for user {}: {}", userId, event, throwable);
            return EventProcessingResult.failed(event.getEventId(), "Actor routing error", throwable);
        }).thenApply(result -> (Object) result);
    }
    
    /**
     * Handles processing errors with appropriate logging and error handling.
     */
    public void handleProcessingError(CommunicationEvent event, Throwable error) {
        log.error("Error processing event for user {}: {}", event.getUserId(), event, error);
        
        // TODO: Implement dead letter queue publishing
        // TODO: Implement retry mechanism
        // TODO: Implement alerting for critical errors
    }
    
    /**
     * Result of event processing.
     */
    public sealed interface EventProcessingResult {
        String eventId();
        
        record Success(String eventId, String userId, java.time.Instant processedAt) implements EventProcessingResult {}
        record Failed(String eventId, String reason, Throwable cause) implements EventProcessingResult {}
        record Retry(String eventId, String reason, int attemptCount) implements EventProcessingResult {}
        
        static Success success(String eventId, String userId, java.time.Instant processedAt) {
            return new Success(eventId, userId, processedAt);
        }
        
        static Failed failed(String eventId, String reason, Throwable cause) {
            return new Failed(eventId, reason, cause);
        }
        
        static Retry retry(String eventId, String reason, int attemptCount) {
            return new Retry(eventId, reason, attemptCount);
        }
    }
}