import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import {
  handleDismissSchoolAnnouncement,
  handleGetEvents,
} from '../src/services/events';
import {
  handleRemoveSchoolStudentFromClass,
  handleUpdateSchoolStudentStatus,
} from '../src/services/users';

type TestUser = {
  id: string;
  role: string;
  status: string;
  grade_id: number;
  section_id: string;
};

type TestEvent = {
  id: string;
  group_id: string;
  event_date: string;
  time_str: string | null;
  title: string;
  description: string | null;
  category: string;
  location: string | null;
  is_official: number;
  created_by: string;
  created_at: number;
  source_name: string;
};

class SchoolAdminAndAnnouncementDb {
  users: TestUser[] = [
    { id: 'admin-1', role: 'ADMIN', status: 'ACTIVE', grade_id: 10, section_id: 'A' },
    { id: 'student-1', role: 'STUDENT', status: 'ACTIVE', grade_id: 10, section_id: 'B' },
    { id: 'teacher-1', role: 'TEACHER', status: 'ACTIVE', grade_id: 10, section_id: 'B' },
  ];
  members = [
    { group_id: 'class_10_B', user_id: 'student-1', status: 'ACTIVE' },
    { group_id: 'class_10_B', user_id: 'teacher-1', status: 'ACTIVE' },
    { group_id: 'class_10_B', user_id: 'student-a', status: 'ACTIVE' },
    { group_id: 'class_10_B', user_id: 'student-b', status: 'ACTIVE' },
  ];
  sessions = [
    { user_id: 'student-1', device_id: 'phone-a' },
    { user_id: 'student-1', device_id: 'phone-b' },
    { user_id: 'teacher-1', device_id: 'teacher-phone' },
  ];
  contents = [{ id: 'lesson-1', group_id: 'class_10_B', created_by: 'student-1' }];
  events: TestEvent[] = [{
    id: 'announcement-1',
    group_id: 'all',
    event_date: '2026-11-05',
    time_str: null,
    title: 'تعميم مدرسي',
    description: 'نص التعميم',
    category: 'ANNOUNCEMENT',
    location: null,
    is_official: 1,
    created_by: 'admin-1',
    created_at: 100,
    source_name: 'مدير المدرسة',
  }];
  dismissed: Array<{ user_id: string; event_id: string; dismissed_at: number }> = [];
  statements: string[] = [];

  prepare(sql: string) {
    const db = this;
    const normalized = sql.trim().replace(/\\s+/g, ' ');
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        db.statements.push(normalized);
        if (normalized.includes("COALESCE(status, 'ACTIVE') AS status FROM users")) {
          const row = db.users.find(user => user.id === params[0]);
          return row ? { id: row.id, role: row.role, status: row.status } as T : null;
        }
        if (normalized.includes('SELECT id, role, grade_id, section_id FROM users')) {
          const row = db.users.find(user => user.id === params[0]);
          return row ? { id: row.id, role: row.role, grade_id: row.grade_id, section_id: row.section_id } as T : null;
        }
        if (normalized.includes("FROM events WHERE id = ? AND group_id = 'all'")) {
          const row = db.events.find(event =>
            event.id === params[0] && event.group_id === 'all' &&
            event.category.toUpperCase() === 'ANNOUNCEMENT'
          );
          return row ? { id: row.id } as T : null;
        }
        if (normalized.includes('SELECT role, status FROM group_members WHERE group_id = ? AND user_id = ?')) {
          const member = db.members.find(row =>
            row.group_id === params[0] && row.user_id === params[1] && row.status === 'ACTIVE'
          );
          return member ? { role: 'STUDENT', status: 'ACTIVE' } as T : null;
        }
        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        db.statements.push(normalized);
        if (normalized.includes('FROM events e')) {
          const groupId = String(params[0]);
          const userId = String(params[1]);
          const rows = db.events.filter(event =>
            (event.group_id === groupId || event.group_id === 'all') &&
            !(event.group_id === 'all' &&
              event.category.toUpperCase() === 'ANNOUNCEMENT' &&
              db.dismissed.some(row => row.user_id === userId && row.event_id === event.id))
          );
          return { results: rows as T[] };
        }
        return { results: [] };
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        db.statements.push(normalized);
        if (normalized.startsWith('UPDATE users SET status = ?')) {
          const row = db.users.find(user => user.id === params[2]);
          if (!row || ['ADMIN', 'SYSTEM_OWNER', 'OWNER'].includes(row.role.toUpperCase())) {
            return { success: true, meta: { changes: 0 } };
          }
          row.status = String(params[0]);
          return { success: true, meta: { changes: 1 } };
        }
        if (normalized.startsWith('DELETE FROM sessions WHERE user_id = ?')) {
          const before = db.sessions.length;
          db.sessions = db.sessions.filter(session => session.user_id !== params[0]);
          return { success: true, meta: { changes: before - db.sessions.length } };
        }
        if (normalized.startsWith("DELETE FROM group_members WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'")) {
          const before = db.members.length;
          db.members = db.members.filter(member =>
            !(member.group_id === params[0] && member.user_id === params[1] && member.status === 'ACTIVE')
          );
          return { success: true, meta: { changes: before - db.members.length } };
        }
        if (normalized.startsWith('INSERT OR IGNORE INTO user_dismissed_announcements')) {
          const [userId, eventId, dismissedAt] = params;
          const exists = db.dismissed.some(row => row.user_id === userId && row.event_id === eventId);
          if (!exists) {
            db.dismissed.push({ user_id: String(userId), event_id: String(eventId), dismissed_at: Number(dismissedAt) });
          }
          return { success: true, meta: { changes: exists ? 0 : 1 } };
        }
        return { success: true, meta: { changes: 0 } };
      },
    };
    return statement;
  }
}

