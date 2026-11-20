import { describe, expect, it } from 'vitest';
import worker from '../src/index';
import { Env } from '../src/env';

describe('WebSocket handshake rate limiting', () => {
  it('applies the per-IP request budget before WebSocket authentication', async () => {
    const clientIp = '198.51.100.41';
    const env = {} as Env;
    let response: Response | undefined;

    // The default Worker budget is 120 requests per minute. These requests have
    // no token, so a missing pre-upgrade limiter would keep returning 401.
    for (let attempt = 0; attempt < 120; attempt++) {
      response = await worker.fetch(
        new Request('https://tanweer.test/ws/groups/class_10_A', {
          headers: { 'CF-Connecting-IP': clientIp },
        }),
        env,
        {} as ExecutionContext,
      );
    }

    expect(response?.status).toBe(401);

    const blocked = await worker.fetch(
      new Request('https://tanweer.test/ws/groups/class_10_A', {
        headers: { 'CF-Connecting-IP': clientIp },
      }),
      env,
      {} as ExecutionContext,
    );

    expect(blocked.status).toBe(429);
    expect(await blocked.json()).toMatchObject({
      success: false,
      error: { code: 'RATE_LIMIT_EXCEEDED' },
    });
  });
});
