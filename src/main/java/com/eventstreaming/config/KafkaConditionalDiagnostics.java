package com.eventstreaming.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PostConstruct;

/**
 * Diagnostic configuration to help determine if Kafka conditional logic is working.
 */
@Configuration
public class KafkaConditionalDiagnostics {

    @Autowired
    private Environment environment;

    @PostConstruct
    public void logKafkaPropertyStatus() {
        String kafkaEnabled = environment.getProperty("app.kafka.enabled");
        System.out.println("\n" + "🔍".repeat(100));
        System.out.println("🔍🔍🔍 KAFKA PROPERTY DIAGNOSTIC 🔍🔍🔍");
        System.out.println("🔍🔍🔍 app.kafka.enabled = " + kafkaEnabled + " 🔍🔍🔍");
        System.out.println("🔍🔍🔍 Active Profiles: " + String.join(",", environment.getActiveProfiles()) + " 🔍🔍🔍");
        System.out.println("🔍".repeat(100) + "\n");
    }

    @Configuration
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
    public static class KafkaEnabledDiagnostic {
        
        @PostConstruct
        public void logKafkaEnabled() {
            System.out.println("\n" + "✅".repeat(100));
            System.out.println("✅✅✅ KAFKA ENABLED DIAGNOSTIC TRIGGERED ✅✅✅");
            System.out.println("✅✅✅ app.kafka.enabled=true - KAFKA SERVICES WILL BE CREATED ✅✅✅");
            System.out.println("✅✅✅ THIS MEANS CONDITIONAL ANNOTATIONS ARE WORKING ✅✅✅");
            System.out.println("✅".repeat(100) + "\n");
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false")
    public static class KafkaDisabledDiagnostic {
        
        @PostConstruct
        public void logKafkaDisabled() {
            System.out.println("\n" + "❌".repeat(100));
            System.out.println("❌❌❌ KAFKA DISABLED DIAGNOSTIC TRIGGERED ❌❌❌");
            System.out.println("❌❌❌ app.kafka.enabled=false - KAFKA SERVICES WILL NOT BE CREATED ❌❌❌");
            System.out.println("❌❌❌ THIS MEANS CONDITIONAL ANNOTATIONS ARE WORKING CORRECTLY ❌❌❌");
            System.out.println("❌".repeat(100) + "\n");
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "app.kafka.enabled", matchIfMissing = true)
    public static class KafkaDefaultDiagnostic {
        
        @PostConstruct
        public void logKafkaDefault() {
            System.out.println("\n" + "🔶".repeat(100));
            System.out.println("🔶🔶🔶 KAFKA DEFAULT DIAGNOSTIC TRIGGERED 🔶🔶🔶");
            System.out.println("🔶🔶🔶 app.kafka.enabled NOT SET - USING DEFAULT BEHAVIOR 🔶🔶🔶");
            System.out.println("🔶🔶🔶 matchIfMissing=true MEANS KAFKA WILL BE ENABLED BY DEFAULT 🔶🔶🔶");
            System.out.println("🔶".repeat(100) + "\n");
        }
    }
}