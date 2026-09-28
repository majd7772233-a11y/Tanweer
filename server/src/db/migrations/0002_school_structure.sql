-- 0002_school_structure.sql
CREATE TABLE IF NOT EXISTS academic_years (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS grades (
    id INTEGER PRIMARY KEY,
    name_ar TEXT NOT NULL,
    level INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sections (
    grade_id INTEGER NOT NULL,
    section_id TEXT NOT NULL,
    name_ar TEXT NOT NULL,
    PRIMARY KEY(grade_id, section_id),
    FOREIGN KEY(grade_id) REFERENCES grades(id)
);

CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL, -- 'CLASS', 'SHARED', 'OPTIONAL'
    description TEXT,
    icon TEXT,
    academic_year_id TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS group_sections (
    group_id TEXT NOT NULL,
    grade_id INTEGER NOT NULL,
    section_id TEXT NOT NULL,
    PRIMARY KEY(group_id, grade_id, section_id),
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_members (
    group_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'MEMBER', -- 'MEMBER', 'MODERATOR', 'VERIFIED_TEACHER'
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'PENDING', 'REJECTED'
    joined_at INTEGER NOT NULL,
    PRIMARY KEY(group_id, user_id),
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Seed predefined Grades & Sections for standard school structure (7 to 12)
INSERT OR IGNORE INTO grades (id, name_ar, level) VALUES
(7, 'الصف السابع', 7),
(8, 'الصف الثامن', 8),
(9, 'الصف التاسع', 9),
(10, 'الأول الثانوي', 10),
(11, 'الثاني الثانوي', 11),
(12, 'الثالث الثانوي', 12);

INSERT OR IGNORE INTO sections (grade_id, section_id, name_ar) VALUES
(7, 'A', 'أ'),
(8, 'A', 'أ'),
(8, 'B', 'ب'),
(9, 'A', 'أ'),
(9, 'B', 'ب'),
(10, 'A', 'أ'),
(10, 'B', 'ب'),
(10, 'C', 'ج'),
(10, 'D', 'د'),
(11, 'A', 'أ'),
(11, 'B', 'ب'),
(11, 'C', 'ج'),
(11, 'D', 'د'),
(12, 'A', 'أ'),
(12, 'B', 'ب'),
(12, 'C', 'ج');
