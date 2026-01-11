package com.eventstreaming.config;

import com.eventstreaming.kafka.KafkaConfigManager;
import com.eventstreaming.kafka.KafkaConfigurationException;
import com.eventstreaming.model.CommunicationEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Kafka configuration for the isolated environment.
 * This configuration uses our custom KafkaConfigManager to load configuration
 * from YAML files and integrates with the existing Spring Boot Kafka setup.
 */
@Configuration
@Profile("isolated")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaIsolatedConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaIsolatedConfiguration.class);
    
    private final KafkaConfigManager kafkaConfigManager;
    
    @Value("${spring.profiles.active:isolated}")
    private String activeProfile;
    
    public KafkaIsolatedConfiguration() {
        this.kafkaConfigManager = new KafkaConfigManager();
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
     * Creates Kafka producer configuration using our custom config manager.
     */
    @Bean
    public ProducerFactory<String, String> isolatedProducerFactory() {
        try {
            String configFile = determineConfigFile();
            Properties kafkaConfig = kafkaConfigManager.loadConfigurationFromYaml(configFile);
            
            Map<String, Object> configProps = new HashMap<>();
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                           kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.ACKS_CONFIG, "1");
            configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
            configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
            configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
            configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
            
            // Add security configuration if needed
            String securityProtocol = kafkaConfig.getProperty("KAFKA_SECURITY_PROTOCOL");
            if (securityProtocol != null && !securityProtocol.equals("PLAINTEXT")) {
                configProps.put("security.protocol", securityProtocol);
                
                String saslMechanism = kafkaConfig.getProperty("KAFKA_SASL_MECHANISM");
                if (saslMechanism != null) {
                    configProps.put("sasl.mechanism", saslMechanism);
                }
                
                String jaasConfig = kafkaConfig.getProperty("KAFKA_SASL_JAAS_CONFIG");
                if (jaasConfig != null) {
                    configProps.put("sasl.jaas.config", jaasConfig);
                }
            }
            
            logger.info("Configured Kafka producer for isolated environment with servers: {}", 
                       kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            
            return new DefaultKafkaProducerFactory<>(configProps);
            
        } catch (KafkaConfigurationException e) {
            logger.error("Failed to configure Kafka producer: {}", e.getMessage());
            // Fallback to default Spring Boot configuration
            return new DefaultKafkaProducerFactory<>(getDefaultProducerConfig());
        }
    }
    
    /**
     * Creates Kafka consumer configuration using our custom config manager.
     */
    @Bean
    public ConsumerFactory<String, String> isolatedConsumerFactory() {
        try {
            String configFile = determineConfigFile();
            Properties kafkaConfig = kafkaConfigManager.loadConfigurationFromYaml(configFile);
            
            Map<String, Object> configProps = new HashMap<>();
            configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                           kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            configProps.put(ConsumerConfig.GROUP_ID_CONFIG, 
                           kafkaConfig.getProperty("KAFKA_TEST_CONSUMER_GROUP", "event-streaming-consumer-isolated"));
            configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
            configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
            configProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);
            configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
            configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
            
            // Add security configuration if needed
            String securityProtocol = kafkaConfig.getProperty("KAFKA_SECURITY_PROTOCOL");
            if (securityProtocol != null && !securityProtocol.equals("PLAINTEXT")) {
                configProps.put("security.protocol", securityProtocol);
                
                String saslMechanism = kafkaConfig.getProperty("KAFKA_SASL_MECHANISM");
                if (saslMechanism != null) {
                    configProps.put("sasl.mechanism", saslMechanism);
                }
                
                String jaasConfig = kafkaConfig.getProperty("KAFKA_SASL_JAAS_CONFIG");
                if (jaasConfig != null) {
                    configProps.put("sasl.jaas.config", jaasConfig);
                }
            }
            
            logger.info("Configured Kafka consumer for isolated environment with servers: {}", 
                       kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            
            return new DefaultKafkaConsumerFactory<>(configProps);
            
        } catch (KafkaConfigurationException e) {
            logger.error("Failed to configure Kafka consumer: {}", e.getMessage());
            // Fallback to default Spring Boot configuration
            return new DefaultKafkaConsumerFactory<>(getDefaultConsumerConfig());
        }
    }
    
    /**
     * Creates KafkaTemplate for sending messages.
     */
    @Bean
    public KafkaTemplate<String, String> isolatedKafkaTemplate() {
        return new KafkaTemplate<>(isolatedProducerFactory());
    }
    
    /**
     * Creates KafkaTemplate for CommunicationEvent objects (for compatibility with EventProducerService).
     */
    @Bean
    public KafkaTemplate<String, CommunicationEvent> kafkaTemplate() {
        return new KafkaTemplate<>(isolatedCommunicationEventProducerFactory());
    }
    
    /**
     * Creates generic KafkaTemplate for Object types (for compatibility with AggregationNotificationPublisher).
     */
    @Bean
    public KafkaTemplate<String, Object> genericKafkaTemplate() {
        return new KafkaTemplate<>(isolatedGenericProducerFactory());
    }
    
    /**
     * Generic producer factory for various object types.
     */
    @Bean
    public ProducerFactory<String, Object> isolatedGenericProducerFactory() {
        try {
            String configFile = determineConfigFile();
            Properties kafkaConfig = kafkaConfigManager.loadConfigurationFromYaml(configFile);
            
            Map<String, Object> configProps = new HashMap<>();
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                           kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonSerializer");
            configProps.put(ProducerConfig.ACKS_CONFIG, "1");
            configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
            configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
            configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
            configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
            
            // Add security configuration if needed
            String securityProtocol = kafkaConfig.getProperty("KAFKA_SECURITY_PROTOCOL");
            if (securityProtocol != null && !securityProtocol.equals("PLAINTEXT")) {
                configProps.put("security.protocol", securityProtocol);
                
                String saslMechanism = kafkaConfig.getProperty("KAFKA_SASL_MECHANISM");
                if (saslMechanism != null) {
                    configProps.put("sasl.mechanism", saslMechanism);
                }
                
                String jaasConfig = kafkaConfig.getProperty("KAFKA_SASL_JAAS_CONFIG");
                if (jaasConfig != null) {
                    configProps.put("sasl.jaas.config", jaasConfig);
                }
            }
            
            logger.info("Configured generic Kafka producer for isolated environment with servers: {}", 
                       kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            
            return new DefaultKafkaProducerFactory<>(configProps);
            
        } catch (KafkaConfigurationException e) {
            logger.error("Failed to configure generic Kafka producer: {}", e.getMessage());
            // Fallback to default Spring Boot configuration
            return new DefaultKafkaProducerFactory<>(getDefaultGenericProducerConfig());
        }
    }
    
    /**
     * Producer factory for CommunicationEvent objects.
     */
    @Bean
    public ProducerFactory<String, CommunicationEvent> isolatedCommunicationEventProducerFactory() {
        try {
            String configFile = determineConfigFile();
            Properties kafkaConfig = kafkaConfigManager.loadConfigurationFromYaml(configFile);
            
            Map<String, Object> configProps = new HashMap<>();
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                           kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonSerializer");
            configProps.put(ProducerConfig.ACKS_CONFIG, "1");
            configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
            configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
            configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
            configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
            
            // Add security configuration if needed
            String securityProtocol = kafkaConfig.getProperty("KAFKA_SECURITY_PROTOCOL");
            if (securityProtocol != null && !securityProtocol.equals("PLAINTEXT")) {
                configProps.put("security.protocol", securityProtocol);
                
                String saslMechanism = kafkaConfig.getProperty("KAFKA_SASL_MECHANISM");
                if (saslMechanism != null) {
                    configProps.put("sasl.mechanism", saslMechanism);
                }
                
                String jaasConfig = kafkaConfig.getProperty("KAFKA_SASL_JAAS_CONFIG");
                if (jaasConfig != null) {
                    configProps.put("sasl.jaas.config", jaasConfig);
                }
            }
            
            logger.info("Configured generic Kafka producer for isolated environment with servers: {}", 
                       kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            
            return new DefaultKafkaProducerFactory<>(configProps);
            
        } catch (KafkaConfigurationException e) {
            logger.error("Failed to configure generic Kafka producer: {}", e.getMessage());
            // Fallback to default Spring Boot configuration
            return new DefaultKafkaProducerFactory<>(getDefaultGenericProducerConfig());
        }
    }
    
    /**
     * Fallback CommunicationEvent producer configuration using Spring Boot defaults.
     */
    private Map<String, Object> getDefaultGenericProducerConfig() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonSerializer");
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        logger.warn("Using fallback generic Kafka producer configuration");
        return configProps;
    }
    
    /**
     * Creates Kafka listener container factory for @KafkaListener annotations.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> isolatedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(isolatedConsumerFactory());
        
        // Configure container properties
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setSyncCommits(true);
        
        // Set concurrency for better performance
        factory.setConcurrency(2);
        
        logger.info("Configured Kafka listener container factory for isolated environment");
        
        return factory;
    }
    
    /**
     * Fallback producer configuration using Spring Boot defaults.
     */
    private Map<String, Object> getDefaultProducerConfig() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        logger.warn("Using fallback Kafka producer configuration");
        return configProps;
    }
    
    /**
     * Fallback consumer configuration using Spring Boot defaults.
     */
    private Map<String, Object> getDefaultConsumerConfig() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "event-streaming-consumer-isolated");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        
        logger.warn("Using fallback Kafka consumer configuration");
        return configProps;
    }
}