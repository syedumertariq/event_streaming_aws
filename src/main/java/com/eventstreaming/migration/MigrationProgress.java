package com.eventstreaming.migration;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks migration progress and statistics
 */
public class MigrationProgress {
    
    private final AtomicLong totalRecords = new AtomicLong(0);
    private final AtomicLong processedRecords = new AtomicLong(0);
    private final AtomicLong successfulRecords = new AtomicLong(0);
    private final AtomicLong failedRecords = new AtomicLong(0);
    private final AtomicLong skippedRecords = new AtomicLong(0);
    
    private final Instant startTime;
    private volatile Instant endTime;
    private volatile boolean completed = false;
    private volatile String currentBatch = "";
    
    public MigrationProgress() {
        this.startTime = Instant.now();
    }
    
    public void setTotalRecords(long total) {
        this.totalRecords.set(total);
    }
    
    public void incrementProcessed() {
        this.processedRecords.incrementAndGet();
    }
    
    public void incrementSuccessful() {
        this.successfulRecords.incrementAndGet();
    }
    
    public void incrementFailed() {
        this.failedRecords.incrementAndGet();
    }
    
    public void incrementSkipped() {
        this.skippedRecords.incrementAndGet();
    }
    
    public void setCurrentBatch(String batchInfo) {
        this.currentBatch = batchInfo;
    }
    
    public void markCompleted() {
        this.completed = true;
        this.endTime = Instant.now();
    }
    
    public double getProgressPercentage() {
        long total = totalRecords.get();
        if (total == 0) return 0.0;
        return (double) processedRecords.get() / total * 100.0;
    }
    
    public long getRecordsPerSecond() {
        long durationSeconds = getDurationSeconds();
        if (durationSeconds == 0) return 0;
        return processedRecords.get() / durationSeconds;
    }
    
    public long getDurationSeconds() {
        Instant end = completed ? endTime : Instant.now();
        return end.getEpochSecond() - startTime.getEpochSecond();
    }
    
    public String getStatusReport() {
        return String.format(
            "Migration Progress: %.2f%% (%d/%d) | " +
            "Success: %d | Failed: %d | Skipped: %d | " +
            "Rate: %d records/sec | Duration: %d seconds | " +
            "Current: %s",
            getProgressPercentage(),
            processedRecords.get(),
            totalRecords.get(),
            successfulRecords.get(),
            failedRecords.get(),
            skippedRecords.get(),
            getRecordsPerSecond(),
            getDurationSeconds(),
            currentBatch
        );
    }
    
    // Getters
    public long getTotalRecords() { return totalRecords.get(); }
    public long getProcessedRecords() { return processedRecords.get(); }
    public long getSuccessfulRecords() { return successfulRecords.get(); }
    public long getFailedRecords() { return failedRecords.get(); }
    public long getSkippedRecords() { return skippedRecords.get(); }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public boolean isCompleted() { return completed; }
    public String getCurrentBatch() { return currentBatch; }
}