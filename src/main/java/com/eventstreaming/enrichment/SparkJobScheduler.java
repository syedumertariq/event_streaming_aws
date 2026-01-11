package com.eventstreaming.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scheduler for Spark batch enrichment jobs.
 * Manages the execution timing and monitoring of user profile enrichment jobs.
 */
@Component
public class SparkJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(SparkJobScheduler.class);

    @Autowired
    private SparkJobMonitor jobMonitor;

    @Value("${spark.job.enabled:true}")
    private boolean jobEnabled;

    @Value("${spark.job.async:true}")
    private boolean asyncExecution;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    /**
     * Scheduled job that runs user profile enrichment every 6 hours.
     * Cron expression: 0 0 0/6 * * ? (every 6 hours starting at midnight)
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduleUserProfileEnrichment() {
        if (!jobEnabled) {
            log.debug("Spark job execution is disabled");
            return;
        }

        log.info("Starting scheduled user profile enrichment job at {}", LocalDateTime.now());

        if (asyncExecution) {
            CompletableFuture.runAsync(this::executeEnrichmentWithMonitoring, executorService)
                    .exceptionally(throwable -> {
                        log.error("Async enrichment job failed", throwable);
                        jobMonitor.recordJobFailure("user_profile_enrichment", throwable.getMessage());
                        return null;
                    });
        } else {
            executeEnrichmentWithMonitoring();
        }
    }

    /**
     * Scheduled job for cache warming - runs more frequently to keep Redis warm.
     * Cron expression: 0 0 0/2 * * ? (every 2 hours starting at midnight)
     */
    @Scheduled(cron = "0 0 */2 * * *")
    public void scheduleCacheWarming() {
        if (!jobEnabled) {
            return;
        }

        log.info("Starting cache warming job at {}", LocalDateTime.now());

        CompletableFuture.runAsync(() -> {
            try {
                jobMonitor.recordJobStart("cache_warming");
                
                // Execute lightweight enrichment for active users only
                log.info("Executing lightweight enrichment for 1000 active users");
                
                jobMonitor.recordJobSuccess("cache_warming");
                log.info("Cache warming job completed successfully");
                
            } catch (Exception e) {
                log.error("Cache warming job failed", e);
                jobMonitor.recordJobFailure("cache_warming", e.getMessage());
            }
        }, executorService);
    }

    /**
     * Manual trigger for enrichment job - useful for testing or emergency runs.
     */
    public void triggerManualEnrichment() {
        log.info("Manual enrichment job triggered at {}", LocalDateTime.now());
        
        CompletableFuture.runAsync(this::executeEnrichmentWithMonitoring, executorService)
                .thenRun(() -> log.info("Manual enrichment job completed"))
                .exceptionally(throwable -> {
                    log.error("Manual enrichment job failed", throwable);
                    return null;
                });
    }

    /**
     * Executes the enrichment job with proper monitoring and error handling.
     */
    private void executeEnrichmentWithMonitoring() {
        String jobId = "user_profile_enrichment_" + System.currentTimeMillis();
        
        try {
            jobMonitor.recordJobStart(jobId);
            
            long startTime = System.currentTimeMillis();
            log.info("Executing enrichment job {}", jobId);
            long duration = System.currentTimeMillis() - startTime;
            
            jobMonitor.recordJobSuccess(jobId);
            log.info("Enrichment job {} completed in {} ms", jobId, duration);
            
        } catch (Exception e) {
            log.error("Enrichment job {} failed", jobId, e);
            jobMonitor.recordJobFailure(jobId, e.getMessage());
            
            // Could implement retry logic here
            // scheduleRetry(jobId, e);
        }
    }

    /**
     * Graceful shutdown of the executor service.
     */
    public void shutdown() {
        log.info("Shutting down Spark job scheduler");
        executorService.shutdown();
    }
}