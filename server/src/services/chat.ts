import { Env, UserContext } from '../env';
import { generateId } from '../lib/ids';
import { mapChatMessage } from '../lib/mappers';
import { requireGroupMember } from '../middleware/permissions';
import { errorResponse, jsonResponse } from '../lib/response';
import { Permission, hasPermission } from '../lib/permissions';
import { Role, normalizeRole } from '../lib/roles';


export const MAX_CHAT_MESSAGE_CHARACTERS = 2000;
export const MAX_CHAT_BODY_BYTES = 16 * 1024;
export const MAX_CHAT_FRAME_BYTES = 16 * 1024;
export const CHAT_RATE_LIMIT_WINDOW_MS = 60_000;
export const CHAT_RATE_LIMIT_BURST_WINDOW_MS = 10_000;
export const CHAT_RATE_LIMIT_MAX_PER_WINDOW = 30;
export const CHAT_RATE_LIMIT_MAX_BURST = 8;
export type ChatMessageValidation = { valid: true; text: string } | { valid: false; code: 'INVALID_INPUT' | 'MESSAGE_TOO_LONG'; message: string };
export interface ChatRateLimitStorage { get<T = unknown>(key: string): Promise<T | undefined>; put<T = unknown>(key: string, value: T): Promise<void>; }
export function validateChatMessageText(value: unknown): ChatMessageValidation {
  if (typeof value !== 'string' || !value.trim()) return { valid: false, code: 'INVALID_INPUT', message: 'يرجى كتابة نص الرسالة' };
  const text = value.trim();
  if (Array.from(text).length > MAX_CHAT_MESSAGE_CHARACTERS) return { valid: false, code: 'MESSAGE_TOO_LONG', message: 'الحد الأقصى لطول الرسالة هو ' + MAX_CHAT_MESSAGE_CHARACTERS + ' حرفاً.' };
  return { valid: true, text };
}
export async function consumeChatRateLimit(storage: ChatRateLimitStorage, userId: string, now = Date.now()): Promise<boolean> {
  if (!userId.trim()) return false;
  const key = 'chat_rate_limit:' + userId;
  const stored = await storage.get<unknown>(key);
  const recent = Array.isArray(stored) ? stored.filter((t): t is number => typeof t === 'number' && Number.isFinite(t) && t <= now && t > now - CHAT_RATE_LIMIT_WINDOW_MS) : [];
  const burstCount = recent.filter(t => t > now - CHAT_RATE_LIMIT_BURST_WINDOW_MS).length;
  if (recent.length >= CHAT_RATE_LIMIT_MAX_PER_WINDOW || burstCount >= CHAT_RATE_LIMIT_MAX_BURST) return false;
  recent.push(now); await storage.put(key, recent); return true;
}
export interface PersistedChatMessageIdentity {
  group_id: string;
  sender_id: string;
  text: string;
  timestamp: number;
}

