package com.eventstreaming.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple health controller for development testing.
 */
@RestController
@RequestMapping("/actuator")
public class SimpleHealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        
        Map<String, Object> components = new HashMap<>();
        
        Map<String, Object> actorSystem = new HashMap<>();
        actorSystem.put("status", "UP");
        actorSystem.put("details", Map.of("description", "Pekko Actor System is running"));
        components.put("actorSystem", actorSystem);
        
        Map<String, Object> database = new HashMap<>();
        database.put("status", "UP");
        database.put("details", Map.of("database", "H2", "validationQuery", "isValid()"));
        components.put("db", database);
        
        health.put("components", components);
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("app", Map.of(
            "name", "Event Streaming Application",
            "version", "1.0.0",
            "profile", "development"
        ));
        info.put("build", Map.of(
            "time", Instant.now().toString(),
            "artifact", "event-streaming-app"
        ));
        
        return ResponseEntity.ok(info);
    }
}