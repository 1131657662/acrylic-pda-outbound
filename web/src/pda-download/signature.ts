// The Android/openssl manifest uses ASN.1 DER; Web Crypto expects fixed-width r || s.
export function decodeBase64(value: string): Uint8Array<ArrayBuffer> {
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)) throw new Error('Invalid base64');
  return Uint8Array.from(atob(value), char => char.charCodeAt(0));
}
function scalar(bytes: Uint8Array, start: number): { value: Uint8Array; next: number } {
  const length = bytes[start + 1];
  if (bytes[start] !== 2 || length === undefined || length < 1 || length > 33) throw new Error('Invalid scalar');
  const value = bytes.slice(start + 2, start + 2 + length);
  if (value.length !== length || value[0]! >= 128) throw new Error('Invalid integer');
  if (length > 1 && value[0] === 0 && value[1]! < 128) throw new Error('Noncanonical integer');
  const unsigned = value[0] === 0 ? value.slice(1) : value;
  if (unsigned.length > 32) throw new Error('Oversized integer');
  return { value: unsigned, next: start + 2 + length };
}
function rawSignature(der: Uint8Array): Uint8Array<ArrayBuffer> {
  if (der[0] !== 48 || der[1] !== der.length - 2) throw new Error('Invalid ECDSA sequence');
  const r = scalar(der, 2); const s = scalar(der, r.next);
  if (s.next !== der.length) throw new Error('Trailing signature bytes');
  const raw = new Uint8Array(64);
  raw.set(r.value, 32 - r.value.length); raw.set(s.value, 64 - s.value.length);
  return raw;
}
export async function verifySignature(publicKey: string, manifest: { payload: string; signature: string }): Promise<Uint8Array<ArrayBuffer>> {
  const key = await crypto.subtle.importKey('spki', decodeBase64(publicKey), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']);
  const payload = decodeBase64(manifest.payload);
  const valid = await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, rawSignature(decodeBase64(manifest.signature)), payload);
  if (!valid) throw new Error('Invalid signature');
  return payload;
}
