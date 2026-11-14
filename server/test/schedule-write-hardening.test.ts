import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import {
  handleCreateScheduleSlot,
  handleDeleteScheduleSlot,
  handleSaveScheduleBatch,
} from '../src/services/schedule';

type Version = {
  id: string;
  group_id: string;
  version_number: number;
  valid_from: string;
  valid_until: string | null;
  is_active: number;
  created_at: number;
  created_by: string;
};

type Slot = {
  id: string;
  version_id: string;
  day_of_week: number;
  slot_order: number;
  subject_id: string;
  start_time: string | null;
  end_time: string | null;
};

class ScheduleWriteDb {
  versions: Version[] = [];
  slots: Slot[] = [];
  statements: string[] = [];

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        return (db.query(sql, params)[0] as T | undefined) ?? null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        return { results: db.query(sql, params) as T[] };
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        const changes = db.execute(sql, params);
        return { success: true, meta: { changes } };
      },
    };
    return statement;
  }

  async batch(statements: Array<{ run: () => Promise<{ success: boolean; meta: { changes: number } }> }>) {
    const results = [];
    for (const statement of statements) {
      results.push(await statement.run());
    }
    return results;
  }

  private query(sql: string, params: unknown[]): any[] {
    const normalized = sql.trim().replace(/\s+/g, ' ');
    this.statements.push(normalized);

    if (normalized.startsWith('SELECT id, version_number FROM schedule_versions')) {
      const active = this.versions
        .filter(row => row.group_id === params[0] && row.is_active === 1)
        .sort((a, b) => b.version_number - a.version_number)[0];
      return active ? [{ id: active.id, version_number: active.version_number }] : [];
    }

    if (normalized.includes('MAX(version_number)') && normalized.includes('FROM schedule_versions')) {
      const relevant = this.versions.filter(row => row.group_id === params[0]);
      return [{ version_number: relevant.length ? Math.max(...relevant.map(row => row.version_number)) : null }];
    }

    if (normalized.includes('COUNT(*)') && normalized.includes('JOIN schedule_versions')) {
      const activeIds = new Set(this.versions
        .filter(row => row.group_id === params[0] && row.is_active === 1)
        .map(row => row.id));
      return [{ count: this.slots.filter(slot => activeIds.has(slot.version_id)).length }];
    }

    if (normalized.includes('COUNT(*)') && normalized.includes('FROM schedule_slots WHERE version_id = ?')) {
      return [{ count: this.slots.filter(slot => slot.version_id === params[0]).length }];
    }

    if (normalized.startsWith('SELECT id, start_time, end_time FROM schedule_slots')) {
      const slot = this.slots.find(row =>
        row.version_id === params[0] &&
        row.day_of_week === params[1] &&
        row.slot_order === params[2]
      );
      return slot ? [{ id: slot.id, start_time: slot.start_time, end_time: slot.end_time }] : [];
    }

    if (normalized.startsWith('SELECT id FROM schedule_versions WHERE group_id = ? AND is_active = 1')) {
      return this.versions
        .filter(row => row.group_id === params[0] && row.is_active === 1)
        .map(row => ({ id: row.id }));
    }

    return [];
  }

  private execute(sql: string, params: unknown[]): number {
    const normalized = sql.trim().replace(/\s+/g, ' ');
    this.statements.push(normalized);

    if (normalized.startsWith('UPDATE schedule_versions SET is_active = 0')) {
      const [marker, versionId, groupId] = params as [string, string, string];
      const version = this.versions.find(row =>
        row.id === versionId && row.group_id === groupId && row.is_active === 1
      );
      if (!version) return 0;
      version.is_active = 0;
      version.valid_until = marker;
      return 1;
    }

    if (normalized.startsWith('UPDATE schedule_versions SET valid_until = ?')) {
      const [finalDate, versionId, groupId, marker] = params as [string, string, string, string];
      const version = this.versions.find(row =>
        row.id === versionId && row.group_id === groupId &&
        row.is_active === 0 && row.valid_until === marker
      );
      if (!version) return 0;
      version.valid_until = finalDate;
      return 1;
    }

    if (normalized.startsWith('INSERT INTO schedule_versions')) {
      const [id, groupId, versionNumber, validFrom, createdAt, createdBy] = params as [
        string, string, number, string, number, string,
      ];

      if (normalized.includes('valid_until = ?')) {
        const oldVersionId = params[6];
        const oldGroupId = params[7];
        const marker = params[8];
        const oldVersion = this.versions.find(row =>
          row.id === oldVersionId &&
          row.group_id === oldGroupId &&
          row.is_active === 0 &&
          row.valid_until === marker
        );
        const hasActive = this.versions.some(row => row.group_id === params[9] && row.is_active === 1);
        if (!oldVersion || hasActive) return 0;
      } else {
        const guardGroup = params[6];
        const hasActive = this.versions.some(row => row.group_id === guardGroup && row.is_active === 1);
        if (hasActive) return 0;
      }

      this.versions.push({
        id, group_id: groupId, version_number: versionNumber,
        valid_from: validFrom, valid_until: null, is_active: 1,
        created_at: createdAt, created_by: createdBy,
      });
      return 1;
    }

    if (normalized.startsWith('INSERT INTO schedule_slots')) {
      const [
        id, versionId, day, order, subjectId, startTime, endTime,
        activeVersionId, groupId, positionVersionId, positionDay, positionOrder,
      ] = params as [
        string, string, number, number, string, string | null, string | null,
        string, string, string, number, number,
      ];

      const active = this.versions.some(row =>
        row.id === activeVersionId && row.group_id === groupId && row.is_active === 1
      );
      const occupied = this.slots.some(row =>
        row.version_id === positionVersionId &&
        row.day_of_week === positionDay &&
        row.slot_order === positionOrder
      );
      if (!active || occupied) return 0;
      this.slots.push({
        id, version_id: versionId, day_of_week: day, slot_order: order,
        subject_id: subjectId, start_time: startTime, end_time: endTime,
      });
      return 1;
    }

    if (normalized.startsWith('DELETE FROM schedule_slots')) {
      if (normalized.includes('WHERE id = ? AND version_id = ?')) {
        const [slotId, versionId, day, order, activeVersionId, groupId] = params;
        const active = this.versions.some(row =>
          row.id === activeVersionId && row.group_id === groupId && row.is_active === 1
        );
        if (!active) return 0;
        const before = this.slots.length;
        this.slots = this.slots.filter(row => !(
          row.id === slotId && row.version_id === versionId &&
          row.day_of_week === day && row.slot_order === order
        ));
        return before - this.slots.length;
      }

      if (normalized.includes('WHERE version_id = ?')) {
        const [versionId, day, order, activeVersionId, groupId] = params;
        const active = this.versions.some(row =>
          row.id === activeVersionId && row.group_id === groupId && row.is_active === 1
        );
        if (!active) return 0;
        const before = this.slots.length;
        this.slots = this.slots.filter(row => !(
          row.version_id === versionId &&
          row.day_of_week === day &&
          row.slot_order === order
        ));
        return before - this.slots.length;
      }
    }

    return 0;
  }
}

