import { describe, expect, it } from 'vitest';
import { GroupChatDO } from '../src/realtime/GroupChatDO';
import { Env } from '../src/env';
import { CHAT_RATE_LIMIT_MAX_BURST, CHAT_RATE_LIMIT_MAX_PER_WINDOW, consumeChatRateLimit, MAX_CHAT_MESSAGE_CHARACTERS, validateChatMessageText } from '../src/services/chat';
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
});
