import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import {
  applyDecisionExecution,
  handleApproveCorrection,
  handleCreateContentCorrection,
} from '../src/services/governance';

type Correction = {
  id: string;
  content_id: string;
  group_id: string;
  user_id: string;
  field_name: string;
  proposed_value: string;
  reason: string;
  status: string;
  reviewed_by?: string | null;
  reviewed_at?: number | null;
};

class CorrectionDb {
  contents: Array<{
    id: string;
    group_id: string;
    title: string;
    description: string | null;
    study_date: string;
    updated_at: number;
  }> = [{
    id: 'content-1',
    group_id: 'group-1',
    title: 'عنوان قديم',
    description: 'وصف قديم',
    study_date: '2026-10-01',
    updated_at: 1,
  }];

  corrections: Correction[] = [];
  decisions: Array<{ id: string; status: string }> = [{ id: 'decision-1', status: 'PENDING' }];
  statements: Array<{ sql: string; params: unknown[] }> = [];

  prepare(sql: string) {
    const db = this;
    let params: unknown[] = [];
    const statement = {
      sql,
      bind(...values: unknown[]) {
        params = values;
        return statement;
      },
      async first<T = unknown>(): Promise<T | null> {
        const normalized = sql.trim().replace(/\s+/g, ' ');
        db.statements.push({ sql: normalized, params });

        if (normalized.includes('FROM contents WHERE id = ?')) {
          const row = db.contents.find((content) => content.id === params[0]);
          return (row ? { ...row } as T : null);
        }

        if (normalized.includes('FROM content_corrections WHERE id = ?')) {
          const correction = db.corrections.find((item) =>
            item.id === params[0] &&
            (!normalized.includes('AND group_id = ?') || item.group_id === params[1])
          );
          return (correction ? { ...correction } as T : null);
        }

        return null;
      },
      async run(): Promise<{ success: boolean; meta: { changes: number } }> {
        const normalized = sql.trim().replace(/\s+/g, ' ');
        db.statements.push({ sql: normalized, params });
        return db.execute(normalized, params);
      },
    };
    return statement;
  }

  async batch(statements: Array<{ sql: string; run: () => Promise<{ success: boolean; meta: { changes: number } }> }>) {
    const results = [];
    for (const statement of statements) results.push(await statement.run());
    return results;
  }

  execute(sql: string, params: unknown[]): { success: boolean; meta: { changes: number } } {
    if (sql.startsWith('INSERT INTO content_corrections')) {
      const [id, contentId, groupId, userId, authorName, fieldName, originalValue, proposedValue, reason, status] = params;
      this.corrections.push({
        id: String(id),
        content_id: String(contentId),
        group_id: String(groupId),
        user_id: String(userId),
        field_name: String(fieldName),
        proposed_value: String(proposedValue),
        reason: String(reason),
        status: String(status),
      });
      return { success: true, meta: { changes: 1 } };
    }

    if (sql.startsWith('UPDATE contents SET title = ?')) {
      const [value, updatedAt, contentId, groupId, correctionId, correctionGroupId] = params;
      const correction = this.corrections.find((item) =>
        item.id === correctionId && item.group_id === correctionGroupId && item.status === 'PENDING'
      );
      const content = this.contents.find((item) =>
        item.id === contentId && item.group_id === groupId
      );
      if (!correction || !content) return { success: true, meta: { changes: 0 } };
      content.title = String(value);
      content.updated_at = Number(updatedAt);
      return { success: true, meta: { changes: 1 } };
    }

    if (sql.startsWith('UPDATE contents SET description = ?')) {
      const [value, updatedAt, contentId, groupId, correctionId, correctionGroupId] = params;
      const correction = this.corrections.find((item) =>
        item.id === correctionId && item.group_id === correctionGroupId && item.status === 'PENDING'
      );
      const content = this.contents.find((item) =>
        item.id === contentId && item.group_id === groupId
      );
      if (!correction || !content) return { success: true, meta: { changes: 0 } };
      content.description = String(value);
      content.updated_at = Number(updatedAt);
      return { success: true, meta: { changes: 1 } };
    }

    if (sql.startsWith('UPDATE contents SET study_date = ?')) {
      const [value, updatedAt, contentId, groupId, correctionId, correctionGroupId] = params;
      const correction = this.corrections.find((item) =>
        item.id === correctionId && item.group_id === correctionGroupId && item.status === 'PENDING'
      );
      const content = this.contents.find((item) =>
        item.id === contentId && item.group_id === groupId
      );
      if (!correction || !content) return { success: true, meta: { changes: 0 } };
      content.study_date = String(value);
      content.updated_at = Number(updatedAt);
      return { success: true, meta: { changes: 1 } };
    }

    if (sql.startsWith('UPDATE contents SET description = COALESCE(description')) {
      const [value, updatedAt, contentId, groupId, correctionId, correctionGroupId] = params;
      const correction = this.corrections.find((item) =>
        item.id === correctionId && item.group_id === correctionGroupId && item.status === 'PENDING'
      );
      const content = this.contents.find((item) =>
        item.id === contentId && item.group_id === groupId
      );
      if (!correction || !content) return { success: true, meta: { changes: 0 } };
      content.description = (content.description ?? '') + String(value);
      content.updated_at = Number(updatedAt);
      return { success: true, meta: { changes: 1 } };
    }

    if (sql.startsWith('UPDATE content_corrections SET status = \'APPROVED\'')) {
      const [reviewerId, reviewedAt, correctionId, groupId, contentId, contentGroupId] = params;
      const correction = this.corrections.find((item) =>
        item.id === correctionId && item.group_id === groupId && item.status === 'PENDING'
      );
      const content = this.contents.find((item) =>
        item.id === contentId && item.group_id === contentGroupId
      );
      if (!correction || !content) return { success: true, meta: { changes: 0 } };
      correction.status = 'APPROVED';
      correction.reviewed_by = String(reviewerId);
      correction.reviewed_at = Number(reviewedAt);
      return { success: true, meta: { changes: 1 } };
    }

    if (sql.startsWith('UPDATE community_decisions SET status = \'APPLIED\'')) {
      const decision = this.decisions.find((item) => item.id === params[2]);
      if (!decision || decision.status !== 'PENDING') return { success: true, meta: { changes: 0 } };
      decision.status = 'APPLIED';
      return { success: true, meta: { changes: 1 } };
    }

    return { success: true, meta: { changes: 0 } };
  }
}

