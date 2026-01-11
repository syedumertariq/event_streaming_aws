package com.eventstreaming.enrichment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Monitoring and metrics collection for Spark batch jobs.
 * Tracks job execution times, success/failure rates, and provides health checks.
 */
@Component
public class SparkJobMonitor {

    private static final Logger log = LoggerFactory.getLogger(SparkJobMonitor.class);

    @Autowired
    private MeterRegistry meterRegistry;

    private final Counter jobSuccessCounter;
    private final Counter jobFailureCounter;
    private final Timer jobExecutionTimer;
    
    private final ConcurrentMap<String, JobExecution> activeJobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, JobStatus> jobHistory = new ConcurrentHashMap<>();

    public SparkJobMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.jobSuccessCounter = Counter.builder("spark.job.success")
                .description("Number of successful Spark job executions")
                .register(meterRegistry);
        this.jobFailureCounter = Counter.builder("spark.job.failure")
                .description("Number of failed Spark job executions")
                .register(meterRegistry);
        this.jobExecutionTimer = Timer.builder("spark.job.execution.time")
                .description("Spark job execution time")
                .register(meterRegistry);
    }

    /**
     * Records the start of a Spark job execution.
     */
    public void recordJobStart(String jobId) {
        JobExecution execution = new JobExecution(jobId, LocalDateTime.now());
        activeJobs.put(jobId, execution);
        
        log.info("Spark job started: {}", jobId);
        
        // Update gauge for active jobs count
        meterRegistry.gauge("spark.job.active.count", activeJobs.size());
    }

    /**
     * Records successful completion of a Spark job.
     */
    public void recordJobSuccess(String jobId) {
        recordJobSuccess(jobId, null);
    }

    /**
     * Records successful completion of a Spark job with duration.
     */
    public void recordJobSuccess(String jobId, Long durationMs) {
        JobExecution execution = activeJobs.remove(jobId);
        if (execution != null) {
            LocalDateTime endTime = LocalDateTime.now();
            long actualDuration = durationMs != null ? durationMs : 
                java.time.Duration.between(execution.startTime, endTime).toMillis();
            
            JobStatus status = new JobStatus(jobId, execution.startTime, endTime, 
                    JobStatus.Status.SUCCESS, null, actualDuration);
            jobHistory.put(jobId, status);
            
            jobSuccessCounter.increment();
            jobExecutionTimer.record(actualDuration, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            log.info("Spark job completed successfully: {} (duration: {} ms)", jobId, actualDuration);
        }
        
        meterRegistry.gauge("spark.job.active.count", activeJobs.size());
    }

    /**
     * Records failure of a Spark job.
     */
    public void recordJobFailure(String jobId, String errorMessage) {
        JobExecution execution = activeJobs.remove(jobId);
        if (execution != null) {
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(execution.startTime, endTime).toMillis();
            
            JobStatus status = new JobStatus(jobId, execution.startTime, endTime, 
                    JobStatus.Status.FAILED, errorMessage, duration);
            jobHistory.put(jobId, status);
            
            jobFailureCounter.increment();
            
            log.error("Spark job failed: {} (duration: {} ms) - Error: {}", 
                    jobId, duration, errorMessage);
        }
        
        meterRegistry.gauge("spark.job.active.count", activeJobs.size());
    }

    /**
     * Gets the current status of all active jobs.
     */
    public ConcurrentMap<String, JobExecution> getActiveJobs() {
        return new ConcurrentHashMap<>(activeJobs);
    }

    /**
     * Gets the history of completed jobs.
     */
    public ConcurrentMap<String, JobStatus> getJobHistory() {
        return new ConcurrentHashMap<>(jobHistory);
    }

    /**
     * Gets the latest job status for a specific job type.
     */
    public JobStatus getLatestJobStatus(String jobType) {
        return jobHistory.values().stream()
                .filter(status -> status.jobId.startsWith(jobType))
                .max((s1, s2) -> s1.startTime.compareTo(s2.startTime))
                .orElse(null);
    }

    /**
     * Checks if the Spark job system is healthy.
     */
    public boolean isHealthy() {
        // Consider system healthy if:
        // 1. No jobs have been stuck for more than 2 hours
        // 2. Recent job failure rate is below 50%
        
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
        
        // Check for stuck jobs
        boolean hasStuckJobs = activeJobs.values().stream()
                .anyMatch(job -> job.startTime.isBefore(twoHoursAgo));
        
        if (hasStuckJobs) {
            log.warn("Detected stuck Spark jobs running for more than 2 hours");
            return false;
        }
        
        // Check recent failure rate
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentJobs = jobHistory.values().stream()
                .filter(status -> status.startTime.isAfter(oneHourAgo))
                .count();
        
        if (recentJobs > 0) {
            long recentFailures = jobHistory.values().stream()
                    .filter(status -> status.startTime.isAfter(oneHourAgo))
                    .filter(status -> status.status == JobStatus.Status.FAILED)
                    .count();
            
            double failureRate = (double) recentFailures / recentJobs;
            if (failureRate > 0.5) {
                log.warn("High Spark job failure rate: {}/{} ({}%)", 
                        recentFailures, recentJobs, failureRate * 100);
                return false;
            }
        }
        
        return true;
    }

    /**
     * Cleans up old job history entries to prevent memory leaks.
     */
    public void cleanupOldHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        
        jobHistory.entrySet().removeIf(entry -> 
                entry.getValue().startTime.isBefore(cutoff));
        
        log.debug("Cleaned up old job history entries before {}", cutoff);
    }

    /**
     * Represents an active job execution.
     */
    public static class JobExecution {
        public final String jobId;
        public final LocalDateTime startTime;

        public JobExecution(String jobId, LocalDateTime startTime) {
            this.jobId = jobId;
            this.startTime = startTime;
        }
    }

    /**
     * Represents the status of a completed job.
     */
    public static class JobStatus {
        public final String jobId;
        public final LocalDateTime startTime;
        public final LocalDateTime endTime;
        public final Status status;
        public final String errorMessage;
        public final long durationMs;

        public JobStatus(String jobId, LocalDateTime startTime, LocalDateTime endTime, 
                        Status status, String errorMessage, long durationMs) {
            this.jobId = jobId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.status = status;
            this.errorMessage = errorMessage;
            this.durationMs = durationMs;
        }

        public enum Status {
            SUCCESS, FAILED
        }
    }
}