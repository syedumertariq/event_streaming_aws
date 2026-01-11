package com.eventstreaming.service;

import com.eventstreaming.actor.UserActor;
import com.eventstreaming.model.UserAggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing aggregation updates and Walker notifications to Kafka.
 * Replaces database polling with real-time messaging for Walker integration.
 */
@Service
public class AggregationNotificationPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(AggregationNotificationPublisher.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${app.kafka.topics.aggregation-updates}")
    private String aggregationUpdatesTopic;
    
    @Value("${app.kafka.topics.walker-notifications}")
    private String walkerNotificationsTopic;
    
    @Autowired
    public AggregationNotificationPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Publishes aggregation update notification for Walker consumption.
     * Uses user ID as partition key for consistent routing.
     */
    public CompletableFuture<SendResult<String, Object>> publishAggregationUpdate(
            String userId, UserAggregations aggregations, String triggerEventType, String triggerEventId) {
        
        UserActor.AggregationUpdate update = new UserActor.AggregationUpdate(
            userId,
            (int) aggregations.getEmailCount(),
            (int) aggregations.getSmsCount(),
            (int) aggregations.getCallCount(),
            aggregations.getLastUpdated(),
            triggerEventType,
            triggerEventId
        );
        
        log.debug("Publishing aggregation update for user {}: total events = {}", 
                 userId, aggregations.getTotalEvents());
        
        return kafkaTemplate.send(aggregationUpdatesTopic, userId, update)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish aggregation update for user {}: {}", userId, update, throwable);
                } else {
                    log.debug("Successfully published aggregation update for user {} to partition {} at offset {}", 
                             userId, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
    }
    
    /**
     * Publishes Walker notification for immediate processing.
     * Uses user ID as partition key for user affinity.
     */
    public CompletableFuture<SendResult<String, Object>> publishWalkerNotification(
            String userId, UserAggregations aggregations, String eventType) {
        
        UserActor.WalkerNotification notification = new UserActor.WalkerNotification(
            userId,
            (int) aggregations.getEmailCount(),
            (int) aggregations.getSmsCount(),
            (int) aggregations.getCallCount(),
            Instant.now(),
            eventType
        );
        
        log.debug("Publishing Walker notification for user {}: {}", userId, notification);
        
        return kafkaTemplate.send(walkerNotificationsTopic, userId, notification)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish Walker notification for user {}: {}", userId, notification, throwable);
                } else {
                    log.debug("Successfully published Walker notification for user {} to partition {} at offset {}", 
                             userId, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });
    }
    
    /**
     * Publishes both aggregation update and Walker notification in parallel.
     * Provides atomic notification publishing for complete Walker integration.
     */
    public CompletableFuture<Void> publishBothNotifications(
            String userId, UserAggregations aggregations, String triggerEventType, String triggerEventId) {
        
        CompletableFuture<SendResult<String, Object>> aggregationFuture = 
            publishAggregationUpdate(userId, aggregations, triggerEventType, triggerEventId);
        
        CompletableFuture<SendResult<String, Object>> walkerFuture = 
            publishWalkerNotification(userId, aggregations, triggerEventType);
        
        return CompletableFuture.allOf(aggregationFuture, walkerFuture)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Error publishing notifications for user {}", userId, throwable);
                } else {
                    log.debug("Successfully published both notifications for user {}", userId);
                }
            });
    }
    
    /**
     * Publishes batch aggregation updates for multiple users.
     * Useful for bulk operations or migration scenarios.
     */
    public CompletableFuture<Void> publishBatchAggregationUpdates(
            java.util.Map<String, UserAggregations> userAggregations, String triggerEventType) {
        
        CompletableFuture<?>[] futures = userAggregations.entrySet().stream()
            .map(entry -> publishAggregationUpdate(
                entry.getKey(), 
                entry.getValue(), 
                triggerEventType, 
                "batch-" + System.currentTimeMillis()
            ))
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Error in batch aggregation update publishing", throwable);
                } else {
                    log.info("Successfully published batch aggregation updates for {} users", 
                            userAggregations.size());
                }
            });
    }
    
    /**
     * Publishes batch Walker notifications for multiple users.
     */
    public CompletableFuture<Void> publishBatchWalkerNotifications(
            java.util.Map<String, UserAggregations> userAggregations, String eventType) {
        
        CompletableFuture<?>[] futures = userAggregations.entrySet().stream()
            .map(entry -> publishWalkerNotification(entry.getKey(), entry.getValue(), eventType))
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Error in batch Walker notification publishing", throwable);
                } else {
                    log.info("Successfully published batch Walker notifications for {} users", 
                            userAggregations.size());
                }
            });
    }
    
    /**
     * Gets the topic name for aggregation updates.
     */
    public String getAggregationUpdatesTopic() {
        return aggregationUpdatesTopic;
    }
    
    /**
     * Gets the topic name for Walker notifications.
     */
    public String getWalkerNotificationsTopic() {
        return walkerNotificationsTopic;
    }
    
    /**
     * Validates that required topics are configured.
     */
    public boolean validateConfiguration() {
        if (aggregationUpdatesTopic == null || aggregationUpdatesTopic.trim().isEmpty()) {
            log.error("Aggregation updates topic is not configured");
            return false;
        }
        
        if (walkerNotificationsTopic == null || walkerNotificationsTopic.trim().isEmpty()) {
            log.error("Walker notifications topic is not configured");
            return false;
        }
        
        return true;
    }
}