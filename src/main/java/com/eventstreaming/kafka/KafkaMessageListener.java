package com.eventstreaming.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

/**
 * A simple Kafka message listener that continuously polls for messages
 * and displays them to the console.
 */
public class KafkaMessageListener {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageListener.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final KafkaConfigManager configManager;
    private volatile boolean running = true;
    
    public KafkaMessageListener(KafkaConfigManager configManager) {
        this.configManager = configManager;
    }
    
    /**
     * Starts listening for messages on the configured topic.
     * This method will block and continuously poll for messages.
     */
    public void startListening() {
        try {
            Properties kafkaConfig = configManager.loadConfigurationFromYaml("src/main/resources/application-development.yml");
            Properties consumerProps = createConsumerProperties(kafkaConfig);
            
            String testTopic = kafkaConfig.getProperty("KAFKA_TEST_TOPIC");
            String digitalTopic = "QuestIncrementerDropperINSDigitalQueueDev";
            String consumerGroup = kafkaConfig.getProperty("KAFKA_TEST_CONSUMER_GROUP");
            
            System.out.println("=".repeat(80));
            System.out.println("🎧 KAFKA MESSAGE LISTENER STARTED");
            System.out.println("=".repeat(80));
            System.out.println("📡 Bootstrap Servers: " + kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
            System.out.println("📋 Topics: " + testTopic + ", " + digitalTopic);
            System.out.println("👥 Consumer Group: " + consumerGroup);
            System.out.println("⏰ Started at: " + LocalDateTime.now().format(TIME_FORMATTER));
            System.out.println("=".repeat(80));
            System.out.println("🔍 Waiting for messages on both topics... (Press Ctrl+C to stop)");
            System.out.println();
            
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
                consumer.subscribe(Arrays.asList(testTopic, digitalTopic));
                
                long messageCount = 0;
                
                while (running) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        messageCount++;
                        displayMessage(record, messageCount);
                    }
                    
                    // Print a heartbeat every 30 seconds to show we're still listening
                    if (System.currentTimeMillis() % 30000 < 1000) {
                        System.out.println("💓 Still listening... (" + LocalDateTime.now().format(TIME_FORMATTER) + ")");
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error while consuming messages", e);
                System.err.println("❌ Error while consuming messages: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Failed to start Kafka listener", e);
            System.err.println("❌ Failed to start Kafka listener: " + e.getMessage());
        }
    }
    
    /**
     * Displays a received message in a formatted way.
     */
    private void displayMessage(ConsumerRecord<String, String> record, long messageCount) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        
        System.out.println("┌" + "─".repeat(78) + "┐");
        System.out.println("│ 📨 MESSAGE #" + messageCount + " RECEIVED");
        System.out.println("├" + "─".repeat(78) + "┤");
        System.out.println("│ ⏰ Timestamp: " + timestamp);
        System.out.println("│ 📋 Topic: " + record.topic());
        System.out.println("│ 🔢 Partition: " + record.partition());
        System.out.println("│ 📍 Offset: " + record.offset());
        System.out.println("│ 🔑 Key: " + (record.key() != null ? record.key() : "null"));
        System.out.println("│ 📄 Value: " + (record.value() != null ? record.value() : "null"));
        System.out.println("│ 📏 Value Length: " + (record.value() != null ? record.value().length() : 0) + " characters");
        
        // If the message is JSON-like, try to format it nicely
        if (record.value() != null && (record.value().startsWith("{") || record.value().startsWith("["))) {
            System.out.println("│ 📝 Formatted Value:");
            String[] lines = record.value().split("(?<=\\})|(?<=\\])|(?<=,)");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    System.out.println("│    " + line.trim());
                }
            }
        }
        
        System.out.println("└" + "─".repeat(78) + "┘");
        System.out.println();
    }
    
    /**
     * Creates consumer properties from Kafka configuration.
     */
    private Properties createConsumerProperties(Properties kafkaConfig) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                  kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, 
                  kafkaConfig.getProperty("KAFKA_TEST_CONSUMER_GROUP", "kafka-message-listener-group"));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // Only consume new messages
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1");
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "3000");
        
        String securityProtocol = kafkaConfig.getProperty("KAFKA_SECURITY_PROTOCOL");
        if (securityProtocol != null && !securityProtocol.equals("PLAINTEXT")) {
            props.put("security.protocol", securityProtocol);
            
            String saslMechanism = kafkaConfig.getProperty("KAFKA_SASL_MECHANISM");
            if (saslMechanism != null) {
                props.put("sasl.mechanism", saslMechanism);
            }
            
            String jaasConfig = kafkaConfig.getProperty("KAFKA_SASL_JAAS_CONFIG");
            if (jaasConfig != null) {
                props.put("sasl.jaas.config", jaasConfig);
            }
        }
        
        return props;
    }
    
    /**
     * Stops the message listener.
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Main method to run the message listener standalone.
     */
    public static void main(String[] args) {
        KafkaConfigManager configManager = new KafkaConfigManager();
        KafkaMessageListener listener = new KafkaMessageListener(configManager);
        
        // Add shutdown hook to gracefully stop the listener
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutdown signal received. Stopping listener...");
            listener.stop();
        }));
        
        listener.startListening();
    }
}