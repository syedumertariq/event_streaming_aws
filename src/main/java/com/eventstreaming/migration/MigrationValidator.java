package com.eventstreaming.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Validates data consistency between source and target after migration
 */
@Component
public class MigrationValidator {
    
    private static final Logger log = LoggerFactory.getLogger(MigrationValidator.class);
    
    @Autowired
    private MigrationConfig migrationConfig;
    
    @Autowired
    private DataSource targetDataSource; // Pekko Persistence MySQL
    
    /**
     * Validate migration results by comparing source and target counts
     */
    public ValidationReport validateMigration() {
        log.info("Starting migration validation");
        
        ValidationReport report = new ValidationReport();
        
        try {
            // Get source counts from legacy database
            Map<String, Long> sourceCounts = getSourceCounts();
            report.setSourceCounts(sourceCounts);
            
            // Get target counts from Pekko Persistence tables
            Map<String, Long> targetCounts = getTargetCounts();
            report.setTargetCounts(targetCounts);
            
            // Compare counts and identify discrepancies
            validateCounts(sourceCounts, targetCounts, report);
            
            // Validate data integrity
            validateDataIntegrity(report);
            
            report.setValidationPassed(report.getDiscrepancies().isEmpty() && 
                                     report.getIntegrityIssues().isEmpty());
            
            log.info("Migration validation completed. Passed: {}", report.isValidationPassed());
            
        } catch (Exception e) {
            log.error("Migration validation failed", e);
            report.setValidationPassed(false);
            report.addIntegrityIssue("Validation failed with exception: " + e.getMessage());
        }
        
        return report;
    }
    
    private Map<String, Long> getSourceCounts() throws SQLException {
        Map<String, Long> counts = new HashMap<>();
        
        String sql = String.format(
            "SELECT " +
            "  COUNT(*) as total_count, " +
            "  COUNT(DISTINCT user_id) as unique_users, " +
            "  SUM(CASE WHEN channel = 'EMAIL' THEN 1 ELSE 0 END) as email_events, " +
            "  SUM(CASE WHEN channel = 'SMS' THEN 1 ELSE 0 END) as sms_events, " +
            "  SUM(CASE WHEN channel = 'CALL' OR channel = 'PHONE' THEN 1 ELSE 0 END) as call_events " +
            "FROM %s",
            migrationConfig.getLegacyTableName()
        );
        
        try (Connection conn = createLegacyConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                counts.put("total_events", rs.getLong("total_count"));
                counts.put("unique_users", rs.getLong("unique_users"));
                counts.put("email_events", rs.getLong("email_events"));
                counts.put("sms_events", rs.getLong("sms_events"));
                counts.put("call_events", rs.getLong("call_events"));
            }
        }
        
