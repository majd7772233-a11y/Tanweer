import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import { handleMarkBestAnswer } from '../src/services/issues';

type Membership = { group_id: string; user_id: string; role: string; status: string };

class ApprovalDb {
  readonly issue = { group_id: 'group-a', created_by: 'student-author' };
  readonly comment = { id: 'answer-a' };
  readonly memberships: Membership[] = [];
  readonly writes: string[] = [];

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
        if (normalized.includes('FROM issues WHERE id = ?')) return db.issue as T;
        if (normalized.includes('FROM group_members')) {
          const member = db.memberships.find(row =>
            row.group_id === params[0] && row.user_id === params[1] && row.status === 'ACTIVE'
          );
          return (member as T | undefined) ?? null;
        }
        if (normalized.includes('FROM issue_comments')) {
          return (params[0] === db.comment.id && params[1] === 'issue-a')
            ? db.comment as T
            : null;
        }
        return null;
      },
      async run() {
        db.writes.push(normalized);
        return { success: true, meta: { changes: 1 } };
      },
    };
    return statement;
  }
}

const createUser = (userId: string, role: string): UserContext => ({
  userId,
  phoneNumber: '777000001',
  fullName: userId,
  gradeId: 10,
  sectionId: 'A',
  role: role as Role,
});

async function approve(db: ApprovalDb, actor: UserContext): Promise<Response> {
  return handleMarkBestAnswer('issue-a', 'answer-a', actor, { DB: db as any } as Env);
}

describe('Best-answer authorization is enforced by the server', () => {
  it('allows the owner of the question to accept an answer', async () => {
    const db = new ApprovalDb();
    db.memberships.push({ group_id: 'group-a', user_id: 'student-author', role: 'STUDENT', status: 'ACTIVE' });

    const response = await approve(db, createUser('student-author', Role.STUDENT));

    expect(response.status).toBe(200);
    expect(db.writes.filter(sql => sql.startsWith('UPDATE '))).toHaveLength(3);
  });

  it('rejects another student even when that student authored the selected answer', async () => {
    const db = new ApprovalDb();
    db.memberships.push({ group_id: 'group-a', user_id: 'student-answer-author', role: 'STUDENT', status: 'ACTIVE' });

    const response = await approve(db, createUser('student-answer-author', Role.STUDENT));

    expect(response.status).toBe(403);
    expect(db.writes).toHaveLength(0);
  });

  it('allows a globally approved teacher who is an active member of the group', async () => {
    const db = new ApprovalDb();
    db.memberships.push({ group_id: 'group-a', user_id: 'teacher-a', role: 'STUDENT', status: 'ACTIVE' });

    const response = await approve(db, createUser('teacher-a', Role.TEACHER));

    expect(response.status).toBe(200);
  });

  it('allows a teacher approved specifically for the group', async () => {
    const db = new ApprovalDb();
    db.memberships.push({ group_id: 'group-a', user_id: 'group-teacher', role: 'TEACHER', status: 'ACTIVE' });

    const response = await approve(db, createUser('group-teacher', Role.STUDENT));

    expect(response.status).toBe(200);
  });

  it('rejects a moderator who is not the owner of the issue', async () => {
    const db = new ApprovalDb();
    db.memberships.push({ group_id: 'group-a', user_id: 'moderator-a', role: 'MODERATOR', status: 'ACTIVE' });

    const response = await approve(db, createUser('moderator-a', Role.MODERATOR));

    expect(response.status).toBe(403);
    expect(db.writes).toHaveLength(0);
  });

  it('allows the school administrator without requiring group membership', async () => {
    const db = new ApprovalDb();

    const response = await approve(db, createUser('admin-a', Role.ADMIN));

    expect(response.status).toBe(200);
  });

  it('allows the system owner without requiring group membership', async () => {
    const db = new ApprovalDb();

    const response = await approve(db, createUser('owner-a', Role.SYSTEM_OWNER));

    expect(response.status).toBe(200);
  });

  it('rejects a selected answer that belongs to a different issue', async () => {
    const db = new ApprovalDb();
    db.memberships.push({ group_id: 'group-a', user_id: 'student-author', role: 'STUDENT', status: 'ACTIVE' });

    const response = await handleMarkBestAnswer('issue-a', 'other-answer', createUser('student-author', Role.STUDENT), { DB: db as any } as Env);

    expect(response.status).toBe(404);
    expect(db.writes).toHaveLength(0);
  });
});
