import { describe, expect, it } from 'vitest';
import { Env } from '../src/env';
import { hashPassword, hashString, verifyPassword, LEGACY_DEFAULT_PEPPER } from '../src/lib/crypto';
import { getUserFromToken } from '../src/middleware/auth';
import { handleLogin, handleLoginWithRecoveryCode, handleRegister } from '../src/services/auth';

const pepper = 'auth-hardening-test-pepper';

describe('Authentication hardening regressions', () => {
  it('checks legacy recovery-token revocation using the hash that actually matched', async () => {
    const recoveryCode = 'A1B2-C3D4-E5F6';
    const cleanCode = recoveryCode.replace(/[\s\-]/g, '').toUpperCase();
    const currentHash = await hashString(cleanCode, pepper);
    const legacyHash = await hashString(cleanCode, LEGACY_DEFAULT_PEPPER);
    let recoveryHashBurnAttempted = false;
    let sessionInsertAttempted = false;
    let tokenLookupHash: string | null = null;

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
                    if (params[0] === legacyHash) {
                      return {
                        id: 'legacy-recovery-user',
                        phone_number: '771234567',
                        full_name: 'مستخدم برمز قديم',
                        grade_id: 10,
                        section_id: 'A',
                        role: 'STUDENT',
                        status: 'ACTIVE',
                      };
                    }
                    return null;
                  }
                  if (sql.includes('FROM user_recovery_tokens')) {
                    tokenLookupHash = String(params[0]);
                    if (params[0] === legacyHash && params[1] === 'legacy-recovery-user') {
                      return {
                        id: 'legacy-token-1',
                        max_uses: 1,
                        uses_count: 0,
                        expires_at: Date.now() + 60_000,
                        revoked: 1,
                      };
                    }
                    return null;
                  }
                  return null;
                },
                async run() {
                  if (sql.includes('UPDATE users') && sql.includes('recovery_code_hash = ?')) {
                    recoveryHashBurnAttempted = true;
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

    const response = await handleLoginWithRecoveryCode(new Request('https://tanweer.test/api/v1/auth/recovery-login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ recoveryCode }),
    }), env);

    expect(tokenLookupHash).toBe(legacyHash);
    expect(tokenLookupHash).not.toBe(currentHash);
    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({
      success: false,
      error: { code: 'RECOVERY_CODE_REVOKED' },
    });
    expect(recoveryHashBurnAttempted).toBe(false);
    expect(sessionInsertAttempted).toBe(false);
  });

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

  it('allows repeated concurrent logins with a permanent recovery code', async () => {
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
      type: 'RECOVERY_CODE',
      max_uses: 1,
      uses_count: 1,
      expires_at: 0,
      revoked: 0,
      used_at: null as number | null,
    };
    const sessions: Array<Record<string, unknown>> = [];
    let recoveryTokenReadCount = 0;
    let releaseRecoveryTokenReads!: () => void;
    const bothRecoveryTokenReads = new Promise<void>((resolve) => {
      releaseRecoveryTokenReads = resolve;
    });

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
                    // Force both requests to read the same valid token snapshot before either
                    // can reach the conditional UPDATE, exercising the real race boundary.
                    recoveryTokenReadCount += 1;
                    if (recoveryTokenReadCount === 2) releaseRecoveryTokenReads();
                    await bothRecoveryTokenReads;
                    return recoveryToken.token_hash === params[0] && recoveryToken.user_id === params[1]
                      ? { ...recoveryToken }
                      : null;
                  }
                  return null;
                },
                async run() {
                  // Final credential revalidation occurs after the device session is reserved.
                  // It must accept repeated use of a permanent, non-revoked recovery code,
                  // regardless of the legacy uses_count/expiry columns.
                  if (sql.includes('SET last_seen = ?') && sql.includes('recovery_code_hash = ?')) {
                    const [now, userId, expectedHash, tokenId, existsTokenId, tokenUserId, tokenHash] = params as any[];
                    const validUserHash = user.id === userId && user.recovery_code_hash === expectedHash;
                    const tokenStillValid = tokenId == null || (
                      recoveryToken.id === tokenId &&
                      recoveryToken.user_id === tokenUserId &&
                      tokenUserId === userId &&
                      recoveryToken.token_hash === tokenHash &&
                      recoveryToken.type === 'RECOVERY_CODE' &&
                      recoveryToken.revoked === 0
                    );
                    return { success: true, meta: { changes: validUserHash && tokenStillValid ? 1 : 0 } };
                  }

                  if (sql.includes('UPDATE users') && sql.includes('recovery_code_hash = ?')) {
                    // The former single-use burn path must never be reached.
                    return { success: true, meta: { changes: 0 } };
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
                    sessions.push({ id: params[0], user_id: params[1], device_id: params[2] });
                    return { success: true, meta: { changes: 1 } };
                  }

                  if (sql.includes('DELETE FROM sessions WHERE id = ? AND user_id = ? AND device_id = ?')) {
                    const before = sessions.length;
                    const [sessionId, userId, deviceId] = params;
                    for (let index = sessions.length - 1; index >= 0; index--) {
                      if (
                        sessions[index].id === sessionId &&
                        sessions[index].user_id === userId &&
                        sessions[index].device_id === deviceId
                      ) {
                        sessions.splice(index, 1);
                      }
                    }
                    return { success: true, meta: { changes: before - sessions.length } };
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

    expect(responses.map(response => response.status).sort()).toEqual([200, 200]);
    expect(sessions).toHaveLength(2);
    expect(recoveryToken.uses_count).toBe(1);
    expect(user.recovery_code_hash).toBe(recoveryHash);
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

  it('does not restore a banned class membership through public registration', async () => {
    const password = 'Magd#School2026'; const passwordHash = (await hashPassword(password, pepper)).hash; let batchAttempted = false;
    const env = { SESSION_PEPPER: pepper, PASSWORD_PEPPER: pepper, DB: {
      prepare(sql: string) { return { bind(..._params: unknown[]) { return {
        async first() {
          if (sql.includes('FROM users WHERE phone_number = ?')) return { id: 'banned-user', password_hash: passwordHash, grade_id: 10, section_id: 'A', status: 'ACTIVE' };
          if (sql.includes('FROM group_members WHERE group_id = ? AND user_id = ?')) return { status: 'BANNED' };
          return null;
        }, async run() { return { success: true, meta: { changes: 1 } }; },
      }; } }; }, async batch() { batchAttempted = true; return []; },
    } } as unknown as Env;
    const response = await handleRegister(new Request('https://tanweer.test/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phoneNumber: '771234567', password, fullName: 'مجد أنور عبدالرحمن', gradeId: 10, sectionId: 'A' }) }), env);
    expect(response.status).toBe(403); expect(await response.json()).toMatchObject({ success: false, error: { code: 'GROUP_BANNED' } }); expect(batchAttempted).toBe(false);
  });

  it('does not use a missing session to move an existing account to another class', async () => {
    const password = 'Magd#School2026'; const passwordHash = (await hashPassword(password, pepper)).hash; let batchAttempted = false; const checkedGroups: unknown[] = [];
    const env = { SESSION_PEPPER: pepper, PASSWORD_PEPPER: pepper, DB: {
      prepare(sql: string) { return { bind(...params: unknown[]) {
        if (sql.includes('FROM group_members WHERE group_id = ? AND user_id = ?')) checkedGroups.push(params[0]);
        return {
          async first() {
            if (sql.includes('FROM users WHERE phone_number = ?')) return { id: 'existing-user', password_hash: passwordHash, grade_id: 10, section_id: 'A', status: 'ACTIVE' };
            if (sql.includes('FROM group_members WHERE group_id = ? AND user_id = ?')) return { status: 'ACTIVE' };
            return null;
          }, async run() { return { success: true, meta: { changes: 1 } }; },
        };
      } }; }, async batch() { batchAttempted = true; return []; },
    } } as unknown as Env;
    const response = await handleRegister(new Request('https://tanweer.test/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phoneNumber: '771234567', password, fullName: 'مجد أنور عبدالرحمن', gradeId: 11, sectionId: 'B' }) }), env);
    expect(response.status).toBe(409); expect(await response.json()).toMatchObject({ success: false, error: { code: 'PHONE_EXISTS' } }); expect(checkedGroups).toEqual(['class_10_A']); expect(batchAttempted).toBe(false);
  });

  it('does not publicly repair an existing account with no membership or session', async () => {
    const password = 'Magd#School2026'; const passwordHash = (await hashPassword(password, pepper)).hash; let batchAttempted = false;
    const env = { SESSION_PEPPER: pepper, PASSWORD_PEPPER: pepper, DB: {
      prepare(sql: string) { return { bind(..._params: unknown[]) { return {
        async first() { if (sql.includes('FROM users WHERE phone_number = ?')) return { id: 'partial-user', password_hash: passwordHash, grade_id: 10, section_id: 'A', status: 'ACTIVE' }; return null; },
        async run() { return { success: true, meta: { changes: 1 } }; },
      }; } }; }, async batch() { batchAttempted = true; return []; },
    } } as unknown as Env;
    const response = await handleRegister(new Request('https://tanweer.test/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phoneNumber: '771234567', password, fullName: 'مجد أنور عبدالرحمن', gradeId: 10, sectionId: 'A' }) }), env);
    expect(response.status).toBe(409); expect(await response.json()).toMatchObject({ success: false, error: { code: 'PHONE_EXISTS' } }); expect(batchAttempted).toBe(false);
  });
});
