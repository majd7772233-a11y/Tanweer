import { describe, it, expect, beforeEach } from 'vitest';
import {
  handleGetCommunityDecisions,
  handleVoteCommunityDecision,
} from '../src/services/governance';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';

class VotingMockDb {
  public tables: Record<string, any[]> = {
    group_members: [],
    contents: [],
    content_media: [],
    community_decisions: [],
    community_votes: [],
  };

  prepare(sql: string) {
    const db = this;
    let boundParams: any[] = [];
    return {
      bind(...params: any[]) {
        boundParams = params;
        return this;
      },
      async first<T = any>(): Promise<T | null> {
        const res = db.execute(sql, boundParams);
        return (res[0] as T) || null;
      },
      async all<T = any>(): Promise<{ results: T[] }> {
        return { results: db.execute(sql, boundParams) as T[] };
      },
      async run(): Promise<{ success: boolean }> {
        db.execute(sql, boundParams);
        return { success: true };
      },
    };
  }

  execute(sql: string, params: any[]): any[] {
    const trimmed = sql.trim().replace(/\s+/g, ' ');

    if (/^INSERT OR REPLACE INTO community_votes/i.test(trimmed)) {
      const [decisionId, userId, voteChoice, createdAt] = params;
      this.tables.community_votes = this.tables.community_votes.filter(
        (v) => !(v.decision_id === decisionId && v.user_id === userId)
      );
      const row = { decision_id: decisionId, user_id: userId, vote_choice: voteChoice, created_at: createdAt };
      this.tables.community_votes.push(row);
      return [row];
    }

    if (/^DELETE/i.test(trimmed)) {
      const match = trimmed.match(/DELETE FROM (\w+)/i);
      if (match) {
        const table = match[1];
        if (trimmed.includes('content_id = ?')) {
          this.tables[table] = (this.tables[table] || []).filter((r) => r.content_id !== params[0]);
        } else if (trimmed.includes('WHERE id = ?')) {
          this.tables[table] = (this.tables[table] || []).filter((r) => r.id !== params[0]);
        }
        return [];
      }
    }

    if (/^UPDATE community_decisions/i.test(trimmed)) {
      const [votesFor, votesAgainst, status, decisionId] = params;
      const dec = this.tables.community_decisions.find((d) => d.id === decisionId);
      if (dec) {
        dec.votes_for = votesFor;
        dec.votes_against = votesAgainst;
        dec.status = status;
      }
      return [dec];
    }

    if (/^SELECT/i.test(trimmed)) {
      const fromMatch = trimmed.match(/FROM (\w+)/i);
      if (!fromMatch) return [];
      const table = fromMatch[1];
      let rows = [...(this.tables[table] || [])];

      if (table === 'group_members') {
        const groupId = params[0];
        const userId = params[1];
        return rows.filter((m) => m.group_id === groupId && m.user_id === userId && m.status === 'ACTIVE');
      }

      if (trimmed.includes('FROM community_votes WHERE decision_id = ?')) {
        const decisionId = params[0];
        const vFor = this.tables.community_votes.filter((v) => v.decision_id === decisionId && v.vote_choice === 1).length;
        const vAgainst = this.tables.community_votes.filter((v) => v.decision_id === decisionId && v.vote_choice === 0).length;
        return [{ v_for: vFor, v_against: vAgainst }];
      }

      if (table === 'community_decisions' && trimmed.includes('WHERE id = ?')) {
        return rows.filter((d) => d.id === params[0]);
      }

      if (table === 'community_decisions' && trimmed.includes('WHERE cd.group_id = ?')) {
        return rows.filter((d) => d.group_id === params[1] || d.group_id === params[0]);
      }

      return rows;
    }

    return [];
  }
}

