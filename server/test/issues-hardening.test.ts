import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import { handleCreateIssue } from '../src/services/issues';

class IssueScopeDb {
  groupMembers = [{ group_id: 'group-a', user_id: 'student-a', status: 'ACTIVE' }];
  subjects = [{ id: 'math' }];
  homeworks = [
    { id: 'homework-a', group_id: 'group-a' },
    { id: 'homework-b', group_id: 'group-b' },
  ];
  exams = [
    { id: 'exam-a', group_id: 'group-a' },
    { id: 'exam-b', group_id: 'group-b' },
  ];
  issues: Array<Record<string, unknown>> = [];

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        const normalized = sql.trim().replace(/\\s+/g, ' ');
        if (normalized.includes('FROM group_members')) {
          const row = db.groupMembers.find((item) =>
            item.group_id === params[0] &&
            item.user_id === params[1] &&
            item.status === 'ACTIVE'
          );
          return (row as T | undefined) ?? null;
        }
        if (normalized.includes('FROM subjects')) {
          return (db.subjects.find((item) => item.id === params[0]) as T | undefined) ?? null;
        }
        if (normalized.includes('FROM homeworks')) {
          return (db.homeworks.find((item) =>
            item.id === params[0] && item.group_id === params[1]
          ) as T | undefined) ?? null;
        }
        if (normalized.includes('FROM exams')) {
          return (db.exams.find((item) =>
            item.id === params[0] && item.group_id === params[1]
          ) as T | undefined) ?? null;
        }
        if (normalized.includes('FROM issues WHERE id = ?')) {
          return (db.issues.find((item) => item.id === params[0]) as T | undefined) ?? null;
        }
        return null;
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        const normalized = sql.trim().replace(/\\s+/g, ' ');
        if (normalized.startsWith('INSERT INTO issues')) {
          const [
            id, groupId, subjectId, homeworkId, examId, title, description,
            createdBy, authorName, createdAt, updatedAt,
          ] = params;
          db.issues.push({
            id, group_id: groupId, subject_id: subjectId, homework_id: homeworkId,
            exam_id: examId, title, description, status: 'OPEN', created_by: createdBy,
            author_name: authorName, created_at: createdAt, updated_at: updatedAt,
          });
          return { success: true, meta: { changes: 1 } };
        }
        return { success: true, meta: { changes: 0 } };
      },
    };
    return statement;
  }
}

const user: UserContext = {
  userId: 'student-a',
  phoneNumber: '777000001',
  fullName: 'طالب تنوير',
  gradeId: 10,
  sectionId: 'A',
  role: Role.STUDENT,
};

function createEnv(db: IssueScopeDb): Env {
  return { DB: db as any } as Env;
}

function request(body: Record<string, unknown>): Request {
  return new Request('https://tanweer.test/api/v1/issues', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('Issue linked-resource group isolation', () => {
  it('rejects a homework reference that belongs to another group', async () => {
    const db = new IssueScopeDb();
    const response = await handleCreateIssue(user, request({
      groupId: 'group-a',
      title: 'سؤال عن الواجب',
      homeworkId: 'homework-b',
    }), createEnv(db));

    expect(response.status).toBe(404);
    expect((await response.json() as any).error.code).toBe('LINKED_HOMEWORK_NOT_FOUND');
    expect(db.issues).toHaveLength(0);
  });

  it('rejects an exam reference that belongs to another group', async () => {
    const db = new IssueScopeDb();
    const response = await handleCreateIssue(user, request({
      groupId: 'group-a',
      title: 'سؤال عن الاختبار',
      examId: 'exam-b',
    }), createEnv(db));

    expect(response.status).toBe(404);
    expect((await response.json() as any).error.code).toBe('LINKED_EXAM_NOT_FOUND');
    expect(db.issues).toHaveLength(0);
  });

  it('rejects subject identifiers that do not exist', async () => {
    const db = new IssueScopeDb();
    const response = await handleCreateIssue(user, request({
      groupId: 'group-a',
      title: 'سؤال عن مادة غير موجودة',
      subjectId: 'unknown-subject',
    }), createEnv(db));

    expect(response.status).toBe(404);
    expect((await response.json() as any).error.code).toBe('SUBJECT_NOT_FOUND');
    expect(db.issues).toHaveLength(0);
  });

  it('creates an issue when every linked academic record is valid for its group', async () => {
    const db = new IssueScopeDb();
    const response = await handleCreateIssue(user, request({
      groupId: 'group-a',
      subjectId: 'math',
      homeworkId: 'homework-a',
      examId: 'exam-a',
      title: 'استفسار عن الدرس والواجب',
      description: 'أحتاج إلى توضيح المسألة الثانية',
    }), createEnv(db));

    expect(response.status).toBe(200);
    expect((await response.json() as any).success).toBe(true);
    expect(db.issues).toHaveLength(1);
    expect(db.issues[0]).toMatchObject({
      group_id: 'group-a',
      subject_id: 'math',
      homework_id: 'homework-a',
      exam_id: 'exam-a',
      created_by: 'student-a',
    });
  });

  it('rejects whitespace-only issue titles', async () => {
    const db = new IssueScopeDb();
    const response = await handleCreateIssue(user, request({
      groupId: 'group-a',
      title: '   ',
    }), createEnv(db));

    expect(response.status).toBe(400);
    expect(db.issues).toHaveLength(0);
  });
});
