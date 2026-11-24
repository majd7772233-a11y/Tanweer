import { describe, expect, it } from 'vitest';
import { Env, UserContext } from '../src/env';
import { decryptSensitivePayload, encryptSensitivePayload } from '../src/lib/crypto';
import {
  handleArchiveMyNotification,
  handleGetMyNotifications,
  handleMarkMyNotificationRead,
  handleRestoreMyNotification,
} from '../src/services/notifications';

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


function createNotificationMutationEnv(initial: {
  id: string;
  user_id: string;
  type: string;
  read_at: number | null;
  archived_at: number | null;
}) {
  const row = { ...initial };
  const calls: Array<{ sql: string; params: unknown[] }> = [];
  const env = {
    DB: {
      prepare(sql: string) {
        return {
          bind(...params: unknown[]) {
            return {
              async first() {
                calls.push({ sql, params });
                const [id, userId] = params;
                return row.id === id && row.user_id === userId ? row : null;
              },
              async run() {
                calls.push({ sql, params });
                if (sql.includes('SET read_at = ?')) {
                  const [readAt, id, userId] = params;
                  if (row.id === id && row.user_id === userId && row.type !== 'RECOVERY_CODE_RESET' && row.archived_at == null) {
                    row.read_at = row.read_at ?? Number(readAt);
                  }
                } else if (sql.includes('SET archived_at = ?, read_at = COALESCE')) {
                  const [archivedAt, readAt, id, userId] = params;
                  if (row.id === id && row.user_id === userId && row.type !== 'RECOVERY_CODE_RESET' && row.archived_at == null) {
                    row.archived_at = Number(archivedAt);
                    row.read_at = row.read_at ?? Number(readAt);
                  }
                } else if (sql.includes('SET archived_at = NULL')) {
                  const [id, userId] = params;
                  if (row.id === id && row.user_id === userId && row.type !== 'RECOVERY_CODE_RESET') {
                    row.archived_at = null;
                  }
                }
                return { meta: { changes: 1 } };
              },
            };
          },
        };
      },
    },
  };
  return { env: env as unknown as Env, row, calls };
}

describe('Private notification inbox state', () => {
  it('marks only an owned, non-recovery notification as read', async () => {
    const { env, row, calls } = createNotificationMutationEnv({
      id: 'ntf-a',
      user_id: 'student-a',
      type: 'GROUP_JOIN_REJECTED',
      read_at: null,
      archived_at: null,
    });
    const response = await handleMarkMyNotificationRead(user, 'ntf-a', env);
    const data = await response.json() as any;

    expect(response.status).toBe(200);
    expect(data.success).toBe(true);
    expect(data.notificationId).toBe('ntf-a');
    expect(data.readAt).toBeTypeOf('number');
    expect(row.read_at).toBe(data.readAt);
    expect(calls.some(call => call.sql.includes('id = ? AND user_id = ?') && call.params.includes('student-a'))).toBe(true);
  });

  it('does not reveal or mutate another account notification', async () => {
    const { env, row, calls } = createNotificationMutationEnv({
      id: 'ntf-private',
      user_id: 'student-b',
      type: 'GROUP_JOIN_ACCEPTED',
      read_at: null,
      archived_at: null,
    });
    const response = await handleMarkMyNotificationRead(user, 'ntf-private', env);

    expect(response.status).toBe(404);
    expect(row.read_at).toBeNull();
    expect(calls.every(call => call.params.includes('student-a'))).toBe(true);
  });

  it('archives an owned notification and marks it read atomically', async () => {
    const { env, row } = createNotificationMutationEnv({
      id: 'ntf-archive',
      user_id: 'student-a',
      type: 'GROUP_JOIN_REQUEST',
      read_at: null,
      archived_at: null,
    });
    const response = await handleArchiveMyNotification(user, 'ntf-archive', env);
    const data = await response.json() as any;

    expect(response.status).toBe(200);
    expect(data.success).toBe(true);
    expect(data.archivedAt).toBeTypeOf('number');
    expect(data.readAt).toBe(data.archivedAt);
    expect(row.archived_at).toBe(data.archivedAt);
    expect(row.read_at).toBe(data.readAt);
  });

  it('restores an archived notification without making it unread again', async () => {
    const { env, row } = createNotificationMutationEnv({
      id: 'ntf-restore',
      user_id: 'student-a',
      type: 'GROUP_JOIN_ACCEPTED',
      read_at: 100,
      archived_at: 200,
    });
    const response = await handleRestoreMyNotification(user, 'ntf-restore', env);
    const data = await response.json() as any;

    expect(response.status).toBe(200);
    expect(data.success).toBe(true);
    expect(data.archivedAt).toBeNull();
    expect(data.readAt).toBe(100);
    expect(row.archived_at).toBeNull();
    expect(row.read_at).toBe(100);
  });

  it('keeps recovery-code delivery managed by Profile and blocks inbox archive actions', async () => {
    const { env, row, calls } = createNotificationMutationEnv({
      id: 'ntf-recovery',
      user_id: 'student-a',
      type: 'RECOVERY_CODE_RESET',
      read_at: null,
      archived_at: null,
    });
    const response = await handleArchiveMyNotification(user, 'ntf-recovery', env);
    const data = await response.json() as any;

    expect(response.status).toBe(409);
    expect(data.error.code).toBe('RECOVERY_NOTIFICATION_MANAGED_IN_PROFILE');
    expect(row.archived_at).toBeNull();
    expect(calls.some(call => call.sql.includes('UPDATE user_notifications'))).toBe(false);
  });
});
