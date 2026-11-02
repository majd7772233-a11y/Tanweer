import { describe, it, expect, beforeEach } from 'vitest';
import {
  verifyOwnerAuth,
  handlePreviewPurge,
  handleGetDashboardHomeworkDetail,
  handleTogglePinDashboardContent,
  handleGetDashboardExams,
  handleGetDashboardChatMessages,
  handleGetDashboardSchedules,
  handleGetDashboardContentDetail,
  handleGetDashboardDecisions,
  handleDashboardCreateHoliday,
  handleGetDashboardUsers,
  renderDashboardHtml,
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
    content_media: [],
    community_decisions: [],
    events: [],
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

    if (/^SELECT COUNT\\(\\*\\)/i.test(trimmed)) {
      if (/FROM users/i.test(trimmed)) {
        let rows = this.tables.users;
        if (/role != 'SYSTEM_OWNER'/i.test(trimmed)) rows = rows.filter(row => row.role !== 'SYSTEM_OWNER');
        if (/role = 'SYSTEM_OWNER'/i.test(trimmed)) rows = rows.filter(row => row.role === 'SYSTEM_OWNER');
        if (/status = 'DISABLED'/i.test(trimmed)) rows = rows.filter(row => row.status === 'DISABLED');
        return [{ total: rows.length, count: rows.length }];
      }
      if (/FROM contents/i.test(trimmed)) return [{ total: this.tables.contents.length, count: this.tables.contents.length }];
      if (/FROM homeworks/i.test(trimmed)) return [{ total: this.tables.homeworks.length, count: this.tables.homeworks.length }];
      if (/FROM exams/i.test(trimmed)) return [{ total: this.tables.exams.length, count: this.tables.exams.length }];
      if (/FROM chat_messages/i.test(trimmed)) return [{ total: this.tables.chat_messages.length, count: this.tables.chat_messages.length }];
      return [{ total: 0, count: 0 }];
    }

    if (/^SELECT id, phone_number, full_name, grade_id, section_id, role/i.test(trimmed) && /FROM users/i.test(trimmed)) {
      return this.tables.users.filter(row => row.role !== 'SYSTEM_OWNER');
    }

    if (trimmed.includes('FROM contents c') && trimmed.includes('WHERE c.id = ?')) {
      const row = this.tables.contents.find(item => item.id === params[0]);
      if (!row) return [];
      const author = this.tables.users.find(item => item.id === row.created_by);
      const group = this.tables.groups.find(item => item.id === row.group_id);
      const subject = this.tables.subjects.find(item => item.id === row.subject_id);
      return [{
        ...row,
        author_name: author?.full_name || null,
        author_phone: author?.phone_number || null,
        group_name: group?.name || null,
        subject_name: subject?.name_ar || subject?.id || null,
      }];
    }

    if (/FROM content_media/i.test(trimmed)) {
      return this.tables.content_media.filter(item => item.content_id === params[0]);
    }

    if (/FROM community_decisions cd/i.test(trimmed)) {
      if (/cd\\.created_by/i.test(trimmed)) throw new Error('community_decisions.created_by does not exist');
      return this.tables.community_decisions.map(item => {
        const author = this.tables.users.find(user => user.id === item.requested_by);
        const group = this.tables.groups.find(entry => entry.id === item.group_id);
        return { ...item, creator_name: author?.full_name || item.requester_name || null, group_name: group?.name || null };
      });
    }

    if (/^INSERT INTO events/i.test(trimmed)) {
      const actor = this.tables.users.find(user => user.id === params[5]);
      const group = this.tables.groups.find(item => item.id === params[1]);
      if (!actor || !group) throw new Error('FOREIGN KEY constraint failed');
      this.tables.events.push({
        id: params[0], group_id: params[1], event_date: params[2], title: params[3],
        description: params[4], category: 'HOLIDAY', created_by: params[5],
        created_at: params[6], is_official: 1,
      });
      return [];
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
    db.tables.users.push({
      id: 'SYSTEM_OWNER',
      phone_number: '__TANWEER_INTERNAL_SYSTEM_ACTOR__',
      password_hash: '!SYSTEM_ACCOUNT_LOGIN_DISABLED!',
      full_name: 'مستخدم النظام الداخلي',
      role: 'SYSTEM_OWNER',
    });
    db.tables.groups.push({ id: 'all', name: 'كامل المدرسة' });
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
  describe('D-08: Dashboard detail SQL and decisions', () => {
    it('loads lesson details using the actual subjects.name_ar column', async () => {
      db.tables.users.push({ id: 'teacher1', full_name: 'أحمد', phone_number: '771234567' });
      db.tables.groups.push({ id: 'g1', name: 'الشعبة أ' });
      db.tables.subjects.push({ id: 'physics', name_ar: 'الفيزياء' });
      db.tables.contents.push({
        id: 'c1', group_id: 'g1', study_date: '2026-11-01', subject_id: 'physics',
        created_by: 'teacher1', title: 'الحركة', description: 'شرح الدرس',
        type: 'LESSON', views_count: 2, useful_count: 1, is_pinned: 0, created_at: 1,
      });
      db.tables.content_media.push({
        id: 'media1', content_id: 'c1', page_order: 1, object_key: '',
        url: 'https://tanweer.magd.workers.dev/api/v1/media/blob1', mime_type: 'image/jpeg', file_size: 100,
        checksum: null, is_primary: 1, created_at: 1,
      });

      const response = await handleGetDashboardContentDetail('c1', env);
      expect(response.status).toBe(200);
      const data = await response.json() as any;
      expect(data.success).toBe(true);
      expect(data.content.subject_name).toBe('الفيزياء');
      expect(data.media).toHaveLength(1);
      expect(data.media[0].objectKey).toBe('blob1');
      expect(data.media[0].previewUrl).toBe('/api/dashboard/media/blob1');
    });

    it('resolves decision creators through requested_by and requester_name', async () => {
      db.tables.users.push({ id: 'teacher1', full_name: 'أحمد', phone_number: '771234567' });
      db.tables.groups.push({ id: 'g1', name: 'الشعبة أ' });
      db.tables.community_decisions.push({
        id: 'decision1', group_id: 'g1', requested_by: 'teacher1',
        requester_name: 'اسم مقدم الطلب', title: 'قرار اختبار', created_at: 1,
      });

      const response = await handleGetDashboardDecisions(env);
      expect(response.status).toBe(200);
      const data = await response.json() as any;
      expect(data.decisions[0].creator_name).toBe('أحمد');
      expect(data.decisions[0].group_name).toBe('الشعبة أ');
    });
  });

  describe('D-09: System owner principal and owner-created holidays', () => {
    it('creates an official holiday using the internal SYSTEM_OWNER principal', async () => {
      const request = new Request('http://localhost/api/dashboard/holidays', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'إجازة اختبار',
          eventDate: '2026-11-10',
          description: 'اختبار إنشاء الإجازة الرسمية',
          groupId: 'all',
          isSchoolWide: true,
        }),
      });

      const response = await handleDashboardCreateHoliday(request, 'SYSTEM_OWNER', env);
      expect(response.status).toBe(200);
      expect(db.tables.events).toHaveLength(1);
      expect(db.tables.events[0].created_by).toBe('SYSTEM_OWNER');
    });

    it('keeps the internal principal out of the owner-managed user list', async () => {
      db.tables.users.push({ id: 'student1', phone_number: '771234568', full_name: 'طالب', role: 'STUDENT' });
      const request = new Request('http://localhost/api/dashboard/users');
      const response = await handleGetDashboardUsers(request, env);
      expect(response.status).toBe(200);
      const data = await response.json() as any;
      expect(data.users.map((user: any) => user.id)).toEqual(['student1']);
      expect(data.users.some((user: any) => user.id === 'SYSTEM_OWNER')).toBe(false);
    });
  });

  describe('D-10: Dashboard HTML and JavaScript DOM contract', () => {
    it('keeps homework and purge JavaScript element references aligned with rendered HTML', async () => {
      const response = renderDashboardHtml();
      expect(response.status).toBe(200);
      const html = await response.text();
      const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1] || '';
      const ids = new Set(Array.from(html.matchAll(/\bid=["']([^"']+)["']/g)).map(match => match[1]));
      const references = Array.from(script.matchAll(/getElementById\(["']([^"']+)["']\)/g)).map(match => match[1]);
      const criticalReferences = references.filter(id => /^(hd|hwModal|purge|acad)/.test(id));
      const missing = criticalReferences.filter(id => !ids.has(id));

      expect(missing).toEqual([]);
      expect(script).toContain('function toggleMobileMenu()');
      expect(script).not.toContain("getElementById('hwModalTitle')");
      expect(script).not.toContain("getElementById('purgeModalSecretInput')");
    });
  });
  
  describe('D-11: Dashboard inline JavaScript syntax', () => {
    it('renders a JavaScript block that the browser can parse before wiring any controls', async () => {
      const response = renderDashboardHtml();
      expect(response.status).toBe(200);
      const html = await response.text();
      const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];

      expect(script).toBeDefined();
      expect(() => new Function(script!)).not.toThrow();
    });
  });

});
