package com.eventstreaming.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * H2-based event journal for storing Pekko persistence events.
 * This provides a reliable alternative to LevelDB for development and testing.
 * Only active when H2-related profiles are enabled (test, h2).
 */
@Component
@Profile({"test", "h2"})
public class H2EventJournal {
    
    private static final Logger logger = LoggerFactory.getLogger(H2EventJournal.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // CRITICAL DIAGNOSTIC LOGGING - CONSTRUCTOR
    public H2EventJournal() {
        System.out.println("\n" + "⚡".repeat(100));
        System.out.println("⚡⚡⚡ H2EVENTJOURNAL.JAVA CONSTRUCTOR CALLED ⚡⚡⚡");
        System.out.println("⚡⚡⚡ SPRING IS CREATING H2EVENTJOURNAL BEAN ⚡⚡⚡");
        System.out.println("⚡⚡⚡ THIS SHOULD NOT HAPPEN WITH cluster-mysql PROFILE ⚡⚡⚡");
        System.out.println("⚡".repeat(100) + "\n");
    }
    
    @PostConstruct
    public void initializeSchema() {
        // CRITICAL DIAGNOSTIC LOGGING - EASILY VISIBLE
        System.out.println("\n" + "💥".repeat(100));
        System.out.println("💥💥💥 H2EVENTJOURNAL.JAVA INITIALIZESCHEMA() METHOD CALLED 💥💥💥");
        System.out.println("💥💥💥 THIS PROVES H2EVENTJOURNAL IS BEING ACTIVATED 💥💥💥");
        System.out.println("💥💥💥 THIS WILL CREATE event_journal TABLE 💥💥💥");
        System.out.println("💥💥💥 PROFILE CONDITIONS: test, h2, !cluster-mysql 💥💥💥");
        System.out.println("💥".repeat(100));
        
        try {
            System.out.println("💥💥💥 H2EVENTJOURNAL CREATING event_journal TABLE NOW 💥💥💥");
            
            // Create event journal table
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS event_journal (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    persistence_id VARCHAR(255) NOT NULL,
                    sequence_nr BIGINT NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    event_data TEXT NOT NULL,
                    timestamp TIMESTAMP NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(persistence_id, sequence_nr)
                )
            """);
            
            System.out.println("💥💥💥 H2EVENTJOURNAL event_journal TABLE CREATED SUCCESSFULLY 💥💥💥");
            
            // Create index for faster queries (H2 supports IF NOT EXISTS)
            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_persistence_id ON event_journal(persistence_id)
            """);
            
            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sequence_nr ON event_journal(persistence_id, sequence_nr)
            """);
            
            System.out.println("💥💥💥 H2EVENTJOURNAL INDEXES CREATED SUCCESSFULLY 💥💥💥");
            System.out.println("💥".repeat(100) + "\n");
            
            logger.info("✅ H2 Event Journal schema initialized successfully");
            
        } catch (Exception e) {
            System.out.println("💥💥💥 H2EVENTJOURNAL SCHEMA CREATION FAILED 💥💥💥");
            System.out.println("💥💥💥 ERROR: " + e.getMessage() + " 💥💥💥");
            System.out.println("💥".repeat(100) + "\n");
            
            logger.error("❌ Failed to initialize H2 Event Journal schema", e);
            throw new RuntimeException("Failed to initialize event journal schema", e);
        }
    }
    
    /**
     * Store an event in the H2 journal.
     */
    public void persistEvent(String persistenceId, long sequenceNr, Object event) {
        try {
            String eventType = event.getClass().getSimpleName();
            String eventData = objectMapper.writeValueAsString(event);
            
            jdbcTemplate.update("""
                INSERT INTO event_journal (persistence_id, sequence_nr, event_type, event_data, timestamp)
                VALUES (?, ?, ?, ?, ?)
                """,
                persistenceId, sequenceNr, eventType, eventData, LocalDateTime.now()
            );
            
            logger.debug("Persisted event: {} seq={} for {}", eventType, sequenceNr, persistenceId);
            
        } catch (Exception e) {
            logger.error("Failed to persist event for {}: {}", persistenceId, e.getMessage(), e);
            throw new RuntimeException("Failed to persist event", e);
        }
    }
    
    /**
     * Retrieve events for a specific persistence ID.
     */
    public List<EventJournalEntry> getEvents(String persistenceId, long fromSequenceNr, int maxEvents) {
        try {
            String sql = """
                SELECT persistence_id, sequence_nr, event_type, event_data, timestamp
                FROM event_journal
                WHERE persistence_id = ? AND sequence_nr >= ?
                ORDER BY sequence_nr
                LIMIT ?
                """;
            
            List<EventJournalEntry> events = jdbcTemplate.query(sql, 
                new EventJournalRowMapper(), 
                persistenceId, fromSequenceNr, maxEvents);
            
            logger.debug("Retrieved {} events for {} from sequence {}", events.size(), persistenceId, fromSequenceNr);
            return events;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve events for {}: {}", persistenceId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve events", e);
        }
    }
    
    /**
     * Get all persistence IDs that have events.
     */
    public List<String> getAllPersistenceIds() {
        try {
            String sql = "SELECT DISTINCT persistence_id FROM event_journal ORDER BY persistence_id";
            List<String> persistenceIds = jdbcTemplate.queryForList(sql, String.class);
            
            logger.debug("Found {} persistence IDs", persistenceIds.size());
            return persistenceIds;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve persistence IDs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve persistence IDs", e);
        }
    }
    
    /**
     * Get event count for a specific persistence ID.
     */
    public long getEventCount(String persistenceId) {
        try {
            String sql = "SELECT COUNT(*) FROM event_journal WHERE persistence_id = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, persistenceId);
            return count != null ? count : 0L;
            
        } catch (Exception e) {
            logger.error("Failed to get event count for {}: {}", persistenceId, e.getMessage(), e);
            return 0L;
        }
    }
    
    /**
     * Get the highest sequence number for a persistence ID.
     */
    public long getHighestSequenceNr(String persistenceId) {
        try {
            String sql = "SELECT COALESCE(MAX(sequence_nr), 0) FROM event_journal WHERE persistence_id = ?";
            Long maxSeq = jdbcTemplate.queryForObject(sql, Long.class, persistenceId);
            return maxSeq != null ? maxSeq : 0L;
            
        } catch (Exception e) {
            logger.error("Failed to get highest sequence number for {}: {}", persistenceId, e.getMessage(), e);
            return 0L;
        }
    }
    
    /**
     * Clear all events (for testing).
     */
    public void clearAllEvents() {
        try {
            jdbcTemplate.update("DELETE FROM event_journal");
            logger.info("Cleared all events from H2 journal");
            
        } catch (Exception e) {
            logger.error("Failed to clear events: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to clear events", e);
        }
    }
    
    /**
     * Row mapper for event journal entries.
     */
    private static class EventJournalRowMapper implements RowMapper<EventJournalEntry> {
        @Override
        public EventJournalEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EventJournalEntry(
                rs.getString("persistence_id"),
                rs.getLong("sequence_nr"),
                rs.getString("event_type"),
                rs.getString("event_data"),
                rs.getTimestamp("timestamp").toLocalDateTime()
            );
        }
    }
    
    /**
     * Event journal entry data class.
     */
    public static class EventJournalEntry {
        private final String persistenceId;
        private final long sequenceNr;
        private final String eventType;
        private final String eventData;
        private final LocalDateTime timestamp;
        
        public EventJournalEntry(String persistenceId, long sequenceNr, String eventType, 
                               String eventData, LocalDateTime timestamp) {
            this.persistenceId = persistenceId;
            this.sequenceNr = sequenceNr;
            this.eventType = eventType;
            this.eventData = eventData;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getPersistenceId() { return persistenceId; }
        public long getSequenceNr() { return sequenceNr; }
        public String getEventType() { return eventType; }
        public String getEventData() { return eventData; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("EventJournalEntry{persistenceId='%s', sequenceNr=%d, eventType='%s', timestamp=%s}",
                persistenceId, sequenceNr, eventType, timestamp);
        }
    }
}