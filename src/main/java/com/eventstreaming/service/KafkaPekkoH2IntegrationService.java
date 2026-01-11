package com.eventstreaming.service;

import com.eventstreaming.cluster.UserActorEvent;
import com.eventstreaming.model.UserEvent;
import com.eventstreaming.persistence.H2EventJournal;
import com.eventstreaming.kafka.KafkaEventStreamingService;
import com.eventstreaming.kafka.PekkoKafkaStreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service that integrates Kafka streaming with Pekko processing and H2 event journal.
 * Provides a complete event-driven architecture demonstration.
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaPekkoH2IntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaPekkoH2IntegrationService.class);
    
    public KafkaPekkoH2IntegrationService() {
        System.out.println("\n" + "🔥".repeat(100));
        System.out.println("🔥🔥🔥 KAFKAPEKKO-H2-INTEGRATION-SERVICE CONSTRUCTOR CALLED 🔥🔥🔥");
        System.out.println("🔥🔥🔥 THIS MEANS THE NEW JAR WITH CONDITIONAL ANNOTATIONS IS BEING USED 🔥🔥🔥");
        System.out.println("🔥🔥🔥 IF YOU SEE THIS, app.kafka.enabled=true OR CONDITIONAL FAILED 🔥🔥🔥");
        System.out.println("🔥".repeat(100) + "\n");
    }
    
    @Autowired
    private PersistentUserService persistentUserService;
    
    @Autowired(required = false)
    private H2EventJournal h2EventJournal;
    
    @Autowired
    private Environment environment;
    
    @Autowired(required = false)
    private KafkaEventStreamingService kafkaEventStreamingService;
    
    @Autowired(required = false)
    private PekkoKafkaStreamingService pekkoKafkaStreamingService;
    
    @Autowired(required = false)
    private DashboardService dashboardService;
    
    private long sequenceCounter = 1L;
    
    /**
     * Process a streaming event through the complete pipeline:
     * Kafka -> Pekko -> H2 Event Journal
     */
    public Map<String, Object> processStreamingEvent(UserEvent userEvent) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Processing streaming event for user: {} type: {}", 
                userEvent.getUserId(), userEvent.getEventType());
            
            // Set defaults if not provided
            if (userEvent.getEventId() == null) {
                userEvent.setEventId(UUID.randomUUID().toString());
            }
            if (userEvent.getTimestamp() == null) {
                userEvent.setTimestamp(LocalDateTime.now());
            }
            if (userEvent.getSource() == null) {
                userEvent.setSource("KafkaPekkoH2Integration");
            }
            
            // Record Kafka stage (simulated since this is integration service)
            recordPipelineEvent("kafka", userEvent.getEventType(), userEvent.getUserId(), 
                java.time.Duration.ofMillis(5));
            
            // Process through Pekko persistent actors (this also stores in H2 for isolated profile)
            var actorResponse = persistentUserService.processEvent(
                convertToEmailEvent(userEvent)
            ).toCompletableFuture().get();
            
            // Record Pekko processing
            recordPipelineEvent("pekko", userEvent.getEventType(), userEvent.getUserId(), 
                java.time.Duration.ofMillis(System.currentTimeMillis() - startTime));
            
            // Additional H2 storage for streaming metadata
            storeStreamingMetadata(userEvent);
            
            // Record H2 persistence
            if (actorResponse.success) {
                recordPipelineEvent("h2", userEvent.getEventType(), userEvent.getUserId(), 
                    java.time.Duration.ofMillis(System.currentTimeMillis() - startTime));
            } else {
                recordPipelineError("h2", actorResponse.message, userEvent.getEventType(), userEvent.getUserId());
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", actorResponse.success);
            result.put("message", "Event processed through Kafka->Pekko->H2 pipeline");
            result.put("userId", userEvent.getUserId());
            result.put("eventId", userEvent.getEventId());
            result.put("processedAt", actorResponse.processedAt);
            result.put("pipeline", "kafka-pekko-h2");
            result.put("actorResponse", actorResponse.message);
            
            logger.info("Successfully processed streaming event: {} for user: {}", 
                userEvent.getEventId(), userEvent.getUserId());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to process streaming event for user: {}", userEvent.getUserId(), e);
            
            // Record pipeline error
            recordPipelineError("pekko", e.getMessage(), userEvent.getEventType(), userEvent.getUserId());
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Streaming pipeline error: " + e.getMessage());
            error.put("userId", userEvent.getUserId());
            error.put("eventId", userEvent.getEventId());
            error.put("pipeline", "kafka-pekko-h2");
            
            return error;
        }
    }
    
    /**
     * Process multiple streaming events in batch.
     */
    public Map<String, Object> processStreamingEventBatch(List<UserEvent> userEvents) {
        try {
            logger.info("Processing streaming event batch of {} events", userEvents.size());
            
            int successCount = 0;
            int errorCount = 0;
            
            for (UserEvent event : userEvents) {
                try {
                    Map<String, Object> result = processStreamingEvent(event);
                    if ((Boolean) result.get("success")) {
                        successCount++;
                    } else {
                        errorCount++;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to process event in batch: {}", e.getMessage());
                    errorCount++;
                }
            }
            
            Map<String, Object> batchResult = new HashMap<>();
            batchResult.put("success", errorCount == 0);
            batchResult.put("message", String.format("Batch processed: %d success, %d errors", successCount, errorCount));
            batchResult.put("totalEvents", userEvents.size());
            batchResult.put("successCount", successCount);
            batchResult.put("errorCount", errorCount);
            batchResult.put("pipeline", "kafka-pekko-h2-batch");
            batchResult.put("processedAt", LocalDateTime.now());
            
            logger.info("Completed streaming batch: {} success, {} errors", successCount, errorCount);
            return batchResult;
            
        } catch (Exception e) {
            logger.error("Failed to process streaming event batch", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Batch processing error: " + e.getMessage());
            error.put("totalEvents", userEvents.size());
            error.put("pipeline", "kafka-pekko-h2-batch");
            
            return error;
        }
    }
    
    /**
     * Get streaming statistics from H2 journal.
     */
    public Map<String, Object> getStreamingStatistics() {
        try {
            logger.info("Retrieving streaming statistics from H2 journal");
            
            if (h2EventJournal == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "H2 Event Journal not available (using MySQL persistence)");
                error.put("journalType", "mysql");
                return error;
            }
            
            List<String> allPersistenceIds = h2EventJournal.getAllPersistenceIds();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalPersistenceIds", allPersistenceIds.size());
            stats.put("persistenceIds", allPersistenceIds);
            
            // Count events by persistence ID
            Map<String, Long> eventCounts = new HashMap<>();
            long totalEvents = 0;
            
            for (String persistenceId : allPersistenceIds) {
                long count = h2EventJournal.getEventCount(persistenceId);
                eventCounts.put(persistenceId, count);
                totalEvents += count;
            }
            
            stats.put("eventCountsByPersistenceId", eventCounts);
            stats.put("totalEventsInJournal", totalEvents);
            stats.put("averageEventsPerUser", allPersistenceIds.isEmpty() ? 0 : totalEvents / allPersistenceIds.size());
            stats.put("journalType", "h2");
            stats.put("retrievedAt", LocalDateTime.now());
            
            logger.info("Streaming statistics: {} users, {} total events", allPersistenceIds.size(), totalEvents);
            return stats;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve streaming statistics", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve streaming statistics: " + e.getMessage());
            error.put("journalType", "h2");
            
            return error;
        }
    }
    
    /**
     * Store additional streaming metadata in H2.
     */
    private void storeStreamingMetadata(UserEvent userEvent) {
        try {
            boolean isIsolatedProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("isolated");
            
            if (isIsolatedProfile && h2EventJournal != null) {
                String persistenceId = "StreamingMetadata|" + userEvent.getUserId();
                
                // Create a streaming metadata event
                UserActorEvent.UserEventProcessed metadataEvent = UserActorEvent.UserEventProcessed.from(userEvent);
                
                long sequenceNr = getNextSequenceNr(persistenceId);
                h2EventJournal.persistEvent(persistenceId, sequenceNr, metadataEvent);
                
                logger.debug("Stored streaming metadata for user: {} seq: {}", userEvent.getUserId(), sequenceNr);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to store streaming metadata (non-critical): {}", e.getMessage());
        }
    }
    
    /**
     * Convert UserEvent to EmailEvent for processing.
     */
    private com.eventstreaming.model.EmailEvent convertToEmailEvent(UserEvent userEvent) {
        com.eventstreaming.model.EventType eventType;
        try {
            eventType = com.eventstreaming.model.EventType.valueOf(userEvent.getEventType().toUpperCase());
        } catch (IllegalArgumentException e) {
            eventType = com.eventstreaming.model.EventType.EMAIL_DELIVERY;
        }
        
        return new com.eventstreaming.model.EmailEvent(
            userEvent.getUserId(),
            userEvent.getEventId(),
            eventType,
            userEvent.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant()
        );
    }
    
    /**
     * Send event to Kafka topics via Pekko streaming (simulated - events will be processed when consumed).
     * Note: This simulates sending to Kafka by processing the event through our pipeline.
     * In a real scenario, you would use a Kafka producer within Pekko Streams.
     */
    public Map<String, Object> sendEventToKafka(UserEvent userEvent, String topic) {
        try {
            logger.info("Processing event for Kafka topic: {} for user: {} (via Pekko pipeline)", topic, userEvent.getUserId());
            
            // Since we're using Pekko Kafka streaming (consumer-only), we simulate "sending" 
            // by processing the event through our pipeline and marking it as destined for the topic
            userEvent.setSource("kafka-topic-" + topic);
            
            // Process through the normal pipeline (this will store in H2)
            Map<String, Object> pipelineResult = processStreamingEvent(userEvent);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", (Boolean) pipelineResult.get("success"));
            result.put("message", "Event processed for Kafka topic: " + topic + " (via Pekko pipeline)");
            result.put("topic", topic);
            result.put("userId", userEvent.getUserId());
            result.put("eventId", userEvent.getEventId());
            result.put("sentAt", LocalDateTime.now());
            result.put("method", "pekko-pipeline");
            result.put("pipelineResult", pipelineResult);
            
            logger.info("Event processed for Kafka topic: {} via Pekko pipeline", topic);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to process event for Kafka topic: {}", topic, e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to process for Kafka topic: " + e.getMessage());
            error.put("topic", topic);
            error.put("userId", userEvent.getUserId());
            error.put("method", "pekko-pipeline");
            
            return error;
        }
    }
    
    /**
     * Process event through complete pipeline with Kafka topic simulation.
     */
    public Map<String, Object> processEventWithKafka(UserEvent userEvent, List<String> kafkaTopics) {
        try {
            logger.info("Processing event with Pekko Kafka integration for user: {}", userEvent.getUserId());
            
            // Process through pipeline for each topic (simulating multi-topic processing)
            Map<String, Object> kafkaResults = new HashMap<>();
            Map<String, Object> mainPipelineResult = null;
            
            for (String topic : kafkaTopics) {
                // Create a copy of the event for each topic
                UserEvent topicEvent = new UserEvent();
                topicEvent.setUserId(userEvent.getUserId());
                topicEvent.setEventType(userEvent.getEventType());
                topicEvent.setContactId(userEvent.getContactId());
                topicEvent.setEventId(userEvent.getEventId() + "-" + topic);
                topicEvent.setTimestamp(userEvent.getTimestamp());
                topicEvent.setSource("pekko-kafka-" + topic);
                topicEvent.setEventData(userEvent.getEventData());
                
                Map<String, Object> topicResult = processStreamingEvent(topicEvent);
                kafkaResults.put(topic, topicResult);
                
                if (mainPipelineResult == null) {
                    mainPipelineResult = topicResult;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", mainPipelineResult != null ? (Boolean) mainPipelineResult.get("success") : true);
            result.put("message", "Event processed through Pekko pipeline for Kafka topics");
            result.put("userId", userEvent.getUserId());
            result.put("eventId", userEvent.getEventId());
            result.put("pipeline", "pekko-kafka-h2-with-topics");
            result.put("kafkaTopics", kafkaTopics);
            result.put("kafkaResults", kafkaResults);
            result.put("processedAt", LocalDateTime.now());
            result.put("method", "pekko-streaming");
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to process event with Pekko Kafka integration", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Pekko Kafka pipeline error: " + e.getMessage());
            error.put("userId", userEvent.getUserId());
            error.put("pipeline", "pekko-kafka-h2-with-topics");
            error.put("method", "pekko-streaming");
            
            return error;
        }
    }
    
    /**
     * Get Pekko Kafka streaming status and statistics.
     */
    public Map<String, Object> getKafkaStreamingStatus() {
        try {
            logger.info("Retrieving Pekko Kafka streaming status");
            
            Map<String, Object> status = new HashMap<>();
            
            // Spring Kafka is disabled - only show status
            status.put("springKafka", Map.of(
                "available", false,
                "status", "disabled",
                "description", "Spring Kafka disabled - using Pekko Streams only"
            ));
            
            // Check Pekko Kafka service
            if (pekkoKafkaStreamingService != null) {
                boolean isRunning = pekkoKafkaStreamingService.isRunning();
                status.put("pekkoKafka", Map.of(
                    "available", true,
                    "running", isRunning,
                    "status", isRunning ? "running" : "stopped",
                    "description", "Pekko Streams Kafka consumer service",
                    "topics", List.of("QuestIncrementerDropperINSCallQueueDev", "QuestIncrementerDropperINSDigitalQueueDev")
                ));
            } else {
                status.put("pekkoKafka", Map.of(
                    "available", false,
                    "running", false,
                    "status", "disabled",
                    "description", "Pekko Kafka service not available (check app.streams.pekko-kafka.enabled)"
                ));
            }
            
            // Overall status - only Pekko Kafka matters
            boolean pekkoKafkaAvailable = pekkoKafkaStreamingService != null;
            status.put("overall", Map.of(
                "kafkaIntegrationAvailable", pekkoKafkaAvailable,
                "integrationMethod", "pekko-streams-only",
                "topics", List.of("QuestIncrementerDropperINSCallQueueDev", "QuestIncrementerDropperINSDigitalQueueDev"),
                "checkedAt", LocalDateTime.now()
            ));
            
            return status;
            
        } catch (Exception e) {
            logger.error("Failed to get Pekko Kafka streaming status", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get Pekko Kafka status: " + e.getMessage());
            error.put("checkedAt", LocalDateTime.now());
            
            return error;
        }
    }
    
    /**
     * Test Pekko Kafka integration by processing test events through the pipeline.
     */
    public Map<String, Object> testKafkaIntegration() {
        try {
            logger.info("Testing Pekko Kafka integration");
            
            // Create test event
            UserEvent testEvent = new UserEvent();
            testEvent.setUserId("pekko-kafka-test-user");
            testEvent.setEventType("PEKKO_KAFKA_TEST");
            testEvent.setContactId(99999L);
            testEvent.setSource("pekko-kafka-integration-test");
            testEvent.setEventId(UUID.randomUUID().toString());
            testEvent.setTimestamp(LocalDateTime.now());
            
            // Test topics
            List<String> testTopics = List.of(
                "QuestIncrementerDropperINSCallQueueDev",
                "QuestIncrementerDropperINSDigitalQueueDev"
            );
            
            // Process with Pekko Kafka pipeline
            Map<String, Object> result = processEventWithKafka(testEvent, testTopics);
            
            result.put("testType", "pekko-kafka-integration");
            result.put("testEventId", testEvent.getEventId());
            result.put("testedTopics", testTopics);
            result.put("integrationMethod", "pekko-streams");
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to test Pekko Kafka integration", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Pekko Kafka integration test failed: " + e.getMessage());
            error.put("testType", "pekko-kafka-integration");
            error.put("integrationMethod", "pekko-streams");
            
            return error;
        }
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
    
    /**
     * Record pipeline event for dashboard tracking.
     */
    private void recordPipelineEvent(String stage, String eventType, String userId, java.time.Duration processingTime) {
        try {
            if (dashboardService != null) {
                dashboardService.recordPipelineEvent(stage, eventType, userId, processingTime);
            }
        } catch (Exception e) {
            logger.debug("Failed to record pipeline event: {}", e.getMessage());
        }
    }
    
    /**
     * Record pipeline error for dashboard tracking.
     */
    private void recordPipelineError(String stage, String errorMessage, String eventType, String userId) {
        try {
            if (dashboardService != null) {
                dashboardService.recordPipelineError(stage, errorMessage, eventType, userId);
            }
        } catch (Exception e) {
            logger.debug("Failed to record pipeline error: {}", e.getMessage());
        }
    }
}