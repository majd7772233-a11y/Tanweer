import { describe, it, expect } from 'vitest';
import { Permission, hasPermission } from '../src/lib/permissions';
import { Role, normalizeRole } from '../src/lib/roles';
import { UserContext } from '../src/env';

describe('Permissions Matrix & Security Enforcements', () => {
  const studentUser: UserContext = {
    userId: 'usr_student_1',
    phoneNumber: '777000001',
    fullName: 'طالب مجتهد',
    gradeId: 10,
    sectionId: 'A',
    role: Role.STUDENT,
  };

  const moderatorUser: UserContext = {
    userId: 'usr_mod_1',
    phoneNumber: '777000002',
    fullName: 'مشرف الشعبة',
    gradeId: 10,
    sectionId: 'A',
    role: Role.MODERATOR,
  };

  const teacherUser: UserContext = {
    userId: 'usr_teacher_1',
    phoneNumber: '777000003',
    fullName: 'الأستاذ أحمد',
    gradeId: 10,
    sectionId: 'A',
    role: Role.TEACHER,
  };

  const adminUser: UserContext = {
    userId: 'usr_admin_1',
    phoneNumber: '777000004',
    fullName: 'مدير المدرسة',
    gradeId: 10,
    sectionId: 'A',
    role: Role.ADMIN,
  };

  const ownerUser: UserContext = {
    userId: 'usr_owner_1',
    phoneNumber: '777000005',
    fullName: 'مالك المنظومة',
    gradeId: 0,
    sectionId: '',
    role: Role.SYSTEM_OWNER,
  };

  it('verifies that normal student has universal contribution permissions only', async () => {
    // Universal Student Allowed Actions
    expect(await hasPermission(studentUser, Permission.CONTRIBUTE_CONTENT)).toBe(true);
    expect(await hasPermission(studentUser, Permission.CREATE_HOMEWORK)).toBe(true);
    expect(await hasPermission(studentUser, Permission.CREATE_EXAM)).toBe(true);
    expect(await hasPermission(studentUser, Permission.ASK_QUESTION)).toBe(true);
    expect(await hasPermission(studentUser, Permission.PROPOSE_SCHEDULE)).toBe(true);
    expect(await hasPermission(studentUser, Permission.REQUEST_ROLE_UPGRADE)).toBe(true);

    // Forbidden Actions for normal student
    expect(await hasPermission(studentUser, Permission.MANAGE_SCHEDULE)).toBe(false);
    expect(await hasPermission(studentUser, Permission.MODERATE_CONTENT)).toBe(false);
    expect(await hasPermission(studentUser, Permission.MODERATE_CHAT)).toBe(false);
    expect(await hasPermission(studentUser, Permission.MANAGE_GROUP_MEMBERS)).toBe(false);
    expect(await hasPermission(studentUser, Permission.VIEW_AUDIT_LOG)).toBe(false);
    expect(await hasPermission(studentUser, Permission.MANAGE_USERS)).toBe(false);
    expect(await hasPermission(studentUser, Permission.ACCESS_OWNER_DASHBOARD)).toBe(false);
  });

  it('verifies that moderator can moderate group, review content, and manage schedule', async () => {
    expect(await hasPermission(moderatorUser, Permission.MODERATE_CONTENT)).toBe(true);
    expect(await hasPermission(moderatorUser, Permission.MANAGE_SCHEDULE)).toBe(true);
    expect(await hasPermission(moderatorUser, Permission.MODERATE_CHAT)).toBe(true);
    expect(await hasPermission(moderatorUser, Permission.MANAGE_GROUP_MEMBERS)).toBe(true);

    // Moderator cannot access owner dashboard or manage global users
    expect(await hasPermission(moderatorUser, Permission.ACCESS_OWNER_DASHBOARD)).toBe(false);
    expect(await hasPermission(moderatorUser, Permission.MANAGE_USERS)).toBe(false);
    expect(await hasPermission(moderatorUser, Permission.VIEW_AUDIT_LOG)).toBe(false);
  });

  it('verifies that verified teacher has pedagogical permissions & official content verification', async () => {
    expect(await hasPermission(teacherUser, Permission.CONTRIBUTE_CONTENT)).toBe(true);
    expect(await hasPermission(teacherUser, Permission.MODERATE_CONTENT)).toBe(true);
    expect(await hasPermission(teacherUser, Permission.MANAGE_SCHEDULE)).toBe(true);
    expect(await hasPermission(teacherUser, Permission.VERIFY_BEST_ANSWER)).toBe(true);
    expect(await hasPermission(teacherUser, Permission.PIN_CONTENT)).toBe(true);

    // Teacher cannot access owner dashboard or manage global users
    expect(await hasPermission(teacherUser, Permission.ACCESS_OWNER_DASHBOARD)).toBe(false);
    expect(await hasPermission(teacherUser, Permission.MANAGE_USERS)).toBe(false);
  });

  it('verifies that admin has full school management rights', async () => {
    expect(await hasPermission(adminUser, Permission.MODERATE_CONTENT)).toBe(true);
    expect(await hasPermission(adminUser, Permission.MANAGE_SCHEDULE)).toBe(true);
    expect(await hasPermission(adminUser, Permission.MANAGE_USERS)).toBe(true);
    expect(await hasPermission(adminUser, Permission.MANAGE_GROUP_MEMBERS)).toBe(true);

    // Admin cannot access owner server dashboard
    expect(await hasPermission(adminUser, Permission.ACCESS_OWNER_DASHBOARD)).toBe(false);
  });

  it('verifies that system owner has unrestricted master access including server dashboard', async () => {
    expect(await hasPermission(ownerUser, Permission.MODERATE_CONTENT)).toBe(true);
    expect(await hasPermission(ownerUser, Permission.MANAGE_SCHEDULE)).toBe(true);
    expect(await hasPermission(ownerUser, Permission.MANAGE_USERS)).toBe(true);
    expect(await hasPermission(ownerUser, Permission.VIEW_AUDIT_LOG)).toBe(true);
    expect(await hasPermission(ownerUser, Permission.ACCESS_OWNER_DASHBOARD)).toBe(true);
  });

  it('ensures role normalization handles legacy and case-insensitive strings safely', () => {
    expect(normalizeRole('verified_teacher')).toBe(Role.TEACHER);
    expect(normalizeRole('TEACHER')).toBe(Role.TEACHER);
    expect(normalizeRole('ADMINISTRATOR')).toBe(Role.ADMIN);
    expect(normalizeRole('ADMIN')).toBe(Role.ADMIN);
    expect(normalizeRole('owner')).toBe(Role.SYSTEM_OWNER);
    expect(normalizeRole('SYSTEM_OWNER')).toBe(Role.SYSTEM_OWNER);
    expect(normalizeRole('moderator')).toBe(Role.MODERATOR);
    expect(normalizeRole('unknown_attacker_role')).toBe(Role.STUDENT);
    expect(normalizeRole(null as any)).toBe(Role.STUDENT);
    expect(normalizeRole(undefined as any)).toBe(Role.STUDENT);
  });

  describe('Schedule Permissions & Governance', () => {
    class ScheduleTestMockDb {
      public slotsCount = 0;
      public members: string[] = ['usr_student_1', 'usr_mod_1', 'usr_teacher_1', 'usr_admin_1'];

      prepare(sql: string) {
        const db = this;
        let boundParams: any[] = [];
        return {
          bind(...params: any[]) {
            boundParams = params;
            return this;
          },
          async first<T = any>(): Promise<T | null> {
            const trimmed = sql.trim().replace(/\s+/g, ' ');
            if (trimmed.includes('COUNT(*) as count FROM schedule_slots')) {
              return { count: db.slotsCount } as any;
            }
            if (trimmed.includes('FROM group_members WHERE group_id = ? AND user_id = ?')) {
              const isMember = db.members.includes(boundParams[1]);
              return isMember ? ({ id: 'gm_1' } as any) : null;
            }
            return null;
          },
          async all<T = any>(): Promise<{ results: T[] }> {
            return { results: [] };
          },
          async run() {
            return { success: true };
          },
        };
      }
    }

    it('Student CAN create initial schedule when no official slots exist', async () => {
      const { requireScheduleManager } = await import('../src/middleware/permissions');
      const db = new ScheduleTestMockDb();
      db.slotsCount = 0; // Fresh unpopulated group

      const res = await requireScheduleManager(studentUser, 'group_10_A', db as any);
      expect(res).toBeNull(); // Allowed!
    });

    it('SECURITY: Student CANNOT edit official schedule after initial creation (403 Forbidden)', async () => {
      const { requireScheduleManager } = await import('../src/middleware/permissions');
      const db = new ScheduleTestMockDb();
      db.slotsCount = 5; // Established schedule with 5 slots

      const res = await requireScheduleManager(studentUser, 'group_10_A', db as any);
      expect(res).not.toBeNull();
      expect(res!.status).toBe(403);

      const body = await res!.json() as any;
      expect(body.error?.code || body.error).toBe('FORBIDDEN_SCHEDULE_MANAGEMENT');
    });

    it('Teacher, Moderator, and Admin CAN directly manage established schedule', async () => {
      const { requireScheduleManager } = await import('../src/middleware/permissions');
      const db = new ScheduleTestMockDb();
      db.slotsCount = 5;

      const modRes = await requireScheduleManager(moderatorUser, 'group_10_A', db as any);
      expect(modRes).toBeNull();

      const teacherRes = await requireScheduleManager(teacherUser, 'group_10_A', db as any);
      expect(teacherRes).toBeNull();

      const adminRes = await requireScheduleManager(adminUser, 'group_10_A', db as any);
      expect(adminRes).toBeNull();
    });

    it('Student can propose schedule changes through community voting', async () => {
      expect(await hasPermission(studentUser, Permission.PROPOSE_SCHEDULE)).toBe(true);
    });
  });

  describe('Anti-Forgery Security: Android & WebSocket Role Protection', () => {
    it('SECURITY: Android client cannot forge role in request payload or headers', async () => {
      const { authenticateRequest } = await import('../src/middleware/auth');
      const { hashString } = await import('../src/lib/crypto');

      const pepper = 'test_pepper_sec';
      const validToken = 'valid_session_token_12345';
      const tokenHash = await hashString(validToken, pepper);

      const mockDb = {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => ({
              id: 'sess_1',
              user_id: 'usr_student_1',
              device_id: 'dev_android_1',
              expires_at: Date.now() + 86400000,
              phone_number: '777000001',
              full_name: 'طالب حقيقي',
              grade_id: 10,
              section_id: 'A',
              role: Role.STUDENT, // Role stored in DB is strictly STUDENT
              status: 'ACTIVE',
            }),
          }),
        }),
      };

      const env: any = { DB: mockDb, SESSION_PEPPER: pepper };

      // Attacker sends forged headers claiming to be ADMIN or TEACHER
      const req = new Request('http://localhost/api/v1/content', {
        headers: {
          'Authorization': `Bearer ${validToken}`,
          'X-User-Role': 'ADMIN',
          'X-Role': 'SYSTEM_OWNER',
        },
      });

      const { user, error } = await authenticateRequest(req, env);
      expect(error).toBeUndefined();
      expect(user).toBeDefined();
      // Server strictly derived role from DB session table, ignoring forged headers completely
      expect(user!.role).toBe(Role.STUDENT);
      expect(user!.role).not.toBe(Role.ADMIN);
      expect(user!.role).not.toBe(Role.SYSTEM_OWNER);
    });

    it('SECURITY: WebSocket connection resolves role strictly from database session', async () => {
      const { getUserFromToken } = await import('../src/middleware/auth');
      const { hashString } = await import('../src/lib/crypto');

      const pepper = 'test_pepper_ws';
      const wsToken = 'ws_token_67890';
      const tokenHash = await hashString(wsToken, pepper);

      const mockDb = {
        prepare: (sql: string) => ({
          bind: () => ({
            first: async () => ({
              id: 'sess_ws_1',
              user_id: 'usr_student_ws',
              device_id: 'dev_ws',
              expires_at: Date.now() + 86400000,
              phone_number: '777000002',
              full_name: 'طالب ويب سوكت',
              grade_id: 10,
              section_id: 'A',
              role: Role.STUDENT,
              status: 'ACTIVE',
            }),
          }),
        }),
      };

      const env: any = { DB: mockDb, SESSION_PEPPER: pepper };
      const wsUser = await getUserFromToken(wsToken, env);

      expect(wsUser).toBeDefined();
      expect(wsUser!.role).toBe(Role.STUDENT);
    });
  });
});
