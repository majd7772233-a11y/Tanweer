-- 0010_verification_and_official_content.sql
-- 1. Role Verification Codes (24-hour, single-use, user-bound, role-bound, request-bound, hashed & attempt-limited)
CREATE TABLE IF NOT EXISTS role_verification_codes (
    id TEXT PRIMARY KEY,
    request_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    role TEXT NOT NULL,
    code_hash TEXT NOT NULL,
    code_display TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    expires_at INTEGER NOT NULL,
    used_at INTEGER,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(request_id) REFERENCES role_requests(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_role_codes_request ON role_verification_codes(request_id);
CREATE INDEX IF NOT EXISTS idx_role_codes_user ON role_verification_codes(user_id);

-- 2. Official flags for differentiating student contributions from verified teacher/admin publications
ALTER TABLE homeworks ADD COLUMN is_official INTEGER NOT NULL DEFAULT 0;
ALTER TABLE exams ADD COLUMN is_official INTEGER NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN is_official INTEGER NOT NULL DEFAULT 0;
ALTER TABLE issue_comments ADD COLUMN is_teacher_answer INTEGER NOT NULL DEFAULT 0;
ALTER TABLE issue_comments ADD COLUMN author_role TEXT NOT NULL DEFAULT 'STUDENT';