const owner: UserContext = {
  userId: 'SYSTEM_OWNER',
  phoneNumber: '__SYSTEM_OWNER__',
  fullName: 'مالك المنظومة',
  gradeId: 0,
  sectionId: 'SYSTEM',
  role: Role.SYSTEM_OWNER,
};

function createEnv(db: CorrectionDb): Env {
  return { DB: db as any } as Env;
}

function createCorrectionRequest(body: Record<string, unknown>): Request {
  return new Request('https://tanweer.test/api/v1/content/content-1/corrections', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

function seedCorrection(db: CorrectionDb, correction: Partial<Correction> = {}) {
  const row: Correction = {
    id: 'correction-1',
    content_id: 'content-1',
    group_id: 'group-1',
    user_id: 'student-1',
    field_name: 'TITLE',
    proposed_value: 'عنوان مصحح',
    reason: 'تصحيح خطأ مطبعي',
    status: 'PENDING',
    ...correction,
  };
  db.corrections.push(row);
  return row;
}

describe('Content correction execution safety', () => {
  it('rejects fields that have no implemented correction handler', async () => {
    const db = new CorrectionDb();
    const response = await handleCreateContentCorrection(
      'content-1',
      owner,
      createCorrectionRequest({
        fieldName: 'SUBJECT',
        proposedValue: 'math',
        reason: 'المادة غير صحيحة',
      }),
      createEnv(db),
    );

    expect(response.status).toBe(400);
    expect((await response.json() as any).error.code).toBe('INVALID_CORRECTION_FIELD');
    expect(db.corrections).toHaveLength(0);
  });

  it('rejects invalid study dates before creating a correction request', async () => {
    const db = new CorrectionDb();
    const response = await handleCreateContentCorrection(
      'content-1',
      owner,
      createCorrectionRequest({
        fieldName: 'DATE',
        proposedValue: '2026-02-31',
        reason: 'تصحيح التاريخ',
      }),
      createEnv(db),
    );

    expect(response.status).toBe(400);
    expect((await response.json() as any).error.code).toBe('INVALID_CORRECTION_VALUE');
    expect(db.corrections).toHaveLength(0);
  });

  it('applies a supported title correction and approves it in one D1 batch', async () => {
    const db = new CorrectionDb();
    seedCorrection(db, { field_name: 'TITLE', proposed_value: 'عنوان جديد' });

    const response = await handleApproveCorrection('correction-1', owner, createEnv(db));

    expect(response.status).toBe(200);
    expect(db.contents[0].title).toBe('عنوان جديد');
    expect(db.corrections[0].status).toBe('APPROVED');
    expect(db.corrections[0].reviewed_by).toBe('SYSTEM_OWNER');
    expect(db.statements.some((statement) => statement.sql.startsWith('UPDATE contents SET title = ?'))).toBe(true);
  });

  it('does not approve a historical correction with an unsupported field', async () => {
    const db = new CorrectionDb();
    seedCorrection(db, { field_name: 'SUBJECT', proposed_value: 'math' });

    const response = await handleApproveCorrection('correction-1', owner, createEnv(db));

    expect(response.status).toBe(400);
    expect((await response.json() as any).error.code).toBe('UNSUPPORTED_CORRECTION_FIELD');
    expect(db.contents[0].title).toBe('عنوان قديم');
    expect(db.corrections[0].status).toBe('PENDING');
    expect(db.statements.some((statement) => statement.sql.startsWith('UPDATE contents'))).toBe(false);
  });

  it('applies community-approved study-date corrections using the same field handler', async () => {
    const db = new CorrectionDb();
    seedCorrection(db, {
      field_name: 'STUDY_DATE',
      proposed_value: '2026-11-02',
    });

    const result = await applyDecisionExecution(
      'decision-1',
      'CORRECTION',
      'correction-1',
      'group-1',
      'SYSTEM_OWNER',
      createEnv(db),
    );

    expect(result.success).toBe(true);
    expect(db.contents[0].study_date).toBe('2026-11-02');
    expect(db.corrections[0].status).toBe('APPROVED');
    expect(db.decisions[0].status).toBe('APPLIED');
  });

  it('does not mark a community decision APPLIED when its correction no longer exists', async () => {
    const db = new CorrectionDb();

    const result = await applyDecisionExecution(
      'decision-1',
      'CORRECTION',
      'missing-correction',
      'group-1',
      'SYSTEM_OWNER',
      createEnv(db),
    );

    expect(result.success).toBe(false);
    expect(db.decisions[0].status).toBe('PENDING');
    expect(db.statements.some((statement) => statement.sql.startsWith('UPDATE community_decisions SET status = \'APPLIED\''))).toBe(false);
  });

  it('does not approve a correction when its content has been removed', async () => {
    const db = new CorrectionDb();
    seedCorrection(db);
    db.contents = [];

    const response = await handleApproveCorrection('correction-1', owner, createEnv(db));

    expect(response.status).toBe(409);
    expect(db.corrections[0].status).toBe('PENDING');
  });
});
