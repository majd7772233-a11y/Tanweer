import { describe, it, expect, beforeEach } from 'vitest';
import { handleRegister } from '../src/services/auth';
import {
  handleCreateRoleRequest,
  handleRedeemRoleCode,
} from '../src/services/roleRequests';
import { Env, UserContext } from '../src/env';
import { Role } from '../src/lib/roles';
import { hashString } from '../src/lib/crypto';

class RolesMockDb {
  public tables: Record<string, any[]> = {
    users: [],
    sessions: [],
    devices: [],
    groups: [],
    group_sections: [],
    group_members: [],
    academic_years: [{ id: 'ay_2025_2026', is_current: 1 }],
    role_requests: [],
    role_verification_codes: [],
    role_audit_log: [],
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

    if (/^INSERT/i.test(trimmed)) {
      const match = trimmed.match(/INSERT (?:OR IGNORE )?INTO (\w+)/i);
      if (match) {
        const table = match[1];
        if (!this.tables[table]) this.tables[table] = [];
        const colMatch = trimmed.match(/\((.*?)\)\s*VALUES/i);
        const colNames = colMatch ? colMatch[1].split(',').map((c) => c.replace(/["`\s]/g, '')) : null;
        const row: any = {};
        if (colMatch) {
          const colNames = colMatch[1].split(',').map((c) => c.replace(/["`\s]/g, ''));
          const valuesMatch = trimmed.match(/VALUES\s*\((.*?)\)/i);
          if (valuesMatch) {
            const rawVals = valuesMatch[1].split(',').map((s) => s.trim());
            let paramIdx = 0;
            colNames.forEach((c, idx) => {
              const valExpr = rawVals[idx];
              if (valExpr && valExpr.startsWith("'") && valExpr.endsWith("'")) {
                row[c] = valExpr.slice(1, -1);
              } else {
                row[c] = params[paramIdx++];
              }
            });
          } else {
            colNames.forEach((c, idx) => { row[c] = params[idx]; });
          }
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
        if (table === 'users' && trimmed.includes('WHERE id = ?')) {
          const userId = params[params.length - 1];
          const user = rows.find((u) => u.id === userId);
          if (user && trimmed.includes('role = ?')) user.role = params[0];
        }
        if (table === 'role_verification_codes' && trimmed.includes('WHERE id = ?')) {
          const codeId = params[params.length - 1];
          const c = rows.find((r) => r.id === codeId);
          if (c) {
            if (trimmed.includes('used_at = ?')) c.used_at = params[0];
            if (trimmed.includes('attempts = ?')) c.attempts = params[0];
          }
        }
        if (table === 'role_requests' && trimmed.includes('WHERE id = ?')) {
          const reqId = params[params.length - 1];
          const req = rows.find((r) => r.id === reqId);
          if (req && trimmed.includes("status = 'APPROVED'")) req.status = 'APPROVED';
        }
        return rows;
      }
    }

    if (/^SELECT/i.test(trimmed)) {
      const fromMatch = trimmed.match(/FROM (\w+)/i);
      if (!fromMatch) return [];
      const table = fromMatch[1];
      let rows = [...(this.tables[table] || [])];

      if (table === 'academic_years') {
        return rows;
      }

      if (table === 'role_verification_codes') {
        if (trimmed.includes('WHERE user_id = ? AND used_at IS NULL')) {
          const userId = params[0];
          return rows.filter((r) => r.user_id === userId && (r.used_at === null || r.used_at === undefined));
        }
      }

      if (trimmed.includes('phone_number = ?')) {
        return rows.filter((r) => r.phone_number === params[0]);
      }

      if (table === 'role_requests' && trimmed.includes('WHERE user_id = ?')) {
        return rows.filter((r) => r.user_id === params[0]);
      }

      if (trimmed.includes('WHERE id = ?')) {
        return rows.filter((r) => r.id === params[0]);
      }

      return rows;
    }

    return [];
  }
}

describe('Roles, Upgrades & Verification Token Security', () => {
  let mockDb: RolesMockDb;
  let env: Env;

  beforeEach(() => {
    mockDb = new RolesMockDb();
    env = {
      DB: mockDb as any,
      JWT_SECRET: 'test-jwt-secret-key-12345678901234567890',
      PASSWORD_PEPPER: 'test-pepper-123',
    } as any;
  });

  it('CRITICAL: Registration ALWAYS assigns STUDENT regardless of attacker role tampering in payload', async () => {
    const maliciousPayload = {
      phoneNumber: '777112233',
      password: 'StrongPassword123!',
      fullName: 'محمد علي سالم',
      gradeId: 10,
      sectionId: 'A',
      role: 'ADMIN', // Attacker attempts to become ADMIN directly on sign up!
    };

    const req = new Request('http://localhost/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(maliciousPayload),
    });

    const res = await handleRegister(req, env);
    expect(res.status).toBe(200);

    const body = await res.json() as any;
    expect(body.user.role).toBe('STUDENT'); // Must be STUDENT!

    // Verify stored user in database is strictly STUDENT
    const dbUser = mockDb.tables.users[0];
    expect(dbUser).toBeDefined();
    expect(dbUser.role).toBe('STUDENT');
  });

  it('allows student to submit role request with PENDING status (no direct promotion)', async () => {
    const user: UserContext = {
      userId: 'usr_student_1',
      phoneNumber: '777112233',
      fullName: 'محمد علي سالم',
      gradeId: 10,
      sectionId: 'A',
      role: Role.STUDENT,
    };

    const req = new Request('http://localhost/api/v1/role-requests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        requestedRole: 'TEACHER',
        reason: 'أنا معلم الرياضيات الجديد في المدرسة',
      }),
    });

    const res = await handleCreateRoleRequest(user, req, env);
    expect(res.status).toBe(200);

    const data = await res.json() as any;
    expect(data.success).toBe(true);
    expect(data.requestId).toBeDefined();

    // Verify request in database
    const savedReq = mockDb.tables.role_requests.find((r) => r.id === data.requestId);
    expect(savedReq).toBeDefined();
    expect(savedReq.status).toBe('PENDING');
    expect(savedReq.requested_role).toBe('TEACHER');
  });

  it('SECURITY: Expired verification code (>24h) is rejected', async () => {
    const studentId = 'usr_student_expired';
    mockDb.tables.users.push({
      id: studentId,
      phone_number: '777000001',
      role: 'STUDENT',
      full_name: 'طالب منتهي الرمز',
    });

    const cleanCode = '73918426';
    const codeHash = await hashString(cleanCode, env.PASSWORD_PEPPER);

    // Expired 25 hours ago
    const expiredTimestamp = Date.now() - (25 * 60 * 60 * 1000);

    mockDb.tables.role_verification_codes.push({
      id: 'code_expired_1',
      request_id: 'req_expired',
      user_id: studentId,
      role: 'TEACHER',
      code_hash: codeHash,
      attempts: 0,
      max_attempts: 5,
      expires_at: expiredTimestamp,
      used_at: null,
    });

    const userCtx: UserContext = {
      userId: studentId,
      phoneNumber: '777000001',
      fullName: 'طالب منتهي الرمز',
      gradeId: 10,
      sectionId: 'A',
      role: Role.STUDENT,
    };

    const req = new Request('http://localhost/api/v1/role-requests/redeem', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '7391-8426' }),
    });

    const res = await handleRedeemRoleCode(userCtx, req, env);
    expect(res.status).toBe(410);

    const body = await res.json() as any;
    expect(body.error?.code || body.error).toBe('CODE_EXPIRED');

    // Role remains STUDENT
    const dbUser = mockDb.tables.users.find((u) => u.id === studentId);
    expect(dbUser.role).toBe('STUDENT');
  });

  it('SECURITY: A used token cannot be redeemed twice', async () => {
    const studentId = 'usr_student_already_used';
    mockDb.tables.users.push({
      id: studentId,
      phone_number: '777000002',
      role: 'TEACHER', // already upgraded
      full_name: 'معلم مسبقاً',
    });

    const cleanCode = '88223344';
    const codeHash = await hashString(cleanCode, env.PASSWORD_PEPPER);

    mockDb.tables.role_verification_codes.push({
      id: 'code_used_1',
      request_id: 'req_used',
      user_id: studentId,
      role: 'TEACHER',
      code_hash: codeHash,
      attempts: 1,
      max_attempts: 5,
      expires_at: Date.now() + 86400000,
      used_at: Date.now() - 10000, // already used!
    });

    const userCtx: UserContext = {
      userId: studentId,
      phoneNumber: '777000002',
      fullName: 'معلم مسبقاً',
      gradeId: 10,
      sectionId: 'A',
      role: Role.STUDENT,
    };

    const req = new Request('http://localhost/api/v1/role-requests/redeem', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '8822-3344' }),
    });

