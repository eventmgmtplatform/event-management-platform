/** UUID v4 for operation IDs, including HTTP lab hosts without randomUUID. */
export function createUuid(cryptoApi: Pick<Crypto,'getRandomValues'> & Partial<Pick<Crypto,'randomUUID'>> = globalThis.crypto): string {
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID();
  // getRandomValues is also available outside secure contexts. Keep cryptographic
  // randomness for idempotency keys; never substitute Math.random or timestamps.
  if (typeof cryptoApi?.getRandomValues !== 'function') throw new Error('El navegador no permite generar identificadores seguros.');
  const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, b => b.toString(16).padStart(2,'0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}
