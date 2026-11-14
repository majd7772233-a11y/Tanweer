import { Env, UserContext } from '../env';
import { decryptSensitivePayload, encryptSensitivePayload, generateRecoveryCode, hashString } from '../lib/crypto';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';

export const RECOVERY_NOTIFICATION_TYPE = 'RECOVERY_CODE_RESET';
const RECOVERY_CODE_MAX_USES = 2147483647;
const NEVER_EXPIRES = Number.MAX_SAFE_INTEGER;

export function buildRecoveryCodeRotationStatements(
  db: D1Database, userId: string, tokenId: string, tokenHash: string,
  encryptedPayload: string, actorId: string, now: number
): D1PreparedStatement[] {
  const notificationId = generateId('ntf');
  return [
    db.prepare(`UPDATE users SET recovery_code_hash = ?, updated_at = ?
      WHERE id = ? AND COALESCE(status, 'ACTIVE') != 'DISABLED'`).bind(tokenHash, now, userId),
    db.prepare(`UPDATE user_recovery_tokens SET revoked = 1
      WHERE user_id = ? AND type IN ('RECOVERY_CODE', 'TEMP_PASSWORD') AND revoked = 0`).bind(userId),
    db.prepare(`DELETE FROM user_notifications WHERE user_id = ? AND type = ?`).bind(userId, RECOVERY_NOTIFICATION_TYPE),
    db.prepare(`INSERT INTO user_recovery_tokens
      (id, user_id, token_hash, type, max_uses, uses_count, expires_at, revoked, created_by, created_at)
      VALUES (?, ?, ?, 'RECOVERY_CODE', ?, 0, ?, 0, ?, ?)`)
      .bind(tokenId, userId, tokenHash, RECOVERY_CODE_MAX_USES, NEVER_EXPIRES, actorId, now),
    db.prepare(`INSERT INTO user_notifications
      (id, user_id, type, title, body, encrypted_payload, related_id, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)`).bind(
        notificationId, userId, RECOVERY_NOTIFICATION_TYPE,
        'تم إصدار رمز استرداد جديد',
        'رمز الاسترداد الدائم لحسابك جاهز. لا تنتهي صلاحيته بمرور الوقت، ويبقى صالحاً حتى تغييره أو إلغائه من الإدارة. لا تشاركه مع أي شخص.',
        encryptedPayload, tokenId, now
      ),
  ];
}

export async function handleGenerateMyRecoveryCode(user: UserContext, env: Env): Promise<Response> {
  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER;
  if (!pepper) return errorResponse('CONFIG_ERROR', 'مفتاح حماية رمز الاسترداد غير مهيأ في الخادم', 500);
  if (typeof env.DB.batch !== 'function') return errorResponse('RECOVERY_ROTATION_UNAVAILABLE', 'قاعدة البيانات لا تدعم تحديث رمز الاسترداد بصورة ذرية حالياً', 503);

  const recoveryCode = generateRecoveryCode();
  const tokenHash = await hashString(recoveryCode.replace(/[\s\-]/g, '').toUpperCase(), pepper);
  const encryptedPayload = await encryptSensitivePayload(JSON.stringify({ recoveryCode }), pepper);
  const tokenId = generateId('rec_tok');
  try {
    const results = await env.DB.batch(buildRecoveryCodeRotationStatements(
      env.DB, user.userId, tokenId, tokenHash, encryptedPayload, user.userId, Date.now()
    ));
    if (results.length !== 5 || results.some((result, index) => [0, 3, 4].includes(index) && result.meta?.changes !== 1)) {
      return errorResponse('RECOVERY_ROTATION_FAILED', 'تعذر حفظ رمز الاسترداد الجديد ورسالة التسليم بصورة مكتملة', 500);
    }
  } catch (error) {
    console.error('Failed to rotate the authenticated user recovery code:', error);
    return errorResponse('RECOVERY_ROTATION_FAILED', 'تعذر تحديث رمز الاسترداد حالياً. لم يتم تأكيد العملية.', 503);
  }
  return jsonResponse({ success: true, recoveryCode, message: 'تم تغيير رمز الاسترداد. الرمز الجديد دائم حتى تغييره أو إلغائه.' });
}

export async function handleGetMyNotifications(user: UserContext, env: Env): Promise<Response> {
  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER;
  if (!pepper) return errorResponse('CONFIG_ERROR', 'مفتاح قراءة الإشعارات الخاصة غير مهيأ في الخادم', 500);
  const rows = await env.DB.prepare(
    `SELECT n.id, n.type, n.title, n.body, n.encrypted_payload, n.created_at
     FROM user_notifications n
     WHERE n.user_id = ?
       AND (n.type != ? OR EXISTS (
         SELECT 1 FROM user_recovery_tokens r
         JOIN users u ON u.id = r.user_id
         WHERE r.id = n.related_id AND r.user_id = n.user_id
           AND r.type = 'RECOVERY_CODE' AND r.revoked = 0
           AND r.token_hash = u.recovery_code_hash
       ))
     ORDER BY n.created_at DESC LIMIT 50`
  ).bind(user.userId, RECOVERY_NOTIFICATION_TYPE).all<{
    id: string; type: string; title: string; body: string;
    encrypted_payload: string | null; created_at: number;
  }>();
  try {
    const notifications = await Promise.all((rows.results || []).map(async row => {
      let recoveryCode: string | null = null;
      if (row.type === RECOVERY_NOTIFICATION_TYPE && row.encrypted_payload) {
        const payload = JSON.parse(await decryptSensitivePayload(row.encrypted_payload, pepper)) as { recoveryCode?: string };
        recoveryCode = typeof payload.recoveryCode === 'string' ? payload.recoveryCode : null;
      }
      return { id: row.id, type: row.type, title: row.title, body: row.body, createdAt: row.created_at, recoveryCode };
    }));
    return jsonResponse({ success: true, notifications });
  } catch (error) {
    console.error('Failed to decrypt account-private notification:', error);
    return errorResponse('NOTIFICATIONS_UNAVAILABLE', 'تعذر فتح الرسائل الخاصة حالياً. أعد المحاولة لاحقاً.', 503);
  }
}
