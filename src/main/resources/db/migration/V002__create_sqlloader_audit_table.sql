-- SQL*Loader Audit Table
-- This table stores audit information extracted from SQL*Loader log files

CREATE TABLE sqlloader_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    log_filename VARCHAR(255) NOT NULL,
    control_filename VARCHAR(255),
    data_filename VARCHAR(255),
    table_name VARCHAR(128),
    load_start_time TIMESTAMP,
    load_end_time TIMESTAMP,
    records_loaded BIGINT DEFAULT 0,
    records_rejected BIGINT DEFAULT 0,
    total_records BIGINT DEFAULT 0,
    load_status VARCHAR(50) NOT NULL,
    error_details TEXT,
    audit_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_sqlloader_audit_log_filename ON sqlloader_audit(log_filename);
CREATE INDEX idx_sqlloader_audit_table_name ON sqlloader_audit(table_name);
CREATE INDEX idx_sqlloader_audit_load_status ON sqlloader_audit(load_status);
CREATE INDEX idx_sqlloader_audit_audit_timestamp ON sqlloader_audit(audit_timestamp);
CREATE INDEX idx_sqlloader_audit_load_start_time ON sqlloader_audit(load_start_time);

-- Add comments for documentation
ALTER TABLE sqlloader_audit COMMENT = 'Audit table for SQL*Loader operations tracking';
ALTER TABLE sqlloader_audit MODIFY COLUMN log_filename VARCHAR(255) COMMENT 'Name of the SQL*Loader log file';
ALTER TABLE sqlloader_audit MODIFY COLUMN control_filename VARCHAR(255) COMMENT 'Name of the control file used';
ALTER TABLE sqlloader_audit MODIFY COLUMN data_filename VARCHAR(255) COMMENT 'Name of the data file loaded';
ALTER TABLE sqlloader_audit MODIFY COLUMN table_name VARCHAR(128) COMMENT 'Target table name';
ALTER TABLE sqlloader_audit MODIFY COLUMN load_start_time TIMESTAMP COMMENT 'When the load operation started';
ALTER TABLE sqlloader_audit MODIFY COLUMN load_end_time TIMESTAMP COMMENT 'When the load operation ended';
ALTER TABLE sqlloader_audit MODIFY COLUMN records_loaded BIGINT COMMENT 'Number of records successfully loaded';
ALTER TABLE sqlloader_audit MODIFY COLUMN records_rejected BIGINT COMMENT 'Number of records rejected due to errors';
ALTER TABLE sqlloader_audit MODIFY COLUMN total_records BIGINT COMMENT 'Total number of records processed';
ALTER TABLE sqlloader_audit MODIFY COLUMN load_status VARCHAR(50) COMMENT 'Status of the load operation (SUCCESS, ERROR, COMPLETED_WITH_ERRORS)';
ALTER TABLE sqlloader_audit MODIFY COLUMN error_details TEXT COMMENT 'Details of any errors encountered';
ALTER TABLE sqlloader_audit MODIFY COLUMN audit_timestamp TIMESTAMP COMMENT 'When this audit record was created';