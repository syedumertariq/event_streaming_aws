package com.eventstreaming.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles errors during migration process with logging and recovery capabilities
 */
@Component
public class MigrationErrorHandler {
    
    private static final Logger log = LoggerFactory.getLogger(MigrationErrorHandler.class);
    
    private final ConcurrentLinkedQueue<MigrationError> errorQueue = new ConcurrentLinkedQueue<>();
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong skipCount = new AtomicLong(0);
    
    /**
     * Handle transformation error for a legacy record
     */
    public void handleTransformationError(LegacyEventRecord record, Exception error, boolean skipRecord) {
        MigrationError migrationError = new MigrationError(
            MigrationError.ErrorType.TRANSFORMATION_ERROR,
            String.valueOf(record.getId()),
            record.getUserId(),
            error.getMessage(),
            record.toString(),
            Instant.now()
        );
        
        logError(migrationError, skipRecord);
        errorQueue.offer(migrationError);
        
        if (skipRecord) {
            skipCount.incrementAndGet();
            log.warn("Skipping invalid record {} for user {}: {}", 
                    record.getId(), record.getUserId(), error.getMessage());
        } else {
            errorCount.incrementAndGet();
            log.error("Failed to transform record {} for user {}: {}", 
                     record.getId(), record.getUserId(), error.getMessage());
        }
    }
    
    /**
     * Handle actor processing error
     */
    public void handleActorError(String userId, String eventId, Exception error) {
        MigrationError migrationError = new MigrationError(
            MigrationError.ErrorType.ACTOR_PROCESSING_ERROR,
            eventId,
            userId,
            error.getMessage(),
            String.format("Event: %s, User: %s", eventId, userId),
            Instant.now()
        );
        
        logError(migrationError, false);
        errorQueue.offer(migrationError);
        errorCount.incrementAndGet();
        
        log.error("Actor processing failed for event {} and user {}: {}", 
                 eventId, userId, error.getMessage());
    }
    
    /**
     * Handle database connection error
     */
    public void handleDatabaseError(String operation, Exception error) {
        MigrationError migrationError = new MigrationError(
            MigrationError.ErrorType.DATABASE_ERROR,
            null,
            null,
            error.getMessage(),
            String.format("Operation: %s", operation),
            Instant.now()
        );
        
        logError(migrationError, false);
        errorQueue.offer(migrationError);
        errorCount.incrementAndGet();
        
        log.error("Database error during {}: {}", operation, error.getMessage());
    }
    
    /**
     * Handle general migration error
     */
    public void handleGeneralError(String context, Exception error) {
        MigrationError migrationError = new MigrationError(
            MigrationError.ErrorType.GENERAL_ERROR,
            null,
            null,
            error.getMessage(),
            context,
            Instant.now()
        );
        
        logError(migrationError, false);
        errorQueue.offer(migrationError);
        errorCount.incrementAndGet();
        
        log.error("General migration error in {}: {}", context, error.getMessage());
    }
    
    /**
     * Generate error report
     */
    public MigrationErrorReport generateErrorReport() {
        MigrationErrorReport report = new MigrationErrorReport();
        report.setTotalErrors(errorCount.get());
        report.setSkippedRecords(skipCount.get());
        report.setGenerationTime(Instant.now());
        
        // Copy errors to report (limit to avoid memory issues)
        int maxErrors = 1000;
        int errorIndex = 0;
        for (MigrationError error : errorQueue) {
            if (errorIndex >= maxErrors) {
                report.setTruncated(true);
                break;
            }
            report.addError(error);
            errorIndex++;
        }
        
        return report;
    }
    
    /**
     * Export errors to file for analysis
     */
    public void exportErrorsToFile(String filename) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("Migration Error Report\n");
            writer.write("=====================\n");
            writer.write(String.format("Generated: %s\n", 
                        DateTimeFormatter.ISO_INSTANT.format(Instant.now())));
            writer.write(String.format("Total Errors: %d\n", errorCount.get()));
            writer.write(String.format("Skipped Records: %d\n", skipCount.get()));
            writer.write("\n");
            
            writer.write("Error Details:\n");
            writer.write("--------------\n");
            
            for (MigrationError error : errorQueue) {
                writer.write(String.format(
                    "Time: %s | Type: %s | ID: %s | User: %s | Message: %s | Context: %s\n",
                    DateTimeFormatter.ISO_INSTANT.format(error.getTimestamp()),
                    error.getErrorType(),
                    error.getRecordId(),
                    error.getUserId(),
                    error.getErrorMessage(),
                    error.getContext()
                ));
            }
        }
        
        log.info("Exported {} errors to file: {}", errorQueue.size(), filename);
    }
    
    /**
     * Clear error history
     */
    public void clearErrors() {
        errorQueue.clear();
        errorCount.set(0);
        skipCount.set(0);
        log.info("Cleared migration error history");
    }
    
    /**
     * Check if migration should continue based on error rate
     */
    public boolean shouldContinueMigration(long totalProcessed, double maxErrorRate) {
        if (totalProcessed == 0) return true;
        
        double currentErrorRate = (double) errorCount.get() / totalProcessed;
        boolean shouldContinue = currentErrorRate <= maxErrorRate;
        
        if (!shouldContinue) {
            log.error("Migration error rate ({:.2f}%) exceeds maximum allowed rate ({:.2f}%). " +
                     "Consider stopping migration.", 
                     currentErrorRate * 100, maxErrorRate * 100);
        }
        
        return shouldContinue;
    }
    
    private void logError(MigrationError error, boolean skipped) {
        String logMessage = String.format(
            "Migration error [%s] - ID: %s, User: %s, Message: %s, Context: %s",
            error.getErrorType(),
            error.getRecordId(),
            error.getUserId(),
            error.getErrorMessage(),
            error.getContext()
        );
        
        if (skipped) {
            log.warn("SKIPPED: {}", logMessage);
        } else {
            log.error("ERROR: {}", logMessage);
        }
    }
    
    // Getters for statistics
    public long getErrorCount() { return errorCount.get(); }
    public long getSkipCount() { return skipCount.get(); }
    public int getQueueSize() { return errorQueue.size(); }
}