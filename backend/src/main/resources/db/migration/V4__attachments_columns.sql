-- Add missing columns to attachments table if they don't exist
-- V1 created the base table, this adds columns needed for FileController

-- Add stored_filename if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'stored_filename'
    ) THEN
        ALTER TABLE attachments ADD COLUMN stored_filename VARCHAR(100);
        -- Populate from file_id if it exists
        UPDATE attachments SET stored_filename = file_id::text WHERE stored_filename IS NULL AND file_id IS NOT NULL;
    END IF;
END $$;

-- Add bucket if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'bucket'
    ) THEN
        ALTER TABLE attachments ADD COLUMN bucket VARCHAR(100) DEFAULT 'eplatform-files';
    END IF;
END $$;

-- Add case_id if not exists (V1 has entity_type/entity_id instead)
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'case_id'
    ) THEN
        ALTER TABLE attachments ADD COLUMN case_id UUID REFERENCES cases(id) ON DELETE SET NULL;
        -- Migrate data from entity_id where entity_type is 'CASE'
        UPDATE attachments SET case_id = entity_id WHERE entity_type = 'CASE' AND case_id IS NULL;
    END IF;
END $$;

-- Add query_definition_id if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'query_definition_id'
    ) THEN
        ALTER TABLE attachments ADD COLUMN query_definition_id UUID;
    END IF;
END $$;

-- Add uploaded_by if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'uploaded_by'
    ) THEN
        ALTER TABLE attachments ADD COLUMN uploaded_by UUID;
        -- Copy from created_by if available
        UPDATE attachments SET uploaded_by = created_by WHERE uploaded_by IS NULL AND created_by IS NOT NULL;
    END IF;
END $$;

-- Add uploaded_at if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'uploaded_at'
    ) THEN
        ALTER TABLE attachments ADD COLUMN uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
        -- Copy from created_at if available
        UPDATE attachments SET uploaded_at = created_at WHERE uploaded_at IS NULL AND created_at IS NOT NULL;
    END IF;
END $$;

-- Add is_deleted if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'is_deleted'
    ) THEN
        ALTER TABLE attachments ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

-- Add deleted_at if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'deleted_at'
    ) THEN
        ALTER TABLE attachments ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
    END IF;
END $$;

-- Add updated_at if not exists
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'updated_at'
    ) THEN
        ALTER TABLE attachments ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
    END IF;
END $$;

-- Add file_size if not exists (V1 has size_bytes instead)
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'attachments' AND column_name = 'file_size'
    ) THEN
        ALTER TABLE attachments ADD COLUMN file_size BIGINT;
        -- Copy from size_bytes if available
        UPDATE attachments SET file_size = size_bytes WHERE file_size IS NULL AND size_bytes IS NOT NULL;
    END IF;
END $$;

-- Create indexes if not exist
CREATE INDEX IF NOT EXISTS idx_attachments_case_id ON attachments(case_id);
CREATE INDEX IF NOT EXISTS idx_attachments_uploaded_by ON attachments(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_attachments_stored_filename ON attachments(stored_filename);
CREATE INDEX IF NOT EXISTS idx_attachments_query_def ON attachments(case_id, query_definition_id);
