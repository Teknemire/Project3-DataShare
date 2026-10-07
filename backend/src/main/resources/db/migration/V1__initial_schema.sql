CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE file_metadata (
    id UUID PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    mime_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    download_token VARCHAR(64) NOT NULL UNIQUE,
    download_password_hash VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID REFERENCES users(id)
);
CREATE INDEX idx_file_metadata_user_id ON file_metadata(user_id);
CREATE INDEX idx_file_metadata_expires_at ON file_metadata(expires_at);
CREATE TABLE file_tag (
    file_id UUID NOT NULL REFERENCES file_metadata(id),
    tag VARCHAR(30) NOT NULL,
    CONSTRAINT uk_file_tag_file_id_tag UNIQUE(file_id, tag)
);
