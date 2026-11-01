import { describe, expect, it } from 'vitest';
import { Env } from '../src/env';
import { applyDecisionExecution } from '../src/services/governance';

type ProposalRow = {
  id: string;
  group_id: string;
  proposed_by: string;
  day_of_week: number;
  slot_order: number;
  old_subject_id: string | null;
  new_subject_id: string;
  status: string;
};

type VersionRow = {
  id: string;
  group_id: string;
  version_number: number;
  valid_from: string;
  valid_until: string | null;
  is_active: number;
  created_at: number;
  created_by: string;
};

type SlotRow = {
  id: string;
  version_id: string;
  day_of_week: number;
  slot_order: number;
  subject_id: string;
  start_time: string | null;
  end_time: string | null;
};

class ScheduleDecisionDb {
  statements: string[] = [];
  proposals: ProposalRow[] = [{
    id: 'proposal-1',
    group_id: 'group-1',
    proposed_by: 'student-1',
    day_of_week: 1,
    slot_order: 1,
    old_subject_id: 'math',
    new_subject_id: 'chemistry',
    status: 'PENDING',
  }];
  versions: VersionRow[] = [{
    id: 'version-1',
    group_id: 'group-1',
    version_number: 1,
    valid_from: '2026-10-01',
    valid_until: null,
    is_active: 1,
    created_at: 1,
    created_by: 'admin-1',
  }];
  slots: SlotRow[] = [
    {
      id: 'slot-math',
      version_id: 'version-1',
      day_of_week: 1,
      slot_order: 1,
      subject_id: 'math',
      start_time: '08:00',
      end_time: '08:45',
    },
    {
      id: 'slot-english',
      version_id: 'version-1',
      day_of_week: 2,
      slot_order: 1,
      subject_id: 'english',
      start_time: '08:00',
      end_time: '08:45',
    },
  ];
  decision = {
    id: 'decision-1',
    group_id: 'group-1',
    status: 'PENDING',
    applied_by: null as string | null,
    applied_at: null as number | null,
  };

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
      async run(): Promise<{ success: boolean }> {
        db.execute(sql, params);
        return { success: true };
      },
    };
    return statement as any;
  }

  async batch(statements: any[]): Promise<unknown[]> {
    const results: unknown[] = [];
    for (const statement of statements) {
      results.push(await statement.run());
    }
    return results;
  }

  private query(sql: string, params: unknown[]): any[] {
    const normalized = sql.trim().replace(/\\s+/g, ' ');
    this.statements.push(normalized);

    if (normalized.includes('FROM schedule_proposals WHERE id = ? AND group_id = ?')) {
      return this.proposals.filter((row) => row.id === params[0] && row.group_id === params[1]);
    }

    if (normalized.includes('FROM schedule_proposals sp LEFT JOIN schedule_versions sv')) {
      const proposal = this.proposals.find((row) => row.id === params[0]);
      const activeVersion = this.versions.find((row) => row.group_id === proposal?.group_id && row.is_active === 1);
      return proposal ? [{
        status: proposal.status,
        version_number: activeVersion?.version_number ?? null,
      }] : [];
    }

    if (normalized.startsWith('SELECT id, version_number FROM schedule_versions')) {
      const activeVersion = this.versions.find((row) => row.group_id === params[0] && row.is_active === 1);
      return activeVersion ? [{ id: activeVersion.id, version_number: activeVersion.version_number }] : [];
    }

    if (normalized.startsWith('SELECT day_of_week, slot_order, subject_id, start_time, end_time FROM schedule_slots')) {
      return this.slots
        .filter((row) => row.version_id === params[0])
        .map(({ day_of_week, slot_order, subject_id, start_time, end_time }) => ({
          day_of_week, slot_order, subject_id, start_time, end_time,
        }));
    }

    return [];
  }

  private execute(sql: string, params: unknown[]): void {
    const normalized = sql.trim().replace(/\\s+/g, ' ');
    this.statements.push(normalized);

    if (/UPDATE schedule_proposals/i.test(normalized) && /\\b(reviewed_by|reviewed_at|subject_id)\\b/i.test(normalized)) {
      throw new Error('no such column in schedule_proposals');
    }

    if (normalized.startsWith('UPDATE schedule_versions SET is_active = 0')) {
      const [validUntil, versionId] = params as [string, string];
      const version = this.versions.find((row) => row.id === versionId);
      if (version) {
        version.is_active = 0;
        version.valid_until = validUntil;
      }
      return;
    }

    if (normalized.startsWith('INSERT INTO schedule_versions')) {
      const [id, groupId, versionNumber, validFrom, createdAt, createdBy] = params as [
        string, string, number, string, number, string,
      ];
      this.versions.push({
        id, group_id: groupId, version_number: versionNumber, valid_from: validFrom,
        valid_until: null, is_active: 1, created_at: createdAt, created_by: createdBy,
      });
      return;
    }

    if (normalized.startsWith('INSERT INTO schedule_slots')) {
      const [id, versionId, day, order, subjectId, startTime, endTime] = params as [
        string, string, number, number, string, string | null, string | null,
      ];
      this.slots.push({
        id, version_id: versionId, day_of_week: day, slot_order: order,
        subject_id: subjectId, start_time: startTime, end_time: endTime,
      });
      return;
    }

    if (normalized.startsWith("UPDATE schedule_proposals SET status = 'ACCEPTED'")) {
      const proposal = this.proposals.find((row) => row.id === params[0] && row.status === 'PENDING');
      if (proposal) proposal.status = 'ACCEPTED';
      return;
    }

    if (normalized.startsWith("UPDATE schedule_proposals SET status = 'CONFLICTED'")) {
      const proposal = this.proposals.find((row) => row.id === params[0]);
      if (proposal) proposal.status = 'CONFLICTED';
      return;
    }

    if (normalized.startsWith("UPDATE community_decisions SET status = 'APPLIED'")) {
      const [appliedAt, appliedBy, decisionId, groupId] = params as [number, string, string, string];
      if (this.decision.id === decisionId && this.decision.group_id === groupId) {
        this.decision.status = 'APPLIED';
        this.decision.applied_at = appliedAt;
        this.decision.applied_by = appliedBy;
      }
      return;
    }

    if (normalized.startsWith("UPDATE community_decisions SET status = 'REJECTED'")) {
      const [decisionId, groupId] = params as [string, string];
      if (this.decision.id === decisionId && this.decision.group_id === groupId) {
        this.decision.status = 'REJECTED';
      }
    }
  }
}

