package com.eventstreaming.streams;

import com.eventstreaming.actor.UserActorRegistry;
import com.eventstreaming.model.CommunicationEvent;
import com.eventstreaming.model.EmailEvent;
import com.eventstreaming.model.EventType;
import com.eventstreaming.monitoring.StreamProcessingMetrics;
import com.eventstreaming.validation.EventSequenceValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for the EventProcessingPipeline.
 * Tests the complete flow from event consumption to processing.
 */
@ExtendWith(MockitoExtension.class)
class EventProcessingPipelineTest {
    
    private ActorTestKit testKit;
    private ActorSystem<Void> actorSystem;
    
    @Mock
    private UnifiedEventConsumer eventConsumer;
    
    @Mock
    private UserActorRegistry userActorRegistry;
    
    @Mock
    private EventSequenceValidator sequenceValidator;
    
    @Mock
    private StreamProcessingMetrics metrics;
    
    private ObjectMapper objectMapper;
    private EventProcessingPipeline pipeline;
    
    @BeforeEach
    void setUp() {
        testKit = ActorTestKit.create();
        actorSystem = testKit.system();
        objectMapper = new ObjectMapper();
        
        // Mock metrics methods to avoid null pointer exceptions
        when(metrics.startDeserializationTimer()).thenReturn(null);
        when(metrics.startSequenceValidationTimer()).thenReturn(null);
        when(metrics.startActorAskTimer()).thenReturn(null);
        
        pipeline = new EventProcessingPipeline(
            actorSystem,
            eventConsumer,
            userActorRegistry,
            sequenceValidator,
            metrics,
            objectMapper
        );
    }
    
    @AfterEach
    void tearDown() {
        testKit.shutdownTestKit();
    }
    
    @Test
    void testEventDeserializationFlow() throws Exception {
        // Given
        String eventJson = """
            {
                "eventId": "test-event-1",
                "userId": "user123",
                "eventType": "EMAIL_OPEN",
                "timestamp": "2024-01-01T10:00:00Z",
                "metadata": {}
            }
            """;
        
        CommunicationEvent expectedEvent = EmailEvent.builder()
            .eventId("test-event-1")
            .userId("user123")
            .eventType(EventType.EMAIL_OPEN)
            .timestamp(Instant.parse("2024-01-01T10:00:00Z"))
            .build();
        
        when(eventConsumer.deserializeEvent(eventJson)).thenReturn(expectedEvent);
        
        // Create a test consumer record
        var testRecord = new org.apache.kafka.clients.consumer.ConsumerRecord<>(
            "email-events", 0, 0L, "user123", eventJson
        );
        
        // When
        Source<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>, org.apache.pekko.NotUsed> testSource = 
            Source.single(testRecord);
        
        var deserializationFlow = pipeline.createEventDeserializationFlow();
        
        CompletionStage<List<EventProcessingPipeline.EventWithMetadata>> result = 
            testSource.via(deserializationFlow)
                .runWith(Sink.seq(), actorSystem);
        
        // Then
        List<EventProcessingPipeline.EventWithMetadata> events = result.toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        assertEquals(1, events.size());
        EventProcessingPipeline.EventWithMetadata eventWithMetadata = events.get(0);
        assertEquals("test-event-1", eventWithMetadata.event().getEventId());
        assertEquals("user123", eventWithMetadata.event().getUserId());
        assertEquals(EventType.EMAIL_OPEN, eventWithMetadata.event().getEventType());
        assertEquals("user123", eventWithMetadata.partitionKey());
        assertEquals("email-events", eventWithMetadata.topic());
    }
    
