import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapChatMessage } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { Role, normalizeRole } from '../lib/roles';


export async function getGroupChatSendPermission(
  groupId: string,
  userId: string,
  globalRole: string,
  env: Env
): Promise<{ allowed: boolean; chatPolicy: string; memberRole: string }> {
  const row = await env.DB.prepare(
    `SELECT g.chat_policy, gm.role AS member_role, u.role AS global_role
     FROM groups g
     JOIN group_members gm ON gm.group_id = g.id AND gm.user_id = ? AND gm.status = 'ACTIVE'
     JOIN users u ON u.id = gm.user_id
     WHERE g.id = ?`
  ).bind(userId, groupId).first<{
    chat_policy: string | null;
    member_role: string;
    global_role: string;
  }>();

  const chatPolicy = (row?.chat_policy || 'MEMBERS').toUpperCase();
  const effectiveGlobalRole = normalizeRole(row?.global_role || globalRole);
  const groupRole = normalizeRole(row?.member_role || Role.STUDENT);
  if (chatPolicy !== 'STAFF_ONLY') {
    return { allowed: true, chatPolicy, memberRole: groupRole };
  }

  const allowedRoles = new Set<Role>([Role.TEACHER, Role.ADMIN, Role.SYSTEM_OWNER]);
  return {
    allowed: allowedRoles.has(effectiveGlobalRole) || groupRole === Role.TEACHER || groupRole === Role.ADMIN,
    chatPolicy,
    memberRole: groupRole,
  };
}

export async function handleGetGroupMessages(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  // Retrieve the latest 100 messages in chronological order
  const messages = await env.DB.prepare(
    `SELECT * FROM (
       SELECT id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp
       FROM chat_messages
       WHERE group_id = ?
       ORDER BY timestamp DESC LIMIT 100
     ) ORDER BY timestamp ASC`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    messages: (messages.results || []).map(mapChatMessage),
  });
}

export async function handlePostGroupMessage(
  groupId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const sendPermission = await getGroupChatSendPermission(groupId, user.userId, user.role, env);
  if (!sendPermission.allowed) {
    return errorResponse('READ_ONLY_GROUP', 'هذه المجموعة مخصصة للقراءة للطلاب. الإرسال متاح للأساتذة المعتمدين وإدارة المدرسة فقط.', 403);
  }

  const body = await request.json() as { id?: string; text?: string; messageText?: string; message?: string };
  const messageText = body.text || body.messageText || body.message;

  if (!messageText || !messageText.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة نص الرسالة');
  }

  const messageId = (body.id && typeof body.id === 'string' && body.id.length > 3)
    ? body.id
    : generateId('msg');
  const now = Date.now();
  const gradeNameMap: Record<number, string> = {
    7: 'سابع', 8: 'ثامن', 9: 'تاسع', 10: 'أول ثانوي', 11: 'ثاني ثانوي', 12: 'ثالث ثانوي'
  };
  const senderGradeSection = `${gradeNameMap[user.gradeId] || user.gradeId} — ${user.sectionId}`;

  // Server-authoritative sender identity from UserContext
  await env.DB.prepare(
    `INSERT OR IGNORE INTO chat_messages (id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    messageId,
    groupId,
    user.userId,
    user.fullName,
    senderGradeSection,
    messageText.trim(),
    now
  ).run();

  const messagePayload = {
    id: messageId,
    groupId,
    senderId: user.userId,
    senderName: user.fullName,
    senderGradeSection,
    text: messageText.trim(),
    timestamp: now,
    status: 'SENT',
  };

  // Forward to Durable Object for live WebSocket broadcast
  try {
    const doId = env.CHAT.idFromName(groupId);
    const doStub = env.CHAT.get(doId);
    await doStub.fetch(new Request('http://internal/broadcast', {
      method: 'POST',
      body: JSON.stringify({
        type: 'chat_message',
        ...messagePayload,
      }),
    }));
  } catch {
    // Durable object fallback
  }

  return jsonResponse({
    success: true,
    message: messagePayload,
  });
}

export async function handleDeleteGroupMessage(
  groupId: string,
  messageId: string,
  user: UserContext,
  env: Env
): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const message = await env.DB.prepare(
    `SELECT sender_id FROM chat_messages WHERE id = ? AND group_id = ?`
  ).bind(messageId, groupId).first<{ sender_id: string }>();

  if (!message) {
    return errorResponse('MESSAGE_NOT_FOUND', 'الرسالة غير موجودة', 404);
  }

  const isOwner = message.sender_id === user.userId;
  const canModerate = await hasPermission(user, Permission.DELETE_ANY_CHAT_MESSAGE, {
    db: env.DB,
    groupId,
    resourceOwnerId: message.sender_id,
  });

  if (!isOwner && !canModerate) {
    return errorResponse('FORBIDDEN', 'لا تملك صلاحية حذف هذه الرسالة', 403);
  }

  await env.DB.prepare(`DELETE FROM chat_messages WHERE id = ?`).bind(messageId).run();

  try {
    const doId = env.CHAT.idFromName(groupId);
    const doStub = env.CHAT.get(doId);
    await doStub.fetch(new Request('http://internal/broadcast', {
      method: 'POST',
      body: JSON.stringify({
        type: 'message_deleted',
        messageId,
        groupId,
        deletedBy: user.userId,
      }),
    }));
  } catch {}

  return jsonResponse({
    success: true,
    message: 'تم حذف الرسالة بنجاح',
  });
}
