/**
 * Tanweer Control Center (Owner Dashboard & Management Core)
 * Unified Administrative Engine & Cloudflare Worker Dashboard
 * URL: https://tanweer.magd.workers.dev/dashboard
 */

import { Env } from '../env';
import { errorResponse, jsonResponse } from '../lib/response';
import { Role, normalizeRole, ROLE_METADATA } from '../lib/roles';
import { generateId } from '../lib/ids';
import { hashString } from '../lib/crypto';
import { applyDecisionExecution } from './governance';

const OWNER_COOKIE_NAME = 'tanweer_owner_token';

/**
 * Extracts and verifies the owner authentication session from cookie or header.
 * The session is completely independent from Android client sessions.
 * Returns unauthorized if TANWEER_OWNER_SECRET is unconfigured (no fallback defaults).
 */
export async function verifyOwnerAuth(request: Request, env: Env): Promise<{ isOwner: boolean; ownerId: string }> {
  const secretKey = env.TANWEER_OWNER_SECRET;
  if (!secretKey || !secretKey.trim()) {
    // If TANWEER_OWNER_SECRET is not configured in production, dashboard access is strictly disabled
    return { isOwner: false, ownerId: '' };
  }

  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER || 'tanweer-pepper-2026';

  // 1. Direct Secret Header check (for API / script automation)
  const headerSecret = request.headers.get('X-Owner-Secret');
  if (headerSecret && headerSecret === secretKey) {
    return { isOwner: true, ownerId: 'SYSTEM_OWNER' };
  }

  // 2. Cookie or Bearer Token check
  let token: string | null = null;
  const authHeader = request.headers.get('Authorization');
  if (authHeader && authHeader.startsWith('Bearer ')) {
    token = authHeader.substring(7);
  } else {
    const cookieHeader = request.headers.get('Cookie');
    if (cookieHeader) {
      const match = cookieHeader.match(/(?:^|;\s*)tanweer_owner_token=([^;]+)/);
      if (match) {
        token = decodeURIComponent(match[1]);
      }
    }
  }

  if (!token) {
    return { isOwner: false, ownerId: '' };
  }

  // Verify token hash
  const expectedToken = await hashString(`owner_authenticated_${secretKey}`, pepper);
  if (token === expectedToken) {
    return { isOwner: true, ownerId: 'SYSTEM_OWNER' };
  }

  return { isOwner: false, ownerId: '' };
}

/**
 * Audit log recording helper.
 */
