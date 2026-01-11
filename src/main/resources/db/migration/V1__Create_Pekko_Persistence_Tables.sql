-- Pekko Persistence JDBC Schema for MySQL
-- This script creates the necessary tables for Pekko Persistence with MySQL backend

-- Journal table for event sourcing
CREATE TABLE IF NOT EXISTS journal (
    ordering BIGINT NOT NULL AUTO_INCREMENT,
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,
    tags VARCHAR(255) DEFAULT NULL,
    message LONGBLOB NOT NULL,
    created TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    
    PRIMARY KEY (ordering),
    UNIQUE KEY journal_persistence_id_sequence_number_idx (persistence_id, sequence_number),
    KEY journal_persistence_id_idx (persistence_id),
    KEY journal_sequence_number_idx (sequence_number),
    KEY journal_created_idx (created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Snapshot table for state snapshots
CREATE TABLE IF NOT EXISTS snapshot (
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    created TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    snapshot LONGBLOB NOT NULL,
    
    PRIMARY KEY (persistence_id, sequence_number),
    KEY snapshot_created_idx (created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Durable state table for Pekko Persistence state store
CREATE TABLE IF NOT EXISTS durable_state (
    persistence_id VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    state_payload LONGBLOB NOT NULL,
    state_ser_id INTEGER NOT NULL,
    state_ser_manifest VARCHAR(255) NOT NULL,
    tag VARCHAR(255) DEFAULT NULL,
    created TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    
    PRIMARY KEY (persistence_id),
    KEY durable_state_revision_idx (revision),
    KEY durable_state_created_idx (created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Projection offset table for Pekko Projections
CREATE TABLE IF NOT EXISTS projection_offset_store (
    projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    current_offset VARCHAR(255) NOT NULL,
    manifest VARCHAR(32) NOT NULL,
    mergeable BOOLEAN NOT NULL,
    last_updated TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL,
    
    PRIMARY KEY (projection_name, projection_key),
    KEY projection_offset_last_updated_idx (last_updated)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Projection management table
CREATE TABLE IF NOT EXISTS projection_management (
    projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    paused BOOLEAN NOT NULL,
    last_updated TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL,
    
    PRIMARY KEY (projection_name, projection_key),
    KEY projection_management_last_updated_idx (last_updated)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Application-specific tables

-- User aggregations read model table
CREATE TABLE IF NOT EXISTS user_aggregations (
    user_id VARCHAR(255) NOT NULL,
    email_opens BIGINT DEFAULT 0 NOT NULL,
    email_clicks BIGINT DEFAULT 0 NOT NULL,
    sms_replies BIGINT DEFAULT 0 NOT NULL,
    sms_deliveries BIGINT DEFAULT 0 NOT NULL,
    calls_completed BIGINT DEFAULT 0 NOT NULL,
    calls_missed BIGINT DEFAULT 0 NOT NULL,
    last_activity TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    email_open_rate DECIMAL(5,4) DEFAULT 0.0000 NOT NULL,
    sms_response_rate DECIMAL(5,4) DEFAULT 0.0000 NOT NULL,
    call_completion_rate DECIMAL(5,4) DEFAULT 0.0000 NOT NULL,
    contactable_status ENUM('CONTACTABLE', 'NOT_CONTACTABLE', 'PENDING_REVIEW', 'SUPPRESSED', 'OPTED_OUT') DEFAULT 'PENDING_REVIEW',
    current_graph_node VARCHAR(255) DEFAULT NULL,
    last_walker_processing TIMESTAMP(6) DEFAULT NULL,
    walker_processing_count BIGINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL,
    
    PRIMARY KEY (user_id),
    KEY user_aggregations_last_activity_idx (last_activity),
    KEY user_aggregations_contactable_status_idx (contactable_status),
    KEY user_aggregations_updated_at_idx (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Event processing metrics table
CREATE TABLE IF NOT EXISTS event_processing_metrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(255) NOT NULL,
    event_type ENUM('EMAIL_OPEN', 'EMAIL_CLICK', 'SMS_REPLY', 'SMS_DELIVERY', 'CALL_COMPLETED', 'CALL_MISSED') NOT NULL,
    processing_time_ms BIGINT NOT NULL,
    processed_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    
    PRIMARY KEY (id),
    KEY event_metrics_user_id_idx (user_id),
    KEY event_metrics_event_type_idx (event_type),
    KEY event_metrics_processed_at_idx (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for performance optimization
CREATE INDEX journal_persistence_id_sequence_number_deleted_idx ON journal (persistence_id, sequence_number, deleted);
CREATE INDEX journal_tags_idx ON journal (tags);

-- Add comments for documentation
ALTER TABLE journal COMMENT = 'Pekko Persistence journal table for event sourcing';
ALTER TABLE snapshot COMMENT = 'Pekko Persistence snapshot table for state snapshots';
ALTER TABLE durable_state COMMENT = 'Pekko Persistence durable state table';
ALTER TABLE projection_offset_store COMMENT = 'Pekko Projection offset tracking table';
ALTER TABLE projection_management COMMENT = 'Pekko Projection management table';
ALTER TABLE user_aggregations COMMENT = 'User communication event aggregations read model';
ALTER TABLE event_processing_metrics COMMENT = 'Event processing performance metrics';