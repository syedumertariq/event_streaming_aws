package com.eventstreaming.monitoring;

import com.eventstreaming.persistence.H2EventJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting and calculating performance metrics including throughput,
 * latency, and system resource usage for the event streaming system.
 */
@Service
public class PerformanceMetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMetricsService.class);
    
    @Autowired(required = false)
    private H2EventJournal h2EventJournal;
    
    // Event tracking for throughput calculation
    private final ConcurrentHashMap<String, List<LocalDateTime>> eventTimestamps = new ConcurrentHashMap<>();
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    
    // System resource monitoring
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    
    /**
     * Records an event processing timestamp for throughput calculation.
     */
    public void recordEventProcessed(String eventType) {
        LocalDateTime now = LocalDateTime.now();
        eventTimestamps.computeIfAbsent(eventType, k -> new ArrayList<>()).add(now);
        totalEventsProcessed.incrementAndGet();
        
        // Clean old timestamps (keep only last hour)
        cleanOldTimestamps(eventType, now.minusHours(1));
    }
    
    /**
     * Records processing time for latency calculation.
     */
    public void recordProcessingTime(Duration processingTime) {
        totalProcessingTimeMs.addAndGet(processingTime.toMillis());
    }
    
    /**
     * Gets current throughput metrics showing events per second.
     */
    public ThroughputMetrics getThroughputMetrics(Duration period) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minus(period);
            long totalEvents = 0;
            double peakThroughput = 0.0;
            
            // Count events in the specified period
            for (List<LocalDateTime> timestamps : eventTimestamps.values()) {
                long eventsInPeriod = timestamps.stream()
                    .filter(timestamp -> timestamp.isAfter(cutoff))
                    .count();
                totalEvents += eventsInPeriod;
            }
            
            // Calculate events per second
            double eventsPerSecond = totalEvents / (double) period.getSeconds();
            
            // Calculate peak throughput (highest throughput in any 1-minute window)
            peakThroughput = calculatePeakThroughput(period);
            
            return new ThroughputMetrics(
                eventsPerSecond,
                peakThroughput,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            logger.error("Failed to calculate throughput metrics", e);
            return new ThroughputMetrics(0.0, 0.0, LocalDateTime.now());
        }
    }
    
    /**
     * Gets current latency metrics for the Kafka → H2 pipeline.
     */
    public LatencyMetrics getLatencyMetrics() {
        try {
            // Calculate average latency from recorded processing times
            long totalEvents = totalEventsProcessed.get();
            long totalTime = totalProcessingTimeMs.get();
            
            Duration averageLatency = totalEvents > 0 ? 
                Duration.ofMillis(totalTime / totalEvents) : Duration.ZERO;
            
            // Simulate P95 and max latency (in real implementation, would track percentiles)
            Duration p95Latency = Duration.ofMillis((long) (averageLatency.toMillis() * 1.5));
            Duration maxLatency = Duration.ofMillis((long) (averageLatency.toMillis() * 2.0));
            
            return new LatencyMetrics(averageLatency, p95Latency, maxLatency);
            
        } catch (Exception e) {
            logger.error("Failed to calculate latency metrics", e);
            return new LatencyMetrics(Duration.ZERO, Duration.ZERO, Duration.ZERO);
        }
    }
    
    /**
     * Gets current system resource metrics using Java Management APIs.
     */
    public ResourceMetrics getResourceMetrics() {
        try {
            // Memory usage
            long memoryUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long memoryTotal = memoryBean.getHeapMemoryUsage().getMax();
            
            // CPU usage (if available)
            double cpuUsage = 0.0;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                cpuUsage = sunOsBean.getProcessCpuLoad() * 100.0;
            }
            
            // Disk usage (simplified - using available disk space)
            double diskUsage = 0.0;
            try {
                java.io.File root = new java.io.File("/");
                long totalSpace = root.getTotalSpace();
                long freeSpace = root.getFreeSpace();
                diskUsage = ((double) (totalSpace - freeSpace) / totalSpace) * 100.0;
            } catch (Exception e) {
                logger.debug("Could not get disk usage", e);
            }
            
            return new ResourceMetrics(cpuUsage, memoryUsed, memoryTotal, diskUsage);
            
        } catch (Exception e) {
            logger.error("Failed to get resource metrics", e);
            return new ResourceMetrics(0.0, 0L, 0L, 0.0);
        }
    }
    
    /**
     * Gets performance trend data over the specified period.
     */
    public List<PerformanceDataPoint> getPerformanceTrend(Duration period) {
        try {
            List<PerformanceDataPoint> trendPoints = new ArrayList<>();
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minus(period);
            
            // Create data points at 5-minute intervals
            Duration interval = Duration.ofMinutes(5);
            LocalDateTime currentTime = startTime;
            
            while (currentTime.isBefore(endTime)) {
                LocalDateTime nextTime = currentTime.plus(interval);
                
                // Count events in this interval
                long eventsInInterval = countEventsInTimeRange(currentTime, nextTime);
                double throughput = eventsInInterval / (double) interval.getSeconds();
                
                // Get resource metrics at this point (current values as approximation)
                ResourceMetrics resources = getResourceMetrics();
                
                trendPoints.add(new PerformanceDataPoint(
                    currentTime,
                    throughput,
                    resources.cpuUsage(),
                    (double) resources.memoryUsed() / resources.memoryTotal() * 100.0
                ));
                
                currentTime = nextTime;
            }
            
            return trendPoints;
            
        } catch (Exception e) {
            logger.error("Failed to get performance trend", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Calculates peak throughput in any 1-minute window within the period.
     */
    private double calculatePeakThroughput(Duration period) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minus(period);
        double peakThroughput = 0.0;
        
        // Check 1-minute windows
        LocalDateTime windowStart = startTime;
        while (windowStart.isBefore(endTime.minusMinutes(1))) {
            LocalDateTime windowEnd = windowStart.plusMinutes(1);
            long eventsInWindow = countEventsInTimeRange(windowStart, windowEnd);
            double throughputInWindow = eventsInWindow / 60.0; // events per second
            
            if (throughputInWindow > peakThroughput) {
                peakThroughput = throughputInWindow;
            }
            
            windowStart = windowStart.plusMinutes(1);
        }
        
        return peakThroughput;
    }
    
    /**
     * Counts events processed in the specified time range.
     */
    private long countEventsInTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return eventTimestamps.values().stream()
            .flatMap(List::stream)
            .filter(timestamp -> timestamp.isAfter(startTime) && timestamp.isBefore(endTime))
            .count();
    }
    
    /**
     * Cleans old timestamps to prevent memory leaks.
     */
    private void cleanOldTimestamps(String eventType, LocalDateTime cutoff) {
        List<LocalDateTime> timestamps = eventTimestamps.get(eventType);
        if (timestamps != null) {
            timestamps.removeIf(timestamp -> timestamp.isBefore(cutoff));
        }
    }
    
    /**
     * Resets all performance metrics (useful for testing).
     */
    public void resetMetrics() {
        eventTimestamps.clear();
        totalEventsProcessed.set(0);
        totalProcessingTimeMs.set(0);
    }
}