ALTER TABLE file_metadata ADD COLUMN content_deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_file_metadata_history ON file_metadata(user_id, created_at DESC, id DESC);
CREATE INDEX idx_file_metadata_cleanup ON file_metadata(content_deleted, expires_at, id);