function makeUser(role: string, userId: string): UserContext {
  return {
    userId,
    phoneNumber: '777000001',
    fullName: role === 'ADMIN' ? 'مدير المدرسة' : 'مستخدم تجريبي',
    gradeId: 10,
    sectionId: 'A',
    role,
  };
}

function makeEnv(db: SchoolAdminAndAnnouncementDb): Env {
  return { DB: db as any } as Env;
}

function statusRequest(status: string): Request {
  return new Request('https://tanweer.test/api/v1/admin/users/student-1/status', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  });
}

describe('School Admin student management', () => {
  it('disables a student account and revokes all of its active sessions', async () => {
    const db = new SchoolAdminAndAnnouncementDb();
    const response = await handleUpdateSchoolStudentStatus(
      'student-1',
      makeUser('ADMIN', 'admin-1'),
      statusRequest('DISABLED'),
      makeEnv(db)
    );

    expect(response.status).toBe(200);
    expect(db.users.find(row => row.id === 'student-1')?.status).toBe('DISABLED');
    expect(db.sessions.filter(row => row.user_id === 'student-1')).toHaveLength(0);
    expect(db.sessions.filter(row => row.user_id === 'teacher-1')).toHaveLength(1);
    expect(db.contents).toHaveLength(1);
  });

  it('reactivates a student without deleting or recreating their account', async () => {
    const db = new SchoolAdminAndAnnouncementDb();
    db.users.find(row => row.id === 'student-1')!.status = 'DISABLED';
    const response = await handleUpdateSchoolStudentStatus(
      'student-1',
      makeUser('ADMIN', 'admin-1'),
      statusRequest('ACTIVE'),
      makeEnv(db)
    );

    expect(response.status).toBe(200);
    expect(db.users.find(row => row.id === 'student-1')?.status).toBe('ACTIVE');
    expect(db.contents).toHaveLength(1);
  });

  it('does not allow teachers or ordinary students to change account status', async () => {
    const db = new SchoolAdminAndAnnouncementDb();
    for (const actor of [makeUser('TEACHER', 'teacher-1'), makeUser('STUDENT', 'student-1')]) {
      const response = await handleUpdateSchoolStudentStatus(
        'student-1',
        actor,
        statusRequest('DISABLED'),
        makeEnv(db)
      );
      expect(response.status).toBe(403);
    }
    expect(db.users.find(row => row.id === 'student-1')?.status).toBe('ACTIVE');
  });

  it('protects staff accounts from student status controls', async () => {
    const db = new SchoolAdminAndAnnouncementDb();
    const response = await handleUpdateSchoolStudentStatus(
      'teacher-1',
      makeUser('ADMIN', 'admin-1'),
      statusRequest('DISABLED'),
      makeEnv(db)
    );

    expect(response.status).toBe(403);
    expect(db.users.find(row => row.id === 'teacher-1')?.status).toBe('ACTIVE');
  });

  it('removes only the current class membership while preserving the account and content', async () => {
    const db = new SchoolAdminAndAnnouncementDb();
    const response = await handleRemoveSchoolStudentFromClass(
      'student-1',
      makeUser('ADMIN', 'admin-1'),
      makeEnv(db)
    );

    expect(response.status).toBe(200);
    expect(db.members.some(row => row.user_id === 'student-1')).toBe(false);
    expect(db.users.some(row => row.id === 'student-1')).toBe(true);
    expect(db.contents).toHaveLength(1);
    expect(db.members.some(row => row.user_id === 'teacher-1')).toBe(true);
  });
});

describe('Per-account school announcement dismissal', () => {
  it('hides a school announcement only for the requesting account', async () => {
    const db = new SchoolAdminAndAnnouncementDb();
    const userA = makeUser('STUDENT', 'student-a');
    const userB = makeUser('STUDENT', 'student-b');

    const dismissal = await handleDismissSchoolAnnouncement('announcement-1', userA, makeEnv(db));
    expect(dismissal.status).toBe(200);
    expect(db.events).toHaveLength(1);

    const [responseA, responseB] = await Promise.all([
      handleGetEvents('class_10_B', userA, makeEnv(db)),
      handleGetEvents('class_10_B', userB, makeEnv(db)),
    ]);
    const bodyA = await responseA.json() as { events: Array<{ id: string }> };
    const bodyB = await responseB.json() as { events: Array<{ id: string }> };

    expect(bodyA.events.some(event => event.id === 'announcement-1')).toBe(false);
    expect(bodyB.events.some(event => event.id === 'announcement-1')).toBe(true);
    expect(db.events).toHaveLength(1);
  });
});
