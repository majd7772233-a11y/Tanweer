import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapGroup } from '../lib/mappers';
import { errorResponse, jsonResponse } from '../lib/response';
import { Role, normalizeRole } from '../lib/roles';
import { Permission, hasPermission } from '../lib/permissions';

export async function handleGetGroups(user: UserContext, env: Env): Promise<Response> {
  const userGroups = await env.DB.prepare(
    `SELECT g.id, g.name, g.type, g.description, g.icon, gm.role, gm.status,
            (SELECT COUNT(*) FROM group_members WHERE group_id = g.id AND status = 'ACTIVE') as member_count
     FROM groups g
     JOIN group_members gm ON g.id = gm.group_id
     WHERE gm.user_id = ? AND gm.status = 'ACTIVE'`
  ).bind(user.userId).all();

  const publicClubs = await env.DB.prepare(
    `SELECT g.id, g.name, g.type, g.description, g.icon,
            (SELECT COUNT(*) FROM group_members WHERE group_id = g.id AND status = 'ACTIVE') as member_count
     FROM groups g
     WHERE g.type = 'OPTIONAL' AND g.id NOT IN (
       SELECT group_id FROM group_members WHERE user_id = ? AND status = 'ACTIVE'
     )`
  ).bind(user.userId).all();

  return jsonResponse({
    success: true,
    myGroups: (userGroups.results || []).map(g => mapGroup(g, (g as any).role || Role.STUDENT)),
    discoverGroups: (publicClubs.results || []).map(g => mapGroup(g, 'DISCOVER')),
  });
}

export async function handleJoinGroupRequest(groupId: string, user: UserContext, env: Env): Promise<Response> {
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

  const initialStatus = group.type === 'OPTIONAL' ? 'ACTIVE' : 'PENDING';
  await env.DB.prepare(
    `INSERT INTO group_members (group_id, user_id, role, status, joined_at)
     VALUES (?, ?, ?, ?, ?)`
  ).bind(groupId, user.userId, Role.STUDENT, initialStatus, Date.now()).run();

  return jsonResponse({
    success: true,
    status: initialStatus,
    message: initialStatus === 'ACTIVE' ? 'تم الانضمام إلى المجموعة بنجاح' : 'تم إرسال طلب الانضمام إلى المشرفين',
  });
}

/**
 * Lists all members of a group with their roles and statuses.
 */
export async function handleGetGroupMembers(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const canManage = await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId }) ||
                    ['TEACHER', 'MODERATOR', 'ADMIN', 'SYSTEM_OWNER'].includes(normalizeRole(user.role));

  const members = await env.DB.prepare(
    `SELECT gm.user_id, gm.role as member_role, gm.status, gm.joined_at,
            u.full_name, u.phone_number, u.role as global_role, u.grade_id, u.section_id
     FROM group_members gm
     JOIN users u ON gm.user_id = u.id
     WHERE gm.group_id = ?
     ORDER BY CASE WHEN gm.role = 'ADMIN' THEN 1 WHEN gm.role = 'TEACHER' THEN 2 WHEN gm.role = 'MODERATOR' THEN 3 ELSE 4 END, gm.joined_at ASC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    canManageMembers: canManage,
    members: (members.results || []).map((m: any) => ({
      userId: m.user_id,
      fullName: m.full_name,
      phoneNumber: canManage ? m.phone_number : undefined,
      memberRole: m.member_role,
      globalRole: m.global_role,
      status: m.status,
      joinedAt: m.joined_at,
      gradeId: m.grade_id,
      sectionId: m.section_id,
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
  const canManage = await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId }) ||
                    ['TEACHER', 'MODERATOR', 'ADMIN', 'SYSTEM_OWNER'].includes(normalizeRole(user.role));

  if (!canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية تعديل رتب أعضاء المجموعة', 403);
  }

  const body = await request.json() as { newRole?: string };
  if (!body.newRole) {
    return errorResponse('INVALID_INPUT', 'يرجى تحديد الرتبة الجديدة');
  }

  const norm = normalizeRole(body.newRole);
  if (norm === Role.SYSTEM_OWNER) {
    return errorResponse('FORBIDDEN', 'لا يمكن تعيين رتبة مالك المنظومة (SYSTEM_OWNER) لأعضاء المجموعات', 403);
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
  const canManage = await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId }) ||
                    ['TEACHER', 'MODERATOR', 'ADMIN', 'SYSTEM_OWNER'].includes(normalizeRole(user.role));

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
  const canManage = await hasPermission(user, Permission.MANAGE_GROUP_MEMBERS, { db: env.DB, groupId }) ||
                    ['TEACHER', 'MODERATOR', 'ADMIN', 'SYSTEM_OWNER'].includes(normalizeRole(user.role));

  if (!canManage) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية اعتماد طلبات الانضمام', 403);
  }

  await env.DB.prepare(
    `UPDATE group_members SET status = 'ACTIVE' WHERE group_id = ? AND user_id = ? AND status = 'PENDING'`
  ).bind(groupId, targetUserId).run();

  return jsonResponse({
    success: true,
    message: 'تم قبول العضو واعتماد انضمامه للمجموعة بنجاح ✅',
  });
}
