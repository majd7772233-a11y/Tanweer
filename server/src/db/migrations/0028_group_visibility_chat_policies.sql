-- 0028: Explicit group visibility/chat policies, public school groups and announcements support.
-- Existing class sections stay approval-based; existing optional/shared clubs remain discoverable.

ALTER TABLE groups ADD COLUMN is_official INTEGER NOT NULL DEFAULT 0;
ALTER TABLE groups ADD COLUMN is_discoverable INTEGER NOT NULL DEFAULT 1;
ALTER TABLE groups ADD COLUMN created_by TEXT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE groups ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE groups ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE groups ADD COLUMN chat_policy TEXT NOT NULL DEFAULT 'MEMBERS';

UPDATE groups
SET visibility = 'PRIVATE'
WHERE type = 'CLASS';

UPDATE groups
SET visibility = 'PUBLIC',
    chat_policy = 'MEMBERS',
    is_official = 1,
    is_discoverable = 1,
    created_by = COALESCE(created_by, 'SYSTEM_OWNER'),
    updated_at = CASE WHEN updated_at = 0 THEN created_at ELSE updated_at END
WHERE id = 'all';

INSERT OR IGNORE INTO groups (
    id, name, type, description, icon, academic_year_id, created_at,
    is_official, is_discoverable, created_by, updated_at, visibility, chat_policy
) VALUES (
    'staff_official',
    'الهيئة التعليمية الرسمية',
    'SHARED',
    'مساحة إعلانات ومناقشات الهيئة التعليمية. يمكن للطلاب الانضمام والقراءة فقط.',
    '🎓',
    'default',
    1700000000000,
    1,
    1,
    'SYSTEM_OWNER',
    1700000000000,
    'PUBLIC',
    'STAFF_ONLY'
);

UPDATE groups
SET visibility = 'PUBLIC',
    chat_policy = 'STAFF_ONLY',
    is_official = 1,
    is_discoverable = 1,
    created_by = COALESCE(created_by, 'SYSTEM_OWNER'),
    updated_at = CASE WHEN updated_at = 0 THEN created_at ELSE updated_at END
WHERE id = 'staff_official';

CREATE INDEX IF NOT EXISTS idx_groups_discovery_policy
ON groups(is_discoverable, visibility, type);

CREATE INDEX IF NOT EXISTS idx_group_members_pending_manager
ON group_members(group_id, status, role);
