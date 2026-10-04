import { describe, it, expect, beforeEach } from 'vitest';
import { handleUpdateContent, handleDeleteContent } from '../src/services/content';
import {
  handleCreateContentCorrection,
  handleApproveCorrection,
  handleRejectCorrection,
} from '../src/services/governance';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';

class OwnershipMockDb {
  public tables: Record<string, any[]> = {
    users: [],
    contents: [],
    content_media: [],
    content_corrections: [],
    group_members: [],
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

    if (/^INSERT INTO/i.test(trimmed)) {
      const match = trimmed.match(/INSERT (?:OR IGNORE )?INTO (\w+)/i);
      if (match) {
        const table = match[1];
        if (!this.tables[table]) this.tables[table] = [];
        const colMatch = trimmed.match(/\((.*?)\)\s*VALUES/i);
        const colNames = colMatch ? colMatch[1].split(',').map((c) => c.replace(/["`\s]/g, '')) : null;
        const row: any = {};
        if (colNames) {
          colNames.forEach((c, idx) => { row[c] = params[idx]; });
        } else {
          params.forEach((v, idx) => { row[`c_${idx}`] = v; });
        }
        this.tables[table].push(row);
        return [row];
      }
    }

    if (/^UPDATE/i.test(trimmed)) {
      const match = trimmed.match(/UPDATE (\w+)/i);
      if (match) {
        const table = match[1];
        const rows = this.tables[table] || [];
        if (table === 'contents' && trimmed.includes('WHERE id = ?')) {
          const contentId = params[params.length - 1];
          const c = rows.find((r) => r.id === contentId);
          if (c) {
            if (params[0] !== null && params[0] !== undefined) c.title = params[0];
            if (params[1] !== null && params[1] !== undefined) c.description = params[1];
          }
        }
        if (table === 'content_corrections' && trimmed.includes('WHERE id = ?')) {
          const corrId = params[params.length - 1];
          const corr = rows.find((r) => r.id === corrId);
          if (corr) {
            if (trimmed.includes("status = 'APPROVED'")) corr.status = 'APPROVED';
            if (trimmed.includes("status = 'REJECTED'")) corr.status = 'REJECTED';
          }
        }
        return rows;
      }
    }

    if (/^DELETE/i.test(trimmed)) {
      const match = trimmed.match(/DELETE FROM (\w+)/i);
      if (match) {
        const table = match[1];
        if (table === 'contents' && trimmed.includes('WHERE id = ?')) {
          const id = params[0];
          this.tables[table] = (this.tables[table] || []).filter((r) => r.id !== id);
        }
        return [];
      }
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

      if (trimmed.includes('WHERE id = ?')) {
        rows = rows.filter((r) => r.id === params[0]);
      }
      return rows;
    }

    return [];
  }
}

describe('Content Ownership, Modification & Correction Hierarchy', () => {
  let mockDb: OwnershipMockDb;
  let env: Env;

  const originalAuthor: UserContext = {
    userId: 'usr_author_1',
    phoneNumber: '777000001',
    fullName: 'الكاتب الأصلي',
    gradeId: 10,
    sectionId: 'A',
    role: Role.STUDENT,
  };

  const otherStudent: UserContext = {
    userId: 'usr_other_student',
    phoneNumber: '777000002',
    fullName: 'طالب آخر',
    gradeId: 10,
    sectionId: 'A',
    role: Role.STUDENT,
  };

  const moderator: UserContext = {
    userId: 'usr_mod_1',
    phoneNumber: '777000003',
    fullName: 'مشرف الشعبة',
    gradeId: 10,
    sectionId: 'A',
    role: Role.MODERATOR,
  };

  beforeEach(() => {
    mockDb = new OwnershipMockDb();
    env = { DB: mockDb as any } as any;

    // Both are members of group class_10_A
    mockDb.tables.group_members.push(
      { group_id: 'class_10_A', user_id: originalAuthor.userId, status: 'ACTIVE', role: 'STUDENT' },
      { group_id: 'class_10_A', user_id: otherStudent.userId, status: 'ACTIVE', role: 'STUDENT' },
      { group_id: 'class_10_A', user_id: moderator.userId, status: 'ACTIVE', role: 'MODERATOR' }
    );

    // Initial content created by originalAuthor
    mockDb.tables.contents.push({
      id: 'lesson_math_1',
      group_id: 'class_10_A',
      title: 'درس التكامل المحدد',
      description: 'شرح مبسط لقوانين التكامل',
      created_by: originalAuthor.userId,
    });
  });

  it('Owner CAN successfully update their own content', async () => {
    const req = new Request('http://localhost/api/v1/content/lesson_math_1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: 'درس التكامل المحدد (محدث)',
        description: 'إضافة أمثلة وتمارين وزارية',
      }),
    });

    const res = await handleUpdateContent('lesson_math_1', originalAuthor, req, env);
    expect(res.status).toBe(200);

    const updated = mockDb.tables.contents.find((c) => c.id === 'lesson_math_1');
    expect(updated.title).toBe('درس التكامل المحدد (محدث)');
  });

  it('SECURITY: Non-owner student CANNOT update another user content (403 Forbidden)', async () => {
    const maliciousReq = new Request('http://localhost/api/v1/content/lesson_math_1', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: 'تعديل غير مصرح به من طالب آخر',
      }),
    });

