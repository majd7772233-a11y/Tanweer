import { Env } from '../env';
import { generateId } from '../lib/ids';

interface ClientSession {
  ws: WebSocket;
  userId: string;
  fullName: string;
  gradeSection: string;
  groupId: string;
}

export class GroupChatDO {
  state: DurableObjectState;
  env: Env;
  sessions: Map<WebSocket, ClientSession>;

  constructor(state: DurableObjectState, env: Env) {
    this.state = state;
    this.env = env;
    this.sessions = new Map();
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    // 1. Broadcast endpoint from internal REST postGroupMessage
    if (request.method === 'POST') {
      try {
        const data = await request.text();
        this.broadcast(data);
        return new Response(JSON.stringify({ success: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      } catch {
        return new Response('Error', { status: 500 });
      }
    }

    // 2. WebSocket Upgrade
    if (request.headers.get('Upgrade') === 'websocket') {
      const userId = request.headers.get('X-User-Id');
      const rawName = request.headers.get('X-User-Name');
      const rawGrade = request.headers.get('X-User-Grade-Section');
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
        groupId,
      };

      this.sessions.set(server, session);

      server.addEventListener('message', async (event) => {
        try {
          const raw = typeof event.data === 'string' ? event.data : new TextDecoder().decode(event.data);
          const data = JSON.parse(raw);

          const text = (data.text || '').trim();
          if (!text) return; // ignore empty messages

          const messageId = (data.id && typeof data.id === 'string' && data.id.length > 3)
            ? data.id
            : generateId('msg');
          const now = Date.now();

          // Server-authoritative sender identity from verified session
          const messagePayload = {
            type: 'chat_message',
            id: messageId,
            groupId: session.groupId,
            senderId: session.userId,
            senderName: session.fullName,
            senderGradeSection: session.gradeSection,
            text: text,
            timestamp: now,
            status: 'SENT',
          };

          // Save message to D1 (idempotent insert)
          try {
            await this.env.DB.prepare(
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
          } catch (dbErr) {
            console.error('Failed to persist chat message in DO:', dbErr);
          }

          // Broadcast verified message to all connected clients
          this.broadcast(JSON.stringify(messagePayload));
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
