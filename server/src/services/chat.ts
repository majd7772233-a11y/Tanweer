import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { errorResponse, jsonResponse } from '../lib/response';

export async function handleGetGroupMessages(groupId: string, env: Env): Promise<Response> {
  const messages = await env.DB.prepare(
    `SELECT id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp
     FROM chat_messages
     WHERE group_id = ?
     ORDER BY timestamp ASC LIMIT 100`
  ).bind(groupId).all();

  return jsonResponse({
    success: true,
    messages: messages.results,
  });
}

export async function handlePostGroupMessage(
  groupId: string,
  user: UserContext,
  request: Request,
  env: Env
): Promise<Response> {
  const body = await request.json() as { text?: string };
  if (!body.text || !body.text.trim()) {
    return errorResponse('INVALID_INPUT', 'يرجى كتابة نص الرسالة');
  }

  const messageId = generateId('msg');
  const now = Date.now();
  const gradeNameMap: Record<number, string> = {
    7: 'سابع', 8: 'ثامن', 9: 'تاسع', 10: 'أول ثانوي', 11: 'ثاني ثانوي', 12: 'ثالث ثانوي'
  };
  const senderGradeSection = `${gradeNameMap[user.gradeId] || user.gradeId} — ${user.sectionId}`;

  await env.DB.prepare(
    `INSERT INTO chat_messages (id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    messageId,
    groupId,
    user.userId,
    user.fullName,
    senderGradeSection,
    body.text.trim(),
    now
  ).run();

  // Forward to Durable Object for live WebSocket broadcast if available
  try {
    const doId = env.CHAT.idFromName(groupId);
    const doStub = env.CHAT.get(doId);
    await doStub.fetch(new Request('http://internal/broadcast', {
      method: 'POST',
      body: JSON.stringify({
        id: messageId,
        groupId,
        senderId: user.userId,
        senderName: user.fullName,
        senderGradeSection,
        text: body.text.trim(),
        timestamp: now,
      }),
    }));
  } catch {
    // Durable object broadcast fallback
  }

  return jsonResponse({
    success: true,
    message: {
      id: messageId,
      groupId,
      senderId: user.userId,
      senderName: user.fullName,
      senderGradeSection,
      text: body.text.trim(),
      timestamp: now,
    },
  });
}
