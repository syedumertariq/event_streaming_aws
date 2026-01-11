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
 * Health test for the Pekko Streams pipeline to verify basic functionality.
 */
@ExtendWith(MockitoExtension.class)
class PipelineHealthTest {
    
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
    private EventStreamingService streamingService;
    
    @BeforeEach
    void setUp() {
        testKit = ActorTestKit.create();
        actorSystem = testKit.system();
        objectMapper = new ObjectMapper();
        
        // Mock metrics methods
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
        
        streamingService = new EventStreamingService(actorSystem, pipeline);
    }
    
    @AfterEach
    void tearDown() {
        testKit.shutdownTestKit();
    }
    
    @Test
    void testPipelineComponentsInitialization() {
        // Verify all components can be created without errors
        assertNotNull(pipeline, "EventProcessingPipeline should be created");
        assertNotNull(streamingService, "EventStreamingService should be created");
        
        // Test pipeline methods don't throw exceptions
        assertDoesNotThrow(() -> {
            pipeline.createEventProcessingPipeline();
        }, "Pipeline creation should not throw exceptions");
        
        assertDoesNotThrow(() -> {
            pipeline.createProcessingResultSink();
        }, "Sink creation should not throw exceptions");
    }
    
    @Test
    void testStreamingServiceStatus() {
        // Test service status methods
        assertFalse(streamingService.isRunning(), "Service should not be running initially");
        assertFalse(streamingService.isShuttingDown(), "Service should not be shutting down initially");
        
        var healthInfo = streamingService.getHealthInfo();
        assertNotNull(healthInfo, "Health info should not be null");
        assertFalse(healthInfo.isRunning(), "Health info should show not running");
        
        var pipelineInfo = streamingService.getPipelineInfo();
        assertNotNull(pipelineInfo, "Pipeline info should not be null");
        assertEquals(EventStreamingService.StreamingStatus.STOPPED, pipelineInfo.status());
    }
    
    @Test
    void testEventWithMetadataCreation() {
        // Test that event metadata records work correctly
        var event = EmailEvent.builder()
            .eventId("test-event-123")
            .userId("user456")
            .eventType(EventType.EMAIL_OPEN)
            .timestamp(Instant.now())
            .source("test-source")
            .build();
        
        assertNotNull(event, "EmailEvent should be created");
        assertEquals("test-event-123", event.getEventId());
        assertEquals("user456", event.getUserId());
        assertEquals(EventType.EMAIL_OPEN, event.getEventType());
        assertEquals("test-source", event.getSource());
        assertTrue(event.isValid(), "Event should be valid");
        
        var eventWithMetadata = new EventProcessingPipeline.EventWithMetadata(
            event, "user456", "email-events", 0, 100L, Instant.now()
        );
        
        assertEquals(event, eventWithMetadata.event());
        assertEquals("user456", eventWithMetadata.partitionKey());
        assertEquals("email-events", eventWithMetadata.topic());
        assertEquals(0, eventWithMetadata.partition());
        assertEquals(100L, eventWithMetadata.offset());
    }
    
    @Test
    void testProcessingResultTypes() {
        // Test all processing result types
        var success = new EventProcessingPipeline.ProcessingResult.Success(
            "event-1", "user123", Instant.now(), java.time.Duration.ofMillis(50),
            null, null, null
        );
        
        var failed = new EventProcessingPipeline.ProcessingResult.Failed(
            "event-2", "user456", "Processing failed", 
            new RuntimeException("Test error"), null
        );
        
        var retry = new EventProcessingPipeline.ProcessingResult.Retry(
            "event-3", "user789", "Retry needed", 2, null
        );
        
        // Verify all types implement the interface correctly
        assertEquals("event-1", success.eventId());
        assertEquals("user123", success.userId());
        
        assertEquals("event-2", failed.eventId());
        assertEquals("user456", failed.userId());
        assertEquals("Processing failed", failed.reason());
        
        assertEquals("event-3", retry.eventId());
        assertEquals("user789", retry.userId());
        assertEquals("Retry needed", retry.reason());
        assertEquals(2, retry.attemptCount());
    }
    
    @Test
    void testSupervisionStrategyCreation() {
        // Test that supervision strategy can be created
        assertDoesNotThrow(() -> {
            var strategy = pipeline.createSupervisionStrategy();
            assertNotNull(strategy, "Supervision strategy should not be null");
        }, "Supervision strategy creation should not throw exceptions");
    }
}