package com.eventstreaming.validation;

import com.eventstreaming.model.CommunicationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Validates event sequencing across multiple sources to ensure chronological processing per user.
 * Uses Redis to store last processed timestamp for each user across all communication channels.
 * Only loads when Redis profiles are active.
 */
@Component
@Profile({"redis", "local-redis", "cluster-mysql", "isolated"})
public class EventSequenceValidator {
    
    private static final Logger log = LoggerFactory.getLogger(EventSequenceValidator.class);
    private static final String LAST_EVENT_TIME_PREFIX = "last_event_time:";
    private static final String SEQUENCE_VIOLATION_PREFIX = "sequence_violation:";
    
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${app.kafka.sequence.validation-enabled:true}")
    private boolean validationEnabled;
    
    @Value("${app.kafka.sequence.max-out-of-order-ms:1000}")
    private long maxOutOfOrderMs;
    
    @Value("${app.kafka.sequence.store-last-timestamp:redis}")
    private String timestampStore;
    
    @Autowired
    public EventSequenceValidator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Validates if the event is in correct chronological sequence for the user.
     * Allows minor timing differences due to network delays and clock skew.
     */
    public SequenceValidationResult validateSequence(CommunicationEvent event) {
        if (!validationEnabled) {
            return SequenceValidationResult.valid();
        }
        
        if (event == null || event.getUserId() == null || event.getTimestamp() == null) {
            log.warn("Invalid event for sequence validation: {}", event);
            return SequenceValidationResult.invalid("Invalid event data");
        }
        
        String userId = event.getUserId();
        Instant eventTime = event.getTimestamp();
        
        try {
            // Get last processed timestamp for this user
            String lastTimestampKey = LAST_EVENT_TIME_PREFIX + userId;
            String lastTimestampStr = redisTemplate.opsForValue().get(lastTimestampKey);
            
            if (lastTimestampStr != null) {
                Instant lastEventTime = Instant.parse(lastTimestampStr);
                
                if (eventTime.isBefore(lastEventTime)) {
                    Duration timeDiff = Duration.between(eventTime, lastEventTime);
                    long diffMs = timeDiff.toMillis();
                    
                    log.warn("Out-of-sequence event detected for user {}: current={}, last={}, diff={}ms, source={}", 
                            userId, eventTime, lastEventTime, diffMs, event.getSource());
                    
                    // Record sequence violation for monitoring
                    recordSequenceViolation(event, lastEventTime, diffMs);
                    
                    if (diffMs > maxOutOfOrderMs) {
                        return SequenceValidationResult.invalid(
                            String.format("Event too far out of sequence: %dms > %dms", diffMs, maxOutOfOrderMs));
                    } else {
                        // Allow minor timing differences but log warning
                        return SequenceValidationResult.validWithWarning(
                            String.format("Minor sequence violation: %dms", diffMs));
                    }
                }
            }
            
            // Update last processed time with TTL (24 hours)
            redisTemplate.opsForValue().set(lastTimestampKey, eventTime.toString(), 24, TimeUnit.HOURS);
            
            return SequenceValidationResult.valid();
            
        } catch (Exception e) {
            log.error("Error validating sequence for user {}: {}", userId, event, e);
            // Don't fail processing due to validation errors
            return SequenceValidationResult.validWithWarning("Sequence validation error: " + e.getMessage());
        }
    }
    
    /**
     * Records sequence violation for monitoring and analysis.
     */
    private void recordSequenceViolation(CommunicationEvent event, Instant lastEventTime, long diffMs) {
        try {
            String violationKey = SEQUENCE_VIOLATION_PREFIX + event.getUserId() + ":" + System.currentTimeMillis();
            String violationData = String.format(
                "eventId=%s,eventType=%s,eventTime=%s,lastTime=%s,diffMs=%d,source=%s",
                event.getEventId(), event.getEventType(), event.getTimestamp(), 
                lastEventTime, diffMs, event.getSource()
            );
            
            // Store violation data with 7 days TTL for analysis
            redisTemplate.opsForValue().set(violationKey, violationData, 7, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("Error recording sequence violation: {}", e.getMessage());
        }
    }
    
    /**
     * Gets the last processed timestamp for a user.
     */
    public Instant getLastProcessedTime(String userId) {
        try {
            String lastTimestampStr = redisTemplate.opsForValue().get(LAST_EVENT_TIME_PREFIX + userId);
            return lastTimestampStr != null ? Instant.parse(lastTimestampStr) : null;
        } catch (Exception e) {
            log.error("Error getting last processed time for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Manually updates the last processed timestamp for a user.
     * Useful for initialization or recovery scenarios.
     */
    public void updateLastProcessedTime(String userId, Instant timestamp) {
        try {
            String lastTimestampKey = LAST_EVENT_TIME_PREFIX + userId;
            redisTemplate.opsForValue().set(lastTimestampKey, timestamp.toString(), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Error updating last processed time for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Clears sequence validation data for a user.
     * Useful for testing or data cleanup.
     */
    public void clearUserSequenceData(String userId) {
        try {
            redisTemplate.delete(LAST_EVENT_TIME_PREFIX + userId);
            log.debug("Cleared sequence data for user: {}", userId);
        } catch (Exception e) {
            log.error("Error clearing sequence data for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Result of sequence validation.
     */
    public static class SequenceValidationResult {
        private final boolean valid;
        private final boolean hasWarning;
        private final String message;
        
        private SequenceValidationResult(boolean valid, boolean hasWarning, String message) {
            this.valid = valid;
            this.hasWarning = hasWarning;
            this.message = message;
        }
        
        public static SequenceValidationResult valid() {
            return new SequenceValidationResult(true, false, null);
        }
        
        public static SequenceValidationResult validWithWarning(String message) {
            return new SequenceValidationResult(true, true, message);
        }
        
        public static SequenceValidationResult invalid(String message) {
            return new SequenceValidationResult(false, false, message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public boolean hasWarning() {
            return hasWarning;
        }
        
        public String getMessage() {
            return message;
        }
        
        @Override
        public String toString() {
            return String.format("SequenceValidationResult{valid=%s, hasWarning=%s, message='%s'}", 
                               valid, hasWarning, message);
        }
    }
}