package com.eventstreaming.service;

import com.eventstreaming.actor.UserActor;
import com.eventstreaming.actor.UserActorRegistry;
import com.eventstreaming.model.CommunicationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced service for publishing communication events to Kafka topics and User Actors.
 * Supports dual integration: Kafka publishing for multi-node distribution and direct actor calls for backward compatibility.
 */
@Service
public class EventProducerService {
    
    private static final Logger log = LoggerFactory.getLogger(EventProducerService.class);
    
    private final UserActorRegistry userActorRegistry;
    private final KafkaTemplate<String, CommunicationEvent> kafkaTemplate;
    
    @Value("${app.kafka.topics.email-events}")
    private String emailEventsTopic;
    
    @Value("${app.kafka.topics.sms-events}")
    private String smsEventsTopic;
    
    @Value("${app.kafka.topics.call-events}")
    private String callEventsTopic;
    
    @Value("${app.streams.event-processing.kafka-enabled:true}")
    private boolean kafkaEnabled;
    
    @Autowired
    public EventProducerService(UserActorRegistry userActorRegistry, 
                               KafkaTemplate<String, CommunicationEvent> kafkaTemplate) {
        this.userActorRegistry = userActorRegistry;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Publishes a communication event to Kafka topic with user affinity.
     * Uses user ID as partition key to ensure same user events go to same partition.
     */
    public CompletableFuture<SendResult<String, CommunicationEvent>> publishToKafka(CommunicationEvent event) {
        if (event == null || !event.isValid()) {
            log.warn("Invalid event provided for Kafka publishing: {}", event);
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid event"));
        }
        
        String topicName = determineTopicName(event);
        String partitionKey = determinePartitionKey(event);
        
        log.debug("Publishing {} event to Kafka topic {} with key {}: {}", 
                 event.getEventType(), topicName, partitionKey, event.getEventId());
        
        return kafkaTemplate.send(topicName, partitionKey, event)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish event to Kafka: topic={}, key={}, event={}", 
                             topicName, partitionKey, event, throwable);
                } else {
                    log.debug("Successfully published event to Kafka: topic={}, partition={}, offset={}", 
                             topicName, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
    }
    
    /**
     * Publishes a communication event using the configured method (Kafka or direct actor).
     * Provides backward compatibility while supporting new Kafka-based flow.
     */
    public CompletableFuture<UserActor.ProcessEventResponse> publishEvent(CommunicationEvent event) {
        if (kafkaEnabled) {
            // Kafka-based publishing - event will be processed by Pekko Streams
            return publishToKafka(event)
                .thenApply(result -> (UserActor.ProcessEventResponse) new UserActor.ProcessEventResponse.Success(null))
                .exceptionally(throwable -> {
                    log.error("Error publishing event to Kafka for user {}: {}", event.getUserId(), event, throwable);
                    return new UserActor.ProcessEventResponse.Failed("Kafka publishing error: " + throwable.getMessage());
                });
        } else {
            // Direct actor publishing - backward compatibility
            return publishEventDirectly(event);
        }
    }
    
    /**
     * Direct actor publishing for backward compatibility and testing.
     */
    public CompletableFuture<UserActor.ProcessEventResponse> publishEventDirectly(CommunicationEvent event) {
        if (event == null || !event.isValid()) {
            log.warn("Invalid event provided for direct publishing: {}", event);
            return CompletableFuture.completedFuture(
                new UserActor.ProcessEventResponse.Failed("Invalid event"));
        }
        
        String userId = event.getUserId();
        log.debug("Publishing {} event directly to actor for user {}: {}", 
                 event.getEventType(), userId, event.getEventId());
        
        try {
            return userActorRegistry.askUserActor(
                userId,
                replyTo -> new UserActor.ProcessEvent(event, replyTo),
                Duration.ofSeconds(10),
                UserActor.ProcessEventResponse.class
            ).toCompletableFuture()
            .exceptionally(throwable -> {
                log.error("Error publishing event directly to actor for user {}: {}", userId, event, throwable);
                return new UserActor.ProcessEventResponse.Failed("Direct publishing error: " + throwable.getMessage());
            });
            
        } catch (Exception e) {
            log.error("Error publishing event directly to actor for user {}: {}", userId, event, e);
            return CompletableFuture.completedFuture(
                new UserActor.ProcessEventResponse.Failed("Direct publishing error: " + e.getMessage()));
        }
    }
    
    /**
     * Publishes multiple events in parallel using the configured method.
     */
    public CompletableFuture<Void> publishEvents(CommunicationEvent... events) {
        CompletableFuture<?>[] futures = new CompletableFuture[events.length];
        
        for (int i = 0; i < events.length; i++) {
            futures[i] = publishEvent(events[i]);
        }
        
        return CompletableFuture.allOf(futures);
    }
    
    /**
     * Publishes multiple events to Kafka in batch for high throughput.
     */
    public CompletableFuture<Void> publishEventsToKafka(CommunicationEvent... events) {
        CompletableFuture<?>[] futures = new CompletableFuture[events.length];
        
        for (int i = 0; i < events.length; i++) {
            futures[i] = publishToKafka(events[i]);
        }
        
        return CompletableFuture.allOf(futures);
    }
    
    /**
     * Determines the appropriate Kafka topic based on event type.
     */
    public String determineTopicName(CommunicationEvent event) {
        return switch (event.getEventType()) {
            case EMAIL_OPEN, EMAIL_CLICK -> emailEventsTopic;
            case SMS_REPLY, SMS_DELIVERY -> smsEventsTopic;
            case CALL_COMPLETED, CALL_MISSED -> callEventsTopic;
            default -> throw new IllegalArgumentException("Unknown event type: " + event.getEventType());
        };
    }
    
    /**
     * Determines the partition key for user affinity.
     * CRITICAL: Ensures same user always goes to same partition for event ordering.
     */
    public String determinePartitionKey(CommunicationEvent event) {
        return event.getUserId(); // User ID as partition key ensures user affinity
    }
    
    /**
     * Validates event before publishing.
     */
    public boolean validateEvent(CommunicationEvent event) {
        if (event == null) {
            log.warn("Event is null");
            return false;
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            log.warn("Event has null or empty user ID: {}", event);
            return false;
        }
        
        if (event.getEventType() == null) {
            log.warn("Event has null event type: {}", event);
            return false;
        }
        
        if (event.getTimestamp() == null) {
            log.warn("Event has null timestamp: {}", event);
            return false;
        }
        
        return true;
    }
}