    const res = await handleRedeemRoleCode(userCtx, req, env);
    expect(res.status).toBe(404);

    const body = await res.json() as any;
    expect(body.error?.code || body.error).toBe('NO_ACTIVE_CODE');
  });

  it('SECURITY: User cannot use another user verification token (User mismatch fails)', async () => {
    const victimUser = 'usr_legitimate_teacher';
    const attackerUser = 'usr_malicious_student';

    mockDb.tables.users.push({
      id: victimUser,
      phone_number: '777000010',
      role: 'STUDENT',
      full_name: 'أستاذ حقيقي',
    });

    mockDb.tables.users.push({
      id: attackerUser,
      phone_number: '777000099',
      role: 'STUDENT',
      full_name: 'طالب مخترق',
    });

    const cleanCode = '55667788';
    const codeHash = await hashString(cleanCode, env.PASSWORD_PEPPER);

    // Code is generated specifically for victimUser
    mockDb.tables.role_verification_codes.push({
      id: 'code_victim_1',
      request_id: 'req_victim',
      user_id: victimUser, // Belongs to victim
      role: 'TEACHER',
      code_hash: codeHash,
      attempts: 0,
      max_attempts: 5,
      expires_at: Date.now() + 86400000,
      used_at: null,
    });

    // Attacker tries to redeem victim's code
    const attackerCtx: UserContext = {
      userId: attackerUser,
      phoneNumber: '777000099',
      fullName: 'طالب مخترق',
      gradeId: 10,
      sectionId: 'A',
      role: Role.STUDENT,
    };

    const req = new Request('http://localhost/api/v1/role-requests/redeem', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '5566-7788' }),
    });

    const res = await handleRedeemRoleCode(attackerCtx, req, env);
    expect(res.status).toBe(404);

    const body = await res.json() as any;
    expect(body.error?.code || body.error).toBe('NO_ACTIVE_CODE');

    // Attacker role remains strictly STUDENT
    const dbAttacker = mockDb.tables.users.find((u) => u.id === attackerUser);
    expect(dbAttacker.role).toBe('STUDENT');
  });

  it('SUCCESS: Correct user with valid unexpired token successfully upgrades role and marks token USED', async () => {
    const studentId = 'usr_happy_path_teacher';
    mockDb.tables.users.push({
      id: studentId,
      phone_number: '777888999',
      role: 'STUDENT',
      full_name: 'الأستاذ سعيد',
    });

    const cleanCode = '12345678';
    const codeHash = await hashString(cleanCode, env.PASSWORD_PEPPER);

    mockDb.tables.role_requests.push({
      id: 'req_success',
      user_id: studentId,
      requested_role: 'TEACHER',
      status: 'APPROVED',
    });

    mockDb.tables.role_verification_codes.push({
      id: 'code_success_1',
      request_id: 'req_success',
      user_id: studentId,
      role: 'TEACHER',
      code_hash: codeHash,
      attempts: 0,
      max_attempts: 5,
      expires_at: Date.now() + 86400000,
      used_at: null,
    });

    const userCtx: UserContext = {
      userId: studentId,
      phoneNumber: '777888999',
      fullName: 'الأستاذ سعيد',
      gradeId: 10,
      sectionId: 'A',
      role: Role.STUDENT,
    };

    const req = new Request('http://localhost/api/v1/role-requests/redeem', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: '1234-5678' }),
    });

    const res = await handleRedeemRoleCode(userCtx, req, env);
    expect(res.status).toBe(200);

    const body = await res.json() as any;
    expect(body.success).toBe(true);
    expect(body.newRole).toBe('TEACHER');

    // User in DB upgraded to TEACHER
    const dbUser = mockDb.tables.users.find((u) => u.id === studentId);
    expect(dbUser.role).toBe('TEACHER');

    // Token marked as USED
    const dbCode = mockDb.tables.role_verification_codes.find((c) => c.id === 'code_success_1');
    expect(dbCode.used_at).toBeDefined();
  });
});
