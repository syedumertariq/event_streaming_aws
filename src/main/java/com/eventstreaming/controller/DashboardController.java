package com.eventstreaming.controller;

import com.eventstreaming.service.ClusterSafeUserActorRegistry;
import com.eventstreaming.service.DashboardService;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the live event streaming dashboard.
 * Now available for all profiles with optional cluster integration.
 */
@Controller
public class DashboardController {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    
    @Autowired
    private DashboardService dashboardService;
    
    @Autowired(required = false)
    private Cluster cluster; // Optional injection for cluster data
    
    @Autowired(required = false) 
    private ClusterSafeUserActorRegistry actorRegistry; // Optional for cluster mode
    
    /**
     * Serve the main dashboard HTML page.
     */
    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> dashboard() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/dashboard.html");
            String content = new String(resource.getInputStream().readAllBytes());
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(content);
        } catch (Exception e) {
            logger.error("Failed to load dashboard template", e);
            return ResponseEntity.internalServerError()
                .body(generateFallbackDashboard(e));
        }
    }
    
    /**
     * Get dashboard data as JSON for AJAX updates.
     * Includes cluster information when available with graceful handling.
     */
    @GetMapping("/dashboard/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDashboardData() {
        try {
            Map<String, Object> data = dashboardService.getDashboardData();
            
            // Add cluster data if available with comprehensive error handling
            Map<String, Object> clusterInfo = getClusterInfo();
            if (clusterInfo != null) {
                data.put("clusterInfo", clusterInfo);
                data.put("isClusterMode", true);
            } else {
                data.put("isClusterMode", false);
                // Add placeholder cluster info for UI consistency
                Map<String, Object> placeholderCluster = new HashMap<>();
                placeholderCluster.put("available", false);
                placeholderCluster.put("message", "Cluster not available in this configuration");
                data.put("clusterInfo", placeholderCluster);
            }
            
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("Failed to get dashboard data", e);
            
            // Provide detailed error information
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get dashboard data: " + e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());
            errorResponse.put("isClusterMode", cluster != null);
            
            // Try to provide minimal cluster info even in error scenarios
            if (cluster != null) {
                try {
                    Map<String, Object> minimalClusterInfo = new HashMap<>();
                    minimalClusterInfo.put("selfAddress", cluster.selfMember().address().toString());
                    minimalClusterInfo.put("available", true);
                    minimalClusterInfo.put("error", "Dashboard data unavailable but cluster is running");
                    errorResponse.put("clusterInfo", minimalClusterInfo);
                } catch (Exception clusterError) {
                    logger.warn("Failed to get minimal cluster info during error: {}", clusterError.getMessage());
                }
            }
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get real-time metrics for live updates.
     * Includes cluster health information when available.
     */
    @GetMapping("/dashboard/metrics")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMetrics() {
        try {
            Map<String, Object> data = dashboardService.getDashboardData();
            
            // Extract just the key metrics for frequent updates
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("overview", data.get("overview"));
            metrics.put("systemHealth", data.get("systemHealth"));
            metrics.put("performance", data.get("performance"));
            metrics.put("lastUpdated", data.get("lastUpdated"));
            
            // Add cluster health information if cluster is available
            if (cluster != null) {
                try {
                    Map<String, Object> clusterHealth = new HashMap<>();
                    clusterHealth.put("status", "UP");
                    clusterHealth.put("clusterSize", getClusterMemberCount());
                    clusterHealth.put("isLeader", cluster.state().getLeader() != null && 
                              cluster.state().getLeader().equals(cluster.selfMember().address()));
                    clusterHealth.put("selfNode", cluster.selfMember().address().toString());
                    clusterHealth.put("available", true);
                    
                    metrics.put("clusterHealth", clusterHealth);
                } catch (Exception clusterError) {
                    logger.warn("Failed to get cluster health for metrics: {}", clusterError.getMessage());
                    // Add error indicator but continue with other metrics
                    Map<String, Object> clusterHealth = new HashMap<>();
                    clusterHealth.put("available", false);
                    clusterHealth.put("error", "Cluster health unavailable: " + clusterError.getMessage());
                    metrics.put("clusterHealth", clusterHealth);
                }
            } else {
                // Indicate cluster is not configured
                Map<String, Object> clusterHealth = new HashMap<>();
                clusterHealth.put("available", false);
                clusterHealth.put("message", "Cluster not configured");
                metrics.put("clusterHealth", clusterHealth);
            }
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Failed to get metrics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Helper method to count cluster members safely.
     */
    private int getClusterMemberCount() {
        if (cluster == null) return 0;
        
        int count = 0;
        for (@SuppressWarnings("unused") Member member : cluster.state().getMembers()) {
            count++;
        }
        return count;
    }
    
    /**
     * Get detailed performance metrics.
     */
    @GetMapping("/dashboard/performance")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        try {
            Map<String, Object> performance = dashboardService.getPerformanceMetrics();
            return ResponseEntity.ok(performance);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get performance metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Get real-time performance metrics for WebSocket updates.
     */
    @GetMapping("/dashboard/performance/realtime")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRealTimePerformanceMetrics() {
        try {
            Map<String, Object> realTimeMetrics = dashboardService.getRealTimePerformanceMetrics();
            return ResponseEntity.ok(realTimeMetrics);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get real-time performance metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Clear performance metrics cache.
     */
    @GetMapping("/dashboard/performance/cache/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearPerformanceCache() {
        try {
            dashboardService.clearPerformanceCache();
            return ResponseEntity.ok(Map.of("message", "Performance cache cleared successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to clear performance cache: " + e.getMessage()));
        }
    }
    
    /**
     * Get pipeline visualization status.
     */
    @GetMapping("/dashboard/pipeline/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPipelineStatus() {
        try {
            Map<String, Object> pipelineStatus = dashboardService.getPipelineStatus();
            return ResponseEntity.ok(pipelineStatus);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get pipeline status: " + e.getMessage()));
        }
    }
    
    /**
     * Get pipeline flow metrics.
     */
    @GetMapping("/dashboard/pipeline/flow")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPipelineFlowMetrics() {
        try {
            Map<String, Object> flowMetrics = dashboardService.getPipelineFlowMetrics();
            return ResponseEntity.ok(flowMetrics);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get pipeline flow metrics: " + e.getMessage()));
        }
    }
    
    /**
     * Get recent pipeline errors.
     */
    @GetMapping("/dashboard/pipeline/errors")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPipelineErrors() {
        try {
            Map<String, Object> errors = dashboardService.getPipelineErrors();
            return ResponseEntity.ok(errors);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get pipeline errors: " + e.getMessage()));
        }
    }
    
    /**
     * Get comprehensive cluster information when cluster is available.
     * Includes member details, actor registry stats, and cluster health.
     */
    private Map<String, Object> getClusterInfo() {
        try {
            if (cluster == null) return null;
            
            Map<String, Object> clusterInfo = new HashMap<>();
            
            // Basic cluster information
            clusterInfo.put("selfAddress", cluster.selfMember().address().toString());
            clusterInfo.put("isLeader", cluster.state().getLeader() != null && 
                           cluster.state().getLeader().equals(cluster.selfMember().address()));
            
            // Collect member information
            List<Map<String, Object>> members = new ArrayList<>();
            int memberCount = 0;
            
            for (Member member : cluster.state().getMembers()) {
                memberCount++;
                
                // Create detailed member info
                Map<String, Object> memberInfo = new HashMap<>();
                memberInfo.put("address", member.address().toString());
                memberInfo.put("roles", member.getRoles());
                memberInfo.put("status", member.status().toString());
                memberInfo.put("isLeader", cluster.state().getLeader() != null && 
                              cluster.state().getLeader().equals(member.address()));
                members.add(memberInfo);
            }
            
            clusterInfo.put("memberCount", memberCount);
            clusterInfo.put("members", members);
            clusterInfo.put("clusterStatus", "UP");
            
            // Add actor registry information if available
            if (actorRegistry != null) {
                try {
                    ClusterSafeUserActorRegistry.RegistryStats registryStats = actorRegistry.getStats();
                    Map<String, Object> actorStats = new HashMap<>();
                    actorStats.put("actorType", registryStats.actorType());
                    actorStats.put("passivationConfig", registryStats.passivationConfig());
                    actorStats.put("snapshotConfig", registryStats.snapshotConfig());
                    clusterInfo.put("actorRegistry", actorStats);
                } catch (Exception e) {
                    logger.warn("Failed to get actor registry stats: {}", e.getMessage());
                    // Continue without actor registry stats
                }
            }
            
            // Add cluster health information
            Map<String, Object> clusterHealth = new HashMap<>();
            clusterHealth.put("status", "UP");
            clusterHealth.put("clusterSize", memberCount);
            clusterHealth.put("selfNode", cluster.selfMember().address().toString());
            clusterInfo.put("health", clusterHealth);
            
            return clusterInfo;
        } catch (Exception e) {
            logger.warn("Failed to get cluster info: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate comprehensive fallback dashboard when template loading fails.
     * Provides detailed error information, retry functionality, and alternative dashboard links.
     * Requirements: 2.4
     */
    private String generateFallbackDashboard(Exception error) {
        String timestamp = java.time.LocalDateTime.now().toString();
        String clusterStatus = getClusterStatusForFallback();
        
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Dashboard - Template Loading Error</title>
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
                        margin: 0; 
                        padding: 40px; 
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .fallback-container { 
                        background: white; 
                        padding: 40px; 
                        border-radius: 12px; 
                        box-shadow: 0 8px 32px rgba(0,0,0,0.1);
                        max-width: 600px;
                        width: 100%%;
                    }
                    .error-header {
                        text-align: center;
                        margin-bottom: 30px;
                    }
                    .error-icon {
                        font-size: 4rem;
                        margin-bottom: 15px;
                    }
                    .error-title { 
                        color: #dc3545; 
                        font-size: 1.8rem; 
                        margin-bottom: 10px;
                        font-weight: 600;
                    }
                    .error-subtitle {
                        color: #6c757d;
                        font-size: 1rem;
                        margin-bottom: 25px;
                    }
                    .error-details {
                        background: #f8f9fa;
                        border-left: 4px solid #dc3545;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 4px;
                    }
                    .error-message { 
                        color: #495057; 
                        margin-bottom: 10px;
                        font-family: 'Courier New', monospace;
                        font-size: 0.9rem;
                        word-break: break-word;
                    }
                    .system-info {
                        background: #e3f2fd;
                        border-left: 4px solid #2196f3;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 4px;
                    }
                    .info-row {
                        display: flex;
                        justify-content: space-between;
                        margin-bottom: 8px;
                        font-size: 0.9rem;
                    }
                    .info-label {
                        font-weight: 600;
                        color: #1976d2;
                    }
                    .info-value {
                        color: #424242;
                        font-family: 'Courier New', monospace;
                    }
                    .action-buttons {
                        display: flex;
                        gap: 15px;
                        margin: 30px 0 20px 0;
                        flex-wrap: wrap;
                    }
                    .btn {
                        padding: 12px 24px;
                        border: none;
                        border-radius: 6px;
                        cursor: pointer;
                        font-size: 0.95rem;
                        font-weight: 500;
                        text-decoration: none;
                        display: inline-flex;
                        align-items: center;
                        gap: 8px;
                        transition: all 0.2s ease;
                    }
                    .btn-primary {
                        background: #007bff;
                        color: white;
                    }
                    .btn-primary:hover {
                        background: #0056b3;
                        transform: translateY(-1px);
                    }
                    .btn-secondary {
                        background: #6c757d;
                        color: white;
                    }
                    .btn-secondary:hover {
                        background: #545b62;
                        transform: translateY(-1px);
                    }
                    .btn-outline {
                        background: transparent;
                        color: #007bff;
                        border: 2px solid #007bff;
                    }
                    .btn-outline:hover {
                        background: #007bff;
                        color: white;
                    }
                    .alternative-links {
                        border-top: 1px solid #dee2e6;
                        padding-top: 20px;
                        margin-top: 20px;
                    }
                    .alternative-title {
                        font-weight: 600;
                        color: #495057;
                        margin-bottom: 15px;
                    }
                    .link-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 15px;
                    }
                    .dashboard-link {
                        display: block;
                        padding: 15px;
                        background: #f8f9fa;
                        border: 1px solid #dee2e6;
                        border-radius: 6px;
                        text-decoration: none;
                        color: #495057;
                        transition: all 0.2s ease;
                    }
                    .dashboard-link:hover {
                        background: #e9ecef;
                        border-color: #007bff;
                        color: #007bff;
                        transform: translateY(-2px);
                    }
                    .link-icon {
                        font-size: 1.2rem;
                        margin-bottom: 5px;
                    }
                    .link-title {
                        font-weight: 600;
                        margin-bottom: 5px;
                    }
                    .link-description {
                        font-size: 0.85rem;
                        color: #6c757d;
                    }
                    .troubleshooting {
                        background: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 4px;
                    }
                    .troubleshooting-title {
                        font-weight: 600;
                        color: #856404;
                        margin-bottom: 10px;
                    }
                    .troubleshooting-steps {
                        color: #856404;
                        font-size: 0.9rem;
                        line-height: 1.5;
                    }
                    .auto-retry {
                        text-align: center;
                        margin-top: 20px;
                        padding: 15px;
                        background: #d1ecf1;
                        border-radius: 6px;
                        color: #0c5460;
                    }
                    @media (max-width: 768px) {
                        body { padding: 20px; }
                        .fallback-container { padding: 25px; }
                        .action-buttons { flex-direction: column; }
                        .btn { justify-content: center; }
                    }
                </style>
                <script>
                    let retryCount = 0;
                    const maxRetries = 3;
                    
                    function retryDashboard() {
                        retryCount++;
                        const retryBtn = document.getElementById('retryBtn');
                        retryBtn.disabled = true;
                        retryBtn.innerHTML = '🔄 Retrying...';
                        
                        setTimeout(() => {
                            location.reload();
                        }, 1000);
                    }
                    
                    function startAutoRetry() {
                        if (retryCount < maxRetries) {
                            const autoRetryDiv = document.getElementById('autoRetry');
                            let countdown = 10;
                            
                            const countdownInterval = setInterval(() => {
                                autoRetryDiv.innerHTML = `🔄 Auto-retry in ${countdown} seconds... <button onclick="clearInterval(${countdownInterval}); this.parentElement.style.display='none';" style="margin-left: 10px; padding: 5px 10px; border: none; background: #007bff; color: white; border-radius: 3px; cursor: pointer;">Cancel</button>`;
                                countdown--;
                                
                                if (countdown < 0) {
                                    clearInterval(countdownInterval);
                                    retryDashboard();
                                }
                            }, 1000);
                        }
                    }
                    
                    function checkDashboardHealth() {
                        fetch('/dashboard/data')
                            .then(response => {
                                if (response.ok) {
                                    document.getElementById('healthStatus').innerHTML = '✅ Dashboard API is responding';
                                    document.getElementById('healthStatus').style.color = '#28a745';
                                } else {
                                    document.getElementById('healthStatus').innerHTML = '❌ Dashboard API error: ' + response.status;
                                    document.getElementById('healthStatus').style.color = '#dc3545';
                                }
                            })
                            .catch(error => {
                                document.getElementById('healthStatus').innerHTML = '❌ Dashboard API unreachable';
                                document.getElementById('healthStatus').style.color = '#dc3545';
                            });
                    }
                    
                    // Start health check and auto-retry on page load
                    window.onload = function() {
                        checkDashboardHealth();
                        if (retryCount === 0) {
                            setTimeout(startAutoRetry, 2000);
                        }
                    };
                </script>
            </head>
            <body>
                <div class="fallback-container">
                    <div class="error-header">
                        <div class="error-icon">⚠️</div>
                        <div class="error-title">Dashboard Template Loading Error</div>
                        <div class="error-subtitle">The main dashboard template could not be loaded</div>
                    </div>
                    
                    <div class="error-details">
                        <div class="error-message"><strong>Error:</strong> %s</div>
                        <div class="error-message"><strong>Type:</strong> %s</div>
                        <div class="error-message"><strong>Time:</strong> %s</div>
                    </div>
                    
                    <div class="system-info">
                        <div class="info-row">
                            <span class="info-label">Cluster Status:</span>
                            <span class="info-value">%s</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Dashboard API:</span>
                            <span class="info-value" id="healthStatus">🔍 Checking...</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Template Path:</span>
                            <span class="info-value">templates/dashboard.html</span>
                        </div>
                    </div>
                    
                    <div class="action-buttons">
                        <button id="retryBtn" class="btn btn-primary" onclick="retryDashboard()">
                            🔄 Retry Loading
                        </button>
                        <button class="btn btn-secondary" onclick="checkDashboardHealth()">
                            🔍 Check API Health
                        </button>
                        <a href="/dashboard/data" class="btn btn-outline" target="_blank">
                            📊 View Raw Data
                        </a>
                    </div>
                    
                    <div class="auto-retry" id="autoRetry" style="display: none;"></div>
                    
                    <div class="troubleshooting">
                        <div class="troubleshooting-title">💡 Troubleshooting Steps</div>
                        <div class="troubleshooting-steps">
                            1. Check if the dashboard template file exists at <code>src/main/resources/templates/dashboard.html</code><br>
                            2. Verify the application has proper read permissions for template files<br>
                            3. Check application logs for detailed error information<br>
                            4. Try accessing alternative dashboard endpoints below
                        </div>
                    </div>
                    
                    <div class="alternative-links">
                        <div class="alternative-title">🔗 Alternative Dashboard Options</div>
                        <div class="link-grid">
                            <a href="/cluster/dashboard" class="dashboard-link">
                                <div class="link-icon">🌐</div>
                                <div class="link-title">Cluster Dashboard</div>
                                <div class="link-description">Monitor cluster health and member status</div>
                            </a>
                            <a href="/dashboard/data" class="dashboard-link" target="_blank">
                                <div class="link-icon">📊</div>
                                <div class="link-title">Raw Dashboard Data</div>
                                <div class="link-description">View dashboard data in JSON format</div>
                            </a>
                            <a href="/dashboard/metrics" class="dashboard-link" target="_blank">
                                <div class="link-icon">📈</div>
                                <div class="link-title">System Metrics</div>
                                <div class="link-description">Real-time system performance metrics</div>
                            </a>
                            <a href="/dashboard/performance" class="dashboard-link" target="_blank">
                                <div class="link-icon">⚡</div>
                                <div class="link-title">Performance Dashboard</div>
                                <div class="link-description">Detailed performance analytics</div>
                            </a>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                escapeHtml(error.getMessage()),
                error.getClass().getSimpleName(),
                timestamp,
                clusterStatus
            );
    }
    
    /**
     * Get cluster status information for fallback dashboard display.
     */
    private String getClusterStatusForFallback() {
        try {
            if (cluster == null) {
                return "Not configured (Single node mode)";
            }
            
            int memberCount = getClusterMemberCount();
            boolean isLeader = cluster.state().getLeader() != null && 
                             cluster.state().getLeader().equals(cluster.selfMember().address());
            String selfAddress = cluster.selfMember().address().toString();
            
            return String.format("Active (%d nodes, %s, %s)", 
                memberCount, 
                isLeader ? "Leader" : "Member",
                selfAddress);
        } catch (Exception e) {
            return "Error retrieving cluster status: " + e.getMessage();
        }
    }
    
    /**
     * Escape HTML characters to prevent XSS in error messages.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
}