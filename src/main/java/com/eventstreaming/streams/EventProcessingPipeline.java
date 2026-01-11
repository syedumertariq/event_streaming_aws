package com.eventstreaming.streams;

import com.eventstreaming.actor.UserActorRegistry;
import com.eventstreaming.model.CommunicationEvent;
import com.eventstreaming.monitoring.StreamProcessingMetrics;
import com.eventstreaming.validation.EventSequenceValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.stream.ActorAttributes;
import org.apache.pekko.stream.Supervision;
import org.apache.pekko.stream.javadsl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * Pekko Streams pipeline for processing events from Kafka topics.
 * Handles deserialization, validation, routing, and error handling.
 */
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class EventProcessingPipeline {
    
    private static final Logger log = LoggerFactory.getLogger(EventProcessingPipeline.class);
    
    private final ActorSystem<Void> actorSystem;
    private final UnifiedEventConsumer eventConsumer;
    private final UserActorRegistry userActorRegistry;
    private final EventSequenceValidator sequenceValidator;
    private final StreamProcessingMetrics metrics;
    private final ObjectMapper objectMapper;
    
    @Value("${app.streams.event-processing.parallelism:10}")
    private int processingParallelism;
    
    @Value("${app.streams.event-processing.buffer-size:1000}")
    private int bufferSize;
    
    @Value("${app.streams.event-processing.ask-timeout:5s}")
    private Duration askTimeout;
    
    @Value("${app.streams.event-processing.retry-attempts:3}")
    private int retryAttempts;
    
    @Value("${app.streams.event-processing.restart-settings.min-backoff:3s}")
    private Duration minBackoff;
    
    @Value("${app.streams.event-processing.restart-settings.max-backoff:30s}")
    private Duration maxBackoff;
    
    @Value("${app.streams.event-processing.restart-settings.random-factor:0.2}")
    private double randomFactor;
    
    @Autowired
    public EventProcessingPipeline(ActorSystem<Void> actorSystem,
                                 UnifiedEventConsumer eventConsumer,
                                 UserActorRegistry userActorRegistry,
                                 EventSequenceValidator sequenceValidator,
                                 StreamProcessingMetrics metrics,
                                 ObjectMapper objectMapper) {
        this.actorSystem = actorSystem;
        this.eventConsumer = eventConsumer;
        this.userActorRegistry = userActorRegistry;
        this.sequenceValidator = sequenceValidator;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Creates the main event processing pipeline with backpressure handling,
     * error recovery, and user affinity routing.
     */
    public Source<Object, Consumer.Control> createEventProcessingPipeline() {
        return createBaseEventProcessingSource();
    }
    
    /**
     * Creates the base event processing source without restart wrapper.
     */
    private Source<Object, Consumer.Control> createBaseEventProcessingSource() {
        return eventConsumer.createUnifiedEventSource()
            .buffer(bufferSize, org.apache.pekko.stream.OverflowStrategy.backpressure())
            .via(createEventDeserializationFlow())
            .via(createSequenceValidationFlow())
            .via(createUserAffinityProcessingFlow())
            .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider()));
    }
    
    /**
     * Creates flow for deserializing JSON events to CommunicationEvent objects.
     */
    Flow<ConsumerRecord<String, String>, EventWithMetadata, NotUsed> createEventDeserializationFlow() {
        return Flow.<ConsumerRecord<String, String>>create()
            .map(record -> {
                var deserializationTimer = metrics.startDeserializationTimer();
                try {
                    CommunicationEvent event = eventConsumer.deserializeEvent(record.value());
                    metrics.recordDeserializationTime(deserializationTimer);
                    
                    return new EventWithMetadata(
                        event,
                        record.key(),
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        Instant.now()
                    );
                } catch (Exception e) {
                    metrics.recordDeserializationTime(deserializationTimer);
                    metrics.incrementDeserializationErrors();
                    
                    log.error("Failed to deserialize event from topic {} partition {} offset {}: {}", 
                             record.topic(), record.partition(), record.offset(), record.value(), e);
                    throw new EventDeserializationException("Deserialization failed", e, record);
                }
            })
            .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider()));
    }
    
    /**
     * Creates flow for validating event sequences across sources.
     */
    Flow<EventWithMetadata, ValidatedEventWithMetadata, NotUsed> createSequenceValidationFlow() {
        return Flow.<EventWithMetadata>create()
            .map(eventWithMetadata -> {
                var validationTimer = metrics.startSequenceValidationTimer();
                try {
                    EventSequenceValidator.SequenceValidationResult validationResult = 
                        sequenceValidator.validateSequence(eventWithMetadata.event());
                    
                    metrics.recordSequenceValidationTime(validationTimer);
                    
                    if (!validationResult.isValid()) {
                        metrics.incrementSequenceValidationErrors();
                        log.warn("Sequence validation failed for user {}: {} - Event: {}", 
                                eventWithMetadata.event().getUserId(), 
                                validationResult.getMessage(),
                                eventWithMetadata.event().getEventId());
                    }
                    
                    return new ValidatedEventWithMetadata(eventWithMetadata, validationResult);
                } catch (Exception e) {
                    metrics.recordSequenceValidationTime(validationTimer);
                    metrics.incrementSequenceValidationErrors();
                    throw e;
                }
            })
            .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider()));
    }
    
    /**
     * Creates flow for processing events with user affinity routing.
     */
    private Flow<ValidatedEventWithMetadata, Object, NotUsed> createUserAffinityProcessingFlow() {
        return Flow.<ValidatedEventWithMetadata>create()
            .mapAsync(processingParallelism, validatedEvent -> {
                EventWithMetadata eventWithMetadata = validatedEvent.eventWithMetadata();
                CommunicationEvent event = eventWithMetadata.event();
                String userId = event.getUserId();
                
                log.debug("Processing {} event for user {}: {} from topic {} partition {} offset {}", 
                         event.getEventType(), userId, event.getEventId(),
                         eventWithMetadata.topic(), eventWithMetadata.partition(), eventWithMetadata.offset());
                
                return processEventWithUserActor(event, eventWithMetadata, validatedEvent.validationResult())
                    .thenApply(result -> (Object) result);
            })
            .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider()));
    }
    
    /**
     * Processes an event using the appropriate UserActor with user affinity.
     */
    private CompletionStage<ProcessingResult> processEventWithUserActor(
            CommunicationEvent event, 
            EventWithMetadata metadata,
            EventSequenceValidator.SequenceValidationResult validationResult) {
        
        String userId = event.getUserId();
        Instant processingStartTime = Instant.now();
        var actorAskTimer = metrics.startActorAskTimer();
        
        return userActorRegistry.askUserActor(
            userId,
            replyTo -> new com.eventstreaming.actor.UserActor.ProcessEvent(event, replyTo),
            askTimeout,
            com.eventstreaming.actor.UserActor.ProcessEventResponse.class
        ).thenApply(response -> {
            metrics.recordActorAskTime(actorAskTimer);
            Instant processingEndTime = Instant.now();
            Duration processingDuration = Duration.between(processingStartTime, processingEndTime);
            
            if (response instanceof com.eventstreaming.actor.UserActor.ProcessEventResponse.Success success) {
                metrics.recordSuccessfulProcessing(event.getEventType().toString(), userId, processingDuration);
                
                log.debug("Successfully processed event {} for user {} in {}ms", 
                         event.getEventId(), userId, processingDuration.toMillis());
                
                return new ProcessingResult.Success(
                    event.getEventId(),
                    userId,
                    processingEndTime,
                    processingDuration,
                    success.aggregations(),
                    metadata,
                    validationResult
                );
            } else if (response instanceof com.eventstreaming.actor.UserActor.ProcessEventResponse.Failed failed) {
                metrics.recordFailedProcessing(event.getEventType().toString(), failed.reason(), processingDuration);
                
                log.error("Failed to process event {} for user {}: {}", 
                         event.getEventId(), userId, failed.reason());
                
                return new ProcessingResult.Failed(
                    event.getEventId(),
                    userId,
                    failed.reason(),
                    null,
                    metadata
                );
            } else {
                String reason = "Unknown response type: " + response.getClass().getSimpleName();
                metrics.recordFailedProcessing(event.getEventType().toString(), reason, processingDuration);
                
                log.error("Unknown response type for event {} user {}: {}", 
                         event.getEventId(), userId, response.getClass().getSimpleName());
                
                return new ProcessingResult.Failed(
                    event.getEventId(),
                    userId,
                    reason,
                    null,
                    metadata
                );
            }
        }).exceptionally(throwable -> {
            metrics.recordActorAskTime(actorAskTimer);
            
            if (throwable instanceof java.util.concurrent.TimeoutException) {
                metrics.incrementActorTimeouts();
            }
            
            Duration processingDuration = Duration.between(processingStartTime, Instant.now());
            metrics.recordFailedProcessing(event.getEventType().toString(), "Actor processing error", processingDuration);
            
            return new ProcessingResult.Failed(
                event.getEventId(),
                userId,
                "Actor processing error: " + throwable.getMessage(),
                throwable,
                metadata
            );
        });
    }
    
    /**
     * Creates a supervision strategy for handling stream failures.
     */
    org.apache.pekko.japi.function.Function<Throwable, Supervision.Directive> createSupervisionStrategy() {
        return throwable -> {
            if (throwable instanceof EventDeserializationException) {
                log.error("Event deserialization error, skipping message: {}", throwable.getMessage());
                return Supervision.resume(); // Skip invalid messages
            } else if (throwable instanceof java.util.concurrent.TimeoutException) {
                log.warn("Actor timeout, retrying: {}", throwable.getMessage());
                return Supervision.restart(); // Restart on timeout
            } else if (throwable instanceof RuntimeException) {
                log.error("Runtime exception in stream processing: {}", throwable.getMessage(), throwable);
                return Supervision.restart(); // Restart on runtime exceptions
            } else {
                log.error("Unexpected error in stream processing: {}", throwable.getMessage(), throwable);
                return Supervision.stop(); // Stop on unexpected errors
            }
        };
    }
    
    /**
     * Creates a sink for processing results (logging, metrics, etc.).
     */
    public Sink<Object, CompletionStage<Done>> createProcessingResultSink() {
        return Sink.foreach(result -> {
            if (result instanceof ProcessingResult.Success success) {
                log.debug("Event processing success: {} for user {} in {}ms", 
                         success.eventId(), success.userId(), success.processingDuration().toMillis());
            } else if (result instanceof ProcessingResult.Failed failed) {
                log.error("Event processing failed: {} for user {} - {}", 
                         failed.eventId(), failed.userId(), failed.reason());
            } else if (result instanceof ProcessingResult.Retry retry) {
                log.warn("Event processing retry: {} for user {} - attempt {} - {}", 
                         retry.eventId(), retry.userId(), retry.attemptCount(), retry.reason());
            } else {
                log.debug("Processing result: {}", result);
            }
        });
    }
    
    /**
     * Starts the event processing pipeline.
     */
    public CompletionStage<Done> startProcessing() {
        log.info("Starting event processing pipeline with parallelism={}, bufferSize={}, askTimeout={}", 
                processingParallelism, bufferSize, askTimeout);
        
        metrics.incrementActiveStreams();
        
        return createEventProcessingPipeline()
            .runWith(createProcessingResultSink(), actorSystem)
            .whenComplete((done, throwable) -> {
                metrics.decrementActiveStreams();
                if (throwable != null) {
                    log.error("Event processing pipeline completed with error", throwable);
                } else {
                    log.info("Event processing pipeline completed successfully");
                }
            });
    }
    
    // Data classes for pipeline processing
    
    /**
     * Event with Kafka metadata for tracking and debugging.
     */
    public record EventWithMetadata(
        CommunicationEvent event,
        String partitionKey,
        String topic,
        int partition,
        long offset,
        Instant receivedAt
    ) {}
    
    /**
     * Event with validation result for sequence checking.
     */
    public record ValidatedEventWithMetadata(
        EventWithMetadata eventWithMetadata,
        EventSequenceValidator.SequenceValidationResult validationResult
    ) {}
    
    /**
     * Result of event processing with detailed information.
     */
    public static abstract class ProcessingResult {
        public abstract String eventId();
        public abstract String userId();
        
        public static class Success extends ProcessingResult {
            private final String eventId;
            private final String userId;
            private final Instant processedAt;
            private final Duration processingDuration;
            private final com.eventstreaming.model.UserAggregations aggregations;
            private final EventWithMetadata metadata;
            private final EventSequenceValidator.SequenceValidationResult validationResult;
            
            public Success(String eventId, String userId, Instant processedAt, Duration processingDuration,
                          com.eventstreaming.model.UserAggregations aggregations, EventWithMetadata metadata,
                          EventSequenceValidator.SequenceValidationResult validationResult) {
                this.eventId = eventId;
                this.userId = userId;
                this.processedAt = processedAt;
                this.processingDuration = processingDuration;
                this.aggregations = aggregations;
                this.metadata = metadata;
                this.validationResult = validationResult;
            }
            
            @Override public String eventId() { return eventId; }
            @Override public String userId() { return userId; }
            public Instant processedAt() { return processedAt; }
            public Duration processingDuration() { return processingDuration; }
            public com.eventstreaming.model.UserAggregations aggregations() { return aggregations; }
            public EventWithMetadata metadata() { return metadata; }
            public EventSequenceValidator.SequenceValidationResult validationResult() { return validationResult; }
        }
        
        public static class Failed extends ProcessingResult {
            private final String eventId;
            private final String userId;
            private final String reason;
            private final Throwable cause;
            private final EventWithMetadata metadata;
            
            public Failed(String eventId, String userId, String reason, Throwable cause, EventWithMetadata metadata) {
                this.eventId = eventId;
                this.userId = userId;
                this.reason = reason;
                this.cause = cause;
                this.metadata = metadata;
            }
            
            @Override public String eventId() { return eventId; }
            @Override public String userId() { return userId; }
            public String reason() { return reason; }
            public Throwable cause() { return cause; }
            public EventWithMetadata metadata() { return metadata; }
        }
        
        public static class Retry extends ProcessingResult {
            private final String eventId;
            private final String userId;
            private final String reason;
            private final int attemptCount;
            private final EventWithMetadata metadata;
            
            public Retry(String eventId, String userId, String reason, int attemptCount, EventWithMetadata metadata) {
                this.eventId = eventId;
                this.userId = userId;
                this.reason = reason;
                this.attemptCount = attemptCount;
                this.metadata = metadata;
            }
            
            @Override public String eventId() { return eventId; }
            @Override public String userId() { return userId; }
            public String reason() { return reason; }
            public int attemptCount() { return attemptCount; }
            public EventWithMetadata metadata() { return metadata; }
        }
    }
    
    /**
     * Exception for event deserialization failures.
     */
    public static class EventDeserializationException extends RuntimeException {
        private final ConsumerRecord<String, String> record;
        
        public EventDeserializationException(String message, Throwable cause, ConsumerRecord<String, String> record) {
            super(message, cause);
            this.record = record;
        }
        
        public ConsumerRecord<String, String> getRecord() {
            return record;
        }
    }
}