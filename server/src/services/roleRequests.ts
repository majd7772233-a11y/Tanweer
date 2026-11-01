/**
 * Role Requests Service
 * Allows users to submit, track, and inspect role upgrades (Moderator / Teacher).
 */

import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';
import { Role, normalizeRole, ROLE_METADATA, ROLE_HIERARCHY, PUBLIC_ROLE_METADATA, PUBLIC_ROLE_HIERARCHY } from '../lib/roles';
import { Permission, hasPermission } from '../lib/permissions';
import { requireGroupMember } from '../middleware/permissions';
import { hashString, LEGACY_DEFAULT_PEPPER } from '../lib/crypto';
import { logRoleAudit } from './dashboard';

const WHATSAPP_CONTACT_NUMBER = '+967 735465673';

function generateShortRequestId(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let rand = '';
  for (let i = 0; i < 5; i++) {
    rand += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return `TNV-${rand}`;
}

export async function handleCreateRoleRequest(
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const allowed = await hasPermission(user, Permission.REQUEST_ROLE_UPGRADE);
  if (!allowed) {
    return errorResponse('FORBIDDEN', 'لا يمكنك تقديم طلب ترقية رتبة في الوقت الحالي', 403);
  }

  const body = await request.json() as {
    requestedRole?: string;
    reason?: string;
    groupId?: string;
  };

  if (!body.requestedRole || !body.reason || !body.reason.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى اختيار الرتبة المطلوبة وتوضيح سبب ومبرر الطلب');
  }

  const targetRole = normalizeRole(body.requestedRole);
  if (targetRole !== Role.MODERATOR && targetRole !== Role.TEACHER) {
    return errorResponse('INVALID_ROLE', 'الرتب القابلة للتقديم هي: مسؤول (MODERATOR) أو أستاذ (TEACHER). رتبة المدير (ADMIN) تُمنح حصراً بقرار مباشر من المالك.');
  }

  const currentRole = normalizeRole(user.role);
  if (ROLE_HIERARCHY[currentRole] >= ROLE_HIERARCHY[targetRole]) {
    return errorResponse('ALREADY_ELIGIBLE', 'لديك بالفعل هذه الرتبة أو رتبة أعلى منها');
  }

  const requestedGroupId = body.groupId?.trim() || null;
  if (body.groupId && !requestedGroupId) {
    return errorResponse('INVALID_GROUP', 'معرف المجموعة غير صالح');
  }
  if (requestedGroupId) {
    const memberCheck = await requireGroupMember(user, requestedGroupId, env.DB);
    if (memberCheck) return memberCheck;
  }

  // Check for existing pending request
  const existingPending = await env.DB.prepare(
    `SELECT id FROM role_requests WHERE user_id = ? AND requested_role = ? AND status = 'PENDING'`
  ).bind(user.userId, targetRole).first();

  if (existingPending) {
    return errorResponse('REQUEST_ALREADY_EXISTS', 'لديك طلب ترقية قيد المراجعة لهذه الرتبة بالفعل');
  }

  const reqId = generateShortRequestId();
  const now = Date.now();

  await env.DB.prepare(
    `INSERT INTO role_requests (id, user_id, requested_role, group_id, reason, phone_number, full_name, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)`
  ).bind(
    reqId,
    user.userId,
    targetRole,
    requestedGroupId,
    body.reason.trim(),
    user.phoneNumber || null,
    user.fullName || null,
    now
  ).run();

  await logRoleAudit(
    env.DB,
    'SUBMIT_ROLE_REQUEST',
    user.userId,
    user.role,
    user.userId,
    `تقديم طلب ترقية إلى ${ROLE_METADATA[targetRole].nameAr} برقم الطلب (${reqId})`,
    request.headers.get('CF-Connecting-IP')
  );

  const cleanWaNumber = WHATSAPP_CONTACT_NUMBER.replace(/[^0-9]/g, '');
  const waMessage = encodeURIComponent(`السلام عليكم، أنا ${user.fullName} (${user.phoneNumber})، قمت بتقديم طلب ترقية إلى رتبة ${ROLE_METADATA[targetRole].nameAr} في منصة تنوير برقم الطلب: ${reqId}. أرجو مراجعة الطلب وتزويدي برمز التفعيل.`);
  const whatsappUrl = `https://wa.me/${cleanWaNumber}?text=${waMessage}`;

  return jsonResponse({
    success: true,
    requestId: reqId,
    status: 'PENDING',
    targetRole: targetRole,
    targetRoleNameAr: ROLE_METADATA[targetRole].nameAr,
    whatsappNumber: WHATSAPP_CONTACT_NUMBER,
    whatsappUrl,
    message: `تم إنشاء طلب الترقية بنجاح برقم (${reqId}). يرجى التواصل عبر الواتساب لتأكيد الهوية واستلام رمز التفعيل السري.`,
  });
}

/**
 * Redeems an 8-digit verification code to activate a role upgrade.
 * Enforces: One-time, User-bound, Request-bound, Role-bound, 24h Expiry, Max 5 Attempts, Hashed check.
 */
export async function handleRedeemRoleCode(
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const body = await request.json() as { code?: string; requestId?: string };
  if (!body.code || !body.code.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى إدخال رمز التحقق المكون من 8 أرقام');
  }

  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER || 'tanweer-pepper-2026';
  const cleanCode = body.code.replace(/[\s\-]/g, '').trim();

  if (cleanCode.length !== 8 || !/^\d{8}$/.test(cleanCode)) {
    return errorResponse('INVALID_CODE_FORMAT', 'رمز التحقق يجب أن يتكون من 8 أرقام (مثال: 7391-8426)');
  }

  const inputHash = await hashString(cleanCode, pepper);
  const now = Date.now();

  // Find the active code and its still-approved request for this exact user.
  // A code belonging to a rejected or otherwise resolved request must never activate a role.
  let query = `
    SELECT id, request_id, user_id, role, code_hash, attempts, max_attempts, expires_at, used_at
    FROM role_verification_codes
    WHERE user_id = ? AND used_at IS NULL
  `;
  const binds: any[] = [user.userId];

  if (body.requestId) {
    query += ` AND request_id = ?`;
    binds.push(body.requestId);
  }

  query += ` ORDER BY created_at DESC LIMIT 1`;

  const codeRecord = await env.DB.prepare(query).bind(...binds).first<{
    id: string;
    request_id: string;
    user_id: string;
    role: string;
    code_hash: string;
    attempts: number;
    max_attempts: number;
    expires_at: number;
    used_at: number | null;
  }>();

  if (!codeRecord) {
    return errorResponse('NO_ACTIVE_CODE', 'لا يوجد رمز تفعيل نشط معتمد لهذا الحساب أو تم استخدامه مسبقاً', 404);
  }

  const originalReq = await env.DB.prepare(
    `SELECT group_id, status FROM role_requests WHERE id = ? AND user_id = ?`
  ).bind(codeRecord.request_id, user.userId).first<{ group_id: string | null; status: string }>();

  if (!originalReq || originalReq.status !== 'APPROVED') {
    return errorResponse('NO_ACTIVE_CODE', 'طلب الترقية المرتبط بهذا الرمز لم يعد معتمداً', 404);
  }

  if (originalReq.group_id) {
    const memberCheck = await requireGroupMember(user, originalReq.group_id, env.DB);
    if (memberCheck) return memberCheck;
  }

  const targetRole = normalizeRole(codeRecord.role);
  if (targetRole !== Role.MODERATOR && targetRole !== Role.TEACHER) {
    return errorResponse('INVALID_ROLE_CODE', 'رمز التفعيل مرتبط برتبة غير مسموح بها', 400);
  }

  // 1. Check Expiration (24 hours)
  if (codeRecord.expires_at <= now) {
    return errorResponse('CODE_EXPIRED', 'انتهت صلاحية رمز التحقق (صالح لمدة 24 ساعة فقط). يرجى طلب رمز جديد من الإدارة.', 410);
  }

  // 2. Check Attempts Limit (Max 5)
  if (codeRecord.attempts >= codeRecord.max_attempts) {
    return errorResponse('MAX_ATTEMPTS_EXCEEDED', 'تم استنفاد الحد الأقصى للمحاولات (5 محاولات). تم إيقاف الرمز لحماية الحساب.', 429);
  }

  // 3. Compare Hash with backward compatibility fallback
  let isCodeValid = codeRecord.code_hash === inputHash;
  if (!isCodeValid && pepper !== LEGACY_DEFAULT_PEPPER) {
    const legacyInputHash = await hashString(cleanCode, LEGACY_DEFAULT_PEPPER);
    if (codeRecord.code_hash === legacyInputHash) {
      isCodeValid = true;
      // Rehash with current configured secret pepper
      await env.DB.prepare(
        `UPDATE role_verification_codes SET code_hash = ? WHERE id = ?`
      ).bind(inputHash, codeRecord.id).run();
    }
  }

  if (!isCodeValid) {
    // Increment atomically so concurrent invalid attempts cannot overwrite one another.
    const attemptUpdate = await env.DB.prepare(
      `UPDATE role_verification_codes
       SET attempts = attempts + 1
       WHERE id = ? AND used_at IS NULL AND expires_at > ? AND attempts < max_attempts`
    ).bind(codeRecord.id, now).run();

    if (attemptUpdate.meta?.changes !== 1) {
      return errorResponse('MAX_ATTEMPTS_EXCEEDED', 'انتهت صلاحية الرمز أو تم إيقافه بعد استنفاد المحاولات', 429);
    }

    const updatedCode = await env.DB.prepare(
      `SELECT attempts, max_attempts FROM role_verification_codes WHERE id = ?`
    ).bind(codeRecord.id).first<{ attempts: number; max_attempts: number }>();
    const attempts = updatedCode?.attempts ?? (codeRecord.attempts + 1);
    const maxAttempts = updatedCode?.max_attempts ?? codeRecord.max_attempts;
    const remaining = maxAttempts - attempts;
    if (remaining <= 0) {
      await logRoleAudit(
        env.DB,
        'ROLE_CODE_LOCKED',
        user.userId,
        user.role,
        user.userId,
        `تم قفل رمز التفعيل لطلب (${codeRecord.request_id}) بعد استنفاد 5 محاولات خاطئة`,
        request.headers.get('CF-Connecting-IP')
      );
      return errorResponse('CODE_LOCKED', 'تم إدخال الرمز بشكل خاطئ 5 مرات. تم قفل الرمز لأسباب أمنية.', 429);
    }

    return errorResponse('INVALID_CODE', `رمز التحقق غير صحيح. متبقي لديك (${remaining}) محاولات.`);
  }

  // 4. Success: consume the code atomically before changing any role.
  // Only one concurrent request can change used_at from NULL.
  const consumeResult = await env.DB.prepare(
    `UPDATE role_verification_codes
     SET used_at = ?
     WHERE id = ? AND user_id = ? AND used_at IS NULL
       AND expires_at > ? AND attempts < max_attempts
       AND EXISTS (
         SELECT 1 FROM role_requests rr
         WHERE rr.id = role_verification_codes.request_id
           AND rr.user_id = ? AND rr.status = 'APPROVED'
       )`
  ).bind(now, codeRecord.id, user.userId, now, user.userId).run();

  if (consumeResult.meta?.changes !== 1) {
    return errorResponse(
      'CODE_ALREADY_USED',
      'تم استخدام رمز التفعيل أو إيقافه بالفعل. اطلب رمزاً جديداً من الإدارة.',
      409
    );
  }

  if (originalReq.group_id) {
    // Group-scoped upgrade: update ONLY this group's member role
    await env.DB.prepare(
      `UPDATE group_members SET role = ? WHERE group_id = ? AND user_id = ?`
    ).bind(targetRole, originalReq.group_id, user.userId).run();

    // Teacher certification is also reflected in the global user profile.
    if (targetRole === Role.TEACHER) {
      await env.DB.prepare(
        `UPDATE users SET role = ?, updated_at = ? WHERE id = ?`
      ).bind(targetRole, now, user.userId).run();
    }
  } else {
    // Global-scoped upgrade
    await env.DB.prepare(
      `UPDATE users SET role = ?, updated_at = ? WHERE id = ?`
    ).bind(targetRole, now, user.userId).run();

    if (targetRole === Role.TEACHER) {
      await env.DB.prepare(
        `UPDATE group_members SET role = ? WHERE user_id = ?`
      ).bind(targetRole, user.userId).run();
    }
  }

  // Update role request status to APPROVED
  await env.DB.prepare(
    `UPDATE role_requests SET status = 'APPROVED', review_notes = 'تم تفعيل الرتبة بنجاح عبر رمز التحقق', reviewed_at = ? WHERE id = ?`
  ).bind(now, codeRecord.request_id).run();

  await logRoleAudit(
    env.DB,
    'ROLE_UPGRADE_SUCCESS',
    user.userId,
    targetRole,
    user.userId,
    `تم تفعيل واعتماد ترقية الرتبة بنجاح إلى ${ROLE_METADATA[targetRole].nameAr} عبر إدخال رمز التحقق المعتمد`,
    request.headers.get('CF-Connecting-IP')
  );

  return jsonResponse({
    success: true,
    message: `تهانينا! تم تفعيل رتبة (${ROLE_METADATA[targetRole].nameAr}) في حسابك بنجاح 🎉`,
    newRole: targetRole,
    newRoleNameAr: ROLE_METADATA[targetRole].nameAr,
  });
}

export async function handleGetMyRoleRequests(
  user: UserContext,
  env: Env
): Promise<Response> {
  const rows = await env.DB.prepare(
    `SELECT id, requested_role, group_id, reason, status, review_notes, reviewed_at, created_at
     FROM role_requests
     WHERE user_id = ?
     ORDER BY created_at DESC`
  ).bind(user.userId).all();

  return jsonResponse({
    success: true,
    whatsappNumber: WHATSAPP_CONTACT_NUMBER,
    requests: rows.results || [],
  });
}

export function handleGetRoleMetadata(): Response {
  return jsonResponse({
    success: true,
    roles: PUBLIC_ROLE_METADATA,
    hierarchy: PUBLIC_ROLE_HIERARCHY,
    whatsappNumber: WHATSAPP_CONTACT_NUMBER,
  });
}
