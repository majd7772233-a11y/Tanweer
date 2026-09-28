import { Env } from '../env';

export class GroupChatDO {
  state: DurableObjectState;
  env: Env;
  sessions: Set<WebSocket>;

  constructor(state: DurableObjectState, env: Env) {
    this.state = state;
    this.env = env;
    this.sessions = new Set();
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (request.headers.get('Upgrade') === 'websocket') {
      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);

      server.accept();
      this.sessions.add(server);

      server.addEventListener('message', async (event) => {
        try {
          const data = JSON.parse(event.data as string);
          // Broadcast to all connected clients in the group
          this.broadcast(JSON.stringify({
            type: 'chat_message',
            ...data,
            timestamp: Date.now(),
          }));
        } catch {
          // ignore malformed
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

    if (request.method === 'POST') {
      try {
        const data = await request.text();
        this.broadcast(data);
        return new Response(JSON.stringify({ success: true }), { status: 200 });
      } catch {
        return new Response('Error', { status: 500 });
      }
    }

    return new Response('Not WebSocket', { status: 400 });
  }

  broadcast(message: string) {
    for (const ws of this.sessions) {
      try {
        ws.send(message);
      } catch {
        this.sessions.delete(ws);
      }
    }
  }
}
