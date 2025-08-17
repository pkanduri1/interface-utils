-- SQL*Loader Audit Table Schema
-- Compatible with multiple database types (MySQL, PostgreSQL, Oracle, H2)

-- Drop table if exists (for testing/development)
-- DROP TABLE IF EXISTS sqlloader_audit;

-- Create the main audit table
CREATE TABLE sqlloader_audit (
    id BIGINT NOT NULL,
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
    error_details CLOB,
    audit_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sqlloader_audit PRIMARY KEY (id)
);

-- Create sequence for ID generation (PostgreSQL/Oracle style)
-- For databases that support sequences
-- CREATE SEQUENCE sqlloader_audit_seq START WITH 1 INCREMENT BY 1;

-- Create indexes for performance
CREATE INDEX idx_sqlloader_audit_log_filename ON sqlloader_audit(log_filename);
CREATE INDEX idx_sqlloader_audit_table_name ON sqlloader_audit(table_name);
CREATE INDEX idx_sqlloader_audit_load_status ON sqlloader_audit(load_status);
CREATE INDEX idx_sqlloader_audit_audit_timestamp ON sqlloader_audit(audit_timestamp);
CREATE INDEX idx_sqlloader_audit_load_start_time ON sqlloader_audit(load_start_time);

-- Insert sample data for testing (optional)
/*
INSERT INTO sqlloader_audit (
    id, log_filename, control_filename, data_filename, table_name,
    load_start_time, load_end_time, records_loaded, records_rejected,
    total_records, load_status, error_details
) VALUES (
    1, 'sample_load.log', 'sample.ctl', 'sample.dat', 'EMPLOYEES',
    CURRENT_TIMESTAMP - INTERVAL '1' HOUR, CURRENT_TIMESTAMP - INTERVAL '59' MINUTE,
    1000, 5, 1005, 'COMPLETED_WITH_ERRORS', 'Some records had invalid date formats'
);
*/