describe('Unified Governance & Community Decisions Voting Engine', () => {
  let mockDb: VotingMockDb;
  let env: Env;

  const studentA: UserContext = {
    userId: 'usr_student_a',
    phoneNumber: '777000001',
    fullName: 'طالب أ',
    gradeId: 10,
    sectionId: 'A',
    role: Role.STUDENT,
  };

  const studentB: UserContext = {
    userId: 'usr_student_b',
    phoneNumber: '777000002',
    fullName: 'طالب ب',
    gradeId: 10,
    sectionId: 'A',
    role: Role.STUDENT,
  };

  const studentC: UserContext = {
    userId: 'usr_student_c',
    phoneNumber: '777000003',
    fullName: 'طالب ج',
    gradeId: 10,
    sectionId: 'A',
    role: Role.STUDENT,
  };

  beforeEach(() => {
    mockDb = new VotingMockDb();
    env = { DB: mockDb as any } as any;

    mockDb.tables.group_members.push(
      { group_id: 'class_10_A', user_id: studentA.userId, status: 'ACTIVE' },
      { group_id: 'class_10_A', user_id: studentB.userId, status: 'ACTIVE' },
      { group_id: 'class_10_A', user_id: studentC.userId, status: 'ACTIVE' }
    );

    mockDb.tables.contents.push({
      id: 'lesson_spam_1',
      group_id: 'class_10_A',
      title: 'منشور مكرر وغير لائق',
      created_by: 'usr_spammer',
    });

    // Decision to delete spam lesson: total eligible voters = 3, threshold = 66% (requires 2 FOR votes)
    mockDb.tables.community_decisions.push({
      id: 'dec_del_1',
      group_id: 'class_10_A',
      request_type: 'DELETION',
      target_id: 'lesson_spam_1',
      title: 'حذف المنشور المكرر المخالف',
      description: 'تم رفع نفس المحتوى 3 مرات بطريقة غير مفيدة',
      requested_by: studentA.userId,
      total_eligible_voters: 3,
      threshold_percent: 66,
      votes_for: 0,
      votes_against: 0,
      status: 'PENDING',
      created_at: Date.now(),
    });
  });

  it('records first vote and maintains PENDING status when threshold is not yet reached', async () => {
    const voteReq = new Request('http://localhost/api/v1/decisions/dec_del_1/vote', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voteChoice: 1 }), // Vote FOR
    });

    const res = await handleVoteCommunityDecision('dec_del_1', studentA, voteReq, env);
    expect(res.status).toBe(200);

    const data = await res.json() as any;
    expect(data.votesFor).toBe(1);
    expect(data.status).toBe('PENDING'); // 1 of 2 required votes -> still PENDING

    // Content should NOT be deleted yet
    expect(mockDb.tables.contents).toHaveLength(1);
  });

  it('automatically APPLIES action and executes deletion when threshold is reached', async () => {
    // Vote 1: Student A votes FOR
    await handleVoteCommunityDecision('dec_del_1', studentA, new Request('http://localhost/api/v1/decisions/dec_del_1/vote', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voteChoice: 1 }),
    }), env);

    // Vote 2: Student B votes FOR (reaches required quorum of 2/3)
    const res = await handleVoteCommunityDecision('dec_del_1', studentB, new Request('http://localhost/api/v1/decisions/dec_del_1/vote', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voteChoice: 1 }),
    }), env);

    expect(res.status).toBe(200);
    const data = await res.json() as any;
    expect(data.votesFor).toBe(2);
    expect(data.status).toBe('APPLIED'); // Automatically APPLIED!

    // Automatic Execution: The spam lesson is wiped out from contents table!
    expect(mockDb.tables.contents).toHaveLength(0);
  });

  it('SECURITY: Rejects votes on decisions that are already closed or applied', async () => {
    const dec = mockDb.tables.community_decisions.find((d) => d.id === 'dec_del_1');
    dec.status = 'APPLIED';

    const lateVoteReq = new Request('http://localhost/api/v1/decisions/dec_del_1/vote', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voteChoice: 1 }),
    });

    const res = await handleVoteCommunityDecision('dec_del_1', studentC, lateVoteReq, env);
    expect(res.status).toBe(400);

    const body = await res.json() as any;
    expect(body.error?.code || body.error).toBe('DECISION_CLOSED');
  });
});
