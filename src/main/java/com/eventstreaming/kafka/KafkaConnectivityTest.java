package com.eventstreaming.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tests Kafka connectivity using the configuration loaded by KafkaConfigManager.
 * This class provides methods to test basic Kafka operations like connecting,
 * listing topics, producing messages, and consuming messages.
 */
@Component
public class KafkaConnectivityTest {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaConnectivityTest.class);
    
    private final KafkaConfigManager configManager;
    
    public KafkaConnectivityTest(KafkaConfigManager configManager) {
        this.configManager = configManager;
    }
    
    /**
     * Tests basic Kafka connectivity by attempting to list topics.
     * 
     * @return TestResult containing the outcome of the connectivity test
     */
    public TestResult testConnectivity() {
        try {
            Properties kafkaConfig = configManager.loadConfiguration();
            Properties adminProps = createAdminProperties(kafkaConfig);
            
            try (AdminClient adminClient = AdminClient.create(adminProps)) {
                ListTopicsResult topicsResult = adminClient.listTopics();
                Set<String> topics = topicsResult.names().get(30, TimeUnit.SECONDS);
                
                logger.info("Successfully connected to Kafka. Found {} topics", topics.size());
                return TestResult.success("Connected successfully. Found " + topics.size() + " topics.");
                
            } catch (Exception e) {
                logger.error("Failed to connect to Kafka", e);
                return TestResult.failure("Connection failed: " + e.getMessage());
            }
            
        } catch (KafkaConfigurationException e) {
            logger.error("Configuration error", e);
            return TestResult.failure("Configuration error: " + e.getMessage());
        }
    }
    
    /**
     * Tests Kafka connectivity using YAML configuration file.
     * 
     * @param yamlFilePath Path to the YAML configuration file
     * @return TestResult containing the outcome of the connectivity test
     */
    public TestResult testConnectivityWithYaml(String yamlFilePath) {
        try {
            Properties kafkaConfig = configManager.loadConfigurationFromYaml(yamlFilePath);
            Properties adminProps = createAdminProperties(kafkaConfig);
            
            try (AdminClient adminClient = AdminClient.create(adminProps)) {
                ListTopicsResult topicsResult = adminClient.listTopics();
                Set<String> topics = topicsResult.names().get(30, TimeUnit.SECONDS);
                
                logger.info("Successfully connected to Kafka using YAML config. Found {} topics", topics.size());
                return TestResult.success("Connected successfully using YAML config. Found " + topics.size() + " topics.");
                
            } catch (Exception e) {
                logger.error("Failed to connect to Kafka using YAML config", e);
                return TestResult.failure("Connection failed: " + e.getMessage());
            }
            
        } catch (KafkaConfigurationException e) {
            logger.error("YAML configuration error", e);
            return TestResult.failure("YAML configuration error: " + e.getMessage());
        }
    }
    
    /**
     * Tests producing a simple message to Kafka.
     * 
     * @return TestResult containing the outcome of the producer test
     */
    public TestResult testProducer() {
        try {
            Properties kafkaConfig = configManager.loadConfiguration();
            Properties producerProps = createProducerProperties(kafkaConfig);
            
            String testTopic = kafkaConfig.getProperty("KAFKA_TEST_TOPIC");
            String testMessage = "Test message from KafkaConnectivityTest at " + System.currentTimeMillis();
            
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
                ProducerRecord<String, String> record = new ProducerRecord<>(testTopic, "test-key", testMessage);
                producer.send(record).get(30, TimeUnit.SECONDS);
                
                logger.info("Successfully sent test message to topic: {}", testTopic);
                return TestResult.success("Successfully sent message to topic: " + testTopic);
                
            } catch (Exception e) {
                logger.error("Failed to send message to Kafka", e);
                return TestResult.failure("Producer test failed: " + e.getMessage());
            }
            
        } catch (KafkaConfigurationException e) {
            logger.error("Configuration error during producer test", e);
            return TestResult.failure("Configuration error: " + e.getMessage());
        }
    }
    
    /**
     * Tests consuming messages from Kafka.
     * 
     * @return TestResult containing the outcome of the consumer test
     */
    public TestResult testConsumer() {
        try {
            Properties kafkaConfig = configManager.loadConfiguration();
            Properties consumerProps = createConsumerProperties(kafkaConfig);
            
            String testTopic = kafkaConfig.getProperty("KAFKA_TEST_TOPIC");
            
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
                consumer.subscribe(Collections.singletonList(testTopic));
                
                // Poll for a short time to test connectivity
                consumer.poll(Duration.ofSeconds(5));
                
                logger.info("Successfully connected consumer to topic: {}", testTopic);
                return TestResult.success("Successfully connected consumer to topic: " + testTopic);
                
            } catch (Exception e) {
                logger.error("Failed to connect consumer to Kafka", e);
                return TestResult.failure("Consumer test failed: " + e.getMessage());
            }
            
        } catch (KafkaConfigurationException e) {
            logger.error("Configuration error during consumer test", e);
            return TestResult.failure("Configuration error: " + e.getMessage());
        }
    }
    
    /**
     * Creates admin client properties from Kafka configuration.
     * 
     * @param kafkaConfig Kafka configuration properties
     * @return Properties for AdminClient
     */
    private Properties createAdminProperties(Properties kafkaConfig) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, 
                  kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
        
        String securityProtocol = kafkaConfig.getProperty("KAFKA_SECURITY_PROTOCOL");
        if (securityProtocol != null && !securityProtocol.equals("PLAINTEXT")) {
            props.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, securityProtocol);
            
            String saslMechanism = kafkaConfig.getProperty("KAFKA_SASL_MECHANISM");
            if (saslMechanism != null) {
                props.put("sasl.mechanism", saslMechanism);
            }
            
            String jaasConfig = kafkaConfig.getProperty("KAFKA_SASL_JAAS_CONFIG");
            if (jaasConfig != null) {
                props.put("sasl.jaas.config", jaasConfig);
            }
        }
        
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, "30000");
        
        return props;
    }
    
    /**
     * Creates producer properties from Kafka configuration.
     * 
     * @param kafkaConfig Kafka configuration properties
     * @return Properties for KafkaProducer
     */
    private Properties createProducerProperties(Properties kafkaConfig) {
        Properties props = createAdminProperties(kafkaConfig);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, "3");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "1");
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");
        
        return props;
    }
    
    /**
     * Creates consumer properties from Kafka configuration.
     * 
     * @param kafkaConfig Kafka configuration properties
     * @return Properties for KafkaConsumer
     */
    private Properties createConsumerProperties(Properties kafkaConfig) {
        Properties props = createAdminProperties(kafkaConfig);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, 
                  kafkaConfig.getProperty("KAFKA_TEST_CONSUMER_GROUP", "kafka-connectivity-test-group"));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1");
        
        return props;
    }
    
    /**
     * Represents the result of a Kafka connectivity test.
     */
    public static class TestResult {
        private final boolean success;
        private final String message;
        private final long timestamp;
        
        private TestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
        
        public static TestResult success(String message) {
            return new TestResult(true, message);
        }
        
        public static TestResult failure(String message) {
            return new TestResult(false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("TestResult{success=%s, message='%s', timestamp=%d}", 
                               success, message, timestamp);
        }
    }
}