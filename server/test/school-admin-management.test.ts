import { describe, expect, it } from 'vitest';
import { handleRemoveSchoolStudentFromClass, handleUpdateSchoolStudentStatus } from '../src/services/users';
import { Env, UserContext } from '../src/env';

type TargetUser = {
  id: string;
  role: string;
  grade_id: number;
  section_id: string;
  status: string;
};

type Membership = {
  group_id: string;
  user_id: string;
  role: string;
  status: string;
};

class SchoolAdminMockDb {
  user: TargetUser = {
    id: 'student-1',
    role: 'MEMBER',
    grade_id: 10,
    section_id: 'A',
    status: 'ACTIVE',
  };
  memberships: Membership[] = [
    { group_id: 'class_10_A', user_id: 'student-1', role: 'MEMBER', status: 'ACTIVE' },
  ];
  sessions = [{ id: 'session-1', user_id: 'student-1' }];
  contents = [{ id: 'lesson-1', created_by: 'student-1' }];
  statements: string[] = [];

  prepare(sql: string) {
    const db = this;
    const normalized = sql.trim().replace(/\\s+/g, ' ');
    let params: unknown[] = [];

    return {
      bind(...values: unknown[]) {
        params = values;
        return this;
      },
      async first<T = unknown>(): Promise<T | null> {
        db.statements.push(normalized);
        if (normalized.includes('MAX(CASE') && normalized.includes('FROM users u')) {
          if (db.user.id !== params[0]) return null;
          const roleRank = (role: string) => {
            switch (role.toUpperCase()) {
              case 'ADMIN':
              case 'ADMINISTRATOR': return 4;
              case 'TEACHER':
              case 'VERIFIED_TEACHER': return 3;
              case 'MODERATOR': return 2;
              default: return 1;
            }
          };
          const activeRoles = db.memberships
            .filter(member => member.user_id === db.user.id && member.status === 'ACTIVE')
            .map(member => roleRank(member.role));
          return { role_rank: Math.max(roleRank(db.user.role), ...activeRoles, 1) } as T;
        }
        if (normalized.includes('COALESCE(status,') && normalized.includes('FROM users WHERE id = ?')) {
          if (db.user.id !== params[0]) return null;
          return { id: db.user.id, role: db.user.role, status: db.user.status } as T;
        }
        if (normalized.startsWith('SELECT id, role, grade_id, section_id FROM users')) {
          if (db.user.id !== params[0]) return null;
          return {
            id: db.user.id,
            role: db.user.role,
            grade_id: db.user.grade_id,
            section_id: db.user.section_id,
          } as T;
        }
        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        return { results: [] };
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        db.statements.push(normalized);
        if (normalized.startsWith('UPDATE users SET status')) {
          const nextStatus = String(params[0]);
          const userId = String(params[2]);
          const elevatedRole = ['ADMIN', 'ADMINISTRATOR', 'SYSTEM_OWNER', 'OWNER', 'TEACHER', 'VERIFIED_TEACHER', 'MODERATOR'];
          const elevatedMembership = db.memberships.some(member =>
            member.user_id === userId &&
            member.status === 'ACTIVE' &&
            ['ADMIN', 'ADMINISTRATOR', 'TEACHER', 'VERIFIED_TEACHER', 'MODERATOR'].includes(member.role.toUpperCase())
          );
          if (
            db.user.id !== userId ||
            elevatedRole.includes(db.user.role.toUpperCase()) ||
            elevatedMembership ||
            db.user.status === nextStatus
          ) return { success: true, meta: { changes: 0 } };
          db.user.status = nextStatus;
          return { success: true, meta: { changes: 1 } };
        }
        if (normalized.startsWith('DELETE FROM sessions WHERE user_id = ?')) {
          const userId = String(params[0]);
          db.sessions = db.sessions.filter(session => session.user_id !== userId);
          return { success: true, meta: { changes: 1 } };
        }
        if (normalized.startsWith('DELETE FROM group_members')) {
          const [groupId, userId] = params.map(String);
          const before = db.memberships.length;
          db.memberships = db.memberships.filter(member => !(
            member.group_id === groupId &&
            member.user_id === userId &&
            member.status === 'ACTIVE' &&
            ['STUDENT', 'MEMBER'].includes(member.role.toUpperCase())
          ));
          return { success: true, meta: { changes: before - db.memberships.length } };
        }
        return { success: true, meta: { changes: 0 } };
      },
    };
  }
}

