import { Env, UserContext } from '../env';
import { errorResponse, jsonResponse } from '../lib/response';

export const MAX_ACTIVE_DEVICES = 3;

export interface NewDeviceSessionInput {
  userId: string;
  deviceId: string;
  sessionId: string;
  tokenHash: string;
  expiresAt: number;
  platform: string;
  appVersion: string;
  now: number;
}

export function buildNewDeviceSessionStatements(env: Env, input: NewDeviceSessionInput) {
  const { userId, deviceId, sessionId, tokenHash, expiresAt, platform, appVersion, now } = input;

  return [
    // A device represents one active slot. Re-login on the same install replaces
    // its previous token; expired sessions are removed before the limit is counted.
    env.DB.prepare(
      `DELETE FROM sessions
       WHERE user_id = ? AND (expires_at <= ? OR device_id = ?)`
    ).bind(userId, now, deviceId),

    // This INSERT is conditional and runs in the same D1 batch as the cleanup.
    // Counting DISTINCT device IDs prevents duplicate sessions from consuming slots.
    env.DB.prepare(
      `INSERT INTO sessions (id, user_id, device_id, refresh_token_hash, expires_at, created_at)
       SELECT ?, ?, ?, ?, ?, ?
       WHERE (
         SELECT COUNT(DISTINCT device_id)
         FROM sessions
         WHERE user_id = ? AND expires_at > ?
       ) < ?`
    ).bind(sessionId, userId, deviceId, tokenHash, expiresAt, now, userId, now, MAX_ACTIVE_DEVICES),

    // Keep the device inventory synchronized only when the new session exists.
    env.DB.prepare(
      `INSERT INTO devices (device_id, user_id, platform, app_version, push_token, last_seen)
       SELECT ?, ?, ?, ?, NULL, ?
       WHERE EXISTS (
         SELECT 1 FROM sessions
         WHERE id = ? AND user_id = ? AND refresh_token_hash = ?
       )
       ON CONFLICT(device_id) DO UPDATE SET
         user_id = excluded.user_id,
         platform = excluded.platform,
         app_version = excluded.app_version,
         last_seen = excluded.last_seen`
    ).bind(deviceId, userId, platform, appVersion, now, sessionId, userId, tokenHash),

    // Remove stale device inventory rows left behind by expired sessions.
    env.DB.prepare(
      `DELETE FROM devices
       WHERE user_id = ?
         AND NOT EXISTS (
           SELECT 1 FROM sessions
           WHERE sessions.user_id = ? AND sessions.device_id = devices.device_id
             AND sessions.expires_at > ?
         )`
    ).bind(userId, userId, now),
  ];
}

export async function createDeviceSession(env: Env, input: NewDeviceSessionInput): Promise<boolean> {
  const statements = buildNewDeviceSessionStatements(env, input);
  if (typeof env.DB.batch === 'function') {
    const results = await env.DB.batch(statements);
    return results[1]?.meta?.changes === 1;
  }

  // Fallback for non-Cloudflare test adapters. Production D1 always supports batch().
  await statements[0].run();
  const inserted = await statements[1].run();
  if (inserted.meta?.changes !== 1) return false;
  await statements[2].run();
  await statements[3].run();
  return true;
}

export async function revokeCreatedDeviceSession(
  env: Env,
  userId: string,
  deviceId: string,
  sessionId: string
): Promise<void> {
  await env.DB.prepare(
    `DELETE FROM sessions WHERE id = ? AND user_id = ? AND device_id = ?`
  ).bind(sessionId, userId, deviceId).run();

  await env.DB.prepare(
    `DELETE FROM devices
     WHERE device_id = ? AND user_id = ?
       AND NOT EXISTS (
         SELECT 1 FROM sessions
         WHERE device_id = ? AND user_id = ? AND expires_at > ?
       )`
  ).bind(deviceId, userId, deviceId, userId, Date.now()).run();
}

