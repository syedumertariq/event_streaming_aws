package com.eventstreaming.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Manages loading and validation of Kafka configuration from private properties
 * files.
 * This component loads sensitive Kafka configuration from
 * local-config-private.properties
 * and validates that all required properties are present.
 */
@Component
public class KafkaConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfigManager.class);

    private static final String PRIVATE_CONFIG_FILE = "local-config-private.properties";

    // Required Kafka configuration properties
    private static final Set<String> REQUIRED_PROPERTIES = Set.of(
            "KAFKA_BOOTSTRAP_SERVERS",
            "KAFKA_SECURITY_PROTOCOL",
            "KAFKA_SASL_MECHANISM",
            "KAFKA_SASL_JAAS_CONFIG",
            "KAFKA_TEST_TOPIC");

    // Optional properties with defaults
    private static final String DEFAULT_CONSUMER_GROUP = "kafka-connectivity-test-group";
    private static final String DEFAULT_TIMEOUT_MS = "30000";

    private Properties kafkaProperties;
    private boolean configurationLoaded = false;

    /**
     * Loads Kafka configuration from the private properties file.
     * This method attempts to load from both classpath and file system.
     * 
     * @return Properties object containing Kafka configuration
     * @throws KafkaConfigurationException if configuration cannot be loaded or is
     *                                     invalid
     */
    public Properties loadConfiguration() throws KafkaConfigurationException {
        return loadConfiguration(PRIVATE_CONFIG_FILE);
    }

    /**
     * Loads Kafka configuration from the specified properties file.
     * This method attempts to load from both classpath and file system.
     * 
     * @param configFileName Name of the configuration file to load
     * @return Properties object containing Kafka configuration
     * @throws KafkaConfigurationException if configuration cannot be loaded or is
     *                                     invalid
     */
    public Properties loadConfiguration(String configFileName) throws KafkaConfigurationException {
        if (configurationLoaded && kafkaProperties != null) {
            return kafkaProperties;
        }

        kafkaProperties = new Properties();

        // Try to load from classpath first
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(configFileName)) {
            if (inputStream != null) {
                kafkaProperties.load(inputStream);
                logger.info("Loaded Kafka configuration from classpath: {}", configFileName);
            }
        } catch (IOException e) {
            logger.warn("Could not load configuration from classpath: {}", e.getMessage());
        }

        // If not found in classpath, try to load from file system
        if (kafkaProperties.isEmpty()) {
            try (FileInputStream fileInputStream = new FileInputStream(configFileName)) {
                kafkaProperties.load(fileInputStream);
                logger.info("Loaded Kafka configuration from file system: {}", configFileName);
            } catch (IOException e) {
                throw new KafkaConfigurationException(
                        "Could not load configuration file: " + configFileName +
                                ". Please ensure the file exists in classpath or current directory.",
                        e);
            }
        }

        // Validate the loaded configuration
        validateConfiguration(kafkaProperties);

        // Set default values for optional properties
        setDefaultValues(kafkaProperties);

        configurationLoaded = true;
        logger.info("Kafka configuration loaded and validated successfully");

        return kafkaProperties;
    }

    /**
     * Validates that all required Kafka properties are present and not empty.
     * 
     * @param properties Properties to validate
     * @throws KafkaConfigurationException if validation fails
     */
    private void validateConfiguration(Properties properties) throws KafkaConfigurationException {
        StringBuilder missingProperties = new StringBuilder();

        for (String requiredProperty : REQUIRED_PROPERTIES) {
            String value = properties.getProperty(requiredProperty);
            if (value == null || value.trim().isEmpty()) {
                if (missingProperties.length() > 0) {
                    missingProperties.append(", ");
                }
                missingProperties.append(requiredProperty);
            }
        }

        if (missingProperties.length() > 0) {
            throw new KafkaConfigurationException(
                    "Missing required Kafka configuration properties: " + missingProperties.toString() +
                            ". Please check your " + PRIVATE_CONFIG_FILE + " file.");
        }

        // Validate specific property formats
        validateBootstrapServers(properties.getProperty("KAFKA_BOOTSTRAP_SERVERS"));
        validateSecurityProtocol(properties.getProperty("KAFKA_SECURITY_PROTOCOL"));
        validateSaslMechanism(properties.getProperty("KAFKA_SASL_MECHANISM"));
    }

    /**
     * Sets default values for optional configuration properties.
     * 
     * @param properties Properties to set defaults for
     */
    private void setDefaultValues(Properties properties) {
        properties.putIfAbsent("KAFKA_TEST_CONSUMER_GROUP", DEFAULT_CONSUMER_GROUP);
        properties.putIfAbsent("KAFKA_TEST_TIMEOUT_MS", DEFAULT_TIMEOUT_MS);

        logger.debug("Applied default values for optional properties");
    }

    /**
     * Validates the format of bootstrap servers property.
     * 
     * @param bootstrapServers Bootstrap servers string to validate
     * @throws KafkaConfigurationException if format is invalid
     */
    private void validateBootstrapServers(String bootstrapServers) throws KafkaConfigurationException {
        if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
            return; // Already handled by required property validation
        }

        String[] servers = bootstrapServers.split(",");
        for (String server : servers) {
            server = server.trim();
            if (!server.contains(":")) {
                throw new KafkaConfigurationException(
                        "Invalid bootstrap server format: " + server +
                                ". Expected format: hostname:port");
            }

            String[] parts = server.split(":");
            if (parts.length != 2) {
                throw new KafkaConfigurationException(
                        "Invalid bootstrap server format: " + server +
                                ". Expected format: hostname:port");
            }

            try {
                int port = Integer.parseInt(parts[1]);
                if (port <= 0 || port > 65535) {
                    throw new KafkaConfigurationException(
                            "Invalid port number in bootstrap server: " + server +
                                    ". Port must be between 1 and 65535");
                }
            } catch (NumberFormatException e) {
                throw new KafkaConfigurationException(
                        "Invalid port number in bootstrap server: " + server +
                                ". Port must be a valid integer");
            }
        }
    }

    /**
     * Validates the security protocol value.
     * 
     * @param securityProtocol Security protocol to validate
     * @throws KafkaConfigurationException if protocol is not supported
     */
    private void validateSecurityProtocol(String securityProtocol) throws KafkaConfigurationException {
        if (securityProtocol == null || securityProtocol.trim().isEmpty()) {
            return; // Already handled by required property validation
        }

        Set<String> validProtocols = Set.of("PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL");
        if (!validProtocols.contains(securityProtocol.toUpperCase())) {
            throw new KafkaConfigurationException(
                    "Invalid security protocol: " + securityProtocol +
                            ". Valid values are: " + validProtocols);
        }
    }

    /**
     * Validates the SASL mechanism value.
     * 
     * @param saslMechanism SASL mechanism to validate
     * @throws KafkaConfigurationException if mechanism is not supported
     */
    private void validateSaslMechanism(String saslMechanism) throws KafkaConfigurationException {
        if (saslMechanism == null || saslMechanism.trim().isEmpty()) {
            return; // Already handled by required property validation
        }

        Set<String> validMechanisms = Set.of("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512", "GSSAPI", "OAUTHBEARER");
        if (!validMechanisms.contains(saslMechanism.toUpperCase())) {
            throw new KafkaConfigurationException(
                    "Invalid SASL mechanism: " + saslMechanism +
                            ". Valid values are: " + validMechanisms);
        }
    }

    /**
     * Gets a specific configuration property value.
     * 
     * @param propertyName Name of the property to retrieve
     * @return Property value or null if not found
     * @throws KafkaConfigurationException if configuration is not loaded
     */
    public String getProperty(String propertyName) throws KafkaConfigurationException {
        if (!configurationLoaded || kafkaProperties == null) {
            throw new KafkaConfigurationException("Configuration not loaded. Call loadConfiguration() first.");
        }

        return kafkaProperties.getProperty(propertyName);
    }

    /**
     * Gets a specific configuration property value with a default.
     * 
     * @param propertyName Name of the property to retrieve
     * @param defaultValue Default value if property is not found
     * @return Property value or default value if not found
     * @throws KafkaConfigurationException if configuration is not loaded
     */
    public String getProperty(String propertyName, String defaultValue) throws KafkaConfigurationException {
        if (!configurationLoaded || kafkaProperties == null) {
            throw new KafkaConfigurationException("Configuration not loaded. Call loadConfiguration() first.");
        }

        return kafkaProperties.getProperty(propertyName, defaultValue);
    }

    /**
     * Checks if the configuration has been successfully loaded.
     * 
     * @return true if configuration is loaded, false otherwise
     */
    public boolean isConfigurationLoaded() {
        return configurationLoaded;
    }

    /**
     * Loads Kafka configuration from a YAML file (like
     * application-development.yml).
     * This method extracts Kafka-related properties from Spring Boot YAML
     * configuration.
     * 
     * @param yamlFilePath Path to the YAML configuration file
     * @return Properties object containing Kafka configuration
     * @throws KafkaConfigurationException if configuration cannot be loaded or is
     *                                     invalid
     */
    public Properties loadConfigurationFromYaml(String yamlFilePath) throws KafkaConfigurationException {
        kafkaProperties = new Properties();

        try (InputStream inputStream = new FileInputStream(yamlFilePath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlData = yaml.load(inputStream);

            // Extract Kafka configuration from Spring Boot YAML structure
            extractKafkaPropertiesFromYaml(yamlData);

            logger.info("Loaded Kafka configuration from YAML file: {}", yamlFilePath);

        } catch (IOException e) {
            throw new KafkaConfigurationException(
                    "Could not load YAML configuration file: " + yamlFilePath, e);
        } catch (Exception e) {
            throw new KafkaConfigurationException(
                    "Error parsing YAML configuration: " + e.getMessage(), e);
        }

        // Convert Spring Boot properties to our expected format
        convertSpringBootPropertiesToStandardFormat();

        // Validate the loaded configuration
        validateConfiguration(kafkaProperties);

        // Set default values for optional properties
        setDefaultValues(kafkaProperties);

        configurationLoaded = true;
        logger.info("Kafka configuration loaded and validated successfully from YAML");

        return kafkaProperties;
    }

    /**
     * Extracts Kafka properties from the parsed YAML data structure.
     * 
     * @param yamlData Parsed YAML data as a Map
     */
    @SuppressWarnings("unchecked")
    private void extractKafkaPropertiesFromYaml(Map<String, Object> yamlData) {
        // Navigate through the YAML structure: spring -> kafka
        Object springObj = yamlData.get("spring");
        if (springObj instanceof Map) {
            Map<String, Object> springConfig = (Map<String, Object>) springObj;
            Object kafkaObj = springConfig.get("kafka");

            if (kafkaObj instanceof Map) {
                Map<String, Object> kafkaConfig = (Map<String, Object>) kafkaObj;

                // Extract bootstrap servers
                Object bootstrapServers = kafkaConfig.get("bootstrap-servers");
                if (bootstrapServers != null) {
                    String resolvedBootstrapServers = resolvePropertyPlaceholder(bootstrapServers.toString());
                    kafkaProperties.setProperty("spring.kafka.bootstrap-servers", resolvedBootstrapServers);
                }

                // Extract producer configuration
                Object producerObj = kafkaConfig.get("producer");
                if (producerObj instanceof Map) {
                    Map<String, Object> producerConfig = (Map<String, Object>) producerObj;
                    producerConfig.forEach((key, value) -> {
                        if (value != null) {
                            String resolvedValue = resolvePropertyPlaceholder(value.toString());
                            kafkaProperties.setProperty("spring.kafka.producer." + key, resolvedValue);
                        }
                    });
                }

                // Extract consumer configuration
                Object consumerObj = kafkaConfig.get("consumer");
                if (consumerObj instanceof Map) {
                    Map<String, Object> consumerConfig = (Map<String, Object>) consumerObj;
                    consumerConfig.forEach((key, value) -> {
                        if (value != null) {
                            String resolvedValue = resolvePropertyPlaceholder(value.toString());
                            kafkaProperties.setProperty("spring.kafka.consumer." + key, resolvedValue);
                        }
                    });
                }
            }
        }

        // Also check for environment variables in the YAML
        extractEnvironmentVariables();
    }

    /**
     * Resolves Spring Boot property placeholders like
     * ${PROPERTY_NAME:default_value}.
     * 
     * @param value The property value that may contain placeholders
     * @return The resolved value
     */
    private String resolvePropertyPlaceholder(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        // Handle ${PROPERTY_NAME:default_value} format
        if (value.startsWith("${") && value.endsWith("}")) {
            String placeholder = value.substring(2, value.length() - 1);
            String[] parts = placeholder.split(":", 2);
            String propertyName = parts[0];
            String defaultValue = parts.length > 1 ? parts[1] : "";

            // Check environment variable first
            String envValue = System.getenv(propertyName);
            if (envValue != null && !envValue.trim().isEmpty()) {
                return envValue;
            }

            // Check system property
            String systemValue = System.getProperty(propertyName);
            if (systemValue != null && !systemValue.trim().isEmpty()) {
                return systemValue;
            }

            // Return default value
            return defaultValue;
        }

        return value;
    }

    /**
     * Extracts environment variables that might be referenced in the YAML.
     */
    private void extractEnvironmentVariables() {
        // Check for common Kafka environment variables
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers != null) {
            kafkaProperties.setProperty("KAFKA_BOOTSTRAP_SERVERS", bootstrapServers);
        }

        String securityProtocol = System.getenv("KAFKA_SECURITY_PROTOCOL");
        if (securityProtocol != null) {
            kafkaProperties.setProperty("KAFKA_SECURITY_PROTOCOL", securityProtocol);
        }

        String saslMechanism = System.getenv("KAFKA_SASL_MECHANISM");
        if (saslMechanism != null) {
            kafkaProperties.setProperty("KAFKA_SASL_MECHANISM", saslMechanism);
        }

        String jaasConfig = System.getenv("KAFKA_SASL_JAAS_CONFIG");
        if (jaasConfig != null) {
            kafkaProperties.setProperty("KAFKA_SASL_JAAS_CONFIG", jaasConfig);
        }

        String testTopic = System.getenv("KAFKA_TEST_TOPIC");
        if (testTopic != null) {
            kafkaProperties.setProperty("KAFKA_TEST_TOPIC", testTopic);
        }
    }

    /**
     * Converts Spring Boot Kafka properties to our standard format for validation.
     */
    private void convertSpringBootPropertiesToStandardFormat() {
        // Convert spring.kafka.bootstrap-servers to KAFKA_BOOTSTRAP_SERVERS
        String springBootstrapServers = kafkaProperties.getProperty("spring.kafka.bootstrap-servers");
        if (springBootstrapServers != null) {
            kafkaProperties.setProperty("KAFKA_BOOTSTRAP_SERVERS", springBootstrapServers);
        }

        // Set default values for required properties if not present from environment
        if (!kafkaProperties.containsKey("KAFKA_SECURITY_PROTOCOL")) {
            kafkaProperties.setProperty("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT");
        }

        if (!kafkaProperties.containsKey("KAFKA_SASL_MECHANISM")) {
            kafkaProperties.setProperty("KAFKA_SASL_MECHANISM", "PLAIN");
        }

        if (!kafkaProperties.containsKey("KAFKA_SASL_JAAS_CONFIG")) {
            kafkaProperties.setProperty("KAFKA_SASL_JAAS_CONFIG",
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"\" password=\"\";");
        }

        if (!kafkaProperties.containsKey("KAFKA_TEST_TOPIC")) {
            kafkaProperties.setProperty("KAFKA_TEST_TOPIC", "QuestIncrementerDropperINSCallQueueDev");
        }

        // Extract consumer group from Spring Boot configuration
        String consumerGroup = kafkaProperties.getProperty("spring.kafka.consumer.group-id");
        if (consumerGroup != null) {
            kafkaProperties.setProperty("KAFKA_TEST_CONSUMER_GROUP", consumerGroup);
        }
    }

    /**
     * Reloads the configuration from the properties file.
     * This method clears the current configuration and loads it again.
     * 
     * @return Properties object containing reloaded Kafka configuration
     * @throws KafkaConfigurationException if configuration cannot be reloaded
     */
    public Properties reloadConfiguration() throws KafkaConfigurationException {
        configurationLoaded = false;
        kafkaProperties = null;
        return loadConfiguration();
    }
}