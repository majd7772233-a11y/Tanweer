import { describe, expect, it } from 'vitest';
import { Env } from '../src/env';
import { hashPassword, hashString, verifyPassword } from '../src/lib/crypto';
import { getUserFromToken } from '../src/middleware/auth';
import { handleLogin, handleLoginWithRecoveryCode, handleRegister } from '../src/services/auth';

const pepper = 'auth-hardening-test-pepper';

describe('Authentication hardening regressions', () => {
  it('fails closed when the recovery-token state cannot be read', async () => {
    const recoveryCode = 'A1B2-C3D4-E5F6';
    const recoveryHash = await hashString(recoveryCode.replace(/[\\s\\-]/g, '').toUpperCase(), pepper);
    const user = {
      id: 'recovery-user',
      phone_number: '771234567',
      full_name: 'مستخدم الاسترداد',
      grade_id: 10,
      section_id: 'A',
      role: 'STUDENT',
      status: 'ACTIVE',
      recovery_code_hash: recoveryHash,
    };
    let sessionCount = 0;
    let hashBurnAttempted = false;

    const env = {
      SESSION_PEPPER: pepper,
      PASSWORD_PEPPER: pepper,
      DB: {
        prepare(sql: string) {
          return {
            bind(...params: unknown[]) {
              return {
                async first() {
                  if (sql.includes('FROM users WHERE recovery_code_hash = ?')) {
                    return user.recovery_code_hash === params[0] ? { ...user } : null;
                  }
                  if (sql.includes('FROM user_recovery_tokens')) {
                    throw new Error('recovery-token table unavailable');
                  }
                  return null;
                },
                async run() {
                  if (sql.includes('UPDATE users') && sql.includes('recovery_code_hash = ?')) {
                    hashBurnAttempted = true;
                  }
                  if (sql.includes('INSERT INTO sessions')) sessionCount += 1;
                  return { success: true, meta: { changes: 1 } };
                },
              };
            },
          };
        },
      },
    } as unknown as Env;

    const response = await handleLoginWithRecoveryCode(new Request('https://tanweer.test/api/v1/auth/recovery-login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ recoveryCode }),
    }), env);

    expect(response.status).toBe(503);
    expect(await response.json()).toMatchObject({
      success: false,
      error: { code: 'AUTHENTICATION_UNAVAILABLE' },
    });
    expect(hashBurnAttempted).toBe(false);
    expect(sessionCount).toBe(0);
  });

  it('allows only one concurrent redemption of the same recovery code', async () => {
    const recoveryCode = 'A1B2-C3D4-E5F6';
    const recoveryHash = await hashString(recoveryCode.replace(/[\\s\\-]/g, '').toUpperCase(), pepper);
    const user = {
      id: 'recovery-user',
      phone_number: '771234567',
      full_name: 'مستخدم الاسترداد',
      grade_id: 10,
      section_id: 'A',
      role: 'STUDENT',
      status: 'ACTIVE',
      recovery_code_hash: recoveryHash,
    };
    const recoveryToken = {
      id: 'recovery-token-1',
      token_hash: recoveryHash,
      user_id: user.id,
      max_uses: 1,
      uses_count: 0,
      expires_at: Date.now() + 60_000,
      revoked: 0,
    };
    const sessions: Array<Record<string, unknown>> = [];

    const env = {
      SESSION_PEPPER: pepper,
      PASSWORD_PEPPER: pepper,
      DB: {
        prepare(sql: string) {
          return {
            bind(...params: unknown[]) {
              return {
                async first() {
                  if (sql.includes('FROM users WHERE recovery_code_hash = ?')) {
                    return user.recovery_code_hash === params[0] ? { ...user } : null;
                  }
                  if (sql.includes('FROM user_recovery_tokens')) {
                    return recoveryToken.token_hash === params[0] && recoveryToken.user_id === params[1]
                      ? { ...recoveryToken }
                      : null;
                  }
                  return null;
                },
                async run() {
                  if (sql.includes('UPDATE users') && sql.includes('recovery_code_hash = ?')) {
                    const validUserHash = user.recovery_code_hash === params[3];
                    const tokenId = params[5];
                    const tokenStillValid = tokenId == null || (
                      recoveryToken.id === tokenId &&
                      recoveryToken.user_id === params[6] &&
                      recoveryToken.revoked === 0 &&
                      recoveryToken.expires_at > Number(params[7]) &&
                      recoveryToken.uses_count < recoveryToken.max_uses
                    );
                    if (!validUserHash || !tokenStillValid) {
                      return { success: true, meta: { changes: 0 } };
                    }
                    user.recovery_code_hash = String(params[0]);
                    return { success: true, meta: { changes: 1 } };
                  }

                  if (sql.includes('UPDATE user_recovery_tokens') && sql.includes('uses_count = uses_count + 1')) {
                    const valid = recoveryToken.id === params[1] &&
                      recoveryToken.user_id === params[2] &&
                      recoveryToken.revoked === 0 &&
                      recoveryToken.expires_at > Number(params[3]) &&
                      recoveryToken.uses_count < recoveryToken.max_uses;
                    if (!valid) return { success: true, meta: { changes: 0 } };
                    recoveryToken.uses_count += 1;
                    recoveryToken.used_at = Number(params[0]);
                    return { success: true, meta: { changes: 1 } };
                  }

                  if (sql.includes('INSERT INTO sessions')) {
                    sessions.push({ id: params[0], user_id: params[1] });
                    return { success: true, meta: { changes: 1 } };
                  }

                  return { success: true, meta: { changes: 0 } };
                },
              };
            },
          };
        },
      },
    } as unknown as Env;

    const createRequest = () => new Request('https://tanweer.test/api/v1/auth/recovery-login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ recoveryCode }),
    });

    const responses = await Promise.all([
      handleLoginWithRecoveryCode(createRequest(), env),
      handleLoginWithRecoveryCode(createRequest(), env),
    ]);

    expect(responses.map(response => response.status).sort()).toEqual([200, 409]);
    expect(sessions).toHaveLength(1);
    expect(recoveryToken.uses_count).toBe(1);
    expect(String(user.recovery_code_hash)).toMatch(/^USED_/);
  });

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


  it('rejects disabled users before creating a login session', async () => {
    const password = 'School#Secure2026';
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
                      id: 'disabled-user',
                      phone_number: '771234567',
                      full_name: 'مجد أنور عبدالرحمن',
                      grade_id: 10,
                      section_id: 'A',
                      role: 'STUDENT',
                      password_hash: passwordHash,
                      status: 'DISABLED',
                    };
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

    expect(response.status).toBe(403);
    expect(await response.json()).toMatchObject({
      success: false,
      error: { code: 'ACCOUNT_DISABLED' },
    });
    expect(sessionInsertAttempted).toBe(false);
  });

  it('does not convert an internal SYSTEM_OWNER principal into an Android ADMIN session', async () => {
    const token = 'owner-session-token-for-test';
    const tokenHash = await hashString(token, pepper);

    const env = {
      SESSION_PEPPER: pepper,
      PASSWORD_PEPPER: pepper,
      DB: {
        prepare(sql: string) {
          return {
            bind(..._params: unknown[]) {
              return {
                async first() {
                  if (sql.includes('FROM sessions s') && sql.includes('JOIN users u')) {
                    return {
                      id: 'session-owner',
                      user_id: 'SYSTEM_OWNER',
                      device_id: 'device-owner',
                      expires_at: Date.now() + 60_000,
                      phone_number: '__TANWEER_INTERNAL_SYSTEM_ACTOR__',
                      full_name: 'مستخدم النظام الداخلي',
                      grade_id: 0,
                      section_id: 'SYSTEM',
                      role: 'SYSTEM_OWNER',
                      status: 'ACTIVE',
                    };
                  }
                  return null;
                },
                async run() {
                  return { success: true, meta: { changes: 1 } };
                },
              };
            },
          };
        },
      },
    } as unknown as Env;

    expect(tokenHash).toMatch(/^[a-f0-9]+$/);
    await expect(getUserFromToken(token, env)).resolves.toBeNull();
  });

  it('does not restore a banned class membership during incomplete-registration repair', async () => {
    const password = 'Magd#School2026';
    const passwordHash = (await hashPassword(password, pepper)).hash;
    let batchAttempted = false;

    const env = {
      SESSION_PEPPER: pepper,
      PASSWORD_PEPPER: pepper,
      DB: {
        prepare(sql: string) {
          return {
            bind(...params: unknown[]) {
              return {
                async first() {
                  if (sql.includes('FROM users WHERE phone_number = ?')) {
                    return {
                      id: 'banned-user',
                      password_hash: passwordHash,
                      grade_id: 10,
                      section_id: 'A',
                      status: 'ACTIVE',
                    };
                  }
                  if (sql.includes('FROM group_members WHERE group_id = ? AND user_id = ?')) {
                    return { status: 'BANNED' };
                  }
                  if (sql.includes('FROM group_members WHERE user_id = ?')) return null;
                  if (sql.includes('FROM sessions WHERE user_id = ?')) return null;
                  return null;
                },
                async run() {
                  return { success: true, meta: { changes: 1 } };
                },
              };
            },
          };
        },
        async batch() {
          batchAttempted = true;
          return [];
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

    expect(response.status).toBe(403);
    expect(await response.json()).toMatchObject({
      success: false,
      error: { code: 'GROUP_BANNED' },
    });
    expect(batchAttempted).toBe(false);
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
