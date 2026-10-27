-- 0014_homework_media_table.sql
-- Creates homework_media relationship table for explicit media linkage and cleanup
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