const admin: UserContext = {
  userId: 'school-admin',
  phoneNumber: '777000001',
  fullName: 'مدير المدرسة',
  gradeId: 10,
  sectionId: 'A',
  role: 'ADMIN',
};

const student: UserContext = {
  userId: 'student-actor',
  phoneNumber: '777000002',
  fullName: 'طالب',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
};

function makeEnv(db: SchoolAdminMockDb): Env {
  return { DB: db as any } as Env;
}

function statusRequest(status: string): Request {
  return new Request('https://tanweer.test/api/v1/admin/users/student-1/status', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  });
}

describe('Limited school administrator actions', () => {
  it('lets the school admin disable and later reactivate a student without deleting academic data', async () => {
    const db = new SchoolAdminMockDb();

    const disabled = await handleUpdateSchoolStudentStatus(
      'student-1', admin, statusRequest('DISABLED'), makeEnv(db)
    );
    expect(disabled.status).toBe(200);
    expect(await disabled.json()).toMatchObject({ success: true, status: 'DISABLED' });
    expect(db.user.status).toBe('DISABLED');
    expect(db.sessions).toHaveLength(0);
    expect(db.contents).toHaveLength(1);

    const enabled = await handleUpdateSchoolStudentStatus(
      'student-1', admin, statusRequest('ACTIVE'), makeEnv(db)
    );
    expect(enabled.status).toBe(200);
    expect(await enabled.json()).toMatchObject({ success: true, status: 'ACTIVE' });
    expect(db.user.status).toBe('ACTIVE');
    expect(db.sessions).toHaveLength(0);
    expect(db.contents).toHaveLength(1);
  });

  it('does not allow a non-admin to disable accounts', async () => {
    const db = new SchoolAdminMockDb();
    const response = await handleUpdateSchoolStudentStatus(
      'student-1', student, statusRequest('DISABLED'), makeEnv(db)
    );
    expect(response.status).toBe(403);
    expect(db.user.status).toBe('ACTIVE');
    expect(db.sessions).toHaveLength(1);
  });

  it('protects teachers and moderators who hold elevated roles inside any group', async () => {
    const db = new SchoolAdminMockDb();
    db.memberships.push({
      group_id: 'club-math',
      user_id: 'student-1',
      role: 'TEACHER',
      status: 'ACTIVE',
    });

    const disabled = await handleUpdateSchoolStudentStatus(
      'student-1', admin, statusRequest('DISABLED'), makeEnv(db)
    );
    const removed = await handleRemoveSchoolStudentFromClass(
      'student-1', admin, makeEnv(db)
    );

    expect(disabled.status).toBe(403);
    expect(removed.status).toBe(403);
    expect(db.user.status).toBe('ACTIVE');
    expect(db.memberships).toHaveLength(2);
  });

  it('removes only the student class membership and preserves the account and authored records', async () => {
    const db = new SchoolAdminMockDb();
    const response = await handleRemoveSchoolStudentFromClass(
      'student-1', admin, makeEnv(db)
    );

    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ success: true, groupId: 'class_10_A' });
    expect(db.user.id).toBe('student-1');
    expect(db.contents).toHaveLength(1);
    expect(db.memberships).toHaveLength(0);
  });

  it('does not treat a teacher membership in the class as a student membership to remove', async () => {
    const db = new SchoolAdminMockDb();
    db.memberships[0].role = 'TEACHER';

    const response = await handleRemoveSchoolStudentFromClass(
      'student-1', admin, makeEnv(db)
    );

    expect(response.status).toBe(403);
    expect(db.memberships).toHaveLength(1);
  });

  it('does not report a successful class removal when no active student membership exists', async () => {
    const db = new SchoolAdminMockDb();
    db.memberships = [];

    const response = await handleRemoveSchoolStudentFromClass(
      'student-1', admin, makeEnv(db)
    );

    expect(response.status).toBe(404);
    expect(db.user.id).toBe('student-1');
    expect(db.contents).toHaveLength(1);
  });

  it('rejects invalid status values without changing the account', async () => {
    const db = new SchoolAdminMockDb();
    const response = await handleUpdateSchoolStudentStatus(
      'student-1', admin, statusRequest('OWNER'), makeEnv(db)
    );

    expect(response.status).toBe(400);
    expect(db.user.status).toBe('ACTIVE');
  });
});
