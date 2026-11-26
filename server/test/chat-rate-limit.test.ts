import { describe, expect, it } from 'vitest';
import { GroupChatDO } from '../src/realtime/GroupChatDO';
import { Env } from '../src/env';
import { CHAT_RATE_LIMIT_MAX_BURST, CHAT_RATE_LIMIT_MAX_PER_WINDOW, consumeChatRateLimit, getChatFrameByteLength, MAX_CHAT_BODY_BYTES, MAX_CHAT_FRAME_BYTES, MAX_CHAT_MESSAGE_CHARACTERS, readChatRequestBody, validateChatMessageText } from '../src/services/chat';
class MemoryStorage {
  private values = new Map<string, unknown>();
  async get<T = unknown>(key: string): Promise<T | undefined> { return this.values.get(key) as T | undefined; }
  async put<T = unknown>(key: string, value: T): Promise<void> { this.values.set(key, value); }
}
function quotaRequest(userId: string) { return new Request('http://internal/rate-limit', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId }) }); }
describe('group chat guardrails', () => {
  it('validates text type, emptiness and Unicode character boundaries', () => {
    expect(validateChatMessageText('رسالة قصيرة')).toMatchObject({ valid: true });
    expect(validateChatMessageText('x'.repeat(MAX_CHAT_MESSAGE_CHARACTERS))).toMatchObject({ valid: true });
    expect(validateChatMessageText('😀'.repeat(MAX_CHAT_MESSAGE_CHARACTERS))).toMatchObject({ valid: true });
    expect(validateChatMessageText('x'.repeat(MAX_CHAT_MESSAGE_CHARACTERS + 1))).toMatchObject({ valid: false, code: 'MESSAGE_TOO_LONG' });
    expect(validateChatMessageText(123)).toMatchObject({ valid: false, code: 'INVALID_INPUT' });
    expect(validateChatMessageText('  ')).toMatchObject({ valid: false, code: 'INVALID_INPUT' });
  });
  it('enforces burst quota independently per user', async () => {
    const storage = new MemoryStorage(); const now = 1_900_000_000_000;
    for (let i = 0; i < CHAT_RATE_LIMIT_MAX_BURST; i++) expect(await consumeChatRateLimit(storage, 'student-1', now + i * 100)).toBe(true);
    expect(await consumeChatRateLimit(storage, 'student-1', now + 900)).toBe(false);
    expect(await consumeChatRateLimit(storage, 'student-2', now + 900)).toBe(true);
  });
  it('enforces the per-minute ceiling across separated bursts', async () => {
    const storage = new MemoryStorage(); const now = 1_900_000_000_000; let accepted = 0;
    for (const start of [0, 15_000, 30_000, 45_000]) for (let i = 0; i < CHAT_RATE_LIMIT_MAX_BURST; i++) if (await consumeChatRateLimit(storage, 'student-1', now + start + i * 100)) accepted++;
    expect(accepted).toBe(CHAT_RATE_LIMIT_MAX_PER_WINDOW); expect(await consumeChatRateLimit(storage, 'student-1', now + 45_900)).toBe(false);
  });
  it('shares persisted quota through the Durable Object bridge across instances', async () => {
    const storage = new MemoryStorage(); const state = { storage } as any;
    const one = new GroupChatDO(state, {} as Env); const two = new GroupChatDO(state, {} as Env);
    for (let i = 0; i < CHAT_RATE_LIMIT_MAX_BURST; i++) expect((await one.fetch(quotaRequest('student-1'))).status).toBe(200);
    expect((await two.fetch(quotaRequest('student-1'))).status).toBe(429);
    expect((await two.fetch(quotaRequest('student-2'))).status).toBe(200);
  });
  it('cancels an oversized streamed REST body as soon as it crosses the byte limit', async () => {
    let pullCount = 0;
    let cancelled = false;
    const body = new ReadableStream<Uint8Array>({
      pull(controller) {
        pullCount++;
        if (pullCount === 1) controller.enqueue(new Uint8Array(MAX_CHAT_BODY_BYTES + 1));
        else controller.close();
      },
      cancel() { cancelled = true; },
    });
    const result = await readChatRequestBody(body);
    expect(result).toEqual({ ok: false, reason: 'TOO_LARGE' });
    expect(pullCount).toBe(1);
    expect(cancelled).toBe(true);
  });
  it('counts binary WebSocket frame bytes before decoding', () => {
    expect(getChatFrameByteLength('😀')).toBe(4);
    expect(getChatFrameByteLength(new Uint8Array(MAX_CHAT_FRAME_BYTES + 1).buffer)).toBe(MAX_CHAT_FRAME_BYTES + 1);
  });
  it('serializes concurrent quota checks inside one Durable Object instance', async () => {
    const storage = new MemoryStorage(); const object = new GroupChatDO({ storage } as any, {} as Env);
    const responses = await Promise.all(Array.from({ length: 24 }, () => object.fetch(quotaRequest('student-concurrent'))));
    expect(responses.filter(response => response.status === 200)).toHaveLength(CHAT_RATE_LIMIT_MAX_BURST);
    expect(responses.filter(response => response.status === 429)).toHaveLength(24 - CHAT_RATE_LIMIT_MAX_BURST);
  });
  it('fails closed when Durable Object rate-limit storage cannot be read or written', async () => {
    const readFailure = new GroupChatDO({ storage: { async get() { throw new Error('storage read failed'); }, async put() {} } } as any, {} as Env);
    const writeFailure = new GroupChatDO({ storage: { async get() { return undefined; }, async put() { throw new Error('storage write failed'); } } } as any, {} as Env);
    for (const object of [readFailure, writeFailure]) {
      const response = await object.fetch(quotaRequest('student-1'));
      expect(response.status).toBe(503);
      expect(await response.json()).toMatchObject({ success: false, code: 'CHAT_RATE_LIMIT_UNAVAILABLE' });
    }
  });
});
