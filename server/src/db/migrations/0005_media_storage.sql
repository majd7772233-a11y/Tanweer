-- 0005_media_storage.sql
CREATE TABLE IF NOT EXISTS media_blobs (
    id TEXT PRIMARY KEY,
    mime_type TEXT NOT NULL DEFAULT 'image/jpeg',
    data BLOB NOT NULL,
    file_size INTEGER NOT NULL,
    checksum TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_media_checksum ON media_blobs(checksum);
