import { describe, expect, it } from 'vitest';
import { handleUpdateExam } from '../src/services/exams';
import { Env, UserContext } from '../src/env';

type ExamRow = {
  id: string;
  group_id: string;
  created_by: string;
  exam_date: string;
  subject_id: string;
  title: string;
  required_chapters: string | null;
  notes: string | null;
  is_official: number;
  exam_type: string;
};

class ExamUpdateDb {
  constructor(private readonly memberRole = 'STUDENT') {}

  row: ExamRow = {
    id: 'exam_owner_1',
    group_id: 'class_10_A',
    created_by: 'student_owner_1',
    exam_date: '2026-11-10',
    subject_id: 'MATH',
    title: 'Original exam',
    required_chapters: 'CH1, CH2',
    notes: 'Bring calculator',
    is_official: 0,
    exam_type: 'MONTHLY',
  };
  updateCalls = 0;

  prepare(sql: string) {
    const normalized = sql.replace(/\s+/g, ' ').trim().toUpperCase();
    let values: unknown[] = [];
    const statement = {
      bind: (...bound: unknown[]) => {
        values = bound;
        return statement;
      },
      first: async () => {
        if (normalized.includes('FROM GROUP_MEMBERS')) {
          return { role: this.memberRole, status: 'ACTIVE' };
        }
        if (normalized.startsWith('SELECT * FROM EXAMS')) {
          return { ...this.row };
        }
        if (normalized.startsWith('SELECT ID FROM EXAMS')) {
          return null;
        }
        return null;
      },
      all: async () => ({ results: [] }),
      run: async () => {
        if (normalized.startsWith('UPDATE EXAMS')) {
          this.updateCalls++;
          const [
            examDate,
            subjectId,
            title,
            chaptersProvided,
            chaptersValue,
            notesProvided,
            notesValue,
            officialValue,
            examType,
            examId,
          ] = values as [string | null, string | null, string | null, number, string | null, number, string | null, number | null, string | null, string];

          if (examId === this.row.id) {
            if (examDate) this.row.exam_date = examDate;
            if (subjectId) this.row.subject_id = subjectId;
            if (title) this.row.title = title;
            if (chaptersProvided === 1) this.row.required_chapters = chaptersValue || null;
            if (notesProvided === 1) this.row.notes = notesValue || null;
            if (officialValue !== null) this.row.is_official = officialValue;
            if (examType) this.row.exam_type = examType;
          }
        }
        return { success: true, meta: { changes: 1 } };
      },
    };
    return statement;
  }
}

function makeEnv(db: ExamUpdateDb): Env {
  return { DB: db as any } as Env;
}

const owner: UserContext = {
  userId: 'student_owner_1',
  phoneNumber: '700000001',
  fullName: 'Student Owner',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
};

const otherStudent: UserContext = {
  ...owner,
  userId: 'student_other_2',
  phoneNumber: '700000002',
  fullName: 'Other Student',
};

describe('Student-created exam update ownership', () => {
  it('allows the creator to update and clear optional chapters and notes', async () => {
    const db = new ExamUpdateDb();
    const request = new Request('https://example.test/api/exams/exam_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        examDate: '2026-12-12',
        title: 'Updated exam',
        requiredChapters: '',
        notes: '',
        examType: 'MONTHLY',
      }),
    });

    const response = await handleUpdateExam('exam_owner_1', owner, request, makeEnv(db));

    expect(response.status).toBe(200);
    expect(db.updateCalls).toBe(1);
    expect(db.row.title).toBe('Updated exam');
    expect(db.row.exam_date).toBe('2026-12-12');
    expect(db.row.required_chapters).toBeNull();
    expect(db.row.notes).toBeNull();
  });

  it('rejects a different student even when they belong to the same group', async () => {
    const db = new ExamUpdateDb();
    const request = new Request('https://example.test/api/exams/exam_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Unauthorized change' }),
    });

    const response = await handleUpdateExam('exam_owner_1', otherStudent, request, makeEnv(db));

    expect(response.status).toBe(403);
    expect(db.updateCalls).toBe(0);
    expect(db.row.title).toBe('Original exam');
  });

  it('allows an active group teacher to moderate another student exam', async () => {
    const db = new ExamUpdateDb('TEACHER');
    const request = new Request('https://example.test/api/exams/exam_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Teacher correction' }),
    });
    const teacher: UserContext = {
      ...otherStudent,
      userId: 'teacher_1',
      role: 'STUDENT',
    };

    const response = await handleUpdateExam('exam_owner_1', teacher, request, makeEnv(db));

    expect(response.status).toBe(200);
    expect(db.updateCalls).toBe(1);
    expect(db.row.title).toBe('Teacher correction');
  });

  it('does not grant an active moderator permission to edit another student exam', async () => {
    const db = new ExamUpdateDb('MODERATOR');
    const request = new Request('https://example.test/api/exams/exam_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Unauthorized moderator change' }),
    });

    const response = await handleUpdateExam('exam_owner_1', otherStudent, request, makeEnv(db));

    expect(response.status).toBe(403);
    expect(db.updateCalls).toBe(0);
    expect(db.row.title).toBe('Original exam');
  });

  it('preserves optional fields when they are omitted from the patch', async () => {
    const db = new ExamUpdateDb();
    const request = new Request('https://example.test/api/exams/exam_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Title only' }),
    });

    const response = await handleUpdateExam('exam_owner_1', owner, request, makeEnv(db));

    expect(response.status).toBe(200);
    expect(db.row.title).toBe('Title only');
    expect(db.row.required_chapters).toBe('CH1, CH2');
    expect(db.row.notes).toBe('Bring calculator');
  });
});