import { Env } from '../env';
import { generateId } from '../lib/ids';
import { consumeChatRateLimit, getGroupChatSendPermission, isSamePersistedChatMessage, MAX_CHAT_FRAME_BYTES, PersistedChatMessageIdentity, validateChatMessageText } from '../services/chat';

interface ClientSession {
  ws: WebSocket;
  userId: string;
  fullName: string;
  gradeSection: string;
  role: string;
  groupId: string;
  canSend: boolean;
}

export class GroupChatDO {
  state: DurableObjectState;
  env: Env;
  sessions: Map<WebSocket, ClientSession>;
  private rateLimitQueue: Promise<void> = Promise.resolve();

  constructor(state: DurableObjectState, env: Env) {
    this.state = state;
    this.env = env;
    this.sessions = new Map();
  }

  private async consumeMessageRateLimit(userId: string): Promise<boolean> {
    const previous = this.rateLimitQueue;
    let release!: () => void;
    this.rateLimitQueue = new Promise<void>((resolve) => { release = resolve; });
    await previous;
    try { return await consumeChatRateLimit(this.state.storage, userId); } finally { release(); }
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === 'POST' && url.pathname === '/rate-limit') {
      let data: { userId?: unknown };
      try { data = await request.json() as { userId?: unknown }; } catch { return new Response(JSON.stringify({ success: false }), { status: 400, headers: { 'Content-Type': 'application/json' } }); }
      if (typeof data.userId !== 'string' || !data.userId.trim()) return new Response(JSON.stringify({ success: false }), { status: 400, headers: { 'Content-Type': 'application/json' } });
      try {
        const allowed = await this.consumeMessageRateLimit(data.userId);
        return new Response(JSON.stringify({ success: true, allowed }), { status: allowed ? 200 : 429, headers: { 'Content-Type': 'application/json' } });
      } catch { return new Response(JSON.stringify({ success: false, code: 'CHAT_RATE_LIMIT_UNAVAILABLE' }), { status: 503, headers: { 'Content-Type': 'application/json' } }); }
    }

