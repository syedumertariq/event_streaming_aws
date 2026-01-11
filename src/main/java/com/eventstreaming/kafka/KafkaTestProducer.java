package com.eventstreaming.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.Future;

/**
 * Kafka test producer for sending real-format messages to Pekko streaming service.
 * Sends messages in the exact format that your Pekko Kafka consumer expects.
 */
public class KafkaTestProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaTestProducer.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final KafkaConfigManager configManager;
    private KafkaProducer<String, String> producer;
    private String activeProfile = System.getProperty("spring.profiles.active", "isolated");
    
    public KafkaTestProducer(KafkaConfigManager configManager) {
        this.configManager = configManager;
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
     * Initialize the Kafka producer with configuration.
     */
    public void initialize() throws Exception {
        String configFile = determineConfigFile();
        Properties kafkaConfig = configManager.loadConfigurationFromYaml(configFile);
        Properties producerProps = createProducerProperties(kafkaConfig);
        
        this.producer = new KafkaProducer<>(producerProps);
        
        System.out.println("=".repeat(80));
        System.out.println("🚀 KAFKA TEST PRODUCER INITIALIZED");
        System.out.println("=".repeat(80));
        System.out.println("📡 Bootstrap Servers: " + kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
        System.out.println("⏰ Initialized at: " + LocalDateTime.now().format(TIME_FORMATTER));
        System.out.println("=".repeat(80));
    }
    
    /**
     * Send a real-format Kafka message to the specified topic.
     * 
     * @param topic The Kafka topic to send to
     * @param key The message key (user hash)
     * @param value The message value (timestamp@userid:action:contactid:additional)
     */
    public void sendRealFormatMessage(String topic, String key, String value) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            
            System.out.println("📤 SENDING REAL FORMAT MESSAGE");
            System.out.println("├─ Topic: " + topic);
            System.out.println("├─ Key: " + key);
            System.out.println("├─ Value: " + value);
            System.out.println("└─ Time: " + LocalDateTime.now().format(TIME_FORMATTER));
            
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    logger.error("Failed to send message to topic: {}", topic, exception);
                    System.err.println("❌ Failed to send message: " + exception.getMessage());
                } else {
                    logger.info("Message sent successfully to topic: {} partition: {} offset: {}", 
                               metadata.topic(), metadata.partition(), metadata.offset());
                    System.out.println("✅ Message sent successfully!");
                    System.out.println("   Topic: " + metadata.topic());
                    System.out.println("   Partition: " + metadata.partition());
                    System.out.println("   Offset: " + metadata.offset());
                }
            });
            
            // Wait for the message to be sent
            RecordMetadata metadata = future.get();
            System.out.println("📨 Message delivered to partition " + metadata.partition() + " at offset " + metadata.offset());
            
        } catch (Exception e) {
            logger.error("Error sending message to topic: {}", topic, e);
            System.err.println("❌ Error sending message: " + e.getMessage());
        }
    }
    
    /**
     * Send your exact test message format.
     */
    public void sendTestMessage() {
        String topic = "QuestIncrementerDropperINSCallQueueDev";
        String key = "aa806415340acc676593b725919ce29603e40aa1bb993f03057e8b962725a3eb";
        String value = "TS1761078201761@aa806415340acc676593b725919ce29603e40aa1bb993f03057e8b962725a3eb:DROP:84214:12951617384";
        
        System.out.println("🧪 SENDING YOUR EXACT TEST MESSAGE");
        sendRealFormatMessage(topic, key, value);
    }
    
    /**
     * Send multiple test messages with variations.
     */
    public void sendMultipleTestMessages(int count) {
        String[] topics = {
            "QuestIncrementerDropperINSCallQueueDev",
            "QuestIncrementerDropperINSDigitalQueueDev"
        };
        
        System.out.println("🔄 SENDING " + count + " TEST MESSAGES");
        
        for (int i = 1; i <= count; i++) {
            String topic = topics[i % topics.length];
            
            // Generate variations of your message format
            String userId = generateUserId(i);
            String timestamp = "TS" + (1761078201761L + i);
            String contactId = String.valueOf(84214 + i);
            String additional = String.valueOf(12951617384L + i);
            
            String key = userId;
            String value = timestamp + "@" + userId + ":DROP:" + contactId + ":" + additional;
            
            System.out.println("\n📤 Message " + i + "/" + count);
            sendRealFormatMessage(topic, key, value);
            
            // Small delay between messages
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("\n✅ Completed sending " + count + " test messages");
    }
    
    /**
     * Send messages with different event types.
     */
    public void sendDifferentEventTypes() {
        String[] eventTypes = {"DROP", "PICKUP", "DELIVERY", "CANCEL", "UPDATE"};
        String topic = "QuestIncrementerDropperINSCallQueueDev";
        
        System.out.println("🎭 SENDING MESSAGES WITH DIFFERENT EVENT TYPES");
        
        for (int i = 0; i < eventTypes.length; i++) {
            String eventType = eventTypes[i];
            String userId = generateUserId(i + 100);
            String timestamp = "TS" + (1761078201761L + i + 100);
            String contactId = String.valueOf(85000 + i);
            String additional = String.valueOf(12951617384L + i + 100);
            
            String key = userId;
            String value = timestamp + "@" + userId + ":" + eventType + ":" + contactId + ":" + additional;
            
            System.out.println("\n📤 Event Type: " + eventType);
            sendRealFormatMessage(topic, key, value);
            
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("\n✅ Completed sending different event types");
    }
    
    /**
     * Generate a user ID similar to your format.
     */
    private String generateUserId(int seed) {
        // Generate a hash-like string similar to your format
        String base = "aa806415340acc676593b725919ce29603e40aa1bb993f03057e8b962725a3eb";
        return base.substring(0, 60) + String.format("%04d", seed);
    }
    
    /**
     * Create producer properties from Kafka configuration.
     */
    private Properties createProducerProperties(Properties kafkaConfig) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                  kafkaConfig.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, "3");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "1");
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");
        
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
     * Close the producer.
     */
    public void close() {
        if (producer != null) {
            System.out.println("🔒 Closing Kafka producer...");
            producer.close();
            System.out.println("✅ Kafka producer closed");
        }
    }
    
    /**
     * Main method to run the test producer.
     */
    public static void main(String[] args) {
        KafkaConfigManager configManager = new KafkaConfigManager();
        KafkaTestProducer producer = new KafkaTestProducer(configManager);
        
        try {
            producer.initialize();
            
            if (args.length > 0) {
                String command = args[0].toLowerCase();
                switch (command) {
                    case "single":
                        producer.sendTestMessage();
                        break;
                    case "multiple":
                        int count = args.length > 1 ? Integer.parseInt(args[1]) : 5;
                        producer.sendMultipleTestMessages(count);
                        break;
                    case "types":
                        producer.sendDifferentEventTypes();
                        break;
                    default:
                        System.out.println("Usage: java KafkaTestProducer [single|multiple <count>|types]");
                        producer.sendTestMessage(); // Default to single message
                }
            } else {
                // Default: send your exact test message
                producer.sendTestMessage();
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error running Kafka test producer: " + e.getMessage());
            e.printStackTrace();
        } finally {
            producer.close();
        }
    }
}