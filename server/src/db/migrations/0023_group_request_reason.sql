-- Migration 0023: Add request_reason to group_members for cross-section join requests (A-11)
ALTER TABLE group_members ADD COLUMN request_reason TEXT;