export function isSamePersistedChatMessage(
  existing: PersistedChatMessageIdentity | null,
  expected: { groupId: string; senderId: string; text: string }
): existing is PersistedChatMessageIdentity {
  return !!existing &&
    existing.group_id === expected.groupId &&
    existing.sender_id === expected.senderId &&
    existing.text === expected.text;
}

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

  if (!row) {
    // Membership can be revoked while a WebSocket is still open.
    // Missing an active membership must never fall back to public send access.
    return { allowed: false, chatPolicy: 'UNKNOWN', memberRole: Role.STUDENT };
  }

  const chatPolicy = (row.chat_policy || 'MEMBERS').toUpperCase();
  const effectiveGlobalRole = normalizeRole(row.global_role || globalRole);
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

  const contentLength = Number(request.headers.get('Content-Length') || 0);
  if (Number.isFinite(contentLength) && contentLength > MAX_CHAT_BODY_BYTES) return errorResponse('MESSAGE_BODY_TOO_LARGE', 'حجم طلب الرسالة أكبر من الحد المسموح.', 413);
  let rawBody: string;
  try { rawBody = await request.text(); } catch { return errorResponse('INVALID_INPUT', 'تعذر قراءة نص الرسالة.', 400); }
  if (new TextEncoder().encode(rawBody).byteLength > MAX_CHAT_BODY_BYTES) return errorResponse('MESSAGE_BODY_TOO_LARGE', 'حجم طلب الرسالة أكبر من الحد المسموح.', 413);
  let parsed: unknown;
  try { parsed = JSON.parse(rawBody) as unknown; } catch { return errorResponse('INVALID_INPUT', 'صيغة طلب الرسالة غير صالحة.', 400); }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return errorResponse('INVALID_INPUT', 'صيغة طلب الرسالة غير صالحة.', 400);
  const body = parsed as { id?: string; text?: unknown; messageText?: unknown; message?: unknown };
  const validation = validateChatMessageText(body.text ?? body.messageText ?? body.message);
  if (!validation.valid) return errorResponse(validation.code, validation.message, 400);
  const text = validation.text;
  const chatStub = env.CHAT.get(env.CHAT.idFromName(groupId));
  let rateResponse: Response;
  try { rateResponse = await chatStub.fetch(new Request('http://internal/rate-limit', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId: user.userId }) })); }
  catch { return errorResponse('CHAT_RATE_LIMIT_UNAVAILABLE', 'تعذر التحقق من حد إرسال الرسائل. حاول مجدداً.', 503); }
  if (rateResponse.status === 429) return errorResponse('RATE_LIMIT_EXCEEDED', 'أرسلت رسائل كثيرة خلال وقت قصير. انتظر قليلاً ثم حاول مجدداً.', 429);
  if (!rateResponse.ok) return errorResponse('CHAT_RATE_LIMIT_UNAVAILABLE', 'تعذر التحقق من حد إرسال الرسائل. حاول مجدداً.', 503);
  let rateBody: { allowed?: boolean };
  try { rateBody = await rateResponse.json() as { allowed?: boolean }; } catch { return errorResponse('CHAT_RATE_LIMIT_UNAVAILABLE', 'تعذر التحقق من حد إرسال الرسائل. حاول مجدداً.', 503); }
  if (rateBody.allowed !== true) return errorResponse('CHAT_RATE_LIMIT_UNAVAILABLE', 'تعذر التحقق من حد إرسال الرسائل. حاول مجدداً.', 503);
  const messageId = (body.id && typeof body.id === 'string' && body.id.length > 3) ? body.id : generateId('msg');
  const now = Date.now();
  const gradeNameMap: Record<number, string> = { 7: 'سابع', 8: 'ثامن', 9: 'تاسع', 10: 'أول ثانوي', 11: 'ثاني ثانوي', 12: 'ثالث ثانوي' };
  const senderGradeSection = `${gradeNameMap[user.gradeId] || user.gradeId} — ${user.sectionId}`;

  // Server-authoritative sender identity from UserContext
  const insertResult = await env.DB.prepare(
    `INSERT OR IGNORE INTO chat_messages (id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    messageId,
    groupId,
    user.userId,
    user.fullName,
    senderGradeSection,
    text,
    now
  ).run();

  let persistedTimestamp = now;
  const wasInserted = insertResult.meta?.changes === 1;
  if (!wasInserted) {
    const existing = await env.DB.prepare(
      `SELECT group_id, sender_id, text, timestamp
       FROM chat_messages WHERE id = ?`
    ).bind(messageId).first<PersistedChatMessageIdentity>();

    if (!isSamePersistedChatMessage(existing, { groupId, senderId: user.userId, text })) {
      return errorResponse(
        'MESSAGE_ID_CONFLICT',
        'معرّف الرسالة مستخدم مسبقاً لرسالة أخرى. لم يتم تأكيد الإرسال.',
        409
      );
    }
    persistedTimestamp = existing.timestamp;
  }

  const messagePayload = {
    id: messageId,
    groupId,
    senderId: user.userId,
    senderName: user.fullName,
    senderGradeSection,
    text,
    timestamp: persistedTimestamp,
    status: 'SENT',
  };

  // Broadcast only a row that was newly persisted. An exact retry is already
  // visible to other members and must not be announced as a second message.
  if (wasInserted) {
    try {
      await chatStub.fetch(new Request('http://internal/broadcast', {
        method: 'POST',
        body: JSON.stringify({
          type: 'chat_message',
          ...messagePayload,
        }),
      }));
    } catch {
      // Persisted in D1; clients will reconcile through chat history.
    }
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

  const sendPermission = await getGroupChatSendPermission(groupId, user.userId, user.role, env);
  if (!sendPermission.allowed) {
    return errorResponse('READ_ONLY_GROUP', 'لا تملك صلاحية حذف الرسائل في مجموعة الهيئة التعليمية؛ يمكنك القراءة فقط.', 403);
  }

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
