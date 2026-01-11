package com.eventstreaming.kafka;

import com.eventstreaming.model.UserEvent;
import com.eventstreaming.service.UserEventAggregationService;
import com.eventstreaming.service.PersistentUserService;
import com.eventstreaming.service.DashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pekko Streams Kafka consumer that uses the working Spring Kafka configuration.
 * Provides reactive streaming with backpressure, user affinity, and fault tolerance.
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class PekkoKafkaStreamingService {
    
    private static final Logger logger = LoggerFactory.getLogger(PekkoKafkaStreamingService.class);
    
    private final ActorSystem<Void> actorSystem;
    private final ObjectMapper objectMapper;
    private final KafkaConfigManager kafkaConfigManager;
    private final UserEventAggregationService aggregationService;
    private final PersistentUserService persistentUserService;
    private final DashboardService dashboardService;
    
    private volatile Consumer.Control streamControl;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    @Value("${app.streams.pekko-kafka.parallelism:10}")
    private int processingParallelism;
    
    @Value("${app.streams.pekko-kafka.buffer-size:1000}")
    private int bufferSize;
    
    @Value("${app.streams.pekko-kafka.auto-start:true}")
    private boolean autoStart;
    
    @Value("${spring.profiles.active:default}")
    private String activeProfile;
    
    @Autowired
    public PekkoKafkaStreamingService(ActorSystem<Void> actorSystem,
                                     ObjectMapper objectMapper,
                                     KafkaConfigManager kafkaConfigManager,
                                     UserEventAggregationService aggregationService,
                                     PersistentUserService persistentUserService,
                                     DashboardService dashboardService) {
        this.actorSystem = actorSystem;
        this.objectMapper = objectMapper;
        this.kafkaConfigManager = kafkaConfigManager;
        this.aggregationService = aggregationService;
        this.persistentUserService = persistentUserService;
        this.dashboardService = dashboardService;
    }
    
    @PostConstruct
    public void initialize() {
        if (autoStart) {
            logger.info("Auto-starting Pekko Kafka streaming service");
            startStreaming();
        } else {
            logger.info("Pekko Kafka streaming service initialized but not auto-started");
        }
    }
    
    /**
     * Determine the appropriate configuration file based on active profile.
     */
    private String determineConfigFile() {
        if (activeProfile.contains("isolated")) {
            return "src/main/resources/application-isolated.yml";
        } else if (activeProfile.contains("cluster-mysql")) {
            return "src/main/resources/application-cluster-mysql.yml";
        } else if (activeProfile.contains("aws")) {
            return "src/main/resources/application-aws.yml";
        } else {
            // Default fallback
            return "src/main/resources/application-isolated.yml";
        }
    }
    
    /**
     * Starts the Pekko Streams Kafka consumer using your working configuration.
     */
    public synchronized void startStreaming() {
        if (isRunning.get()) {
            logger.warn("Pekko Kafka streaming is already running");
            return;
        }
        
        try {
            logger.info("Starting Pekko Kafka streaming with parallelism={}, bufferSize={}", 
                       processingParallelism, bufferSize);
            
            // Load Kafka configuration based on active profile
            String configFile = determineConfigFile();
            var kafkaConfig = kafkaConfigManager.loadConfigurationFromYaml(configFile);
            
            // Create Pekko consumer settings using your working config
            ConsumerSettings<String, String> consumerSettings = 
                ConsumerSettings.create(actorSystem, new StringDeserializer(), new StringDeserializer())
                    .withBootstrapServers(kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"))
                    .withGroupId("pekko-event-streaming-consumer")
                    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                    .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                    .withProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500")
                    .withProperty(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1024")
                    .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
                    .withProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000");
            
            // Create the reactive stream pipeline
            CompletionStage<Done> streamCompletion = Consumer.plainSource(
                    consumerSettings,
                    Subscriptions.topics("QuestIncrementerDropperINSCallQueueDev", "QuestIncrementerDropperINSDigitalQueueDev")
                )
                .buffer(bufferSize, org.apache.pekko.stream.OverflowStrategy.backpressure())
                .map(this::logIncomingMessage)
                .map(this::parseKafkaMessage)
                .filter(userEvent -> userEvent != null)
                .groupBy(Integer.MAX_VALUE, userEvent -> userEvent.getUserId()) // User affinity grouping
                .mapAsync(processingParallelism, this::processUserEventAsync)
                .mergeSubstreams()
                .toMat(Sink.foreach(this::logProcessingResult), (control, completion) -> {
                    this.streamControl = control;
                    return completion;
                })
                .run(actorSystem);
            
            isRunning.set(true);
            logger.info("Pekko Kafka streaming started successfully");
            
            // Handle completion and errors
            streamCompletion.whenComplete((done, throwable) -> {
                if (throwable != null) {
                    logger.error("Pekko Kafka streaming completed with error", throwable);
                } else {
                    logger.info("Pekko Kafka streaming completed successfully");
                }
                isRunning.set(false);
            });
            
        } catch (Exception e) {
            logger.error("Failed to start Pekko Kafka streaming", e);
            isRunning.set(false);
            throw new RuntimeException("Pekko Kafka streaming startup failed", e);
        }
    }
    
    /**
     * Logs incoming Kafka messages with detailed information.
     */
    private ConsumerRecord<String, String> logIncomingMessage(ConsumerRecord<String, String> record) {
        logger.info("=== PEKKO STREAMS RECEIVED MESSAGE ===");
        logger.info("Topic: {}", record.topic());
        logger.info("Key: {}", record.key());
        logger.info("Value: {}", record.value());
        logger.info("Partition: {}, Offset: {}", record.partition(), record.offset());
        logger.info("Timestamp: {}", record.timestamp());
        logger.info("=====================================");
        
        // Record Kafka event received
        try {
            String eventType = extractEventTypeFromMessage(record.key(), record.value());
            String userId = extractUserIdFromMessage(record.key(), record.value());
            recordPipelineEvent("kafka", eventType, userId, java.time.Duration.ofMillis(10));
        } catch (Exception e) {
            logger.warn("Failed to record Kafka pipeline event: {}", e.getMessage());
        }
        
        return record;
    }
    
    /**
     * Extract event type from Kafka message for pipeline tracking.
     */
    private String extractEventTypeFromMessage(String key, String value) {
        try {
            String dataSource = key != null && key.contains(":") ? key : value;
            if (dataSource != null && dataSource.contains(":")) {
                String[] parts = dataSource.split(":");
                if (parts.length >= 2) {
                    return parts[1]; // Event type is typically the second part
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract event type from message");
        }
        return "UNKNOWN";
    }
    
    /**
     * Extract user ID from Kafka message for pipeline tracking.
     */
    private String extractUserIdFromMessage(String key, String value) {
        try {
            String dataSource = key != null && key.contains("@") ? key : value;
            if (dataSource != null && dataSource.contains("@")) {
                String[] parts = dataSource.split("@");
                if (parts.length >= 2) {
                    return parts[1].split(":")[0]; // User ID is after @ and before next :
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract user ID from message");
        }
        return "unknown-user";
    }
    
    /**
     * Parses Kafka message using the same logic as Spring Kafka service.
     * Supports both key-based and value-based parsing.
     */
    private UserEvent parseKafkaMessage(ConsumerRecord<String, String> record) {
        try {
            String key = record.key();
            String value = record.value();
            
            logger.info("--- PEKKO PARSING MESSAGE ---");
            logger.info("Raw Key: '{}'", key);
            logger.info("Raw Value: '{}'", value);
            logger.info("Key Length: {}", key != null ? key.length() : 0);
            logger.info("Value Length: {}", value != null ? value.length() : 0);
            
            // Determine which format to use for parsing
            String dataSource = null;
            boolean useKeyForParsing = false;
            
            if (key != null && !key.trim().isEmpty()) {
                // Check if key contains structured data (has colons and @)
                if (key.contains(":") && key.contains("@")) {
                    String[] keyParts = key.split(":");
                    if (keyParts.length >= 3 && keyParts[0].contains("@")) {
                        dataSource = key;
                        useKeyForParsing = true;
                        logger.info("PEKKO: Using KEY for parsing (structured format detected)");
                    }
                }
            }
            
            // If key doesn't have structured data, try to use value
            if (dataSource == null && value != null && !value.trim().isEmpty()) {
                // Check if value contains structured data
                if (value.contains(":") && value.contains("@")) {
                    String[] valueParts = value.split(":");
                    if (valueParts.length >= 3 && valueParts[0].contains("@")) {
                        dataSource = value;
                        useKeyForParsing = false;
                        logger.info("PEKKO: Using VALUE for parsing (key appears to be hash: '{}')", key);
                    }
                }
            }
            
            if (dataSource == null) {
                logger.warn("PEKKO: No structured data found in key or value. Key: '{}', Value: '{}'", key, value);
                return null;
            }
            
            // Parse the structured data
            String[] dataParts = dataSource.split(":");
            logger.info("PEKKO: Data split into {} parts: {}", dataParts.length, java.util.Arrays.toString(dataParts));
            
            if (dataParts.length < 3) {
                logger.warn("PEKKO: Invalid data format - expected at least 3 parts separated by ':', got {} parts: {}", 
                           dataParts.length, dataSource);
                return null;
            }
            
            // Extract timestamp and user ID from first part
            String timestampUserPart = dataParts[0];
            logger.info("PEKKO: Timestamp-User part: '{}'", timestampUserPart);
            
            String[] timestampUser = timestampUserPart.split("@");
            logger.info("PEKKO: Timestamp-User split into {} parts: {}", timestampUser.length, java.util.Arrays.toString(timestampUser));
            
            if (timestampUser.length != 2) {
                logger.warn("PEKKO: Invalid timestamp@userid format - expected 2 parts separated by '@', got {} parts: {}", 
                           timestampUser.length, timestampUserPart);
                return null;
            }
            
            // Extract the components
            String timestampStr = timestampUser[0];
            String userId = timestampUser[1];
            String action = dataParts[1];
            String contactId = dataParts[2];
            
            logger.info("PEKKO: Extracted - Timestamp: '{}', UserId: '{}', Action: '{}', ContactId: '{}'", 
                       timestampStr, userId, action, contactId);
            
            // Create UserEvent
            UserEvent userEvent = new UserEvent();
            userEvent.setEventId(java.util.UUID.randomUUID().toString());
            userEvent.setUserId(userId);
            userEvent.setEventType(action);
            userEvent.setContactId(Long.parseLong(contactId));
            userEvent.setTimestamp(LocalDateTime.now());
            
            // Set event data based on source
            if (useKeyForParsing) {
                userEvent.setEventData(value != null ? value : "");
            } else {
                userEvent.setEventData(dataSource);
            }
            
            // Add Kafka metadata
            userEvent.setSource("pekko-kafka-" + record.topic());
            userEvent.setPartition(record.partition());
            userEvent.setOffset(record.offset());
            
            logger.info("PEKKO: Successfully parsed UserEvent: userId={}, eventType={}, contactId={}, source={}", 
                       userId, action, contactId, userEvent.getSource());
            
            return userEvent;
            
        } catch (Exception e) {
            logger.error("PEKKO: Error parsing Kafka message - Key: '{}', Value: '{}', Error: {}", 
                        record.key(), record.value(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Processes UserEvent asynchronously with user affinity.
     * This is where you'd integrate with your Pekko Cluster UserActors.
     */
    private CompletionStage<ProcessingResult> processUserEventAsync(UserEvent userEvent) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                logger.info("PEKKO: Processing UserEvent: userId={}, eventType={}, contactId={}, source={}", 
                           userEvent.getUserId(), userEvent.getEventType(), 
                           userEvent.getContactId(), userEvent.getSource());
                
                // Record Pekko processing start
                recordPipelineEvent("pekko", userEvent.getEventType(), userEvent.getUserId(), 
                    java.time.Duration.ofMillis(System.currentTimeMillis() - startTime));
                
                // Process event for aggregation
                aggregationService.processEvent(userEvent);
                
                // Process through PersistentUserService to store in H2 event journal
                var emailEvent = convertToEmailEvent(userEvent);
                var persistentResult = persistentUserService.processEvent(emailEvent).toCompletableFuture().get();
                
                logger.info("PEKKO: Event stored in H2 journal: success={}, message={}", 
                           persistentResult.success, persistentResult.message);
                
                // Record H2 persistence
                if (persistentResult.success) {
                    recordPipelineEvent("h2", userEvent.getEventType(), userEvent.getUserId(), 
                        java.time.Duration.ofMillis(System.currentTimeMillis() - startTime));
                } else {
                    recordPipelineError("h2", persistentResult.message, userEvent.getEventType(), userEvent.getUserId());
                }
                
                // Small processing delay
                Thread.sleep(10);
                
                logger.info("PEKKO: Successfully processed UserEvent from Kafka: {}", userEvent.getUserId());
                
                return new ProcessingResult.Success(
                    userEvent.getEventId(),
                    userEvent.getUserId(),
                    LocalDateTime.now(),
                    userEvent
                );
                
            } catch (Exception e) {
                logger.error("PEKKO: Error processing UserEvent: {}", e.getMessage(), e);
                
                // Record pipeline error
                recordPipelineError("pekko", e.getMessage(), userEvent.getEventType(), userEvent.getUserId());
                
                return new ProcessingResult.Failed(
                    userEvent.getEventId(),
                    userEvent.getUserId(),
                    e.getMessage(),
                    userEvent
                );
            }
        }, actorSystem.executionContext());
    }
    
    /**
     * Logs processing results.
     */
    private void logProcessingResult(ProcessingResult result) {
        if (result instanceof ProcessingResult.Success success) {
            logger.info("PEKKO: ✅ Processing SUCCESS: {} for user {} at {}", 
                       success.eventId(), success.userId(), success.processedAt());
        } else if (result instanceof ProcessingResult.Failed failed) {
            logger.error("PEKKO: ❌ Processing FAILED: {} for user {} - {}", 
                        failed.eventId(), failed.userId(), failed.reason());
        }
    }
    
    /**
     * Stops the Pekko Kafka streaming gracefully.
     */
    public synchronized CompletionStage<Done> stopStreaming() {
        if (!isRunning.get()) {
            logger.warn("Pekko Kafka streaming is not running");
            return java.util.concurrent.CompletableFuture.completedFuture(Done.getInstance());
        }
        
        logger.info("Stopping Pekko Kafka streaming...");
        
        if (streamControl != null) {
            return streamControl.shutdown()
                .whenComplete((done, throwable) -> {
                    if (throwable != null) {
                        logger.error("Error during Pekko Kafka streaming shutdown", throwable);
                    } else {
                        logger.info("Pekko Kafka streaming stopped successfully");
                    }
                    isRunning.set(false);
                });
        } else {
            isRunning.set(false);
            return java.util.concurrent.CompletableFuture.completedFuture(Done.getInstance());
        }
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down Pekko Kafka streaming service...");
        if (isRunning.get()) {
            try {
                stopStreaming().toCompletableFuture().get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("Error during graceful shutdown", e);
            }
        }
        logger.info("Pekko Kafka streaming service shutdown complete");
    }
    
    /**
     * Convert UserEvent to EmailEvent for processing by PersistentUserService.
     */
    private com.eventstreaming.model.EmailEvent convertToEmailEvent(UserEvent userEvent) {
        com.eventstreaming.model.EventType eventType;
        try {
            eventType = com.eventstreaming.model.EventType.valueOf(userEvent.getEventType().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Default to EMAIL_DELIVERY for unknown event types
            eventType = com.eventstreaming.model.EventType.EMAIL_DELIVERY;
            logger.warn("PEKKO: Unknown event type '{}', defaulting to EMAIL_DELIVERY", userEvent.getEventType());
        }
        
        return new com.eventstreaming.model.EmailEvent(
            userEvent.getUserId(),
            userEvent.getEventId(),
            eventType,
            userEvent.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant()
        );
    }
    
    /**
     * Gets the current status of the streaming service.
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Record pipeline event for dashboard tracking.
     */
    private void recordPipelineEvent(String stage, String eventType, String userId, java.time.Duration processingTime) {
        try {
            dashboardService.recordPipelineEvent(stage, eventType, userId, processingTime);
        } catch (Exception e) {
            logger.debug("Failed to record pipeline event: {}", e.getMessage());
        }
    }
    
    /**
     * Record pipeline error for dashboard tracking.
     */
    private void recordPipelineError(String stage, String errorMessage, String eventType, String userId) {
        try {
            dashboardService.recordPipelineError(stage, errorMessage, eventType, userId);
        } catch (Exception e) {
            logger.debug("Failed to record pipeline error: {}", e.getMessage());
        }
    }
    
    /**
     * Processing result classes.
     */
    public static abstract class ProcessingResult {
        public abstract String eventId();
        public abstract String userId();
        
        public static class Success extends ProcessingResult {
            private final String eventId;
            private final String userId;
            private final LocalDateTime processedAt;
            private final UserEvent userEvent;
            
            public Success(String eventId, String userId, LocalDateTime processedAt, UserEvent userEvent) {
                this.eventId = eventId;
                this.userId = userId;
                this.processedAt = processedAt;
                this.userEvent = userEvent;
            }
            
            @Override public String eventId() { return eventId; }
            @Override public String userId() { return userId; }
            public LocalDateTime processedAt() { return processedAt; }
            public UserEvent userEvent() { return userEvent; }
        }
        
        public static class Failed extends ProcessingResult {
            private final String eventId;
            private final String userId;
            private final String reason;
            private final UserEvent userEvent;
            
            public Failed(String eventId, String userId, String reason, UserEvent userEvent) {
                this.eventId = eventId;
                this.userId = userId;
                this.reason = reason;
                this.userEvent = userEvent;
            }
            
            @Override public String eventId() { return eventId; }
            @Override public String userId() { return userId; }
            public String reason() { return reason; }
            public UserEvent userEvent() { return userEvent; }
        }
    }
}