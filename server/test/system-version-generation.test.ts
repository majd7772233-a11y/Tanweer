import { afterEach, describe, expect, it, vi } from 'vitest';
import { Env } from '../src/env';
import { handleApiRoute } from '../src/router';
function makeEnv(readGeneration: () => Promise<{ value?: string } | null>): Env {
  return { DB: { prepare(_sql: string) { return { bind(..._params: unknown[]) { return { first: readGeneration }; } }; } } } as unknown as Env;
}
function request(): Request { return new Request('https://tanweer.test/api/v1/system/version', { method: 'GET' }); }
afterEach(() => vi.unstubAllGlobals());
describe('system version data-generation safety', () => {
  it('fails closed before contacting GitHub when D1 cannot read the marker', async () => {
    const fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock);
    const response = await handleApiRoute(request(), makeEnv(async () => { throw new Error('D1 unavailable'); }));
    const body = await response.json() as any;
    expect(response.status).toBe(503); expect(body.error.code).toBe('SYSTEM_GENERATION_UNAVAILABLE'); expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each([null, { value: '' }, { value: '   ' }])('does not fabricate a generation for a missing/blank D1 value', async row => {
    vi.stubGlobal('fetch', vi.fn());
    const response = await handleApiRoute(request(), makeEnv(async () => row));
    const body = await response.json() as any;
    expect(response.status).toBe(503); expect(body.error.code).toBe('SYSTEM_GENERATION_UNAVAILABLE');
  });
  it('keeps a verified D1 generation if GitHub metadata is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('GitHub unavailable')));
    const response = await handleApiRoute(request(), makeEnv(async () => ({ value: 'gen_initial_7' })));
    const body = await response.json() as any;
    expect(response.status).toBe(200); expect(body.dataGeneration).toBe('gen_initial_7');
  });
});
