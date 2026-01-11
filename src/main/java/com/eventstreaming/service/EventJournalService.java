package com.eventstreaming.service;

import com.eventstreaming.cluster.PersistentUserActor;
import com.eventstreaming.cluster.UserActorState;
import com.eventstreaming.persistence.H2EventJournal;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.persistence.query.PersistenceQuery;
import org.apache.pekko.persistence.query.journal.leveldb.javadsl.LeveldbReadJournal;
import org.apache.pekko.stream.javadsl.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * Service for querying the Pekko event journal to retrieve persisted events.
 */
@Service
public class EventJournalService {
    
    private static final Logger logger = LoggerFactory.getLogger(EventJournalService.class);
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(10);
    
    @Autowired
    private ActorSystem<?> actorSystem;
    
    @Autowired(required = false)
    private ClusterSharding clusterSharding;
    
    @Autowired(required = false)
    private H2EventJournal h2EventJournal;
    
    @Autowired
    private Environment environment;
    
    /**
     * Get events for a specific user from the event journal.
     */
    public Map<String, Object> getUserEvents(String userId, long fromSequenceNr, int maxEvents) {
        try {
            logger.info("Retrieving events for userId: {} from sequence: {} max: {}", userId, fromSequenceNr, maxEvents);
            
            // Check if we're using isolated profile and H2 is available
            boolean isIsolatedProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("isolated");
            
            String persistenceId = PersistentUserActor.ENTITY_TYPE_KEY.name() + "|" + userId;
            
            if (isIsolatedProfile && h2EventJournal != null) {
                // Use H2 database for isolated profile
                logger.info("Using H2 database for event journal in isolated profile");
                
                List<H2EventJournal.EventJournalEntry> journalEntries = 
                    h2EventJournal.getEvents(persistenceId, fromSequenceNr, maxEvents);
                
                List<Map<String, Object>> events = journalEntries.stream()
                    .map(entry -> {
                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("sequenceNr", entry.getSequenceNr());
                        eventData.put("persistenceId", entry.getPersistenceId());
                        eventData.put("timestamp", entry.getTimestamp());
                        eventData.put("eventType", entry.getEventType());
                        eventData.put("eventData", entry.getEventData());
                        return eventData;
                    })
                    .toList();
                
                Map<String, Object> result = new HashMap<>();
                result.put("userId", userId);
                result.put("persistenceId", persistenceId);
                result.put("events", events);
                result.put("eventCount", events.size());
                result.put("fromSequenceNr", fromSequenceNr);
                result.put("journalType", "h2");
                result.put("message", "Events stored in H2 database");
                
                logger.info("Retrieved {} events for userId: {} from H2 journal", events.size(), userId);
                return result;
            } else {
                // Use LevelDB journal for other profiles
                LeveldbReadJournal readJournal = PersistenceQuery.get(actorSystem)
                    .getReadJournalFor(LeveldbReadJournal.class, LeveldbReadJournal.Identifier());
                
                // Query events from the journal
                CompletionStage<List<Map<String, Object>>> eventsFuture = readJournal
                    .eventsByPersistenceId(persistenceId, fromSequenceNr, Long.MAX_VALUE)
                    .take(maxEvents)
                    .map(envelope -> {
                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("sequenceNr", envelope.sequenceNr());
                        eventData.put("persistenceId", envelope.persistenceId());
                        eventData.put("timestamp", envelope.timestamp());
                        eventData.put("event", envelope.event());
                        return eventData;
                    })
                    .runWith(Sink.seq(), actorSystem);
                
                List<Map<String, Object>> events = eventsFuture.toCompletableFuture().get();
                
                Map<String, Object> result = new HashMap<>();
                result.put("userId", userId);
                result.put("persistenceId", persistenceId);
                result.put("events", events);
                result.put("eventCount", events.size());
                result.put("fromSequenceNr", fromSequenceNr);
                result.put("journalType", "leveldb");
                
                logger.info("Retrieved {} events for userId: {} from LevelDB journal", events.size(), userId);
                return result;
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving events for userId: {}", userId, e);
            throw new RuntimeException("Failed to retrieve events for user: " + userId, e);
        }
    }
    
    /**
     * Get current state for a specific user by asking the persistent actor.
     */
    public Map<String, Object> getUserState(String userId) {
        try {
            logger.info("Retrieving current state for userId: {}", userId);
            
            // Check if cluster sharding is available
            if (clusterSharding == null) {
                logger.warn("ClusterSharding not available - returning empty state for userId: {}", userId);
                Map<String, Object> result = new HashMap<>();
                result.put("userId", userId);
                result.put("totalEvents", 0);
                result.put("eventTypeCounts", new HashMap<>());
                result.put("lastUpdated", null);
                result.put("firstEventTime", null);
                result.put("lastEventTime", null);
                result.put("error", "ClusterSharding not available");
                return result;
            }
            
            EntityRef<PersistentUserActor.Command> userActor = 
                clusterSharding.entityRefFor(PersistentUserActor.ENTITY_TYPE_KEY, userId);
            
            CompletionStage<PersistentUserActor.UserStatsResponse> responseFuture = 
                AskPattern.ask(userActor, 
                    PersistentUserActor.GetUserStats::new, 
                    ASK_TIMEOUT, 
                    actorSystem.scheduler());
            
            PersistentUserActor.UserStatsResponse response = responseFuture.toCompletableFuture().get();
            UserActorState state = response.state;
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", state.getUserId());
            result.put("totalEvents", state.getTotalEvents());
            result.put("eventTypeCounts", state.getEventTypeCounts());
            result.put("lastUpdated", state.getLastUpdated());
            result.put("firstEventTime", state.getFirstEventTime());
            result.put("lastEventTime", state.getLastEventTime());
            result.put("hasEvents", state.hasEvents());
            
            logger.info("Retrieved state for userId: {}, totalEvents: {}", userId, state.getTotalEvents());
            return result;
            
        } catch (Exception e) {
            logger.error("Error retrieving state for userId: {}", userId, e);
            throw new RuntimeException("Failed to retrieve state for user: " + userId, e);
        }
    }
    
    /**
     * Get all persistence IDs that have events in the journal.
     */
    public List<String> getAllPersistenceIds() {
        try {
            logger.info("Retrieving all persistence IDs from journal");
            
            // Check if we're using isolated profile and H2 is available
            boolean isIsolatedProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("isolated");
            
            if (isIsolatedProfile && h2EventJournal != null) {
                // Use H2 database for isolated profile
                List<String> persistenceIds = h2EventJournal.getAllPersistenceIds();
                
                // Filter to only PersistentUserActor persistence IDs and extract user IDs
                List<String> userIds = persistenceIds.stream()
                    .filter(id -> id.startsWith(PersistentUserActor.ENTITY_TYPE_KEY.name() + "|"))
                    .map(id -> id.substring(PersistentUserActor.ENTITY_TYPE_KEY.name().length() + 1))
                    .sorted()
                    .toList();
                
                logger.info("Found {} user persistence IDs from H2", userIds.size());
                return userIds;
            } else {
                // Use LevelDB journal for other profiles
                LeveldbReadJournal readJournal = PersistenceQuery.get(actorSystem)
                    .getReadJournalFor(LeveldbReadJournal.class, LeveldbReadJournal.Identifier());
                
                CompletionStage<List<String>> persistenceIdsFuture = readJournal
                    .persistenceIds()
                    .take(1000)
                    .runWith(Sink.seq(), actorSystem);
                
                List<String> persistenceIds = persistenceIdsFuture.toCompletableFuture().get();
                
                // Filter to only PersistentUserActor persistence IDs and extract user IDs
                List<String> userIds = persistenceIds.stream()
                    .filter(id -> id.startsWith(PersistentUserActor.ENTITY_TYPE_KEY.name() + "|"))
                    .map(id -> id.substring(PersistentUserActor.ENTITY_TYPE_KEY.name().length() + 1))
                    .sorted()
                    .toList();
                
                logger.info("Found {} user persistence IDs from LevelDB", userIds.size());
                return userIds;
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving persistence IDs", e);
            throw new RuntimeException("Failed to retrieve persistence IDs", e);
        }
    }
    
    /**
     * Get general statistics about the event journal.
     */
    public Map<String, Object> getJournalStats() {
        try {
            logger.info("Retrieving journal statistics");
            
            List<String> userIds = getAllPersistenceIds();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", userIds.size());
            stats.put("userIds", userIds);
            
            // Get event counts for each user
            Map<String, Long> userEventCounts = new HashMap<>();
            long totalEventsAcrossAllUsers = 0;
            
            for (String userId : userIds) {
                try {
                    Map<String, Object> userState = getUserState(userId);
                    Long userEventCount = (Long) userState.get("totalEvents");
                    userEventCounts.put(userId, userEventCount);
                    totalEventsAcrossAllUsers += userEventCount;
                } catch (Exception e) {
                    logger.warn("Failed to get event count for userId: {}", userId, e);
                    userEventCounts.put(userId, 0L);
                }
            }
            
            stats.put("userEventCounts", userEventCounts);
            stats.put("totalEventsAcrossAllUsers", totalEventsAcrossAllUsers);
            
            logger.info("Journal stats: {} users, {} total events", userIds.size(), totalEventsAcrossAllUsers);
            return stats;
            
        } catch (Exception e) {
            logger.error("Error retrieving journal statistics", e);
            throw new RuntimeException("Failed to retrieve journal statistics", e);
        }
    }
}