export async function logRoleAudit(
  db: D1Database,
  action: string,
  actorId: string,
  actorRole: string,
  targetUserId: string | null,
  details: string,
  ipAddress: string | null = null
): Promise<void> {
  try {
    const id = generateId('audit');
    await db.prepare(
      `INSERT INTO role_audit_log (id, action, actor_id, actor_role, target_user_id, details, ip_address, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(id, action, actorId, actorRole, targetUserId, details, ipAddress, Date.now()).run();
  } catch (e) {
    console.error('Failed to write audit log:', e);
  }
}

/**
 * Handles Owner Login via secret key.
 */
export async function handleOwnerLogin(request: Request, env: Env): Promise<Response> {
  const secretKey = env.TANWEER_OWNER_SECRET;
  if (!secretKey || !secretKey.trim()) {
    return errorResponse('CONFIGURATION_ERROR', 'لوحة التحكم معطلة: مفتاح المالك TANWEER_OWNER_SECRET غير مهيأ في متغيرات البيئة', 500);
  }

  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER || 'tanweer-pepper-2026';

  let secret = '';
  const contentType = request.headers.get('Content-Type') || '';

  if (contentType.includes('application/json')) {
    const body = await request.json() as { secret?: string };
    secret = body.secret || '';
  } else if (contentType.includes('application/x-www-form-urlencoded')) {
    const formData = await request.formData();
    secret = formData.get('secret')?.toString() || '';
  }

  if (!secret || secret !== secretKey) {
    return jsonResponse({ success: false, message: 'مفتاح مالك المنظومة السري غير صحيح' }, 401);
  }

  const token = await hashString(`owner_authenticated_${secretKey}`, pepper);

  await logRoleAudit(
    env.DB,
    'OWNER_LOGIN_SUCCESS',
    'SYSTEM_OWNER',
    'SYSTEM_OWNER',
    null,
    'تسجيل دخول ناجح لمالك النظام في غرفة التحكم',
    request.headers.get('CF-Connecting-IP') || 'local'
  );

  const cookieHeader = `${OWNER_COOKIE_NAME}=${encodeURIComponent(token)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=86400`;

  return new Response(JSON.stringify({ success: true, message: 'تم التحقق من هوية المالك بنجاح' }), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Set-Cookie': cookieHeader,
    },
  });
}

/**
 * Handles Owner Logout.
 */
export async function handleOwnerLogout(): Promise<Response> {
  const cookieHeader = `${OWNER_COOKIE_NAME}=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0`;
  return new Response(JSON.stringify({ success: true, message: 'تم تسجيل الخروج بنجاح' }), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Set-Cookie': cookieHeader,
    },
  });
}

/**
 * Returns platform overview statistics across all 18 sections.
 */
export async function handleGetDashboardStats(env: Env): Promise<Response> {
  try {
    const [
      usersTotal,
      studentsCount,
      moderatorsCount,
      teachersCount,
      adminsCount,
      disabledUsersCount,
      groupsTotal,
      pendingRequests,
      totalRequests,
      contentsTotal,
      homeworksTotal,
      examsTotal,
      eventsTotal,
      schedulesTotal,
      proposalsTotal,
      issuesTotal,
      messagesTotal,
      mediaTotal,
      deletionRequestsTotal,
      activeSessionsTotal,
      auditLogsTotal
    ] = await Promise.all([
      env.DB.prepare(`SELECT COUNT(*) as count FROM users`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM users WHERE role = 'STUDENT' OR role = 'MEMBER'`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM users WHERE role = 'MODERATOR'`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM users WHERE role = 'TEACHER' OR role = 'VERIFIED_TEACHER'`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM users WHERE role = 'ADMIN'`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM users WHERE status = 'DISABLED'`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM groups`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM role_requests WHERE status = 'PENDING'`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM role_requests`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM contents`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM homeworks`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM exams`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM events`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM schedule_slots`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM schedule_proposals WHERE status = 'PENDING'`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM issues`).first<{ count: number }>(),
      env.DB.prepare(`SELECT COUNT(*) as count FROM chat_messages`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM content_media`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM deletion_requests`).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM sessions WHERE expires_at > ?`).bind(Date.now()).first<{ count: number }>().catch(() => ({ count: 0 })),
      env.DB.prepare(`SELECT COUNT(*) as count FROM role_audit_log`).first<{ count: number }>().catch(() => ({ count: 0 }))
    ]);

    return jsonResponse({
      success: true,
      stats: {
        users: {
          total: usersTotal?.count || 0,
          students: studentsCount?.count || 0,
          moderators: moderatorsCount?.count || 0,
          teachers: teachersCount?.count || 0,
          admins: adminsCount?.count || 0,
          disabled: disabledUsersCount?.count || 0,
        },
        teachers: teachersCount?.count || 0,
        moderators: moderatorsCount?.count || 0,
        administrators: adminsCount?.count || 0,
        groups: groupsTotal?.count || 0,
        roleRequests: {
          total: totalRequests?.count || 0,
          pending: pendingRequests?.count || 0,
        },
        content: contentsTotal?.count || 0,
        homework: homeworksTotal?.count || 0,
        exams: examsTotal?.count || 0,
        events: eventsTotal?.count || 0,
        schedules: {
          slots: schedulesTotal?.count || 0,
          pendingProposals: proposalsTotal?.count || 0,
        },
        issues: issuesTotal?.count || 0,
        chat: messagesTotal?.count || 0,
        media: mediaTotal?.count || 0,
        votes: deletionRequestsTotal?.count || 0,
        security: {
          activeSessions: activeSessionsTotal?.count || 0,
        },
        auditLog: auditLogsTotal?.count || 0,
        system: {
          environment: env.ENVIRONMENT || 'production',
          workerRegion: 'Cloudflare Edge',
          dbStatus: 'D1 Connected',
          timestamp: Date.now(),
        },
      },
    });
  } catch (err: any) {
    return errorResponse('STATS_ERROR', err?.message || 'فشل جلب إحصائيات المنظومة', 500);
  }
}

/**
 * Returns filtered list of users with search and pagination.
 */
export async function handleGetDashboardUsers(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const search = url.searchParams.get('q')?.trim() || '';
  const roleFilter = url.searchParams.get('role')?.toUpperCase() || '';
  const statusFilter = url.searchParams.get('status')?.toUpperCase() || '';
  const limit = Math.min(parseInt(url.searchParams.get('limit') || '50'), 100);
  const offset = Math.max(parseInt(url.searchParams.get('offset') || '0'), 0);

  let query = `SELECT id, phone_number, full_name, grade_id, section_id, role, COALESCE(status, 'ACTIVE') as status, last_seen, created_at FROM users WHERE 1=1`;
  const binds: any[] = [];

  if (search) {
    query += ` AND (full_name LIKE ? OR phone_number LIKE ?)`;
    binds.push(`%${search}%`, `%${search}%`);
  }

  if (roleFilter && roleFilter !== 'ALL') {
    if (roleFilter === 'STUDENT') {
      query += ` AND (role = 'STUDENT' OR role = 'MEMBER')`;
    } else if (roleFilter === 'TEACHER') {
      query += ` AND (role = 'TEACHER' OR role = 'VERIFIED_TEACHER')`;
    } else {
      query += ` AND role = ?`;
      binds.push(roleFilter);
    }
  }

  if (statusFilter && statusFilter !== 'ALL') {
    query += ` AND COALESCE(status, 'ACTIVE') = ?`;
    binds.push(statusFilter);
  }

  query += ` ORDER BY created_at DESC LIMIT ? OFFSET ?`;
  binds.push(limit, offset);

  const stmt = env.DB.prepare(query);
  const results = await (binds.length > 0 ? stmt.bind(...binds) : stmt).all();

  const users = (results.results || []).map((u: any) => ({
    id: u.id,
    phoneNumber: u.phone_number,
    fullName: u.full_name,
    gradeId: u.grade_id,
    sectionId: u.section_id,
    role: normalizeRole(u.role),
    status: u.status || 'ACTIVE',
    lastSeen: u.last_seen,
    createdAt: u.created_at,
  }));

  return jsonResponse({
    success: true,
    users,
    count: users.length,
    offset,
    limit,
  });
}

/**
 * Updates a user's role and logs the audit event.
 */
export async function handleChangeUserRole(
  userId: string,
  actorId: string,
  request: Request,
  env: Env
): Promise<Response> {
  const body = await request.json() as { newRole?: string; reason?: string };
  if (!body.newRole) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد الرتبة الجديدة');
  }

  const newRole = normalizeRole(body.newRole);
  if (newRole === Role.SYSTEM_OWNER) {
    return errorResponse('FORBIDDEN', 'لا يمكن تعيين رتبة مالك المنظومة (SYSTEM_OWNER) عبر لوحة التحكم. المالك فريد ويتم تكوينه حصراً في متغيرات البيئة.', 403);
  }

  const user = await env.DB.prepare(`SELECT id, full_name, role FROM users WHERE id = ?`).bind(userId).first<{
    id: string;
    full_name: string;
    role: string;
  }>();

  if (!user) {
    return errorResponse('USER_NOT_FOUND', 'المستخدم غير موجود', 404);
  }

  const oldRole = normalizeRole(user.role);
  const now = Date.now();

  const bodyWithScope = body as { newRole?: string; reason?: string; scope?: string; groupId?: string };
  if (bodyWithScope.groupId || bodyWithScope.scope === 'GROUP') {
    // Group-scoped role update
    if (bodyWithScope.groupId) {
      await env.DB.prepare(
        `UPDATE group_members SET role = ? WHERE group_id = ? AND user_id = ?`
      ).bind(newRole, bodyWithScope.groupId, userId).run();
    }
  } else {
    // Global role update
    await env.DB.prepare(`UPDATE users SET role = ?, updated_at = ? WHERE id = ?`).bind(newRole, now, userId).run();
    if (newRole === Role.STUDENT) {
      // Complete downgrade: clean up all elevated group roles across all groups
      await env.DB.prepare(
        `UPDATE group_members SET role = ? WHERE user_id = ?`
      ).bind(Role.STUDENT, userId).run();
    } else if (newRole === Role.MODERATOR) {
      // Downgrade any higher teacher/admin group roles
      await env.DB.prepare(
        `UPDATE group_members SET role = ? WHERE user_id = ? AND (role = 'TEACHER' OR role = 'ADMIN' OR role = 'VERIFIED_TEACHER')`
      ).bind(Role.MODERATOR, userId).run();
    } else if (newRole === Role.TEACHER || newRole === Role.ADMIN) {
      await env.DB.prepare(
        `UPDATE group_members SET role = ? WHERE user_id = ?`
      ).bind(newRole, userId).run();
    }
  }

  await logRoleAudit(
    env.DB,
    'CHANGE_USER_ROLE',
    actorId,
    'SYSTEM_OWNER',
    userId,
    `تغيير رتبة المستخدم ${user.full_name} من ${ROLE_METADATA[oldRole].nameAr} إلى ${ROLE_METADATA[newRole].nameAr}${bodyWithScope.groupId ? ` في المجموعة (${bodyWithScope.groupId})` : ' (عام)'}. السبب: ${body.reason || 'إجراء إداري'}`,
    request.headers.get('CF-Connecting-IP')
  );

  return jsonResponse({
    success: true,
    message: `تم ترقية وتحديث رتبة ${user.full_name} إلى ${ROLE_METADATA[newRole].nameAr} بنجاح`,
    user: {
      id: userId,
      role: newRole,
      scope: bodyWithScope.groupId ? 'GROUP' : 'GLOBAL',
      groupId: bodyWithScope.groupId || null,
    },
  });
}

/**
 * Freezes / Disables a user account.
 */
export async function handleDisableUser(
  userId: string,
  actorId: string,
  request: Request,
  env: Env
): Promise<Response> {
  const user = await env.DB.prepare(`SELECT id, full_name FROM users WHERE id = ?`).bind(userId).first<{ id: string; full_name: string }>();
  if (!user) {
    return errorResponse('USER_NOT_FOUND', 'المستخدم غير موجود', 404);
  }

  await env.DB.prepare(`UPDATE users SET status = 'DISABLED', updated_at = ? WHERE id = ?`).bind(Date.now(), userId).run();
  await env.DB.prepare(`DELETE FROM sessions WHERE user_id = ?`).bind(userId).run();

  await logRoleAudit(
    env.DB,
    'DISABLE_USER',
    actorId,
    'SYSTEM_OWNER',
    userId,
    `تم تجميد حساب المستخدم ${user.full_name} وإبطال كافة جلساته النشطة`,
    request.headers.get('CF-Connecting-IP')
  );

  return jsonResponse({
    success: true,
    message: `تم تجميد حساب ${user.full_name} وإخراجه من المنظومة بنجاح`,
  });
}

/**
 * Unfreezes / Enables a user account.
 */
export async function handleEnableUser(
  userId: string,
  actorId: string,
  request: Request,
  env: Env
): Promise<Response> {
  const user = await env.DB.prepare(`SELECT id, full_name FROM users WHERE id = ?`).bind(userId).first<{ id: string; full_name: string }>();
  if (!user) {
    return errorResponse('USER_NOT_FOUND', 'المستخدم غير موجود', 404);
  }

  await env.DB.prepare(`UPDATE users SET status = 'ACTIVE', updated_at = ? WHERE id = ?`).bind(Date.now(), userId).run();

  await logRoleAudit(
    env.DB,
    'ENABLE_USER',
    actorId,
    'SYSTEM_OWNER',
    userId,
    `تم إلغاء تجميد حساب المستخدم ${user.full_name} وإعادته للعمل`,
    request.headers.get('CF-Connecting-IP')
  );

  return jsonResponse({
    success: true,
    message: `تم تنشيط وإلغاء تجميد حساب ${user.full_name} بنجاح`,
  });
}

/**
 * Revokes all active sessions for a user (Force Logout).
 */
export async function handleRevokeUserSessions(
  userId: string,
  actorId: string,
  request: Request,
  env: Env
): Promise<Response> {
  const user = await env.DB.prepare(`SELECT id, full_name FROM users WHERE id = ?`).bind(userId).first<{ id: string; full_name: string }>();
  if (!user) {
    return errorResponse('USER_NOT_FOUND', 'المستخدم غير موجود', 404);
  }

  await env.DB.prepare(`DELETE FROM sessions WHERE user_id = ?`).bind(userId).run();

  await logRoleAudit(
    env.DB,
    'REVOKE_USER_SESSIONS',
    actorId,
    'SYSTEM_OWNER',
    userId,
    `تم إنهاء وإلغاء جميع الجلسات النشطة للمستخدم ${user.full_name}`,
    request.headers.get('CF-Connecting-IP')
  );

  return jsonResponse({
    success: true,
    message: `تم إبطال جميع جلسات ${user.full_name} بنجاح وإخراجه من التطبيق فورياً`,
  });
}

/**
 * Returns role upgrade requests.
 */
export async function handleGetRoleRequests(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const status = url.searchParams.get('status') || 'ALL';

  let query = `
    SELECT rr.id, rr.user_id, rr.requested_role, rr.group_id, rr.reason, rr.status,
           rr.reviewed_by, rr.review_notes, rr.reviewed_at, rr.created_at,
           u.full_name, u.phone_number, u.grade_id, u.section_id, u.role as current_role
    FROM role_requests rr
    JOIN users u ON rr.user_id = u.id
  `;

  if (status !== 'ALL') {
    query += ` WHERE rr.status = ? ORDER BY rr.created_at DESC`;
    const rows = await env.DB.prepare(query).bind(status).all();
    return jsonResponse({ success: true, requests: rows.results || [] });
  } else {
    query += ` ORDER BY rr.created_at DESC`;
    const rows = await env.DB.prepare(query).all();
    return jsonResponse({ success: true, requests: rows.results || [] });
  }
}

/**
 * Approves a role request.
 */
export async function handleApproveRoleRequest(
  requestId: string,
  actorId: string,
  request: Request,
  env: Env
): Promise<Response> {
  const req = await env.DB.prepare(
    `SELECT rr.*, u.full_name, u.phone_number FROM role_requests rr JOIN users u ON rr.user_id = u.id WHERE rr.id = ?`
  ).bind(requestId).first<{
    id: string;
    user_id: string;
    requested_role: string;
    group_id?: string;
    status: string;
    full_name: string;
    phone_number: string;
  }>();

  if (!req) {
    return errorResponse('REQUEST_NOT_FOUND', 'طلب الترقية غير موجود', 404);
  }

  if (req.status !== 'PENDING') {
    return errorResponse('ALREADY_REVIEWED', 'تم البت في هذا الطلب مسبقاً');
  }

  const pepper = env.SESSION_PEPPER || env.PASSWORD_PEPPER || 'tanweer-pepper-2026';
  const targetRole = normalizeRole(req.requested_role);
  const now = Date.now();
  const expiresAt = now + 24 * 60 * 60 * 1000; // 24 hours

  // Generate 8-digit cryptographically secure verification code: XXXX-XXXX (e.g. 7391-8426)
  const randomBuffer = new Uint32Array(2);
  crypto.getRandomValues(randomBuffer);
  const part1 = (1000 + (randomBuffer[0] % 9000)).toString();
  const part2 = (1000 + (randomBuffer[1] % 9000)).toString();
  const codeDisplay = `${part1}-${part2}`;
  const codeRaw = `${part1}${part2}`;
  const codeHash = await hashString(codeRaw, pepper);
  const codeId = generateId('code');

  // Insert verification code record storing only code_hash (no plain text in DB)
  await env.DB.prepare(
    `INSERT INTO role_verification_codes (id, request_id, user_id, role, code_hash, code_display, attempts, max_attempts, expires_at, created_at)
     VALUES (?, ?, ?, ?, ?, NULL, 0, 5, ?, ?)`
  ).bind(
    codeId,
    requestId,
    req.user_id,
    targetRole,
    codeHash,
    expiresAt,
    now
  ).run();

  await env.DB.prepare(
    `UPDATE role_requests SET status = 'APPROVED', reviewed_by = ?, review_notes = ?, reviewed_at = ? WHERE id = ?`
  ).bind(actorId, 'تم اعتماد الطلب وتوليد رمز تفعيل مشفر بنجاح', now, requestId).run();

  await logRoleAudit(
    env.DB,
    'GENERATE_ROLE_CODE',
    actorId,
    'SYSTEM_OWNER',
    req.user_id,
    `تم اعتماد طلب الترقية لـ ${req.full_name} إلى ${ROLE_METADATA[targetRole].nameAr} وتوليد رمز التفعيل (صالح 24 ساعة)`,
    request.headers.get('CF-Connecting-IP')
  );

  return jsonResponse({
    success: true,
    message: `تم اعتماد الطلب وتوليد رمز التفعيل بنجاح`,
    code: codeDisplay,
    expiresAt,
    targetRole: ROLE_METADATA[targetRole].nameAr,
    applicantName: req.full_name,
    applicantPhone: req.phone_number,
  });
}

/**
 * Rejects a role request.
 */
export async function handleRejectRoleRequest(
  requestId: string,
  actorId: string,
  request: Request,
  env: Env
): Promise<Response> {
  const body = await request.json() as { reason?: string };
  const req = await env.DB.prepare(
    `SELECT rr.*, u.full_name FROM role_requests rr JOIN users u ON rr.user_id = u.id WHERE rr.id = ?`
  ).bind(requestId).first<{
    id: string;
    user_id: string;
    full_name: string;
    status: string;
  }>();

  if (!req) {
    return errorResponse('REQUEST_NOT_FOUND', 'طلب الترقية غير موجود', 404);
  }

  const now = Date.now();
  await env.DB.prepare(
    `UPDATE role_requests SET status = 'REJECTED', reviewed_by = ?, review_notes = ?, reviewed_at = ? WHERE id = ?`
  ).bind(actorId, body.reason || 'لم يستوفِ الشروط المطلوبة', now, requestId).run();

  await logRoleAudit(
    env.DB,
    'REJECT_ROLE_REQUEST',
    actorId,
    'SYSTEM_OWNER',
    req.user_id,
    `تم رفض طلب الترقية لـ ${req.full_name}. السبب: ${body.reason || 'لم يستوفِ الشروط'}`,
    request.headers.get('CF-Connecting-IP')
  );

  return jsonResponse({
    success: true,
    message: `تم رفض الطلب بنجاح وتسجيل السبب`,
  });
}

/**
 * Returns roles metadata, definitions, permission matrices, and active counts (عرض الرتب).
 */
export async function handleGetDashboardRoles(env: Env): Promise<Response> {
  try {
    const counts = await env.DB.prepare(
      `SELECT role, COUNT(*) as count FROM users GROUP BY role`
    ).all<{ role: string; count: number }>();

    const countsMap: Record<string, number> = {};
    for (const row of counts.results || []) {
      const normalized = normalizeRole(row.role);
      countsMap[normalized] = (countsMap[normalized] || 0) + row.count;
    }

    const roles = Object.values(Role).map(role => ({
      ...ROLE_METADATA[role],
      userCount: countsMap[role] || 0,
    }));

    return jsonResponse({
      success: true,
      roles,
    });
  } catch (err: any) {
    return errorResponse('ROLES_ERROR', err?.message || 'فشل جلب قائمة الرتب', 500);
  }
}

/**
 * Returns groups overview.
 */
export async function handleGetDashboardGroups(env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    `SELECT g.id, g.name, g.type, g.description, g.academic_year_id, g.created_at,
            (SELECT COUNT(*) FROM group_members WHERE group_id = g.id AND status = 'ACTIVE') as member_count
     FROM groups g
     ORDER BY g.created_at DESC`
  ).all();

  return jsonResponse({
    success: true,
    groups: rows.results || [],
  });
}

/**
 * Returns administrative audit logs.
 */
export async function handleGetAuditLogs(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const limit = Math.min(parseInt(url.searchParams.get('limit') || '50'), 100);
  const offset = Math.max(parseInt(url.searchParams.get('offset') || '0'), 0);

  const rows = await env.DB.prepare(
    `SELECT id, action, actor_id, actor_role, target_user_id, details, ip_address, created_at
     FROM role_audit_log
     ORDER BY created_at DESC
     LIMIT ? OFFSET ?`
  ).bind(limit, offset).all();

  return jsonResponse({
    success: true,
    logs: rows.results || [],
  });
}

/**
 * God Mode: Returns all lessons / content items across the platform with pagination.
 */
export async function handleGetDashboardContents(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const search = url.searchParams.get('q') || '';
  const limit = Math.min(parseInt(url.searchParams.get('limit') || '100'), 500);
  const offset = Math.max(parseInt(url.searchParams.get('offset') || '0'), 0);

  let query = `
    SELECT c.id, c.group_id, c.subject_id, c.created_by, c.title, c.type, c.views_count, c.useful_count, c.is_pinned, c.created_at,
           u.full_name as author_name, g.name as group_name
    FROM contents c
    LEFT JOIN users u ON c.created_by = u.id
    LEFT JOIN groups g ON c.group_id = g.id
  `;
  const binds: any[] = [];
  if (search) {
    query += ` WHERE c.title LIKE ? OR u.full_name LIKE ?`;
    binds.push(`%${search}%`, `%${search}%`);
  }
  query += ` ORDER BY c.created_at DESC LIMIT ? OFFSET ?`;
  binds.push(limit, offset);

  const rows = await env.DB.prepare(query).bind(...binds).all();
  return jsonResponse({ success: true, contents: rows.results || [], count: rows.results?.length || 0, offset, limit });
}

/**
 * God Mode: Force deletes content.
 */
export async function handleDeleteDashboardContent(contentId: string, actorId: string, env: Env): Promise<Response> {
  await env.DB.prepare(`DELETE FROM contents WHERE id = ?`).bind(contentId).run();
  await env.DB.prepare(`DELETE FROM content_media WHERE content_id = ?`).bind(contentId).run();
  await env.DB.prepare(`DELETE FROM content_corrections WHERE content_id = ?`).bind(contentId).run();
  await logRoleAudit(env.DB, 'OWNER_DELETE_CONTENT', actorId, 'SYSTEM_OWNER', null, `حذف إداري شامل للدرس (${contentId}) من لوحة المالك`);
  return jsonResponse({ success: true, message: 'تم حذف الدرس بنجاح' });
}

/**
 * God Mode: Toggles pinned state of content.
 */
export async function handleTogglePinDashboardContent(contentId: string, actorId: string, env: Env): Promise<Response> {
  const c = await env.DB.prepare(`SELECT is_pinned FROM contents WHERE id = ?`).bind(contentId).first<{ is_pinned: number }>();
  if (!c) return errorResponse('NOT_FOUND', 'الدرس غير موجود', 404);
  const newPinned = c.is_pinned ? 0 : 1;
  await env.DB.prepare(`UPDATE contents SET is_pinned = ? WHERE id = ?`).bind(newPinned, contentId).run();
  await logRoleAudit(env.DB, 'OWNER_PIN_CONTENT', actorId, 'SYSTEM_OWNER', null, `تعديل تثبيت الدرس (${contentId}) إلى: ${newPinned}`);
  return jsonResponse({ success: true, isPinned: newPinned });
}

/**
 * God Mode: Returns all homeworks across groups with pagination.
 */
export async function handleGetDashboardHomeworks(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const limit = Math.min(parseInt(url.searchParams.get('limit') || '100'), 500);
  const offset = Math.max(parseInt(url.searchParams.get('offset') || '0'), 0);

  const rows = await env.DB.prepare(`
    SELECT h.*, u.full_name as author_name, g.name as group_name
    FROM homeworks h
    LEFT JOIN users u ON h.created_by = u.id
    LEFT JOIN groups g ON h.group_id = g.id
    ORDER BY h.created_at DESC LIMIT ? OFFSET ?
  `).bind(limit, offset).all();
  return jsonResponse({ success: true, homeworks: rows.results || [], count: rows.results?.length || 0, offset, limit });
}

/**
 * God Mode: Deletes a homework.
 */
export async function handleDeleteDashboardHomework(hwId: string, actorId: string, env: Env): Promise<Response> {
  await env.DB.prepare(`DELETE FROM homeworks WHERE id = ?`).bind(hwId).run();
  await env.DB.prepare(`DELETE FROM user_homework_completions WHERE homework_id = ?`).bind(hwId).run();
  await logRoleAudit(env.DB, 'OWNER_DELETE_HOMEWORK', actorId, 'SYSTEM_OWNER', null, `حذف الواجب (${hwId}) من لوحة المالك`);
  return jsonResponse({ success: true, message: 'تم حذف الواجب بنجاح' });
}

/**
 * God Mode: Returns all exams across groups with pagination.
 */
export async function handleGetDashboardExams(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const limit = Math.min(parseInt(url.searchParams.get('limit') || '100'), 500);
  const offset = Math.max(parseInt(url.searchParams.get('offset') || '0'), 0);

  const rows = await env.DB.prepare(`
    SELECT e.*, u.full_name as author_name, g.name as group_name
    FROM exams e
    LEFT JOIN users u ON e.created_by = u.id
    LEFT JOIN groups g ON e.group_id = g.id
    ORDER BY e.created_at DESC LIMIT ? OFFSET ?
  `).bind(limit, offset).all();
  return jsonResponse({ success: true, exams: rows.results || [], count: rows.results?.length || 0, offset, limit });
}

/**
 * God Mode: Deletes an exam.
 */
export async function handleDeleteDashboardExam(examId: string, actorId: string, env: Env): Promise<Response> {
  await env.DB.prepare(`DELETE FROM exams WHERE id = ?`).bind(examId).run();
  await logRoleAudit(env.DB, 'OWNER_DELETE_EXAM', actorId, 'SYSTEM_OWNER', null, `حذف الاختبار (${examId}) من لوحة المالك`);
  return jsonResponse({ success: true, message: 'تم حذف الاختبار بنجاح' });
}

/**
 * God Mode: Returns all events across groups with pagination.
 */
export async function handleGetDashboardEvents(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const limit = Math.min(parseInt(url.searchParams.get('limit') || '100'), 500);
  const offset = Math.max(parseInt(url.searchParams.get('offset') || '0'), 0);

  const rows = await env.DB.prepare(`
    SELECT ev.*, u.full_name as author_name, g.name as group_name
    FROM events ev
    LEFT JOIN users u ON ev.created_by = u.id
    LEFT JOIN groups g ON ev.group_id = g.id
    ORDER BY ev.created_at DESC LIMIT ? OFFSET ?
  `).bind(limit, offset).all();
  return jsonResponse({ success: true, events: rows.results || [], count: rows.results?.length || 0, offset, limit });
}

/**
 * God Mode: Deletes an event.
 */
export async function handleDeleteDashboardEvent(eventId: string, actorId: string, env: Env): Promise<Response> {
  await env.DB.prepare(`DELETE FROM events WHERE id = ?`).bind(eventId).run();
  await logRoleAudit(env.DB, 'OWNER_DELETE_EVENT', actorId, 'SYSTEM_OWNER', null, `حذف الفعالية (${eventId}) من لوحة المالك`);
  return jsonResponse({ success: true, message: 'تم حذف الفعالية بنجاح' });
}

/**
 * God Mode: Returns all issues / Q&A with pagination.
 */
export async function handleGetDashboardIssues(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const limit = Math.min(parseInt(url.searchParams.get('limit') || '100'), 500);
  const offset = Math.max(parseInt(url.searchParams.get('offset') || '0'), 0);

  const rows = await env.DB.prepare(`
    SELECT i.*, u.full_name as author_name, g.name as group_name,
           (SELECT COUNT(*) FROM issue_comments WHERE issue_id = i.id) as comments_count
    FROM issues i
    LEFT JOIN users u ON i.created_by = u.id
    LEFT JOIN groups g ON i.group_id = g.id
    ORDER BY i.created_at DESC LIMIT ? OFFSET ?
  `).bind(limit, offset).all();
  return jsonResponse({ success: true, issues: rows.results || [], count: rows.results?.length || 0, offset, limit });
}

/**
 * God Mode: Deletes an issue and its comments.
 */
export async function handleDeleteDashboardIssue(issueId: string, actorId: string, env: Env): Promise<Response> {
  await env.DB.prepare(`DELETE FROM issues WHERE id = ?`).bind(issueId).run();
  await env.DB.prepare(`DELETE FROM issue_comments WHERE issue_id = ?`).bind(issueId).run();
  await logRoleAudit(env.DB, 'OWNER_DELETE_ISSUE', actorId, 'SYSTEM_OWNER', null, `حذف السؤال (${issueId}) من لوحة المالك`);
  return jsonResponse({ success: true, message: 'تم حذف السؤال بنجاح' });
}

/**
 * God Mode: Returns active schedules across groups.
 */
export async function handleGetDashboardSchedules(env: Env): Promise<Response> {
  const rows = await env.DB.prepare(`
    SELECT sv.id, sv.group_id, sv.version_number, sv.valid_from, sv.is_active, sv.created_at, sv.created_by,
           g.name as group_name, u.full_name as author_name,
           (SELECT COUNT(*) FROM schedule_slots WHERE version_id = sv.id) as slots_count
    FROM schedule_versions sv
    LEFT JOIN groups g ON sv.group_id = g.id
    LEFT JOIN users u ON sv.created_by = u.id
    WHERE sv.is_active = 1
    ORDER BY sv.group_id ASC
  `).all();
  return jsonResponse({ success: true, schedules: rows.results || [] });
}

/**
 * God Mode: Resets / purges a schedule for a group.
 */
export async function handleResetDashboardSchedule(groupId: string, actorId: string, env: Env): Promise<Response> {
  const versions = await env.DB.prepare(`SELECT id FROM schedule_versions WHERE group_id = ?`).bind(groupId).all<{ id: string }>();
  for (const v of versions.results || []) {
    await env.DB.prepare(`DELETE FROM schedule_slots WHERE version_id = ?`).bind(v.id).run();
  }
  await env.DB.prepare(`DELETE FROM schedule_versions WHERE group_id = ?`).bind(groupId).run();
  await env.DB.prepare(`DELETE FROM schedule_proposals WHERE group_id = ?`).bind(groupId).run();
  await logRoleAudit(env.DB, 'OWNER_RESET_SCHEDULE', actorId, 'SYSTEM_OWNER', null, `تصفير وإعادة تعيين جدول المجموعة (${groupId}) بالكامل`);
  return jsonResponse({ success: true, message: 'تمت إعادة تعيين جدول الشعبة بنجاح' });
}

/**
 * God Mode: Returns community decisions across groups.
 */
export async function handleGetDashboardDecisions(env: Env): Promise<Response> {
  const rows = await env.DB.prepare(`
    SELECT cd.*, g.name as group_name, u.full_name as creator_name
    FROM community_decisions cd
    LEFT JOIN groups g ON cd.group_id = g.id
    LEFT JOIN users u ON cd.created_by = u.id
    ORDER BY cd.created_at DESC LIMIT 100
  `).all();
  return jsonResponse({ success: true, decisions: rows.results || [] });
}

/**
 * God Mode: Force applies a community decision.
 */
export async function handleApplyDashboardDecision(decisionId: string, actorId: string, env: Env): Promise<Response> {
  const decision = await env.DB.prepare(
    `SELECT * FROM community_decisions WHERE id = ?`
  ).bind(decisionId).first<{
    id: string;
    group_id: string;
    request_type: string;
    target_id: string;
    status: string;
  }>();

  if (!decision) {
    return errorResponse('DECISION_NOT_FOUND', 'القرار الجماعي غير موجود', 404);
  }

  if (decision.status !== 'PENDING') {
    return errorResponse('DECISION_ALREADY_RESOLVED', `لا يمكن تطبيق هذا القرار لأنه محسوم مسبقاً بحالة (${decision.status})`, 400);
  }

  const result = await applyDecisionExecution(
    decisionId,
    decision.request_type,
    decision.target_id,
    decision.group_id,
    actorId,
    env
  );

  if (!result.success) {
    return errorResponse('EXECUTION_FAILED', result.message || 'فشل تطبيق القرار البرمجي في قاعدة البيانات', 500);
  }

  await logRoleAudit(
    env.DB,
    'OWNER_APPLY_DECISION',
    actorId,
    'SYSTEM_OWNER',
    null,
    `تنفيذ وتطبيق القرار الجماعي (${decisionId} - ${decision.request_type}) يدوياً وبأثر فوري من لوحة المالك`
  );

  return jsonResponse({
    success: true,
    message: result.message || 'تم تطبيق القرار وتنفيذ أثره الفعلي على قاعدة البيانات بنجاح ✅',
  });
}

/**
 * God Mode: Force rejects a community decision.
 */
export async function handleRejectDashboardDecision(decisionId: string, actorId: string, env: Env): Promise<Response> {
  const decision = await env.DB.prepare(
    `SELECT * FROM community_decisions WHERE id = ?`
  ).bind(decisionId).first<{ id: string; status: string; request_type: string }>();

  if (!decision) {
    return errorResponse('DECISION_NOT_FOUND', 'القرار الجماعي غير موجود', 404);
  }

  if (decision.status !== 'PENDING') {
    return errorResponse('DECISION_ALREADY_RESOLVED', `لا يمكن رفض هذا القرار لأنه محسوم مسبقاً بحالة (${decision.status})`, 400);
  }

  const now = Date.now();
  await env.DB.prepare(`UPDATE community_decisions SET status = 'REJECTED', applied_at = ?, applied_by = ? WHERE id = ?`).bind(now, actorId, decisionId).run();
  await logRoleAudit(env.DB, 'OWNER_REJECT_DECISION', actorId, 'SYSTEM_OWNER', null, `رفض وإغلاق القرار الجماعي (${decisionId}) يدوياً من لوحة المالك`);
  return jsonResponse({ success: true, message: 'تم رفض وإغلاق القرار بنجاح ❌' });
}

/**
 * God Mode: Returns recent chat messages across all groups.
 */
export async function handleGetDashboardChatMessages(request: Request, env: Env): Promise<Response> {
  const rows = await env.DB.prepare(`
    SELECT cm.id, cm.group_id, cm.user_id, cm.content, cm.created_at,
           u.full_name, u.phone_number, u.role, g.name as group_name
    FROM chat_messages cm
    LEFT JOIN users u ON cm.user_id = u.id
    LEFT JOIN groups g ON cm.group_id = g.id
    ORDER BY cm.created_at DESC LIMIT 100
  `).all();
  return jsonResponse({ success: true, messages: rows.results || [] });
}

/**
 * God Mode: Deletes a chat message.
 */
export async function handleDeleteDashboardChatMessage(msgId: string, actorId: string, env: Env): Promise<Response> {
  await env.DB.prepare(`DELETE FROM chat_messages WHERE id = ?`).bind(msgId).run();
  await logRoleAudit(env.DB, 'OWNER_DELETE_CHAT_MSG', actorId, 'SYSTEM_OWNER', null, `حذف رسالة الشات (${msgId}) من لوحة المالك`);
  return jsonResponse({ success: true, message: 'تم حذف الرسالة بنجاح' });
}

/**
 * God Mode: Returns content corrections.
 */
export async function handleGetDashboardCorrections(env: Env): Promise<Response> {
  const rows = await env.DB.prepare(`
    SELECT cc.*, c.title as content_title, u.full_name as author_name, g.name as group_name
    FROM content_corrections cc
    LEFT JOIN contents c ON cc.content_id = c.id
    LEFT JOIN users u ON cc.user_id = u.id
    LEFT JOIN groups g ON cc.group_id = g.id
    ORDER BY cc.created_at DESC LIMIT 100
  `).all();
  return jsonResponse({ success: true, corrections: rows.results || [] });
}

/**
 * God Mode: Direct SQL console executor for the Owner.
 */
export async function handleExecuteSql(request: Request, actorId: string, env: Env): Promise<Response> {
  const body = await request.json() as { query?: string };
  if (!body.query || !body.query.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة استعلام SQL للتنفيذ');
  }

  const query = body.query.trim();

  try {
    const isSelect = /^SELECT\s+/i.test(query) || /^PRAGMA\s+/i.test(query);
    if (isSelect) {
      const results = await env.DB.prepare(query).all();
      return jsonResponse({
        success: true,
        type: 'SELECT',
        rowCount: results.results?.length || 0,
        data: results.results || [],
      });
    } else {
      const res = await env.DB.prepare(query).run();
      await logRoleAudit(env.DB, 'OWNER_SQL_EXECUTE', actorId, 'SYSTEM_OWNER', null, `تنفيذ استعلام SQL مباشر: ${query.slice(0, 100)}`);
      return jsonResponse({
        success: true,
        type: 'MUTATION',
        meta: res.meta,
        message: 'تم تنفيذ الاستعلام بنجاح',
      });
    }
  } catch (err: any) {
    return jsonResponse({
      success: false,
      error: err.message || 'خطأ أثناء تنفيذ استعلام SQL',
    }, 400);
  }
}

/**
 * Renders the Owner Login HTML page.
 */
export function renderLoginHtml(errorMessage?: string): Response {
  const html = `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>🔐 دخول مالك المنظومة | TANWEER CONTROL CENTER</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;800;900&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #070B14;
      --card-bg: rgba(14, 23, 42, 0.85);
      --border: rgba(0, 229, 255, 0.25);
      --cyan: #00E5FF;
      --cyan-glow: rgba(0, 229, 255, 0.15);
      --text-pri: #F8FAFC;
      --text-sec: #94A3B8;
      --error: #EF4444;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Cairo', system-ui, sans-serif; }
    body {
      background: var(--bg);
      color: var(--text-pri);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
      background-image: 
        radial-gradient(circle at 10% 20%, rgba(0, 229, 255, 0.08) 0%, transparent 40%),
        radial-gradient(circle at 90% 80%, rgba(16, 185, 129, 0.08) 0%, transparent 40%);
    }
    .login-card {
      width: 100%;
      max-width: 440px;
      background: var(--card-bg);
      backdrop-filter: blur(16px);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 2.5rem;
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.6), 0 0 40px var(--cyan-glow);
      text-align: center;
    }
    .badge-icon {
      font-size: 3rem;
      margin-bottom: 1rem;
      display: inline-block;
      animation: pulse 3s infinite ease-in-out;
    }
    @keyframes pulse {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.06); }
    }
    h1 { font-size: 1.5rem; font-weight: 800; color: var(--cyan); margin-bottom: 0.5rem; }
    p { font-size: 0.9rem; color: var(--text-sec); margin-bottom: 2rem; }
    .input-group { text-align: right; margin-bottom: 1.5rem; }
    label { display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.5rem; color: var(--text-pri); }
    input[type="password"] {
      width: 100%;
      background: rgba(3, 7, 18, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 12px;
      padding: 0.85rem 1rem;
      color: white;
      font-size: 1rem;
      direction: ltr;
      text-align: center;
      letter-spacing: 2px;
      transition: all 0.2s;
    }
    input[type="password"]:focus {
      outline: none;
      border-color: var(--cyan);
      box-shadow: 0 0 15px var(--cyan-glow);
    }
    .submit-btn {
      width: 100%;
      background: linear-gradient(135deg, #00E5FF 0%, #2979FF 100%);
      color: #070B14;
      border: none;
      border-radius: 12px;
      padding: 0.9rem;
      font-size: 1rem;
      font-weight: 800;
      cursor: pointer;
      transition: all 0.2s;
    }
    .submit-btn:hover {
      opacity: 0.92;
      transform: translateY(-1px);
      box-shadow: 0 5px 20px rgba(0, 229, 255, 0.4);
    }
    .error-msg {
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid var(--error);
      color: #FCA5A5;
      padding: 0.75rem;
      border-radius: 10px;
      font-size: 0.85rem;
      margin-bottom: 1.5rem;
      display: none;
    }
  </style>
</head>
<body>
  <div class="login-card">
    <div class="badge-icon">🔐</div>
    <h1>TANWEER CONTROL CENTER</h1>
    <p>غرفة التحكم المركزية لمالك المنظومة (Cloudflare Edge)</p>

    <div class="error-msg" id="errorDiv"></div>

    <form id="loginForm">
      <div class="input-group">
        <label for="secret">مفتاح مالك النظام السري (TANWEER_OWNER_SECRET)</label>
        <input type="password" id="secret" name="secret" placeholder="••••••••••••••••" required autofocus autocomplete="current-password">
      </div>

      <button type="submit" class="submit-btn" id="submitBtn">تحقق والدخول إلى غرفة التحكم ✨</button>
    </form>
  </div>

  <script>
    const form = document.getElementById('loginForm');
    const errorDiv = document.getElementById('errorDiv');
    const submitBtn = document.getElementById('submitBtn');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const secret = document.getElementById('secret').value;
      if (!secret) return;

      submitBtn.disabled = true;
      submitBtn.textContent = 'جاري التحقق...';
      errorDiv.style.display = 'none';

      try {
        const res = await fetch('/api/dashboard/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ secret })
        });
        const data = await res.json();
        if (data.success) {
          window.location.href = '/dashboard';
        } else {
          errorDiv.textContent = data.message || 'مفتاح المالك السري غير صحيح';
          errorDiv.style.display = 'block';
          submitBtn.disabled = false;
          submitBtn.textContent = 'تحقق والدخول إلى غرفة التحكم ✨';
        }
      } catch (err) {
        errorDiv.textContent = 'حدث خطأ في الاتصال بالخادم';
        errorDiv.style.display = 'block';
        submitBtn.disabled = false;
        submitBtn.textContent = 'تحقق والدخول إلى غرفة التحكم ✨';
      }
    });
  </script>
