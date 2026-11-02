import { describe, expect, it } from 'vitest';
import { handleUpdateEvent } from '../src/services/events';
import { Env, UserContext } from '../src/env';

type EventRow = {
  id: string;
  group_id: string;
  created_by: string;
  event_date: string;
  time_str: string | null;
  title: string;
  description: string | null;
  category: string;
  location: string | null;
  is_official: number;
};

class EventCrudMockDb {
  readonly event: EventRow = {
    id: 'evt_existing',
    group_id: 'class_10_A',
    created_by: 'student_owner',
    event_date: '2026-11-10',
    time_str: null,
    title: 'نشاط مدرسي',
    description: 'وصف النشاط',
    category: 'ACTIVITY',
    location: null,
    is_official: 0,
  };

  constructor(private readonly memberRole: string) {}

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const normalized = sql.trim().replace(/\s+/g, ' ');

    return {
      bind(...values: unknown[]) {
        params = values;
        return this;
      },
      async first<T = unknown>(): Promise<T | null> {
        if (/FROM events WHERE id = \?/i.test(normalized)) {
          return { ...db.event } as T;
        }
        if (/FROM group_members WHERE group_id = \?/i.test(normalized)) {
          return { role: db.memberRole, status: 'ACTIVE' } as T;
        }
        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        return { results: [] };
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        if (/^UPDATE events SET/i.test(normalized)) {
          const [eventDate, timeStr, title, description, category, location, official, eventId] = params;
          if (eventId !== db.event.id) return { success: true, meta: { changes: 0 } };
          if (eventDate !== null && eventDate !== undefined) db.event.event_date = String(eventDate);
          if (timeStr !== null && timeStr !== undefined) db.event.time_str = String(timeStr);
          if (title !== null && title !== undefined) db.event.title = String(title);
          if (description !== null && description !== undefined) db.event.description = String(description);
          if (category !== null && category !== undefined) db.event.category = String(category);
          if (location !== null && location !== undefined) db.event.location = String(location);
          if (official !== null && official !== undefined) db.event.is_official = Number(official);
          return { success: true, meta: { changes: 1 } };
        }
        return { success: true, meta: { changes: 0 } };
      },
    };
  }
}

function makeUser(role: string, userId = 'student_owner'): UserContext {
  return {
    userId,
    phoneNumber: '777000000',
    fullName: 'مستخدم الاختبار',
    gradeId: 10,
    sectionId: 'A',
    role,
  };
}

function makeEnv(db: EventCrudMockDb): Env {
  return { DB: db as any } as Env;
}

function makeRequest(body: Record<string, unknown>): Request {
  return new Request('https://tanweer.test/api/v1/events/evt_existing', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('Event CRUD authorization and holiday invariants', () => {
  it('prevents an ordinary event owner from converting their event into a school holiday', async () => {
    const db = new EventCrudMockDb('STUDENT');
    const user = makeUser('STUDENT');

    const response = await handleUpdateEvent(
      'evt_existing',
      user,
      makeRequest({ category: 'HOLIDAY', description: 'إجازة رسمية' }),
      makeEnv(db)
    );

    expect(response.status).toBe(403);
    expect(db.event.category).toBe('ACTIVITY');
    expect(db.event.is_official).toBe(0);
  });

  it('does not let an owner bypass holiday restrictions with an empty category field', async () => {
    const db = new EventCrudMockDb('STUDENT');
    db.event.category = 'HOLIDAY';
    db.event.is_official = 1;
    const user = makeUser('STUDENT');

    const response = await handleUpdateEvent(
      'evt_existing',
      user,
      makeRequest({ category: '', title: 'تعديل إجازة معتمدة' }),
      makeEnv(db)
    );

    expect(response.status).toBe(403);
    expect(db.event.title).toBe('نشاط مدرسي');
    expect(db.event.category).toBe('HOLIDAY');
  });

  it('requires a non-empty reason when an authorized user creates a holiday by updating an event', async () => {
    const db = new EventCrudMockDb('TEACHER');
    const user = makeUser('TEACHER', 'teacher_1');

    const response = await handleUpdateEvent(
      'evt_existing',
      user,
      makeRequest({ category: 'HOLIDAY', description: '   ' }),
      makeEnv(db)
    );

    expect(response.status).toBe(400);
    expect(db.event.category).toBe('ACTIVITY');
  });

  it('marks an authorized holiday update as official even when isOfficial is omitted', async () => {
    const db = new EventCrudMockDb('TEACHER');
    const user = makeUser('TEACHER', 'teacher_1');

    const response = await handleUpdateEvent(
      'evt_existing',
      user,
      makeRequest({ category: 'holiday', description: 'إجازة مدرسية معتمدة' }),
      makeEnv(db)
    );

    expect(response.status).toBe(200);
    expect(db.event.category).toBe('HOLIDAY');
    expect(db.event.is_official).toBe(1);
  });
});
