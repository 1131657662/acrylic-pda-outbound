import { registerHooks } from 'node:module';
import { randomUUID } from 'node:crypto';

registerHooks({ resolve(specifier, context, next) {
  if (context.parentURL?.includes('/src/') && specifier.startsWith('.') && !specifier.endsWith('.ts'))
    return next(`${specifier}.ts`, context);
  return next(specifier, context);
} });
export function fixture() {
  const owner = { userId: randomUUID(), warehouseId: randomUUID(), requestId: randomUUID() };
  const scope = [{ source: 'TEMP', localOrderId: randomUUID() }];
  const document = { purpose: 'PDA_RESCUE_REQUEST_V1', requestKey: randomUUID(), deviceId: randomUUID(),
    operatorId: randomUUID(), warehouseId: owner.warehouseId, rescuerId: randomUUID(), orderScope: scope, reason: 'Original rescue' };
  const material = { request: { requestId: owner.requestId, requestSha256: 'a'.repeat(64) }, originalText: JSON.stringify(document),
    deviceSignature: 'original-signature', orderScope: scope, status: 'REQUESTED', authorization: null, revocation: null, localDeletionAllowed: false };
  const approval = { ...owner, action: 'APPROVE', key: randomUUID(), epoch: '2', receipt: null, rejectedExpiry: false,
    body: JSON.stringify({ expiresAt: null, reason: 'Original explicit permanent read' }) };
  const grantId = randomUUID(); const receipt = { grantId, signedAuthorization: 'original-server-authority' };
  const revocation = { ...owner, action: 'REVOKE', grantId, key: randomUUID(), epoch: '2', receipt: null,
    body: JSON.stringify({ reason: 'Original revoke' }) };
  const entries = new Map(); const calls = [];
  globalThis.localStorage = {
    getItem: name => entries.get(name) ?? null,
    setItem: (name, raw) => entries.set(name, raw),
    removeItem: name => entries.delete(name),
  };
  Object.defineProperty(globalThis.navigator, 'locks', { configurable: true, value: { request: async (_key, work) => work() } });
  const transport = { fail: null, result: receipt };
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/auth/csrf')) return Response.json({ code: 'OK', message: '', requestId: randomUUID(),
      serverTime: new Date().toISOString(), serverRecoveryEpoch: '3', serviceMode: 'NORMAL', data: { headerName: 'X-CSRF-TOKEN', token: 'test-only' } });
    calls.push({ url, body: options.body, key: options.headers.get('Idempotency-Key'), epoch: options.headers.get('X-Server-Recovery-Epoch') });
    if (transport.fail instanceof Error) throw transport.fail;
    return Response.json({ code: transport.fail?.code ?? 'OK', message: 'Isolated response', requestId: randomUUID(),
      serverTime: new Date().toISOString(), serverRecoveryEpoch: '3', serviceMode: 'NORMAL', data: transport.result }, { status: transport.fail?.status ?? 200 });
  };
  return { owner, scope, document, material, approval, revocation, receipt, entries, calls, transport };
}