    @Test
    void testSequenceValidationFlow() throws Exception {
        // Given
        CommunicationEvent testEvent = EmailEvent.builder()
            .eventId("test-event-1")
            .userId("user123")
            .eventType(EventType.EMAIL_OPEN)
            .timestamp(Instant.now())
            .build();
        
        EventProcessingPipeline.EventWithMetadata eventWithMetadata = 
            new EventProcessingPipeline.EventWithMetadata(
                testEvent, "user123", "email-events", 0, 0L, Instant.now()
            );
        
        EventSequenceValidator.SequenceValidationResult validationResult = 
            EventSequenceValidator.SequenceValidationResult.valid();
        
        when(sequenceValidator.validateSequence(any(CommunicationEvent.class)))
            .thenReturn(validationResult);
        
        // When
        Source<EventProcessingPipeline.EventWithMetadata, org.apache.pekko.NotUsed> testSource = 
            Source.single(eventWithMetadata);
        
        var validationFlow = pipeline.createSequenceValidationFlow();
        
        CompletionStage<List<EventProcessingPipeline.ValidatedEventWithMetadata>> result = 
            testSource.via(validationFlow)
                .runWith(Sink.seq(), actorSystem);
        
        // Then
        List<EventProcessingPipeline.ValidatedEventWithMetadata> validatedEvents = 
            result.toCompletableFuture().get(5, TimeUnit.SECONDS);
        
        assertEquals(1, validatedEvents.size());
        EventProcessingPipeline.ValidatedEventWithMetadata validatedEvent = validatedEvents.get(0);
        assertEquals(eventWithMetadata, validatedEvent.eventWithMetadata());
        assertTrue(validatedEvent.validationResult().isValid());
    }
    
    @Test
    void testProcessingResultSink() throws Exception {
        // Given
        var successResult = new EventProcessingPipeline.ProcessingResult.Success(
            "test-event-1",
            "user123",
            Instant.now(),
            Duration.ofMillis(100),
            null, // aggregations
            null, // metadata
            null  // validation result
        );
        
        var failedResult = new EventProcessingPipeline.ProcessingResult.Failed(
            "test-event-2",
            "user456",
            "Test failure",
            null, // cause
            null  // metadata
        );
        
        // When
        Source<EventProcessingPipeline.ProcessingResult, org.apache.pekko.NotUsed> testSource = 
            Source.from(List.of(successResult, failedResult));
        
        var processingSink = pipeline.createProcessingResultSink();
        
        CompletionStage<org.apache.pekko.Done> result = 
            testSource.map(r -> (Object) r).runWith(processingSink, actorSystem);
        
        // Then
        assertDoesNotThrow(() -> {
            result.toCompletableFuture().get(5, TimeUnit.SECONDS);
        });
    }
    
    @Test
    void testSupervisionStrategy() {
        // Given
        var supervisionDecider = pipeline.createSupervisionStrategy();
        
        // Test different exception types
        var deserializationException = new EventProcessingPipeline.EventDeserializationException(
            "Test deserialization error", 
            new RuntimeException(), 
            null
        );
        var timeoutException = new java.util.concurrent.TimeoutException("Test timeout");
        var runtimeException = new RuntimeException("Test runtime error");
        var unexpectedException = new Exception("Test unexpected error");
        
        // When & Then
        assertDoesNotThrow(() -> {
            assertEquals(org.apache.pekko.stream.Supervision.resume(), 
                        supervisionDecider.apply(deserializationException));
            assertEquals(org.apache.pekko.stream.Supervision.restart(), 
                        supervisionDecider.apply(timeoutException));
            assertEquals(org.apache.pekko.stream.Supervision.restart(), 
                        supervisionDecider.apply(runtimeException));
            assertEquals(org.apache.pekko.stream.Supervision.stop(), 
                        supervisionDecider.apply(unexpectedException));
        });
    }
    
    @Test
    void testEventWithMetadataRecord() {
        // Given
        CommunicationEvent event = EmailEvent.builder()
            .eventId("test-event")
            .userId("user123")
            .eventType(EventType.EMAIL_OPEN)
            .timestamp(Instant.now())
            .build();
        
        Instant receivedAt = Instant.now();
        
        // When
        EventProcessingPipeline.EventWithMetadata eventWithMetadata = 
            new EventProcessingPipeline.EventWithMetadata(
                event, "user123", "email-events", 0, 100L, receivedAt
            );
        
        // Then
        assertEquals(event, eventWithMetadata.event());
        assertEquals("user123", eventWithMetadata.partitionKey());
        assertEquals("email-events", eventWithMetadata.topic());
        assertEquals(0, eventWithMetadata.partition());
        assertEquals(100L, eventWithMetadata.offset());
        assertEquals(receivedAt, eventWithMetadata.receivedAt());
    }
}