-- 0034_detach_global_school_admins.sql
-- A global school ADMIN has no academic class assignment or active CLASS membership.
-- Preserve the previous assignment and memberships privately so a later role change
-- can restore the account without silently losing its former class context.

CREATE TABLE IF NOT EXISTS admin_role_restore_points (
    user_id TEXT PRIMARY KEY,
    grade_id INTEGER NOT NULL,
    section_id TEXT NOT NULL,
    saved_at INTEGER NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS admin_class_membership_restore_points (
    user_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT NOT NULL,
    joined_at INTEGER NOT NULL,
    PRIMARY KEY(user_id, group_id),
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE
);

-- Save only academically valid assignments for existing administrators.
INSERT OR IGNORE INTO admin_role_restore_points (user_id, grade_id, section_id, saved_at)
SELECT u.id, u.grade_id, u.section_id, u.updated_at
FROM users u
JOIN grades g ON g.id = u.grade_id
JOIN sections s
  ON s.grade_id = u.grade_id
 AND UPPER(s.section_id) = UPPER(u.section_id)
WHERE UPPER(u.role) IN ('ADMIN', 'ADMINISTRATOR');

-- Preserve all existing class memberships exactly, including non-active states.
INSERT OR IGNORE INTO admin_class_membership_restore_points
    (user_id, group_id, role, status, joined_at)
SELECT gm.user_id, gm.group_id, gm.role, gm.status, gm.joined_at
FROM group_members gm
JOIN groups g ON g.id = gm.group_id
JOIN users u ON u.id = gm.user_id
WHERE UPPER(u.role) IN ('ADMIN', 'ADMINISTRATOR')
  AND UPPER(g.type) = 'CLASS';

-- Keep school-wide memberships and roles unchanged; only remove class scope.
DELETE FROM group_members
WHERE user_id IN (
    SELECT id FROM users WHERE UPPER(role) IN ('ADMIN', 'ADMINISTRATOR')
)
AND group_id IN (
    SELECT id FROM groups WHERE UPPER(type) = 'CLASS'
);

-- The schema keeps these columns NOT NULL for non-admin accounts; the private
-- restore point above holds the previous valid class assignment while ADMIN.
UPDATE users
SET grade_id = 0,
    section_id = '',
    updated_at = CAST(strftime('%s', 'now') AS INTEGER) * 1000
WHERE UPPER(role) IN ('ADMIN', 'ADMINISTRATOR');
