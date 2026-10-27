-- Migration 0015: System Generation tracking & School-wide holiday scope
CREATE TABLE IF NOT EXISTS system_settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

INSERT OR IGNORE INTO system_settings (key, value, updated_at)
VALUES ('data_generation', 'gen_initial_1', 1700000000000);
