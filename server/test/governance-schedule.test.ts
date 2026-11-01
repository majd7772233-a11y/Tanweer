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
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        const changes = db.execute(sql, params);
        return { success: true, meta: { changes } };
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
    const normalized = sql.trim().replace(/\s+/g, ' ');
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

  private execute(sql: string, params: unknown[]): number {
    const normalized = sql.trim().replace(/\s+/g, ' ');
    this.statements.push(normalized);

    if (normalized.startsWith("UPDATE schedule_proposals SET status = 'APPLYING'")) {
      const proposal = this.proposals.find((row) =>
        row.id === params[0] && row.group_id === params[1] && row.status === 'PENDING'
      );
      if (!proposal) return 0;
      proposal.status = 'APPLYING';
      return 1;
    }

    if (normalized.startsWith('UPDATE schedule_versions SET is_active = 0')) {
      const [validUntil, versionId, groupId, proposalId, proposalGroupId] = params as [
        string, string, string, string, string,
      ];
      const proposal = this.proposals.find((row) =>
        row.id === proposalId && row.group_id === proposalGroupId && row.status === 'APPLYING'
      );
      const version = this.versions.find((row) =>
        row.id === versionId && row.group_id === groupId && row.is_active === 1
      );
      if (!proposal || !version) return 0;
      version.is_active = 0;
      version.valid_until = validUntil;
      return 1;
    }

    if (normalized.startsWith('INSERT INTO schedule_versions')) {
      const [id, groupId, versionNumber, validFrom, createdAt, createdBy, proposalId, proposalGroupId, guardGroupId] = params as [
        string, string, number, string, number, string, string, string, string,
      ];
      const proposal = this.proposals.find((row) =>
        row.id === proposalId && row.group_id === proposalGroupId && row.status === 'APPLYING'
      );
      const hasActive = this.versions.some((row) => row.group_id === guardGroupId && row.is_active === 1);
      if (!proposal || hasActive) return 0;
      this.versions.push({
        id, group_id: groupId, version_number: versionNumber, valid_from: validFrom,
        valid_until: null, is_active: 1, created_at: createdAt, created_by: createdBy,
      });
      return 1;
    }

    if (normalized.startsWith('INSERT INTO schedule_slots')) {
      const [id, versionId, day, order, subjectId, startTime, endTime, guardedVersionId, groupId] = params as [
        string, string, number, number, string, string | null, string | null, string, string,
      ];
      const version = this.versions.find((row) =>
        row.id === guardedVersionId && row.id === versionId && row.group_id === groupId && row.is_active === 1
      );
      if (!version) return 0;
      this.slots.push({
        id, version_id: versionId, day_of_week: day, slot_order: order,
        subject_id: subjectId, start_time: startTime, end_time: endTime,
      });
      return 1;
    }

    if (normalized.startsWith("UPDATE schedule_proposals SET status = 'ACCEPTED'")) {
      const [proposalId, groupId, versionId, versionGroupId] = params as [string, string, string, string];
      const proposal = this.proposals.find((row) =>
        row.id === proposalId && row.group_id === groupId && row.status === 'APPLYING'
      );
      const version = this.versions.find((row) =>
        row.id === versionId && row.group_id === versionGroupId && row.is_active === 1
      );
      if (!proposal || !version) return 0;
      proposal.status = 'ACCEPTED';
      return 1;
    }

    if (normalized.startsWith("UPDATE schedule_proposals SET status = 'CONFLICTED'")) {
      const [proposalId, groupId, versionId, versionGroupId] = params as [string, string, string, string];
      const proposal = this.proposals.find((row) => row.id === proposalId);
      if (!proposal) return 0;

      if (params.length === 1) {
        if (proposal.status !== 'PENDING') return 0;
        proposal.status = 'CONFLICTED';
        return 1;
      }

      const version = this.versions.find((row) =>
        row.id === versionId && row.group_id === versionGroupId && row.is_active === 1
      );
      if (proposal.group_id !== groupId || proposal.status !== 'APPLYING' || version) return 0;
      proposal.status = 'CONFLICTED';
      return 1;
    }

    if (normalized.startsWith("UPDATE community_decisions SET status = 'APPLIED'")) {
      const [appliedAt, appliedBy, decisionId, groupId] = params as [number, string, string, string];
      if (this.decision.id !== decisionId || this.decision.group_id !== groupId || this.decision.status !== 'PENDING') return 0;
      this.decision.status = 'APPLIED';
      this.decision.applied_at = appliedAt;
      this.decision.applied_by = appliedBy;
      return 1;
    }

    if (normalized.startsWith("UPDATE community_decisions SET status = 'REJECTED'")) {
      const [decisionId, groupId] = params as [string, string];
      if (this.decision.id !== decisionId || this.decision.group_id !== groupId) return 0;
      this.decision.status = 'REJECTED';
      return 1;
    }

    return 0;
  }}

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

  it('allows only one concurrent proposal to create the next active schedule version', async () => {
    const db = new ScheduleDecisionDb();
    db.proposals.push({
      id: 'proposal-2',
      group_id: 'group-1',
      proposed_by: 'student-2',
      day_of_week: 2,
      slot_order: 1,
      old_subject_id: 'english',
      new_subject_id: 'physics',
      status: 'PENDING',
    });

    const [first, second] = await Promise.all([
      applyScheduleProposalToActiveSchedule(db.proposals[0], 'student-1', createEnv(db)),
      applyScheduleProposalToActiveSchedule(db.proposals[1], 'student-2', createEnv(db)),
    ]);

    const activeVersions = db.versions.filter((version) => version.is_active === 1);
    expect(activeVersions).toHaveLength(1);
    expect(db.versions).toHaveLength(2);
    expect([first, second].filter((result) => result.success)).toHaveLength(1);
    expect(db.proposals.filter((proposal) => proposal.status === 'ACCEPTED')).toHaveLength(1);
    expect(db.proposals.some((proposal) => proposal.status === 'CONFLICTED')).toBe(true);
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
