package com.eventstreaming.config;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap;
import org.apache.pekko.management.javadsl.PekkoManagement;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import com.eventstreaming.cluster.ClusterUserActor;
import com.eventstreaming.cluster.PersistentUserActor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration(proxyBeanMethods = false)
@Profile({"cluster", "cluster-mysql", "aws-mysql", "default", "test", "isolated", "aws-simple", "cluster-test-node1", "cluster-test-node2", "cluster-test-node3"})
public class ClusterConfiguration {

    private PekkoManagement management;
    private ClusterBootstrap bootstrap;

    @Bean
    public Cluster cluster(ActorSystem<?> actorSystem) {
        Cluster cluster = Cluster.get(actorSystem);
        actorSystem.log().info("✅ Typed Cluster bean created successfully");
        return cluster;
    }

    @Bean
    public ClusterSharding clusterSharding(ActorSystem<?> actorSystem) {
        ClusterSharding sharding = ClusterSharding.get(actorSystem);
        
        // Initialize sharding immediately when the bean is created
        try {
            initializeSharding(sharding, actorSystem);
            actorSystem.log().info("✅ ClusterSharding bean created and initialized successfully");
        } catch (Exception e) {
            actorSystem.log().error("❌ Failed to initialize sharding: {}", e.getMessage(), e);
            throw e;
        }
        
        return sharding;
    }

    @PostConstruct
    public void initializeCluster() {
        // Management and bootstrap will be initialized after all beans are created
        // This is handled by the @EventListener method below
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        ActorSystem<?> actorSystem = event.getApplicationContext().getBean(ActorSystem.class);
        try {
            // Start Pekko Management for cluster monitoring
            management = PekkoManagement.get(actorSystem);
            management.start();

            // Start cluster bootstrap for automatic cluster formation
            bootstrap = ClusterBootstrap.get(actorSystem);
            bootstrap.start();

            actorSystem.log().info("✅ Cluster configuration initialized successfully");
        } catch (Exception e) {
            actorSystem.log().error("❌ Failed to initialize cluster: {}", e.getMessage(), e);
        }
    }

    private void initializeSharding(ClusterSharding sharding, ActorSystem<?> actorSystem) {
        // Initialize both regular and persistent user actors
        
        // Regular ClusterUserActor (for backward compatibility)
        EntityTypeKey<ClusterUserActor.Command> userEntityTypeKey = ClusterUserActor.ENTITY_TYPE_KEY;
        actorSystem.log().info("🔧 Initializing sharding for entity type: {}", userEntityTypeKey.name());
        sharding.init(Entity.of(userEntityTypeKey, entityContext -> {
            actorSystem.log().debug("Creating ClusterUserActor for entity: {}", entityContext.getEntityId());
            return ClusterUserActor.create(entityContext.getEntityId());
        }));
        
        // PersistentUserActor (for event sourcing)
        EntityTypeKey<PersistentUserActor.Command> persistentUserEntityTypeKey = PersistentUserActor.ENTITY_TYPE_KEY;
        actorSystem.log().info("🔧 Initializing sharding for persistent entity type: {}", persistentUserEntityTypeKey.name());
        sharding.init(Entity.of(persistentUserEntityTypeKey, entityContext -> {
            actorSystem.log().debug("Creating PersistentUserActor for entity: {}", entityContext.getEntityId());
            return PersistentUserActor.create(entityContext.getEntityId());
        }));

        actorSystem.log().info("✅ Cluster sharding initialized for both UserActor and PersistentUserActor entities");
    }

    @PreDestroy
    public void shutdown() {
        // Note: ClusterBootstrap and PekkoManagement don't have explicit stop methods
        // They will be stopped when the actor system terminates
        System.out.println("Cluster configuration shutdown completed");
    }
}