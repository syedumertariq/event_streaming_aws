package com.eventstreaming;

import com.eventstreaming.actor.UserActor;
import com.eventstreaming.actor.UserActorRegistry;
import com.eventstreaming.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Event Processing Controller for real-time event streaming
 * Demonstrates Pekko Typed actor event processing
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    
    private final UserActorRegistry userActorRegistry;

    @Autowired
    public EventController(UserActorRegistry userActorRegistry) {
        this.userActorRegistry = userActorRegistry;
    }

    /**
     * Process email open event
     */
    @PostMapping("/users/{userId}/email-open")
    public CompletableFuture<ResponseEntity<String>> processEmailOpen(@PathVariable String userId) {
        EmailEvent event = EmailEvent.builder()
            .userId(userId)
            .eventId(UUID.randomUUID().toString())
            .eventType(EventType.EMAIL_OPEN)
            .timestamp(Instant.now())
            .source("api")
            .emailAddress("user@example.com")
            .campaignId("campaign123")
            .subject("Test Subject")
            .metadata(Map.of())
            .build();

        return processEvent(userId, event, "email open");
    }

    /**
     * Process SMS reply event
     */
    @PostMapping("/users/{userId}/sms-reply")
    public CompletableFuture<ResponseEntity<String>> processSmsReply(@PathVariable String userId) {
        SmsEvent event = new SmsEvent(
            userId,
            UUID.randomUUID().toString(),
            EventType.SMS_REPLY,
            Instant.now(),
            "+1234567890",
            "Thanks for the message!",
            "campaign123",
            null,
            Map.of()
        );

        return processEvent(userId, event, "SMS reply");
    }

    /**
     * Process call completed event
     */
    @PostMapping("/users/{userId}/call-completed")
    public CompletableFuture<ResponseEntity<String>> processCallCompleted(@PathVariable String userId) {
        CallEvent event = new CallEvent(
            userId,
            UUID.randomUUID().toString(),
            EventType.CALL_COMPLETED,
            Instant.now(),
            "+1234567890",
            Duration.ofMinutes(5),
            "campaign123",
            null,
            Map.of()
        );

        return processEvent(userId, event, "call completed");
    }

    /**
     * Generic event processing method
     */
    private CompletableFuture<ResponseEntity<String>> processEvent(String userId, CommunicationEvent event, String eventType) {
        log.info("Processing {} event for user: {}", eventType, userId);

        try {
            return userActorRegistry.askUserActor(
                    userId,
                    replyTo -> new UserActor.ProcessEvent(event, replyTo),
                    Duration.ofSeconds(5),
                    UserActor.ProcessEventResponse.class
            ).thenApply(result -> {
                if (result instanceof UserActor.ProcessEventResponse.Success success) {
                    String message = String.format("%s event processed successfully. New aggregations: %s", 
                        eventType, success.aggregations());
                    log.info("Successfully processed {} event for user: {}", eventType, userId);
                    return ResponseEntity.ok(message);
                } else if (result instanceof UserActor.ProcessEventResponse.Failed failed) {
                    log.error("Failed to process {} event for user {}: {}", eventType, userId, failed.reason());
                    return ResponseEntity.internalServerError()
                        .body("Failed to process " + eventType + " event: " + failed.reason());
                } else {
                    log.error("Unexpected response type for {} event processing: {}", eventType, result.getClass());
                    return ResponseEntity.internalServerError()
                        .body("Unexpected error processing " + eventType + " event");
                }
            }).exceptionally(throwable -> {
                // Check for actor termination errors
                if (isActorTerminationError(throwable)) {
                    log.warn("Actor for user {} was terminated during {} event processing, returning service unavailable", userId, eventType);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "2")
                        .body("Actor temporarily unavailable, please retry");
                }
                
                // Check for timeout errors
                if (throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
                    log.warn("Request timeout for user {} during {} event processing, returning service unavailable", userId, eventType);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "1")
                        .body("Request timeout, please retry");
                }
                
                log.error("Exception processing {} event for user: {}", eventType, userId, throwable);
                return ResponseEntity.internalServerError()
                    .body("Error processing " + eventType + " event: " + throwable.getMessage());
            }).toCompletableFuture();

        } catch (Exception e) {
            log.error("Error processing {} event for user: {}", eventType, userId, e);
            return CompletableFuture.completedFuture(
                ResponseEntity.internalServerError()
                    .body("Error processing " + eventType + " event: " + e.getMessage()));
        }
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