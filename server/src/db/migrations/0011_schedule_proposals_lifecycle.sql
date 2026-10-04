-- 0011_schedule_proposals_lifecycle.sql
CREATE TABLE IF NOT EXISTS schedule_proposal_votes (
    proposal_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    vote_type TEXT NOT NULL DEFAULT 'FOR', -- 'FOR', 'AGAINST'
    created_at INTEGER NOT NULL,
    PRIMARY KEY (proposal_id, user_id),
    FOREIGN KEY (proposal_id) REFERENCES schedule_proposals(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Index for speedy proposal vote counting
CREATE INDEX IF NOT EXISTS idx_spv_prop ON schedule_proposal_votes(proposal_id);
CREATE INDEX IF NOT EXISTS idx_sp_group ON schedule_proposals(group_id, status);
CREATE INDEX IF NOT EXISTS idx_sv_group ON schedule_versions(group_id, is_active);
