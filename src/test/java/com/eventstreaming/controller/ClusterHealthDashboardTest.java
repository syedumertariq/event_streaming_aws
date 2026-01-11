package com.eventstreaming.controller;

import com.eventstreaming.service.ClusterSafeUserActorRegistry;
import com.eventstreaming.service.DashboardService;
import org.apache.pekko.actor.Address;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cluster health dashboard functionality.
 * Validates Requirements 4.1, 4.2, 4.3, 4.4
 */
@WebMvcTest(ClusterDashboardController.class)
@ActiveProfiles("cluster-mysql")
public class ClusterHealthDashboardTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClusterSafeUserActorRegistry actorRegistry;

    @MockBean
    private Cluster cluster;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private Member mockSelfMember;

    @MockBean
    private org.apache.pekko.cluster.ClusterEvent.CurrentClusterState mockClusterState;

    @BeforeEach
    void setUp() {
        // Setup cluster mocks with simplified approach
        Address selfAddress = Address.apply("pekko", "EventStreamingSystem", "127.0.0.1", 2551);
        
        when(mockSelfMember.address()).thenReturn(selfAddress);
        when(cluster.selfMember()).thenReturn(mockSelfMember);
        when(cluster.state()).thenReturn(mockClusterState);
        when(mockClusterState.getLeader()).thenReturn(selfAddress);

        // Setup actor registry mocks
        ClusterSafeUserActorRegistry.RegistryStats registryStats = 
            new ClusterSafeUserActorRegistry.RegistryStats(
                "UserActor",
                "timeout-based",
                "enabled"
            );
        when(actorRegistry.getStats()).thenReturn(registryStats);

        // Setup dashboard service mocks
        Map<String, Object> mockDashboardData = new HashMap<>();
        mockDashboardData.put("overview", Map.of("totalEvents", 100));
        mockDashboardData.put("performance", Map.of(
            "memoryUsed", 512000000L,
            "memoryTotal", 1024000000L,
            "memoryMax", 2048000000L
        ));
        when(dashboardService.getDashboardData()).thenReturn(mockDashboardData);
    }

    /**
     * Test cluster dashboard HTML contains required health elements.
     * Validates Requirements 4.1, 4.3
     */
    @Test
    public void testClusterDashboardHtmlContainsHealthElements() throws Exception {
        mockMvc.perform(get("/cluster/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cluster Overview")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Actor Registry")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cluster Members")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Leader Status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Performance Metrics")));
    }

    /**
     * Test cluster dashboard data endpoint returns cluster information.
     * Validates Requirements 4.1, 4.2, 4.4
     */
    @Test
    public void testClusterDashboardDataEndpoint() throws Exception {
        mockMvc.perform(get("/cluster/dashboard/data"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.clusterOverview").exists())
                .andExpect(jsonPath("$.actorRegistry").exists())
                .andExpect(jsonPath("$.systemInfo").exists())
                .andExpect(jsonPath("$.systemInfo.profile").value("cluster-mysql"))
                .andExpect(jsonPath("$.systemInfo.clusterMode").value(true));
    }

    /**
     * Test cluster metrics endpoint provides health information.
     * Validates Requirements 4.1, 4.2
     */
    @Test
    public void testClusterMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/cluster/dashboard/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterHealth").exists())
                .andExpect(jsonPath("$.clusterHealth.status").value("UP"));
    }

    /**
     * Test actor registry information is displayed.
     * Validates Requirements 4.4
     */
    @Test
    public void testActorRegistryInformation() throws Exception {
        mockMvc.perform(get("/cluster/dashboard/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actorRegistry.actorType").value("UserActor"))
                .andExpect(jsonPath("$.actorRegistry.passivationConfig").value("timeout-based"))
                .andExpect(jsonPath("$.actorRegistry.snapshotConfig").value("enabled"));
    }

    /**
     * Test error handling when cluster service fails.
     * Validates Requirements 4.3
     */
    @Test
    public void testErrorHandlingWhenClusterFails() throws Exception {
        when(cluster.selfMember()).thenThrow(new RuntimeException("Cluster not available"));

        mockMvc.perform(get("/cluster/dashboard/data"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Failed to get dashboard data")));
    }

    /**
     * Test cluster dashboard HTML error handling.
     * Validates Requirements 4.3
     */
    @Test
    public void testClusterDashboardHtmlErrorHandling() throws Exception {
        when(dashboardService.getDashboardData()).thenThrow(new RuntimeException("Service unavailable"));

        mockMvc.perform(get("/cluster/dashboard"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cluster Dashboard Error")));
    }

    /**
     * Test performance metrics endpoint.
     * Validates Requirements 4.2
     */
    @Test
    public void testPerformanceMetricsEndpoint() throws Exception {
        Map<String, Object> performanceData = Map.of(
            "memoryUsed", 512000000L,
            "memoryTotal", 1024000000L,
            "cpuUsage", 45.5
        );
        when(dashboardService.getPerformanceMetrics()).thenReturn(performanceData);

        mockMvc.perform(get("/cluster/dashboard/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memoryUsed").value(512000000L))
                .andExpect(jsonPath("$.memoryTotal").value(1024000000L))
                .andExpect(jsonPath("$.cpuUsage").value(45.5));
    }

    /**
     * Test pipeline status endpoint for cluster monitoring.
     * Validates Requirements 4.2
     */
    @Test
    public void testPipelineStatusEndpoint() throws Exception {
        Map<String, Object> pipelineData = Map.of(
            "status", "RUNNING",
            "throughput", 1000,
            "errors", 0
        );
        when(dashboardService.getPipelineStatus()).thenReturn(pipelineData);

        mockMvc.perform(get("/cluster/dashboard/pipeline/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.throughput").value(1000))
                .andExpect(jsonPath("$.errors").value(0));
    }
}