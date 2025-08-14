-- Flyway migration script V2 - Add Functions and Triggers
-- This script adds stored functions, triggers, and views

-- Set search path
SET search_path TO filewatcher, public;

-- Create function to update processing statistics
CREATE OR REPLACE FUNCTION update_processing_statistics()
RETURNS TRIGGER AS $$
BEGIN
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

-- Create trigger
CREATE TRIGGER trg_update_processing_statistics
    AFTER INSERT ON processing_history
    FOR EACH ROW
    EXECUTE FUNCTION update_processing_statistics();

-- Create cleanup function
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

-- Create monitoring view
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