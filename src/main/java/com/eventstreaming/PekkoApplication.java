package com.eventstreaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import com.eventstreaming.config.PekkoConfig;
import java.util.Arrays;
import java.util.Map;

/**
 * Minimal Pekko + Spring Boot Application
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.eventstreaming"}) // Explicit component scan
@EnableAutoConfiguration
@Import(PekkoConfig.class) // Force import PekkoConfig
public class PekkoApplication {

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🚀 EVENT STREAMING APPLICATION STARTUP DEBUG");
        System.out.println("=".repeat(80));
        
        // FORCE PEKKOCONFIG LOADING TEST
        System.out.println("\n🔥 TESTING PEKKOCONFIG LOADING MANUALLY:");
        try {
            com.eventstreaming.config.PekkoConfig testConfig = new com.eventstreaming.config.PekkoConfig();
            System.out.println("✅ PekkoConfig can be instantiated manually");
        } catch (Exception e) {
            System.out.println("❌ PekkoConfig instantiation failed: " + e.getMessage());
        }
        
        // Show command line arguments
        System.out.println("\n📋 Command Line Arguments:");
        if (args.length == 0) {
            System.out.println("  ❌ No arguments provided");
        } else {
            for (int i = 0; i < args.length; i++) {
                System.out.println("  [" + i + "] " + args[i]);
            }
        }
        
        // Show all system properties that might affect configuration
        System.out.println("\n🔧 Relevant System Properties:");
        System.getProperties().entrySet().stream()
            .filter(entry -> {
                String key = entry.getKey().toString().toLowerCase();
                return key.contains("server.port") || 
                       key.contains("spring.profiles") ||
                       key.contains("pekko") || 
                       key.contains("cluster") || 
                       key.contains("bind") || 
                       key.contains("seed") ||
                       key.contains("canonical");
            })
            .sorted((e1, e2) -> e1.getKey().toString().compareTo(e2.getKey().toString()))
            .forEach(entry -> System.out.println("  " + entry.getKey() + " = " + entry.getValue()));
        
        // Show environment variables that might affect configuration
        System.out.println("\n🌍 Relevant Environment Variables:");
        Map<String, String> env = System.getenv();
        env.entrySet().stream()
            .filter(entry -> {
                String key = entry.getKey().toLowerCase();
                return key.contains("server_port") || 
                       key.contains("spring_profiles") ||
                       key.contains("cluster") || 
                       key.contains("bind") || 
                       key.contains("pekko");
            })
            .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
            .forEach(entry -> System.out.println("  " + entry.getKey() + " = " + entry.getValue()));
        
        // Show Java version and runtime info
        System.out.println("\n☕ Java Runtime Information:");
        System.out.println("  Java Version: " + System.getProperty("java.version"));
        System.out.println("  Java Home: " + System.getProperty("java.home"));
        System.out.println("  Working Directory: " + System.getProperty("user.dir"));
        System.out.println("  Available Processors: " + Runtime.getRuntime().availableProcessors());
        
        System.out.println("\n🎯 Starting Spring Boot Application...");
        System.out.println("=".repeat(80) + "\n");
        
        // Create SpringApplication with debug listeners
        SpringApplication app = new SpringApplication(PekkoApplication.class);
        
        // Add application listener to see what beans are being created
        app.addListeners(event -> {
            System.out.println("🌱 Spring Event: " + event.getClass().getSimpleName());
            if (event.toString().contains("PekkoConfig")) {
                System.out.println("🎯🎯🎯 PEKKOCONFIG RELATED EVENT: " + event.toString() + " 🎯🎯🎯");
            }
        });
        
        app.run(args);
    }
}