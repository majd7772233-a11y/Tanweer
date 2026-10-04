-- 0012_unified_governance_and_corrections.sql
CREATE TABLE IF NOT EXISTS content_corrections (
    id TEXT PRIMARY KEY,
    content_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    author_name TEXT,
    field_name TEXT NOT NULL, -- 'TITLE', 'DESCRIPTION', 'SUBJECT', 'DATE', 'MEDIA'
    original_value TEXT,
    proposed_value TEXT NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED', 'APPLIED'
    reviewed_by TEXT,
    reviewed_at INTEGER,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS community_decisions (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    request_type TEXT NOT NULL, -- 'DELETION', 'CORRECTION', 'SCHEDULE', 'VERIFICATION'
    target_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    requested_by TEXT NOT NULL,
    requester_name TEXT,
    total_eligible_voters INTEGER NOT NULL DEFAULT 1,
    threshold_percent INTEGER NOT NULL DEFAULT 50,
    votes_for INTEGER NOT NULL DEFAULT 0,
    votes_against INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED', 'APPLIED', 'EXPIRED'
    expires_at INTEGER,
    applied_at INTEGER,
    applied_by TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS community_votes (
    decision_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    vote_choice INTEGER NOT NULL DEFAULT 1, -- 1 = FOR, 0 = AGAINST
    created_at INTEGER NOT NULL,
    PRIMARY KEY (decision_id, user_id),
    FOREIGN KEY (decision_id) REFERENCES community_decisions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_corr_grp ON content_corrections(group_id, status);
CREATE INDEX IF NOT EXISTS idx_dec_grp ON community_decisions(group_id, status);
