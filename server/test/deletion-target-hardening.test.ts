import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import {
  applyDecisionExecution,
  handleCreateCommunityDecision,
} from '../src/services/governance';

type Resource = { id: string; group_id: string; title?: string };

class DeletionTargetDb {
  members = [
    { group_id: 'group-1', user_id: 'student-1', status: 'ACTIVE' },
    { group_id: 'group-1', user_id: 'student-2', status: 'ACTIVE' },
  ];
  resources: Record<'contents' | 'homeworks' | 'exams' | 'issues', Resource[]> = {
    contents: [],
    homeworks: [],
    exams: [],
    issues: [],
  };
  decisions: Array<Record<string, any>> = [];
  votes: Array<Record<string, any>> = [];
  statements: Array<{ sql: string; params: unknown[] }> = [];

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
        db.statements.push({ sql: normalized, params });
        if (normalized.includes('FROM group_members')) {
          return (db.members.find((row) =>
            row.group_id === params[0] &&
            row.user_id === params[1] &&
            row.status === 'ACTIVE'
          ) as T | undefined) ?? null;
        }

        if (normalized.includes('FROM community_decisions')) {
          const decision = db.decisions.find((row) =>
            row.id === params[0] &&
            (!normalized.includes('AND group_id = ?') || row.group_id === params[1])
          );
          return (decision ? { ...decision } as T : null);
        }

        const table = normalized.match(/FROM (contents|homeworks|exams|issues)/)?.[1] as
          'contents' | 'homeworks' | 'exams' | 'issues' | undefined;
        if (table) {
          const row = db.resources[table].find((resource) =>
            resource.id === params[0] && resource.group_id === params[1]
          );
          return (row ? { id: row.id } as T : null);
        }

        return null;
      },
      async all<T = unknown>(): Promise<{ results: T[] }> {
        db.statements.push({ sql: normalized, params });
        if (normalized.includes('FROM group_members') && normalized.includes('SELECT user_id')) {
          return {
            results: db.members.filter((row) =>
              row.group_id === params[0] && row.status === 'ACTIVE'
            ) as T[],
          };
        }
        return { results: [] as T[] };
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        db.statements.push({ sql: normalized, params });
        const changes = db.execute(normalized, params);
        return { success: true, meta: { changes } };
      },
    };
    return statement;
  }

  execute(sql: string, params: unknown[]): number {
    if (sql.startsWith('INSERT INTO community_decisions')) {
      const [
        id, groupId, requestType, targetId, title, description,
        requestedBy, requesterName, eligibleCount, thresholdPercent, expiresAt, createdAt,
      ] = params;
      this.decisions.push({
        id, group_id: groupId, request_type: requestType, target_id: targetId, title,
        description, requested_by: requestedBy, requester_name: requesterName,
        total_eligible_voters: eligibleCount, threshold_percent: thresholdPercent,
        votes_for: 1, votes_against: 0, status: 'PENDING', expires_at: expiresAt, created_at: createdAt,
      });
      return 1;
    }

    if (sql.startsWith('INSERT INTO community_votes')) {
      this.votes.push({
        decision_id: params[0], user_id: params[1], vote_choice: params[2], created_at: params[3],
      });
      return 1;
    }

    if (sql.startsWith('DELETE FROM ')) {
      const table = sql.match(/DELETE FROM (contents|homeworks|exams|issues)/)?.[1] as
        'contents' | 'homeworks' | 'exams' | 'issues' | undefined;
      if (!table) return 0;
      const before = this.resources[table].length;
      this.resources[table] = this.resources[table].filter((row) =>
        !(row.id === params[0] && row.group_id === params[1])
      );
      return before - this.resources[table].length;
    }

    if (sql.startsWith("UPDATE community_decisions SET status = 'APPLIED'")) {
      const decision = this.decisions.find((row) =>
        row.id === params[2] && row.group_id === params[3] && row.status === 'PENDING'
      );
      if (!decision) return 0;
      decision.status = 'APPLIED';
      decision.applied_at = params[0];
      decision.applied_by = params[1];
      return 1;
    }

    return 0;
  }
}

