-- Audit events table for tracking user actions
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255),
    user_name VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(255),
    details TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    request_path VARCHAR(500),
    request_method VARCHAR(10),
    response_status INTEGER,
    duration_ms BIGINT
);

-- Indexes for common queries
CREATE INDEX idx_audit_user_id ON audit_events(user_id);
CREATE INDEX idx_audit_action ON audit_events(action);
CREATE INDEX idx_audit_entity ON audit_events(entity_type, entity_id);
CREATE INDEX idx_audit_timestamp ON audit_events(timestamp DESC);
CREATE INDEX idx_audit_ip_address ON audit_events(ip_address);

-- Partial index for security-related events (faster security monitoring)
CREATE INDEX idx_audit_security_events ON audit_events(action, ip_address, timestamp)
    WHERE action IN ('LOGIN_FAILURE', 'LOGIN_SUCCESS', 'RATE_LIMIT_EXCEEDED', 'UNAUTHORIZED_ACCESS');

-- Comment on table
COMMENT ON TABLE audit_events IS 'Audit log for tracking user actions and system events';
