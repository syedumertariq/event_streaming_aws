package com.eventstreaming.validation;

/**
 * Enumeration of supported schema formats for event validation.
 */
public enum SchemaFormat {
    /**
     * JSON Schema format for JSON-based validation.
     */
    JSON_SCHEMA,
    
    /**
     * Apache Avro schema format for binary serialization.
     */
    AVRO,
    
    /**
     * Protocol Buffers schema format.
     */
    PROTOBUF
}