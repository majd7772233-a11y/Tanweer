import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { handleCreateExam, SUPPORTED_EXAM_TYPES } from '../src/services/exams';

const student: UserContext = {
  userId: 'student_1',
  phoneNumber: '777123456',
  fullName: 'طالب الاختبار',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
};

type StoredExam = {
  id: string;
  group_id: string;
  exam_date: string;
  subject_id: string;
  title: string;
  exam_type: string;
  is_official: number;
  created_by: string;
};

class MonthlyExamDb {
  exams: StoredExam[] = [];
  skipNextDuplicatePreflight = false;

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const normalized = sql.trim().replace(/\s+/g, ' ');
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        if (normalized.includes('FROM group_members')) {
          return { role: 'STUDENT', status: 'ACTIVE' } as T;
        }

        if (normalized.startsWith('SELECT id, is_official, group_id, created_by, exam_type FROM exams')) {
          const existing = db.exams.find(exam => exam.id === params[0]);
          if (!existing) return null;
          return {
            id: existing.id,
            is_official: existing.is_official,
            group_id: existing.group_id,
            created_by: existing.created_by,
            exam_type: existing.exam_type,
          } as T;
        }

        if (normalized.startsWith('SELECT id FROM exams')) {
          if (db.skipNextDuplicatePreflight) {
            db.skipNextDuplicatePreflight = false;
            return null;
          }
          const [groupId, subjectId, monthKey] = params.map(String);
          const duplicate = db.exams.find(exam =>
            exam.group_id === groupId &&
            exam.subject_id === subjectId &&
            exam.exam_type === 'MONTHLY' &&
            exam.exam_date.slice(0, 7) === monthKey
          );
          return duplicate ? ({ id: duplicate.id } as T) : null;
        }

        return null;
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        if (normalized.startsWith('INSERT INTO exams')) {
          const [
            id, groupId, examDate, subjectId, title,
            _chapters, _notes, isOfficial, createdBy, _createdAt, examType,
          ] = params as [
            string, string, string, string, string,
            string | null, string | null, number, string, number, string,
          ];

          const duplicate = examType === 'MONTHLY' && db.exams.some(exam =>
            exam.group_id === groupId &&
            exam.subject_id === subjectId &&
            exam.exam_type === 'MONTHLY' &&
            exam.exam_date.slice(0, 7) === examDate.slice(0, 7)
          );
          if (duplicate) {
            throw new Error('MONTHLY_EXAM_DUPLICATE');
          }

          db.exams.push({
            id,
            group_id: groupId,
            exam_date: examDate,
            subject_id: subjectId,
            title,
            exam_type: examType,
            is_official: isOfficial,
            created_by: createdBy,
          });
          return { success: true, meta: { changes: 1 } };
        }

        return { success: true, meta: { changes: 0 } };
      },
    };
    return statement;
  }
}

function createExamRequest(input: {
  id: string;
  date?: string;
  subjectId?: string;
  examType?: string;
  groupId?: string;
}): Request {
  return new Request('https://tanweer-staging.magd.workers.dev/api/v1/exams', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      id: input.id,
      groupId: input.groupId ?? 'class_10_A',
      examDate: input.date ?? '2026-11-10',
      subjectId: input.subjectId ?? 'math',
      title: 'اختبار شهري',
      examType: input.examType ?? 'MONTHLY',
    }),
  });
}

function envFor(db: MonthlyExamDb): Env {
  return { DB: db as unknown as D1Database } as Env;
}

describe('Supported school exam categories', () => {
  it('keeps exactly the four categories used by the school', () => {
    expect(SUPPORTED_EXAM_TYPES).toEqual(['MONTHLY', 'ORAL', 'MIDTERM', 'FINAL']);
  });

  it('rejects retired QUIZ records at the API boundary', async () => {
    const request = new Request('https://tanweer-staging.magd.workers.dev/api/v1/exams', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        groupId: 'class_10_A',
        examDate: '2026-11-10',
        subjectId: 'math',
        title: 'محاولة إنشاء كويز',
        examType: 'QUIZ',
      }),
    });

    // Invalid exam types are rejected before the database is accessed.
    const response = await handleCreateExam(student, request, {} as Env);
    const body = await response.json() as any;

    expect(response.status).toBe(400);
    expect(body.error?.code).toBe('INVALID_EXAM_TYPE');
  });

  it('allows only one monthly exam for the same subject in the same month', async () => {
    const db = new MonthlyExamDb();

    const first = await handleCreateExam(student, createExamRequest({ id: 'monthly-math-nov' }), envFor(db));
    const duplicate = await handleCreateExam(student, createExamRequest({ id: 'monthly-math-nov-duplicate' }), envFor(db));
    const firstBody = await first.json() as any;
    const duplicateBody = await duplicate.json() as any;

    expect(first.status).toBe(200);
    expect(firstBody.success).toBe(true);
    expect(duplicate.status).toBe(409);
    expect(duplicateBody.error?.code).toBe('MONTHLY_EXAM_EXISTS');
    expect(db.exams).toHaveLength(1);
  });

  it('allows the same subject to have a monthly exam in the following month', async () => {
    const db = new MonthlyExamDb();

    const november = await handleCreateExam(student, createExamRequest({ id: 'math-nov', date: '2026-11-10' }), envFor(db));
    const december = await handleCreateExam(student, createExamRequest({ id: 'math-dec', date: '2026-12-10' }), envFor(db));

    expect(november.status).toBe(200);
    expect(december.status).toBe(200);
    expect(db.exams).toHaveLength(2);
  });

  it('allows different subjects to have monthly exams in the same month', async () => {
    const db = new MonthlyExamDb();

    const math = await handleCreateExam(student, createExamRequest({ id: 'math-nov', subjectId: 'math' }), envFor(db));
    const science = await handleCreateExam(student, createExamRequest({ id: 'science-nov', subjectId: 'science' }), envFor(db));

    expect(math.status).toBe(200);
    expect(science.status).toBe(200);
    expect(db.exams).toHaveLength(2);
  });

  it('does not apply monthly uniqueness to oral examinations', async () => {
    const db = new MonthlyExamDb();

    const first = await handleCreateExam(student, createExamRequest({ id: 'oral-math-1', examType: 'ORAL' }), envFor(db));
    const second = await handleCreateExam(student, createExamRequest({ id: 'oral-math-2', examType: 'ORAL' }), envFor(db));

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(db.exams).toHaveLength(2);
  });

  it('converts a concurrent database uniqueness rejection into a useful conflict response', async () => {
    const db = new MonthlyExamDb();
    db.skipNextDuplicatePreflight = true;
    db.exams.push({
      id: 'existing-monthly',
      group_id: 'class_10_A',
      exam_date: '2026-11-05',
      subject_id: 'math',
      title: 'الاختبار الموجود',
      exam_type: 'MONTHLY',
      is_official: 0,
      created_by: student.userId,
    });

    // This mock exercises the database-level duplicate guard after the request
    // has passed its preflight path; the SQL migration supplies the atomic D1 guard.
    const response = await handleCreateExam(
      student,
      createExamRequest({ id: 'racing-monthly', date: '2026-11-20' }),
      envFor(db)
    );

    expect(response.status).toBe(409);
    expect((await response.json() as any).error?.code).toBe('MONTHLY_EXAM_EXISTS');
  });
});