function createEnv(db: ScheduleDecisionDb): Env {
  return { DB: db as any } as Env;
}

describe('Schedule community decision execution', () => {
  it('applies an accepted schedule proposal through a new version without invalid proposal columns', async () => {
    const db = new ScheduleDecisionDb();

    const result = await applyDecisionExecution(
      'decision-1',
      'SCHEDULE_CHANGE',
      'proposal-1',
      'group-1',
      'student-2',
      createEnv(db),
    );

    expect(result.success).toBe(true);
    expect(db.proposals[0].status).toBe('ACCEPTED');
    expect(db.decision.status).toBe('APPLIED');
    expect(db.decision.applied_by).toBe('student-2');

    const activeVersions = db.versions.filter((version) => version.is_active === 1);
    expect(activeVersions).toHaveLength(1);
    expect(activeVersions[0].version_number).toBe(2);

    const activeSlots = db.slots.filter((slot) => slot.version_id === activeVersions[0].id);
    expect(activeSlots).toHaveLength(2);
    expect(activeSlots).toContainEqual(expect.objectContaining({
      day_of_week: 1,
      slot_order: 1,
      subject_id: 'chemistry',
      start_time: '08:00',
      end_time: '08:45',
    }));
    expect(activeSlots).toContainEqual(expect.objectContaining({
      day_of_week: 2,
      slot_order: 1,
      subject_id: 'english',
    }));

    expect(db.statements.some((sql) => /reviewed_by|reviewed_at/.test(sql))).toBe(false);
    expect(db.statements.some((sql) => /UPDATE schedule_slots SET subject_id/.test(sql))).toBe(false);
  });

  it('does not mark a conflicted proposal as applied or create another schedule version', async () => {
    const db = new ScheduleDecisionDb();
    db.slots[0].subject_id = 'biology';

    const result = await applyDecisionExecution(
      'decision-1',
      'SCHEDULE',
      'proposal-1',
      'group-1',
      'student-2',
      createEnv(db),
    );

    expect(result.success).toBe(false);
    expect(result.conflict).toBe(true);
    expect(db.proposals[0].status).toBe('CONFLICTED');
    expect(db.decision.status).toBe('REJECTED');
    expect(db.versions).toHaveLength(1);
    expect(db.slots).toHaveLength(2);
  });
});
