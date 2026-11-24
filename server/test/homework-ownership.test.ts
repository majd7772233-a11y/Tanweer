import { describe, expect, it } from 'vitest';
import { handleDeleteHomework, handleUpdateHomework } from '../src/services/homework';
import { Env, UserContext } from '../src/env';

type HomeworkRow = {
  id: string;
  group_id: string;
  created_by: string;
  due_date: string;
  title: string;
  details: string | null;
  page_numbers: string | null;
  question_numbers: string | null;
  media_urls: string | null;
  is_official: number;
};

class HomeworkOwnershipDb {
  constructor(private readonly memberRole = 'STUDENT') {}

  row: HomeworkRow = {
    id: 'hw_owner_1',
    group_id: 'class_10_A',
    created_by: 'student_owner_1',
    due_date: '2026-11-25',
    title: 'Original homework',
    details: 'Read the chapter',
    page_numbers: '12-14',
    question_numbers: '1-5',
    media_urls: null,
    is_official: 0,
  };

  updateCalls = 0;
  homeworkDeleteCalls = 0;
  completionDeleteCalls = 0;
  mediaDeleteCalls = 0;

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
        if (normalized.startsWith('SELECT * FROM HOMEWORKS')) {
          return { ...this.row };
        }
        return null;
      },
      all: async () => ({ results: [] }),
      run: async () => {
        if (normalized.startsWith('UPDATE HOMEWORKS')) {
          this.updateCalls++;
          const [title, detailsProvided, details, dueDate, pagesProvided, pages, questionsProvided, questions, official, id] =
            values as [string | null, number, string | null, string | null, number, string | null, number, string | null, number | null, string];
          if (id === this.row.id) {
            if (title !== null) this.row.title = title;
            if (detailsProvided === 1) this.row.details = details || null;
            if (dueDate !== null) this.row.due_date = dueDate;
            if (pagesProvided === 1) this.row.page_numbers = pages || null;
            if (questionsProvided === 1) this.row.question_numbers = questions || null;
            if (official !== null) this.row.is_official = official;
          }
        } else if (normalized.startsWith('DELETE FROM HOMEWORKS')) {
          this.homeworkDeleteCalls++;
        } else if (normalized.startsWith('DELETE FROM HOMEWORK_COMPLETIONS')) {
          this.completionDeleteCalls++;
        } else if (normalized.startsWith('DELETE FROM HOMEWORK_MEDIA')) {
          this.mediaDeleteCalls++;
        }
        return { success: true, meta: { changes: 1 } };
      },
    };
    return statement;
  }
}

function makeEnv(db: HomeworkOwnershipDb): Env {
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

describe('Student-created homework ownership', () => {
  it('allows the creator to update and explicitly clear optional fields', async () => {
    const db = new HomeworkOwnershipDb();
    const request = new Request('https://example.test/api/homeworks/hw_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: 'Updated homework',
        dueDate: '2026-12-01',
        details: '',
        pageNumbers: '',
      }),
    });

    const response = await handleUpdateHomework('hw_owner_1', owner, request, makeEnv(db));

    expect(response.status).toBe(200);
    expect(db.updateCalls).toBe(1);
    expect(db.row.title).toBe('Updated homework');
    expect(db.row.due_date).toBe('2026-12-01');
    expect(db.row.details).toBeNull();
    expect(db.row.page_numbers).toBeNull();
    expect(db.row.question_numbers).toBe('1-5');
  });

  it('preserves optional values when the patch omits them', async () => {
    const db = new HomeworkOwnershipDb();
    const request = new Request('https://example.test/api/homeworks/hw_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Title only' }),
    });

    const response = await handleUpdateHomework('hw_owner_1', owner, request, makeEnv(db));

    expect(response.status).toBe(200);
    expect(db.row.title).toBe('Title only');
    expect(db.row.details).toBe('Read the chapter');
    expect(db.row.page_numbers).toBe('12-14');
    expect(db.row.question_numbers).toBe('1-5');
  });

  it('rejects another active group student from editing the homework', async () => {
    const db = new HomeworkOwnershipDb();
    const request = new Request('https://example.test/api/homeworks/hw_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Unauthorized title' }),
    });

    const response = await handleUpdateHomework('hw_owner_1', otherStudent, request, makeEnv(db));

    expect(response.status).toBe(403);
    expect(db.updateCalls).toBe(0);
    expect(db.row.title).toBe('Original homework');
  });

  it('allows an active group teacher to moderate another student homework', async () => {
    const db = new HomeworkOwnershipDb('TEACHER');
    const teacher: UserContext = { ...otherStudent, userId: 'teacher_1', role: 'STUDENT' };
    const request = new Request('https://example.test/api/homeworks/hw_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Teacher correction' }),
    });

    const response = await handleUpdateHomework('hw_owner_1', teacher, request, makeEnv(db));

    expect(response.status).toBe(200);
    expect(db.updateCalls).toBe(1);
    expect(db.row.title).toBe('Teacher correction');
  });

  it('rejects a blank required title without writing', async () => {
    const db = new HomeworkOwnershipDb();
    const request = new Request('https://example.test/api/homeworks/hw_owner_1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: '   ' }),
    });

    const response = await handleUpdateHomework('hw_owner_1', owner, request, makeEnv(db));

    expect(response.status).toBe(400);
    expect(db.updateCalls).toBe(0);
    expect(db.row.title).toBe('Original homework');
  });

  it('allows the creator to delete homework and associated records', async () => {
    const db = new HomeworkOwnershipDb();

    const response = await handleDeleteHomework('hw_owner_1', owner, makeEnv(db));

    expect(response.status).toBe(200);
    expect(db.homeworkDeleteCalls).toBe(1);
    expect(db.completionDeleteCalls).toBe(1);
    expect(db.mediaDeleteCalls).toBe(1);
  });

  it('rejects another active group student from deleting the homework', async () => {
    const db = new HomeworkOwnershipDb();

    const response = await handleDeleteHomework('hw_owner_1', otherStudent, makeEnv(db));

    expect(response.status).toBe(403);
    expect(db.homeworkDeleteCalls).toBe(0);
    expect(db.completionDeleteCalls).toBe(0);
    expect(db.mediaDeleteCalls).toBe(0);
  });
});
