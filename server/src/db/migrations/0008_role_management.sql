-- 0008_role_management.sql
-- Unified Role Management, Verification, and Audit Logging

-- 1. Role Upgrade Requests (Students/Members requesting Teacher or Moderator rank)
CREATE TABLE IF NOT EXISTS role_requests (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    requested_role TEXT NOT NULL, -- 'MODERATOR', 'TEACHER', 'ADMIN'
    group_id TEXT,               -- Optional specific group scope
    reason TEXT,
    phone_number TEXT,
    full_name TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED'
    reviewed_by TEXT,
    review_notes TEXT,
    reviewed_at INTEGER,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_role_requests_status ON role_requests(status);
CREATE INDEX IF NOT EXISTS idx_role_requests_user ON role_requests(user_id);

-- 2. Role Verification Tokens (One-time invite/claim tokens for teachers/moderators)
CREATE TABLE IF NOT EXISTS role_verification_tokens (
    id TEXT PRIMARY KEY,
    user_id TEXT,
    role TEXT NOT NULL,
    token TEXT NOT NULL UNIQUE,
    created_by TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    used_at INTEGER,
    used_by TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_role_tokens_token ON role_verification_tokens(token);

-- 3. Role Audit Log (Comprehensive trace of all administrative & role events)
CREATE TABLE IF NOT EXISTS role_audit_log (
    id TEXT PRIMARY KEY,
    action TEXT NOT NULL,       -- 'CHANGE_ROLE', 'APPROVE_ROLE_REQUEST', 'REJECT_ROLE_REQUEST', 'DISABLE_USER', 'ENABLE_USER', 'REVOKE_SESSIONS'
    actor_id TEXT NOT NULL,     -- 'SYSTEM_OWNER' or user_id
    actor_role TEXT NOT NULL,   -- e.g. 'SYSTEM_OWNER', 'ADMIN'
    target_user_id TEXT,
    details TEXT,               -- JSON details or human-readable explanation
    ip_address TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_role_audit_created ON role_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_role_audit_target ON role_audit_log(target_user_id);

-- 4. Teacher Subject & Class Assignments (For academic governance)
CREATE TABLE IF NOT EXISTS teacher_assignments (
    id TEXT PRIMARY KEY,
    teacher_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_teacher_assignments ON teacher_assignments(teacher_id, group_id);
