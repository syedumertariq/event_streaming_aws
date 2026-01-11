package com.eventstreaming.model;

/**
 * Enumeration representing the contactable status of a user as determined by Walker.
 * This status influences whether the user should be contacted through various channels.
 */
public enum ContactableStatus {
    /**
     * User is contactable and can receive communications
     */
    CONTACTABLE,
    
    /**
     * User is not contactable and should not receive communications
     */
    NOT_CONTACTABLE,
    
    /**
     * User's contactable status is pending review by Walker
     */
    PENDING_REVIEW,
    
    /**
     * User has been suppressed from communications
     */
    SUPPRESSED,
    
    /**
     * User has opted out of communications
     */
    OPTED_OUT
}