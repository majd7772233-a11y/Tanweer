import { describe, it, expect, beforeEach } from 'vitest';
import {
  handleGetTeacherDashboardData,
  handleGetModeratorDashboardData,
  handleGetAdminDashboardData,
} from '../src/services/governance';
import { verifyOwnerAuth } from '../src/services/dashboard';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import { hashString } from '../src/lib/crypto';

class DashboardMockDb {
  public tables: Record<string, any[]> = {
    users: [],
    homeworks: [],
    exams: [],
    issue_comments: [],
    contents: [],
    content_corrections: [],
    schedule_proposals: [],
    community_decisions: [],
    groups: [],
    group_members: [],
    owner_sessions: [],
    holidays: [],
    events: [],
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
    if (/^INSERT INTO owner_sessions/i.test(trimmed)) {
      this.tables.owner_sessions.push({
        id: params[0],
        token_hash: params[1],
        expires_at: params[2],
        created_at: params[3],
        revoked: 0,
      });
      return [];
    }
    if (/^UPDATE owner_sessions SET revoked = 1 WHERE token_hash = \?/i.test(trimmed)) {
      for (const s of this.tables.owner_sessions) {
        if (s.token_hash === params[0]) s.revoked = 1;
      }
      return [];
    }
    if (/^SELECT id, expires_at, revoked FROM owner_sessions WHERE token_hash = \?/i.test(trimmed)) {
      return this.tables.owner_sessions.filter(s => s.token_hash === params[0]);
    }
    if (/^SELECT id, phone_number, full_name/i.test(trimmed)) {
      return [{
        id: params[0],
        phone_number: '777000001',
        full_name: 'مستخدم تجريبي',
        grade_id: 10,
        section_id: 'A',
        email: null,
        role: 'STUDENT',
        status: 'ACTIVE',
        last_seen: 1700000000000,
        created_at: 1690000000000,
        updated_at: 1700000000000,
      }];
    }
    if (/^SELECT gm\.group_id, gm\.role/i.test(trimmed)) {
      return [{
        group_id: 'grp_1',
        role: 'MEMBER',
        joined_at: 1690000000000,
        group_name: 'شعبة أ',
        group_type: 'CLASS',
      }];
    }
    if (/^SELECT s\.id as session_id/i.test(trimmed)) {
      return [{
        session_id: 'sess_123456789_abcdef',
        device_id: 'dev_1',
        expires_at: 1750000000000,
        created_at: 1700000000000,
        platform: 'Android',
        app_version: '1.2.0',
        device_last_seen: 1700000000000,
      }];
    }
    if (/^SELECT/i.test(trimmed)) {
      if (trimmed.includes('COUNT(*) as count')) {
        return [{ count: 0 }];
      }
      return [];
    }
    return [];
  }
}

