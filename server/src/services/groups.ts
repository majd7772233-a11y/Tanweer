import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapGroup } from '../lib/mappers';
import { errorResponse, jsonResponse } from '../lib/response';

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
    myGroups: (userGroups.results || []).map(g => mapGroup(g, (g as any).role || 'MEMBER')),
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
    return errorResponse('ALREADY_REQUESTED', 'طلب الانضمام قيد المراجعة');
  }

  const initialStatus = group.type === 'OPTIONAL' ? 'ACTIVE' : 'PENDING';
  await env.DB.prepare(
    `INSERT INTO group_members (group_id, user_id, role, status, joined_at)
     VALUES (?, ?, 'MEMBER', ?, ?)`
  ).bind(groupId, user.userId, initialStatus, Date.now()).run();

  return jsonResponse({
    success: true,
    status: initialStatus,
    message: initialStatus === 'ACTIVE' ? 'تم الانضمام إلى المجموعة بنجاح' : 'تم إرسال طلب الانضمام إلى المشرفين',
  });
}
