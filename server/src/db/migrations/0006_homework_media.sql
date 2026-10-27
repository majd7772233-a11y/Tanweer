-- Migration 0006: Add media_urls support to homeworks table and create homework_media relationship table
ALTER TABLE homeworks ADD COLUMN media_urls TEXT;

CREATE TABLE IF NOT EXISTS homework_media (
    id TEXT PRIMARY KEY,
    homework_id TEXT NOT NULL,
    media_id TEXT NOT NULL,
    url TEXT NOT NULL,
    page_order INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(homework_id) REFERENCES homeworks(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_hw_media_homework ON homework_media(homework_id);
CREATE INDEX IF NOT EXISTS idx_hw_media_blob ON homework_media(media_id);
