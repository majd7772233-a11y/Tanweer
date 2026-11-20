import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { decryptSensitivePayload, encryptSensitivePayload } from '../src/lib/crypto';
import { handleGetMyNotifications } from '../src/services/notifications';

const pepper = 'private-notification-test-secret';
const user: UserContext = {
  userId: 'student-a',
  phoneNumber: '777000001',
  fullName: 'طالب تجريبي',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT' as any,
};

describe('Private recovery notification delivery', () => {
  it('encrypts recovery-code delivery payloads and decrypts only with the configured secret', async () => {
    const recoveryCode = 'ABCD-EFGH-IJKL-MNOP';
    const encrypted = await encryptSensitivePayload(JSON.stringify({ recoveryCode }), pepper);

    expect(encrypted).not.toContain(recoveryCode);
    expect(await decryptSensitivePayload(encrypted, pepper)).toBe(JSON.stringify({ recoveryCode }));
    await expect(decryptSensitivePayload(encrypted, 'different-secret')).rejects.toThrow();
  });

  it('returns a recovery code only through the authenticated account notification query', async () => {
    const recoveryCode = 'ABCD-EFGH-IJKL-MNOP';
    const encryptedPayload = await encryptSensitivePayload(JSON.stringify({ recoveryCode }), pepper);
    const queryOwners: string[] = [];
    const env = {
      SESSION_PEPPER: pepper,
      DB: {
        prepare(sql: string) {
          let params: unknown[] = [];
          return {
            bind(...values: unknown[]) {
              params = values;
              return this;
            },
            async all() {
              queryOwners.push(String(params[0]));
              if (params[0] !== 'student-a') return { results: [] };
              return {
                results: [{
                  id: 'notification-1',
                  type: 'RECOVERY_CODE_RESET',
                  title: 'تم إصدار رمز استرداد جديد',
                  body: 'رمز الاسترداد الدائم لحسابك جاهز.',
                  encrypted_payload: encryptedPayload,
                  created_at: 123,
                }],
              };
            },
          };
        },
      },
    } as unknown as Env;

    const response = await handleGetMyNotifications(user, env);
    const data = await response.json() as any;

    expect(response.status).toBe(200);
    expect(queryOwners).toEqual(['student-a']);
    expect(data.notifications).toHaveLength(1);
    expect(data.notifications[0].recoveryCode).toBe(recoveryCode);
  });
});
