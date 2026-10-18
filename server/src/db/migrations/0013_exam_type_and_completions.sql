-- 0013_exam_type_and_completions.sql
-- Adds exam_type to exams and creates exam_completions table for per-user completion tracking

ALTER TABLE exams ADD COLUMN exam_type TEXT NOT NULL DEFAULT 'MONTHLY';

CREATE TABLE IF NOT EXISTS exam_completions (
    exam_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    completed_at INTEGER NOT NULL,
    PRIMARY KEY(exam_id, user_id),
    FOREIGN KEY(exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