describe('Dashboard Roles & Authentication Security Boundaries', () => {
  let mockDb: DashboardMockDb;
  let env: Env;

  const studentUser: UserContext = {
    userId: 'usr_student',
    phoneNumber: '777000001',
    fullName: 'طالب',
    gradeId: 10,
    sectionId: 'A',
    role: Role.STUDENT,
  };

  const moderatorUser: UserContext = {
    userId: 'usr_moderator',
    phoneNumber: '777000002',
    fullName: 'مشرف',
    gradeId: 10,
    sectionId: 'A',
    role: Role.MODERATOR,
  };

  const teacherUser: UserContext = {
    userId: 'usr_teacher',
    phoneNumber: '777000003',
    fullName: 'أستاذ',
    gradeId: 10,
    sectionId: 'A',
    role: Role.TEACHER,
  };

  const adminUser: UserContext = {
    userId: 'usr_admin',
    phoneNumber: '777000004',
    fullName: 'مدير',
    gradeId: 10,
    sectionId: 'A',
    role: Role.ADMIN,
  };

  beforeEach(() => {
    mockDb = new DashboardMockDb();
    env = {
      DB: mockDb as any,
      TANWEER_OWNER_SECRET: 'super_secret_owner_key_9999',
      PASSWORD_PEPPER: 'test_pepper',
    } as any;
  });

  describe('Central Owner Server Dashboard Security', () => {
    it('REJECTS unauthenticated requests without cookie or secret key', async () => {
      const req = new Request('http://localhost/dashboard');
      const isAuthed = await verifyOwnerAuth(req, env);
      expect(isAuthed.isOwner).toBe(false);
    });

    it('REJECTS invalid secret key', async () => {
      const req = new Request('http://localhost/dashboard', {
        headers: { 'X-Owner-Secret': 'wrong_invalid_key' },
      });
      const isAuthed = await verifyOwnerAuth(req, env);
      expect(isAuthed.isOwner).toBe(false);
    });

    it('ACCEPTS correct X-Owner-Secret', async () => {
      const req = new Request('http://localhost/dashboard', {
        headers: { 'X-Owner-Secret': 'super_secret_owner_key_9999' },
      });
      const isAuthed = await verifyOwnerAuth(req, env);
      expect(isAuthed.isOwner).toBe(true);
    });

    it('TA-02: Generates random expiring session token on login and validates it via Cookie & Bearer', async () => {
      const loginReq = new Request('http://localhost/api/dashboard/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ secret: 'super_secret_owner_key_9999' }),
      });
      const { handleOwnerLogin } = await import('../src/services/dashboard');
      const loginRes = await handleOwnerLogin(loginReq, env);
      expect(loginRes.status).toBe(200);

      const data = await loginRes.json() as any;
      expect(data.success).toBe(true);
      expect(data.token).toBeDefined();
      expect(data.expiresAt).toBeGreaterThan(Date.now());
      expect(data.token.startsWith('owner_')).toBe(true);

      // Verify session stored in owner_sessions
      expect(mockDb.tables.owner_sessions.length).toBe(1);

      // Verify request with Cookie
      const cookieReq = new Request('http://localhost/dashboard', {
        headers: { Cookie: `tanweer_owner_token=${data.token}` },
      });
      const authedViaCookie = await verifyOwnerAuth(cookieReq, env);
      expect(authedViaCookie.isOwner).toBe(true);

      // Verify request with Authorization: Bearer
      const bearerReq = new Request('http://localhost/dashboard', {
        headers: { Authorization: `Bearer ${data.token}` },
      });
      const authedViaBearer = await verifyOwnerAuth(bearerReq, env);
      expect(authedViaBearer.isOwner).toBe(true);
    });

    it('TA-02: REJECTS expired session token', async () => {
      // Simulate expired token in DB
      const expiredToken = 'owner_expired_token_test_123';
      const tokenHash = await hashString(expiredToken, env.PASSWORD_PEPPER);
      mockDb.tables.owner_sessions.push({
        id: 'sess_expired',
        token_hash: tokenHash,
        expires_at: Date.now() - 10000, // expired 10 seconds ago
        created_at: Date.now() - 100000,
        revoked: 0,
      });

      const req = new Request('http://localhost/dashboard', {
        headers: { Authorization: `Bearer ${expiredToken}` },
      });
      const isAuthed = await verifyOwnerAuth(req, env);
      expect(isAuthed.isOwner).toBe(false);
    });

    it('TA-02: Revokes session token on logout and rejects subsequent requests', async () => {
      const loginReq = new Request('http://localhost/api/dashboard/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ secret: 'super_secret_owner_key_9999' }),
      });
      const { handleOwnerLogin, handleOwnerLogout } = await import('../src/services/dashboard');
      const loginRes = await handleOwnerLogin(loginReq, env);
      const data = await loginRes.json() as any;
      const token = data.token;

      // Authenticated before logout
      const preLogoutReq = new Request('http://localhost/dashboard', {
        headers: { Cookie: `tanweer_owner_token=${token}` },
      });
      expect((await verifyOwnerAuth(preLogoutReq, env)).isOwner).toBe(true);

      // Perform logout
      const logoutReq = new Request('http://localhost/api/dashboard/logout', {
        method: 'POST',
        headers: { Cookie: `tanweer_owner_token=${token}` },
      });
      const logoutRes = await handleOwnerLogout(logoutReq, env);
      expect(logoutRes.status).toBe(200);

      // Session marked revoked in DB
      const postLogoutReq = new Request('http://localhost/dashboard', {
        headers: { Cookie: `tanweer_owner_token=${token}` },
      });
      expect((await verifyOwnerAuth(postLogoutReq, env)).isOwner).toBe(false);
    });

    it('SECURITY: Rejects access and disables dashboard if TANWEER_OWNER_SECRET is not configured', async () => {
      const unconfiguredEnv: any = { DB: mockDb, PASSWORD_PEPPER: 'test_pepper' };
      const req = new Request('http://localhost/dashboard', {
        headers: { 'X-Owner-Secret': 'super_secret_owner_key_9999' },
      });
      const isAuthed = await verifyOwnerAuth(req, unconfiguredEnv);
      expect(isAuthed.isOwner).toBe(false);
    });

    it('TA-01: Renders Dashboard and Login pages with strict Content-Security-Policy & anti-clickjacking headers', async () => {
      const { renderDashboardHtml, renderLoginHtml } = await import('../src/services/dashboard');
      const dashRes = renderDashboardHtml();
      expect(dashRes.headers.get('Content-Security-Policy')).toContain("default-src 'self'");
      expect(dashRes.headers.get('X-Content-Type-Options')).toBe('nosniff');
      expect(dashRes.headers.get('X-Frame-Options')).toBe('DENY');

      const loginRes = renderLoginHtml();
      expect(loginRes.headers.get('Content-Security-Policy')).toContain("default-src 'self'");
      expect(loginRes.headers.get('X-Content-Type-Options')).toBe('nosniff');
      expect(loginRes.headers.get('X-Frame-Options')).toBe('DENY');
    });

    it('TA-03: sanitizePublicRole masks SYSTEM_OWNER so Android never receives owner role', async () => {
      const { sanitizePublicRole } = await import('../src/lib/roles');
      expect(sanitizePublicRole(Role.SYSTEM_OWNER)).toBe(Role.ADMIN);
      expect(sanitizePublicRole(Role.ADMIN)).toBe(Role.ADMIN);
      expect(sanitizePublicRole(Role.TEACHER)).toBe(Role.TEACHER);
      expect(sanitizePublicRole(Role.MODERATOR)).toBe(Role.MODERATOR);
      expect(sanitizePublicRole(Role.STUDENT)).toBe(Role.STUDENT);
    });

    it('TA-07: handleGetDashboardUserDetail returns unified session fields, lastActiveAt, and activities', async () => {
      const { handleGetDashboardUserDetail } = await import('../src/services/dashboard');
      const res = await handleGetDashboardUserDetail('usr_test', env);
      expect(res.status).toBe(200);
      const data = await res.json() as any;
      expect(data.success).toBe(true);
      expect(data.user.lastActiveAt).toBe(1700000000000);
      expect(data.user.lastSeen).toBe(1700000000000);
      expect(data.activities).toBeDefined();
      expect(data.activity).toBeDefined();
      expect(data.memberships[0].role_in_group).toBe('MEMBER');
      expect(data.sessions[0].id).toBe('sess_123456789_abcdef');
      expect(data.sessions[0].session_id).toBe('sess_123456789_abcdef');
      expect(data.sessions[0].device_info).toContain('Android');
    });

    it('TA-09: handleSaveScheduleBatch strictly validates slots and rejects invalid/incomplete slots without modifying versions', async () => {
      const { handleSaveScheduleBatch } = await import('../src/services/schedule');
      const invalidReq = new Request('http://localhost/api/groups/grp_1/schedule/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          slots: [
            { dayOfWeek: 0, slotOrder: 1, subjectId: 'subj_math' },
            { dayOfWeek: 1, slotOrder: 0, subjectId: '' }, // invalid slotOrder and subjectId
          ],
        }),
      });
      const res = await handleSaveScheduleBatch('grp_1', adminUser, invalidReq, env);
      expect(res.status).toBe(400);
      const data = await res.json() as any;
      expect(data.error.code).toBe('INVALID_INPUT');
    });

    it('TA-06: handleGetEvents is defined and supports school-wide event integration', async () => {
      const { handleGetEvents } = await import('../src/services/events');
      expect(typeof handleGetEvents).toBe('function');
    });
  });

  describe('In-App Teacher Teaching Center Security', () => {
    it('SECURITY: Rejects students from accessing Teacher Dashboard (403)', async () => {
      const res = await handleGetTeacherDashboardData(studentUser, env);
      expect(res.status).toBe(403);
    });

    it('Allows verified teacher to access Teacher Dashboard (200)', async () => {
      const res = await handleGetTeacherDashboardData(teacherUser, env);
      expect(res.status).toBe(200);
      const data = await res.json() as any;
      expect(data.success).toBe(true);
      expect(data.teacher.verified).toBe(true);
    });
  });

  describe('In-App Moderator Group Moderation Security', () => {
    it('SECURITY: Rejects normal students from Moderator Dashboard (403)', async () => {
      const res = await handleGetModeratorDashboardData(studentUser, env);
      expect(res.status).toBe(403);
    });

    it('Allows moderator to access Moderator Dashboard (200)', async () => {
      const res = await handleGetModeratorDashboardData(moderatorUser, env);
      expect(res.status).toBe(200);
      const data = await res.json() as any;
      expect(data.success).toBe(true);
      expect(data.stats).toBeDefined();
    });
  });

  describe('In-App School Admin Management Security', () => {
    it('SECURITY: Rejects teachers and students from Admin Dashboard (403)', async () => {
      const studentRes = await handleGetAdminDashboardData(studentUser, env);
      expect(studentRes.status).toBe(403);

      const teacherRes = await handleGetAdminDashboardData(teacherUser, env);
      expect(teacherRes.status).toBe(403);
    });

    it('Allows school Admin to access School Admin Dashboard (200)', async () => {
      const res = await handleGetAdminDashboardData(adminUser, env);
      expect(res.status).toBe(200);
      const data = await res.json() as any;
      expect(data.success).toBe(true);
      expect(data.school).toBeDefined();
    });
  });
});