    const res = await handleUpdateContent('lesson_math_1', otherStudent, maliciousReq, env);
    expect(res.status).toBe(403);

    const body = await res.json() as any;
    expect(body.error?.code || body.error).toBe('FORBIDDEN');

    // Title remains unchanged
    const content = mockDb.tables.contents.find((c) => c.id === 'lesson_math_1');
    expect(content.title).toBe('درس التكامل المحدد');
  });

  it('SECURITY: Non-owner student CANNOT delete another user content (403 Forbidden)', async () => {
    const res = await handleDeleteContent('lesson_math_1', otherStudent, env);
    expect(res.status).toBe(403);

    // Content still exists
    expect(mockDb.tables.contents).toHaveLength(1);
  });

  it('CORRECTION WORKFLOW: Non-owner can submit a correction request instead of direct mutation', async () => {
    const correctionReq = new Request('http://localhost/api/v1/content/lesson_math_1/corrections', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fieldName: 'TITLE',
        originalValue: 'درس التكامل المحدد',
        proposedValue: 'درس التكامل غير المحدد',
        reason: 'يوجد خطأ في عنوان الدرس، المسائل المعروضة تخص التكامل غير المحدد',
      }),
    });

    const res = await handleCreateContentCorrection('lesson_math_1', otherStudent, correctionReq, env);
    expect(res.status).toBe(200);

    const body = await res.json() as any;
    expect(body.success).toBe(true);
    expect(body.correctionId).toBeDefined();

    // Verify correction record is created in PENDING status
    const corr = mockDb.tables.content_corrections.find((c) => c.id === body.correctionId);
    expect(corr).toBeDefined();
    expect(corr.status).toBe('PENDING');
    expect(corr.proposed_value).toBe('درس التكامل غير المحدد');
  });

  it('MODERATION REVIEW: Moderator can review and approve correction, automatically applying changes to content', async () => {
    mockDb.tables.content_corrections.push({
      id: 'cor_123',
      content_id: 'lesson_math_1',
      group_id: 'class_10_A',
      user_id: otherStudent.userId,
      field_name: 'TITLE',
      proposed_value: 'درس التكامل المتقدم المعتمد',
      reason: 'تصويب العنوان',
      status: 'PENDING',
    });

    const approveRes = await handleApproveCorrection('cor_123', moderator, env);
    expect(approveRes.status).toBe(200);

    // Correction marked as APPROVED
    const corr = mockDb.tables.content_corrections.find((c) => c.id === 'cor_123');
    expect(corr.status).toBe('APPROVED');

    // Content title updated automatically
    const content = mockDb.tables.contents.find((c) => c.id === 'lesson_math_1');
    expect(content.title).toBe('درس التكامل المتقدم المعتمد');
  });
});
