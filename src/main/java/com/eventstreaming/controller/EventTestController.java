package com.eventstreaming.controller;

import com.eventstreaming.actor.UserActor;
import com.eventstreaming.model.*;
import com.eventstreaming.service.EventProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Test controller for demonstrating event processing and aggregation functionality.
 * This would not exist in production - events would come from Kafka.
 * Only enabled for test and development profiles, excluded from isolated profile.
 */
@RestController
@RequestMapping("/api/v1/test")
@org.springframework.context.annotation.Profile({"test", "development"})
public class EventTestController {
    
    private static final Logger log = LoggerFactory.getLogger(EventTestController.class);
    
    private final EventProducerService eventProducerService;
    
    @Autowired
    public EventTestController(EventProducerService eventProducerService) {
        this.eventProducerService = eventProducerService;
    }
    
    /**
     * Send a test email open event for a user
     */
    @PostMapping("/users/{userId}/events/email-open")
    public CompletableFuture<ResponseEntity<String>> sendEmailOpenEvent(@PathVariable("userId") String userId) {
        
        EmailEvent event = EmailEvent.builder()
            .userId(userId)
            .eventId(UUID.randomUUID().toString())
            .eventType(EventType.EMAIL_OPEN)
            .timestamp(Instant.now())
            .source("test-controller")
            .emailAddress("user@example.com")
            .campaignId("campaign-123")
            .subject("Test Email Subject")
            .metadata(Map.of("source", "test-controller"))
            .build();
        
        return eventProducerService.publishEvent(event)
            .thenApply(response -> {
                if (response instanceof UserActor.ProcessEventResponse.Success success) {
                    log.info("Successfully processed email open event for user: {}", userId);
                    return ResponseEntity.ok("Email open event processed. New email opens: " + 
                                           success.aggregations().getEmailOpens());
                } else if (response instanceof UserActor.ProcessEventResponse.Failed failed) {
                    // Check if this is an actor termination issue
                    if (failed.reason().contains("had already been terminated")) {
                        log.warn("Actor for user {} was terminated during email open event, returning service unavailable", userId);
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("Retry-After", "1")
                            .body("Actor temporarily unavailable, please retry");
                    }
                    log.error("Failed to process email open event for user {}: {}", userId, failed.reason());
                    return ResponseEntity.badRequest().body("Failed: " + failed.reason());
                } else {
                    return ResponseEntity.internalServerError().body("Unexpected response type");
                }
            });
    }
    
    /**
     * Send a test SMS reply event for a user
     */
    @PostMapping("/users/{userId}/events/sms-reply")
    public CompletableFuture<ResponseEntity<String>> sendSmsReplyEvent(@PathVariable("userId") String userId) {
        
        SmsEvent event = new SmsEvent(
            userId,
            UUID.randomUUID().toString(),
            EventType.SMS_REPLY,
            Instant.now(),
            "+1234567890",
            "Original message",
            "carrier-123",
            "User reply message",
            Map.of("source", "test-controller")
        );
        
        return eventProducerService.publishEvent(event)
            .thenApply(response -> {
                if (response instanceof UserActor.ProcessEventResponse.Success success) {
                    log.info("Successfully processed SMS reply event for user: {}", userId);
                    return ResponseEntity.ok("SMS reply event processed. New SMS replies: " + 
                                           success.aggregations().getSmsReplies());
                } else if (response instanceof UserActor.ProcessEventResponse.Failed failed) {
                    // Check if this is an actor termination issue
                    if (failed.reason().contains("had already been terminated")) {
                        log.warn("Actor for user {} was terminated during SMS reply event, returning service unavailable", userId);
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("Retry-After", "1")
                            .body("Actor temporarily unavailable, please retry");
                    }
                    log.error("Failed to process SMS reply event for user {}: {}", userId, failed.reason());
                    return ResponseEntity.badRequest().body("Failed: " + failed.reason());
                } else {
                    return ResponseEntity.internalServerError().body("Unexpected response type");
                }
            });
    }
    
