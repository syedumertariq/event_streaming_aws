package com.eventstreaming.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Environment-Aware Pekko Configuration
 * - Local development: Multi-node cluster (127.0.0.1:2551,2552,2553)
 * - AWS deployment: Single-node cluster (AWS private IP:2551)
 */
@Configuration(proxyBeanMethods = false)
public class PekkoConfig {
    
    @Autowired
    private Environment environment;
    
    // CRITICAL DIAGNOSTIC LOGGING - CONSTRUCTOR
    public PekkoConfig() {
        System.out.println("\n" + "🎯".repeat(100));
        System.out.println("🎯🎯🎯 PEKKOCONFIG.JAVA CONSTRUCTOR CALLED 🎯🎯🎯");
        System.out.println("🎯🎯🎯 SPRING IS CREATING PEKKOCONFIG BEAN 🎯🎯🎯");
        System.out.println("🎯".repeat(100) + "\n");
    }
    
    /**
     * Gets configuration value from system properties first, then environment variables, then default.
     * This allows runtime -D parameters to override environment variables.
     */
    private String getConfigValue(String key, String defaultValue) {
        // First check system properties (set by -D parameters)
        String systemProperty = System.getProperty(key);
        if (systemProperty != null && !systemProperty.trim().isEmpty()) {
            System.out.println("  🔧 Using system property " + key + " = " + systemProperty);
            return systemProperty;
        }
        
        // Then check environment variables
        String envVariable = System.getenv(key);
        if (envVariable != null && !envVariable.trim().isEmpty()) {
            System.out.println("  🌐 Using environment variable " + key + " = " + envVariable);
            return envVariable;
        }
        
        // Finally use default
        System.out.println("  📋 Using default " + key + " = " + defaultValue);
        return defaultValue;
    }
    
    @Bean
    public ActorSystem<Void> actorSystem() {
        // CRITICAL DIAGNOSTIC LOGGING - EASILY VISIBLE
        System.out.println("\n" + "🔥".repeat(100));
        System.out.println("🔥🔥🔥 PEKKOCONFIG.JAVA ACTORSYSTEM() METHOD CALLED 🔥🔥🔥");
        System.out.println("🔥🔥🔥 THIS PROVES PEKKOCONFIG.JAVA IS BEING USED 🔥🔥🔥");
        System.out.println("🔥".repeat(100));
        
        Config config = createMinimalConfig();
        
        System.out.println("🔥🔥🔥 PEKKOCONFIG.JAVA CREATING ACTORSYSTEM WITH PROGRAMMATIC CONFIG 🔥🔥🔥");
        System.out.println("🔥".repeat(100) + "\n");
        
        return ActorSystem.create(
            Behaviors.setup(context -> {
                context.getLog().info("Event Streaming ActorSystem started successfully");
                return Behaviors.empty();
            }),
            "EventStreamingSystem",
            config
        );
    }
    
