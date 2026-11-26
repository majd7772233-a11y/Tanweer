import { describe, expect, it } from 'vitest';
import { handleDismissSchoolAnnouncement, handleGetEvents } from '../src/services/events';
import { Env, UserContext } from '../src/env';

type Announcement = {
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

class AnnouncementDismissalDb {
  events: Announcement[] = [{
    id: 'announcement-1',
    group_id: 'all',
    event_date: '2026-11-05',
    time_str: null,
    title: 'تعميم المدرسة',
    description: 'تنبيه لجميع الطلاب',
    category: 'ANNOUNCEMENT',
    location: null,
    is_official: 1,
    created_by: 'school-admin',
    created_at: 1,
    source_name: 'إدارة المدرسة',
  }];
  memberships = new Set(['student-a:class_10_A', 'student-b:class_10_A']);
  dismissed = new Set<string>();

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
        if (normalized.includes('FROM group_members WHERE group_id = ?')) {
          const key = `${String(params[1])}:${String(params[0])}`;
          return db.memberships.has(key) ? ({ role: 'STUDENT', status: 'ACTIVE' } as T) : null;
        }
        if (normalized.includes('FROM events WHERE id = ?') && normalized.includes("group_id = 'all'")) {
          const event = db.events.find(item =>
            item.id === params[0] &&
            item.group_id === 'all' &&
            item.category.toUpperCase() === 'ANNOUNCEMENT'
          );
          return event ? ({ id: event.id } as T) : null;
        }
        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        if (normalized.includes('FROM events e') && normalized.includes('user_dismissed_announcements')) {
          const [groupId, userId] = params.map(String);
          const results = db.events.filter(event => {
            const inScope = event.group_id === groupId || event.group_id === 'all';
            const dismissed = event.group_id === 'all' &&
              event.category.toUpperCase() === 'ANNOUNCEMENT' &&
              db.dismissed.has(`${userId}:${event.id}`);
            return inScope && !dismissed;
          });
          return { results: results as T[] };
        }
        return { results: [] };
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        if (normalized.startsWith('INSERT OR IGNORE INTO user_dismissed_announcements')) {
          const [userId, eventId] = params.map(String);
          const before = db.dismissed.size;
          db.dismissed.add(`${userId}:${eventId}`);
          return { success: true, meta: { changes: db.dismissed.size - before } };
        }
        return { success: true, meta: { changes: 0 } };
      },
    };
  }
}

function user(userId: string): UserContext {
  return {
    userId,
    phoneNumber: '777000000',
    fullName: userId,
    gradeId: 10,
    sectionId: 'A',
    role: 'STUDENT',
  };
}

function env(db: AnnouncementDismissalDb): Env {
  return { DB: db as any } as Env;
}

describe('Per-account school announcement dismissal', () => {
  it('hides a school circular only for the student who dismissed it', async () => {
    const db = new AnnouncementDismissalDb();

    const dismiss = await handleDismissSchoolAnnouncement('announcement-1', user('student-a'), env(db));
    expect(dismiss.status).toBe(200);
    expect(await dismiss.json()).toMatchObject({ success: true });

    const studentAEvents = await handleGetEvents('class_10_A', user('student-a'), env(db));
    const studentBEvents = await handleGetEvents('class_10_A', user('student-b'), env(db));

    expect((await studentAEvents.json() as any).events).toHaveLength(0);
    expect((await studentBEvents.json() as any).events).toHaveLength(1);
    expect(db.events).toHaveLength(1);
  });

  it('is idempotent when the same account presses X more than once', async () => {
    const db = new AnnouncementDismissalDb();

    const first = await handleDismissSchoolAnnouncement('announcement-1', user('student-a'), env(db));
    const second = await handleDismissSchoolAnnouncement('announcement-1', user('student-a'), env(db));

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(db.dismissed.size).toBe(1);
  });

  it('does not allow dismissing a non-school event or unknown event', async () => {
    const db = new AnnouncementDismissalDb();
    db.events[0].group_id = 'class_10_A';
    db.events[0].category = 'ACTIVITY';

    const response = await handleDismissSchoolAnnouncement('announcement-1', user('student-a'), env(db));

    expect(response.status).toBe(404);
    expect(db.dismissed.size).toBe(0);
  });

  it('still requires active membership to read group events', async () => {
    const db = new AnnouncementDismissalDb();
    db.memberships.delete('student-a:class_10_A');

    const response = await handleGetEvents('class_10_A', user('student-a'), env(db));

    expect(response.status).toBe(403);
  });
});
