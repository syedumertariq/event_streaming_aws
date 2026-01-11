package com.eventstreaming.persistence;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.util.concurrent.CompletionException;

/**
 * Persistence error handler for MySQL-specific error handling and recovery.
 * 
 * This class provides specialized error handling for database connectivity issues,
 * transaction failures, and other MySQL-specific errors that may occur during
 * event sourcing operations.
 */
public class PersistenceErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(PersistenceErrorHandler.class);
    
    // MySQL error codes for specific error handling
    private static final int MYSQL_CONNECTION_ERROR = 1040;
    private static final int MYSQL_TIMEOUT_ERROR = 1205;
    private static final int MYSQL_DEADLOCK_ERROR = 1213;
    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;
    private static final int MYSQL_TABLE_DOESNT_EXIST = 1146;
    
    /**
     * Create a supervisor strategy for persistence-related actors.
     * 
     * This strategy handles different types of database errors with appropriate
     * recovery mechanisms including retries, restarts, and escalation.
     */
    public static SupervisorStrategy createPersistenceSupervisorStrategy() {
        return SupervisorStrategy.restart()
            .withLimit(3, Duration.ofMinutes(1))
            .withLoggingEnabled(true)
            .withStopChildren(false);
    }
    
    /**
     * Create a behavior wrapper that handles persistence errors.
     */
    public static <T> Behavior<T> withPersistenceErrorHandling(
            Behavior<T> behavior, 
            String actorName) {
        
        return Behaviors.supervise(behavior)
            .onFailure(Exception.class, SupervisorStrategy.restart()
                .withLimit(5, Duration.ofMinutes(2))
                .withLoggingEnabled(true));
    }
    
    /**
     * Analyze SQL exception and determine appropriate recovery action.
     */
    public static RecoveryAction analyzeException(Throwable throwable) {
        if (throwable instanceof SQLException sqlException) {
            return analyzeSQLException(sqlException);
        }
        
        if (throwable instanceof CompletionException completionException) {
            Throwable cause = completionException.getCause();
            if (cause instanceof SQLException sqlException) {
                return analyzeSQLException(sqlException);
            }
        }
        
        // Default recovery for unknown exceptions
        logger.warn("Unknown persistence exception: {}", throwable.getMessage());
        return RecoveryAction.RESTART;
    }
    
    /**
     * Analyze MySQL-specific SQL exceptions.
     */
    private static RecoveryAction analyzeSQLException(SQLException sqlException) {
        int errorCode = sqlException.getErrorCode();
        String sqlState = sqlException.getSQLState();
        
        logger.error("MySQL error - Code: {}, SQLState: {}, Message: {}", 
                    errorCode, sqlState, sqlException.getMessage());
        
        return switch (errorCode) {
            case MYSQL_CONNECTION_ERROR -> {
                logger.warn("MySQL connection limit reached - will retry with backoff");
                yield RecoveryAction.RETRY_WITH_BACKOFF;
            }
            
            case MYSQL_TIMEOUT_ERROR -> {
                logger.warn("MySQL timeout error - will retry");
                yield RecoveryAction.RETRY;
            }
            
            case MYSQL_DEADLOCK_ERROR -> {
                logger.warn("MySQL deadlock detected - will retry with random delay");
                yield RecoveryAction.RETRY_WITH_RANDOM_DELAY;
            }
            
            case MYSQL_DUPLICATE_KEY_ERROR -> {
                logger.error("MySQL duplicate key error - this indicates a logic error");
                yield RecoveryAction.STOP;
            }
            
            case MYSQL_TABLE_DOESNT_EXIST -> {
                logger.error("MySQL table doesn't exist - database schema issue");
                yield RecoveryAction.ESCALATE;
            }
            
            default -> {
                if (sqlState != null && sqlState.startsWith("08")) {
                    // Connection-related errors
                    logger.warn("MySQL connection error (SQLState: {}) - will restart", sqlState);
                    yield RecoveryAction.RESTART;
                } else if (sqlState != null && sqlState.startsWith("40")) {
                    // Transaction rollback errors
                    logger.warn("MySQL transaction error (SQLState: {}) - will retry", sqlState);
                    yield RecoveryAction.RETRY;
                } else {
                    logger.error("Unknown MySQL error - will restart");
                    yield RecoveryAction.RESTART;
                }
            }
        };
    }
    
    /**
     * Log persistence metrics for monitoring and alerting.
     */
    public static void logPersistenceMetrics(String operation, long durationMs, boolean success) {
        if (success) {
            logger.debug("Persistence operation '{}' completed in {}ms", operation, durationMs);
        } else {
            logger.warn("Persistence operation '{}' failed after {}ms", operation, durationMs);
        }
        
        // TODO: Integrate with Micrometer metrics
        // meterRegistry.timer("persistence.operation", "type", operation, "success", String.valueOf(success))
        //     .record(Duration.ofMillis(durationMs));
    }
    
    /**
     * Check if an exception is retryable.
     */
    public static boolean isRetryableException(Throwable throwable) {
        RecoveryAction action = analyzeException(throwable);
        return action == RecoveryAction.RETRY || 
               action == RecoveryAction.RETRY_WITH_BACKOFF || 
               action == RecoveryAction.RETRY_WITH_RANDOM_DELAY;
    }
    
    /**
     * Get retry delay based on the exception type and attempt number.
     */
    public static Duration getRetryDelay(Throwable throwable, int attemptNumber) {
        RecoveryAction action = analyzeException(throwable);
        
        return switch (action) {
            case RETRY -> Duration.ofMillis(100);
            case RETRY_WITH_BACKOFF -> Duration.ofMillis(100 * (long) Math.pow(2, attemptNumber));
            case RETRY_WITH_RANDOM_DELAY -> Duration.ofMillis(100 + (long) (Math.random() * 500));
            default -> Duration.ZERO;
        };
    }
    
    /**
     * Recovery actions for different types of persistence errors.
     */
    public enum RecoveryAction {
        RETRY,                    // Simple retry
        RETRY_WITH_BACKOFF,      // Exponential backoff retry
        RETRY_WITH_RANDOM_DELAY, // Random delay retry (for deadlocks)
        RESTART,                 // Restart the actor
        STOP,                    // Stop the actor (unrecoverable error)
        ESCALATE                 // Escalate to parent supervisor
    }
}