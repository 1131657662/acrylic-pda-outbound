import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
const hook = registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier.startsWith('./') && !/\.[a-z]+$/.test(specifier)) return nextResolve(`${specifier}.ts`, context);
  return nextResolve(specifier, context);
} });
const { conflictResultSchema, conflictEvidenceSchema, verifyConflict } = await import('../src/independent-review/conflict-contracts.ts');
const { loadConflict, saveConflict } = await import('../src/independent-review/conflict-storage.ts');
hook.deregister();
const file = process.env.INDEPENDENT_CONFLICT_EVIDENCE;
if (!file) throw new Error('Requires retained actual conflict HTTP evidence');
const success = JSON.parse(readFileSync(file, 'utf8'));
const owner = { userId: success.result.receipt.supervisorId, warehouseId: success.result.receipt.warehouseId, relayId: success.result.receipt.relayId };

test('actual decision binds selected original and every physical peer', async () => {
  await verifyConflict(conflictResultSchema.parse(success.result), owner, success.body);
  await assert.rejects(verifyConflict(success.result, { ...owner, warehouseId: crypto.randomUUID() }, success.body));
  await assert.rejects(verifyConflict(success.result, owner, { ...success.body, reason: 'changed' }));
});
test('full peer references require nonempty unique original identifiers', () => {
  const row = success.body.peerChecks[0];
  for (const peers of [[], [row, row], [{ ...row, physicalCheckId: '' }]])
    assert.equal(conflictEvidenceSchema.safeParse({ ...success.body, peerChecks: peers }).success, false);
});
test('pending conflict intent survives reload and remains isolated by all owner fields', () => {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const saved = { ...owner, version: 1, command: { key: success.key, epoch: '1', body: success.body, receipt: null } };
  saveConflict(saved); assert.deepEqual(loadConflict(owner), saved);
  for (const field of ['userId', 'warehouseId', 'relayId']) assert.equal(loadConflict({ ...owner, [field]: crypto.randomUUID() }).command, null);
  const name = [...values.keys()][0]; values.set(name, JSON.stringify({ ...saved, warehouseId: crypto.randomUUID() }));
  assert.throws(() => loadConflict(owner), /无法读取原冲突请求/);
});
test('unreadable persistence prevents sending and foreign receipt cannot replace original', () => {
  globalThis.localStorage = { getItem: () => null, setItem: () => {} };
  const saved = { ...owner, version: 1, command: { key: success.key, epoch: '1', body: success.body, receipt: null } };
  assert.throws(() => saveConflict(saved), /持久化回读失败/);
  assert.throws(() => saveConflict({ ...saved, command: { ...saved.command, receipt: { ...success.result.receipt, supervisorId: crypto.randomUUID() } } }), /身份不一致/);
});
