import { describe, it, expect, beforeEach } from 'vitest';
import {
  verifyOwnerAuth,
  handlePreviewPurge,
  handleGetDashboardHomeworkDetail,
  handleTogglePinDashboardContent,
  handleGetDashboardExams,
  handleGetDashboardChatMessages,
  handleGetDashboardSchedules,
} from '../src/services/dashboard';
import { Env } from '../src/env';
import { hashString } from '../src/lib/crypto';

class TestMockDb {
  public tables: Record<string, any[]> = {
    users: [],
    homeworks: [],
    homework_media: [],
    exams: [],
    contents: [],
    chat_messages: [],
    schedule_slots: [],
    groups: [],
    subjects: [],
    owner_sessions: [],
    system_audit_logs: [],
    role_audit_logs: [],
    admin_audit_logs: [],
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
      async run(): Promise<{ success: boolean; changes?: number }> {
        db.execute(sql, boundParams);
        return { success: true, changes: 1 };
      },
    };
  }

  execute(sql: string, params: any[]): any[] {
    const trimmed = sql.trim().replace(/\s+/g, ' ');

    if (/^SELECT is_pinned FROM contents/i.test(trimmed)) {
      const c = this.tables.contents.find(row => row.id === params[0]);
      return c ? [{ is_pinned: c.is_pinned ?? 0 }] : [];
    }

    if (/^UPDATE contents SET is_pinned/i.test(trimmed)) {
      const c = this.tables.contents.find(row => row.id === params[1]);
      if (c) c.is_pinned = params[0];
      return [];
    }

    if (/^SELECT COUNT\(\*\)/i.test(trimmed)) {
      if (/FROM contents/i.test(trimmed)) return [{ total: this.tables.contents.length, count: this.tables.contents.length }];
      if (/FROM homeworks/i.test(trimmed)) return [{ total: this.tables.homeworks.length, count: this.tables.homeworks.length }];
      if (/FROM exams/i.test(trimmed)) return [{ total: this.tables.exams.length, count: this.tables.exams.length }];
      if (/FROM chat_messages/i.test(trimmed)) return [{ total: this.tables.chat_messages.length, count: this.tables.chat_messages.length }];
      return [{ total: 0, count: 0 }];
    }

    if (/FROM homeworks h/i.test(trimmed) && /WHERE h\.id = \?/i.test(trimmed)) {
      const h = this.tables.homeworks.find(row => row.id === params[0]);
      if (!h) return [];
      const u = this.tables.users.find(row => row.id === h.created_by);
      const g = this.tables.groups.find(row => row.id === h.group_id);
      const s = this.tables.subjects.find(row => row.id === h.subject_id);
      return [{
        ...h,
        author_name: u?.full_name || 'Author',
        group_name: g?.name || 'Group',
        subject_name: s?.name_ar || 'Subject',
      }];
    }

    if (/FROM homework_media/i.test(trimmed)) {
      return this.tables.homework_media.filter(m => m.homework_id === params[0]);
    }

    if (/FROM exams e/i.test(trimmed)) {
      return this.tables.exams.map(e => ({
        ...e,
        subject_name: 'رياضيات',
        group_name: 'شعبة أ',
        author_name: 'أستاذ أحمد',
      }));
    }

    if (/FROM chat_messages cm/i.test(trimmed)) {
      return this.tables.chat_messages.map(m => ({
        id: m.id,
        group_id: m.group_id,
        user_id: m.sender_id,
        content: m.text,
        message: m.text,
        created_at: m.timestamp,
        full_name: m.sender_name,
        group_name: 'شعبة أ',
      }));
    }

    if (/FROM schedule_slots/i.test(trimmed)) {
      return this.tables.schedule_slots;
    }

    if (/FROM groups/i.test(trimmed)) {
      return this.tables.groups;
    }

    if (/FROM owner_sessions/i.test(trimmed)) {
      const hash = params[0];
      return this.tables.owner_sessions.filter(s => s.token_hash === hash && !s.revoked);
    }

    if (/^INSERT INTO role_audit_logs/i.test(trimmed)) {
      this.tables.role_audit_logs.push(params);
      return [];
    }

    return [];
  }
}

