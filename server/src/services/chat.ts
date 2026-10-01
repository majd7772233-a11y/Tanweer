import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapChatMessage } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetGroupMessages(groupId: string, user: UserContext, env: Env): Promise<Response> {
  const memberCheck = await requireGroupMember(user, groupId, env.DB);
  if (memberCheck) return memberCheck;

  const messages = await env.DB.prepare(
    `SELECT id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp
     FROM chat_messages
     WHERE group_id = ?
     ORDER BY timestamp ASC LIMIT 100`
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

  const body = await request.json() as { text?: string; messageText?: string; message?: string };
  const messageText = body.text || body.messageText || body.message;

  if (!messageText || !messageText.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة نص الرسالة');
  }

  const messageId = generateId('msg');
  const now = Date.now();
  const gradeNameMap: Record<number, string> = {
    7: 'سابع', 8: 'ثامن', 9: 'تاسع', 10: 'أول ثانوي', 11: 'ثاني ثانوي', 12: 'ثالث ثانوي'
  };
  const senderGradeSection = `${gradeNameMap[user.gradeId] || user.gradeId} — ${user.sectionId}`;

  // Server-authoritative sender identity from UserContext
  await env.DB.prepare(
    `INSERT INTO chat_messages (id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp)
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
