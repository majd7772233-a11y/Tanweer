-- 0004_content_system.sql
CREATE TABLE IF NOT EXISTS contents (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    study_date TEXT NOT NULL, -- YYYY-MM-DD
    subject_id TEXT NOT NULL,
    type TEXT NOT NULL, -- 'LESSON', 'SUMMARY', 'NOTE', 'FILE'
    title TEXT NOT NULL,
    description TEXT,
    created_by TEXT NOT NULL,
    author_name TEXT NOT NULL,
    author_grade_section TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PUBLISHED', -- 'PUBLISHED', 'PENDING_CORRECTION', 'PENDING_DELETION', 'DELETED'
    version INTEGER NOT NULL DEFAULT 1,
    views_count INTEGER NOT NULL DEFAULT 0,
    useful_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY(subject_id) REFERENCES subjects(id),
    FOREIGN KEY(created_by) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_content_lookup ON contents(group_id, study_date, subject_id);

CREATE TABLE IF NOT EXISTS content_media (
    id TEXT PRIMARY KEY,
    content_id TEXT NOT NULL,
    page_order INTEGER NOT NULL DEFAULT 1,
    object_key TEXT NOT NULL,
    url TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    checksum TEXT,
    is_primary INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(content_id) REFERENCES contents(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS homeworks (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    study_date TEXT NOT NULL,
    due_date TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    title TEXT NOT NULL,
    details TEXT,
    page_numbers TEXT,
    question_numbers TEXT,
    task_type TEXT NOT NULL DEFAULT 'HOMEWORK', -- 'HOMEWORK', 'TASK'
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY(subject_id) REFERENCES subjects(id),
    FOREIGN KEY(created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS homework_completions (
    homework_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    completed_at INTEGER NOT NULL,
    PRIMARY KEY(homework_id, user_id),
    FOREIGN KEY(homework_id) REFERENCES homeworks(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS exams (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    exam_date TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    title TEXT NOT NULL,
    required_chapters TEXT,
    notes TEXT,
    study_package_info TEXT,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY(subject_id) REFERENCES subjects(id),
    FOREIGN KEY(created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS events (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    event_date TEXT NOT NULL,
    time_str TEXT,
    title TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL, -- 'ACTIVITY', 'COMPETITION', 'TRIP', 'ANNOUNCEMENT', 'HOLIDAY'
    location TEXT,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY(created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS issues (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    subject_id TEXT,
    homework_id TEXT,
    exam_id TEXT,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'IN_DISCUSSION', 'SOLVED', 'CLOSED'
    best_comment_id TEXT,
    created_by TEXT NOT NULL,
    author_name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY(created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS issue_comments (
    id TEXT PRIMARY KEY,
    issue_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    author_name TEXT NOT NULL,
    comment TEXT NOT NULL,
    is_best_answer INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(issue_id) REFERENCES issues(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS deletion_requests (
    id TEXT PRIMARY KEY,
    content_id TEXT NOT NULL,
    requested_by TEXT NOT NULL,
    reason TEXT NOT NULL,
    total_eligible_voters INTEGER NOT NULL,
    votes_in_favor INTEGER NOT NULL DEFAULT 0,
    votes_against INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'REJECTED'
    created_at INTEGER NOT NULL,
    FOREIGN KEY(content_id) REFERENCES contents(id) ON DELETE CASCADE,
    FOREIGN KEY(requested_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS correction_requests (
    id TEXT PRIMARY KEY,
    content_id TEXT NOT NULL,
    requested_by TEXT NOT NULL,
    proposed_date TEXT,
    proposed_subject_id TEXT,
    proposed_title TEXT,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at INTEGER NOT NULL,
    FOREIGN KEY(content_id) REFERENCES contents(id) ON DELETE CASCADE,
    FOREIGN KEY(requested_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS votes (
    request_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    request_type TEXT NOT NULL, -- 'DELETION', 'CORRECTION', 'SCHEDULE'
    vote_choice INTEGER NOT NULL, -- 1 = Approve, 0 = Reject
    created_at INTEGER NOT NULL,
    PRIMARY KEY(request_id, user_id)
);

CREATE TABLE IF NOT EXISTS user_bookmarks (
    user_id TEXT NOT NULL,
    target_id TEXT NOT NULL,
    target_type TEXT NOT NULL, -- 'LESSON', 'HOMEWORK', 'ISSUE', 'FILE'
    title TEXT NOT NULL,
    subject_id TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY(user_id, target_id)
);

CREATE TABLE IF NOT EXISTS user_notes (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    target_id TEXT,
    note_text TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS books (
    id TEXT PRIMARY KEY,
    grade_id INTEGER NOT NULL,
    subject_id TEXT NOT NULL,
    title TEXT NOT NULL,
    edition TEXT,
    file_size_mb REAL NOT NULL,
    file_url TEXT NOT NULL,
    thumbnail_url TEXT
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    sender_grade_section TEXT NOT NULL,
    text TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_group ON chat_messages(group_id, timestamp);
