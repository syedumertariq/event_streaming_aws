package com.eventstreaming.cluster;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.typed.Cluster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for monitoring cluster health and performance.
 * Only active when cluster-related profiles are enabled.
 */
@Service
@Profile({"cluster", "default", "test", "isolated", "aws-simple"})
public class ClusterMonitoringService {

    private final Cluster cluster;
    private final ClusterSharding sharding;
    private final ActorSystem<?> actorSystem;
    private final Instant startTime = Instant.now();

    @Autowired
    public ClusterMonitoringService(Cluster cluster, ClusterSharding sharding, ActorSystem<?> actorSystem) {
        this.cluster = cluster;
        this.sharding = sharding;
        this.actorSystem = actorSystem;
    }

    /**
     * Get comprehensive monitoring information about the cluster.
     */
    public ClusterController.ClusterMonitoringInfo getMonitoringInfo() {
        try {
            // Calculate uptime
            long uptimeSeconds = Duration.between(startTime, Instant.now()).getSeconds();
            
            // Get cluster health
            String clusterHealth = determineClusterHealth();
            
            // For now, we'll use placeholder values for shards and entities
            // In a real implementation, you'd query the sharding coordinator
            int activeShards = 100; // This would come from sharding coordinator
            int totalEntities = 0;  // This would come from entity counting
            
            return new ClusterController.ClusterMonitoringInfo(
                activeShards,
                totalEntities,
                clusterHealth,
                uptimeSeconds
            );
            
        } catch (Exception e) {
            actorSystem.log().error("Error getting monitoring info: {}", e.getMessage(), e);
            return new ClusterController.ClusterMonitoringInfo(0, 0, "ERROR", 0);
        }
    }

    private String determineClusterHealth() {
        try {
            Iterable<org.apache.pekko.cluster.Member> members = cluster.state().getMembers();
            int memberCount = 0;
            for (org.apache.pekko.cluster.Member member : members) {
                memberCount++;
            }
            boolean selfUp = cluster.selfMember().status().toString().equals("Up");
            
            if (selfUp && memberCount > 0) {
                return "HEALTHY";
            } else if (selfUp) {
                return "DEGRADED";
            } else {
                return "UNHEALTHY";
            }
        } catch (Exception e) {
            return "ERROR";
        }
    }

    /**
     * Get basic cluster statistics.
     */
    public ClusterStats getClusterStats() {
        Iterable<org.apache.pekko.cluster.Member> members = cluster.state().getMembers();
        int memberCount = 0;
        for (org.apache.pekko.cluster.Member member : members) {
            memberCount++;
        }
        String selfStatus = cluster.selfMember().status().toString();
        String selfAddress = cluster.selfMember().address().toString();
        long uptime = Duration.between(startTime, Instant.now()).getSeconds();
        
        return new ClusterStats(memberCount, selfStatus, selfAddress, uptime);
    }

    public static class ClusterStats {
        public final int memberCount;
        public final String selfStatus;
        public final String selfAddress;
        public final long uptimeSeconds;

        public ClusterStats(int memberCount, String selfStatus, String selfAddress, long uptimeSeconds) {
            this.memberCount = memberCount;
            this.selfStatus = selfStatus;
            this.selfAddress = selfAddress;
            this.uptimeSeconds = uptimeSeconds;
        }

        @Override
        public String toString() {
            return String.format("ClusterStats{members=%d, status=%s, address=%s, uptime=%ds}", 
                memberCount, selfStatus, selfAddress, uptimeSeconds);
        }
    }
}