</body>
</html>`;

  return new Response(html, {
    status: 200,
    headers: { 'Content-Type': 'text/html; charset=utf-8' },
  });
}

/**
 * Renders the Full-featured Single Page Dashboard HTML.
 * Includes all 18 sections requested by Tanweer Control Center specifications.
 */
export function renderDashboardHtml(): Response {
  const html = `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>✨ TANWEER CONTROL CENTER | لوحة تحكم المنظومة</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;800;900&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #070B14;
      --sidebar-bg: #0B1120;
      --card-bg: rgba(15, 23, 42, 0.7);
      --card-hover: rgba(30, 41, 59, 0.7);
      --border: rgba(255, 255, 255, 0.08);
      --border-accent: rgba(0, 229, 255, 0.3);
      --cyan: #00E5FF;
      --cyan-glow: rgba(0, 229, 255, 0.15);
      --emerald: #10B981;
      --amber: #F59E0B;
      --ruby: #EF4444;
      --purple: #A855F7;
      --text-pri: #F8FAFC;
      --text-sec: #94A3B8;
      --text-muted: #64748B;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Cairo', system-ui, sans-serif; }
    body {
      background: var(--bg);
      color: var(--text-pri);
      display: flex;
      min-height: 100vh;
      overflow-x: hidden;
    }
    /* Sidebar */
    .sidebar {
      width: 290px;
      background: var(--sidebar-bg);
      border-left: 1px solid var(--border);
      display: flex;
      flex-direction: column;
      flex-shrink: 0;
      position: sticky;
      top: 0;
      height: 100vh;
      z-index: 10;
    }
    .brand {
      padding: 1.5rem 1.25rem;
      border-bottom: 1px solid var(--border);
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .brand-icon {
      font-size: 1.8rem;
      background: var(--cyan-glow);
      width: 44px;
      height: 44px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 12px;
      border: 1px solid var(--border-accent);
    }
    .brand-title { font-size: 1.05rem; font-weight: 800; color: var(--cyan); }
    .brand-sub { font-size: 0.75rem; color: var(--text-sec); }
    .nav-links {
      padding: 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      flex: 1;
      overflow-y: auto;
    }
    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.65rem 0.9rem;
      border-radius: 10px;
      color: var(--text-sec);
      cursor: pointer;
      font-weight: 600;
      font-size: 0.85rem;
      transition: all 0.2s;
    }
    .nav-item:hover {
      background: rgba(255, 255, 255, 0.04);
      color: var(--text-pri);
    }
    .nav-item.active {
      background: var(--cyan-glow);
      color: var(--cyan);
      border: 1px solid var(--border-accent);
    }
    .nav-badge {
      margin-right: auto;
      background: var(--ruby);
      color: white;
      font-size: 0.7rem;
      padding: 2px 7px;
      border-radius: 99px;
      font-weight: 800;
    }
    .owner-info {
      padding: 1.25rem;
      border-top: 1px solid var(--border);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .logout-btn {
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: var(--ruby);
      padding: 0.4rem 0.8rem;
      border-radius: 8px;
      font-size: 0.8rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.2s;
    }
    .logout-btn:hover { background: var(--ruby); color: white; }

    /* Main Content */
    .main-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      height: 100vh;
      overflow-y: auto;
    }
    .top-bar {
      padding: 1.25rem 2rem;
      border-bottom: 1px solid var(--border);
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: rgba(7, 11, 20, 0.8);
      backdrop-filter: blur(12px);
      position: sticky;
      top: 0;
      z-index: 5;
    }
    .page-title { font-size: 1.35rem; font-weight: 800; }
    .page-body { padding: 1.75rem 2rem; }

    /* Stats Grid */
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 1rem;
      margin-bottom: 1.75rem;
    }
    .stat-card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 14px;
      padding: 1.2rem;
      position: relative;
      overflow: hidden;
    }
    .stat-card::after {
      content: '';
      position: absolute;
      top: 0; right: 0; width: 4px; height: 100%;
      background: var(--cyan);
    }
    .stat-card.teachers::after { background: var(--purple); }
    .stat-card.moderators::after { background: var(--amber); }
    .stat-card.requests::after { background: var(--ruby); }
    .stat-card.groups::after { background: var(--emerald); }
    .stat-card.security::after { background: #3B82F6; }

    .stat-num { font-size: 1.8rem; font-weight: 900; margin-bottom: 0.2rem; }
    .stat-label { font-size: 0.8rem; color: var(--text-sec); font-weight: 600; }

    /* Tables & Cards */
    .section-card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 1.5rem;
      margin-bottom: 1.75rem;
    }
    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1.25rem;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
    .filter-bar {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      flex-wrap: wrap;
    }
    .search-input {
      background: rgba(3, 7, 18, 0.6);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 0.5rem 1rem;
      color: white;
      font-size: 0.85rem;
      min-width: 200px;
    }
    .search-input:focus { outline: none; border-color: var(--cyan); }
    .role-select {
      background: rgba(3, 7, 18, 0.6);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 0.5rem 0.75rem;
      color: white;
      font-size: 0.85rem;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: right;
      font-size: 0.85rem;
    }
    th {
      padding: 0.75rem 1rem;
      background: rgba(255, 255, 255, 0.02);
      color: var(--text-sec);
      border-bottom: 1px solid var(--border);
      font-weight: 700;
    }
    td {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      vertical-align: middle;
    }
    tr:hover td { background: rgba(255, 255, 255, 0.02); }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.25rem 0.6rem;
      border-radius: 8px;
      font-size: 0.75rem;
      font-weight: 700;
    }
    .badge.student { background: rgba(148, 163, 184, 0.15); color: #CBD5E1; }
    .badge.moderator { background: rgba(245, 158, 11, 0.15); color: var(--amber); }
    .badge.teacher { background: rgba(168, 85, 247, 0.15); color: var(--purple); }
    .badge.admin { background: rgba(0, 229, 255, 0.15); color: var(--cyan); }
    .badge.owner { background: rgba(239, 68, 68, 0.2); color: var(--ruby); }
    .badge.pending { background: rgba(245, 158, 11, 0.2); color: var(--amber); }
    .badge.approved { background: rgba(16, 185, 129, 0.2); color: var(--emerald); }
    .badge.rejected { background: rgba(239, 68, 68, 0.2); color: var(--ruby); }
    .badge.active { background: rgba(16, 185, 129, 0.2); color: var(--emerald); }
    .badge.disabled { background: rgba(239, 68, 68, 0.2); color: var(--ruby); }

    .action-btn {
      padding: 0.35rem 0.65rem;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: rgba(255, 255, 255, 0.05);
      color: white;
      font-size: 0.75rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.15s;
    }
    .action-btn:hover { background: var(--cyan-glow); border-color: var(--cyan); color: var(--cyan); }
    .action-btn.approve { background: rgba(16, 185, 129, 0.15); color: var(--emerald); border-color: rgba(16, 185, 129, 0.3); }
    .action-btn.approve:hover { background: var(--emerald); color: black; }
    .action-btn.reject { background: rgba(239, 68, 68, 0.15); color: var(--ruby); border-color: rgba(239, 68, 68, 0.3); }
    .action-btn.reject:hover { background: var(--ruby); color: white; }
    .action-btn.freeze { background: rgba(239, 68, 68, 0.1); color: var(--ruby); border-color: rgba(239, 68, 68, 0.25); }
    .action-btn.freeze:hover { background: var(--ruby); color: white; }
    .action-btn.unfreeze { background: rgba(16, 185, 129, 0.1); color: var(--emerald); border-color: rgba(16, 185, 129, 0.25); }
    .action-btn.unfreeze:hover { background: var(--emerald); color: black; }

    /* Modal */
    .modal-overlay {
      position: fixed;
      top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(0, 0, 0, 0.75);
      backdrop-filter: blur(8px);
      display: none;
      align-items: center;
      justify-content: center;
      z-index: 100;
      padding: 1rem;
    }
    .modal-card {
      background: var(--sidebar-bg);
      border: 1px solid var(--border-accent);
      border-radius: 20px;
      width: 100%;
      max-width: 480px;
      padding: 2rem;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
    }
    .modal-title { font-size: 1.25rem; font-weight: 800; color: var(--cyan); margin-bottom: 0.5rem; }
    .modal-sub { font-size: 0.85rem; color: var(--text-sec); margin-bottom: 1.5rem; }
    .form-group { margin-bottom: 1.25rem; }
    .form-group label { display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.5rem; }
    .form-control {
      width: 100%;
      background: rgba(3, 7, 18, 0.6);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 0.75rem;
      color: white;
      font-size: 0.9rem;
    }
    .menu-toggle-btn {
      display: none;
      background: var(--cyan-glow);
      border: 1px solid var(--border-accent);
      color: var(--cyan);
      font-size: 1.2rem;
      padding: 0.4rem 0.75rem;
      border-radius: 8px;
      cursor: pointer;
    }
    @media (max-width: 900px) {
      body {
        flex-direction: column;
      }
      .sidebar {
        width: 100%;
        height: auto;
        position: sticky;
        top: 0;
        z-index: 20;
        border-left: none;
        border-bottom: 1px solid var(--border);
      }
      .brand {
        padding: 0.85rem 1.25rem;
        justify-content: space-between;
      }
      .menu-toggle-btn {
        display: block;
      }
      .nav-links {
        display: none;
        max-height: 55vh;
        overflow-y: auto;
      }
      .nav-links.mobile-open {
        display: flex;
      }
      .owner-info {
        display: none;
      }
      .owner-info.mobile-open {
        display: flex;
      }
      .main-content {
        height: auto;
        overflow-y: visible;
      }
      .top-bar {
        padding: 1rem;
      }
      .page-body {
        padding: 1rem;
      }
      .stats-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
    @media (max-width: 500px) {
      .stats-grid {
        grid-template-columns: 1fr;
      }
    }
    .toast {
      position: fixed;
      bottom: 2rem;
      left: 2rem;
      background: #0F172A;
      border: 1px solid var(--cyan);
      color: white;
      padding: 0.9rem 1.5rem;
      border-radius: 12px;
      font-weight: 700;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
      display: none;
      z-index: 1000;
    }
  </style>
</head>
<body>
  <!-- Sidebar -->
  <aside class="sidebar">
    <div class="brand">
      <div style="display:flex; align-items:center; gap:0.75rem;">
        <div class="brand-icon">✨</div>
        <div>
          <div class="brand-title">TANWEER CONTROL</div>
          <div class="brand-sub">غرفة التحكم المركزية (God Mode)</div>
        </div>
      </div>
      <button class="menu-toggle-btn" onclick="toggleMobileMenu()">☰</button>
    </div>

    <nav class="nav-links" id="dashboardNav">
      <div class="nav-item active" onclick="switchTab('overview')">
        <span>📊</span> <span>نظرة عامة والمنظومة</span>
      </div>
      <div class="nav-item" onclick="switchTab('users')">
        <span>👥</span> <span>Users (المستخدمون)</span>
      </div>
      <div class="nav-item" onclick="switchTab('roles')">
        <span>👑</span> <span>Roles (عرض الرتب)</span>
      </div>
      <div class="nav-item" onclick="switchTab('requests')">
        <span>📋</span> <span>Role Requests (طلبات الترقية)</span>
        <span class="nav-badge" id="requestsBadge" style="display:none;">0</span>
      </div>
      <div class="nav-item" onclick="switchTab('contents')">
        <span>📚</span> <span>Lessons & Content (الدروس)</span>
      </div>
      <div class="nav-item" onclick="switchTab('homeworks')">
        <span>📝</span> <span>Homework & Exams (الواجبات)</span>
      </div>
      <div class="nav-item" onclick="switchTab('issues')">
        <span>❓</span> <span>Issues & Q&A (الاستفسارات)</span>
      </div>
      <div class="nav-item" onclick="switchTab('schedules')">
        <span>📅</span> <span>Schedules (جداول الحصص)</span>
      </div>
      <div class="nav-item" onclick="switchTab('decisions')">
        <span>🗳️</span> <span>Governance & Votes (القرارات)</span>
      </div>
      <div class="nav-item" onclick="switchTab('chat')">
        <span>💬</span> <span>Live Chat (المحادثات الحية)</span>
      </div>
      <div class="nav-item" onclick="switchTab('corrections')">
        <span>✏️</span> <span>Corrections (طلبات التصحيح)</span>
      </div>
      <div class="nav-item" onclick="switchTab('groups')">
        <span>🏫</span> <span>Groups (الشعب والنوادي)</span>
      </div>
      <div class="nav-item" onclick="switchTab('sql')">
        <span>🛠️</span> <span>SQL Studio (استوديو البيانات)</span>
      </div>
      <div class="nav-item" onclick="switchTab('audit')">
        <span>📜</span> <span>Audit Log (سجل العمليات)</span>
      </div>
      <div class="nav-item" onclick="switchTab('system')">
        <span>⚙️</span> <span>Security & System (الأمان)</span>
      </div>
    </nav>

    <div class="owner-info">
      <div>
        <div style="font-weight:800; font-size:0.85rem; color:var(--cyan);">SYSTEM OWNER 👑</div>
        <div style="font-size:0.75rem; color:var(--text-sec);">جلسة مشفرة مستقلة</div>
      </div>
      <button class="logout-btn" onclick="logout()">خروج</button>
    </div>
  </aside>

  <!-- Main Content -->
  <main class="main-content">
    <header class="top-bar">
      <div class="page-title" id="pageTitle">نظرة عامة على المنظومة</div>
      <div>
        <span class="badge owner">🔐 جلسة المالك نشطة (Server Edge)</span>
      </div>
    </header>

    <div class="page-body">
      <!-- Overview Tab: All 18 System Sections -->
      <section id="tab-overview">
        <h3 style="font-size:1rem; font-weight:800; color:var(--cyan); margin-bottom:1rem;">✨ TANWEER CONTROL CENTER — إحصائيات المنظومة الحية</h3>
        <div class="stats-grid">
          <div class="stat-card" onclick="switchTab('users')" style="cursor:pointer;">
            <div class="stat-num" id="statUsers" style="color:var(--cyan);">-</div>
            <div class="stat-label">👥 Users (المستخدمون)</div>
          </div>
          <div class="stat-card teachers" onclick="switchTab('teachers')" style="cursor:pointer;">
            <div class="stat-num" id="statTeachers" style="color:var(--purple);">-</div>
            <div class="stat-label">🎓 Teachers (الأساتذة)</div>
          </div>
          <div class="stat-card moderators" onclick="switchTab('moderators')" style="cursor:pointer;">
            <div class="stat-num" id="statModerators" style="color:var(--amber);">-</div>
            <div class="stat-label">🛡️ Moderators (المشرفون)</div>
          </div>
          <div class="stat-card" onclick="switchTab('admins')" style="cursor:pointer;">
            <div class="stat-num" id="statAdmins" style="color:var(--cyan);">-</div>
            <div class="stat-label">👑 Administrators (المدراء)</div>
          </div>
          <div class="stat-card groups" onclick="switchTab('groups')" style="cursor:pointer;">
            <div class="stat-num" id="statGroups" style="color:var(--emerald);">-</div>
            <div class="stat-label">🏫 Groups (الشعب)</div>
          </div>
          <div class="stat-card requests" onclick="switchTab('requests')" style="cursor:pointer;">
            <div class="stat-num" id="statPendingRequests" style="color:var(--ruby);">-</div>
            <div class="stat-label">📋 Role Requests (طلبات الترقية)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statContents">-</div>
            <div class="stat-label">📚 Content (الدروس والمشاركات)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statHomeworks">-</div>
            <div class="stat-label">📝 Homework (الواجبات)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statExams">-</div>
            <div class="stat-label">🔴 Exams (الاختبارات)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statEvents">-</div>
            <div class="stat-label">🎪 Events (الفعاليات)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statSchedules">-</div>
            <div class="stat-label">📅 Schedules (حصص الجدول)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statIssues">-</div>
            <div class="stat-label">❓ Issues (الاستفسارات)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statChat">-</div>
            <div class="stat-label">💬 Chat (رسائل الشعب)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statMedia">-</div>
            <div class="stat-label">🖼️ Media (ملفات الوسائط)</div>
          </div>
          <div class="stat-card">
            <div class="stat-num" id="statVotes">-</div>
            <div class="stat-label">🗳️ Votes (طلبات التصويت)</div>
          </div>
          <div class="stat-card security">
            <div class="stat-num" id="statSecurity" style="color:#3B82F6;">-</div>
            <div class="stat-label">🔒 Security (الجلسات النشطة)</div>
          </div>
          <div class="stat-card" onclick="switchTab('audit')" style="cursor:pointer;">
            <div class="stat-num" id="statAudit" style="color:var(--cyan);">-</div>
            <div class="stat-label">📜 Audit Log (سجل العمليات)</div>
          </div>
          <div class="stat-card" onclick="switchTab('system')" style="cursor:pointer;">
            <div class="stat-num" id="statSystem" style="color:var(--emerald);">OK</div>
            <div class="stat-label">⚙️ System (حالة الخادم)</div>
          </div>
        </div>
      </section>

      <!-- Users Tab -->
      <section id="tab-users" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">👥 إدارة المستخدمين والرتب والحسابات</h2>
            <div class="filter-bar">
              <input type="text" class="search-input" id="userSearch" placeholder="بحث بالاسم أو الهاتف..." oninput="debounceUserSearch()">
              <select class="role-select" id="userRoleFilter" onchange="loadUsers()">
                <option value="ALL">جميع الرتب</option>
                <option value="STUDENT">طالب (STUDENT)</option>
                <option value="MODERATOR">مشرف (MODERATOR)</option>
                <option value="TEACHER">أستاذ (TEACHER)</option>
                <option value="ADMIN">مدير (ADMIN)</option>
              </select>
              <select class="role-select" id="userStatusFilter" onchange="loadUsers()">
                <option value="ALL">جميع الحالات</option>
                <option value="ACTIVE">نشط (ACTIVE)</option>
                <option value="DISABLED">مجمّد (DISABLED)</option>
              </select>
            </div>
          </div>

          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>الاسم الكامل</th>
                  <th>رقم الهاتف</th>
                  <th>الصف / الشعبة</th>
                  <th>الرتبة</th>
                  <th>الحالة</th>
                  <th>تاريخ الانضمام</th>
                  <th>الإجراءات الإدارية المستقلة</th>
                </tr>
              </thead>
              <tbody id="usersTableBody">
                <tr><td colspan="7" style="text-align:center; color:var(--text-sec);">جاري جلب البيانات...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Roles Tab (عرض الرتب) -->
      <section id="tab-roles" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">👑 هيكل الرتب والصلاحيات المنظومية (Platform Roles)</h2>
            <button class="action-btn" onclick="loadRoles()">تحديث الرتب 🔄</button>
          </div>
          <p style="font-size:0.85rem; color:var(--text-sec); margin-bottom:1.5rem;">
            فلسفة المنظومة: المساهمة مفتوحة لجميع الطلاب والأساتذة (رفع دروس، إضافة واجبات، طرح أسئلة، اقتراح جدول)، وتحدد الرتب مستويات الاعتماد والرقابة والإدارة.
          </p>
          <div id="rolesList" style="display:grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap:1.25rem;">
            <div style="text-align:center; color:var(--text-sec);">جاري جلب مصفوفة الرتب...</div>
          </div>
        </div>
      </section>

      <!-- Teachers Tab -->
      <section id="tab-teachers" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">🎓 هيئة التدريس والأساتذة المعتمدين</h2>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>الأستاذ</th>
                  <th>رقم الهاتف</th>
                  <th>المرحلة</th>
                  <th>الحالة</th>
                  <th>الإجراءات</th>
                </tr>
              </thead>
              <tbody id="teachersTableBody">
                <tr><td colspan="5" style="text-align:center;">جاري جلب قائمة الأساتذة...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Moderators Tab -->
      <section id="tab-moderators" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">🛡️ مسؤولو الشعب والمشرفون</h2>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>المشرف</th>
                  <th>رقم الهاتف</th>
                  <th>الشعبة المكلف بها</th>
                  <th>الحالة</th>
                  <th>الإجراءات</th>
                </tr>
              </thead>
              <tbody id="moderatorsTableBody">
                <tr><td colspan="5" style="text-align:center;">جاري جلب قائمة المشرفين...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Admins Tab -->
      <section id="tab-admins" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">👑 مدراء النظام (Administrators)</h2>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>المدير</th>
                  <th>رقم الهاتف</th>
                  <th>الصلاحيات</th>
                  <th>الإجراءات</th>
                </tr>
              </thead>
              <tbody id="adminsTableBody">
                <tr><td colspan="4" style="text-align:center;">جاري جلب قائمة المدراء...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Role Requests Tab -->
      <section id="tab-requests" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">📋 طلبات ترقية الرتب (Role Requests)</h2>
            <select class="role-select" id="requestStatusFilter" onchange="loadRequests()">
              <option value="PENDING">الطلبات المعلقة فقط ⏳</option>
              <option value="ALL">جميع الطلبات</option>
              <option value="APPROVED">المعتمدة ✅</option>
              <option value="REJECTED">المرفوضة ❌</option>
            </select>
          </div>

          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>صاحب الطلب</th>
                  <th>رقم الهاتف</th>
                  <th>الرتبة المطلوبة</th>
                  <th>السبب والمبرر</th>
                  <th>الحالة</th>
                  <th>تاريخ التقديم</th>
                  <th>القرار الإداري المستقل</th>
                </tr>
              </thead>
              <tbody id="requestsTableBody">
                <tr><td colspan="7" style="text-align:center; color:var(--text-sec);">جاري جلب الطلبات...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Groups Tab -->
      <section id="tab-groups" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">🏫 الشعب والمجموعات الرسمية</h2>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>معرف المجموعة</th>
                  <th>اسم الشعبة</th>
                  <th>النوع</th>
                  <th>عدد الأعضاء</th>
                  <th>العام الدراسي</th>
                </tr>
              </thead>
              <tbody id="groupsTableBody">
                <tr><td colspan="5" style="text-align:center; color:var(--text-sec);">جاري جلب المجموعات...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Contents Tab -->
      <section id="tab-contents" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">📚 إدارة الدروس والمحتوى التعليمي (God Mode)</h2>
            <div class="filter-bar">
              <input type="text" class="search-input" id="contentSearch" placeholder="بحث بعنوان الدرس أو الكاتب..." oninput="loadContents()">
              <button class="action-btn" onclick="loadContents()">تحديث 🔄</button>
            </div>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>عنوان الدرس</th>
                  <th>الشعبة</th>
                  <th>الكاتب</th>
                  <th>المشاهدات</th>
                  <th>التثبيت</th>
                  <th>التاريخ</th>
                  <th>الإجراءات المباشرة</th>
                </tr>
              </thead>
              <tbody id="contentsTableBody">
                <tr><td colspan="7" style="text-align:center;">جاري التحميل...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Homework & Exams Tab -->
      <section id="tab-homeworks" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">📝 إدارة الواجبات المدرسية والاختبارات</h2>
            <button class="action-btn" onclick="loadHomeworks(); loadExams();">تحديث 🔄</button>
          </div>
          <h3 style="font-size:0.95rem; color:var(--cyan); margin:1rem 0 0.5rem;">الواجبات الحالية:</h3>
          <div style="overflow-x:auto; margin-bottom:1.5rem;">
            <table>
              <thead>
                <tr>
                  <th>عنوان الواجب</th>
                  <th>المادة</th>
                  <th>الشعبة</th>
                  <th>تاريخ الاستحقاق</th>
                  <th>الإجراء</th>
                </tr>
              </thead>
              <tbody id="homeworksTableBody">
                <tr><td colspan="5" style="text-align:center;">جاري التحميل...</td></tr>
              </tbody>
            </table>
          </div>
          <h3 style="font-size:0.95rem; color:var(--ruby); margin:1rem 0 0.5rem;">الاختبارات المجدولة:</h3>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>عنوان الاختبار</th>
                  <th>المادة</th>
                  <th>الشعبة</th>
                  <th>تاريخ الاختبار</th>
                  <th>الإجراء</th>
                </tr>
              </thead>
              <tbody id="examsTableBody">
                <tr><td colspan="5" style="text-align:center;">جاري التحميل...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Issues / Q&A Tab -->
      <section id="tab-issues" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">❓ بنك الأسئلة والاستفسارات (Q&A)</h2>
            <button class="action-btn" onclick="loadIssues()">تحديث 🔄</button>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>عنوان السؤال</th>
                  <th>الشعبة</th>
                  <th>السائل</th>
                  <th>الردود</th>
                  <th>الحالة</th>
                  <th>التاريخ</th>
                  <th>الإجراء</th>
                </tr>
              </thead>
              <tbody id="issuesTableBody">
                <tr><td colspan="7" style="text-align:center;">جاري التحميل...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Schedules Tab -->
      <section id="tab-schedules" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">📅 الجداول المعتمدة وتصفير الحصص</h2>
            <button class="action-btn" onclick="loadSchedules()">تحديث 🔄</button>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>الشعبة</th>
                  <th>النسخة</th>
                  <th>عدد الحصص</th>
                  <th>المنشئ</th>
                  <th>ساري من</th>
                  <th>الإجراءات</th>
                </tr>
              </thead>
              <tbody id="schedulesTableBody">
                <tr><td colspan="6" style="text-align:center;">جاري التحميل...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Decisions & Governance Tab -->
      <section id="tab-decisions" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">🗳️ القرارات الجماعية والتصويتات (Governance Engine)</h2>
            <button class="action-btn" onclick="loadDecisions()">تحديث 🔄</button>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>نوع القرار</th>
                  <th>العنوان</th>
                  <th>الشعبة</th>
                  <th>الأصوات (موافق/معارض)</th>
                  <th>الحالة</th>
                  <th>الإجراء الإداري المباشر</th>
                </tr>
              </thead>
              <tbody id="decisionsTableBody">
                <tr><td colspan="6" style="text-align:center;">جاري التحميل...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Live Chat Messages Tab -->
      <section id="tab-chat" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">💬 المحادثات المباشرة للشعب والنوادي</h2>
            <button class="action-btn" onclick="loadChatMessages()">تحديث 🔄</button>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>المرسل</th>
                  <th>الشعبة</th>
                  <th>الرسالة</th>
                  <th>التوقيت</th>
                  <th>الإجراء</th>
                </tr>
              </thead>
              <tbody id="chatTableBody">
                <tr><td colspan="5" style="text-align:center;">جاري التحميل...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- Corrections Tab -->
      <section id="tab-corrections" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">✏️ طلبات تصحيح المحتوى والدروس</h2>
            <button class="action-btn" onclick="loadCorrections()">تحديث 🔄</button>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>الدرس المستهدف</th>
                  <th>الحقل المصوب</th>
                  <th>القيمة المقترحة</th>
                  <th>المبرر</th>
                  <th>مقدم الطلب</th>
                  <th>الحالة</th>
                </tr>
              </thead>
              <tbody id="correctionsTableBody">
                <tr><td colspan="6" style="text-align:center;">جاري التحميل...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- SQL Studio Tab -->
      <section id="tab-sql" style="display:none;">
        <div class="section-card">
          <h2 style="font-size:1.1rem; font-weight:800; color:var(--cyan); margin-bottom:0.75rem;">🛠️ استوديو SQL المباشر لقاعدة البيانات (D1 Studio)</h2>
          <p style="font-size:0.85rem; color:var(--text-sec); margin-bottom:1rem;">تنفيذ استعلامات SQL حرة مباشرة على قاعدة بيانات تنوير D1 مع تسجيل رقابي كامل.</p>
          <div style="display:flex; flex-direction:column; gap:0.75rem; margin-bottom:1rem;">
            <textarea id="sqlQuery" style="width:100%; height:110px; background:rgba(3,7,18,0.8); border:1px solid var(--border-accent); border-radius:10px; padding:0.75rem; color:#A5F3FC; font-family:monospace; font-size:0.9rem;" placeholder="SELECT * FROM users LIMIT 10;"></textarea>
            <div style="display:flex; gap:0.5rem; justify-content:flex-end;">
              <button class="action-btn" onclick="document.getElementById('sqlQuery').value='SELECT name, tbl_name FROM sqlite_master WHERE type=\\'table\\';'">عرض الجداول 📋</button>
              <button class="action-btn approve" onclick="runSqlConsole()">تنفيذ الاستعلام ⚡</button>
            </div>
          </div>
          <div id="sqlResult" style="background:rgba(0,0,0,0.4); border:1px solid var(--border); border-radius:10px; padding:1rem; font-family:monospace; font-size:0.85rem; max-height:400px; overflow:auto;">
            النتائج ستظهر هنا...
          </div>
        </div>
      </section>

      <!-- Audit Log Tab -->
      <section id="tab-audit" style="display:none;">
        <div class="section-card">
          <div class="section-header">
            <h2 style="font-size:1.1rem; font-weight:800;">📜 سجل العمليات الإدارية والرقابة (Audit Log)</h2>
            <button class="action-btn" onclick="loadAudit()">تحديث السجل 🔄</button>
          </div>
          <div style="overflow-x:auto;">
            <table>
              <thead>
                <tr>
                  <th>نوع العملية</th>
                  <th>المنفذ</th>
                  <th>المستهدف</th>
                  <th>التفاصيل والمبرر</th>
                  <th>عنوان IP</th>
                  <th>التوقيت</th>
                </tr>
              </thead>
              <tbody id="auditTableBody">
                <tr><td colspan="6" style="text-align:center; color:var(--text-sec);">جاري جلب السجل...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- System & Security Tab -->
      <section id="tab-system" style="display:none;">
        <div class="section-card">
          <h2 style="font-size:1.1rem; font-weight:800; margin-bottom:1rem;">⚙️ الأمان والبنية التحتية للخادم</h2>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:1.25rem;">
            <div style="background:rgba(255,255,255,0.02); padding:1.25rem; border-radius:12px; border:1px solid var(--border);">
              <h3 style="font-size:0.95rem; color:var(--cyan); margin-bottom:0.75rem;">بيانات الخادم (Cloudflare Workers)</h3>
              <p style="font-size:0.85rem; color:var(--text-sec); margin-bottom:0.4rem;">المسار المعتمد: <code>https://tanweer.magd.workers.dev/dashboard</code></p>
              <p style="font-size:0.85rem; color:var(--text-sec); margin-bottom:0.4rem;">حالة البيئة: <strong id="sysEnv" style="color:var(--emerald);">-</strong></p>
              <p style="font-size:0.85rem; color:var(--text-sec); margin-bottom:0.4rem;">الموقع الجغرافي: <strong>Cloudflare Edge Network</strong></p>
              <p style="font-size:0.85rem; color:var(--text-sec);">قاعدة البيانات: <strong>Cloudflare D1 (مفعلة ومتصلة)</strong></p>
            </div>
            <div style="background:rgba(255,255,255,0.02); padding:1.25rem; border-radius:12px; border:1px solid var(--border);">
              <h3 style="font-size:0.95rem; color:var(--emerald); margin-bottom:0.75rem;">حماية الجلسة والمفاتيح السرية</h3>
              <p style="font-size:0.85rem; color:var(--text-sec); margin-bottom:0.4rem;">المفتاح السري: <code>TANWEER_OWNER_SECRET</code> (محمي في Cloudflare Secrets)</p>
              <p style="font-size:0.85rem; color:var(--text-sec); margin-bottom:0.4rem;">نوع الجلسة: <strong>HttpOnly Lax Secure Cookie</strong></p>
              <p style="font-size:0.85rem; color:var(--text-sec);">استقلالية الجلسة: <strong>مستقلة تماماً عن جلسات تطبيق الأندرويد</strong></p>
            </div>
          </div>
        </div>
      </section>
    </div>
  </main>

  <!-- Role Change Modal -->
  <div class="modal-overlay" id="roleModal">
    <div class="modal-card">
      <div class="modal-title">تعديل رتبة المستخدم 👑</div>
      <div class="modal-sub" id="roleModalUser">-</div>

      <div class="form-group">
        <label>الرتبة الجديدة المعتمدة</label>
        <select class="form-control" id="modalNewRole">
          <option value="STUDENT">👤 طالب (STUDENT)</option>
          <option value="MODERATOR">🛡️ مسؤول / مشرف (MODERATOR)</option>
          <option value="TEACHER">🎓 أستاذ معتمد (TEACHER)</option>
          <option value="ADMIN">👑 مدير النظام (ADMIN)</option>
        </select>
      </div>

      <div class="form-group">
        <label>مبرر التعديل (يسجل في سجل الرقابة)</label>
        <input type="text" class="form-control" id="modalRoleReason" placeholder="مثلاً: تكليف بإشراف الشعبة أو معلم المادة">
      </div>

      <div class="modal-actions">
        <button class="action-btn" onclick="closeRoleModal()">إلغاء</button>
        <button class="action-btn approve" onclick="submitRoleChange()">حفظ واعتماد الرتبة</button>
      </div>
    </div>
  </div>

  <!-- Verification Code Modal -->
  <div class="modal-overlay" id="codeModal">
    <div class="modal-card" style="text-align:center;">
      <div style="font-size:2.5rem; margin-bottom:0.5rem;">🎉</div>
      <div class="modal-title" style="font-size:1.3rem;">تم اعتماد الطلب وتوليد رمز التفعيل ✨</div>
      <div class="modal-sub" id="codeModalSub">صالح لمدة 24 ساعة لمرة واحدة فقط</div>

      <div style="background:rgba(0, 229, 255, 0.08); border:2px dashed var(--cyan); border-radius:16px; padding:1.5rem; margin:1.5rem 0;">
        <div style="font-size:0.8rem; color:var(--text-sec); margin-bottom:0.5rem;">رمز التحقق السري (One-Time Activation Code)</div>
        <div style="font-size:2.4rem; font-weight:900; letter-spacing:4px; color:var(--cyan); font-family:monospace;" id="generatedCodeDisplay">----</div>
        <div style="font-size:0.75rem; color:var(--emerald); margin-top:0.5rem;">🔒 مشفر، محمي بعدد 5 محاولات، مرتبط بحساب المتقدم حصراً</div>
      </div>

      <div style="display:flex; gap:0.75rem; justify-content:center; flex-wrap:wrap;">
        <button class="action-btn" onclick="copyGeneratedCode()" style="padding:0.75rem 1.25rem; font-size:0.9rem;">📋 نسخ الرمز</button>
        <a id="whatsappSendBtn" target="_blank" class="action-btn approve" style="padding:0.75rem 1.25rem; font-size:0.9rem; text-decoration:none; display:inline-flex; align-items:center; gap:0.4rem;">💬 إرسال للمتقدم عبر WhatsApp</a>
        <button class="action-btn" onclick="closeCodeModal()" style="padding:0.75rem 1.25rem; font-size:0.9rem;">إغلاق</button>
      </div>
    </div>
  </div>

  <div class="toast" id="toast"></div>

  <script>
    let activeUserId = '';
    let searchTimeout = null;

    function showToast(msg) {
      const t = document.getElementById('toast');
      t.textContent = msg;
      t.style.display = 'block';
      setTimeout(() => { t.style.display = 'none'; }, 3000);
    }

    function switchTab(tabId) {
      document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
      const activeNav = Array.from(document.querySelectorAll('.nav-item')).find(el => el.getAttribute('onclick')?.includes(tabId));
      if (activeNav) activeNav.classList.add('active');

      const tabs = ['overview', 'users', 'roles', 'teachers', 'moderators', 'admins', 'groups', 'requests', 'academic', 'audit', 'system'];
      tabs.forEach(t => {
        const el = document.getElementById('tab-' + t);
        if (el) el.style.display = t === tabId ? 'block' : 'none';
      });

      const titles = {
        overview: 'نظرة عامة على المنظومة',
        users: 'إدارة المستخدمين والحسابات والرتب',
        roles: 'هيكل الرتب والصلاحيات المنظومية (Roles)',
        teachers: 'هيئة التدريس والمعلمون المعتمدون',
        moderators: 'مسؤولو الشعب والمشرفون',
        admins: 'مدراء النظام',
        groups: 'الشعب والمجموعات الرسمية',
        requests: 'طلبات ترقية الرتب (Role Requests)',
        academic: 'النشاط الأكاديمي والمحتوى',
        audit: 'سجل الرقابة والعمليات الإدارية',
        system: 'إعدادات النظام والأمان'
      };
      document.getElementById('pageTitle').textContent = titles[tabId] || 'غرفة التحكم';

      if (tabId === 'overview') loadStats();
      if (tabId === 'users') loadUsers();
      if (tabId === 'roles') loadRoles();
      if (tabId === 'teachers') loadTeachers();
      if (tabId === 'moderators') loadModerators();
      if (tabId === 'admins') loadAdmins();
      if (tabId === 'requests') loadRequests();
      if (tabId === 'groups') loadGroups();
      if (tabId === 'audit') loadAudit();
    }

    async function loadStats() {
      try {
        const res = await fetch('/api/dashboard/stats');
        const data = await res.json();
        if (!data.success) return;
        const s = data.stats;
        document.getElementById('statUsers').textContent = s.users.total;
        document.getElementById('statTeachers').textContent = s.teachers;
        document.getElementById('statModerators').textContent = s.moderators;
        document.getElementById('statAdmins').textContent = s.administrators;
        document.getElementById('statGroups').textContent = s.groups;
        document.getElementById('statPendingRequests').textContent = s.roleRequests.pending;

        document.getElementById('statContents').textContent = s.content;
        document.getElementById('statHomeworks').textContent = s.homework;
        document.getElementById('statExams').textContent = s.exams;
        document.getElementById('statEvents').textContent = s.events;
        document.getElementById('statSchedules').textContent = s.schedules.slots;
        document.getElementById('statIssues').textContent = s.issues;
        document.getElementById('statChat').textContent = s.chat;
        document.getElementById('statMedia').textContent = s.media;
        document.getElementById('statVotes').textContent = s.votes;
        document.getElementById('statSecurity').textContent = s.security.activeSessions;
        document.getElementById('statAudit').textContent = s.auditLog;

        document.getElementById('acadLessons').textContent = s.content;
        document.getElementById('acadHomeworks').textContent = s.homework;
        document.getElementById('acadExams').textContent = s.exams;
        document.getElementById('acadEvents').textContent = s.events;

        document.getElementById('sysEnv').textContent = s.system.environment;

        if (s.roleRequests.pending > 0) {
          const b = document.getElementById('requestsBadge');
          b.textContent = s.roleRequests.pending;
          b.style.display = 'inline-block';
        }
      } catch (e) {
        console.error(e);
      }
    }

    function debounceUserSearch() {
      clearTimeout(searchTimeout);
      searchTimeout = setTimeout(loadUsers, 300);
    }

    async function loadUsers() {
      const q = document.getElementById('userSearch')?.value || '';
      const role = document.getElementById('userRoleFilter')?.value || 'ALL';
      const status = document.getElementById('userStatusFilter')?.value || 'ALL';
      const tbody = document.getElementById('usersTableBody');
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;">جاري البحث والتحميل...</td></tr>';

      try {
        const res = await fetch('/api/dashboard/users?q=' + encodeURIComponent(q) + '&role=' + encodeURIComponent(role) + '&status=' + encodeURIComponent(status));
        const data = await res.json();
        if (!data.success || !data.users.length) {
          tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color:var(--text-sec);">لا توجد حسابات مطابقة</td></tr>';
          return;
        }

        tbody.innerHTML = data.users.map(u => {
          const roleClass = u.role.toLowerCase();
          const roleName = {
            STUDENT: '👤 طالب',
            MODERATOR: '🛡️ مشرف',
            TEACHER: '🎓 أستاذ',
            ADMIN: '👑 مدير',
            SYSTEM_OWNER: '🔐 مالك'
          }[u.role] || u.role;

          const isFrozen = u.status === 'DISABLED';
          const statusBadge = isFrozen 
            ? '<span class="badge disabled">❄️ مجمّد</span>'
            : '<span class="badge active">🟢 نشط</span>';

          const freezeBtn = isFrozen
            ? \`<button class="action-btn unfreeze" onclick="enableUser('\${u.id}', '\${u.fullName}')">تنشيط 🟢</button>\`
            : \`<button class="action-btn freeze" onclick="disableUser('\${u.id}', '\${u.fullName}')">تجميد ❄️</button>\`;

          return \`<tr>
            <td><strong>\${u.fullName}</strong></td>
            <td><code style="color:var(--cyan);">\${u.phoneNumber}</code></td>
            <td>الصف \${u.gradeId} — شعبة (\${u.sectionId})</td>
            <td><span class="badge \${roleClass}">\${roleName}</span></td>
            <td>\${statusBadge}</td>
            <td>\${new Date(u.createdAt).toLocaleDateString('ar-EG')}</td>
            <td>
              <div style="display:flex; gap:0.4rem; flex-wrap:wrap;">
                <button class="action-btn" onclick="openRoleModal('\${u.id}', '\${u.fullName}', '\${u.role}')">تعديل الرتبة 👑</button>
                \${freezeBtn}
                <button class="action-btn reject" onclick="revokeSessions('\${u.id}', '\${u.fullName}')">إنهاء الجلسات 🚪</button>
              </div>
            </td>
          </tr>\`;
        }).join('');
      } catch (e) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color:var(--ruby);">فشل جلب المستخدمين</td></tr>';
      }
    }

    async function loadRoles() {
      const container = document.getElementById('rolesList');
      container.innerHTML = '<div style="text-align:center; color:var(--text-sec); grid-column:1/-1;">جاري جلب بيانات الرتب...</div>';
      try {
        const res = await fetch('/api/dashboard/roles');
        const data = await res.json();
        if (!data.success || !data.roles.length) {
          container.innerHTML = '<div style="text-align:center; color:var(--ruby); grid-column:1/-1;">فشل تحميل الرتب</div>';
          return;
        }
        container.innerHTML = data.roles.map(r => \`
          <div style="background:var(--card-bg); border:1px solid var(--border); border-radius:14px; padding:1.25rem; display:flex; flex-direction:column; justify-content:space-between;">
            <div>
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.75rem;">
                <span style="font-size:1.8rem;">\${r.badge}</span>
                <span class="badge \${r.role.toLowerCase()}">المستوى \${r.level}</span>
              </div>
              <h3 style="font-size:1.1rem; font-weight:800; color:var(--cyan); margin-bottom:0.4rem;">\${r.nameAr} (\${r.role})</h3>
              <p style="font-size:0.85rem; color:var(--text-sec); line-height:1.5; margin-bottom:1rem;">\${r.descriptionAr}</p>
            </div>
            <div style="border-top:1px solid var(--border); padding-top:0.75rem; display:flex; justify-content:space-between; align-items:center;">
              <span style="font-size:0.85rem; color:var(--text-muted);">الأعضاء الفعليون: <strong style="color:var(--text-pri); font-size:1rem;">\${r.userCount}</strong></span>
              <button class="action-btn" onclick="filterByRole('\${r.role}')">عرض المستخدمين 👥</button>
            </div>
          </div>
        \`).join('');
      } catch (e) {
        container.innerHTML = '<div style="text-align:center; color:var(--ruby); grid-column:1/-1;">حدث خطأ في الاتصال بالخادم</div>';
      }
    }

    function filterByRole(role) {
      switchTab('users');
      const select = document.getElementById('userRoleFilter');
      if (select) {
        select.value = role;
        loadUsers();
      }
    }

    async function loadTeachers() {
      const tbody = document.getElementById('teachersTableBody');
      try {
        const res = await fetch('/api/dashboard/users?role=TEACHER');
        const data = await res.json();
        if (!data.success || !data.users.length) {
          tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--text-sec);">لا يوجد أساتذة مسجلين بعد</td></tr>';
          return;
        }
        tbody.innerHTML = data.users.map(u => \`<tr>
          <td><strong>\${u.fullName}</strong></td>
          <td><code>\${u.phoneNumber}</code></td>
          <td>الصف \${u.gradeId} — شعبة \${u.sectionId}</td>
          <td><span class="badge teacher">🎓 أستاذ معتمد</span></td>
          <td>
            <button class="action-btn" onclick="openRoleModal('\${u.id}', '\${u.fullName}', '\${u.role}')">تعديل الرتبة</button>
          </td>
        </tr>\`).join('');
      } catch (e) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--ruby);">فشل التحميل</td></tr>';
      }
    }

    async function loadModerators() {
      const tbody = document.getElementById('moderatorsTableBody');
      try {
        const res = await fetch('/api/dashboard/users?role=MODERATOR');
        const data = await res.json();
        if (!data.success || !data.users.length) {
          tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--text-sec);">لا يوجد مشرفون مسجلون</td></tr>';
          return;
        }
        tbody.innerHTML = data.users.map(u => \`<tr>
          <td><strong>\${u.fullName}</strong></td>
          <td><code>\${u.phoneNumber}</code></td>
          <td>شعبة \${u.sectionId} (الصف \${u.gradeId})</td>
          <td><span class="badge moderator">🛡️ مسؤول شعبة</span></td>
          <td>
            <button class="action-btn" onclick="openRoleModal('\${u.id}', '\${u.fullName}', '\${u.role}')">تعديل الرتبة</button>
          </td>
        </tr>\`).join('');
      } catch (e) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--ruby);">فشل التحميل</td></tr>';
      }
    }

    async function loadAdmins() {
      const tbody = document.getElementById('adminsTableBody');
      try {
        const res = await fetch('/api/dashboard/users?role=ADMIN');
        const data = await res.json();
        if (!data.success || !data.users.length) {
          tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:var(--text-sec);">لا يوجد مدراء إضافيون</td></tr>';
          return;
        }
        tbody.innerHTML = data.users.map(u => \`<tr>
          <td><strong>\${u.fullName}</strong></td>
          <td><code>\${u.phoneNumber}</code></td>
          <td><span class="badge admin">👑 كامل الصلاحيات الإدارية</span></td>
          <td>
            <button class="action-btn" onclick="openRoleModal('\${u.id}', '\${u.fullName}', '\${u.role}')">تعديل الرتبة</button>
          </td>
        </tr>\`).join('');
      } catch (e) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:var(--ruby);">فشل التحميل</td></tr>';
      }
    }

    function openRoleModal(userId, name, currentRole) {
      activeUserId = userId;
      document.getElementById('roleModalUser').textContent = name + ' (الرتبة الحالية: ' + currentRole + ')';
      document.getElementById('modalNewRole').value = currentRole;
      document.getElementById('roleModal').style.display = 'flex';
    }

    function closeRoleModal() {
      document.getElementById('roleModal').style.display = 'none';
    }

    async function submitRoleChange() {
      const newRole = document.getElementById('modalNewRole').value;
      const reason = document.getElementById('modalRoleReason').value;

      try {
        const res = await fetch('/api/dashboard/users/' + activeUserId + '/role', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ newRole, reason })
        });
        const data = await res.json();
        if (data.success) {
          closeRoleModal();
          showToast(data.message);
          loadUsers();
          loadStats();
        } else {
          alert(data.message || 'فشلت العملية');
        }
      } catch (e) {
        alert('حدث خطأ');
      }
    }

    async function disableUser(userId, name) {
      if (!confirm('هل أنت متأكد من تجميد حساب ' + name + ' ومنعه من الدخول للمنظومة؟')) return;
      try {
        const res = await fetch('/api/dashboard/users/' + userId + '/disable', { method: 'POST' });
        const data = await res.json();
        showToast(data.message || 'تم تجميد الحساب بنجاح');
        loadUsers();
        loadStats();
      } catch (e) {
        alert('حدث خطأ أثناء تجميد الحساب');
      }
    }

    async function enableUser(userId, name) {
      try {
        const res = await fetch('/api/dashboard/users/' + userId + '/enable', { method: 'POST' });
        const data = await res.json();
        showToast(data.message || 'تم تنشيط الحساب بنجاح');
        loadUsers();
        loadStats();
      } catch (e) {
        alert('حدث خطأ أثناء تنشيط الحساب');
      }
    }

    async function revokeSessions(userId, name) {
      if (!confirm('هل أنت متأكد من رغبتك في إنهاء جميع جلسات ' + name + ' وتسجيل خروجه فورياً؟')) return;
      try {
        const res = await fetch('/api/dashboard/users/' + userId + '/revoke-sessions', { method: 'POST' });
        const data = await res.json();
        showToast(data.message || 'تم إنهاء الجلسات');
        loadStats();
      } catch (e) {
        alert('حدث خطأ');
      }
    }

    async function loadRequests() {
      const status = document.getElementById('requestStatusFilter').value;
      const tbody = document.getElementById('requestsTableBody');
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;">جاري جلب الطلبات...</td></tr>';

      try {
        const res = await fetch('/api/dashboard/role-requests?status=' + status);
        const data = await res.json();
        if (!data.success || !data.requests.length) {
          tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color:var(--text-sec);">لا توجد طلبات في هذه الحالة</td></tr>';
          return;
        }

        tbody.innerHTML = data.requests.map(r => {
          const statusBadge = {
            PENDING: '<span class="badge pending">قيد المراجعة ⏳</span>',
            APPROVED: '<span class="badge approved">معتمد ✅</span>',
            REJECTED: '<span class="badge rejected">مرفوض ❌</span>'
          }[r.status] || r.status;

          const actionBtns = r.status === 'PENDING' ? \`
            <div style="display:flex; gap:0.4rem;">
              <button class="action-btn approve" onclick="approveRequest('\${r.id}')">اعتماد ✅</button>
              <button class="action-btn reject" onclick="rejectRequest('\${r.id}')">رفض ❌</button>
            </div>
          \` : '<span style="color:var(--text-muted); font-size:0.75rem;">مكتمل</span>';

          return \`<tr>
            <td><strong>\${r.full_name}</strong></td>
            <td><code>\${r.phone_number}</code></td>
            <td><span class="badge teacher">\${r.requested_role}</span></td>
            <td>\${r.reason || 'لا يوجد'}</td>
            <td>\${statusBadge}</td>
            <td>\${new Date(r.created_at).toLocaleDateString('ar-EG')}</td>
            <td>\${actionBtns}</td>
          </tr>\`;
        }).join('');
      } catch (e) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color:var(--ruby);">فشل تحميل الطلبات</td></tr>';
      }
    }

    let currentGeneratedCode = '';
    async function approveRequest(id) {
      if (!confirm('هل توافق على اعتماد طلب الترقية وتوليد رمز التحقق (صالح 24 ساعة)؟')) return;
      try {
        const res = await fetch('/api/dashboard/role-requests/' + id + '/approve', { method: 'POST' });
        const data = await res.json();
        if (data.success) {
          currentGeneratedCode = data.code;
          document.getElementById('generatedCodeDisplay').textContent = data.code;
          document.getElementById('codeModalSub').textContent = \`تم اعتماد ترقية \${data.applicantName} إلى \${data.targetRole}\`;
          
          let cleanPhone = (data.applicantPhone || '').replace(/[^0-9]/g, '');
          if (cleanPhone.startsWith('0')) cleanPhone = '967' + cleanPhone.substring(1);
          if (!cleanPhone.startsWith('967') && cleanPhone.length === 9) cleanPhone = '967' + cleanPhone;
          
          const msg = encodeURIComponent(\`مرحباً أستاذ/ة \${data.applicantName}، تم اعتماد ترقيتك إلى رتبة (\${data.targetRole}) في منصة تنوير التعليمية.\n\nرمز التفعيل السري الخاص بك:\n*\${data.code}*\n\nالرمز صالح لمدة 24 ساعة ويستخدم لمرة واحدة فقط داخل التطبيق.\`);
          document.getElementById('whatsappSendBtn').href = \`https://wa.me/\${cleanPhone}?text=\${msg}\`;
          
          document.getElementById('codeModal').style.display = 'flex';
          loadRequests();
          loadStats();
        } else {
          alert(data.message || 'فشلت العملية');
        }
      } catch (e) {
        alert('حدث خطأ');
      }
    }

    function copyGeneratedCode() {
      navigator.clipboard.writeText(currentGeneratedCode);
      showToast('تم نسخ رمز التفعيل للحافظة بنجاح 📋');
    }

    function closeCodeModal() {
      document.getElementById('codeModal').style.display = 'none';
    }

    async function rejectRequest(id) {
      const reason = prompt('سبب الرفض (اختياري):', 'لم يستوفِ الشروط');
      if (reason === null) return;
      try {
        const res = await fetch('/api/dashboard/role-requests/' + id + '/reject', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ reason })
        });
        const data = await res.json();
        showToast(data.message || 'تم رفض الطلب');
        loadRequests();
        loadStats();
      } catch (e) {
        alert('حدث خطأ');
      }
    }

    async function loadGroups() {
      const tbody = document.getElementById('groupsTableBody');
      try {
        const res = await fetch('/api/dashboard/groups');
        const data = await res.json();
        tbody.innerHTML = data.groups.map(g => \`<tr>
          <td><code>\${g.id}</code></td>
          <td><strong>\${g.name}</strong></td>
          <td><span class="badge admin">\${g.type}</span></td>
          <td>\${g.member_count} عضو</td>
          <td>\${g.academic_year_id}</td>
        </tr>\`).join('');
      } catch (e) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--ruby);">فشل تحميل المجموعات</td></tr>';
      }
    }

    async function loadAudit() {
      const tbody = document.getElementById('auditTableBody');
      try {
        const res = await fetch('/api/dashboard/audit-logs');
        const data = await res.json();
        if (!data.logs.length) {
          tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;">لا توجد سجلات بعد</td></tr>';
          return;
        }
        tbody.innerHTML = data.logs.map(l => \`<tr>
          <td><strong style="color:var(--cyan);">\${l.action}</strong></td>
          <td>\${l.actor_id} (\${l.actor_role})</td>
          <td><code>\${l.target_user_id || '-'}</code></td>
          <td>\${l.details}</td>
          <td><code>\${l.ip_address || '-'}</code></td>
          <td>\${new Date(l.created_at).toLocaleString('ar-EG')}</td>
        </tr>\`).join('');
      } catch (e) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--ruby);">فشل تحميل سجل الرقابة</td></tr>';
      }
    }

    async function logout() {
      await fetch('/api/dashboard/logout', { method: 'POST' });
      window.location.href = '/dashboard/login';
    }

    // Auto-load on page start
    loadStats();
  </script>
</body>
</html>`;

  return new Response(html, {
    status: 200,
    headers: { 'Content-Type': 'text/html; charset=utf-8' },
  });
}
