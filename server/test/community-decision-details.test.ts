import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { handleGetCommunityDecisions } from '../src/services/governance';

class CommunityDecisionDetailsDb {
  readonly statements: string[] = [];

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        db.statements.push(sql.trim().replace(/\\s+/g, ' '));
        return { role: 'STUDENT', status: 'ACTIVE' } as T;
      },
      async run() {
        db.statements.push(sql.trim().replace(/\\s+/g, ' '));
        return { success: true, meta: { changes: 0 } };
      },
      async all<T = unknown>() {
        db.statements.push(sql.trim().replace(/\\s+/g, ' '));
        return {
          results: [{
            id: 'decision-schedule-1',
            group_id: 'group-1',
            request_type: 'SCHEDULE',
            target_id: 'proposal-1',
            title: 'اقتراح تعديل الجدول • الحصة 2',
            description: JSON.stringify({
              text: 'تعارض موعد الحصة مع نشاط مدرسي',
              voters: ['student-1', 'student-2', 'student-3'],
            }),
            requested_by: 'student-1',
            requester_name: 'طالب الاختبار',
            total_eligible_voters: 3,
            threshold_percent: 60,
            votes_for: 2,
            votes_against: 1,
            status: 'PENDING',
            my_vote: 1,
            expires_at: Date.now() + 60_000,
            created_at: Date.now(),
            schedule_day_of_week: 1,
            schedule_slot_order: 2,
            schedule_old_subject_id: 'math',
            schedule_new_subject_id: 'science',
            schedule_old_subject_name: 'الرياضيات',
            schedule_new_subject_name: 'العلوم',
            schedule_new_subject_icon: '🔬',
          }] as T[],
        };
      },
    };
    return statement;
  }
}

describe('Unified schedule decision details', () => {
  it('returns the requested slot, old/new subject and reason without exposing the eligible voter snapshot', async () => {
    const db = new CommunityDecisionDetailsDb();
    const user = {
      userId: 'student-2',
      phoneNumber: '777000002',
      fullName: 'طالب الاختبار',
      gradeId: 10,
      sectionId: 'A',
      role: 'STUDENT',
    } as UserContext;

    const response = await handleGetCommunityDecisions(
      'group-1',
      user,
      { DB: db as any } as Env,
    );
    const body = await response.json() as {
      success: boolean;
      decisions: Array<{ id: string; description: string; votesFor: number; votesAgainst: number; status: string }>;
    };

    expect(response.status).toBe(200);
    expect(body.success).toBe(true);
    expect(body.decisions).toHaveLength(1);
    expect(body.decisions[0].description).toContain('اليوم: الإثنين • الحصة: 2');
    expect(body.decisions[0].description).toContain('المادة الحالية: الرياضيات ← المادة المقترحة: 🔬 العلوم');
    expect(body.decisions[0].description).toContain('السبب: تعارض موعد الحصة مع نشاط مدرسي');
    expect(body.decisions[0].description).not.toContain('student-1');
    expect(body.decisions[0].description).not.toContain('"voters"');
    expect(body.decisions[0].votesFor).toBe(2);
    expect(body.decisions[0].votesAgainst).toBe(1);
    expect(body.decisions[0].status).toBe('PENDING');
    expect(db.statements.some(sql => sql.includes('LEFT JOIN schedule_proposals sp'))).toBe(true);
  });
});
