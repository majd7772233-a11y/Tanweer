import { describe, it, expect } from 'vitest';
import { hashPassword, verifyPassword, generateRandomHex, generateRecoveryCode } from '../src/lib/crypto';

describe('Server PBKDF2 Password Hashing & Crypto', () => {
  const pepper = 'test_server_pepper_secret_123';

  it('generates PBKDF2 hash and verifies correctly', async () => {
    const password = 'StrongPassword123!';
    const { hash, salt } = await hashPassword(password, pepper);

    expect(hash.startsWith('pbkdf2$100000$')).toBe(true);
    expect(salt.length).toBe(32);

    const isValid = await verifyPassword(password, hash, pepper);
    expect(isValid).toBe(true);

    const isWrong = await verifyPassword('WrongPassword', hash, pepper);
    expect(isWrong).toBe(false);
  });

  it('throws error when server secret pepper is missing', async () => {
    await expect(hashPassword('pass', '')).rejects.toThrow('Server secret pepper is required');
    await expect(verifyPassword('pass', 'hash', '')).rejects.toThrow('Server secret pepper is required');
  });

  it('generates unique 16-character recovery codes', () => {
    const code1 = generateRecoveryCode();
    const code2 = generateRecoveryCode();
    expect(code1).toMatch(/^[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$/);
    expect(code1).not.toBe(code2);
  });

  it('generates random hex strings', () => {
    const hex = generateRandomHex(16);
    expect(hex.length).toBe(32);
    expect(/^[0-9a-f]+$/.test(hex)).toBe(true);
  });
});