    /**
     * Send a test call completed event for a user
     */
    @PostMapping("/users/{userId}/events/call-completed")
    public CompletableFuture<ResponseEntity<String>> sendCallCompletedEvent(@PathVariable("userId") String userId) {
        
        CallEvent event = new CallEvent(
            userId,
            UUID.randomUUID().toString(),
            EventType.CALL_COMPLETED,
            Instant.now(),
            "+1234567890",
            java.time.Duration.ofMinutes(5),
            "OUTBOUND",
            "COMPLETED",
            Map.of("source", "test-controller")
        );
        
        return eventProducerService.publishEvent(event)
            .thenApply(response -> {
                if (response instanceof UserActor.ProcessEventResponse.Success success) {
                    log.info("Successfully processed call completed event for user: {}", userId);
                    return ResponseEntity.ok("Call completed event processed. New calls completed: " + 
                                           success.aggregations().getCallsCompleted());
                } else if (response instanceof UserActor.ProcessEventResponse.Failed failed) {
                    // Check if this is an actor termination issue
                    if (failed.reason().contains("had already been terminated")) {
                        log.warn("Actor for user {} was terminated during call completed event, returning service unavailable", userId);
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .header("Retry-After", "1")
                            .body("Actor temporarily unavailable, please retry");
                    }
                    log.error("Failed to process call completed event for user {}: {}", userId, failed.reason());
                    return ResponseEntity.badRequest().body("Failed: " + failed.reason());
                } else {
                    return ResponseEntity.internalServerError().body("Unexpected response type");
                }
            });
    }
    
    /**
     * Send multiple test events for a user to demonstrate aggregation
     */
    @PostMapping("/users/{userId}/events/bulk")
    public CompletableFuture<ResponseEntity<String>> sendBulkEvents(@PathVariable("userId") String userId) {
        
        // Create multiple events
        EmailEvent emailOpen = new EmailEvent(userId, UUID.randomUUID().toString(), 
                                            EventType.EMAIL_OPEN, Instant.now());
        EmailEvent emailClick = new EmailEvent(userId, UUID.randomUUID().toString(), 
                                             EventType.EMAIL_CLICK, Instant.now());
        SmsEvent smsReply = new SmsEvent(userId, UUID.randomUUID().toString(), 
                                       EventType.SMS_REPLY, Instant.now());
        CallEvent callCompleted = new CallEvent(userId, UUID.randomUUID().toString(), 
                                              EventType.CALL_COMPLETED, Instant.now());
        
        return eventProducerService.publishEvents(emailOpen, emailClick, smsReply, callCompleted)
            .thenApply(result -> {
                log.info("Successfully processed bulk events for user: {}", userId);
                return ResponseEntity.ok("Bulk events processed successfully for user: " + userId);
            })
            .exceptionally(throwable -> {
                // Check for actor termination errors
                if (isActorTerminationError(throwable)) {
                    log.warn("Actor for user {} was terminated during bulk events, returning service unavailable", userId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "2")
                        .body("Actor temporarily unavailable, please retry");
                }
                
                // Check for timeout errors
                if (throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
                    log.warn("Request timeout for user {} during bulk events, returning service unavailable", userId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "1")
                        .body("Request timeout, please retry");
                }
                
                log.error("Failed to process bulk events for user: " + userId, throwable);
                return ResponseEntity.internalServerError().body("Failed to process bulk events");
            });
    }
    
    /**
     * Checks if the throwable indicates an actor termination error.
     */
    private boolean isActorTerminationError(Throwable throwable) {
        if (throwable == null) return false;
        
        String message = throwable.getMessage();
        if (message != null && message.contains("had already been terminated")) {
            return true;
        }
        
        Throwable cause = throwable.getCause();
        if (cause instanceof java.util.concurrent.TimeoutException) {
            String causeMessage = cause.getMessage();
            return causeMessage != null && causeMessage.contains("had already been terminated");
        }
        
        return false;
    }
}