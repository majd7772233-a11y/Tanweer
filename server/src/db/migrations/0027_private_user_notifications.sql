-- 0027: Private in-app notifications, including encrypted recovery-code delivery.
CREATE TABLE IF NOT EXISTS user_notifications (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    encrypted_payload TEXT,
    related_id TEXT,
    created_at INTEGER NOT NULL,
    read_at INTEGER,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_owner_created
ON user_notifications(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_notifications_related
ON user_notifications(related_id);