const student: UserContext = {
  userId: 'student-1',
  phoneNumber: '777000001',
  fullName: 'طالب تنوير',
  gradeId: 10,
  sectionId: 'A',
  role: Role.STUDENT,
};

function createEnv(db: DeletionTargetDb): Env {
  return { DB: db as any } as Env;
}

function createDecisionRequest(targetId: string): Request {
  return new Request('https://tanweer.test/api/v1/groups/group-1/decisions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      requestType: 'DELETION',
      targetId,
      title: 'حذف مورد مكرر',
      description: 'يوجد مورد مكرر ويجب حذفه',
    }),
  });
}

describe('Community deletion target type safety', () => {
  it('rejects creating a deletion decision when one ID matches resources in two tables', async () => {
    const db = new DeletionTargetDb();
    db.resources.contents.push({ id: 'shared-id', group_id: 'group-1', title: 'درس' });
    db.resources.homeworks.push({ id: 'shared-id', group_id: 'group-1', title: 'واجب' });

    const response = await handleCreateCommunityDecision(
      'group-1', student, createDecisionRequest('shared-id'), createEnv(db),
    );

    expect(response.status).toBe(409);
    expect((await response.json() as any).error.code).toBe('AMBIGUOUS_DELETE_TARGET');
    expect(db.decisions).toHaveLength(0);
  });

  it('stores the exact resource type in the decision metadata', async () => {
    const db = new DeletionTargetDb();
    db.resources.contents.push({ id: 'content-id', group_id: 'group-1', title: 'درس' });

    const response = await handleCreateCommunityDecision(
      'group-1', student, createDecisionRequest('content-id'), createEnv(db),
    );

    expect(response.status).toBe(200);
    expect(db.decisions).toHaveLength(1);
    const metadata = JSON.parse(db.decisions[0].description);
    expect(metadata.targetType).toBe('CONTENT');
    expect(metadata.voters).toContain('student-1');
  });

  it('refuses a legacy ambiguous decision without deleting either resource', async () => {
    const db = new DeletionTargetDb();
    db.resources.contents.push({ id: 'shared-id', group_id: 'group-1', title: 'درس' });
    db.resources.homeworks.push({ id: 'shared-id', group_id: 'group-1', title: 'واجب' });
    db.decisions.push({
      id: 'legacy-decision',
      group_id: 'group-1',
      description: 'old plain-text decision description',
      status: 'PENDING',
    });

    const result = await applyDecisionExecution(
      'legacy-decision', 'DELETION', 'shared-id', 'group-1', student.userId, createEnv(db),
    );

    expect(result.success).toBe(false);
    expect(result.message).toContain('أكثر من نوع');
    expect(db.resources.contents).toHaveLength(1);
    expect(db.resources.homeworks).toHaveLength(1);
    expect(db.decisions[0].status).toBe('PENDING');
    expect(db.statements.some((item) => item.sql.startsWith('DELETE FROM '))).toBe(false);
  });

  it('deletes only the declared resource type when IDs collide across tables', async () => {
    const db = new DeletionTargetDb();
    db.resources.contents.push({ id: 'shared-id', group_id: 'group-1', title: 'درس' });
    db.resources.homeworks.push({ id: 'shared-id', group_id: 'group-1', title: 'واجب' });
    db.decisions.push({
      id: 'typed-decision',
      group_id: 'group-1',
      description: JSON.stringify({ text: 'delete lesson', voters: ['student-1'], targetType: 'CONTENT' }),
      status: 'PENDING',
    });

    const result = await applyDecisionExecution(
      'typed-decision', 'DELETION', 'shared-id', 'group-1', student.userId, createEnv(db),
    );

    expect(result.success).toBe(true);
    expect(db.resources.contents).toHaveLength(0);
    expect(db.resources.homeworks).toHaveLength(1);
    expect(db.decisions[0].status).toBe('APPLIED');
  });
});