export async function handleGetMyDevices(user: UserContext, env: Env): Promise<Response> {
  const now = Date.now();
  const result = await env.DB.prepare(
    `SELECT s.device_id AS deviceId,
            COALESCE(d.platform, 'Android') AS platform,
            COALESCE(d.app_version, '') AS appVersion,
            MAX(s.created_at) AS lastSeen,
            MAX(s.created_at) AS connectedAt
     FROM sessions s
     LEFT JOIN devices d ON d.device_id = s.device_id AND d.user_id = s.user_id
     WHERE s.user_id = ? AND s.expires_at > ?
     GROUP BY s.device_id
     ORDER BY MAX(s.created_at) DESC`
  ).bind(user.userId, now).all<{
    deviceId: string;
    platform: string;
    appVersion: string;
    lastSeen: number;
    connectedAt: number;
  }>();

  const devices = (result.results || []).map(device => ({
    ...device,
    isCurrent: device.deviceId === user.deviceId,
  }));

  return jsonResponse({
    success: true,
    maxActiveDevices: MAX_ACTIVE_DEVICES,
    activeDevicesCount: devices.length,
    devices,
  });
}

export async function handleRevokeMyDevice(
  deviceId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const targetDeviceId = deviceId.trim();
  if (!targetDeviceId || targetDeviceId.length > 128) {
    return errorResponse('INVALID_DEVICE_ID', 'معرّف الجهاز غير صالح');
  }

  if (targetDeviceId === user.deviceId) {
    return errorResponse(
      'CURRENT_DEVICE',
      'لا يمكنك إزالة الجهاز الذي تستخدمه الآن من هذه الشاشة. استخدم تسجيل الخروج لإغلاق جلسته.',
      409
    );
  }

  const activeDevice = await env.DB.prepare(
    `SELECT 1 FROM sessions
     WHERE user_id = ? AND device_id = ? AND expires_at > ?
     LIMIT 1`
  ).bind(user.userId, targetDeviceId, Date.now()).first();

  if (!activeDevice) {
    return errorResponse('DEVICE_NOT_FOUND', 'هذا الجهاز لم يعد مسجلاً ضمن أجهزتك النشطة', 404);
  }

  if (typeof env.DB.batch === 'function') {
    await env.DB.batch([
      env.DB.prepare(
        `DELETE FROM sessions WHERE user_id = ? AND device_id = ?`
      ).bind(user.userId, targetDeviceId),
      env.DB.prepare(
        `DELETE FROM devices WHERE user_id = ? AND device_id = ?`
      ).bind(user.userId, targetDeviceId),
    ]);
  } else {
    await env.DB.prepare(
      `DELETE FROM sessions WHERE user_id = ? AND device_id = ?`
    ).bind(user.userId, targetDeviceId).run();
    await env.DB.prepare(
      `DELETE FROM devices WHERE user_id = ? AND device_id = ?`
    ).bind(user.userId, targetDeviceId).run();
  }

  return jsonResponse({
    success: true,
    message: 'تم إلغاء تسجيل الجهاز وإنهاء جلساته. أصبح بإمكانك تسجيل جهاز بديل.',
    maxActiveDevices: MAX_ACTIVE_DEVICES,
  });
}

export async function handleLogoutCurrentDevice(user: UserContext, env: Env): Promise<Response> {
  if (!user.deviceId) {
    return errorResponse('DEVICE_ID_MISSING', 'تعذر تحديد الجهاز الحالي لإنهاء جلسته', 409);
  }

  const result = await env.DB.prepare(
    `DELETE FROM sessions WHERE user_id = ? AND device_id = ?`
  ).bind(user.userId, user.deviceId).run();

  await env.DB.prepare(
    `DELETE FROM devices
     WHERE user_id = ? AND device_id = ?
       AND NOT EXISTS (
         SELECT 1 FROM sessions
         WHERE user_id = ? AND device_id = ? AND expires_at > ?
       )`
  ).bind(user.userId, user.deviceId, user.userId, user.deviceId, Date.now()).run();

  return jsonResponse({
    success: true,
    message: 'تم تسجيل الخروج وإنهاء جلسة هذا الجهاز',
    revokedSessions: result.meta?.changes || 0,
  });
}
