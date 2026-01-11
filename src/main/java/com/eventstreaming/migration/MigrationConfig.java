package com.eventstreaming.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for data migration
 */
@Configuration
@ConfigurationProperties(prefix = "migration")
public class MigrationConfig {
    
    private int batchSize = 1000;
    private int maxRetries = 3;
    private long retryDelayMs = 1000;
    private boolean preserveEventOrdering = true;
    private boolean skipInvalidRecords = true;
    private String legacyTableName = "communication_events";
    private String orderByColumn = "created_at";
    private boolean enableProgressReporting = true;
    private long progressReportInterval = 10000;
    
    // Database connection settings for legacy MySQL
    private LegacyDatabase legacyDatabase = new LegacyDatabase();
    
    public static class LegacyDatabase {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int maxPoolSize = 10;
        private int connectionTimeout = 30000;
        
        // Getters and setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
        
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        
        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    }
    
    // Getters and setters
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    
    public boolean isPreserveEventOrdering() { return preserveEventOrdering; }
    public void setPreserveEventOrdering(boolean preserveEventOrdering) { this.preserveEventOrdering = preserveEventOrdering; }
    
    public boolean isSkipInvalidRecords() { return skipInvalidRecords; }
    public void setSkipInvalidRecords(boolean skipInvalidRecords) { this.skipInvalidRecords = skipInvalidRecords; }
    
    public String getLegacyTableName() { return legacyTableName; }
    public void setLegacyTableName(String legacyTableName) { this.legacyTableName = legacyTableName; }
    
    public String getOrderByColumn() { return orderByColumn; }
    public void setOrderByColumn(String orderByColumn) { this.orderByColumn = orderByColumn; }
    
    public boolean isEnableProgressReporting() { return enableProgressReporting; }
    public void setEnableProgressReporting(boolean enableProgressReporting) { this.enableProgressReporting = enableProgressReporting; }
    
    public long getProgressReportInterval() { return progressReportInterval; }
    public void setProgressReportInterval(long progressReportInterval) { this.progressReportInterval = progressReportInterval; }
    
    public LegacyDatabase getLegacyDatabase() { return legacyDatabase; }
    public void setLegacyDatabase(LegacyDatabase legacyDatabase) { this.legacyDatabase = legacyDatabase; }
}