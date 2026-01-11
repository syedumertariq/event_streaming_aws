package com.eventstreaming.schema;

import java.util.List;
import java.util.Objects;

/**
 * Result of schema compatibility check.
 */
public class CompatibilityResult {
    
    private final boolean compatible;
    private final List<String> incompatibilityReasons;
    private final CompatibilityLevel level;
    
    public CompatibilityResult(boolean compatible, List<String> incompatibilityReasons, CompatibilityLevel level) {
        this.compatible = compatible;
        this.incompatibilityReasons = incompatibilityReasons != null ? List.copyOf(incompatibilityReasons) : List.of();
        this.level = level;
    }
    
    public static CompatibilityResult compatible(CompatibilityLevel level) {
        return new CompatibilityResult(true, List.of(), level);
    }
    
    public static CompatibilityResult incompatible(List<String> reasons) {
        return new CompatibilityResult(false, reasons, CompatibilityLevel.NONE);
    }
    
    public boolean isCompatible() {
        return compatible;
    }
    
    public List<String> getIncompatibilityReasons() {
        return incompatibilityReasons;
    }
    
    public CompatibilityLevel getLevel() {
        return level;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompatibilityResult that = (CompatibilityResult) o;
        return compatible == that.compatible &&
               Objects.equals(incompatibilityReasons, that.incompatibilityReasons) &&
               level == that.level;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(compatible, incompatibilityReasons, level);
    }
    
    @Override
    public String toString() {
        return "CompatibilityResult{" +
               "compatible=" + compatible +
               ", incompatibilityReasons=" + incompatibilityReasons +
               ", level=" + level +
               '}';
    }
    
    /**
     * Levels of schema compatibility.
     */
    public enum CompatibilityLevel {
        BACKWARD,      // New schema can read data written with old schema
        FORWARD,       // Old schema can read data written with new schema
        FULL,          // Both backward and forward compatible
        NONE           // No compatibility
    }
}