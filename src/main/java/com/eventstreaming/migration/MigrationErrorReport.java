package com.eventstreaming.migration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Report containing all migration errors and statistics
 */
public class MigrationErrorReport {
    
    private long totalErrors = 0;
    private long skippedRecords = 0;
    private Instant generationTime;
    private boolean truncated = false;
    private List<MigrationError> errors = new ArrayList<>();
    
    public void addError(MigrationError error) {
        this.errors.add(error);
    }
    
    public Map<MigrationError.ErrorType, Long> getErrorsByType() {
        return errors.stream()
            .collect(Collectors.groupingBy(
                MigrationError::getErrorType,
                Collectors.counting()
            ));
    }
    
    public Map<String, Long> getErrorsByUser() {
        return errors.stream()
            .filter(error -> error.getUserId() != null)
            .collect(Collectors.groupingBy(
                MigrationError::getUserId,
                Collectors.counting()
            ));
    }
    
    public List<MigrationError> getErrorsForUser(String userId) {
        return errors.stream()
            .filter(error -> userId.equals(error.getUserId()))
            .collect(Collectors.toList());
    }
    
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Migration Error Report Summary\n");
        summary.append("=============================\n");
        summary.append(String.format("Generation Time: %s\n", generationTime));
        summary.append(String.format("Total Errors: %d\n", totalErrors));
        summary.append(String.format("Skipped Records: %d\n", skippedRecords));
        summary.append(String.format("Errors in Report: %d\n", errors.size()));
        
        if (truncated) {
            summary.append("⚠️  Report truncated - showing first 1000 errors only\n");
        }
        
        summary.append("\nErrors by Type:\n");
        getErrorsByType().forEach((type, count) -> 
            summary.append(String.format("  %s: %d\n", type, count)));
        
        summary.append("\nTop 10 Users by Error Count:\n");
        getErrorsByUser().entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> 
                summary.append(String.format("  %s: %d errors\n", entry.getKey(), entry.getValue())));
        
        return summary.toString();
    }
    
    // Getters and setters
    public long getTotalErrors() { return totalErrors; }
    public void setTotalErrors(long totalErrors) { this.totalErrors = totalErrors; }
    
    public long getSkippedRecords() { return skippedRecords; }
    public void setSkippedRecords(long skippedRecords) { this.skippedRecords = skippedRecords; }
    
    public Instant getGenerationTime() { return generationTime; }
    public void setGenerationTime(Instant generationTime) { this.generationTime = generationTime; }
    
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
    
    public List<MigrationError> getErrors() { return errors; }
    public void setErrors(List<MigrationError> errors) { this.errors = errors; }
}