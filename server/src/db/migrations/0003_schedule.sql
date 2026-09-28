-- 0003_schedule.sql
CREATE TABLE IF NOT EXISTS subjects (
    id TEXT PRIMARY KEY,
    name_ar TEXT NOT NULL,
    code TEXT,
    icon TEXT,
    color_hex TEXT
);

CREATE TABLE IF NOT EXISTS schedule_versions (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    valid_from TEXT NOT NULL,
    valid_until TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    created_by TEXT,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schedule_slots (
    id TEXT PRIMARY KEY,
    version_id TEXT NOT NULL,
    day_of_week INTEGER NOT NULL, -- 0 = Sunday, 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday
    slot_order INTEGER NOT NULL, -- 1, 2, 3, 4, 5, 6
    subject_id TEXT NOT NULL,
    start_time TEXT,
    end_time TEXT,
    FOREIGN KEY(version_id) REFERENCES schedule_versions(id) ON DELETE CASCADE,
    FOREIGN KEY(subject_id) REFERENCES subjects(id)
);

CREATE TABLE IF NOT EXISTS schedule_proposals (
    id TEXT PRIMARY KEY,
    group_id TEXT NOT NULL,
    proposed_by TEXT NOT NULL,
    day_of_week INTEGER NOT NULL,
    slot_order INTEGER NOT NULL,
    old_subject_id TEXT,
    new_subject_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'REJECTED'
    votes_for INTEGER NOT NULL DEFAULT 0,
    votes_against INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
    FOREIGN KEY(proposed_by) REFERENCES users(id)
);

-- Seed standard subjects
INSERT OR IGNORE INTO subjects (id, name_ar, icon, color_hex) VALUES
('math', 'الرياضيات', '📐', '#00E5FF'),
('physics', 'الفيزياء', '⚡', '#7C4DFF'),
('chemistry', 'الكيمياء', '🧪', '#00E676'),
('biology', 'الأحياء', '🧬', '#69F0AE'),
('science', 'العلوم', '🔬', '#69F0AE'),
('arabic', 'اللغة العربية', '📖', '#FFB300'),
('english', 'اللغة الإنجليزية', '🇬🇧', '#2979FF'),
('islamic', 'التربية الإسلامية', '🕌', '#00B0FF'),
('quran', 'القرآن الكريم', '✨', '#1DE9B6'),
('social', 'الاجتماعيات', '🌍', '#FF9100'),
('history', 'التاريخ', '🏛️', '#FF6D00'),
('geography', 'الجغرافيا', '🧭', '#FFAB00'),
('civics', 'التربية الوطنية', '🇾🇪', '#E040FB'),
('computer', 'الحاسوب وتكنولوجيا المعلومات', '💻', '#00B0FF');
