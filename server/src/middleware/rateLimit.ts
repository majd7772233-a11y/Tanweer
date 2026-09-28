import { Env } from '../env';
import { errorResponse } from '../lib/response';

// In-memory rate limiting map for basic Worker protection
const ipRequestMap = new Map<string, { count: number; resetTime: number }>();

export function checkRateLimit(request: Request, maxPerMinute = 120): Response | null {
  const clientIp = request.headers.get('CF-Connecting-IP') || 'unknown';
  const now = Date.now();
  const entry = ipRequestMap.get(clientIp);

  if (!entry || now > entry.resetTime) {
    ipRequestMap.set(clientIp, { count: 1, resetTime: now + 60000 });
    return null;
  }

  entry.count++;
  if (entry.count > maxPerMinute) {
    return errorResponse('RATE_LIMIT_EXCEEDED', 'تم تجاوز معدل الطلبات المسموح به. يرجى الانتظار دقيقة واحدة.', 429);
  }

  return null;
}
