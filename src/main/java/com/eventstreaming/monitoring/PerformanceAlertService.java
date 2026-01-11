package com.eventstreaming.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for monitoring performance thresholds and generating alerts.
 * Tracks system health and triggers alerts when thresholds are violated.
 */
@Component
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true", matchIfMissing = false)
public class PerformanceAlertService {

    private final MeterRegistry meterRegistry;
    private final ActorSystem<?> actorSystem;
    
    // Configuration thresholds
    private final double memoryThresholdPercent;
    private final long processingLatencyThresholdMs;
    private final int maxActiveActors;
    private final long kafkaLagThreshold;
    
    // Alert tracking
    private final ConcurrentHashMap<String, Instant> activeAlerts = new ConcurrentHashMap<>();
    private final AtomicLong totalAlertsGenerated = new AtomicLong(0);
    
    // Alert cooldown period (5 minutes)
    private static final long ALERT_COOLDOWN_MS = 5 * 60 * 1000;

    @Autowired
    public PerformanceAlertService(
            MeterRegistry meterRegistry,
            ActorSystem<?> actorSystem,
            @Value("${eventstreaming.memory.threshold-percent:80.0}") double memoryThresholdPercent,
            @Value("${app.performance.processing-timeout:30000}") long processingLatencyThresholdMs,
            @Value("${app.actor.max-actors-per-node:10000}") int maxActiveActors,
            @Value("${app.kafka.consumer.lag-threshold:1000}") long kafkaLagThreshold) {
        
        this.meterRegistry = meterRegistry;
        this.actorSystem = actorSystem;
        this.memoryThresholdPercent = memoryThresholdPercent;
        this.processingLatencyThresholdMs = processingLatencyThresholdMs;
        this.maxActiveActors = maxActiveActors;
        this.kafkaLagThreshold = kafkaLagThreshold;
    }

    /**
     * Checks memory usage and generates alert if threshold exceeded.
     */
    public void checkMemoryUsage(double memoryUsagePercent) {
        if (memoryUsagePercent > memoryThresholdPercent) {
            generateAlert("HIGH_MEMORY_USAGE", 
                String.format("Memory usage %.2f%% exceeds threshold %.2f%%", 
                    memoryUsagePercent, memoryThresholdPercent));
        } else {
            clearAlert("HIGH_MEMORY_USAGE");
        }
    }

    /**
     * Checks processing latency and generates alert if threshold exceeded.
     */
    public void checkProcessingLatency(long latencyMs) {
        if (latencyMs > processingLatencyThresholdMs) {
            generateAlert("HIGH_PROCESSING_LATENCY",
                String.format("Processing latency %dms exceeds threshold %dms",
                    latencyMs, processingLatencyThresholdMs));
        } else {
            clearAlert("HIGH_PROCESSING_LATENCY");
        }
    }

    /**
     * Checks active actor count and generates alert if threshold exceeded.
     */
    public void checkActiveActorCount(int activeActors) {
        if (activeActors > maxActiveActors) {
            generateAlert("HIGH_ACTOR_COUNT",
                String.format("Active actors %d exceeds threshold %d",
                    activeActors, maxActiveActors));
        } else {
            clearAlert("HIGH_ACTOR_COUNT");
        }
    }

    /**
     * Checks Kafka consumer lag and generates alert if threshold exceeded.
     */
    public void checkKafkaLag(long lag) {
        if (lag > kafkaLagThreshold) {
            generateAlert("HIGH_KAFKA_LAG",
                String.format("Kafka consumer lag %d exceeds threshold %d",
                    lag, kafkaLagThreshold));
        } else {
            clearAlert("HIGH_KAFKA_LAG");
        }
    }

    /**
     * Checks actor backpressure and generates alert.
     */
    public void checkActorBackpressure(int actorsUnderPressure) {
        if (actorsUnderPressure > 10) {
            generateAlert("ACTOR_BACKPRESSURE",
                String.format("High number of actors under pressure: %d", actorsUnderPressure));
        } else {
            clearAlert("ACTOR_BACKPRESSURE");
        }
    }

    /**
     * Generates an alert if not in cooldown period.
     */
    private void generateAlert(String alertType, String message) {
        Instant now = Instant.now();
        Instant lastAlert = activeAlerts.get(alertType);
        
        // Check cooldown period
        if (lastAlert != null && 
            now.toEpochMilli() - lastAlert.toEpochMilli() < ALERT_COOLDOWN_MS) {
            return;
        }
        
        // Generate alert
        activeAlerts.put(alertType, now);
        totalAlertsGenerated.incrementAndGet();
        
        // Log alert
        actorSystem.log().error("PERFORMANCE ALERT [{}]: {}", alertType, message);
        
        // Record alert metric
        meterRegistry.counter("eventstreaming.alerts.generated",
            "alert_type", alertType)
            .increment();
        
        // Update active alerts gauge
        meterRegistry.gauge("eventstreaming.alerts.active.count", activeAlerts.size());
    }

    /**
     * Clears an active alert.
     */
    private void clearAlert(String alertType) {
        if (activeAlerts.remove(alertType) != null) {
            actorSystem.log().info("PERFORMANCE ALERT CLEARED: {}", alertType);
            
            meterRegistry.counter("eventstreaming.alerts.cleared",
                "alert_type", alertType)
                .increment();
            
            // Update active alerts gauge
            meterRegistry.gauge("eventstreaming.alerts.active.count", activeAlerts.size());
        }
    }

    /**
     * Scheduled health check every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void performHealthCheck() {
        // Get current metrics from meter registry
        double memoryUsage = getMetricValue("eventstreaming.memory.heap.usage.percent");
        double activeActors = getMetricValue("eventstreaming.actors.active.count");
        double kafkaLag = getMetricValue("eventstreaming.kafka.consumer.lag");
        double actorsUnderPressure = getMetricValue("eventstreaming.actors.under.pressure.count");
        
        // Check thresholds
        if (memoryUsage > 0) checkMemoryUsage(memoryUsage);
        if (activeActors > 0) checkActiveActorCount((int) activeActors);
        if (kafkaLag > 0) checkKafkaLag((long) kafkaLag);
        if (actorsUnderPressure > 0) checkActorBackpressure((int) actorsUnderPressure);
        
        // Record health check metric
        meterRegistry.counter("eventstreaming.health.checks.performed").increment();
    }

    /**
     * Gets metric value from meter registry.
     */
    private double getMetricValue(String metricName) {
        try {
            return meterRegistry.find(metricName).gauge() != null ? 
                meterRegistry.find(metricName).gauge().value() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Gets the number of active alerts.
     */
    public int getActiveAlertsCount() {
        return activeAlerts.size();
    }

    /**
     * Gets total alerts generated.
     */
    public long getTotalAlertsGenerated() {
        return totalAlertsGenerated.get();
    }
}