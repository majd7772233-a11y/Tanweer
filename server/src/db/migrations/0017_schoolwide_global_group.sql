-- Migration 0017: Seed global school-wide group 'all' to satisfy FOREIGN KEY constraint on events
INSERT OR IGNORE INTO groups (id, name, type, description, academic_year_id, created_at)
VALUES ('all', 'كامل المدرسة', 'SHARED', 'المجموعة المدرسية العامة لجميع الشعب والصفوف', 'default', 1700000000000);
