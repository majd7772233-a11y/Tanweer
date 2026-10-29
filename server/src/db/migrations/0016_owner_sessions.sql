-- Migration 0016: Server-side Owner Sessions with expiration and revocation
CREATE TABLE IF NOT EXISTS owner_sessions (
    id TEXT PRIMARY KEY,
    token_hash TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    revoked INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_owner_sessions_token ON owner_sessions(token_hash);
