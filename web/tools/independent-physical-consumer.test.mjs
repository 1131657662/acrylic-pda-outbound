import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
import { physicalResultSchema, verifyPhysicalResult } from '../src/independent-review/physical-contracts.ts';
const hook = registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier.startsWith('./') && !specifier.endsWith('.ts')) return nextResolve(`${specifier}.ts`, context);
  return nextResolve(specifier, context);
} });
const { loadPhysical, savePhysical } = await import('../src/independent-review/physical-storage.ts');
hook.deregister();
const evidence = process.env.INDEPENDENT_PHYSICAL_EVIDENCE;
if (!evidence) throw new Error('Requires actual HTTP evidence');
const success = JSON.parse(readFileSync(`${evidence}/successful.json`, 'utf8'));
const owner = { userId: success.result.receipt.supervisorId, warehouseId: success.result.receipt.warehouseId, relayId: success.result.receipt.relayId };
test('actual signed observation binds request and complete context without capabilities', async () => {
  await verifyPhysicalResult(physicalResultSchema.parse(success.result), owner, success.evidence);
  assert.equal(success.result.receipt.localDeletionAllowed, false);
});
test('modified physical request/context and foreign owner are refused', async () => {
  const altered = structuredClone(success.result); altered.originalContext.hiddenStockVersion = 'changed';
  await assert.rejects(verifyPhysicalResult(physicalResultSchema.parse(altered), owner, success.evidence));
  await assert.rejects(verifyPhysicalResult(physicalResultSchema.parse(success.result), { ...owner, userId: crypto.randomUUID() }, success.evidence));
  await assert.rejects(verifyPhysicalResult(physicalResultSchema.parse(success.result), owner, { ...success.evidence, reason: 'changed' }));
});
test('receipt cannot claim association/stock/processing/release/deletion', () => {
  for (const flag of ['associationAuthorized', 'stockAvailable', 'businessProcessed', 'releaseAllowed', 'localDeletionAllowed']) {
    const altered = structuredClone(success.result); altered.receipt[flag] = true;
    assert.equal(physicalResultSchema.safeParse(altered).success, false);
  }
});
test('original browser key and body recover by exact actor/warehouse/original scope', () => {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const saved = { ...owner, version: 1, uploads: [], command: { key: success.key, epoch: '1', body: success.evidence, result: success.result.receipt } };
  savePhysical(saved); assert.deepEqual(loadPhysical(owner), saved);
  assert.equal(loadPhysical({ ...owner, userId: crypto.randomUUID() }).command, null);
  assert.equal(loadPhysical({ ...owner, warehouseId: crypto.randomUUID() }).command, null);
  assert.equal(loadPhysical({ ...owner, relayId: crypto.randomUUID() }).command, null);
});
