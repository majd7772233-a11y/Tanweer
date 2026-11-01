import { describe, expect, it } from 'vitest';
import { Env } from '../src/env';
import { hashPassword, hashString, verifyPassword } from '../src/lib/crypto';
import { handleLogin, handleRegister } from '../src/services/auth';

const pepper = 'auth-hardening-test-pepper';

describe('Authentication hardening regressions', () => {
  it('fails closed when the temporary-credential store cannot be checked', async () => {
    const password = 'Tmp#ABCD2345';
    const passwordHash = (await hashPassword(password, pepper)).hash;
    let sessionInsertAttempted = false;

    const env = {
      SESSION_PEPPER: pepper,
      PASSWORD_PEPPER: pepper,
      DB: {
        prepare(sql: string) {
          return {
            bind(..._params: unknown[]) {
              return {
                async first() {
                  if (sql.includes('FROM users WHERE phone_number = ?')) {
                    return {
                      id: 'user-1',
                      phone_number: '771234567',
                      full_name: 'مجد أنور عبدالرحمن',
                      grade_id: 10,
                      section_id: 'A',
                      role: 'STUDENT',
                      password_hash: passwordHash,
                    };
                  }
                  if (sql.includes('FROM user_recovery_tokens')) {
                    throw new Error('temporary credential store unavailable');
                  }
                  return null;
                },
                async run() {
                  if (sql.includes('INSERT INTO sessions')) sessionInsertAttempted = true;
                  return { success: true, meta: { changes: 1 } };
                },
              };
            },
          };
        },
      },
    } as unknown as Env;

    const response = await handleLogin(new Request('https://tanweer.test/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phoneNumber: '771234567', password }),
    }), env);

    expect(response.status).toBe(503);
    expect(await response.json()).toMatchObject({
      success: false,
      error: { code: 'AUTHENTICATION_UNAVAILABLE' },
    });
    expect(sessionInsertAttempted).toBe(false);
  });

  it('rejects a temporary password when its conditional one-time consume loses a race', async () => {
    const password = 'Tmp#ABCD2345';
    const passwordHash = (await hashPassword(password, pepper)).hash;
    let sessionInsertAttempted = false;
    let conditionalConsumeAttempted = false;

    const env = {
      SESSION_PEPPER: pepper,
      PASSWORD_PEPPER: pepper,
      DB: {
        prepare(sql: string) {
          return {
            bind(..._params: unknown[]) {
              return {
                async first() {
                  if (sql.includes('FROM users WHERE phone_number = ?')) {
                    return {
                      id: 'user-1',
                      phone_number: '771234567',
                      full_name: 'مجد أنور عبدالرحمن',
                      grade_id: 10,
                      section_id: 'A',
                      role: 'STUDENT',
                      password_hash: passwordHash,
                    };
                  }
                  if (sql.includes('FROM user_recovery_tokens')) {
                    return {
                      id: 'temp-1',
                      expires_at: Date.now() + 60_000,
                      max_uses: 1,
                      uses_count: 0,
                      revoked: 0,
                    };
                  }
                  return null;
                },
                async run() {
                  if (sql.includes('UPDATE user_recovery_tokens') && sql.includes('uses_count = uses_count + 1')) {
                    conditionalConsumeAttempted = true;
                    return { success: true, meta: { changes: 0 } };
                  }
                  if (sql.includes('INSERT INTO sessions')) sessionInsertAttempted = true;
                  return { success: true, meta: { changes: 1 } };
                },
              };
            },
          };
        },
      },
    } as unknown as Env;

    const response = await handleLogin(new Request('https://tanweer.test/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phoneNumber: '771234567', password }),
    }), env);

    expect(conditionalConsumeAttempted).toBe(true);
    expect(response.status).toBe(401);
    expect(await response.json()).toMatchObject({
      success: false,
      error: { code: 'CREDENTIALS_EXPIRED' },
    });
    expect(sessionInsertAttempted).toBe(false);
  });

  it('stores the new password and recovery-code hashes when repairing an incomplete registration', async () => {
    const password = 'Magd#School2026';
    const existingPasswordHash = (await hashPassword(password, pepper)).hash;
    let repairedUserStatement: { sql: string; params: unknown[] } | null = null;
    const batchStatements: Array<{ sql: string; params: unknown[] }> = [];

    const env = {
      SESSION_PEPPER: pepper,
      PASSWORD_PEPPER: pepper,
      DB: {
        prepare(sql: string) {
          return {
            bind(...params: unknown[]) {
              const statement = {
                sql,
                params,
                async first() {
                  if (sql.includes('FROM users WHERE phone_number = ?')) {
                    return {
                      id: 'user-incomplete',
                      password_hash: existingPasswordHash,
                      grade_id: 10,
                      section_id: 'A',
                    };
                  }
                  if (sql.includes('FROM group_members WHERE user_id = ?')) return null;
                  if (sql.includes('FROM sessions WHERE user_id = ?')) return null;
                  if (sql.includes('FROM academic_years')) return null;
                  return null;
                },
                async run() {
                  return { success: true, meta: { changes: 1 } };
                },
              };
              return statement;
            },
          };
        },
        async batch(statements: Array<{ sql: string; params: unknown[] }>) {
          batchStatements.push(...statements);
          repairedUserStatement = statements.find((statement) =>
            statement.sql.includes('UPDATE users') &&
            statement.sql.includes('password_hash = ?') &&
            statement.sql.includes('recovery_code_hash = ?')
          ) ?? null;
          return statements.map(() => ({ success: true, meta: { changes: 1 } }));
        },
      },
    } as unknown as Env;

    const response = await handleRegister(new Request('https://tanweer.test/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        phoneNumber: '771234567',
        password,
        fullName: 'مجد أنور عبدالرحمن',
        gradeId: 10,
        sectionId: 'A',
      }),
    }), env);

    expect(response.status).toBe(200);
    const data = await response.json() as any;
    expect(data.success).toBe(true);
    expect(data.user.id).toBe('user-incomplete');
    expect(data.recoveryCode).toBeTruthy();
    expect(repairedUserStatement).not.toBeNull();

    const statement = repairedUserStatement as unknown as { sql: string; params: unknown[] };
    expect(await verifyPassword(password, String(statement.params[0]), pepper)).toBe(true);
    const expectedRecoveryHash = await hashString(
      String(data.recoveryCode).replace(/[\\s\\-]/g, '').toUpperCase(),
      pepper,
    );
    expect(statement.params[1]).toBe(expectedRecoveryHash);
    expect(batchStatements.some((entry) => entry.sql.includes('INSERT INTO sessions'))).toBe(true);
  });
});
