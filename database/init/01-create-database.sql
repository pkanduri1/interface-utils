-- Database initialization script for File Watcher Service
-- This script creates the necessary tables for audit logging

-- Create schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS filewatcher;

-- Set search path
SET search_path TO filewatcher, public;

-- Create audit table for SQL*Loader log processing
CREATE TABLE IF NOT EXISTS sqlloader_audit (
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
    
    -- Indexes for better query performance
    CONSTRAINT uk_sqlloader_audit_log_filename UNIQUE (log_filename, processed_at)
);

-- Create index on frequently queried columns
CREATE INDEX IF NOT EXISTS idx_sqlloader_audit_status ON sqlloader_audit(load_status);
CREATE INDEX IF NOT EXISTS idx_sqlloader_audit_processed_at ON sqlloader_audit(processed_at);
CREATE INDEX IF NOT EXISTS idx_sqlloader_audit_correlation_id ON sqlloader_audit(correlation_id);

-- Create processing statistics table
CREATE TABLE IF NOT EXISTS processing_statistics (
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

-- Create index on config_name for faster lookups
CREATE INDEX IF NOT EXISTS idx_processing_statistics_config_name ON processing_statistics(config_name);
CREATE INDEX IF NOT EXISTS idx_processing_statistics_processor_type ON processing_statistics(processor_type);

-- Create processing history table for detailed tracking
CREATE TABLE IF NOT EXISTS processing_history (
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
    
    -- Partition by execution_time for better performance
    CONSTRAINT chk_processing_history_status CHECK (status IN ('SUCCESS', 'FAILED', 'PROCESSING'))
);

-- Create indexes for processing history
CREATE INDEX IF NOT EXISTS idx_processing_history_config_name ON processing_history(config_name);
CREATE INDEX IF NOT EXISTS idx_processing_history_execution_time ON processing_history(execution_time);
CREATE INDEX IF NOT EXISTS idx_processing_history_status ON processing_history(status);
CREATE INDEX IF NOT EXISTS idx_processing_history_correlation_id ON processing_history(correlation_id);

-- Create function to update processing statistics
CREATE OR REPLACE FUNCTION update_processing_statistics()
RETURNS TRIGGER AS $$
BEGIN
    -- Insert or update processing statistics
    INSERT INTO processing_statistics (
        config_name, 
        processor_type, 
        total_files_processed, 
        successful_executions, 
        failed_executions, 
        last_processing_time,
        current_status,
        updated_at
    )
    VALUES (
        NEW.config_name,
        NEW.processor_type,
        1,
        CASE WHEN NEW.status = 'SUCCESS' THEN 1 ELSE 0 END,
        CASE WHEN NEW.status = 'FAILED' THEN 1 ELSE 0 END,
        NEW.execution_time,
        'ACTIVE',
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (config_name) DO UPDATE SET
        total_files_processed = processing_statistics.total_files_processed + 1,
        successful_executions = processing_statistics.successful_executions + 
            CASE WHEN NEW.status = 'SUCCESS' THEN 1 ELSE 0 END,
        failed_executions = processing_statistics.failed_executions + 
            CASE WHEN NEW.status = 'FAILED' THEN 1 ELSE 0 END,
        last_processing_time = NEW.execution_time,
        current_status = 'ACTIVE',
        updated_at = CURRENT_TIMESTAMP;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to automatically update statistics
DROP TRIGGER IF EXISTS trg_update_processing_statistics ON processing_history;
CREATE TRIGGER trg_update_processing_statistics
    AFTER INSERT ON processing_history
    FOR EACH ROW
    EXECUTE FUNCTION update_processing_statistics();

-- Create function to clean up old processing history (retention policy)
CREATE OR REPLACE FUNCTION cleanup_processing_history(retention_days INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM processing_history 
    WHERE execution_time < CURRENT_TIMESTAMP - INTERVAL '1 day' * retention_days;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Create application user and grant permissions
DO $$
BEGIN
    -- Create application user if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM pg_user WHERE usename = 'filewatcher_app') THEN
        CREATE USER filewatcher_app WITH PASSWORD 'app_password_change_me';
    END IF;
END
$$;

-- Grant necessary permissions
GRANT USAGE ON SCHEMA filewatcher TO filewatcher_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA filewatcher TO filewatcher_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA filewatcher TO filewatcher_app;

-- Grant permissions on future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA filewatcher GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO filewatcher_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA filewatcher GRANT USAGE, SELECT ON SEQUENCES TO filewatcher_app;

-- Insert initial configuration data
INSERT INTO processing_statistics (config_name, processor_type, current_status) 
VALUES 
    ('sql-scripts', 'sql-script', 'IDLE'),
    ('sqlloader-logs', 'sqlloader-log', 'IDLE')
ON CONFLICT (config_name) DO NOTHING;

-- Create view for monitoring dashboard
CREATE OR REPLACE VIEW processing_summary AS
SELECT 
    ps.config_name,
    ps.processor_type,
    ps.total_files_processed,
    ps.successful_executions,
    ps.failed_executions,
    ROUND((ps.successful_executions::DECIMAL / NULLIF(ps.total_files_processed, 0)) * 100, 2) as success_rate,
    ps.last_processing_time,
    ps.current_status,
    COUNT(ph.id) FILTER (WHERE ph.execution_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours') as files_last_24h,
    AVG(ph.execution_duration_ms) FILTER (WHERE ph.execution_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours') as avg_duration_ms_24h
FROM processing_statistics ps
LEFT JOIN processing_history ph ON ps.config_name = ph.config_name
GROUP BY ps.config_name, ps.processor_type, ps.total_files_processed, 
         ps.successful_executions, ps.failed_executions, ps.last_processing_time, ps.current_status;

-- Grant view permissions
GRANT SELECT ON processing_summary TO filewatcher_app;

COMMIT;