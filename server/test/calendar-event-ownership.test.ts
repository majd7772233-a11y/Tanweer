import { describe, expect, it } from 'vitest';
import { handleGetCalendarOverview, handleGetDayDetail } from '../src/services/calendar';
import { Env, UserContext } from '../src/env';

const eventRow = {
  id: 'evt_calendar_owner',
  group_id: 'class_10_A',
  event_date: '2026-11-23',
  time_str: '09:00',
  title: 'Owner event',
  description: null,
  category: 'ACTIVITY',
  location: null,
  created_by: 'calendar_owner_1',
  created_at: 1795430000000,
};

function makeCalendarEnv(): Env {
  const db = {
    prepare(sql: string) {
      return {
        bind() {
          return this;
        },
        async all() {
          if (/FROM events/i.test(sql)) {
            const row = sql.includes('created_by') ? { ...eventRow } : {
              id: eventRow.id,
              group_id: eventRow.group_id,
              event_date: eventRow.event_date,
              time_str: eventRow.time_str,
              title: eventRow.title,
              description: eventRow.description,
              category: eventRow.category,
              location: eventRow.location,
              created_at: eventRow.created_at,
            };
            return { results: [row] };
          }
          return { results: [] };
        },
      };
    },
  };
  return { DB: db as any } as Env;
}

const adminUser: UserContext = {
  userId: 'calendar_admin',
  phoneNumber: '700000000',
  fullName: 'Calendar Admin',
  gradeId: 10,
  sectionId: 'A',
  role: 'ADMIN',
};

describe('Calendar event ownership contract', () => {
  it('preserves createdBy in the monthly calendar overview', async () => {
    const response = await handleGetCalendarOverview(
      'class_10_A',
      '2026-11',
      adminUser,
      makeCalendarEnv()
    );

    expect(response.status).toBe(200);
    const body = await response.json() as { events: Array<{ createdBy: string }> };
    expect(body.events).toHaveLength(1);
    expect(body.events[0].createdBy).toBe('calendar_owner_1');
  });

  it('preserves createdBy in the day-detail calendar response', async () => {
    const response = await handleGetDayDetail(
      'class_10_A',
      '2026-11-23',
      adminUser,
      makeCalendarEnv()
    );

    expect(response.status).toBe(200);
    const body = await response.json() as { events: Array<{ createdBy: string }> };
    expect(body.events).toHaveLength(1);
    expect(body.events[0].createdBy).toBe('calendar_owner_1');
  });
});