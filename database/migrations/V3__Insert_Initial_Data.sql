-- Flyway migration script V3 - Insert Initial Data
-- This script inserts initial configuration data

-- Set search path
SET search_path TO filewatcher, public;

-- Insert initial processing statistics records
INSERT INTO processing_statistics (config_name, processor_type, current_status) 
VALUES 
    ('sql-scripts', 'sql-script', 'IDLE'),
    ('sqlloader-logs', 'sqlloader-log', 'IDLE')
ON CONFLICT (config_name) DO NOTHING;