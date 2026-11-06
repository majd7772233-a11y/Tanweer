import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapGroup } from '../lib/mappers';
import { errorResponse, jsonResponse } from '../lib/response';
import { Role, normalizeRole, sanitizePublicRole } from '../lib/roles';
import { Permission, hasPermission } from '../lib/permissions';
import { requireGroupMember } from '../middleware/permissions';

export async function handleGetGroups(user: UserContext, env: Env): Promise<Response> {
  const userGroups = await env.DB.prepare(
    `SELECT g.id, g.name, g.type, g.description, g.icon, gm.role, gm.status,
            g.visibility, g.chat_policy,
            (SELECT COUNT(*) FROM group_members WHERE group_id = g.id AND status = 'ACTIVE') as member_count
     FROM groups g
     JOIN group_members gm ON g.id = gm.group_id
     WHERE gm.user_id = ? AND gm.status = 'ACTIVE'`
  ).bind(user.userId).all();

  // A-11a: Discover both OPTIONAL groups and other CLASS sections/grades the user is not actively in
  const discoverGroups = await env.DB.prepare(
    `SELECT g.id, g.name, g.type, g.description, g.icon, g.visibility, g.chat_policy,
            gm.status AS membership_status,
            (SELECT COUNT(*) FROM group_members WHERE group_id = g.id AND status = 'ACTIVE') as member_count
     FROM groups g
     LEFT JOIN group_members gm ON gm.group_id = g.id AND gm.user_id = ?
     WHERE g.is_discoverable = 1
       AND COALESCE(gm.status, '') NOT IN ('ACTIVE', 'BANNED')
     ORDER BY CASE WHEN g.type = 'OPTIONAL' THEN 0 WHEN g.type = 'SHARED' THEN 1 ELSE 2 END,
              g.name ASC`
  ).bind(user.userId).all();

  return jsonResponse({
    success: true,
    myGroups: (userGroups.results || []).map(g => mapGroup(g, (g as any).role || Role.STUDENT)),
    discoverGroups: (discoverGroups.results || []).map(g => mapGroup(g, 'DISCOVER')),
  });
}

