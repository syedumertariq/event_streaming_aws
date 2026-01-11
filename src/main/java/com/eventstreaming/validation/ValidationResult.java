package com.eventstreaming.validation;

import java.util.List;
import java.util.Objects;

/**
 * Result of event validation containing success status and detailed error information.
 */
public class ValidationResult {
    
    private final boolean valid;
    private final List<ValidationError> errors;
    private final String schemaVersion;
    
    private ValidationResult(boolean valid, List<ValidationError> errors, String schemaVersion) {
        this.valid = valid;
        this.errors = errors != null ? List.copyOf(errors) : List.of();
        this.schemaVersion = schemaVersion;
    }
    
    /**
     * Creates a successful validation result.
     * 
     * @param schemaVersion the version of schema used for validation
     * @return successful validation result
     */
    public static ValidationResult success(String schemaVersion) {
        return new ValidationResult(true, List.of(), schemaVersion);
    }
    
    /**
     * Creates a failed validation result with errors.
     * 
     * @param errors list of validation errors
     * @param schemaVersion the version of schema used for validation
     * @return failed validation result
     */
    public static ValidationResult failure(List<ValidationError> errors, String schemaVersion) {
        return new ValidationResult(false, errors, schemaVersion);
    }
    
    /**
     * Creates a failed validation result with a single error.
     * 
     * @param error the validation error
     * @param schemaVersion the version of schema used for validation
     * @return failed validation result
     */
    public static ValidationResult failure(ValidationError error, String schemaVersion) {
        return new ValidationResult(false, List.of(error), schemaVersion);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<ValidationError> getErrors() {
        return errors;
    }
    
    public String getSchemaVersion() {
        return schemaVersion;
    }
    
    /**
     * Gets a formatted error message containing all validation errors.
     * 
     * @return formatted error message or empty string if valid
     */
    public String getErrorMessage() {
        if (valid) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder("Validation failed: ");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(errors.get(i).getMessage());
        }
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResult that = (ValidationResult) o;
        return valid == that.valid &&
               Objects.equals(errors, that.errors) &&
               Objects.equals(schemaVersion, that.schemaVersion);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(valid, errors, schemaVersion);
    }
    
    @Override
    public String toString() {
        return "ValidationResult{" +
               "valid=" + valid +
               ", errors=" + errors +
               ", schemaVersion='" + schemaVersion + '\'' +
               '}';
    }
}