const admin: UserContext = {
  userId: 'admin-1',
  phoneNumber: '770000000',
  fullName: 'مدير المدرسة',
  gradeId: 10,
  sectionId: 'A',
  role: Role.ADMIN,
};

function envFor(db: ScheduleWriteDb): Env {
  return { DB: db as any } as Env;
}

function createSlotRequest(dayOfWeek: number, slotOrder: number, subjectId: string): Request {
  return new Request('https://tanweer.test/api/v1/schedule/group-1/slots', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ dayOfWeek, slotOrder, subjectId }),
  });
}

function batchRequest(slots: Array<{ dayOfWeek: number; slotOrder: number; subjectId: string }>): Request {
  return new Request('https://tanweer.test/api/v1/schedule/group-1/batch', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ slots }),
  });
}

describe('Schedule writes are serialized against active versions', () => {
  it('allows only one concurrent request to create the first active version and slot', async () => {
    const db = new ScheduleWriteDb();
    const [first, second] = await Promise.all([
      handleCreateScheduleSlot('group-1', admin, createSlotRequest(1, 1, 'math'), envFor(db)),
      handleCreateScheduleSlot('group-1', admin, createSlotRequest(2, 1, 'english'), envFor(db)),
    ]);

    expect([first.status, second.status].sort()).toEqual([200, 409]);
    expect(db.versions.filter(version => version.is_active === 1)).toHaveLength(1);
    expect(db.versions).toHaveLength(1);
    expect(db.slots).toHaveLength(1);
  });

  it('rejects duplicate positions within one full schedule payload', async () => {
    const db = new ScheduleWriteDb();
    const response = await handleSaveScheduleBatch('group-1', admin, batchRequest([
      { dayOfWeek: 1, slotOrder: 1, subjectId: 'math' },
      { dayOfWeek: 1, slotOrder: 1, subjectId: 'english' },
    ]), envFor(db));

    expect(response.status).toBe(400);
    expect((await response.json() as any).error.code).toBe('DUPLICATE_SCHEDULE_SLOT');
    expect(db.versions).toHaveLength(0);
    expect(db.slots).toHaveLength(0);
  });

  it('allows only one concurrent full-schedule save when no active version exists', async () => {
    const db = new ScheduleWriteDb();
    const [first, second] = await Promise.all([
      handleSaveScheduleBatch('group-1', admin, batchRequest([
        { dayOfWeek: 1, slotOrder: 1, subjectId: 'math' },
        { dayOfWeek: 1, slotOrder: 2, subjectId: 'english' },
      ]), envFor(db)),
      handleSaveScheduleBatch('group-1', admin, batchRequest([
        { dayOfWeek: 2, slotOrder: 1, subjectId: 'science' },
      ]), envFor(db)),
    ]);

    expect([first.status, second.status].sort()).toEqual([200, 409]);
    expect(db.versions.filter(version => version.is_active === 1)).toHaveLength(1);
    expect(db.versions).toHaveLength(1);
    expect(db.slots.length).toBeGreaterThan(0);
  });

  it('deletes a slot only while its version is still the active group version', async () => {
    const db = new ScheduleWriteDb();
    db.versions.push({
      id: 'version-old',
      group_id: 'group-1',
      version_number: 1,
      valid_from: '2026-10-01',
      valid_until: '2026-10-31',
      is_active: 0,
      created_at: 1,
      created_by: 'admin-1',
    });
    db.slots.push({
      id: 'slot-old',
      version_id: 'version-old',
      day_of_week: 1,
      slot_order: 1,
      subject_id: 'math',
      start_time: '08:00',
      end_time: '08:45',
    });

    const response = await handleDeleteScheduleSlot('group-1', 1, 1, admin, envFor(db));
    expect(response.status).toBe(404);
    expect(db.slots).toHaveLength(1);
  });
});
