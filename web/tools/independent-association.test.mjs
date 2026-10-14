import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
const hook = registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier.startsWith('./') && !/\.[a-z]+$/.test(specifier)) return nextResolve(`${specifier}.ts`, context);
  return nextResolve(specifier, context);
} });
const { associationResultSchema, associationEvidenceSchema, verifyAssociation } = await import('../src/independent-review/association-contracts.ts');
const { loadAssociation, saveAssociation } = await import('../src/independent-review/association-storage.ts');
hook.deregister();
const file = process.env.INDEPENDENT_ASSOCIATION_EVIDENCE;
if (!file) throw new Error('Requires retained actual association HTTP evidence');
const success = JSON.parse(readFileSync(file, 'utf8'));
const owner = { userId: success.result.receipt.supervisorId, warehouseId: success.result.receipt.warehouseId, relayId: success.result.receipt.relayId };

test('actual association binds original request, complete context and physical evidence', async () => {
  await verifyAssociation(associationResultSchema.parse(success.result), owner, success.body);
});
test('changed target, hidden context, physical reference and foreign original are refused', async () => {
  for (const mutate of [r => { r.receipt.targetOrderId = crypto.randomUUID(); }, r => { r.originalContext.hiddenFact = 'changed'; },
    r => { r.physicalEvidence.receipt.receiptId = crypto.randomUUID(); }, r => { r.physicalEvidence.evidence.reason = 'changed'; }]) {
    const result = structuredClone(success.result); mutate(result);
    await assert.rejects(verifyAssociation(associationResultSchema.parse(result), owner, success.body));
  }
  await assert.rejects(verifyAssociation(success.result, { ...owner, relayId: crypto.randomUUID() }, success.body));
  await assert.rejects(verifyAssociation(success.result, owner, { ...success.body, reason: 'changed' }));
});
test('response cannot grant collection, stock, processing, release or deletion', () => {
  for (const field of ['newCollectionAuthorized', 'stockAvailable', 'businessProcessed', 'releaseAllowed', 'localDeletionAllowed']) {
    const value = structuredClone(success.result); value.receipt[field] = true;
    assert.equal(associationResultSchema.safeParse(value).success, false);
  }
});
test('candidate exclusions must be unique, nonempty and exclude the selected target', () => {
  const row = { orderId: crypto.randomUUID(), reason: 'Other vehicle' };
  for (const rows of [[row, row], [{ ...row, reason: ' ' }], [{ ...row, orderId: success.body.targetOrderId }]])
    assert.equal(associationEvidenceSchema.safeParse({ ...success.body, candidateExclusions: rows }).success, false);
});
test('pending original intent survives reload and is isolated by identity, warehouse and relay', () => {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const saved = { ...owner, version: 1, command: { key: success.key, epoch: '1', body: success.body, receipt: null } };
  saveAssociation(saved); assert.deepEqual(loadAssociation(owner), saved);
  for (const field of ['userId', 'warehouseId', 'relayId']) assert.equal(loadAssociation({ ...owner, [field]: crypto.randomUUID() }).command, null);
  const name = [...values.keys()][0]; values.set(name, JSON.stringify({ ...saved, userId: crypto.randomUUID() }));
  assert.throws(() => loadAssociation(owner), /无法读取原关联请求/);
});
test('failed persistence readback blocks sending an unrecorded intent', () => {
  globalThis.localStorage = { getItem: () => null, setItem: () => {} };
  assert.throws(() => saveAssociation({ ...owner, version: 1, command: { key: success.key, epoch: '1', body: success.body, receipt: null } }), /持久化回读失败/);
});
