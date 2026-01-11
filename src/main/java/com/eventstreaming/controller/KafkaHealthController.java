package com.eventstreaming.controller;

import com.eventstreaming.kafka.KafkaConnectivityTest;
import com.eventstreaming.kafka.KafkaConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for monitoring Kafka connectivity and health in the isolated environment.
 */
@RestController
@RequestMapping("/api/kafka")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthController {
    
    private final KafkaConnectivityTest kafkaConnectivityTest;
    private final KafkaConfigManager kafkaConfigManager;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${spring.profiles.active:isolated}")
    private String activeProfile;
    
    @Autowired
    public KafkaHealthController(KafkaConnectivityTest kafkaConnectivityTest,
                                KafkaConfigManager kafkaConfigManager,
                                @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaConnectivityTest = kafkaConnectivityTest;
        this.kafkaConfigManager = kafkaConfigManager;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Determine the appropriate configuration file based on active profile.
     */
    private String determineConfigFile() {
        if (activeProfile.contains("isolated")) {
            return "src/main/resources/application-isolated.yml";
        } else if (activeProfile.contains("cluster-mysql")) {
            return "src/main/resources/application-cluster-mysql.yml";
        } else if (activeProfile.contains("aws")) {
            return "src/main/resources/application-aws.yml";
        } else {
            // Default fallback
            return "src/main/resources/application-isolated.yml";
        }
    }
    
    /**
     * Checks Kafka connectivity using the YAML configuration.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkKafkaHealth() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String configFile = determineConfigFile();
            KafkaConnectivityTest.TestResult result = 
                kafkaConnectivityTest.testConnectivityWithYaml(configFile);
            
            response.put("status", result.isSuccess() ? "UP" : "DOWN");
            response.put("message", result.getMessage());
            response.put("timestamp", result.getTimestamp());
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(503).body(response);
            }
            
        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("message", "Health check failed: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(503).body(response);
        }
    }
    
    /**
     * Gets the current Kafka configuration being used.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getKafkaConfig() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String configFile = determineConfigFile();
            var kafkaConfig = kafkaConfigManager.loadConfigurationFromYaml(configFile);
            
            response.put("bootstrapServers", kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            response.put("consumerGroup", kafkaConfig.getProperty("KAFKA_TEST_CONSUMER_GROUP"));
            response.put("securityProtocol", kafkaConfig.getProperty("KAFKA_SECURITY_PROTOCOL"));
            response.put("testTopic", kafkaConfig.getProperty("KAFKA_TEST_TOPIC"));
            response.put("configurationLoaded", kafkaConfigManager.isConfigurationLoaded());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("error", "Failed to load configuration: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Tests Kafka producer functionality.
     */
    @GetMapping("/test-producer")
    public ResponseEntity<Map<String, Object>> testProducer() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            KafkaConnectivityTest.TestResult result = kafkaConnectivityTest.testProducer();
            
            response.put("status", result.isSuccess() ? "SUCCESS" : "FAILED");
            response.put("message", result.getMessage());
            response.put("timestamp", result.getTimestamp());
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("message", "Producer test failed: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Tests Kafka consumer functionality.
     */
    @GetMapping("/test-consumer")
    public ResponseEntity<Map<String, Object>> testConsumer() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            KafkaConnectivityTest.TestResult result = kafkaConnectivityTest.testConsumer();
            
            response.put("status", result.isSuccess() ? "SUCCESS" : "FAILED");
            response.put("message", result.getMessage());
            response.put("timestamp", result.getTimestamp());
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("message", "Consumer test failed: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Sends a message to a Kafka topic for testing purposes.
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody KafkaMessageRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (kafkaTemplate == null) {
                response.put("status", "FAILED");
                response.put("message", "KafkaTemplate not available - Kafka may not be configured");
                response.put("timestamp", System.currentTimeMillis());
                return ResponseEntity.status(503).body(response);
            }
            
            // Send the message to Kafka
            kafkaTemplate.send(request.getTopic(), request.getKey(), request.getValue());
            
            response.put("status", "SUCCESS");
            response.put("message", "Message sent successfully");
            response.put("topic", request.getTopic());
            response.put("key", request.getKey());
            response.put("valueLength", request.getValue() != null ? request.getValue().length() : 0);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("message", "Failed to send message: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Request DTO for sending Kafka messages.
     */
    public static class KafkaMessageRequest {
        @JsonProperty("topic")
        private String topic;
        
        @JsonProperty("key")
        private String key;
        
        @JsonProperty("value")
        private String value;
        
        // Getters and setters
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}