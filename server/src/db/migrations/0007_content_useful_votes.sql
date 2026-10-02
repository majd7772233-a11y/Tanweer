-- Migration 0007: Track user votes on useful content to prevent repeated voting
CREATE TABLE IF NOT EXISTS content_useful_votes (
    user_id TEXT NOT NULL,
    content_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (user_id, content_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
);
