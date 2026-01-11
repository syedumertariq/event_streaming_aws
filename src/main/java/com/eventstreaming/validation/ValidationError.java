package com.eventstreaming.validation;

import java.util.Objects;

/**
 * Represents a single validation error with field path and message.
 */
public class ValidationError {
    
    private final String fieldPath;
    private final String message;
    private final Object rejectedValue;
    private final String errorCode;
    
    public ValidationError(String fieldPath, String message, Object rejectedValue, String errorCode) {
        this.fieldPath = fieldPath;
        this.message = message;
        this.rejectedValue = rejectedValue;
        this.errorCode = errorCode;
    }
    
    public ValidationError(String fieldPath, String message) {
        this(fieldPath, message, null, null);
    }
    
    public String getFieldPath() {
        return fieldPath;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Object getRejectedValue() {
        return rejectedValue;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets a formatted error message including field path.
     * 
     * @return formatted error message
     */
    public String getFormattedMessage() {
        if (fieldPath != null && !fieldPath.isEmpty()) {
            return fieldPath + ": " + message;
        }
        return message;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationError that = (ValidationError) o;
        return Objects.equals(fieldPath, that.fieldPath) &&
               Objects.equals(message, that.message) &&
               Objects.equals(rejectedValue, that.rejectedValue) &&
               Objects.equals(errorCode, that.errorCode);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fieldPath, message, rejectedValue, errorCode);
    }
    
    @Override
    public String toString() {
        return "ValidationError{" +
               "fieldPath='" + fieldPath + '\'' +
               ", message='" + message + '\'' +
               ", rejectedValue=" + rejectedValue +
               ", errorCode='" + errorCode + '\'' +
               '}';
    }
}