    if (request.method === 'POST' && url.pathname === '/broadcast') {
      try {
        const data = await request.text(); this.broadcast(data);
        return new Response(JSON.stringify({ success: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      } catch { return new Response('Error', { status: 500 }); }
    }

    // WebSocket Upgrade
    if (request.headers.get('Upgrade') === 'websocket') {
      const userId = request.headers.get('X-User-Id');
      const rawName = request.headers.get('X-User-Name');
      const rawGrade = request.headers.get('X-User-Grade-Section');
      const verifiedRole = request.headers.get('X-User-Role') || 'STUDENT';
      const groupId = request.headers.get('X-Group-Id') || url.pathname.split('/')[3];

      if (!userId || !rawName) {
        return new Response('Unauthorized: Missing verified identity headers', { status: 401 });
      }

      const fullName = decodeURIComponent(rawName);
      const gradeSection = rawGrade ? decodeURIComponent(rawGrade) : '';

      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);

      server.accept();

      const session: ClientSession = {
        ws: server,
        userId,
        fullName,
        gradeSection,
        role: verifiedRole,
        groupId,
        canSend: request.headers.get('X-Can-Send') === '1',
      };

      this.sessions.set(server, session);

      server.addEventListener('message', async (event) => {
        try {
          const raw = typeof event.data === 'string' ? event.data : new TextDecoder().decode(event.data);
          if (new TextEncoder().encode(raw).byteLength > MAX_CHAT_FRAME_BYTES) {
            try { server.send(JSON.stringify({ type: 'message_error', code: 'MESSAGE_BODY_TOO_LARGE', error: 'حجم الرسالة أكبر من الحد المسموح.' })); } catch {}
            server.close(1009, 'Message frame too large'); return;
          }
          const parsed = JSON.parse(raw);
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return;
          const data = parsed as Record<string, any>;
          const isDeleteMessage = data.type === 'delete_message' || data.action === 'delete_message';
          const hasMessageText = Object.prototype.hasOwnProperty.call(data, 'text');
          if (!isDeleteMessage && !hasMessageText) return;

          // Every supported chat action consumes the persistent per-user/per-group quota.
          // Check before D1 authorization reads so delete operations cannot bypass the guard.
          let rateAllowed = false;
          try { rateAllowed = await this.consumeMessageRateLimit(session.userId); } catch {
            try { server.send(JSON.stringify({ type: 'message_error', code: 'CHAT_RATE_LIMIT_UNAVAILABLE', error: 'تعذر التحقق من حد إرسال الرسائل. حاول مجدداً.' })); } catch {}
            return;
          }
          if (!rateAllowed) {
            try { server.send(JSON.stringify({ type: 'message_error', code: 'RATE_LIMIT_EXCEEDED', error: 'أرسلت رسائل كثيرة خلال وقت قصير. انتظر قليلاً ثم حاول مجدداً.' })); } catch {}
            return;
          }

          // Re-check membership and group policy for every supported chat action.
          // This invalidates already-open sockets immediately after demotion/removal.
          const livePermission = await getGroupChatSendPermission(
            session.groupId,
            session.userId,
            session.role,
            this.env
          );
          if (!livePermission.allowed) {
            try {
              server.send(JSON.stringify({
                type: 'error',
                code: 'READ_ONLY_GROUP',
                message: 'لم تعد تملك صلاحية الإرسال في هذه المجموعة. يمكنك متابعة القراءة فقط.',
              }));
            } catch {}
            return;
          }

          // Handle message deletion action by sender or moderator/teacher/admin
          if (isDeleteMessage) {
            const candidateId = data.messageId ?? data.id;
            if (typeof candidateId !== 'string' || !candidateId.trim() || candidateId.length > 128) {
              try { server.send(JSON.stringify({ type: 'message_error', code: 'INVALID_MESSAGE_ID', error: 'معرّف الرسالة المطلوب حذفه غير صالح.' })); } catch {}
              return;
            }
            const messageId = candidateId;

            const existing = await this.env.DB.prepare(
              `SELECT sender_id FROM chat_messages WHERE id = ? AND group_id = ?`
            ).bind(messageId, session.groupId).first<{ sender_id: string }>();

            if (existing) {
              const isOwner = existing.sender_id === session.userId;
              const isPrivileged = ['MODERATOR', 'TEACHER', 'ADMIN', 'SYSTEM_OWNER'].includes(session.role);

              if (isOwner || isPrivileged) {
                await this.env.DB.prepare(`DELETE FROM chat_messages WHERE id = ?`).bind(messageId).run();
                this.broadcast(JSON.stringify({
                  type: 'message_deleted',
                  messageId,
                  groupId: session.groupId,
                  deletedBy: session.userId,
                }));
              }
            }
            return;
          }

          const validation = validateChatMessageText(data.text);
          if (!validation.valid) {
            try { server.send(JSON.stringify({ type: 'message_error', id: typeof data.id === 'string' ? data.id : '', code: validation.code, error: validation.message })); } catch {}
            return;
          }
          const text = validation.text;
          const messageId = (data.id && typeof data.id === 'string' && data.id.length > 3) ? data.id : generateId('msg');
          const now = Date.now();

          // Server-authoritative sender identity from verified session
          const messagePayload = {
            type: 'chat_message',
            id: messageId,
            groupId: session.groupId,
            senderId: session.userId,
            senderName: session.fullName,
            senderGradeSection: session.gradeSection,
            senderRole: session.role,
            text: text,
            timestamp: now,
            status: 'SENT',
          };

          // Persist once, and ACK only a new row or an exact retry of that row.
          try {
            const insertResult = await this.env.DB.prepare(
              `INSERT OR IGNORE INTO chat_messages (id, group_id, sender_id, sender_name, sender_grade_section, text, timestamp)
               VALUES (?, ?, ?, ?, ?, ?, ?)`
            ).bind(
              messageId,
              session.groupId,
              session.userId,
              session.fullName,
              session.gradeSection,
              text,
              now
            ).run();

            const wasInserted = insertResult.meta?.changes === 1;
            if (!wasInserted) {
              const existing = await this.env.DB.prepare(
                `SELECT group_id, sender_id, text, timestamp
                 FROM chat_messages WHERE id = ?`
              ).bind(messageId).first<PersistedChatMessageIdentity>();

              if (!isSamePersistedChatMessage(existing, {
                groupId: session.groupId,
                senderId: session.userId,
                text,
              })) {
                server.send(JSON.stringify({
                  type: 'message_error',
                  id: messageId,
                  error: 'معرّف الرسالة مستخدم مسبقاً لرسالة أخرى. لم يتم تأكيد الإرسال.',
                }));
                return;
              }
              messagePayload.timestamp = existing.timestamp;
            } else {
              // Broadcast only after D1 confirms a new persisted row.
              this.broadcast(JSON.stringify(messagePayload));
            }

            // An exact persisted retry is safe to ACK, but is not rebroadcast.
            try {
              server.send(JSON.stringify({ type: 'ack', id: messageId, status: 'SENT' }));
            } catch {}
          } catch (dbErr) {
            console.error('Failed to persist chat message in DO:', dbErr);
            // Notify the sender that message persistence failed
            try {
              server.send(JSON.stringify({
                type: 'message_error',
                id: messageId,
                error: 'تعذر حفظ الرسالة في قاعدة البيانات'
              }));
            } catch {}
          }
        } catch {
          // ignore malformed packets
        }
      });

      server.addEventListener('close', () => {
        this.sessions.delete(server);
      });

      server.addEventListener('error', () => {
        this.sessions.delete(server);
      });

      return new Response(null, { status: 101, webSocket: client });
    }

    return new Response('Not WebSocket', { status: 400 });
  }

  broadcast(message: string) {
    for (const [ws] of this.sessions) {
      try {
        ws.send(message);
      } catch {
        this.sessions.delete(ws);
      }
    }
  }
}