export async function handleJoinGroupRequest(groupId: string, user: UserContext, request: Request, env: Env): Promise<Response> {
  const group = await env.DB.prepare(
    `SELECT id, name, type, visibility FROM groups WHERE id = ? AND is_discoverable = 1`
  ).bind(groupId).first<{ id: string; name: string; type: string; visibility: string }>();
  if (!group) {
    return errorResponse('GROUP_NOT_FOUND', 'المجموعة غير موجودة');
  }

  const existing = await env.DB.prepare(
    `SELECT status FROM group_members WHERE group_id = ? AND user_id = ?`
  ).bind(groupId, user.userId).first<{ status: string }>();

  let requestBody: { reason?: unknown };
  try {
    requestBody = await request.json() as { reason?: unknown };
  } catch {
    return errorResponse('INVALID_INPUT', 'بيانات طلب الانضمام غير صالحة');
  }

  if (
    requestBody.reason !== undefined &&
    requestBody.reason !== null &&
    typeof requestBody.reason !== 'string'
  ) {
    return errorResponse('INVALID_JOIN_REASON', 'سبب الانضمام يجب أن يكون نصاً');
  }

  const requestReason = typeof requestBody.reason === 'string'
    ? requestBody.reason.trim() || null
    : null;

  if (requestReason && requestReason.length > 500) {
    return errorResponse('INVALID_JOIN_REASON', 'سبب الانضمام طويل جداً؛ الحد الأقصى 500 حرف');
  }

  const initialStatus = group.visibility === 'PUBLIC' && group.type !== 'CLASS' ? 'ACTIVE' : 'PENDING';
  if (initialStatus === 'PENDING' && !requestReason) {
    return errorResponse(
      'JOIN_REASON_REQUIRED',
      'يجب كتابة سبب طلب الانضمام حتى يتمكن مشرفو المجموعة من مراجعة طلبك.'
    );
  }
  const existingStatusResponse = (status: string): Response => {
    if (status === 'ACTIVE') {
      return errorResponse('ALREADY_MEMBER', 'أنت عضو مسجل بالفعل في هذه المجموعة');
    }
    if (status === 'BANNED') {
      return errorResponse('BANNED', 'تم حظر حسابك من هذه المجموعة من قِبل المشرفين', 403);
    }
    return errorResponse('ALREADY_REQUESTED', 'طلب الانضمام قيد المراجعة');
  };

  if (existing && existing.status !== 'REJECTED') {
    return existingStatusResponse(existing.status);
  }

  if (existing?.status === 'REJECTED') {
    const retry = await env.DB.prepare(
      `UPDATE group_members
       SET status = ?, role = ?, joined_at = ?, request_reason = ?
       WHERE group_id = ? AND user_id = ? AND status = 'REJECTED'`
    ).bind(initialStatus, Role.STUDENT, Date.now(), requestReason, groupId, user.userId).run();
    if (retry.meta?.changes !== 1) {
      const latest = await env.DB.prepare(
        `SELECT status FROM group_members WHERE group_id = ? AND user_id = ?`
      ).bind(groupId, user.userId).first<{ status: string }>();
      return latest ? existingStatusResponse(latest.status) :
        errorResponse('JOIN_REQUEST_CONFLICT', 'تعذر إعادة تقديم طلب الانضمام بسبب تغيير متزامن. حدّث القائمة وحاول مجدداً.', 409);
    }
  } else {
    const insertResult = await env.DB.prepare(
      `INSERT OR IGNORE INTO group_members (group_id, user_id, role, status, joined_at, request_reason)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(groupId, user.userId, Role.STUDENT, initialStatus, Date.now(), requestReason).run();

    if (insertResult.meta?.changes !== 1) {
      // Another request may have created or changed the membership after our first read.
      const latest = await env.DB.prepare(
        `SELECT status FROM group_members WHERE group_id = ? AND user_id = ?`
      ).bind(groupId, user.userId).first<{ status: string }>();

      if (latest) {
        return existingStatusResponse(latest.status);
      }
      return errorResponse('JOIN_REQUEST_CONFLICT', 'تعذر إنشاء طلب الانضمام بسبب تغيير متزامن. حدّث القائمة وحاول مجدداً.', 409);
    }
  }

  if (initialStatus === 'ACTIVE') {
    await postGroupSystemEvent(groupId, `انضم ${user.fullName} إلى المجموعة 🌟`, env);
  } else {
    await notifyGroupManagers(
      groupId,
      'GROUP_JOIN_REQUEST',
      'طلب انضمام جديد',
      `يرغب ${user.fullName} في الانضمام إلى مجموعة "${group.name}". افتح إدارة الأعضاء لمراجعة الطلب.`,
      user.userId,
      env
    );
  }

  return jsonResponse({
    success: true,
    status: initialStatus,
    message: initialStatus === 'ACTIVE' ? 'تم الانضمام إلى المجموعة بنجاح 🎉' : 'تم إرسال طلب الانضمام إلى مشرفي المجموعة',
  });
}

/**
 * Lists all members of a group with their roles and statuses.
 * Requires active group membership or global administrative authority.
 * Scopes phone number visibility and pending status visibility strictly to group managers.
 */
export async function handleGetGroupMembers(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const normRole = normalizeRole(user.role);
  const isGlobalAdmin = normRole === Role.ADMIN || normRole === Role.SYSTEM_OWNER;
  const canManage = isGlobalAdmin || await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId });

  // Authorized school/group managers may review requests without first joining the group.
  if (!canManage) {
    const memberCheck = await requireGroupMember(user, groupId, env.DB);
    if (memberCheck) return memberCheck;
  }

  // Managers see active members, pending requests and banned users.
  // Rejected requests remain visible to the applicant in discovery, not as fake members here.
  const statusFilter = canManage
    ? "AND gm.status IN ('ACTIVE', 'PENDING', 'BANNED')"
    : "AND gm.status = 'ACTIVE'";

  let members: any;
  try {
    members = await env.DB.prepare(
      `SELECT gm.user_id, gm.role as member_role, gm.status, gm.joined_at, gm.request_reason,
              u.full_name, u.phone_number, u.role as global_role, u.grade_id, u.section_id
       FROM group_members gm
       JOIN users u ON gm.user_id = u.id
       WHERE gm.group_id = ? ${statusFilter}
       ORDER BY CASE WHEN gm.role = 'ADMIN' THEN 1 WHEN gm.role = 'TEACHER' THEN 2 WHEN gm.role = 'MODERATOR' THEN 3 ELSE 4 END, gm.joined_at ASC`
    ).bind(groupId).all();
  } catch {
    members = await env.DB.prepare(
      `SELECT gm.user_id, gm.role as member_role, gm.status, gm.joined_at,
              u.full_name, u.phone_number, u.role as global_role, u.grade_id, u.section_id
       FROM group_members gm
       JOIN users u ON gm.user_id = u.id
       WHERE gm.group_id = ? ${statusFilter}
       ORDER BY CASE WHEN gm.role = 'ADMIN' THEN 1 WHEN gm.role = 'TEACHER' THEN 2 WHEN gm.role = 'MODERATOR' THEN 3 ELSE 4 END, gm.joined_at ASC`
    ).bind(groupId).all();
  }

  return jsonResponse({
    success: true,
    canManageMembers: canManage,
    members: (members.results || []).map((m: any) => ({
      userId: m.user_id,
      fullName: m.full_name,
      phoneNumber: canManage ? m.phone_number : undefined,
      memberRole: sanitizePublicRole(m.member_role),
      globalRole: sanitizePublicRole(m.global_role),
      status: m.status,
      joinedAt: m.joined_at,
      gradeId: m.grade_id,
      sectionId: m.section_id,
      requestReason: m.request_reason || undefined,
    })),
  });
}

/**
 * Updates a member's role within the group.
 */
export async function handleUpdateGroupMemberRole(
  groupId: string,
  targetUserId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const normRole = normalizeRole(user.role);
  const isGlobalAdmin = normRole === Role.ADMIN || normRole === Role.SYSTEM_OWNER;
  const canManage = isGlobalAdmin || await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId });

  if (!canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية تعديل رتب أعضاء المجموعة', 403);
  }

  const body = await request.json() as { newRole?: string };
  if (typeof body.newRole !== 'string' || !body.newRole.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد الرتبة الجديدة');
  }

  const requestedRole = body.newRole.trim().toUpperCase();
  const allowedGroupRoles = new Set(['STUDENT', 'MEMBER', 'MODERATOR', 'TEACHER', 'VERIFIED_TEACHER']);
  if (!allowedGroupRoles.has(requestedRole)) {
    return errorResponse('INVALID_ROLE', 'الرتبة المحددة غير معروفة. الرتب المتاحة داخل المجموعة هي طالب أو مشرف أو أستاذ معتمد.');
  }

  const norm = normalizeRole(requestedRole);
  if (norm === Role.SYSTEM_OWNER || norm === Role.ADMIN) {
    return errorResponse('FORBIDDEN', 'رتبتا مالك المنظومة والمدير العام (ADMIN) تُداران حصراً عبر الإدارة العليا ولا يمكن تعيينهما كأدوار فرعية داخل المجموعات', 403);
  }

  const targetMember = await env.DB.prepare(
    `SELECT gm.role AS member_role, gm.status, u.role AS global_role
     FROM group_members gm
     JOIN users u ON u.id = gm.user_id
     WHERE gm.group_id = ? AND gm.user_id = ?`
  ).bind(groupId, targetUserId).first<{
    member_role: string;
    status: string;
    global_role: string;
  }>();

  if (!targetMember) {
    return errorResponse('MEMBER_NOT_FOUND', 'العضو غير موجود في هذه المجموعة', 404);
  }
  if (targetMember.status !== 'ACTIVE') {
    return errorResponse('MEMBER_NOT_ACTIVE', 'لا يمكن تغيير رتبة عضو غير نشط أو طلب انضمام معلّق', 409);
  }

  const targetGlobalRole = normalizeRole(targetMember.global_role);
  const actorRole = normalizeRole(user.role);

  if (norm === Role.TEACHER && targetGlobalRole !== Role.TEACHER && actorRole !== Role.SYSTEM_OWNER) {
    return errorResponse(
      'FORBIDDEN_TEACHER_VERIFICATION_REQUIRED',
      'رتبة الأستاذ المعتمد تتطلب اعتماداً رسمياً وتحققاً من مالك المنظومة عبر رمز التحقق المخصص (8 أرقام) ولا يمكن ترقية العضو لأستاذ داخل المجموعة دون اعتماده المسبق',
      403
    );
  }

  const updateResult = await env.DB.prepare(
    `UPDATE group_members SET role = ?
     WHERE group_id = ? AND user_id = ? AND status = 'ACTIVE'`
  ).bind(norm, groupId, targetUserId).run();

  if (updateResult.meta?.changes !== 1) {
    return errorResponse('MEMBER_NOT_ACTIVE', 'تغيرت حالة العضو قبل تحديث رتبته. حدّث القائمة وحاول مجدداً.', 409);
  }

  return jsonResponse({
    success: true,
    message: 'تم تحديث رتبة العضو داخل المجموعة بنجاح ✅',
  });
}

/**
 * Removes or Bans a member from the group.
 */
export async function handleRemoveGroupMember(
  groupId: string,
  targetUserId: string,
  isBan: boolean,
  user: UserContext,
  env: Env
): Promise<Response> {
  const normRole = normalizeRole(user.role);
  const isGlobalAdmin = normRole === Role.ADMIN || normRole === Role.SYSTEM_OWNER;
  const canManage = isGlobalAdmin || await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId });

  if (!canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية إزالة أو حظر أعضاء المجموعة', 403);
  }

  if (targetUserId === user.userId) {
    return errorResponse('INVALID_ACTION', 'لا يمكنك إزالة نفسك من المجموعة عبر هذا الإجراء');
  }

  if (isBan) {
    const target = await env.DB.prepare(
      `SELECT status FROM group_members WHERE group_id = ? AND user_id = ?`
    ).bind(groupId, targetUserId).first<{ status: string }>();

    if (!target) {
      return errorResponse('MEMBER_NOT_FOUND', 'العضو غير موجود في هذه المجموعة', 404);
    }
    if (target.status === 'BANNED') {
      return errorResponse('ALREADY_BANNED', 'هذا العضو محظور بالفعل من المجموعة', 409);
    }

    const banResult = await env.DB.prepare(
      `UPDATE group_members SET status = 'BANNED'
       WHERE group_id = ? AND user_id = ? AND status != 'BANNED'`
    ).bind(groupId, targetUserId).run();

    if (banResult.meta?.changes !== 1) {
      return errorResponse('MEMBER_STATE_CHANGED', 'تغيرت حالة العضو قبل إتمام الحظر. حدّث القائمة وحاول مجدداً.', 409);
    }
    return jsonResponse({ success: true, message: 'تم حظر العضو من المجموعة بنجاح 🚫' });
  } else {
    const removeResult = await env.DB.prepare(
      `DELETE FROM group_members WHERE group_id = ? AND user_id = ?`
    ).bind(groupId, targetUserId).run();

    if (removeResult.meta?.changes !== 1) {
      return errorResponse('MEMBER_NOT_FOUND', 'العضو غير موجود في هذه المجموعة', 404);
    }
    return jsonResponse({ success: true, message: 'تمت إزالة العضو من المجموعة بنجاح' });
  }
}

/**
 * Approves a pending join request.
 */
export async function handleApproveGroupJoinRequest(
  groupId: string,
  targetUserId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const normRole = normalizeRole(user.role);
  const isGlobalAdmin = normRole === Role.ADMIN || normRole === Role.SYSTEM_OWNER;
  const canManage = isGlobalAdmin || await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId });

  if (!canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية اعتماد طلبات الانضمام', 403);
  }

  const approval = await env.DB.prepare(
    `UPDATE group_members SET status = 'ACTIVE', joined_at = ?
     WHERE group_id = ? AND user_id = ? AND status = 'PENDING'`
  ).bind(Date.now(), groupId, targetUserId).run();

  if (approval.meta?.changes !== 1) {
    return errorResponse('JOIN_REQUEST_NOT_FOUND', 'طلب الانضمام غير موجود أو تم البت فيه مسبقاً', 404);
  }

  const [targetUser, group] = await Promise.all([
    env.DB.prepare(`SELECT full_name FROM users WHERE id = ?`).bind(targetUserId).first<{ full_name: string }>(),
    env.DB.prepare(`SELECT name FROM groups WHERE id = ?`).bind(groupId).first<{ name: string }>(),
  ]);
  if (targetUser?.full_name) {
    await postGroupSystemEvent(groupId, `انضم ${targetUser.full_name} إلى المجموعة 🌟`, env);
    await createUserNotification(
      targetUserId,
      'GROUP_JOIN_ACCEPTED',
      'تم قبول طلب انضمامك',
      `تم قبول طلب انضمامك إلى مجموعة "${group?.name || 'المجموعة'}". يمكنك الآن فتح المحادثة والمشاركة.`,
      env
    );
  }

  return jsonResponse({
    success: true,
    message: 'تم قبول العضو واعتماد انضمامه للمجموعة بنجاح ✅',
  });
}

/**
 * A-11c: Rejects a pending group join request.
 */
export async function handleRejectGroupJoinRequest(
  groupId: string,
  targetUserId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const normRole = normalizeRole(user.role);
  const isGlobalAdmin = normRole === Role.ADMIN || normRole === Role.SYSTEM_OWNER;
  const canManage = isGlobalAdmin || await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId });

  if (!canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية رفض طلبات الانضمام', 403);
  }

  const rejection = await env.DB.prepare(
    `UPDATE group_members SET status = 'REJECTED'
     WHERE group_id = ? AND user_id = ? AND status = 'PENDING'`
  ).bind(groupId, targetUserId).run();

  if (rejection.meta?.changes !== 1) {
    return errorResponse('JOIN_REQUEST_NOT_FOUND', 'طلب الانضمام غير موجود أو تم البت فيه مسبقاً', 404);
  }

  const [targetUser, group] = await Promise.all([
    env.DB.prepare(`SELECT full_name FROM users WHERE id = ?`).bind(targetUserId).first<{ full_name: string }>(),
    env.DB.prepare(`SELECT name FROM groups WHERE id = ?`).bind(groupId).first<{ name: string }>(),
  ]);
  await createUserNotification(
    targetUserId,
    'GROUP_JOIN_REJECTED',
    'لم تتم الموافقة على طلب الانضمام',
    `لم تتم الموافقة على طلب انضمامك إلى مجموعة "${group?.name || 'المجموعة'}". يمكنك التواصل مع مشرفي المجموعة للاستفسار.`,
    env
  );

  return jsonResponse({
    success: true,
    message: 'تم رفض طلب الانضمام وإشعار صاحبه',
  });
}

/**
 * A-11f: Allows moderators, teachers, and admins to create public clubs/optional groups.
 */

async function createUserNotification(
  userId: string,
  type: string,
  title: string,
  body: string,
  env: Env
): Promise<void> {
  try {
    await env.DB.prepare(
      `INSERT INTO user_notifications (id, user_id, type, title, body, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(generateId('ntf'), userId, type, title, body, Date.now()).run();
  } catch (error) {
    console.error('Could not create group membership notification:', error);
  }
}

async function notifyGroupManagers(
  groupId: string,
  type: string,
  title: string,
  body: string,
  excludeUserId: string,
  env: Env
): Promise<void> {
  try {
    const managers = await env.DB.prepare(
      `SELECT DISTINCT u.id AS user_id
       FROM users u
       LEFT JOIN group_members gm
         ON gm.user_id = u.id AND gm.group_id = ? AND gm.status = 'ACTIVE'
       WHERE u.id != ?
         AND (
           -- Global school managers can review requests even before joining the group.
           UPPER(u.role) IN ('ADMIN', 'MODERATOR', 'TEACHER', 'VERIFIED_TEACHER')
           -- Group-level managers can review requests only while their membership is active.
           OR (
             gm.status = 'ACTIVE'
             AND UPPER(gm.role) IN ('MODERATOR', 'TEACHER', 'ADMIN')
           )
         )`
    ).bind(groupId, excludeUserId).all<{ user_id: string }>();
    const rows = managers.results || [];
    if (!rows.length || typeof env.DB.batch !== 'function') return;
    await env.DB.batch(rows.map(row => env.DB.prepare(
      `INSERT INTO user_notifications (id, user_id, type, title, body, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(generateId('ntf'), row.user_id, type, title, body, Date.now())));
  } catch (error) {
    console.error('Could not notify group managers about a join request:', error);
  }
}

async function postGroupSystemEvent(groupId: string, text: string, env: Env): Promise<void> {
  try {
    await env.DB.prepare(
      `INSERT INTO chat_messages (
         id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp
       ) VALUES (?, ?, 'SYSTEM_OWNER', 'تنويه المجموعة', 'النظام', ?, ?)`
    ).bind(generateId('msg'), groupId, text, Date.now()).run();
    try {
      const doId = env.CHAT?.idFromName(groupId);
      if (doId) {
        const stub = env.CHAT.get(doId);
        await stub.fetch(new Request('http://internal/broadcast', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            type: 'system_message',
            groupId,
            senderId: 'SYSTEM_OWNER',
            senderName: 'تنويه المجموعة',
            senderGradeSection: 'النظام',
            text,
            timestamp: Date.now(),
          }),
        }));
      }
    } catch {}
  } catch (error) {
    console.error('Could not persist group system event:', error);
  }
}

export async function handleLeaveGroup(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const group = await env.DB.prepare(
    `SELECT id, name, type FROM groups WHERE id = ?`
  ).bind(groupId).first<{ id: string; name: string; type: string }>();
  if (!group) return errorResponse('GROUP_NOT_FOUND', 'المجموعة غير موجودة', 404);
  if (group.type === 'CLASS') {
    return errorResponse('CLASS_GROUP_REQUIRED', 'لا يمكن مغادرة الشعبة الدراسية الأساسية من هذا القسم. تواصل مع إدارة المدرسة عند الحاجة إلى تغيير شعبتك.', 403);
  }
  const current = await env.DB.prepare(
    `SELECT status FROM group_members WHERE group_id = ? AND user_id = ?`
  ).bind(groupId, user.userId).first<{ status: string }>();
  if (!current) return errorResponse('NOT_A_MEMBER', 'أنت لست مسجلاً في هذه المجموعة', 404);

  const removed = await env.DB.prepare(
    `DELETE FROM group_members WHERE group_id = ? AND user_id = ? AND status IN ('ACTIVE', 'PENDING', 'REJECTED')`
  ).bind(groupId, user.userId).run();
  if (removed.meta?.changes !== 1) {
    return errorResponse('LEAVE_FAILED', 'تعذر مغادرة المجموعة. حدّث القائمة وحاول مجدداً.', 409);
  }

  if (current.status === 'ACTIVE') {
    await postGroupSystemEvent(groupId, `غادر ${user.fullName} المجموعة.`, env);
  }
  return jsonResponse({
    success: true,
    message: current.status === 'PENDING'
      ? 'تم إلغاء طلب الانضمام'
      : `غادرت مجموعة "${group.name}" بنجاح`,
  });
}

export async function handleCreatePublicGroup(request: Request, user: UserContext, env: Env): Promise<Response> {
  const normRole = normalizeRole(user.role);
  const canCreate = normRole === Role.ADMIN || normRole === Role.TEACHER || normRole === Role.MODERATOR || normRole === Role.SYSTEM_OWNER;
  if (!canCreate) {
    return errorResponse('FORBIDDEN', 'إنشاء المجموعات العامة متاح للمشرفين والمعلمين والإدارة فقط', 403);
  }

  const body = await request.json() as {
    name?: string;
    description?: string;
    icon?: string;
    visibility?: string;
  };
  if (!body.name || !body.name.trim() || body.name.trim().length > 80) {
    return errorResponse('INVALID_INPUT', 'يرجى إدخال اسم مجموعة لا يتجاوز 80 حرفاً');
  }
  const visibility = (body.visibility || 'PUBLIC').trim().toUpperCase();
  if (visibility !== 'PUBLIC' && visibility !== 'PRIVATE') {
    return errorResponse('INVALID_VISIBILITY', 'نوع المجموعة يجب أن يكون عامة أو خاصة');
  }
  if ((body.description || '').length > 500) {
    return errorResponse('INVALID_INPUT', 'وصف المجموعة طويل جداً');
  }

  const groupId = generateId('grp');
  const now = Date.now();

  if (typeof env.DB.batch !== 'function') {
    return errorResponse('GROUP_CREATION_UNAVAILABLE', 'قاعدة البيانات لا تدعم إنشاء المجموعة بصورة ذرية حالياً', 503);
  }

  // The group and its creator membership form one invariant: never publish a
  // discoverable group unless its creator is an active moderator.
  try {
    const results = await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO groups (
           id, name, type, description, icon, academic_year_id, created_at,
           is_official, is_discoverable, created_by, updated_at, visibility, chat_policy
         ) VALUES (?, ?, 'OPTIONAL', ?, ?, 'default', ?, 0, 1, ?, ?, ?, 'MEMBERS')`
      ).bind(groupId, body.name.trim(), body.description?.trim() || null, body.icon || '👥', now, user.userId, now, visibility),
      env.DB.prepare(
        `INSERT INTO group_members (group_id, user_id, role, status, joined_at)
         VALUES (?, ?, ?, 'ACTIVE', ?)`
      ).bind(groupId, user.userId, Role.MODERATOR, now),
    ]);

    if (results.length !== 2 || results.some(result => result.meta?.changes !== 1)) {
      return errorResponse('GROUP_CREATION_FAILED', 'تعذر إنشاء المجموعة وتعيين مشرفها بصورة مكتملة', 500);
    }
  } catch (error) {
    console.error('Failed to create public group atomically:', error);
    return errorResponse('GROUP_CREATION_FAILED', 'تعذر إنشاء المجموعة حالياً. لم يتم نشر مجموعة ناقصة.', 500);
  }

  return jsonResponse({
    success: true,
    groupId,
    visibility,
    message: visibility === 'PUBLIC'
      ? 'تم إنشاء المجموعة العامة بنجاح 🎉'
      : 'تم إنشاء المجموعة الخاصة بنجاح. سيحتاج الأعضاء إلى موافقة مشرفيها للانضمام.',

  });
}
