package com.eventstreaming.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model for system resource metrics.
 * Represents CPU, memory, and disk usage measurements.
 */
public record ResourceMetrics(
    @JsonProperty("cpuUsage")
    double cpuUsage,
    
    @JsonProperty("memoryUsed")
    long memoryUsed,
    
    @JsonProperty("memoryTotal")
    long memoryTotal,
    
    @JsonProperty("diskUsage")
    double diskUsage
) {
    
    /**
     * Gets memory usage as a percentage.
     */
    public double getMemoryUsagePercent() {
        return memoryTotal > 0 ? ((double) memoryUsed / memoryTotal) * 100.0 : 0.0;
    }
    
    /**
     * Gets available memory in bytes.
     */
    public long getAvailableMemory() {
        return memoryTotal - memoryUsed;
    }
    
    /**
     * Gets memory usage in MB.
     */
    public double getMemoryUsedMB() {
        return memoryUsed / (1024.0 * 1024.0);
    }
    
    /**
     * Gets total memory in MB.
     */
    public double getMemoryTotalMB() {
        return memoryTotal / (1024.0 * 1024.0);
    }
    
    /**
     * Checks if resource usage is considered healthy.
     */
    public boolean isHealthy() {
        return cpuUsage < 80.0 && getMemoryUsagePercent() < 85.0 && diskUsage < 90.0;
    }
    
    /**
     * Gets the most critical resource constraint.
     */
    public ResourceConstraint getCriticalConstraint() {
        if (cpuUsage > 90.0) {
            return ResourceConstraint.CPU_CRITICAL;
        } else if (getMemoryUsagePercent() > 90.0) {
            return ResourceConstraint.MEMORY_CRITICAL;
        } else if (diskUsage > 95.0) {
            return ResourceConstraint.DISK_CRITICAL;
        } else if (cpuUsage > 75.0) {
            return ResourceConstraint.CPU_HIGH;
        } else if (getMemoryUsagePercent() > 80.0) {
            return ResourceConstraint.MEMORY_HIGH;
        } else if (diskUsage > 85.0) {
            return ResourceConstraint.DISK_HIGH;
        } else {
            return ResourceConstraint.NONE;
        }
    }
    
    /**
     * Gets a human-readable description of the resource status.
     */
    public String getStatusDescription() {
        return switch (getCriticalConstraint()) {
            case CPU_CRITICAL -> "Critical CPU usage - immediate attention required";
            case MEMORY_CRITICAL -> "Critical memory usage - immediate attention required";
            case DISK_CRITICAL -> "Critical disk usage - immediate attention required";
            case CPU_HIGH -> "High CPU usage - monitoring recommended";
            case MEMORY_HIGH -> "High memory usage - monitoring recommended";
            case DISK_HIGH -> "High disk usage - monitoring recommended";
            case NONE -> "Resource usage is healthy";
        };
    }
    
    /**
     * Enumeration for resource constraint levels.
     */
    public enum ResourceConstraint {
        NONE, CPU_HIGH, MEMORY_HIGH, DISK_HIGH, 
        CPU_CRITICAL, MEMORY_CRITICAL, DISK_CRITICAL
    }
}