    private Config createMinimalConfig() {
        // CRITICAL DIAGNOSTIC LOGGING - EASILY VISIBLE
        System.out.println("\n" + "🚀".repeat(100));
        System.out.println("🚀🚀🚀 PEKKOCONFIG.JAVA CREATEMINIMALCONFIG() METHOD CALLED 🚀🚀🚀");
        System.out.println("🚀🚀🚀 THIS IS WHERE PROGRAMMATIC PEKKO CONFIG IS CREATED 🚀🚀🚀");
        System.out.println("🚀".repeat(100));
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔧 ENVIRONMENT-AWARE PEKKO CONFIGURATION");
        System.out.println("=".repeat(60));
        
        // Check active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isIsolatedProfile = java.util.Arrays.asList(activeProfiles).contains("isolated");
        boolean isClusterMysqlProfile = java.util.Arrays.asList(activeProfiles).contains("cluster-mysql") ||
                                       java.util.Arrays.asList(activeProfiles).contains("cluster-test-node1") ||
                                       java.util.Arrays.asList(activeProfiles).contains("cluster-test-node2") ||
                                       java.util.Arrays.asList(activeProfiles).contains("cluster-test-node3");
        
        // Get port configuration from Spring environment (YAML files) with fallback to system properties
        String clusterPort = environment.getProperty("pekko.remote.artery.canonical.port", 
                            System.getProperty("pekko.remote.artery.canonical.port", "2551"));
        String clusterHost = environment.getProperty("pekko.remote.artery.canonical.hostname",
                            System.getProperty("pekko.remote.artery.canonical.hostname", "127.0.0.1"));
        
        // Environment detection for seed nodes
        boolean isAwsEnvironment = System.getenv("AWS_NODE_IP") != null || 
                                 System.getenv("MYSQL_HOST") != null ||
                                 !clusterHost.equals("127.0.0.1");
        
        // Determine seed nodes configuration
        String seedNodesConfig;
        String seedNodeOverride = System.getProperty("pekko.cluster.seed-nodes.0");
        
        if (seedNodeOverride != null) {
            // Use single seed node for bootstrap (Node 1 standalone start)
            seedNodesConfig = "    seed-nodes.0 = \"" + seedNodeOverride + "\"\n";
            System.out.println("  🌱 Seed Node (Bootstrap): " + seedNodeOverride);
        } else if (isAwsEnvironment) {
            // AWS: Use single seed node
            String awsNodeIp = System.getenv().getOrDefault("AWS_NODE_IP", clusterHost);
            if (awsNodeIp.isEmpty()) {
                awsNodeIp = "127.0.0.1"; // fallback
            }
            seedNodesConfig = "    seed-nodes = [\n" +
                            "      \"pekko://EventStreamingSystem@" + awsNodeIp + ":2551\"\n" +
                            "    ]\n";
            System.out.println("  🌱 Seed Nodes (AWS Single): " + awsNodeIp + ":2551");
        } else {
            // Local: Use all 3 seed nodes for multi-node cluster
            seedNodesConfig = "    seed-nodes = [\n" +
                            "      \"pekko://EventStreamingSystem@127.0.0.1:2551\",\n" +
                            "      \"pekko://EventStreamingSystem@127.0.0.1:2552\",\n" +
                            "      \"pekko://EventStreamingSystem@127.0.0.1:2553\"\n" +
                            "    ]\n";
            System.out.println("  🌱 Seed Nodes (Local Multi): 2551, 2552, 2553");
        }
        
        System.out.println("📊 CONFIGURATION:");
        System.out.println("  🔧 Profile: " + (isIsolatedProfile ? "isolated" : "default"));
        System.out.println("  🔧 Active Profiles: " + java.util.Arrays.toString(activeProfiles));
        System.out.println("  🌐 Environment: " + (isAwsEnvironment ? "AWS" : "Local"));
        System.out.println("  🌐 Cluster Host: " + clusterHost);
        System.out.println("  🌐 Cluster Port: " + clusterPort);
        
        // Enhanced HOCON config with serialization, debugging, and persistence settings
        String minimalConfig;
        
        if (isClusterMysqlProfile) {
            // Use MySQL/JDBC persistence for cluster-mysql profile
            System.out.println("🚀🚀🚀 CLUSTER-MYSQL PROFILE DETECTED - USING MYSQL/JDBC PERSISTENCE 🚀🚀🚀");
            System.out.println("  🗄️  Using MySQL/JDBC Persistence");
            
            // Get MySQL connection details from system properties (runtime -D params) or environment variables
            // Priority: System Properties (-D params) > Environment Variables > Defaults
            String mysqlHost = getConfigValue("MYSQL_HOST", 
                isAwsEnvironment ? "pekko-cluster-everest-mysql.caxgimygw4oy.us-east-1.rds.amazonaws.com" 
                                 : "questmysqldev01.quinstreet.net");
            String mysqlPort = getConfigValue("MYSQL_PORT", "3306");
            String mysqlDatabase = getConfigValue("MYSQL_DATABASE", 
                isAwsEnvironment ? "event_streaming_db" : "questks");
            String mysqlUsername = getConfigValue("MYSQL_USERNAME", 
                isAwsEnvironment ? "admin" : "questks_app");
            String mysqlPassword = getConfigValue("MYSQL_PASSWORD", 
                isAwsEnvironment ? System.getenv("DB_PASSWORD") : null);
            String mysqlUseSSL = getConfigValue("MYSQL_USE_SSL", "false");
            
            String jdbcUrl = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=%s&serverTimezone=UTC&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true",
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUseSSL
            );
            
            System.out.println("  🗄️  MySQL Configuration Sources:");
            System.out.println("  🗄️  MySQL URL: " + jdbcUrl);
            System.out.println("  🗄️  MySQL User: " + mysqlUsername);
            System.out.println("  🗄️  MySQL Host Source: " + (System.getProperty("MYSQL_HOST") != null ? "System Property (-D)" : 
                                                              System.getenv("MYSQL_HOST") != null ? "Environment Variable" : "Default"));
            
            System.out.println("🚀🚀🚀 PEKKOCONFIG.JAVA CREATING MYSQL JDBC CONFIG WITH event_journal TABLE 🚀🚀🚀");
            System.out.println("🚀🚀🚀 TABLE NAME WILL BE: event_journal (Pekko default) 🚀🚀🚀");
            
            minimalConfig = String.format(
                "pekko {\n" +
                "  loglevel = \"INFO\"\n" +
                "  jvm-exit-on-fatal-error = off\n" +
                "  actor {\n" +
                "    provider = cluster\n" +
                "    allow-java-serialization = on\n" +
                "    warn-about-java-serializer-usage = off\n" +
                "    serializers {\n" +
                "      java = \"org.apache.pekko.serialization.JavaSerializer\"\n" +
                "    }\n" +
                "    serialization-bindings {\n" +
                "      \"java.io.Serializable\" = java\n" +
                "    }\n" +
                "  }\n" +
                "  remote.artery {\n" +
                "    canonical.hostname = \"%s\"\n" +
                "    canonical.port = %s\n" +
                "    bind.hostname = \"0.0.0.0\"\n" +
                "    bind.port = %s\n" +
                "  }\n" +
                "  cluster {\n" +
                "%s" +
                "    downing-provider-class = \"org.apache.pekko.cluster.sbr.SplitBrainResolverProvider\"\n" +
                "    sharding {\n" +
                "      number-of-shards = 100\n" +
                "      guardian-name = sharding\n" +
                "      role = \"\"\n" +
                "      remember-entities = on\n" +
                "      remember-entities-store = \"eventsourced\"\n" +
                "    }\n" +
                "  }\n" +
                "  persistence {\n" +
                "    journal {\n" +
                "      plugin = \"jdbc-journal\"\n" +
                "      auto-start-journals = [\"jdbc-journal\"]\n" +
                "    }\n" +
                "    snapshot-store {\n" +
                "      plugin = \"jdbc-snapshot-store\"\n" +
                "      auto-start-snapshot-stores = [\"jdbc-snapshot-store\"]\n" +
                "    }\n" +
                "    state {\n" +
                "      plugin = \"jdbc-durable-state-store\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "jdbc-journal {\n" +
                "  slick {\n" +
                "    profile = \"slick.jdbc.MySQLProfile$\"\n" +
                "    db {\n" +
                "      url = \"%s\"\n" +
                "      user = \"%s\"\n" +
                "      password = \"%s\"\n" +
                "      driver = \"com.mysql.cj.jdbc.Driver\"\n" +
                "      connectionPool = \"HikariCP\"\n" +
                "      keepAliveConnection = true\n" +
                "      numThreads = 10\n" +
                "      maxConnections = 10\n" +
                "      minConnections = 2\n" +
                "    }\n" +
                "  }\n" +
                "  tables {\n" +
                "    journal {\n" +
                "      tableName = \"event_journal\"\n" +
                "      schemaName = \"\"\n" +
                "      columnNames {\n" +
                "        persistenceId = \"persistence_id\"\n" +
                "        sequenceNumber = \"sequence_number\"\n" +
                "        created = \"created\"\n" +
                "        tags = \"tags\"\n" +
                "        message = \"message\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "jdbc-snapshot-store {\n" +
                "  slick {\n" +
                "    profile = \"slick.jdbc.MySQLProfile$\"\n" +
                "    db {\n" +
                "      url = \"%s\"\n" +
                "      user = \"%s\"\n" +
                "      password = \"%s\"\n" +
                "      driver = \"com.mysql.cj.jdbc.Driver\"\n" +
                "      connectionPool = \"HikariCP\"\n" +
                "      keepAliveConnection = true\n" +
                "      numThreads = 5\n" +
                "      maxConnections = 5\n" +
                "      minConnections = 1\n" +
                "    }\n" +
                "  }\n" +
                "  tables {\n" +
                "    snapshot {\n" +
                "      tableName = \"snapshot\"\n" +
                "      schemaName = \"\"\n" +
                "      columnNames {\n" +
                "        persistenceId = \"persistence_id\"\n" +
                "        sequenceNumber = \"sequence_number\"\n" +
                "        created = \"created\"\n" +
                "        snapshot = \"snapshot\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "jdbc-durable-state-store {\n" +
                "  slick {\n" +
                "    profile = \"slick.jdbc.MySQLProfile$\"\n" +
                "    db {\n" +
                "      url = \"%s\"\n" +
                "      user = \"%s\"\n" +
                "      password = \"%s\"\n" +
                "      driver = \"com.mysql.cj.jdbc.Driver\"\n" +
                "      connectionPool = \"HikariCP\"\n" +
                "      keepAliveConnection = true\n" +
                "      numThreads = 5\n" +
                "      maxConnections = 5\n" +
                "      minConnections = 1\n" +
                "    }\n" +
                "  }\n" +
                "  tables {\n" +
                "    durable_state {\n" +
                "      tableName = \"durable_state\"\n" +
                "      schemaName = \"\"\n" +
                "      columnNames {\n" +
                "        persistenceId = \"persistence_id\"\n" +
                "        revision = \"revision\"\n" +
                "        statePayload = \"state_payload\"\n" +
                "        stateSerId = \"state_ser_id\"\n" +
                "        stateSerManifest = \"state_ser_manifest\"\n" +
                "        tag = \"tag\"\n" +
                "        created = \"created\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n",
                clusterHost, clusterPort, clusterPort, seedNodesConfig,
                jdbcUrl, mysqlUsername, mysqlPassword,
                jdbcUrl, mysqlUsername, mysqlPassword,
                jdbcUrl, mysqlUsername, mysqlPassword
            );
        } else if (isIsolatedProfile) {
            // Use in-memory persistence for isolated profile to avoid LevelDB issues
            minimalConfig = String.format(
                "pekko {\n" +
                "  loglevel = \"INFO\"\n" +
                "  jvm-exit-on-fatal-error = off\n" +
                "  actor {\n" +
                "    provider = cluster\n" +
                "    allow-java-serialization = on\n" +
                "    warn-about-java-serializer-usage = off\n" +
                "    serializers {\n" +
                "      java = \"org.apache.pekko.serialization.JavaSerializer\"\n" +
                "    }\n" +
                "    serialization-bindings {\n" +
                "      \"java.io.Serializable\" = java\n" +
                "      \"com.eventstreaming.cluster.PersistentUserActor$Command\" = java\n" +
                "      \"com.eventstreaming.cluster.PersistentUserActor$ProcessUserEvent\" = java\n" +
                "      \"com.eventstreaming.cluster.PersistentUserActor$GetUserStats\" = java\n" +
                "    }\n" +
                "  }\n" +
                "  remote.artery {\n" +
                "    canonical.hostname = \"%s\"\n" +
                "    canonical.port = %s\n" +
                "    bind.hostname = \"0.0.0.0\"\n" +
                "    bind.port = %s\n" +
                "  }\n" +
                "  cluster {\n" +
                "%s" +
                "    downing-provider-class = \"org.apache.pekko.cluster.sbr.SplitBrainResolverProvider\"\n" +
                "    sharding {\n" +
                "      number-of-shards = 10\n" +
                "      guardian-name = sharding\n" +
                "      role = \"\"\n" +
                "      remember-entities = off\n" +
                "    }\n" +
                "  }\n" +
                "  persistence {\n" +
                "    journal {\n" +
                "      plugin = \"pekko.persistence.journal.inmem\"\n" +
                "    }\n" +
                "    snapshot-store {\n" +
                "      plugin = \"pekko.persistence.snapshot-store.local\"\n" +
                "      local {\n" +
                "        dir = \"target/snapshots-isolated\"\n" +
                "      }\n" +
                "    }\n" +
                "    query {\n" +
                "      journal {\n" +
                "        inmem {\n" +
                "          class = \"org.apache.pekko.persistence.query.journal.inmem.InmemReadJournalProvider\"\n" +
                "          write-plugin = \"pekko.persistence.journal.inmem\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n",
                clusterHost, clusterPort, clusterPort, seedNodesConfig
            );
        } else {
            // Use LevelDB for non-isolated profiles
            minimalConfig = String.format(
                "pekko {\n" +
                "  loglevel = \"INFO\"\n" +
                "  jvm-exit-on-fatal-error = off\n" +
                "  actor {\n" +
                "    provider = cluster\n" +
                "    allow-java-serialization = on\n" +
                "    warn-about-java-serializer-usage = off\n" +
                "    serializers {\n" +
                "      java = \"org.apache.pekko.serialization.JavaSerializer\"\n" +
                "    }\n" +
                "    serialization-bindings {\n" +
                "      \"java.io.Serializable\" = java\n" +
                "      \"com.eventstreaming.cluster.PersistentUserActor$Command\" = java\n" +
                "      \"com.eventstreaming.cluster.PersistentUserActor$ProcessUserEvent\" = java\n" +
                "      \"com.eventstreaming.cluster.PersistentUserActor$GetUserStats\" = java\n" +
                "    }\n" +
                "  }\n" +
                "  remote.artery {\n" +
                "    canonical.hostname = \"%s\"\n" +
                "    canonical.port = %s\n" +
                "    bind.hostname = \"0.0.0.0\"\n" +
                "    bind.port = %s\n" +
                "  }\n" +
                "  cluster {\n" +
                "%s" +
                "    downing-provider-class = \"org.apache.pekko.cluster.sbr.SplitBrainResolverProvider\"\n" +
                "    sharding {\n" +
                "      number-of-shards = 100\n" +
                "      guardian-name = sharding\n" +
                "      role = \"\"\n" +
                "      remember-entities = off\n" +
                "    }\n" +
                "  }\n" +
                "  persistence {\n" +
                "    journal {\n" +
                "      plugin = \"pekko.persistence.journal.leveldb\"\n" +
                "      leveldb {\n" +
                "        dir = \"target/journal\"\n" +
                "        native = false\n" +
                "      }\n" +
                "    }\n" +
                "    snapshot-store {\n" +
                "      plugin = \"pekko.persistence.snapshot-store.local\"\n" +
                "      local {\n" +
                "        dir = \"target/snapshots\"\n" +
                "      }\n" +
                "    }\n" +
                "    query {\n" +
                "      journal {\n" +
                "        leveldb {\n" +
                "          class = \"org.apache.pekko.persistence.query.journal.leveldb.LeveldbReadJournalProvider\"\n" +
                "          write-plugin = \"pekko.persistence.journal.leveldb\"\n" +
                "          dir = \"target/journal\"\n" +
                "          native = false\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n",
                clusterHost, clusterPort, clusterPort, seedNodesConfig
            );
        }
        
        System.out.println("\n📝 ENVIRONMENT-AWARE CONFIG CREATED");
        System.out.println("✅ Local development: Multi-node cluster preserved");
        System.out.println("✅ AWS deployment: Single-node cluster configured");
        System.out.println("✅ Environment detection: Automatic");
        System.out.println("=".repeat(60) + "\n");
        
        // Parse ONLY this minimal config with NO fallbacks
        Config config = ConfigFactory.parseString(minimalConfig);
        
        return config;
    }
}