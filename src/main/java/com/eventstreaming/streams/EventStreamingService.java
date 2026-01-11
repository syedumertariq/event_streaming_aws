package com.eventstreaming.streams;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for managing the lifecycle of Pekko Streams event processing pipeline.
 * Handles startup, shutdown, and health monitoring of the streaming infrastructure.
 */
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@Service
public class EventStreamingService {
    
    private static final Logger log = LoggerFactory.getLogger(EventStreamingService.class);
    
    private final ActorSystem<Void> actorSystem;
    private final EventProcessingPipeline processingPipeline;
    
    private volatile Consumer.Control streamControl;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    @Value("${app.streams.event-processing.auto-start:true}")
    private boolean autoStart;
    
    @Value("${app.streams.event-processing.kafka-enabled:true}")
    private boolean kafkaEnabled;
    
    @Autowired
    public EventStreamingService(ActorSystem<Void> actorSystem,
                                EventProcessingPipeline processingPipeline) {
        this.actorSystem = actorSystem;
        this.processingPipeline = processingPipeline;
    }
    
    /**
     * Automatically starts the event streaming pipeline when the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (autoStart && kafkaEnabled) {
            log.info("Application ready - starting event streaming pipeline");
            startEventProcessing();
        } else {
            log.info("Event streaming pipeline auto-start disabled or Kafka disabled");
        }
    }
    
    /**
     * Starts the event processing pipeline.
     */
    public synchronized void startEventProcessing() {
        if (isRunning.get()) {
            log.warn("Event streaming pipeline is already running");
            return;
        }
        
        if (!kafkaEnabled) {
            log.info("Kafka is disabled - event streaming pipeline will not start");
            return;
        }
        
        try {
            log.info("Starting event streaming pipeline...");
            
            var pipelineSource = processingPipeline.createEventProcessingPipeline();
            var processingSink = processingPipeline.createProcessingResultSink();
            
            // Run the pipeline and capture control
            var runnableGraph = pipelineSource.toMat(processingSink, (control, completion) -> {
                this.streamControl = control;
                return completion;
            });
            
            CompletionStage<Done> completion = runnableGraph.run(actorSystem);
            
            isRunning.set(true);
            log.info("Event streaming pipeline started successfully");
            
            // Handle completion and errors
            completion.whenComplete((done, throwable) -> {
                if (throwable != null) {
                    log.error("Event processing pipeline completed with error", throwable);
                    if (!isShuttingDown.get()) {
                        handlePipelineFailure(throwable);
                    }
                } else {
                    log.info("Event processing pipeline completed successfully");
                }
                isRunning.set(false);
            });
            
        } catch (Exception e) {
            log.error("Failed to start event streaming pipeline", e);
            isRunning.set(false);
            throw new RuntimeException("Event streaming startup failed", e);
        }
    }
    
    /**
     * Stops the event processing pipeline gracefully.
     */
    public synchronized CompletionStage<Done> stopEventProcessing() {
        if (!isRunning.get()) {
            log.warn("Event streaming pipeline is not running");
            return CompletableFuture.completedFuture(Done.getInstance());
        }
        
        if (isShuttingDown.getAndSet(true)) {
            log.warn("Event streaming pipeline is already shutting down");
            return CompletableFuture.completedFuture(Done.getInstance());
        }
        
        log.info("Stopping event streaming pipeline...");
        
        if (streamControl != null) {
            return streamControl.shutdown()
                .whenComplete((done, throwable) -> {
                    if (throwable != null) {
                        log.error("Error during event streaming shutdown", throwable);
                    } else {
                        log.info("Event streaming pipeline stopped successfully");
                    }
                    isRunning.set(false);
                    isShuttingDown.set(false);
                });
        } else {
            isRunning.set(false);
            isShuttingDown.set(false);
            return CompletableFuture.completedFuture(Done.getInstance());
        }
    }
    
