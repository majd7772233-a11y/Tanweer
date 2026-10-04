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

    it('ACCEPTS valid HMAC session cookie', async () => {
      const expectedToken = await hashString(
        `owner_authenticated_super_secret_owner_key_9999`,
        env.PASSWORD_PEPPER
      );
      const req = new Request('http://localhost/dashboard', {
        headers: { Cookie: `tanweer_owner_token=${expectedToken}` },
      });
      const isAuthed = await verifyOwnerAuth(req, env);
      expect(isAuthed.isOwner).toBe(true);
    });

    it('SECURITY: Rejects access and disables dashboard if TANWEER_OWNER_SECRET is not configured', async () => {
      const unconfiguredEnv: any = { DB: mockDb, PASSWORD_PEPPER: 'test_pepper' };
      const req = new Request('http://localhost/dashboard', {
        headers: { 'X-Owner-Secret': 'super_secret_owner_key_9999' },
      });
      const isAuthed = await verifyOwnerAuth(req, unconfiguredEnv);
      expect(isAuthed.isOwner).toBe(false);
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
