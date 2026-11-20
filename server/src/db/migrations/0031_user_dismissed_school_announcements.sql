-- 0031: Per-account dismissal of official school announcements.
-- Dismissal is private to one account; the original announcement remains visible to everyone else.
CREATE TABLE IF NOT EXISTS user_dismissed_announcements (
    user_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    dismissed_at INTEGER NOT NULL,
    PRIMARY KEY (user_id, event_id),
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_dismissed_announcements_event
ON user_dismissed_announcements(event_id);
