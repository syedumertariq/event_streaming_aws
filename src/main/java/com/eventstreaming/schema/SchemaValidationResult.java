package com.eventstreaming.schema;

import java.util.List;
import java.util.Objects;

/**
 * Result of schema validation.
 */
public class SchemaValidationResult {
    
    private final boolean valid;
    private final List<String> errors;
    
    public SchemaValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }
    
    public static SchemaValidationResult valid() {
        return new SchemaValidationResult(true, List.of());
    }
    
    public static SchemaValidationResult invalid(List<String> errors) {
        return new SchemaValidationResult(false, errors);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaValidationResult that = (SchemaValidationResult) o;
        return valid == that.valid && Objects.equals(errors, that.errors);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(valid, errors);
    }
    
    @Override
    public String toString() {
        return "SchemaValidationResult{" +
               "valid=" + valid +
               ", errors=" + errors +
               '}';
    }
}