    /**
     * Restarts the event processing pipeline.
     */
    public CompletionStage<Done> restartEventProcessing() {
        log.info("Restarting event streaming pipeline...");
        
        return stopEventProcessing()
            .thenRun(() -> {
                try {
                    Thread.sleep(2000); // Brief pause before restart
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                startEventProcessing();
            })
            .thenApply(v -> Done.getInstance());
    }
    
    /**
     * Monitors pipeline health and handles failures.
     */
    private void monitorPipelineHealth() {
        if (streamControl != null) {
            streamControl.isShutdown()
                .whenComplete((done, throwable) -> {
                    if (throwable != null) {
                        log.error("Event streaming pipeline failed", throwable);
                        handlePipelineFailure(throwable);
                    } else if (!isShuttingDown.get()) {
                        log.warn("Event streaming pipeline shut down unexpectedly");
                        handleUnexpectedShutdown();
                    }
                });
        }
    }
    
    /**
     * Handles pipeline failures with restart logic.
     */
    private void handlePipelineFailure(Throwable throwable) {
        log.error("Handling pipeline failure", throwable);
        
        isRunning.set(false);
        
        // TODO: Implement exponential backoff restart strategy
        // TODO: Implement alerting for critical failures
        // TODO: Implement circuit breaker pattern
        
        // Simple restart for now
        actorSystem.scheduler().scheduleOnce(
            java.time.Duration.ofSeconds(5),
            () -> {
                if (!isShuttingDown.get()) {
                    log.info("Attempting to restart failed pipeline");
                    startEventProcessing();
                }
            },
            actorSystem.executionContext()
        );
    }
    
    /**
     * Handles unexpected pipeline shutdown.
     */
    private void handleUnexpectedShutdown() {
        log.warn("Handling unexpected pipeline shutdown");
        
        isRunning.set(false);
        
        // Attempt restart if not intentionally shutting down
        if (!isShuttingDown.get()) {
            actorSystem.scheduler().scheduleOnce(
                java.time.Duration.ofSeconds(3),
                () -> {
                    log.info("Attempting to restart unexpectedly stopped pipeline");
                    startEventProcessing();
                },
                actorSystem.executionContext()
            );
        }
    }
    
    /**
     * Graceful shutdown hook.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down event streaming service...");
        
        if (isRunning.get()) {
            try {
                stopEventProcessing().toCompletableFuture().get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Error during graceful shutdown", e);
            }
        }
        
        log.info("Event streaming service shutdown complete");
    }
    
    /**
     * Gets the current status of the event streaming pipeline.
     */
    public StreamingStatus getStatus() {
        if (isShuttingDown.get()) {
            return StreamingStatus.SHUTTING_DOWN;
        } else if (isRunning.get()) {
            return StreamingStatus.RUNNING;
        } else {
            return StreamingStatus.STOPPED;
        }
    }
    
    /**
     * Checks if the pipeline is healthy and processing events.
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Checks if the pipeline is shutting down.
     */
    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }
    
    /**
     * Gets health information about the streaming pipeline.
     */
    public StreamHealthInfo getHealthInfo() {
        return new StreamHealthInfo(
            isRunning.get(),
            isShuttingDown.get(),
            streamControl != null,
            streamControl != null && !streamControl.isShutdown().toCompletableFuture().isDone()
        );
    }
    
    /**
     * Gets pipeline metrics and status information.
     */
    public PipelineInfo getPipelineInfo() {
        StreamHealthInfo healthInfo = getHealthInfo();
        return new PipelineInfo(
            getStatus(),
            healthInfo.isHealthy(),
            kafkaEnabled,
            autoStart,
            streamControl != null ? streamControl.toString() : "No control available"
        );
    }
    
    /**
     * Status enumeration for the streaming pipeline.
     */
    public enum StreamingStatus {
        RUNNING,
        STOPPED,
        SHUTTING_DOWN,
        FAILED
    }
    
    /**
     * Pipeline information record.
     */
    public record PipelineInfo(
        StreamingStatus status,
        boolean healthy,
        boolean kafkaEnabled,
        boolean autoStart,
        String controlInfo
    ) {}
    
    /**
     * Health information for the streaming pipeline.
     */
    public record StreamHealthInfo(
        boolean isRunning,
        boolean isShuttingDown,
        boolean hasStreamControl,
        boolean streamIsActive
    ) {
        public boolean isHealthy() {
            return isRunning && hasStreamControl && streamIsActive && !isShuttingDown;
        }
    }
}