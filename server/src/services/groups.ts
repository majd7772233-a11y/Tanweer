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
            (SELECT COUNT(*) FROM group_members WHERE group_id = g.id AND status = 'ACTIVE') as member_count
     FROM groups g
     JOIN group_members gm ON g.id = gm.group_id
     WHERE gm.user_id = ? AND gm.status = 'ACTIVE'`
  ).bind(user.userId).all();

  // A-11a: Discover both OPTIONAL groups and other CLASS sections/grades the user is not actively in
  const discoverGroups = await env.DB.prepare(
    `SELECT g.id, g.name, g.type, g.description, g.icon,
            (SELECT COUNT(*) FROM group_members WHERE group_id = g.id AND status = 'ACTIVE') as member_count
     FROM groups g
     WHERE g.id NOT IN (
       SELECT group_id FROM group_members WHERE user_id = ? AND status = 'ACTIVE'
     )
     ORDER BY CASE WHEN g.type = 'OPTIONAL' THEN 0 ELSE 1 END, g.name ASC`
  ).bind(user.userId).all();

  return jsonResponse({
    success: true,
    myGroups: (userGroups.results || []).map(g => mapGroup(g, (g as any).role || Role.STUDENT)),
    discoverGroups: (discoverGroups.results || []).map(g => mapGroup(g, 'DISCOVER')),
  });
}

export async function handleJoinGroupRequest(groupId: string, user: UserContext, request: Request, env: Env): Promise<Response> {
  const group = await env.DB.prepare(`SELECT id, type FROM groups WHERE id = ?`).bind(groupId).first<{ id: string; type: string }>();
  if (!group) {
    return errorResponse('GROUP_NOT_FOUND', 'المجموعة غير موجودة');
  }

  const existing = await env.DB.prepare(
    `SELECT status FROM group_members WHERE group_id = ? AND user_id = ?`
  ).bind(groupId, user.userId).first<{ status: string }>();

  if (existing) {
    if (existing.status === 'ACTIVE') {
      return errorResponse('ALREADY_MEMBER', 'أنت عضو مسجل بالفعل في هذه المجموعة');
    }
    if (existing.status === 'BANNED') {
      return errorResponse('BANNED', 'تم حظر حسابك من هذه المجموعة من قِبل المشرفين', 403);
    }
    return errorResponse('ALREADY_REQUESTED', 'طلب الانضمام قيد المراجعة');
  }

  let requestReason: string | null = null;
  try {
    const body = await request.json() as any;
    if (body?.reason && typeof body.reason === 'string') {
      requestReason = body.reason.trim();
    }
  } catch {}

  const initialStatus = group.type === 'OPTIONAL' ? 'ACTIVE' : 'PENDING';
  try {
    await env.DB.prepare(
      `INSERT INTO group_members (group_id, user_id, role, status, joined_at, request_reason)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(groupId, user.userId, Role.STUDENT, initialStatus, Date.now(), requestReason).run();
  } catch {
    await env.DB.prepare(
      `INSERT INTO group_members (group_id, user_id, role, status, joined_at)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(groupId, user.userId, Role.STUDENT, initialStatus, Date.now()).run();
  }

  // A-11d: Post system event in chat upon confirmed membership
  if (initialStatus === 'ACTIVE') {
    try {
      const msgId = generateId('msg');
      await env.DB.prepare(
        `INSERT INTO chat_messages (id, group_id, sender_id, text, type, created_at)
         VALUES (?, ?, 'system', ?, 'SYSTEM', ?)`
      ).bind(msgId, groupId, `انضم ${user.fullName} إلى المجموعة 🌟`, Date.now()).run();
    } catch {}
  }

  return jsonResponse({
    success: true,
    status: initialStatus,
    message: initialStatus === 'ACTIVE' ? 'تم الانضمام إلى المجموعة بنجاح 🎉' : 'تم إرسال طلب الانضمام إلى المشرفين',
  });
}

/**
 * Lists all members of a group with their roles and statuses.
 * Requires active group membership or global administrative authority.
 * Scopes phone number visibility and pending status visibility strictly to group managers.
 */
export async function handleGetGroupMembers(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const normRole = normalizeRole(user.role);
  const isGlobalAdmin = normRole === Role.ADMIN || normRole === Role.SYSTEM_OWNER;
  const canManage = isGlobalAdmin || await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId });

  const statusFilter = canManage ? '' : "AND gm.status = 'ACTIVE'";

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
  if (!body.newRole) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد الرتبة الجديدة');
  }

  const norm = normalizeRole(body.newRole);
  if (norm === Role.SYSTEM_OWNER || norm === Role.ADMIN) {
    return errorResponse('FORBIDDEN', 'رتبتا مالك المنظومة والمدير العام (ADMIN) تُداران حصراً عبر الإدارة العليا ولا يمكن تعيينهما كأدوار فرعية داخل المجموعات', 403);
  }

  const targetUser = await env.DB.prepare(
    `SELECT role FROM users WHERE id = ?`
  ).bind(targetUserId).first<{ role: string }>();

  const targetGlobalRole = normalizeRole(targetUser?.role);
  const actorRole = normalizeRole(user.role);

  if (norm === Role.TEACHER && targetGlobalRole !== Role.TEACHER && actorRole !== Role.SYSTEM_OWNER) {
    return errorResponse(
      'FORBIDDEN_TEACHER_VERIFICATION_REQUIRED',
      'رتبة الأستاذ المعتمد تتطلب اعتماداً رسمياً وتحققاً من مالك المنظومة عبر رمز التحقق المخصص (8 أرقام) ولا يمكن ترقية العضو لأستاذ داخل المجموعة دون اعتماده المسبق',
      403
    );
  }

  await env.DB.prepare(
    `UPDATE group_members SET role = ? WHERE group_id = ? AND user_id = ?`
  ).bind(norm, groupId, targetUserId).run();

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
    await env.DB.prepare(
      `UPDATE group_members SET status = 'BANNED' WHERE group_id = ? AND user_id = ?`
    ).bind(groupId, targetUserId).run();
    return jsonResponse({ success: true, message: 'تم حظر العضو من المجموعة بنجاح 🚫' });
  } else {
    await env.DB.prepare(
      `DELETE FROM group_members WHERE group_id = ? AND user_id = ?`
    ).bind(groupId, targetUserId).run();
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

  await env.DB.prepare(
    `UPDATE group_members SET status = 'ACTIVE' WHERE group_id = ? AND user_id = ? AND status = 'PENDING'`
  ).bind(groupId, targetUserId).run();

  // A-11d: Post system event in group chat
  try {
    const targetUser = await env.DB.prepare(`SELECT full_name FROM users WHERE id = ?`).bind(targetUserId).first<{ full_name: string }>();
    if (targetUser?.full_name) {
      const msgId = generateId('msg');
      await env.DB.prepare(
        `INSERT INTO chat_messages (id, group_id, sender_id, text, type, created_at)
         VALUES (?, ?, 'system', ?, 'SYSTEM', ?)`
      ).bind(msgId, groupId, `انضم ${targetUser.full_name} إلى المجموعة 🌟`, Date.now()).run();
    }
  } catch {}

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

  await env.DB.prepare(
    `DELETE FROM group_members WHERE group_id = ? AND user_id = ? AND status = 'PENDING'`
  ).bind(groupId, targetUserId).run();

  return jsonResponse({
    success: true,
    message: 'تم رفض طلب الانضمام بنجاح',
  });
}

/**
 * A-11f: Allows moderators, teachers, and admins to create public clubs/optional groups.
 */
export async function handleCreatePublicGroup(request: Request, user: UserContext, env: Env): Promise<Response> {
  const normRole = normalizeRole(user.role);
  const canCreate = normRole === Role.ADMIN || normRole === Role.TEACHER || normRole === Role.MODERATOR || normRole === Role.SYSTEM_OWNER;
  if (!canCreate) {
    return errorResponse('FORBIDDEN', 'إنشاء المجموعات العامة متاح للمشرفين والمعلمين والإدارة فقط', 403);
  }

  const body = await request.json() as { name?: string; description?: string; icon?: string };
  if (!body.name || !body.name.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى إدخال اسم المجموعة');
  }

  const groupId = generateId('grp');
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO groups (id, name, type, description, icon, is_official, is_discoverable, created_by, created_at, updated_at)
     VALUES (?, ?, 'OPTIONAL', ?, ?, 0, 1, ?, ?, ?)`
  ).bind(groupId, body.name.trim(), body.description?.trim() || null, body.icon || '👥', user.userId, now, now).run();

  // Add creator as MODERATOR
  await env.DB.prepare(
    `INSERT INTO group_members (group_id, user_id, role, status, joined_at)
     VALUES (?, ?, ?, 'ACTIVE', ?)`
  ).bind(groupId, user.userId, Role.MODERATOR, now).run();

  return jsonResponse({
    success: true,
    groupId,
    message: 'تم إنشاء المجموعة العامة بنجاح 🎉',
  });
}
