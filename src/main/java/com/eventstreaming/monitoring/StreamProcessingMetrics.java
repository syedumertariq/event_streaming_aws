package com.eventstreaming.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for Pekko Streams event processing pipeline.
 * Provides monitoring and observability for stream processing performance.
 */
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class StreamProcessingMetrics {
    
    private final ActorSystem<Void> actorSystem;
    private final MeterRegistry meterRegistry;
    
    // Counters
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter eventsRetriedCounter;
    private final Counter deserializationErrorsCounter;
    private final Counter sequenceValidationErrorsCounter;
    private final Counter actorTimeoutCounter;
    
    // Timers
    private final Timer eventProcessingTimer;
    private final Timer actorAskTimer;
    private final Timer deserializationTimer;
    private final Timer sequenceValidationTimer;
    
    // Gauges
    private final AtomicLong activeStreamsGauge;
    private final AtomicLong bufferSizeGauge;
    private final AtomicLong backpressureEventsGauge;
    
    @Autowired
    public StreamProcessingMetrics(ActorSystem<Void> actorSystem, MeterRegistry meterRegistry) {
        this.actorSystem = actorSystem;
        this.meterRegistry = meterRegistry;
        
        if (meterRegistry != null) {
            // Initialize counters
            this.eventsProcessedCounter = Counter.builder("eventstreaming.events.processed")
                .description("Total number of events processed successfully")
                .register(meterRegistry);
                
            this.eventsFailedCounter = Counter.builder("eventstreaming.events.failed")
                .description("Total number of events that failed processing")
                .register(meterRegistry);
                
            this.eventsRetriedCounter = Counter.builder("eventstreaming.events.retried")
                .description("Total number of events that were retried")
                .register(meterRegistry);
                
            this.deserializationErrorsCounter = Counter.builder("eventstreaming.deserialization.errors")
                .description("Total number of event deserialization errors")
                .register(meterRegistry);
                
            this.sequenceValidationErrorsCounter = Counter.builder("eventstreaming.sequence.validation.errors")
                .description("Total number of sequence validation errors")
                .register(meterRegistry);
                
            this.actorTimeoutCounter = Counter.builder("eventstreaming.actor.timeouts")
                .description("Total number of actor timeout errors")
                .register(meterRegistry);
            
            // Initialize timers
            this.eventProcessingTimer = Timer.builder("eventstreaming.processing.duration")
                .description("Time taken to process events end-to-end")
                .register(meterRegistry);
                
            this.actorAskTimer = Timer.builder("eventstreaming.actor.ask.duration")
                .description("Time taken for actor ask operations")
                .register(meterRegistry);
                
            this.deserializationTimer = Timer.builder("eventstreaming.deserialization.duration")
                .description("Time taken to deserialize events")
                .register(meterRegistry);
                
            this.sequenceValidationTimer = Timer.builder("eventstreaming.sequence.validation.duration")
                .description("Time taken to validate event sequences")
                .register(meterRegistry);
            
            // Initialize gauges
            this.activeStreamsGauge = meterRegistry.gauge("eventstreaming.streams.active", new AtomicLong(0));
            this.bufferSizeGauge = meterRegistry.gauge("eventstreaming.buffer.size", new AtomicLong(0));
            this.backpressureEventsGauge = meterRegistry.gauge("eventstreaming.backpressure.events", new AtomicLong(0));
        } else {
            // NoOp implementations for when meterRegistry is null
            this.eventsProcessedCounter = null;
            this.eventsFailedCounter = null;
            this.eventsRetriedCounter = null;
            this.deserializationErrorsCounter = null;
            this.sequenceValidationErrorsCounter = null;
            this.actorTimeoutCounter = null;
            this.eventProcessingTimer = null;
            this.actorAskTimer = null;
            this.deserializationTimer = null;
            this.sequenceValidationTimer = null;
            this.activeStreamsGauge = new AtomicLong(0);
            this.bufferSizeGauge = new AtomicLong(0);
            this.backpressureEventsGauge = new AtomicLong(0);
        }
    }
    
    // Counter methods
    
    public void incrementEventsProcessed() {
        if (eventsProcessedCounter != null) {
            eventsProcessedCounter.increment();
        }
    }
    
    public void incrementEventsProcessed(String eventType, String userId) {
        if (meterRegistry != null) {
            Counter.builder("eventstreaming.events.processed")
                .tag("event.type", eventType)
                .tag("user.id", userId)
                .register(meterRegistry)
                .increment();
        }
    }
    
    public void incrementEventsFailed() {
        if (eventsFailedCounter != null) {
            eventsFailedCounter.increment();
        }
    }
    
    public void incrementEventsFailed(String eventType, String reason) {
        if (meterRegistry != null) {
            Counter.builder("eventstreaming.events.failed")
                .tag("event.type", eventType)
                .tag("failure.reason", reason)
                .register(meterRegistry)
                .increment();
        }
    }
    
    public void incrementEventsRetried() {
        if (eventsRetriedCounter != null) {
            eventsRetriedCounter.increment();
        }
    }
    
    public void incrementDeserializationErrors() {
        if (deserializationErrorsCounter != null) {
            deserializationErrorsCounter.increment();
        }
    }
    
    public void incrementSequenceValidationErrors() {
        if (sequenceValidationErrorsCounter != null) {
            sequenceValidationErrorsCounter.increment();
        }
    }
    
    public void incrementActorTimeouts() {
        if (actorTimeoutCounter != null) {
            actorTimeoutCounter.increment();
        }
    }
    
    // Timer methods
    
    public Timer.Sample startEventProcessingTimer() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }
    
    public void recordEventProcessingTime(Timer.Sample sample) {
        if (sample != null && eventProcessingTimer != null) {
            sample.stop(eventProcessingTimer);
        }
    }
    
    public void recordEventProcessingTime(Duration duration) {
        if (eventProcessingTimer != null) {
            eventProcessingTimer.record(duration);
        }
    }
    
    public Timer.Sample startActorAskTimer() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }
    
    public void recordActorAskTime(Timer.Sample sample) {
        if (sample != null && actorAskTimer != null) {
            sample.stop(actorAskTimer);
        }
    }
    
    public Timer.Sample startDeserializationTimer() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }
    
    public void recordDeserializationTime(Timer.Sample sample) {
        if (sample != null && deserializationTimer != null) {
            sample.stop(deserializationTimer);
        }
    }
    
    public Timer.Sample startSequenceValidationTimer() {
        return meterRegistry != null ? Timer.start(meterRegistry) : null;
    }
    
    public void recordSequenceValidationTime(Timer.Sample sample) {
        if (sample != null && sequenceValidationTimer != null) {
            sample.stop(sequenceValidationTimer);
        }
    }
    
    // Gauge methods
    
    public void setActiveStreams(long count) {
        activeStreamsGauge.set(count);
    }
    
    public void incrementActiveStreams() {
        activeStreamsGauge.incrementAndGet();
    }
    
    public void decrementActiveStreams() {
        activeStreamsGauge.decrementAndGet();
    }
    
    public void setBufferSize(long size) {
        bufferSizeGauge.set(size);
    }
    
    public void setBackpressureEvents(long count) {
        backpressureEventsGauge.set(count);
    }
    
    public void incrementBackpressureEvents() {
        backpressureEventsGauge.incrementAndGet();
    }
    
    // Utility methods for common metric patterns
    
    public void recordSuccessfulProcessing(String eventType, String userId, Duration processingTime) {
        incrementEventsProcessed(eventType, userId);
        recordEventProcessingTime(processingTime);
    }
    
    public void recordFailedProcessing(String eventType, String reason, Duration processingTime) {
        incrementEventsFailed(eventType, reason);
        recordEventProcessingTime(processingTime);
    }
    
    public void recordRetryProcessing(String eventType, int attemptCount) {
        incrementEventsRetried();
        if (meterRegistry != null) {
            Counter.builder("eventstreaming.events.retried")
                .tag("event.type", eventType)
                .tag("attempt.count", String.valueOf(attemptCount))
                .register(meterRegistry)
                .increment();
        }
    }
    
    /**
     * Gets current metric values for health checks and monitoring.
     */
    public MetricsSummary getMetricsSummary() {
        return new MetricsSummary(
            eventsProcessedCounter != null ? (long) eventsProcessedCounter.count() : 0L,
            eventsFailedCounter != null ? (long) eventsFailedCounter.count() : 0L,
            eventsRetriedCounter != null ? (long) eventsRetriedCounter.count() : 0L,
            deserializationErrorsCounter != null ? (long) deserializationErrorsCounter.count() : 0L,
            sequenceValidationErrorsCounter != null ? (long) sequenceValidationErrorsCounter.count() : 0L,
            actorTimeoutCounter != null ? (long) actorTimeoutCounter.count() : 0L,
            activeStreamsGauge.get(),
            bufferSizeGauge.get(),
            backpressureEventsGauge.get(),
            eventProcessingTimer != null ? eventProcessingTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0,
            actorAskTimer != null ? actorAskTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0
        );
    }
    
    /**
     * Summary of key metrics for monitoring and health checks.
     */
    public record MetricsSummary(
        long eventsProcessed,
        long eventsFailed,
        long eventsRetried,
        long deserializationErrors,
        long sequenceValidationErrors,
        long actorTimeouts,
        long activeStreams,
        long bufferSize,
        long backpressureEvents,
        double avgProcessingTimeMs,
        double avgActorAskTimeMs
    ) {
        public double getSuccessRate() {
            long total = eventsProcessed + eventsFailed;
            return total > 0 ? (double) eventsProcessed / total * 100.0 : 0.0;
        }
        
        public double getErrorRate() {
            long total = eventsProcessed + eventsFailed;
            return total > 0 ? (double) eventsFailed / total * 100.0 : 0.0;
        }
        
        public boolean isHealthy() {
            return getSuccessRate() > 95.0 && avgProcessingTimeMs < 1000.0 && actorTimeouts < 10;
        }
    }
}