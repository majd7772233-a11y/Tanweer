export function generateId(prefix: string = ''): string {
  const timestamp = Date.now().toString(36);
  const randomBytes = new Uint8Array(6);
  crypto.getRandomValues(randomBytes);
  const randomHex = Array.from(randomBytes, b => b.toString(16).padStart(2, '0')).join('');
  return prefix ? `${prefix}_${timestamp}_${randomHex}` : `${timestamp}_${randomHex}`;
}

