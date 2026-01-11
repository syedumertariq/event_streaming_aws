package com.eventstreaming.migration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Report containing validation results after migration
 */
public class ValidationReport {
    
    private boolean validationPassed = false;
    private Instant validationTime = Instant.now();
    private Map<String, Long> sourceCounts = new HashMap<>();
    private Map<String, Long> targetCounts = new HashMap<>();
    private List<String> discrepancies = new ArrayList<>();
    private List<String> integrityIssues = new ArrayList<>();
    
    public ValidationReport() {}
    
    public void addDiscrepancy(String discrepancy) {
        this.discrepancies.add(discrepancy);
    }
    
    public void addIntegrityIssue(String issue) {
        this.integrityIssues.add(issue);
    }
    
    public boolean hasIssues() {
        return !discrepancies.isEmpty() || !integrityIssues.isEmpty();
    }
    
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Migration Validation Report\n");
        summary.append("==========================\n");
        summary.append(String.format("Validation Time: %s\n", validationTime));
        summary.append(String.format("Validation Passed: %s\n", validationPassed));
        summary.append("\n");
        
        summary.append("Source Counts:\n");
        sourceCounts.forEach((key, value) -> 
            summary.append(String.format("  %s: %d\n", key, value)));
        summary.append("\n");
        
        summary.append("Target Counts:\n");
        targetCounts.forEach((key, value) -> 
            summary.append(String.format("  %s: %d\n", key, value)));
        summary.append("\n");
        
        if (!discrepancies.isEmpty()) {
            summary.append("Count Discrepancies:\n");
            discrepancies.forEach(discrepancy -> 
                summary.append(String.format("  - %s\n", discrepancy)));
            summary.append("\n");
        }
        
        if (!integrityIssues.isEmpty()) {
            summary.append("Data Integrity Issues:\n");
            integrityIssues.forEach(issue -> 
                summary.append(String.format("  - %s\n", issue)));
            summary.append("\n");
        }
        
        if (validationPassed) {
            summary.append("✅ Migration validation PASSED - All data migrated successfully\n");
        } else {
            summary.append("❌ Migration validation FAILED - Issues found that need attention\n");
        }
        
        return summary.toString();
    }
    
    // Getters and setters
    public boolean isValidationPassed() { return validationPassed; }
    public void setValidationPassed(boolean validationPassed) { this.validationPassed = validationPassed; }
    
    public Instant getValidationTime() { return validationTime; }
    public void setValidationTime(Instant validationTime) { this.validationTime = validationTime; }
    
    public Map<String, Long> getSourceCounts() { return sourceCounts; }
    public void setSourceCounts(Map<String, Long> sourceCounts) { this.sourceCounts = sourceCounts; }
    
    public Map<String, Long> getTargetCounts() { return targetCounts; }
    public void setTargetCounts(Map<String, Long> targetCounts) { this.targetCounts = targetCounts; }
    
    public List<String> getDiscrepancies() { return discrepancies; }
    public void setDiscrepancies(List<String> discrepancies) { this.discrepancies = discrepancies; }
    
    public List<String> getIntegrityIssues() { return integrityIssues; }
    public void setIntegrityIssues(List<String> integrityIssues) { this.integrityIssues = integrityIssues; }
}