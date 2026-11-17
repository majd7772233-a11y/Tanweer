import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import process from 'node:process';
import { describe, expect, it } from 'vitest';

const picomatch = createRequire(import.meta.url)('picomatch');

describe('picomatch ReDoS security backport', () => {
  it('removes nested unbounded repetition from affected single-branch extglobs', () => {
    const patterns = ['+(*)', '+(*a)', '*(*a)', '+(a*)', '+(a*b)', '*(*.js)', '+(*a)c', 'x+(*a)'];
    for (const pattern of patterns) {
      const compiled = picomatch.makeRe(pattern);
      expect(compiled.source, pattern).not.toMatch(/\)[+*]/);
    }
  });

  it('preserves ordinary matching semantics after collapsing the redundant repetition', () => {
    expect(picomatch.isMatch('aaa', '+(*a)')).toBe(true);
    expect(picomatch.isMatch('a.a', '+(*a)')).toBe(true);
    expect(picomatch.isMatch('ab', '+(*a)')).toBe(false);
    expect(picomatch.isMatch('axbayb', '+(a*b)')).toBe(true);
    expect(picomatch.isMatch('axbay', '+(a*b)')).toBe(false);
    expect(picomatch.isMatch('foo.js', '*(*.js)')).toBe(true);
    expect(picomatch.isMatch('foo.ts', '*(*.js)')).toBe(false);
  });

  it('keeps capture mode on the conservative literal fallback', () => {
    const captured = picomatch.makeRe('+(*a)', { capture: true });
    expect(captured.test('+(*a)')).toBe(true);
    expect(captured.test('aaa')).toBe(false);
  });

  it('does not hang the event loop on crafted non-matching inputs', () => {
    const childScript = [
      "const picomatch = require('picomatch');",
      "const input = 'a'.repeat(50) + 'b';",
      "for (const pattern of ['+(*a)', '*(*a)', '+(*a)c', 'x+(*a)']) {",
      '  if (picomatch.isMatch(input, pattern)) process.exitCode = 2;',
      '}',
    ].join('\n');
    const child = spawnSync(process.execPath, ['-e', childScript], {
      cwd: process.cwd(),
      encoding: 'utf8',
      timeout: 2000,
    });
    expect(child.error).toBeUndefined();
    expect(child.status).toBe(0);
  });
});