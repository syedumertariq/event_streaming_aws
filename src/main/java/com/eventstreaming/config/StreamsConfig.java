package com.eventstreaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Pekko Streams event processing.
 */
@Configuration
@ConfigurationProperties(prefix = "app.streams.event-processing")
public class StreamsConfig {
    
    private boolean kafkaEnabled = true;
    private boolean autoStart = true;
    private int parallelism = 10;
    private int bufferSize = 1000;
    private String askTimeout = "5s";
    private int retryAttempts = 3;
    private String retryDelay = "1s";
    private RestartSettings restartSettings = new RestartSettings();
    
    // Getters and setters
    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }
    
    public void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }
    
    public boolean isAutoStart() {
        return autoStart;
    }
    
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }
    
    public int getParallelism() {
        return parallelism;
    }
    
    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }
    
    public int getBufferSize() {
        return bufferSize;
    }
    
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
    
    public String getAskTimeout() {
        return askTimeout;
    }
    
    public void setAskTimeout(String askTimeout) {
        this.askTimeout = askTimeout;
    }
    
    public int getRetryAttempts() {
        return retryAttempts;
    }
    
    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }
    
    public String getRetryDelay() {
        return retryDelay;
    }
    
    public void setRetryDelay(String retryDelay) {
        this.retryDelay = retryDelay;
    }
    
    public RestartSettings getRestartSettings() {
        return restartSettings;
    }
    
    public void setRestartSettings(RestartSettings restartSettings) {
        this.restartSettings = restartSettings;
    }
    
    /**
     * Restart settings for stream resilience.
     */
    public static class RestartSettings {
        private String minBackoff = "3s";
        private String maxBackoff = "30s";
        private double randomFactor = 0.2;
        
        public String getMinBackoff() {
            return minBackoff;
        }
        
        public void setMinBackoff(String minBackoff) {
            this.minBackoff = minBackoff;
        }
        
        public String getMaxBackoff() {
            return maxBackoff;
        }
        
        public void setMaxBackoff(String maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
        
        public double getRandomFactor() {
            return randomFactor;
        }
        
        public void setRandomFactor(double randomFactor) {
            this.randomFactor = randomFactor;
        }
    }
}