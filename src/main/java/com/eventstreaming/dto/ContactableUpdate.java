package com.eventstreaming.dto;

import com.eventstreaming.model.ContactableStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO for Walker to update contactable status of users
 */
public class ContactableUpdate {
    
    private final ContactableStatus contactableStatus;
    private final String graphNode;
    private final Instant processingTimestamp;
    
    @JsonCreator
    public ContactableUpdate(
            @JsonProperty("contactableStatus") ContactableStatus contactableStatus,
            @JsonProperty("graphNode") String graphNode,
            @JsonProperty("processingTimestamp") Instant processingTimestamp) {
        
        this.contactableStatus = contactableStatus;
        this.graphNode = graphNode;
        this.processingTimestamp = processingTimestamp != null ? processingTimestamp : Instant.now();
    }
    
    // Convenience constructor
    public ContactableUpdate(ContactableStatus contactableStatus, String graphNode) {
        this(contactableStatus, graphNode, Instant.now());
    }
    
    public ContactableStatus getContactableStatus() {
        return contactableStatus;
    }
    
    public String getGraphNode() {
        return graphNode;
    }
    
    public Instant getProcessingTimestamp() {
        return processingTimestamp;
    }
    
    @Override
    public String toString() {
        return String.format("ContactableUpdate{contactableStatus=%s, graphNode='%s', processingTimestamp=%s}",
                           contactableStatus, graphNode, processingTimestamp);
    }
}