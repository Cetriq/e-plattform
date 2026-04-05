-- Attachments table for file storage metadata
CREATE TABLE attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_filename VARCHAR(500) NOT NULL,
    stored_filename VARCHAR(100) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    checksum VARCHAR(64),
    case_id UUID REFERENCES cases(id) ON DELETE SET NULL,
    query_definition_id UUID,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_attachments_case_id ON attachments(case_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_attachments_uploaded_by ON attachments(uploaded_by) WHERE is_deleted = FALSE;
CREATE INDEX idx_attachments_stored_filename ON attachments(stored_filename);
CREATE INDEX idx_attachments_query_def ON attachments(case_id, query_definition_id) WHERE is_deleted = FALSE;

COMMENT ON TABLE attachments IS 'Metadata for files stored in MinIO object storage';
COMMENT ON COLUMN attachments.stored_filename IS 'UUID-based filename used in MinIO storage';
COMMENT ON COLUMN attachments.checksum IS 'SHA-256 hash of file contents for integrity verification';
