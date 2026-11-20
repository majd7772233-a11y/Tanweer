-- 0026: Bind media blobs to the account that uploaded them.
-- Legacy rows remain NULL and are authorized through their existing content/group links.
ALTER TABLE media_blobs ADD COLUMN uploaded_by TEXT REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_media_uploaded_by_checksum
ON media_blobs(uploaded_by, checksum);
