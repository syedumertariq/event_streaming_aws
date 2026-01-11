package com.eventstreaming.cluster;

import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check for cluster status.
 * Only active when cluster-related profiles are enabled.
 */
@Component
@Profile({"cluster", "cluster-mysql", "default", "test", "isolated", "aws-simple"})
public class ClusterHealthCheck {

    private final Cluster cluster;

    @Autowired
    public ClusterHealthCheck(Cluster cluster) {
        this.cluster = cluster;
    }

    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        try {
            MemberStatus selfStatus = cluster.selfMember().status();
            Iterable<org.apache.pekko.cluster.Member> members = cluster.state().getMembers();
            int memberCount = 0;
            for (org.apache.pekko.cluster.Member member : members) {
                memberCount++;
            }
            
            if (selfStatus == MemberStatus.up()) {
                health.put("status", "UP");
                health.put("selfStatus", selfStatus.toString());
                health.put("memberCount", memberCount);
                health.put("selfAddress", cluster.selfMember().address().toString());
            } else {
                health.put("status", "DOWN");
                health.put("selfStatus", selfStatus.toString());
                health.put("memberCount", memberCount);
                health.put("reason", "Cluster member is not UP");
            }
            
            return health;
            
        } catch (Exception e) {
            health.put("status", "ERROR");
            health.put("error", e.getMessage());
            return health;
        }
    }
}