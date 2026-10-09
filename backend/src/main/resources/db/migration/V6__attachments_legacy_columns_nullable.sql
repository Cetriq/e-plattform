-- V1 created attachments with a polymorphic (entity_type/entity_id) model.
-- V4 switched to the model used by the Attachment entity (case_id, stored_filename, ...)
-- but kept the old columns as NOT NULL, so inserting a new attachment failed.
-- The legacy columns are no longer written, so make them nullable.
ALTER TABLE attachments ALTER COLUMN entity_type DROP NOT NULL;
ALTER TABLE attachments ALTER COLUMN entity_id DROP NOT NULL;
ALTER TABLE attachments ALTER COLUMN file_id DROP NOT NULL;
ALTER TABLE attachments ALTER COLUMN filename DROP NOT NULL;
ALTER TABLE attachments ALTER COLUMN size_bytes DROP NOT NULL;
