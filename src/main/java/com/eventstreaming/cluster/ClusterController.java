package com.eventstreaming.cluster;

import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.typed.Cluster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.eventstreaming.model.CommunicationEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * REST controller for cluster operations and monitoring.
 */
@RestController
@RequestMapping("/api/cluster")
@Profile({"cluster", "default", "test", "isolated", "aws-simple"})
public class ClusterController {

    private final Cluster cluster;
    private final ClusterUserService userService;
    private final ClusterMonitoringService monitoringService;

    @Autowired
    public ClusterController(Cluster cluster, 
                           ClusterUserService userService, 
                           ClusterMonitoringService monitoringService) {
        this.cluster = cluster;
        this.userService = userService;
        this.monitoringService = monitoringService;
    }

    /**
     * Get cluster status and member information.
     */
    @GetMapping("/status")
    public ResponseEntity<ClusterStatus> getClusterStatus() {
        Iterable<Member> membersIterable = cluster.state().getMembers();
        java.util.List<Member> membersList = new java.util.ArrayList<>();
        membersIterable.forEach(membersList::add);
        Member selfMember = cluster.selfMember();
        
        ClusterStatus status = new ClusterStatus(
            selfMember.address().toString(),
            selfMember.status().toString(),
            membersList.size(),
            membersList.stream()
                .map(member -> new MemberInfo(
                    member.address().toString(),
                    member.status().toString(),
                    member.getRoles()
                ))
                .collect(Collectors.toList())
        );
        
        return ResponseEntity.ok(status);
    }

    /**
     * Process a communication event through the cluster.
     */
    @PostMapping("/events")
    public CompletionStage<ResponseEntity<EventResponse>> processEvent(@RequestBody CommunicationEvent event) {
        return userService.processEvent(event)
            .thenApply(success -> {
                if (success) {
                    return ResponseEntity.ok(new EventResponse(true, "Event processed successfully"));
                } else {
                    return ResponseEntity.badRequest()
                        .body(new EventResponse(false, "Failed to process event"));
                }
            })
            .exceptionally(throwable -> {
                System.err.println("Error in processEvent: " + throwable.getMessage());
                throwable.printStackTrace();
                return ResponseEntity.internalServerError()
                    .body(new EventResponse(false, "Error processing event: " + throwable.getMessage()));
            });
    }

    /**
     * Process multiple events through the cluster.
     */
    @PostMapping("/events/batch")
    public CompletionStage<ResponseEntity<EventResponse>> processEvents(@RequestBody List<CommunicationEvent> events) {
        return userService.processEvents(events)
            .thenApply(ignored -> 
                ResponseEntity.ok(new EventResponse(true, 
                    String.format("Processed %d events successfully", events.size())))
            )
            .exceptionally(throwable -> 
                ResponseEntity.badRequest()
                    .body(new EventResponse(false, "Failed to process events: " + throwable.getMessage()))
            );
    }

    /**
     * Get user statistics from the cluster.
     */
    @GetMapping("/users/{userId}/stats")
    public CompletionStage<ResponseEntity<ClusterUserService.UserStatistics>> getUserStats(@PathVariable String userId) {
        return userService.getUserStatistics(userId)
            .thenApply(stats -> {
                if (stats != null) {
                    return ResponseEntity.ok(stats);
                } else {
                    return ResponseEntity.notFound().<ClusterUserService.UserStatistics>build();
                }
            })
            .exceptionally(throwable -> {
                System.err.println("Error getting user stats for " + userId + ": " + throwable.getMessage());
                throwable.printStackTrace();
                return ResponseEntity.internalServerError().build();
            });
    }

    /**
     * Get statistics for multiple users.
     */
    @PostMapping("/users/stats")
    public CompletionStage<ResponseEntity<List<ClusterUserService.UserStatistics>>> getUsersStats(@RequestBody List<String> userIds) {
        return userService.getUserStatistics(userIds)
            .thenApply(ResponseEntity::ok)
            .exceptionally(throwable -> 
                ResponseEntity.badRequest().build()
            );
    }

    /**
     * Get cluster monitoring information.
     */
    @GetMapping("/monitoring")
    public ResponseEntity<ClusterMonitoringInfo> getMonitoringInfo() {
        ClusterMonitoringInfo info = monitoringService.getMonitoringInfo();
        return ResponseEntity.ok(info);
    }

    /**
     * Get cluster health information.
     */
    @GetMapping("/health")
    public ResponseEntity<java.util.Map<String, Object>> getClusterHealth() {
        ClusterHealthCheck healthCheck = new ClusterHealthCheck(cluster);
        return ResponseEntity.ok(healthCheck.getHealth());
    }

    // DTOs
    public static class ClusterStatus {
        public final String selfAddress;
        public final String selfStatus;
        public final int memberCount;
        public final List<MemberInfo> members;

        public ClusterStatus(String selfAddress, String selfStatus, int memberCount, List<MemberInfo> members) {
            this.selfAddress = selfAddress;
            this.selfStatus = selfStatus;
            this.memberCount = memberCount;
            this.members = members;
        }
    }

    public static class MemberInfo {
        public final String address;
        public final String status;
        public final Set<String> roles;

        public MemberInfo(String address, String status, Set<String> roles) {
            this.address = address;
            this.status = status;
            this.roles = roles;
        }
    }

    public static class EventResponse {
        public final boolean success;
        public final String message;

        public EventResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static class ClusterMonitoringInfo {
        public final int activeShards;
        public final int totalEntities;
        public final String clusterHealth;
        public final long uptime;

        public ClusterMonitoringInfo(int activeShards, int totalEntities, String clusterHealth, long uptime) {
            this.activeShards = activeShards;
            this.totalEntities = totalEntities;
            this.clusterHealth = clusterHealth;
            this.uptime = uptime;
        }
    }
}