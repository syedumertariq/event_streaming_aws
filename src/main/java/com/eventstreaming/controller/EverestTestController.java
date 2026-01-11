package com.eventstreaming.controller;

import com.eventstreaming.model.MemoryOnlyUserAggregations;
import com.eventstreaming.model.UserAggregations;
import com.eventstreaming.service.EverestDataLoaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test controller for memory-only Everest integration.
 */
@RestController
@RequestMapping("/api/test/everest")
public class EverestTestController {

    private final EverestDataLoaderService everestDataLoader;

    @Autowired
    public EverestTestController(EverestDataLoaderService everestDataLoader) {
        this.everestDataLoader = everestDataLoader;
    }

    /**
     * Test endpoint to demonstrate Everest field access.
     */
    @GetMapping("/users/{userId}/demo")
    public ResponseEntity<Map<String, Object>> getEverestDemo(@PathVariable String userId) {

        // Create a mock base aggregations
        UserAggregations baseAggregations = new UserAggregations(userId);

        // Enhance with Everest data
        MemoryOnlyUserAggregations enhanced = everestDataLoader.enhanceWithEverestData(baseAggregations);

        Map<String, Object> demo = Map.of(
            "userId", userId,
            "totalPekkoEvents", enhanced.getTotalEvents(),
            "everestAge", enhanced.getEverestAge(),
            "everestState", enhanced.getEverestState(),
            "everestEmailDomain", enhanced.getEverestEmailDomain(),
            "everestSegment", enhanced.getEverestUserSegment(),
            "everestMailable", enhanced.isEverestMailable(),
            "hasEverestData", enhanced.hasEverestData(),
            "contactableStatus", enhanced.getContactableStatus()
        );

        return ResponseEntity.ok(demo);
    }

    /**
     * Get Everest cache statistics.
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(everestDataLoader.getCacheStats());
    }

    /**
     * Clear Everest cache.
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<String> clearCache() {
        everestDataLoader.clearCache();
        return ResponseEntity.ok("Cache cleared successfully");
    }
}
