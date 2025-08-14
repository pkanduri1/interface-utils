-- Flyway migration script V1 - Initial Schema
-- This script creates the initial database schema for the File Watcher Service

-- Create schema
CREATE SCHEMA IF NOT EXISTS filewatcher;

-- Set search path
SET search_path TO filewatcher, public;

-- Create audit table for SQL*Loader log processing
CREATE TABLE sqlloader_audit (
    id BIGSERIAL PRIMARY KEY,
    log_filename VARCHAR(255) NOT NULL,
    control_filename VARCHAR(255),
    data_filename VARCHAR(255),
    load_start_time TIMESTAMP,
    load_end_time TIMESTAMP,
    records_loaded BIGINT DEFAULT 0,
    records_rejected BIGINT DEFAULT 0,
    load_status VARCHAR(50) NOT NULL,
    error_details TEXT,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(100),
    
    CONSTRAINT uk_sqlloader_audit_log_filename UNIQUE (log_filename, processed_at)
);

-- Create processing statistics table
CREATE TABLE processing_statistics (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    processor_type VARCHAR(50) NOT NULL,
    total_files_processed BIGINT DEFAULT 0,
    successful_executions BIGINT DEFAULT 0,
    failed_executions BIGINT DEFAULT 0,
    last_processing_time TIMESTAMP,
    current_status VARCHAR(50) DEFAULT 'IDLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_processing_statistics_config UNIQUE (config_name)
);

-- Create processing history table
CREATE TABLE processing_history (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    processor_type VARCHAR(50) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    file_size BIGINT,
    execution_time TIMESTAMP NOT NULL,
    execution_duration_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    correlation_id VARCHAR(100),
    metadata JSONB,
    
    CONSTRAINT chk_processing_history_status CHECK (status IN ('SUCCESS', 'FAILED', 'PROCESSING'))
);

-- Create indexes
CREATE INDEX idx_sqlloader_audit_status ON sqlloader_audit(load_status);
CREATE INDEX idx_sqlloader_audit_processed_at ON sqlloader_audit(processed_at);
CREATE INDEX idx_sqlloader_audit_correlation_id ON sqlloader_audit(correlation_id);

CREATE INDEX idx_processing_statistics_config_name ON processing_statistics(config_name);
CREATE INDEX idx_processing_statistics_processor_type ON processing_statistics(processor_type);

CREATE INDEX idx_processing_history_config_name ON processing_history(config_name);
CREATE INDEX idx_processing_history_execution_time ON processing_history(execution_time);
CREATE INDEX idx_processing_history_status ON processing_history(status);
CREATE INDEX idx_processing_history_correlation_id ON processing_history(correlation_id);