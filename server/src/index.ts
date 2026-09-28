import { Env } from './env';
import { handleApiRoute } from './router';
import { checkRateLimit } from './middleware/rateLimit';
import { errorResponse, jsonResponse } from './lib/response';
export { GroupChatDO } from './realtime/GroupChatDO';

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    // Handle CORS Preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Device-Id, X-App-Version',
        },
      });
    }

    const url = new URL(request.url);

    // WebSocket upgrade for Group Chat
    if (url.pathname.startsWith('/ws/groups/')) {
      const groupId = url.pathname.split('/')[3];
      if (!groupId) {
        return errorResponse('INVALID_GROUP', 'معرف المجموعة غير محدد');
      }
      const doId = env.CHAT.idFromName(groupId);
      const doStub = env.CHAT.get(doId);
      return doStub.fetch(request);
    }

    // Apply Rate Limiting
    const rateLimitError = checkRateLimit(request);
    if (rateLimitError) return rateLimitError;

    try {
      return await handleApiRoute(request, env);
    } catch (err: any) {
      console.error('Server error:', err);
      return errorResponse('INTERNAL_SERVER_ERROR', err?.message || 'حدث خطأ في الخادم', 500);
    }
  },
};
