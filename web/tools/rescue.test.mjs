import assert from 'node:assert/strict';
import test from 'node:test';
import { randomUUID } from 'node:crypto';
import { fixture } from './rescue-fixture.mjs';
const { materialSchema, newApproval } = await import('../src/rescue/contracts.ts');
const { loadCommand, saveCommand, lockCommand, archiveRejectedApproval } = await import('../src/rescue/pending.ts');
const { sendCommand } = await import('../src/rescue/commands.ts');
const { connection } = await import('../src/connection.ts');

test.beforeEach(() => { connection.state = 'ok'; });
test('original scope material preserves the source and disabled deletion', () => {
  const f = fixture(); assert.deepEqual(materialSchema.parse(f.material).orderScope, f.scope);
  assert.equal(materialSchema.safeParse({ ...f.material, localDeletionAllowed: true }).success, false);
});
for (const corruption of ['duplicate', 'self', 'scope', 'state', 'grant']) test(`material rejects ${corruption}`, () => {
  const f = fixture(); const material = structuredClone(f.material); const doc = structuredClone(f.document);
  if (corruption === 'duplicate') { doc.orderScope.push(doc.orderScope[0]); material.orderScope = doc.orderScope; }
  if (corruption === 'self') doc.rescuerId = doc.operatorId;
  if (corruption === 'scope') material.orderScope[0].source = 'FORMAL';
  if (corruption === 'state') material.status = 'AUTHORIZED';
  if (corruption === 'grant') {
    material.status = 'REVOKED'; material.authorization = f.receipt;
    material.revocation = { grantId: randomUUID(), signedRevocation: 'original-revocation' };
  }
  material.originalText = JSON.stringify(doc);
  assert.equal(materialSchema.safeParse(material).success, false);
});
test('new finite approval cannot persist an already expired intent', () => {
  fixture(); assert.throws(() => newApproval({ expiresAt: '2000-01-01T00:00:00Z', reason: 'expired' }), /到期时间/);
  assert.equal(newApproval({ expiresAt: null, reason: 'explicit permanent' }).expiresAt, null);
});
test('reload retains original bytes and recovery epoch', () => {
  const f = fixture(); saveCommand(f.approval); assert.deepEqual(loadCommand(f.owner, 'APPROVE'), f.approval);
  assert.throws(() => saveCommand({ ...f.approval, body: JSON.stringify({ expiresAt: null, reason: 'edited' }) }), /不替换/);
  assert.throws(() => saveCommand({ ...f.approval, epoch: '3' }), /不替换/);
});
test('another owner cannot consume the original intent', () => {
  const f = fixture(); saveCommand(f.approval);
  assert.equal(loadCommand({ ...f.owner, userId: randomUUID() }, 'APPROVE'), null);
  const [name, raw] = [...f.entries][0]; f.entries.set(name, JSON.stringify({ ...JSON.parse(raw), userId: randomUUID() }));
  assert.throws(() => loadCommand(f.owner, 'APPROVE'), /身份不匹配/);
});
test('saved revocation cannot change the original grant', () => {
  const f = fixture(); saveCommand(f.revocation);
  assert.throws(() => saveCommand({ ...f.revocation, grantId: randomUUID() }), /不替换/);
});
test('saved success receipt cannot disappear or be replaced', () => {
  const f = fixture(); saveCommand({ ...f.approval, receipt: f.receipt });
  assert.throws(() => saveCommand(f.approval), /不替换/);
  assert.throws(() => saveCommand({ ...f.approval, receipt: { ...f.receipt, grantId: randomUUID() } }), /不替换/);
});
for (const failure of ['quota', 'silent', 'locks']) test(`persistence ${failure} prevents command work`, async () => {
  const f = fixture();
  if (failure === 'quota') localStorage.setItem = () => { throw new Error('quota'); };
  if (failure === 'silent') localStorage.setItem = () => {};
  if (failure === 'locks') Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined });
  await assert.rejects(lockCommand(f.owner, 'APPROVE', async () => { saveCommand(f.approval); await sendCommand({ pending: f.approval, material: f.material, assertCurrent() {} }); }));
  assert.equal(f.calls.length, 0);
});
test('lost response followed by reload repeats exact body/key/epoch', async () => {
  const f = fixture(); saveCommand(f.approval); f.transport.fail = new TypeError('lost');
  await assert.rejects(sendCommand({ pending: f.approval, material: f.material, assertCurrent() {} }));
  f.transport.fail = null; const pending = loadCommand(f.owner, 'APPROVE');
  await sendCommand({ pending, material: f.material, assertCurrent() {} });
  assert.deepEqual(f.calls[1], f.calls[0]); assert.equal(f.calls[0].epoch, '2');
  assert.deepEqual(loadCommand(f.owner, 'APPROVE').receipt, f.receipt);
});
test('scope corruption cannot POST to a different request or grant', async () => {
  const f = fixture();
  await assert.rejects(sendCommand({ pending: f.approval, material: { ...f.material, request: { ...f.material.request, requestId: randomUUID() } }, assertCurrent() {} }), /申请/);
  await assert.rejects(sendCommand({ pending: f.revocation, material: { ...f.material, authorization: { ...f.receipt, grantId: randomUUID() } }, assertCurrent() {} }), /原授权/);
  assert.equal(f.calls.length, 0);
});
test('identity changed during csrf read blocks POST', async () => {
  const f = fixture(); saveCommand(f.approval);
  await assert.rejects(sendCommand({ pending: f.approval, material: f.material, assertCurrent() { throw new Error('changed'); } }), /changed/);
  assert.equal(f.calls.length, 0); assert.deepEqual(loadCommand(f.owner, 'APPROVE'), f.approval);
});
test('mismatched revocation receipt preserves the unknown original', async () => {
  const f = fixture(); saveCommand(f.revocation); f.transport.result = { grantId: randomUUID(), signedRevocation: 'wrong' };
  await assert.rejects(sendCommand({ pending: f.revocation, material: { ...f.material, authorization: f.receipt }, assertCurrent() {} }), /原授权/);
  assert.equal(loadCommand(f.owner, 'REVOKE').receipt, null);
});
test('only a known expiry rejection can be archived for a fresh approval', async () => {
  const f = fixture(); saveCommand(f.approval); assert.throws(() => archiveRejectedApproval(f.owner), /尚未明确拒绝/);
  f.transport.fail = { status: 400, code: 'RESCUE_EXPIRY_INVALID' };
  await assert.rejects(sendCommand({ pending: f.approval, material: f.material, assertCurrent() {} }));
  assert.equal(loadCommand(f.owner, 'APPROVE').rejectedExpiry, true);
  archiveRejectedApproval(f.owner); assert.equal(loadCommand(f.owner, 'APPROVE'), null);
  assert.equal([...f.entries.values()].map(JSON.parse)[0].body, f.approval.body);
});
test('failed rejection archive cannot release the original intent', () => {
  const f = fixture(); saveCommand({ ...f.approval, rejectedExpiry: true }); localStorage.setItem = () => {};
  assert.throws(() => archiveRejectedApproval(f.owner), /未完整保留/);
  assert.equal(loadCommand(f.owner, 'APPROVE').key, f.approval.key);
});
test('unrelated server denial never permits a new approval key', async () => {
  const f = fixture(); saveCommand(f.approval); f.transport.fail = { status: 403, code: 'FORBIDDEN' };
  await assert.rejects(sendCommand({ pending: f.approval, material: f.material, assertCurrent() {} }));
  assert.throws(() => archiveRejectedApproval(f.owner), /尚未明确拒绝/);
});
