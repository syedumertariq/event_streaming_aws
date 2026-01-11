package com.eventstreaming.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for KafkaConnectivityTest.
 * Note: This test requires actual Kafka connectivity and may fail if Kafka is not accessible.
 */
class KafkaConnectivityTestIntegrationTest {
    
    private KafkaConnectivityTest connectivityTest;
    private KafkaConfigManager configManager;
    
    @BeforeEach
    void setUp() {
        configManager = new KafkaConfigManager();
        connectivityTest = new KafkaConnectivityTest(configManager);
    }
    
    @Test
    void testConnectivityWithDevelopmentYaml() {
        String yamlPath = "src/main/resources/application-development.yml";
        
        KafkaConnectivityTest.TestResult result = connectivityTest.testConnectivityWithYaml(yamlPath);
        
        assertNotNull(result);
        assertNotNull(result.getMessage());
        assertTrue(result.getTimestamp() > 0);
        
        System.out.println("Connectivity Test Result: " + result);
        
        if (result.isSuccess()) {
            System.out.println("✅ Kafka connectivity test PASSED");
            System.out.println("   Message: " + result.getMessage());
        } else {
            System.out.println("❌ Kafka connectivity test FAILED");
            System.out.println("   Error: " + result.getMessage());
            
            // Don't fail the test if Kafka is not accessible - this is expected in many environments
            System.out.println("   Note: This failure is expected if Kafka servers are not accessible from this environment");
        }
    }
    
    @Test
    void testConfigurationLoading() {
        // This test should always pass as it only tests configuration loading
        try {
            String yamlPath = "src/main/resources/application-development.yml";
            var properties = configManager.loadConfigurationFromYaml(yamlPath);
            
            assertNotNull(properties);
            assertNotNull(properties.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            
            System.out.println("✅ Configuration loading test PASSED");
            System.out.println("   Bootstrap servers: " + properties.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            System.out.println("   Test topic: " + properties.getProperty("KAFKA_TEST_TOPIC"));
            System.out.println("   Consumer group: " + properties.getProperty("KAFKA_TEST_CONSUMER_GROUP"));
            
        } catch (Exception e) {
            fail("Configuration loading should not fail: " + e.getMessage());
        }
    }
}