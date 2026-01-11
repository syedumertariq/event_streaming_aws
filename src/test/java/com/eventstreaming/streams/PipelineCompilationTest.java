package com.eventstreaming.streams;

import com.eventstreaming.actor.UserActorRegistry;
import com.eventstreaming.model.EmailEvent;
import com.eventstreaming.model.EventType;
import com.eventstreaming.monitoring.StreamProcessingMetrics;
import com.eventstreaming.validation.EventSequenceValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Simple compilation test for the pipeline components.
 */
@ExtendWith(MockitoExtension.class)
class PipelineCompilationTest {
    
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
    void testPipelineCreation() {
        // Test that the pipeline can be created without compilation errors
        assertNotNull(pipeline);
    }
    
    @Test
    void testEventWithMetadataRecord() {
        // Test the record classes compile correctly
        var event = EmailEvent.builder()
            .eventId("test-event")
            .userId("user123")
            .eventType(EventType.EMAIL_OPEN)
            .timestamp(Instant.now())
            .build();
        
        var eventWithMetadata = new EventProcessingPipeline.EventWithMetadata(
            event, "user123", "email-events", 0, 100L, Instant.now()
        );
        
        assertEquals(event, eventWithMetadata.event());
        assertEquals("user123", eventWithMetadata.partitionKey());
        assertEquals("email-events", eventWithMetadata.topic());
    }
    
    @Test
    void testProcessingResultRecords() {
        // Test that the sealed interface and records compile correctly
        var success = new EventProcessingPipeline.ProcessingResult.Success(
            "event-1", "user123", Instant.now(), java.time.Duration.ofMillis(100),
            null, null, null
        );
        
        var failed = new EventProcessingPipeline.ProcessingResult.Failed(
            "event-2", "user456", "Test failure", null, null
        );
        
        assertEquals("event-1", success.eventId());
        assertEquals("user123", success.userId());
        assertEquals("event-2", failed.eventId());
        assertEquals("user456", failed.userId());
        assertEquals("Test failure", failed.reason());
    }
}