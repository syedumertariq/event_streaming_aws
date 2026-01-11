package com.eventstreaming.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for loading Kafka configuration from YAML files.
 */
class KafkaConfigManagerYamlTest {
    
    private KafkaConfigManager configManager;
    
    @BeforeEach
    void setUp() {
        configManager = new KafkaConfigManager();
    }
    
    @Test
    void testLoadConfigurationFromDevelopmentYaml() throws Exception {
        String yamlPath = "src/main/resources/application-development.yml";
        
        Properties props = configManager.loadConfigurationFromYaml(yamlPath);
        
        assertNotNull(props);
        
        // Check that bootstrap servers were loaded
        String bootstrapServers = props.getProperty("KAFKA_BOOTSTRAP_SERVERS");
        assertNotNull(bootstrapServers);
        assertTrue(bootstrapServers.contains("10.1.10.113:9092"));
        
        // Check that default values were set
        assertEquals("PLAINTEXT", props.getProperty("KAFKA_SECURITY_PROTOCOL"));
        assertEquals("PLAIN", props.getProperty("KAFKA_SASL_MECHANISM"));
        assertEquals("QuestIncrementerDropperINSCallQueueDev", props.getProperty("KAFKA_TEST_TOPIC"));
        
        // Check that consumer group was extracted from Spring Boot config
        String consumerGroup = props.getProperty("KAFKA_TEST_CONSUMER_GROUP");
        assertNotNull(consumerGroup);
        assertTrue(consumerGroup.contains("event-streaming-consumer"));
        
        assertTrue(configManager.isConfigurationLoaded());
        
        System.out.println("Loaded Kafka configuration from YAML:");
        props.forEach((key, value) -> {
            if (key.toString().startsWith("KAFKA_")) {
                System.out.println("  " + key + " = " + value);
            }
        });
    }
    
    @Test
    void testYamlFileNotFound() {
        String nonExistentYaml = "non-existent-config.yml";
        
        KafkaConfigurationException exception = assertThrows(
            KafkaConfigurationException.class,
            () -> configManager.loadConfigurationFromYaml(nonExistentYaml)
        );
        
        assertTrue(exception.getMessage().contains("Could not load YAML configuration file"));
    }
}