import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapChatMessage } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';

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
