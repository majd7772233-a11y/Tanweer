-- 0032_monthly_exam_uniqueness.sql
-- A group may have at most one MONTHLY exam per subject in each calendar month.
-- Existing historical duplicates are deliberately retained; this migration blocks
-- only new inserts and updates that would create another duplicate.

CREATE INDEX IF NOT EXISTS idx_exams_group_subject_type_date
ON exams(group_id, subject_id, exam_type, exam_date);

DROP TRIGGER IF EXISTS trg_exams_monthly_unique_insert;
CREATE TRIGGER trg_exams_monthly_unique_insert
BEFORE INSERT ON exams
WHEN NEW.exam_type = 'MONTHLY'
BEGIN
    SELECT CASE
        WHEN EXISTS (
            SELECT 1
            FROM exams
            WHERE group_id = NEW.group_id
              AND subject_id = NEW.subject_id
              AND exam_type = 'MONTHLY'
              AND substr(exam_date, 1, 7) = substr(NEW.exam_date, 1, 7)
        )
        THEN RAISE(ABORT, 'MONTHLY_EXAM_DUPLICATE')
    END;
END;

DROP TRIGGER IF EXISTS trg_exams_monthly_unique_update;
CREATE TRIGGER trg_exams_monthly_unique_update
BEFORE UPDATE OF group_id, subject_id, exam_date, exam_type ON exams
WHEN NEW.exam_type = 'MONTHLY'
BEGIN
    SELECT CASE
        WHEN EXISTS (
            SELECT 1
            FROM exams
            WHERE id <> NEW.id
              AND group_id = NEW.group_id
              AND subject_id = NEW.subject_id
              AND exam_type = 'MONTHLY'
              AND substr(exam_date, 1, 7) = substr(NEW.exam_date, 1, 7)
        )
        THEN RAISE(ABORT, 'MONTHLY_EXAM_DUPLICATE')
    END;
END;
