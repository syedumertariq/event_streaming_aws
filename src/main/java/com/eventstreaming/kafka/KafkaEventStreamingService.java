package com.eventstreaming.kafka;

import com.eventstreaming.model.UserEvent;
import com.eventstreaming.service.UserEventAggregationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service that integrates Kafka messaging with the event streaming application.
 * This service consumes messages from Kafka topics and processes them through
 * the existing event streaming infrastructure.
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaEventStreamingService {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaEventStreamingService.class);
    // Removed unused TIME_FORMATTER
    
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final UserEventAggregationService aggregationService;
    
    @Autowired
    public KafkaEventStreamingService(ObjectMapper objectMapper, 
                                    KafkaTemplate<String, String> kafkaTemplate,
                                    UserEventAggregationService aggregationService) {
        System.out.println("\n" + "🚨".repeat(100));
        System.out.println("🚨🚨🚨 KAFKA-EVENT-STREAMING-SERVICE CONSTRUCTOR CALLED 🚨🚨🚨");
        System.out.println("🚨🚨🚨 THIS MEANS THE NEW JAR WITH CONDITIONAL ANNOTATIONS IS BEING USED 🚨🚨🚨");
        System.out.println("🚨🚨🚨 IF YOU SEE THIS, app.kafka.enabled=true OR CONDITIONAL FAILED 🚨🚨🚨");
        System.out.println("🚨".repeat(100) + "\n");
        
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.aggregationService = aggregationService;
    }
    
    /**
     * Listens to the QuestIncrementerDropperINSCallQueueDev topic for incoming messages.
     * Processes messages and converts them to UserEvent objects for the event streaming system.
     */
    @KafkaListener(topics = "QuestIncrementerDropperINSCallQueueDev", 
                   groupId = "${spring.kafka.consumer.group-id}",
                   containerFactory = "stringKafkaListenerContainerFactory")
    public void handleCallQueueMessage(ConsumerRecord<String, String> record) {
        try {
            logger.info("=== RECEIVED CALLQUEUE MESSAGE ===");
            logger.info("Topic: {}", record.topic());
            logger.info("Key: {}", record.key());
            logger.info("Value: {}", record.value());
            logger.info("Partition: {}, Offset: {}", record.partition(), record.offset());
            logger.info("Timestamp: {}", record.timestamp());
            logger.info("Headers: {}", record.headers());
            logger.info("=====================================");
            
            UserEvent userEvent = parseKafkaMessage(record);
            if (userEvent != null) {
                processUserEvent(userEvent);
            }
            
        } catch (Exception e) {
            logger.error("Error processing CallQueue message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Listens to the QuestIncrementerDropperINSDigitalQueueDev topic for incoming messages.
     * Processes messages and converts them to UserEvent objects for the event streaming system.
     */
    @KafkaListener(topics = "QuestIncrementerDropperINSDigitalQueueDev", 
                   groupId = "${spring.kafka.consumer.group-id}",
                   containerFactory = "stringKafkaListenerContainerFactory")
    public void handleDigitalQueueMessage(ConsumerRecord<String, String> record) {
        try {
            logger.info("=== RECEIVED DIGITALQUEUE MESSAGE ===");
            logger.info("Topic: {}", record.topic());
            logger.info("Key: {}", record.key());
            logger.info("Value: {}", record.value());
            logger.info("Partition: {}, Offset: {}", record.partition(), record.offset());
            logger.info("Timestamp: {}", record.timestamp());
            logger.info("Headers: {}", record.headers());
            logger.info("======================================");
            
            UserEvent userEvent = parseKafkaMessage(record);
            if (userEvent != null) {
                processUserEvent(userEvent);
            }
            
        } catch (Exception e) {
            logger.error("Error processing DigitalQueue message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Parses a Kafka message and converts it to a UserEvent.
     * Supports two formats:
     * 1. Key format: timestamp@userid:DROP:contactid (structured key)
     * 2. Value format: timestamp@userid:DROP:contactid (when key is just a hash)
     * 
     * @param record The Kafka consumer record
     * @return UserEvent object or null if parsing fails
     */
    private UserEvent parseKafkaMessage(ConsumerRecord<String, String> record) {
        try {
            String key = record.key();
            String value = record.value();
            
            logger.info("--- PARSING MESSAGE ---");
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
                        logger.info("Using KEY for parsing (structured format detected)");
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
                        logger.info("Using VALUE for parsing (key appears to be hash: '{}')", key);
                    }
                }
            }
            
            if (dataSource == null) {
                logger.warn("No structured data found in key or value. Key: '{}', Value: '{}'", key, value);
                return null;
            }
            
            // Parse the structured data: TS1761078201761@aa806415340acc676593b725919ce29603e40aa1bb993f03057e8b962725a3eb:DROP:84214:12951617384
            // Format: timestamp@userid:action:contactid:additional_data
            String[] dataParts = dataSource.split(":");
            logger.info("Data split into {} parts: {}", dataParts.length, java.util.Arrays.toString(dataParts));
            
            if (dataParts.length < 3) {
                logger.warn("Invalid data format - expected at least 3 parts separated by ':', got {} parts: {}", 
                           dataParts.length, dataSource);
                return null;
            }
            
            // Extract timestamp and user ID from first part
            String timestampUserPart = dataParts[0]; // TS1761078201761@aa806415340acc676593b725919ce29603e40aa1bb993f03057e8b962725a3eb
            logger.info("Timestamp-User part: '{}'", timestampUserPart);
            
            String[] timestampUser = timestampUserPart.split("@");
            logger.info("Timestamp-User split into {} parts: {}", timestampUser.length, java.util.Arrays.toString(timestampUser));
            
            if (timestampUser.length != 2) {
                logger.warn("Invalid timestamp@userid format - expected 2 parts separated by '@', got {} parts: {}", 
                           timestampUser.length, timestampUserPart);
                return null;
            }
            
            // Extract the components
            String timestampStr = timestampUser[0]; // TS1761078201761
            String userId = timestampUser[1]; // aa806415340acc676593b725919ce29603e40aa1bb993f03057e8b962725a3eb
            String action = dataParts[1]; // "DROP"
            String contactId = dataParts[2]; // "84214"
            
            logger.info("Extracted - Timestamp: '{}', UserId: '{}', Action: '{}', ContactId: '{}'", 
                       timestampStr, userId, action, contactId);
            
            // Create UserEvent
            UserEvent userEvent = new UserEvent();
            userEvent.setUserId(userId);
            userEvent.setEventType(action);
            userEvent.setContactId(Long.parseLong(contactId));
            userEvent.setTimestamp(LocalDateTime.now()); // Use current time for processing
            
            // Set event data based on source
            if (useKeyForParsing) {
                // If we parsed from key, value is the event data
                userEvent.setEventData(value != null ? value : "");
            } else {
                // If we parsed from value, use the original structured data as event data
                userEvent.setEventData(dataSource);
            }
            
            // Add Kafka metadata
            userEvent.setSource("kafka-" + record.topic());
            userEvent.setPartition(record.partition());
            userEvent.setOffset(record.offset());
            
            logger.info("Successfully parsed UserEvent: userId={}, eventType={}, contactId={}, source={}", 
                       userId, action, contactId, userEvent.getSource());
            
            return userEvent;
            
        } catch (Exception e) {
            logger.error("Error parsing Kafka message - Key: '{}', Value: '{}', Error: {}", 
                        record.key(), record.value(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Processes a UserEvent through the existing event streaming infrastructure.
     * This method integrates with your existing Pekko actors and event processing logic.
     * 
     * @param userEvent The user event to process
     */
    private void processUserEvent(UserEvent userEvent) {
        try {
            // Log the event for monitoring
            logger.info("Processing UserEvent: userId={}, eventType={}, contactId={}, source={}", 
                       userEvent.getUserId(), userEvent.getEventType(), 
                       userEvent.getContactId(), userEvent.getSource());
            
            // Process event for aggregation
            aggregationService.processEvent(userEvent);
            
            // Here you would integrate with your existing event processing system
            // For example, sending to Pekko actors, storing in database, etc.
            
            // Example: Send to your existing event processing pipeline
            // eventProcessingService.processEvent(userEvent);
            // userActorSystem.tell(userEvent);
            
            // For now, we'll just log the successful processing
            logger.info("Successfully processed UserEvent from Kafka: {}", userEvent.getUserId());
            
        } catch (Exception e) {
            logger.error("Error processing UserEvent: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a message to a Kafka topic.
     * This can be used to publish events back to Kafka from your application.
     * 
     * @param topic The Kafka topic to send to
     * @param key The message key
     * @param message The message content
     */
    public void sendMessage(String topic, String key, String message) {
        try {
            kafkaTemplate.send(topic, key, message);
            logger.info("Sent message to Kafka topic '{}' with key '{}'", topic, key);
        } catch (Exception e) {
            logger.error("Error sending message to Kafka topic '{}': {}", topic, e.getMessage(), e);
        }
    }
    
    /**
     * Sends a UserEvent as a JSON message to a Kafka topic.
     * 
     * @param topic The Kafka topic to send to
     * @param userEvent The UserEvent to send
     */
    public void sendUserEvent(String topic, UserEvent userEvent) {
        try {
            String key = userEvent.getUserId() + ":" + userEvent.getEventType() + ":" + userEvent.getContactId();
            String message = objectMapper.writeValueAsString(userEvent);
            sendMessage(topic, key, message);
        } catch (JsonProcessingException e) {
            logger.error("Error serializing UserEvent to JSON: {}", e.getMessage(), e);
        }
    }
}