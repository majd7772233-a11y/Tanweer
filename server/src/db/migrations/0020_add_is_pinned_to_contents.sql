-- 0020_add_is_pinned_to_contents.sql
-- Add is_pinned flag to contents table for lesson pinning support
ALTER TABLE contents ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_contents_pinned ON contents(group_id, is_pinned);
