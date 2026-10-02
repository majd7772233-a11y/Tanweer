import { Env } from '../env';
import { errorResponse } from '../lib/response';

interface RateLimitRecord {
  tokens: number;
  lastRefill: number;
}

const rateLimitMap = new Map<string, RateLimitRecord>();
const MAX_MAP_SIZE = 5000;

export function checkRateLimit(request: Request, maxPerMinute = 120): Response | null {
  const clientIp = request.headers.get('CF-Connecting-IP') || request.headers.get('X-Forwarded-For') || 'unknown';
  const now = Date.now();

  // Periodic cleanup if map grows large
  if (rateLimitMap.size > MAX_MAP_SIZE) {
    for (const [key, record] of rateLimitMap.entries()) {
      if (now - record.lastRefill > 120000) {
        rateLimitMap.delete(key);
      }
    }
  }

  let record = rateLimitMap.get(clientIp);
  if (!record) {
    record = { tokens: maxPerMinute - 1, lastRefill: now };
    rateLimitMap.set(clientIp, record);
    return null;
  }

  // Refill tokens based on elapsed time (smooth leak / bucket replenishment)
  const elapsedMs = now - record.lastRefill;
  if (elapsedMs > 0) {
    const refillTokens = (elapsedMs / 60000) * maxPerMinute;
    record.tokens = Math.min(maxPerMinute, record.tokens + refillTokens);
    record.lastRefill = now;
  }

  if (record.tokens < 1) {
    return errorResponse('RATE_LIMIT_EXCEEDED', 'تم تجاوز معدل الطلبات المسموح به مؤقتاً. يُرجى الانتظار بضع ثوانٍ.', 429);
  }

  record.tokens -= 1;
  return null;
}

