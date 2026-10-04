-- 0009_user_status.sql
-- Adds status column to users table for account freezing/disabling and enabling
ALTER TABLE users ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