        return counts;
    }
    
    private Map<String, Long> getTargetCounts() throws SQLException {
        Map<String, Long> counts = new HashMap<>();
        
        // Query Pekko Persistence journal table for migrated events
        String sql = 
            "SELECT " +
            "  COUNT(*) as total_events, " +
            "  COUNT(DISTINCT persistence_id) as unique_users, " +
            "  SUM(CASE WHEN event_payload LIKE '%EMAIL%' THEN 1 ELSE 0 END) as email_events, " +
            "  SUM(CASE WHEN event_payload LIKE '%SMS%' THEN 1 ELSE 0 END) as sms_events, " +
            "  SUM(CASE WHEN event_payload LIKE '%CALL%' THEN 1 ELSE 0 END) as call_events " +
            "FROM journal " +
            "WHERE event_payload LIKE '%migrationSequence%'"; // Only count migrated events
        
        try (Connection conn = targetDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                counts.put("total_events", rs.getLong("total_events"));
                counts.put("unique_users", rs.getLong("unique_users"));
                counts.put("email_events", rs.getLong("email_events"));
                counts.put("sms_events", rs.getLong("sms_events"));
                counts.put("call_events", rs.getLong("call_events"));
            }
        }
        
        return counts;
    }
    
    private void validateCounts(Map<String, Long> sourceCounts, Map<String, Long> targetCounts, 
                              ValidationReport report) {
        
        for (String countType : sourceCounts.keySet()) {
            long sourceCount = sourceCounts.get(countType);
            long targetCount = targetCounts.getOrDefault(countType, 0L);
            
            if (sourceCount != targetCount) {
                String discrepancy = String.format(
                    "%s: Source=%d, Target=%d, Difference=%d", 
                    countType, sourceCount, targetCount, sourceCount - targetCount
                );
                report.addDiscrepancy(discrepancy);
                log.warn("Count discrepancy found: {}", discrepancy);
            } else {
                log.info("Count validation passed for {}: {}", countType, sourceCount);
            }
        }
    }
    
    private void validateDataIntegrity(ValidationReport report) throws SQLException {
        // Check for duplicate events in target
        validateNoDuplicateEvents(report);
        
        // Check for missing user IDs
        validateUserIdConsistency(report);
        
        // Check timestamp ordering
        validateTimestampOrdering(report);
    }
    
    private void validateNoDuplicateEvents(ValidationReport report) throws SQLException {
        String sql = 
            "SELECT persistence_id, COUNT(*) as duplicate_count " +
            "FROM journal " +
            "WHERE event_payload LIKE '%migrationSequence%' " +
            "GROUP BY persistence_id, sequence_number " +
            "HAVING COUNT(*) > 1";
        
        try (Connection conn = targetDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int duplicateCount = 0;
            while (rs.next()) {
                duplicateCount++;
                String issue = String.format(
                    "Duplicate events found for persistence_id: %s, count: %d",
                    rs.getString("persistence_id"),
                    rs.getInt("duplicate_count")
                );
                report.addIntegrityIssue(issue);
            }
            
            if (duplicateCount == 0) {
                log.info("No duplicate events found in target database");
            } else {
                log.warn("Found {} duplicate event groups in target database", duplicateCount);
            }
        }
    }
    
    private void validateUserIdConsistency(ValidationReport report) throws SQLException {
        // Check if all user IDs from source exist in target
        String sql = 
            "SELECT DISTINCT user_id FROM " + migrationConfig.getLegacyTableName() + " " +
            "WHERE user_id NOT IN (" +
            "  SELECT DISTINCT SUBSTRING_INDEX(persistence_id, '|', -1) " +
            "  FROM journal " +
            "  WHERE event_payload LIKE '%migrationSequence%'" +
            ")";
        
        try (Connection conn = createLegacyConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int missingUsers = 0;
            while (rs.next()) {
                missingUsers++;
                String issue = String.format(
                    "User ID missing in target: %s", 
                    rs.getString("user_id")
                );
                report.addIntegrityIssue(issue);
            }
            
            if (missingUsers == 0) {
                log.info("All user IDs successfully migrated");
            } else {
                log.warn("Found {} user IDs missing in target database", missingUsers);
            }
        }
    }
    
    private void validateTimestampOrdering(ValidationReport report) throws SQLException {
        // Check if events maintain proper ordering within each user
        String sql = 
            "SELECT persistence_id, COUNT(*) as out_of_order_count " +
            "FROM (" +
            "  SELECT persistence_id, sequence_number, " +
            "         LAG(created) OVER (PARTITION BY persistence_id ORDER BY sequence_number) as prev_timestamp, " +
            "         created " +
            "  FROM journal " +
            "  WHERE event_payload LIKE '%migrationSequence%'" +
            ") ordered_events " +
            "WHERE prev_timestamp > created " +
            "GROUP BY persistence_id";
        
        try (Connection conn = targetDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int orderingIssues = 0;
            while (rs.next()) {
                orderingIssues++;
                String issue = String.format(
                    "Timestamp ordering issue for user: %s, out-of-order events: %d",
                    rs.getString("persistence_id"),
                    rs.getInt("out_of_order_count")
                );
                report.addIntegrityIssue(issue);
            }
            
            if (orderingIssues == 0) {
                log.info("Event timestamp ordering validation passed");
            } else {
                log.warn("Found {} users with timestamp ordering issues", orderingIssues);
            }
        }
    }
    
    private Connection createLegacyConnection() throws SQLException {
        MigrationConfig.LegacyDatabase config = migrationConfig.getLegacyDatabase();
        return DriverManager.getConnection(
            config.getUrl(),
            config.getUsername(),
            config.getPassword()
        );
    }
}