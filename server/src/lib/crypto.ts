export function generateRandomHex(bytes: number = 16): string {
  const array = new Uint8Array(bytes);
  crypto.getRandomValues(array);
  return Array.from(array).map(b => b.toString(16).padStart(2, '0')).join('');
}

export async function hashPassword(
  password: string,
  secretPepper: string,
  saltHex?: string
): Promise<{ hash: string; salt: string }> {
  if (!secretPepper) {
    throw new Error('Server secret pepper is required for password hashing');
  }
  const salt = saltHex || generateRandomHex(16);
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    enc.encode(password + secretPepper),
    { name: 'PBKDF2' },
    false,
    ['deriveBits', 'deriveKey']
  );
  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt: enc.encode(salt),
      iterations: 100000,
      hash: 'SHA-256',
    },
    keyMaterial,
    256
  );
  const hashArray = Array.from(new Uint8Array(derivedBits));
  const derivedHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  return {
    hash: `pbkdf2$100000$${salt}$${derivedHex}`,
    salt,
  };
}

export async function verifyPassword(
  password: string,
  storedHash: string,
  secretPepper: string
): Promise<boolean> {
  if (!secretPepper) {
    throw new Error('Server secret pepper is required for password verification');
  }
  if (storedHash.startsWith('pbkdf2$')) {
    const parts = storedHash.split('$');
    if (parts.length !== 4) return false;
    const [, , salt, expectedHex] = parts;
    const { hash } = await hashPassword(password, secretPepper, salt);
    const computedHex = hash.split('$')[3];
    return computedHex === expectedHex;
  }
  // Backward compatibility with legacy SHA-256
  const legacyHash = await hashString(password, secretPepper);
  return storedHash === legacyHash;
}

export async function hashString(input: string, salt: string = ''): Promise<string> {
  const enc = new TextEncoder();
  const data = enc.encode(input + salt);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

export function generateRecoveryCode(): string {
  const chars = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
  let result = '';
  const array = new Uint8Array(16);
  crypto.getRandomValues(array);
  for (let i = 0; i < 16; i++) {
    if (i > 0 && i % 4 === 0) result += '-';
    result += chars[array[i] % chars.length];
  }
  return result;
}

export function generateRandomToken(): string {
  return generateRandomHex(32);
}
