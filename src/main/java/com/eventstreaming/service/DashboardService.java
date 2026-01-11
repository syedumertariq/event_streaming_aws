package com.eventstreaming.service;

import com.eventstreaming.monitoring.*;
import com.eventstreaming.persistence.H2EventJournal;
import com.eventstreaming.model.UserEventAggregation;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for providing dashboard data and real-time event stream analytics.
 */
@Service
public class DashboardService {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);
    
    @Autowired(required = false)
    private H2EventJournal h2EventJournal;
    
    @Autowired
    private EventJournalService eventJournalService;
    
    @Autowired
    private UserEventAggregationService userEventAggregationService;
    
    @Autowired(required = false)
    private Cluster cluster;
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Value("${dashboard.aggregation.mode:local}")
    private String aggregationMode; // "local" for single-node, "cluster" for multi-node
    
    /**
     * Check if H2 event journal is available.
     */
    private boolean isH2Available() {
        return h2EventJournal != null;
    }
    
    @Autowired
    private PerformanceMetricsService performanceMetricsService;
    
    @Autowired
    private PipelineVisualizationService pipelineVisualizationService;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Cache for expensive performance calculations
    private final ConcurrentHashMap<String, Object> performanceCache = new ConcurrentHashMap<>();
    private LocalDateTime lastCacheUpdate = LocalDateTime.now();
    
    /**
     * Get comprehensive dashboard data including all metrics and charts.
     */
    public Map<String, Object> getDashboardData() {
        try {
            logger.info("Generating dashboard data");
            
            Map<String, Object> dashboard = new HashMap<>();
            
            // Overall statistics
            dashboard.put("overview", getOverviewStats());
            
            // Event type breakdown
            dashboard.put("eventTypes", getEventTypeBreakdown());
            
            // User activity
            dashboard.put("userActivity", getUserActivityStats());
            
            // Recent events
            dashboard.put("recentEvents", getRecentEvents(20));
            
            // Time-based analytics
            dashboard.put("timeAnalytics", getTimeBasedAnalytics());
            
            // System health
            dashboard.put("systemHealth", getSystemHealth());
            
            // Performance metrics
            dashboard.put("performance", getPerformanceMetrics());
            
            // Top users
            dashboard.put("topUsers", getTopUsers(10));
            
            dashboard.put("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            logger.info("Dashboard data generated successfully");
            return dashboard;
            
        } catch (Exception e) {
            logger.error("Failed to generate dashboard data", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate dashboard data: " + e.getMessage());
            error.put("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return error;
        }
    }
    
    /**
     * Get overview statistics with intelligent routing based on deployment type.
     */
    private Map<String, Object> getOverviewStats() {
        try {
            // For single-node deployments or when cluster is not available, use direct service calls
            if (!isMultiNodeCluster()) {
                logger.debug("Using local aggregation service for overview stats (single-node)");
                return getOverviewStatsLocal();
            }
            
            // For multi-node clusters, try HTTP aggregation first, fallback to local
            try {
                logger.debug("Using HTTP aggregation for overview stats (multi-node cluster)");
                return getOverviewStatsViaHttp();
            } catch (Exception e) {
                logger.warn("HTTP aggregation failed, falling back to local: {}", e.getMessage());
                return getOverviewStatsLocal();
            }
            
        } catch (Exception e) {
            logger.error("Failed to get overview stats", e);
            return Map.of("error", "Failed to get overview stats", "source", "error");
        }
    }
    
    /**
     * Get overview stats directly from local aggregation service.
     */
    private Map<String, Object> getOverviewStatsLocal() {
        long totalUsers = userEventAggregationService.getTotalUsers();
        long totalEvents = userEventAggregationService.getTotalEventsAcrossAllUsers();
        
        Map<String, Object> overview = new HashMap<>();
        overview.put("totalUsers", totalUsers);
        overview.put("totalEvents", totalEvents);
        overview.put("averageEventsPerUser", totalUsers > 0 ? (double) totalEvents / totalUsers : 0.0);
        overview.put("source", "local-service");
        
        logger.debug("Overview stats from local service: {} users, {} events", totalUsers, totalEvents);
        return overview;
    }
    
    /**
     * Get overview stats via HTTP (for multi-node cluster aggregation).
     */
    private Map<String, Object> getOverviewStatsViaHttp() {
        String aggregationUrl = "http://localhost:" + serverPort + "/api/aggregations/summary";
        Map<String, Object> aggregationResponse = restTemplate.getForObject(aggregationUrl, Map.class);
        
        if (aggregationResponse != null && aggregationResponse.containsKey("totalEvents")) {
            long totalUsers = aggregationResponse.get("totalUsers") instanceof Number ? 
                ((Number) aggregationResponse.get("totalUsers")).longValue() : 0;
            long totalEvents = aggregationResponse.get("totalEvents") instanceof Number ? 
                ((Number) aggregationResponse.get("totalEvents")).longValue() : 0;
            
            Map<String, Object> overview = new HashMap<>();
            overview.put("totalUsers", totalUsers);
            overview.put("totalEvents", totalEvents);
            overview.put("averageEventsPerUser", totalUsers > 0 ? (double) totalEvents / totalUsers : 0.0);
            overview.put("source", "http-aggregation");
            
            logger.debug("Overview stats from HTTP aggregation: {} users, {} events", totalUsers, totalEvents);
            return overview;
        }
        
        throw new RuntimeException("No data from HTTP aggregation endpoint");
    }
    
    /**
     * Check if this is a multi-node cluster deployment.
     */
    private boolean isMultiNodeCluster() {
        if (cluster == null) {
            return false;
        }
        
        try {
            int memberCount = 0;
            for (Member member : cluster.state().getMembers()) {
                memberCount++;
            }
            boolean isMultiNode = memberCount > 1;
            logger.debug("Cluster member count: {}, isMultiNode: {}", memberCount, isMultiNode);
            return isMultiNode;
        } catch (Exception e) {
            logger.warn("Failed to check cluster size: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get event type breakdown with counts from aggregation service directly.
     */
    private Map<String, Object> getEventTypeBreakdown() {
        try {
            Map<String, Long> eventTypeCounts = new HashMap<>();
            
            // Get event types directly from aggregation service
            Map<String, UserEventAggregation> allAggregations = userEventAggregationService.getAllAggregations();
            
            // Aggregate event type counts across all users
            for (UserEventAggregation aggregation : allAggregations.values()) {
                for (Map.Entry<String, Long> entry : aggregation.getEventTypeCounts().entrySet()) {
                    eventTypeCounts.merge(entry.getKey(), entry.getValue(), Long::sum);
                }
            }
            
            logger.info("Successfully retrieved event types from aggregation service: {}", eventTypeCounts);
            
            // Convert to chart-friendly format
            List<Map<String, Object>> chartData = eventTypeCounts.entrySet().stream()
                .map(entry -> Map.of(
                    "name", (Object) entry.getKey(),
                    "value", (Object) entry.getValue(),
                    "percentage", (Object) (eventTypeCounts.values().stream().mapToLong(Long::longValue).sum() > 0 ? 
                        (double) entry.getValue() / eventTypeCounts.values().stream().mapToLong(Long::longValue).sum() * 100 : 0)
                ))
                .sorted((a, b) -> Long.compare((Long) b.get("value"), (Long) a.get("value")))
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("counts", eventTypeCounts);
            result.put("chartData", chartData);
            result.put("totalTypes", eventTypeCounts.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get event type breakdown", e);
            return Map.of("error", "Failed to get event type breakdown");
        }
    }
    
    /**
     * Extract original event type from event data JSON.
     */
    private String extractOriginalEventType(String eventData) {
        if (eventData == null || eventData.isEmpty()) {
            return null;
        }
        
        try {
            // Look for common patterns in event data that might indicate the original event type
            if (eventData.contains("DROP")) return "DROP";
            if (eventData.contains("EMAIL")) return "EMAIL";
            if (eventData.contains("SMS")) return "SMS";
            if (eventData.contains("CALL")) return "CALL";
            if (eventData.contains("PICKUP")) return "PICKUP";
            if (eventData.contains("DELIVERY")) return "DELIVERY";
            
            return null;
        } catch (Exception e) {
            logger.warn("Failed to extract event type from data: {}", eventData);
            return null;
        }
    }
    
    /**
     * Get user activity statistics.
     */
    private Map<String, Object> getUserActivityStats() {
        try {
            if (!isH2Available()) {
                return Map.of(
                    "totalUsers", 0,
                    "activeUsers", 0,
                    "averageEventsPerUser", 0.0,
                    "maxEventsPerUser", 0,
                    "note", "H2 event journal not available in this profile"
                );
            }
            
            List<String> allPersistenceIds = h2EventJournal.getAllPersistenceIds();
            Map<String, Long> userEventCounts = new HashMap<>();
            
            for (String persistenceId : allPersistenceIds) {
                long eventCount = h2EventJournal.getEventCount(persistenceId);
                userEventCounts.put(persistenceId, eventCount);
            }
            
            // Calculate statistics
            long totalUsers = userEventCounts.size();
            long activeUsers = userEventCounts.values().stream().filter(count -> count > 0).count();
            double averageEventsPerUser = userEventCounts.values().stream().mapToLong(Long::longValue).average().orElse(0.0);
            long maxEventsPerUser = userEventCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalUsers", totalUsers);
            result.put("activeUsers", activeUsers);
            result.put("averageEventsPerUser", Math.round(averageEventsPerUser * 100.0) / 100.0);
            result.put("maxEventsPerUser", maxEventsPerUser);
            result.put("userEventCounts", userEventCounts);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get user activity stats", e);
            return Map.of("error", "Failed to get user activity stats");
        }
    }
    
    /**
     * Get recent events for activity feed directly from aggregation service.
     */
    private List<Map<String, Object>> getRecentEvents(int limit) {
        try {
            List<Map<String, Object>> recentEvents = new ArrayList<>();
            
            // Get aggregations directly from service
            Map<String, UserEventAggregation> allAggregations = userEventAggregationService.getAllAggregations();
            
            // Create event entries from aggregations
            for (UserEventAggregation aggregation : allAggregations.values()) {
                // Create an event entry for each event type the user has
                for (Map.Entry<String, Long> eventTypeEntry : aggregation.getEventTypeCounts().entrySet()) {
                    Map<String, Object> eventInfo = new HashMap<>();
                    eventInfo.put("userId", formatUserIdForDisplay(aggregation.getUserId()));
                    eventInfo.put("fullUserId", aggregation.getUserId());
                    eventInfo.put("eventType", eventTypeEntry.getKey());
                    eventInfo.put("eventCount", eventTypeEntry.getValue());
                    eventInfo.put("timestamp", aggregation.getLastUpdated() != null ? aggregation.getLastUpdated() : LocalDateTime.now());
                    eventInfo.put("lastUpdated", aggregation.getLastUpdated());
                    eventInfo.put("source", "aggregation-service-direct");
                    
                    String details = createEventDetails(eventTypeEntry.getKey(), formatUserIdForDisplay(aggregation.getUserId()));
                    eventInfo.put("details", details);
                    
                    recentEvents.add(eventInfo);
                }
            }
            
            // Sort by last updated (most recent first) and limit
            return recentEvents.stream()
                .sorted((a, b) -> {
                    LocalDateTime timeA = (LocalDateTime) a.get("lastUpdated");
                    LocalDateTime timeB = (LocalDateTime) b.get("lastUpdated");
                    if (timeA == null && timeB == null) return 0;
                    if (timeA == null) return 1;
                    if (timeB == null) return -1;
                    return timeB.compareTo(timeA);
                })
                .limit(limit)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("Failed to get recent events", e);
            return List.of(Map.of("error", "Failed to get recent events"));
        }
    }
    
    /**
     * Extract event type from event data JSON.
     */
    private String extractEventTypeFromEventData(String eventData) {
        if (eventData == null || eventData.isEmpty()) {
            return null;
        }
        
        try {
            // Look for eventType field in JSON
            if (eventData.contains("\"eventType\":")) {
                // Simple regex to extract eventType value
                String pattern = "\"eventType\"\\s*:\\s*\"([^\"]+)\"";
                java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher matcher = regex.matcher(eventData);
                
                if (matcher.find()) {
                    String eventType = matcher.group(1);
                    
                    // Map specific event types to more readable names
                    switch (eventType) {
                        case "EMAIL_DELIVERY":
                            return "EMAIL";
                        case "SMS_DELIVERY":
                            return "SMS";
                        case "CALL_DELIVERY":
                            return "CALL";
                        case "DROP_EVENT":
                            return "DROP";
                        default:
                            return eventType;
                    }
                }
            }
            
            // Fallback to pattern matching
            return extractOriginalEventType(eventData);
            
        } catch (Exception e) {
            logger.warn("Failed to extract event type from event data: {}", eventData);
            return null;
        }
    }
    
    /**
     * Create detailed event description based on event type and user.
     */
    private String createEventDetails(String eventType, String userId) {
        switch (eventType) {
            case "UserEventProcessed":
                return "User " + userId + " processed an event";
            case "DROP":
                return "User " + userId + " dropped an item";
            case "EMAIL":
                return "User " + userId + " sent an email";
            case "SMS":
                return "User " + userId + " sent an SMS";
            case "CALL":
                return "User " + userId + " made a call";
            case "PICKUP":
                return "User " + userId + " picked up an item";
            case "DELIVERY":
                return "User " + userId + " completed delivery";
            default:
                return "User " + userId + " performed " + eventType;
        }
    }
    
    /**
     * Get time-based analytics (simulated for now).
     */
    private Map<String, Object> getTimeBasedAnalytics() {
        try {
            // Simulate hourly event counts for the last 24 hours
            List<Map<String, Object>> hourlyData = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            
            for (int i = 23; i >= 0; i--) {
                LocalDateTime hour = now.minusHours(i);
                Map<String, Object> hourData = new HashMap<>();
                hourData.put("hour", hour.format(DateTimeFormatter.ofPattern("HH:mm")));
                hourData.put("events", (long) (Math.random() * 100 + 10)); // Simulate event counts
                hourData.put("users", (long) (Math.random() * 20 + 5)); // Simulate user counts
                hourlyData.add(hourData);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("hourlyData", hourlyData);
            result.put("timeRange", "Last 24 hours");
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get time-based analytics", e);
            return Map.of("error", "Failed to get time-based analytics");
        }
    }
    
    /**
     * Get system health metrics.
     */
    private Map<String, Object> getSystemHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // Database health - check both H2 and MySQL
            try {
                if (isH2Available()) {
                    List<String> persistenceIds = h2EventJournal.getAllPersistenceIds();
                    health.put("database", Map.of(
                        "status", "healthy",
                        "type", "H2",
                        "persistenceIds", persistenceIds.size(),
                        "accessible", true
                    ));
                } else {
                    // Check MySQL connectivity through aggregation service directly
                    try {
                        long totalUsers = userEventAggregationService.getTotalUsers();
                        long totalEvents = userEventAggregationService.getTotalEventsAcrossAllUsers();
                        
                        health.put("database", Map.of(
                            "status", "healthy",
                            "type", "MySQL (via aggregations)",
                            "totalUsers", totalUsers,
                            "totalEvents", totalEvents,
                            "accessible", true
                        ));
                    } catch (Exception mysqlEx) {
                        health.put("database", Map.of(
                            "status", "error",
                            "type", "MySQL",
                            "error", "Cannot access aggregation service: " + mysqlEx.getMessage(),
                            "accessible", false
                        ));
                    }
                }
            } catch (Exception e) {
                health.put("database", Map.of(
                    "status", "error",
                    "error", e.getMessage(),
                    "accessible", false
                ));
            }
            
            // Memory usage (simulated)
            health.put("memory", Map.of(
                "used", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                "total", Runtime.getRuntime().totalMemory(),
                "percentage", (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / Runtime.getRuntime().totalMemory() * 100
            ));
            
            // Overall status
            health.put("overall", Map.of(
                "status", "healthy",
                "uptime", "Running",
                "lastCheck", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
            
            return health;
            
        } catch (Exception e) {
            logger.error("Failed to get system health", e);
            return Map.of("error", "Failed to get system health");
        }
    }
    
    /**
     * Get top users by event count directly from aggregation service.
     */
    private List<Map<String, Object>> getTopUsers(int limit) {
        try {
            List<Map<String, Object>> topUsers = new ArrayList<>();
            
            // Get all aggregations directly from service
            Map<String, UserEventAggregation> allAggregations = userEventAggregationService.getAllAggregations();
            
            // Convert to top users format
            for (UserEventAggregation aggregation : allAggregations.values()) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("userId", formatUserIdForDisplay(aggregation.getUserId()));
                userData.put("fullUserId", aggregation.getUserId());
                userData.put("eventCount", aggregation.getTotalEvents());
                userData.put("eventTypes", aggregation.getEventTypeCounts());
                userData.put("lastActivity", aggregation.getLastUpdated());
                userData.put("source", "aggregation-service-direct");
                
                topUsers.add(userData);
            }
            
            logger.info("Retrieved {} top users from aggregation service directly", topUsers.size());
            
            return topUsers.stream()
                .sorted((a, b) -> Long.compare((Long) b.get("eventCount"), (Long) a.get("eventCount")))
                .limit(limit)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            logger.error("Failed to get top users", e);
            return List.of(Map.of("error", "Failed to get top users"));
        }
    }
    
    /**
     * Format user ID for better display in the dashboard.
     */
    private String formatUserIdForDisplay(String persistenceId) {
        if (persistenceId == null) return "Unknown";
        
        // Remove PersistentUserActor| prefix if present
        String cleanId = persistenceId.replace("PersistentUserActor|", "");
        
        // If it's a simple user ID (like user123, user456), return as is
        if (cleanId.matches("^user\\w+$")) {
            return cleanId;
        }
        
        // If it's a hash, show first 8 and last 4 characters
        if (cleanId.length() > 20) {
            return cleanId.substring(0, 8) + "..." + cleanId.substring(cleanId.length() - 4);
        }
        
        return cleanId;
    }
    
    /**
     * Get comprehensive performance metrics with caching for expensive calculations.
     */
    @Cacheable(value = "performanceMetrics", unless = "#result.containsKey('error')")
    public Map<String, Object> getPerformanceMetrics() {
        try {
            // Check if cache is still valid (refresh every 30 seconds)
            if (isCacheValid()) {
                Object cachedMetrics = performanceCache.get("performance");
                if (cachedMetrics instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typedCache = (Map<String, Object>) cachedMetrics;
                    return typedCache;
                }
            }
            
            logger.info("Generating performance metrics");
            
            Map<String, Object> performance = new HashMap<>();
            
            // Throughput metrics
            ThroughputMetrics throughput = performanceMetricsService.getThroughputMetrics(Duration.ofMinutes(5));
            performance.put("throughput", Map.of(
                "eventsPerSecond", throughput.eventsPerSecond(),
                "peakThroughput", throughput.peakThroughput(),
                "efficiency", throughput.getThroughputEfficiency(),
                "status", throughput.getStatusDescription(),
                "isHealthy", throughput.isHealthy()
            ));
            
            // Latency metrics
            LatencyMetrics latency = performanceMetricsService.getLatencyMetrics();
            performance.put("latency", Map.of(
                "averageMs", latency.getAverageLatencyMs(),
                "p95Ms", latency.getP95LatencyMs(),
                "maxMs", latency.getMaxLatencyMs(),
                "level", latency.getLatencyLevel().toString(),
                "status", latency.getStatusDescription(),
                "isHealthy", latency.isHealthy()
            ));
            
            // Resource metrics
            ResourceMetrics resources = performanceMetricsService.getResourceMetrics();
            performance.put("resources", Map.of(
                "cpuUsage", resources.cpuUsage(),
                "memoryUsedMB", resources.getMemoryUsedMB(),
                "memoryTotalMB", resources.getMemoryTotalMB(),
                "memoryUsagePercent", resources.getMemoryUsagePercent(),
                "diskUsage", resources.diskUsage(),
                "constraint", resources.getCriticalConstraint().toString(),
                "status", resources.getStatusDescription(),
                "isHealthy", resources.isHealthy()
            ));
            
            // Performance trend (last hour)
            List<PerformanceDataPoint> trendData = performanceMetricsService.getPerformanceTrend(Duration.ofHours(1));
            List<Map<String, Object>> trendChartData = trendData.stream()
                .map(point -> {
                    Map<String, Object> pointMap = new HashMap<>();
                    pointMap.put("timestamp", point.timestamp().format(DateTimeFormatter.ofPattern("HH:mm")));
                    pointMap.put("throughput", point.throughput());
                    pointMap.put("cpuUsage", point.cpuUsage());
                    pointMap.put("memoryUsage", point.memoryUsage());
                    pointMap.put("performanceScore", point.getPerformanceScore());
                    pointMap.put("level", point.getPerformanceLevel().toString());
                    return pointMap;
                })
                .collect(Collectors.toList());
            
            performance.put("trend", Map.of(
                "data", trendChartData,
                "timeRange", "Last 1 hour",
                "dataPoints", trendData.size()
            ));
            
            // Overall performance summary
            boolean overallHealthy = throughput.isHealthy() && latency.isHealthy() && resources.isHealthy();
            performance.put("summary", Map.of(
                "isHealthy", overallHealthy,
                "status", overallHealthy ? "System performing well" : "Performance issues detected",
                "lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
            
            // Cache the results
            performanceCache.put("performance", performance);
            lastCacheUpdate = LocalDateTime.now();
            
            logger.info("Performance metrics generated successfully");
            return performance;
            
        } catch (Exception e) {
            logger.error("Failed to generate performance metrics", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to generate performance metrics: " + e.getMessage());
            error.put("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return error;
        }
    }
    
    /**
     * Records an event processing for performance tracking.
     */
    public void recordEventProcessed(String eventType, Duration processingTime) {
        try {
            performanceMetricsService.recordEventProcessed(eventType);
            performanceMetricsService.recordProcessingTime(processingTime);
        } catch (Exception e) {
            logger.warn("Failed to record event processing metrics", e);
        }
    }
    
    /**
     * Gets real-time performance metrics without caching (for WebSocket updates).
     */
    public Map<String, Object> getRealTimePerformanceMetrics() {
        try {
            Map<String, Object> realTime = new HashMap<>();
            
            // Current throughput (last minute)
            ThroughputMetrics throughput = performanceMetricsService.getThroughputMetrics(Duration.ofMinutes(1));
            realTime.put("currentThroughput", throughput.eventsPerSecond());
            
            // Current latency
            LatencyMetrics latency = performanceMetricsService.getLatencyMetrics();
            realTime.put("currentLatency", latency.getAverageLatencyMs());
            
            // Current resources
            ResourceMetrics resources = performanceMetricsService.getResourceMetrics();
            realTime.put("currentCpuUsage", resources.cpuUsage());
            realTime.put("currentMemoryUsage", resources.getMemoryUsagePercent());
            
            realTime.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return realTime;
            
        } catch (Exception e) {
            logger.error("Failed to get real-time performance metrics", e);
            return Map.of("error", "Failed to get real-time metrics");
        }
    }
    
    /**
     * Clears the performance metrics cache.
     */
    public void clearPerformanceCache() {
        performanceCache.clear();
        lastCacheUpdate = LocalDateTime.now();
    }
    
    /**
     * Checks if the performance cache is still valid.
     */
    private boolean isCacheValid() {
        return Duration.between(lastCacheUpdate, LocalDateTime.now()).getSeconds() < 30;
    }
    
    /**
     * Gets current pipeline status for visualization.
     */
    public Map<String, Object> getPipelineStatus() {
        try {
            logger.info("Getting pipeline status");
            
            PipelineStatus status = pipelineVisualizationService.getCurrentPipelineStatus();
            
            Map<String, Object> result = new HashMap<>();
            result.put("kafkaEventsReceived", status.kafkaEventsReceived());
            result.put("pekkoEventsProcessed", status.pekkoEventsProcessed());
            result.put("h2EventsPersisted", status.h2EventsPersisted());
            result.put("kafkaErrors", status.kafkaErrors());
            result.put("pekkoErrors", status.pekkoErrors());
            result.put("h2Errors", status.h2Errors());
            result.put("kafkaThroughput", status.kafkaThroughput());
            result.put("pekkoThroughput", status.pekkoThroughput());
            result.put("h2Throughput", status.h2Throughput());
            result.put("kafkaHealth", convertStageHealthToMap(status.kafkaHealth()));
            result.put("pekkoHealth", convertStageHealthToMap(status.pekkoHealth()));
            result.put("h2Health", convertStageHealthToMap(status.h2Health()));
            result.put("pipelineEfficiency", status.pipelineEfficiency());
            result.put("isPipelineHealthy", status.isPipelineHealthy());
            result.put("pipelineHealthStatus", status.getPipelineHealthStatus());
            result.put("successRate", status.getSuccessRate());
            result.put("errorRate", status.getErrorRate());
            result.put("eventsInProgress", status.getEventsInProgress());
            result.put("timestamp", status.timestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            logger.info("Pipeline status retrieved successfully");
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get pipeline status", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get pipeline status: " + e.getMessage());
            error.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return error;
        }
    }
    
    /**
     * Gets pipeline flow metrics.
     */
    public Map<String, Object> getPipelineFlowMetrics() {
        try {
            logger.info("Getting pipeline flow metrics");
            
            PipelineFlowMetrics flowMetrics = pipelineVisualizationService.getPipelineFlowMetrics(Duration.ofMinutes(5));
            
            Map<String, Object> result = new HashMap<>();
            result.put("kafkaToPekkoFlow", flowMetrics.kafkaToPekkoFlow());
            result.put("pekkoToH2Flow", flowMetrics.pekkoToH2Flow());
            result.put("endToEndFlow", flowMetrics.endToEndFlow());
            result.put("avgKafkaProcessingTimeMs", flowMetrics.getAvgKafkaProcessingTimeMs());
            result.put("avgPekkoProcessingTimeMs", flowMetrics.getAvgPekkoProcessingTimeMs());
            result.put("avgH2ProcessingTimeMs", flowMetrics.getAvgH2ProcessingTimeMs());
            result.put("totalPipelineTimeMs", flowMetrics.getTotalPipelineTimeMs());
            result.put("eventsInPeriod", flowMetrics.eventsInPeriod());
            result.put("slowestStage", flowMetrics.getSlowestStage().getCode());
            result.put("fastestStage", flowMetrics.getFastestStage().getCode());
            result.put("flowEfficiency", flowMetrics.getFlowEfficiency().getDisplayName());
            result.put("processingSpeed", flowMetrics.getProcessingSpeed().getDisplayName());
            result.put("eventsPerSecond", flowMetrics.getEventsPerSecond());
            result.put("flowSummary", flowMetrics.getFlowSummary());
            
            logger.info("Pipeline flow metrics retrieved successfully");
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get pipeline flow metrics", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get pipeline flow metrics: " + e.getMessage());
            
            return error;
        }
    }
    
    /**
     * Gets recent pipeline errors.
     */
    public Map<String, Object> getPipelineErrors() {
        try {
            logger.info("Getting pipeline errors");
            
            List<PipelineError> allErrors = pipelineVisualizationService.getAllRecentErrors(50);
            
            // Group errors by stage
            Map<String, List<Map<String, Object>>> errorsByStage = new HashMap<>();
            
            for (PipelineError error : allErrors) {
                String stageCode = error.stage().getCode();
                errorsByStage.computeIfAbsent(stageCode, k -> new ArrayList<>())
                    .add(convertPipelineErrorToMap(error));
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("errorsByStage", errorsByStage);
            result.put("totalErrors", allErrors.size());
            result.put("recentErrors", allErrors.stream()
                .limit(10)
                .map(this::convertPipelineErrorToMap)
                .collect(Collectors.toList()));
            
            logger.info("Pipeline errors retrieved successfully");
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get pipeline errors", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get pipeline errors: " + e.getMessage());
            
            return error;
        }
    }
    
    /**
     * Records an event processing for pipeline tracking.
     */
    public void recordPipelineEvent(String stage, String eventType, String userId, Duration processingTime) {
        try {
            PipelineStage pipelineStage = PipelineStage.fromCode(stage.toLowerCase());
            
            switch (pipelineStage) {
                case KAFKA -> pipelineVisualizationService.recordKafkaEventReceived(eventType, userId);
                case PEKKO -> pipelineVisualizationService.recordPekkoEventProcessed(eventType, userId, processingTime);
                case H2 -> pipelineVisualizationService.recordH2EventPersisted(eventType, userId);
            }
            
            // Also record for performance metrics
            performanceMetricsService.recordEventProcessed(eventType);
            performanceMetricsService.recordProcessingTime(processingTime);
            
        } catch (Exception e) {
            logger.warn("Failed to record pipeline event", e);
        }
    }
    
    /**
     * Records a pipeline error.
     */
    public void recordPipelineError(String stage, String errorMessage, String eventType, String userId) {
        try {
            PipelineStage pipelineStage = PipelineStage.fromCode(stage.toLowerCase());
            pipelineVisualizationService.recordPipelineError(pipelineStage, errorMessage, eventType, userId);
        } catch (Exception e) {
            logger.warn("Failed to record pipeline error", e);
        }
    }
    
    /**
     * Converts StageHealth to Map for JSON serialization.
     */
    private Map<String, Object> convertStageHealthToMap(StageHealth stageHealth) {
        Map<String, Object> healthMap = new HashMap<>();
        healthMap.put("stage", stageHealth.stage().getCode());
        healthMap.put("isHealthy", stageHealth.isHealthy());
        healthMap.put("consecutiveFailures", stageHealth.consecutiveFailures());
        healthMap.put("healthStatus", stageHealth.getHealthStatus());
        healthMap.put("healthColor", stageHealth.getHealthColor());
        healthMap.put("healthScore", stageHealth.getHealthScore());
        healthMap.put("requiresAttention", stageHealth.requiresAttention());
        healthMap.put("healthDescription", stageHealth.getHealthDescription());
        healthMap.put("trend", stageHealth.getTrend().getDisplayName());
        healthMap.put("lastUpdated", stageHealth.lastUpdated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return healthMap;
    }
    
    /**
     * Converts PipelineError to Map for JSON serialization.
     */
    private Map<String, Object> convertPipelineErrorToMap(PipelineError error) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("stage", error.stage().getCode());
        errorMap.put("stageName", error.stage().getDisplayName());
        errorMap.put("errorMessage", error.errorMessage());
        errorMap.put("shortErrorMessage", error.getShortErrorMessage());
        errorMap.put("eventType", error.eventType());
        errorMap.put("userId", error.userId());
        errorMap.put("timestamp", error.timestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        errorMap.put("formattedTimestamp", error.getFormattedTimestamp());
        errorMap.put("severity", error.getSeverity().getDisplayName());
        errorMap.put("severityColor", error.getSeverity().getColor());
        errorMap.put("locationDescription", error.getLocationDescription());
        errorMap.put("isRecent", error.isRecent());
        errorMap.put("ageInMinutes", error.getAgeInMinutes());
        return errorMap;
    }
}