describe('Dashboard Server Fixes (D-01 to D-07)', () => {
  let db: TestMockDb;
  let env: Env;

  beforeEach(() => {
    db = new TestMockDb();
    env = {
      DB: db as any,
      TANWEER_OWNER_SECRET: 'master_secret_12345',
      OWNER_SECRET: 'master_secret_12345',
      JWT_SECRET: 'test_jwt_secret',
      PASSWORD_PEPPER: 'test_pepper',
    } as any;
  });

  describe('D-01: System Purge Preview Secret Protection', () => {
    it('rejects purge preview with 403 if secret is missing or incorrect', async () => {
      const reqWrong = new Request('http://localhost/api/dashboard/system/purge/preview', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ secret: 'wrong_secret' }),
      });
      const res = await handlePreviewPurge(reqWrong, 'owner1', env);
      expect(res.status).toBe(403);
      const data = (await res.json()) as any;
      expect(data.error.code).toBe('INVALID_SECRET');
    });

    it('accepts purge preview with 200 when secret is correct', async () => {
      const reqCorrect = new Request('http://localhost/api/dashboard/system/purge/preview', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ secret: 'master_secret_12345' }),
      });
      const res = await handlePreviewPurge(reqCorrect, 'owner1', env);
      expect(res.status).toBe(200);
      const data = (await res.json()) as any;
      expect(data.success).toBe(true);
      expect(data.preview).toBeDefined();
      expect(data.fullyPurgedTables).toBeDefined();
    });
  });

  describe('D-02: Content Pinning Feature', () => {
    it('toggles is_pinned on content successfully', async () => {
      db.tables.contents.push({ id: 'c1', title: 'درس العلوم', is_pinned: 0 });
      const res = await handleTogglePinDashboardContent('c1', 'owner1', env);
      expect(res.status).toBe(200);
      const data = (await res.json()) as any;
      expect(data.success).toBe(true);
      expect(data.isPinned).toBe(1);
      expect(db.tables.contents[0].is_pinned).toBe(1);

      // Toggle back to 0
      const res2 = await handleTogglePinDashboardContent('c1', 'owner1', env);
      const data2 = (await res2.json()) as any;
      expect(data2.isPinned).toBe(0);
      expect(db.tables.contents[0].is_pinned).toBe(0);
    });
  });

  describe('D-03: Chat Messages Query Mapping', () => {
    it('returns mapped chat messages with sender_name as full_name and text as message', async () => {
      db.tables.chat_messages.push({
        id: 'msg1',
        group_id: 'g1',
        sender_id: 'u1',
        sender_name: 'أحمد علي',
        text: 'السلام عليكم ورحمة الله',
        timestamp: Date.now(),
      });

      const req = new Request('http://localhost/api/dashboard/chat-messages?groupId=g1&order=DESC');
      const res = await handleGetDashboardChatMessages(req, env);
      expect(res.status).toBe(200);
      const data = (await res.json()) as any;
      expect(data.success).toBe(true);
      expect(data.messages.length).toBe(1);
      expect(data.messages[0].message).toBe('السلام عليكم ورحمة الله');
      expect(data.messages[0].full_name).toBe('أحمد علي');
    });
  });

  describe('D-05: Exams Query Safe Execution', () => {
    it('fetches exams without referencing non-existent description column', async () => {
      db.tables.exams.push({
        id: 'ex1',
        title: 'امتحان الرياضيات النهائي',
        subject_id: 's1',
        group_id: 'g1',
        exam_date: '2026-11-01',
        exam_type: 'FINAL',
        required_chapters: 'الفصل 1 إلى 3',
        notes: 'ممنوع الآلة الحاسبة',
        created_by: 'u1',
      });

      const req = new Request('http://localhost/api/dashboard/exams?q=الرياضيات');
      const res = await handleGetDashboardExams(req, env);
      expect(res.status).toBe(200);
      const data = (await res.json()) as any;
      expect(data.success).toBe(true);
      expect(data.exams.length).toBe(1);
      expect(data.exams[0].title).toBe('امتحان الرياضيات النهائي');
    });
  });

  describe('D-06: Homework Detail & Media Modal Endpoint', () => {
    it('returns homework details with media items', async () => {
      db.tables.homeworks.push({
        id: 'hw1',
        title: 'واجب الفيزياء 1',
        group_id: 'g1',
        subject_id: 's1',
        due_date: '2026-11-05',
        details: 'حل التمارين ص 45',
        created_by: 'u1',
      });
      db.tables.homework_media.push({
        id: 'm1',
        homework_id: 'hw1',
        media_id: 'med1',
        url: 'https://example.com/hw1.jpg',
        created_at: Date.now(),
      });

      const res = await handleGetDashboardHomeworkDetail('hw1', env);
      expect(res.status).toBe(200);
      const data = (await res.json()) as any;
      expect(data.success).toBe(true);
      expect(data.homework.title).toBe('واجب الفيزياء 1');
      expect(data.media.length).toBe(1);
      expect(data.media[0].url).toBe('https://example.com/hw1.jpg');
    });
  });

  describe('D-07: Owner Auth Rejection of Legacy HMAC Tokens', () => {
    it('rejects deterministic legacy token that is not present in owner_sessions', async () => {
      const legacyToken = 'legacy_fake_hmac_token_value_not_in_db';
      const req = new Request('http://localhost/api/dashboard/stats', {
        headers: { Cookie: `tanweer_owner_token=${legacyToken}` },
      });

      const auth = await verifyOwnerAuth(req, env);
      expect(auth.isOwner).toBe(false);
    });

    it('accepts valid active session token present in owner_sessions', async () => {
      const validToken = 'valid_session_token_xyz_999';
      const pepper = env.PASSWORD_PEPPER;
      const tokenHash = await hashString(validToken, pepper);
      db.tables.owner_sessions.push({
        id: 's1',
        token_hash: tokenHash,
        expires_at: Date.now() + 86400000,
        revoked: 0,
      });

      const req = new Request('http://localhost/api/dashboard/stats', {
        headers: { Cookie: `tanweer_owner_token=${validToken}` },
      });

      const auth = await verifyOwnerAuth(req, env);
      expect(auth.isOwner).toBe(true);
    });
  });
});
