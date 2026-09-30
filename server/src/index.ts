import { Env } from './env';
import { handleApiRoute } from './router';
import { checkRateLimit } from './middleware/rateLimit';
import { errorResponse, jsonResponse } from './lib/response';
import { getUserFromToken } from './middleware/auth';
import { requireGroupMember } from './middleware/permissions';
export { GroupChatDO } from './realtime/GroupChatDO';

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    // Handle CORS Preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, PATCH, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Device-Id, X-App-Version',
        },
      });
    }

    const url = new URL(request.url);

    // WebSocket upgrade for Group Chat with strict authentication & membership validation
    if (url.pathname.startsWith('/ws/groups/') || url.pathname.startsWith('/wss/groups/')) {
      const groupId = url.pathname.split('/')[3];
      if (!groupId) {
        return errorResponse('INVALID_GROUP', 'معرف المجموعة غير محدد');
      }

      // Check token from Query Parameter (?token=...) or Authorization header
      const token = url.searchParams.get('token') ||
        (request.headers.get('Authorization')?.startsWith('Bearer ') ? request.headers.get('Authorization')!.substring(7) : null);

      if (!token) {
        return errorResponse('UNAUTHORIZED', 'يرجى تقديم رمز الدخول للاتصال بالمحادثة المباشرة', 401);
      }

      try {
        const user = await getUserFromToken(token, env);
        if (!user) {
          return errorResponse('UNAUTHORIZED', 'رمز الدخول غير صالح أو منتهي الصلاحية', 401);
        }

        const memberCheck = await requireGroupMember(user, groupId, env.DB);
        if (memberCheck) {
          return memberCheck;
        }

        const gradeNameMap: Record<number, string> = {
          7: 'سابع', 8: 'ثامن', 9: 'تاسع', 10: 'أول ثانوي', 11: 'ثاني ثانوي', 12: 'ثالث ثانوي'
        };
        const gradeSection = `${gradeNameMap[user.gradeId] || user.gradeId} — ${user.sectionId}`;

        // Create new request with verified identity headers injected server-side
        const modifiedHeaders = new Headers(request.headers);
        modifiedHeaders.set('X-User-Id', user.userId);
        modifiedHeaders.set('X-User-Name', encodeURIComponent(user.fullName));
        modifiedHeaders.set('X-User-Grade-Section', encodeURIComponent(gradeSection));
        modifiedHeaders.set('X-Group-Id', groupId);

        const doRequest = new Request(request.url, {
          headers: modifiedHeaders,
        });

        const doId = env.CHAT.idFromName(groupId);
        const doStub = env.CHAT.get(doId);
        return doStub.fetch(doRequest);
      } catch (err: any) {
        return errorResponse('AUTH_ERROR', err?.message || 'خطأ أثناء مصادقة جلسة المحادثة', 500);
      }
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
