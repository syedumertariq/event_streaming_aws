package com.eventstreaming.controller;

import com.eventstreaming.service.ClusterSafeUserActorRegistry;
import com.eventstreaming.service.DashboardService;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Enhanced dashboard controller for cluster-mysql profile.
 * Provides comprehensive event analytics and cluster monitoring.
 */
@Controller
@Profile({"cluster-mysql"})
public class ClusterDashboardController {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterDashboardController.class);
    
    @Autowired
    private ClusterSafeUserActorRegistry actorRegistry;
    
    @Autowired
    private Cluster cluster;
    
    @Autowired
    private DashboardService dashboardService;
    
    /**
     * Serve the cluster dashboard HTML page.
     */
    @GetMapping(value = "/cluster/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> dashboard() {
        try {
            String dashboardHtml = generateClusterDashboardHtml();
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(dashboardHtml);
        } catch (Exception e) {
            logger.error("Error serving cluster dashboard", e);
            return ResponseEntity.internalServerError()
                .body("<html><body><h1>Cluster Dashboard Error</h1><p>Failed to load dashboard: " + e.getMessage() + "</p></body></html>");
        }
    }
    
    /**
     * Get comprehensive cluster dashboard data including event analytics and cluster info.
     */
    @GetMapping("/cluster/dashboard/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClusterDashboardData() {
        try {
            // Get comprehensive dashboard data from DashboardService
            Map<String, Object> data = dashboardService.getDashboardData();
            
            // Add cluster-specific information
            Map<String, Object> clusterOverview = new HashMap<>();
            clusterOverview.put("selfAddress", cluster.selfMember().address().toString());
            clusterOverview.put("isLeader", cluster.state().getLeader() != null && 
                               cluster.state().getLeader().equals(cluster.selfMember().address()));
            
            // Count members
            List<Member> memberList = new ArrayList<>();
            for (Member member : cluster.state().getMembers()) {
                memberList.add(member);
            }
            clusterOverview.put("memberCount", memberList.size());
            
            // Member details
            List<Map<String, Object>> members = new ArrayList<>();
            for (Member member : memberList) {
                Map<String, Object> memberInfo = new HashMap<>();
                memberInfo.put("address", member.address().toString());
                memberInfo.put("roles", member.getRoles());
                memberInfo.put("status", member.status().toString());
                memberInfo.put("isLeader", cluster.state().getLeader() != null && 
                              cluster.state().getLeader().equals(member.address()));
                members.add(memberInfo);
            }
            clusterOverview.put("members", members);
            
            // Add cluster info to existing dashboard data
            data.put("clusterOverview", clusterOverview);
            
            // Actor registry stats
            ClusterSafeUserActorRegistry.RegistryStats registryStats = actorRegistry.getStats();
            Map<String, Object> actorStats = new HashMap<>();
            actorStats.put("actorType", registryStats.actorType());
            actorStats.put("passivationConfig", registryStats.passivationConfig());
            actorStats.put("snapshotConfig", registryStats.snapshotConfig());
            data.put("actorRegistry", actorStats);
            
            // Update system info to reflect cluster-mysql profile
            Map<String, Object> systemInfo = new HashMap<>();
            systemInfo.put("profile", "cluster-mysql");
            systemInfo.put("database", "MySQL");
            systemInfo.put("persistence", "Pekko JDBC");
            systemInfo.put("clusterMode", true);
            systemInfo.put("timestamp", LocalDateTime.now());
            data.put("systemInfo", systemInfo);
            
            return ResponseEntity.ok(data);
            
        } catch (Exception e) {
            logger.error("Error getting cluster dashboard data", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get dashboard data: " + e.getMessage()));
        }
    }
    
    /**
     * Get cluster metrics for live updates.
     */
    @GetMapping("/cluster/dashboard/metrics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClusterMetrics() {
        try {
            Map<String, Object> data = dashboardService.getDashboardData();
            
            // Extract just the key metrics for frequent updates
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("overview", data.get("overview"));
            metrics.put("systemHealth", data.get("systemHealth"));
            metrics.put("performance", data.get("performance"));
            metrics.put("lastUpdated", data.get("lastUpdated"));
            
            // Add cluster-specific health info
            Map<String, Object> clusterHealth = new HashMap<>();
            clusterHealth.put("status", "UP");
            clusterHealth.put("clusterSize", getClusterMemberCount());
            clusterHealth.put("isLeader", cluster.state().getLeader() != null && 
                      cluster.state().getLeader().equals(cluster.selfMember().address()));
            clusterHealth.put("selfNode", cluster.selfMember().address().toString());
            
            metrics.put("clusterHealth", clusterHealth);
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            logger.error("Error getting cluster metrics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Get detailed cluster performance metrics.
     */
    @GetMapping("/cluster/dashboard/performance")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClusterPerformanceMetrics() {
        try {
            Map<String, Object> performance = dashboardService.getPerformanceMetrics();
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get performance metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Get real-time cluster performance metrics for WebSocket updates.
     */
    @GetMapping("/cluster/dashboard/performance/realtime")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClusterRealTimePerformanceMetrics() {
        try {
            Map<String, Object> realTimeMetrics = dashboardService.getRealTimePerformanceMetrics();
            return ResponseEntity.ok(realTimeMetrics);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get real-time performance metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Clear cluster performance metrics cache.
     */
    @GetMapping("/cluster/dashboard/performance/cache/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearClusterPerformanceCache() {
        try {
            dashboardService.clearPerformanceCache();
            return ResponseEntity.ok(Map.of("message", "Performance cache cleared successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to clear performance cache: " + e.getMessage()));
        }
    }
    
    /**
     * Get cluster pipeline visualization status.
     */
    @GetMapping("/cluster/dashboard/pipeline/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClusterPipelineStatus() {
        try {
            Map<String, Object> pipelineStatus = dashboardService.getPipelineStatus();
            return ResponseEntity.ok(pipelineStatus);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get pipeline status: " + e.getMessage()));
        }
    }
    
    /**
     * Get cluster pipeline flow metrics.
     */
    @GetMapping("/cluster/dashboard/pipeline/flow")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClusterPipelineFlowMetrics() {
        try {
            Map<String, Object> flowMetrics = dashboardService.getPipelineFlowMetrics();
            return ResponseEntity.ok(flowMetrics);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get pipeline flow metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Get recent cluster pipeline errors.
     */
    @GetMapping("/cluster/dashboard/pipeline/errors")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClusterPipelineErrors() {
        try {
            Map<String, Object> errors = dashboardService.getPipelineErrors();
            return ResponseEntity.ok(errors);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get pipeline errors: " + e.getMessage()));
        }
    }
    
    private int getClusterMemberCount() {
        int count = 0;
        for (Member member : cluster.state().getMembers()) {
            count++;
        }
        return count;
    }
    
    private String generateClusterDashboardHtml() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Cluster MySQL Dashboard</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
                    .card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; }
                    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
                    .metric { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #eee; }
                    .metric:last-child { border-bottom: none; }
                    .metric-label { font-weight: bold; color: #555; }
                    .metric-value { color: #333; }
                    .status-up { color: #28a745; font-weight: bold; }
                    .status-leader { color: #007bff; font-weight: bold; }
                    .member-list { list-style: none; padding: 0; }
                    .member-item { padding: 10px; margin: 5px 0; background: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff; }
                    .refresh-btn { background: #007bff; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer; }
                    .refresh-btn:hover { background: #0056b3; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🚀 Cluster MySQL Dashboard</h1>
                        <p>Real-time monitoring for Pekko cluster with MySQL persistence</p>
                        <div style="margin-top: 15px;">
                            <button class="refresh-btn" onclick="refreshDashboard()">🔄 Refresh</button>
                            <a href="/dashboard" class="refresh-btn" style="text-decoration: none; margin-left: 10px;">📊 Main Dashboard</a>
                        </div>
                    </div>
                    
                    <div class="grid">
                        <div class="card">
                            <h3>📊 Cluster Overview</h3>
                            <div id="cluster-overview">Loading...</div>
                        </div>
                        
                        <div class="card">
                            <h3>🎭 Actor Registry</h3>
                            <div id="actor-registry">Loading...</div>
                        </div>
                        
                        <div class="card">
                            <h3>💾 System Information</h3>
                            <div id="system-info">Loading...</div>
                        </div>
                        
                        <div class="card">
                            <h3>⚡ Performance Metrics</h3>
                            <div id="performance-metrics">Loading...</div>
                        </div>
                    </div>
                    
                    <div class="card">
                        <h3>🌐 Cluster Members</h3>
                        <div id="cluster-members">Loading...</div>
                    </div>
                </div>
                
                <script>
                    function refreshDashboard() {
                        fetch('/cluster/dashboard/data')
                            .then(response => response.json())
                            .then(data => {
                                updateClusterOverview(data.clusterOverview);
                                updateActorRegistry(data.actorRegistry);
                                updateSystemInfo(data.systemInfo);
                                updateClusterMembers(data.clusterOverview.members);
                            })
                            .catch(error => {
                                console.error('Error fetching dashboard data:', error);
                            });
                            
                        fetch('/cluster/dashboard/metrics')
                            .then(response => response.json())
                            .then(data => {
                                updatePerformanceMetrics(data.performance);
                            })
                            .catch(error => {
                                console.error('Error fetching metrics:', error);
                            });
                    }
                    
                    function updateClusterOverview(data) {
                        const html = `
                            <div class="metric">
                                <span class="metric-label">Self Address:</span>
                                <span class="metric-value">${data.selfAddress}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Leader Status:</span>
                                <span class="metric-value ${data.isLeader ? 'status-leader' : ''}">${data.isLeader ? '👑 Leader' : '👥 Member'}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Cluster Size:</span>
                                <span class="metric-value status-up">${data.memberCount} nodes</span>
                            </div>
                        `;
                        document.getElementById('cluster-overview').innerHTML = html;
                    }
                    
                    function updateActorRegistry(data) {
                        const html = `
                            <div class="metric">
                                <span class="metric-label">Actor Type:</span>
                                <span class="metric-value">${data.actorType}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Passivation:</span>
                                <span class="metric-value">${data.passivationConfig}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Snapshots:</span>
                                <span class="metric-value">${data.snapshotConfig}</span>
                            </div>
                        `;
                        document.getElementById('actor-registry').innerHTML = html;
                    }
                    
                    function updateSystemInfo(data) {
                        const html = `
                            <div class="metric">
                                <span class="metric-label">Profile:</span>
                                <span class="metric-value">${data.profile}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Database:</span>
                                <span class="metric-value">${data.database}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Persistence:</span>
                                <span class="metric-value">${data.persistence}</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Last Updated:</span>
                                <span class="metric-value">${new Date(data.timestamp).toLocaleString()}</span>
                            </div>
                        `;
                        document.getElementById('system-info').innerHTML = html;
                    }
                    
                    function updatePerformanceMetrics(data) {
                        // Performance data is nested under 'resources' and already in MB
                        const resources = data && data.resources ? data.resources : {};
                        
                        const memoryUsedMB = resources.memoryUsedMB || 0;
                        const memoryTotalMB = resources.memoryTotalMB || 0;
                        const memoryPercent = resources.memoryUsagePercent || 0;
                        const cpuUsage = resources.cpuUsage || 0;
                        
                        const html = `
                            <div class="metric">
                                <span class="metric-label">Memory Used:</span>
                                <span class="metric-value">${memoryUsedMB} MB (${Math.round(memoryPercent)}%)</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">Memory Total:</span>
                                <span class="metric-value">${memoryTotalMB} MB</span>
                            </div>
                            <div class="metric">
                                <span class="metric-label">CPU Usage:</span>
                                <span class="metric-value">${Math.round(cpuUsage)}%</span>
                            </div>
                        `;
                        document.getElementById('performance-metrics').innerHTML = html;
                    }
                    
                    function updateClusterMembers(members) {
                        const html = members.map(member => `
                            <div class="member-item">
                                <strong>${member.address}</strong>
                                <br>
                                Status: <span class="status-up">${member.status}</span>
                                ${member.isLeader ? ' <span class="status-leader">👑 Leader</span>' : ''}
                                <br>
                                Roles: ${Array.from(member.roles).join(', ')}
                            </div>
                        `).join('');
                        document.getElementById('cluster-members').innerHTML = html;
                    }
                    
                    // Auto-refresh every 5 seconds
                    setInterval(refreshDashboard, 5000);
                    
                    // Initial load
                    refreshDashboard();
                </script>
            </body>
            </html>
            """;
    }
}