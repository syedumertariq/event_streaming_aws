package com.eventstreaming.model;

/**
 * Enumeration of all supported event types.
 * Used for event routing and polymorphic deserialization.
 */
public enum EventType {
    // Email events
    EMAIL_OPEN,
    EMAIL_CLICK,
    EMAIL_BOUNCE,
    EMAIL_DELIVERY,
    
    // SMS events
    SMS_REPLY,
    SMS_DELIVERY,
    SMS_BOUNCE,
    SMS_CLICK,
    
    // Call events
    CALL_COMPLETED,
    CALL_MISSED,
    CALL_ANSWERED,
    CALL_BUSY,
    
    // User activity events
    LOGIN,
    LOGOUT,
    VIEW_PRODUCT,
    ADD_TO_CART,
    PURCHASE,
    
    // Delivery events
    DROP,
    PICKUP,
    DELIVERY,
    
    // System events
    HEALTH_CHECK,
    PEKKO_KAFKA_TEST
}