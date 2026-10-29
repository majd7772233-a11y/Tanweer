-- 0018_user_recovery_credentials.sql
-- Table for tracking single-use temporary credentials and recovery tokens issued by System Owner
CREATE TABLE IF NOT EXISTS user_recovery_tokens (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'RECOVERY_CODE', -- 'RECOVERY_CODE', 'TEMP_PASSWORD'
    max_uses INTEGER NOT NULL DEFAULT 1,
    uses_count INTEGER NOT NULL DEFAULT 0,
    expires_at INTEGER NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    used_at INTEGER,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_recovery_user ON user_recovery_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_user_recovery_hash ON user_recovery_tokens(token_hash);
