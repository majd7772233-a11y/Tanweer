import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import { handleGetIssueDetails } from '../src/services/issues';

class IssueDetailsDb {
  readonly sql: string[] = [];

  prepare(sql: string) {
    const db = this;
    const normalized = sql.trim().replace(/\s+/g, ' ');
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        if (normalized.includes('FROM group_members')) {
          return { role: 'STUDENT', status: 'ACTIVE' } as unknown as T;
        }
        if (normalized.includes('FROM issues i')) {
          return {
            id: params[0],
            group_id: 'group-a',
            subject_id: null,
            homework_id: null,
            exam_id: null,
            title: 'Question',
            description: 'Description',
            status: 'IN_DISCUSSION',
            best_comment_id: null,
            author_name: 'Student',
            created_by: 'student-a',
            created_at: 1_700_000_000,
            subject_name: null,
            subject_icon: null,
          } as unknown as T;
        }
        if (normalized.includes('COUNT(*) as c FROM issue_comments')) {
          return { c: 1 } as unknown as T;
        }
        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        db.sql.push(normalized);
        if (normalized.includes('FROM issue_comments')) {
          return {
            results: [{
              id: 'comment_teacher',
              issue_id: 'issue-a',
              user_id: 'teacher-a',
              author_name: 'Teacher',
              comment: 'Verified explanation',
              is_best_answer: 0,
              is_teacher_answer: 1,
              created_at: 1_700_000_001,
            } as unknown as T],
          };
        }
        return { results: [] };
      },
      async run() {
        db.sql.push(normalized);
        return { success: true, meta: { changes: 1 } };
      },
    };
    return statement;
  }
}

const user: UserContext = {
  userId: 'student-a',
  phoneNumber: '777000001',
  fullName: 'Student',
  gradeId: 10,
  sectionId: 'A',
  role: Role.STUDENT,
};

describe('Issue detail teacher-answer contract', () => {
  it('selects and returns the verified-teacher-answer marker', async () => {
    const db = new IssueDetailsDb();
    const response = await handleGetIssueDetails(
      'issue-a',
      user,
      { DB: db as any } as Env
    );

    expect(response.status).toBe(200);
    const payload = await response.json() as any;
    const commentQuery = db.sql.find(sql => sql.includes('FROM issue_comments'));
    expect(commentQuery).toContain('is_teacher_answer');
    expect(payload.comments).toHaveLength(1);
    expect(payload.comments[0].isTeacherAnswer).toBe(true);
    expect(payload.comments[0].is_teacher_answer).toBeUndefined();
  });
});
