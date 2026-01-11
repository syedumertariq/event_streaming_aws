package com.eventstreaming.actor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DEPRECATED: Additional commands for UserActor
 * 
 * These commands were used for keep-warm and pre-allocation logic,
 * which has been removed in favor of Pekko's built-in optimizations.
 * 
 * Kept for backward compatibility but should not be used in new code.
 */
@Deprecated
public class UserActorCommands {
    
    /**
     * @deprecated Keep-warm logic removed. PersistentUserActor recovers fast with snapshots.
     */
    @Deprecated
    public static final class KeepWarm implements UserActor.Command {
        public static final KeepWarm INSTANCE = new KeepWarm();
        
        private KeepWarm() {}
        
        @Override
        public String toString() {
            return "KeepWarm(DEPRECATED)";
        }
    }
    
    /**
     * @deprecated Ping logic removed. Use direct actor messages instead.
     */
    @Deprecated
    public static final class Ping implements UserActor.Command {
        public static final Ping INSTANCE = new Ping();
        
        private Ping() {}
        
        @Override
        public String toString() {
            return "Ping(DEPRECATED)";
        }
    }
    
    /**
     * @deprecated Pong responses removed with ping logic.
     */
    @Deprecated
    public static final class Pong implements UserActor.Command {
        private final String userId;
        private final long timestamp;
        
        @JsonCreator
        public Pong(@JsonProperty("userId") String userId, @JsonProperty("timestamp") long timestamp) {
            this.userId = userId;
            this.timestamp = timestamp;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return "Pong{userId='" + userId + "', timestamp=" + timestamp + "}(DEPRECATED)";
        }
    }
    
    /**
     * @deprecated Stats collection removed. Use Pekko's built-in metrics instead.
     */
    @Deprecated
    public static final class GetStats implements UserActor.Command {
        public static final GetStats INSTANCE = new GetStats();
        
        private GetStats() {}
        
        @Override
        public String toString() {
            return "GetStats(DEPRECATED)";
        }
    }
    
    /**
     * @deprecated Stats responses removed with stats collection.
     */
    @Deprecated
    public static final class ActorStats implements UserActor.Command {
        private final String userId;
        private final long totalEvents;
        private final long uptime;
        private final boolean isHot;
        
        @JsonCreator
        public ActorStats(@JsonProperty("userId") String userId, 
                         @JsonProperty("totalEvents") long totalEvents,
                         @JsonProperty("uptime") long uptime,
                         @JsonProperty("isHot") boolean isHot) {
            this.userId = userId;
            this.totalEvents = totalEvents;
            this.uptime = uptime;
            this.isHot = isHot;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public long getTotalEvents() {
            return totalEvents;
        }
        
        public long getUptime() {
            return uptime;
        }
        
        public boolean isHot() {
            return isHot;
        }
        
        @Override
        public String toString() {
            return "ActorStats{userId='" + userId + "', totalEvents=" + totalEvents + 
                   ", uptime=" + uptime + ", isHot=" + isHot + "}(DEPRECATED)";
        }
    }
}