-- 0035: Reversible archive state for private in-app notifications.
-- Read state already exists from 0027; archival is separate and never deletes data.
ALTER TABLE user_notifications ADD COLUMN archived_at INTEGER;

CREATE INDEX IF NOT EXISTS idx_user_notifications_owner_archive_created
ON user_notifications(user_id, archived_at, created_at DESC);
