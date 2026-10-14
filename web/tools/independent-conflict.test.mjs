import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
const hook = registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier.startsWith('./') && !/\.[a-z]+$/.test(specifier)) return nextResolve(`${specifier}.ts`, context);
  return nextResolve(specifier, context);
} });
const { conflictResultSchema, conflictEvidenceSchema, verifyConflict } = await import('../src/independent-review/conflict-contracts.ts');
const { loadConflict, saveConflict, prepareConflictExtension, sameConflictReceipt } = await import('../src/independent-review/conflict-storage.ts');
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
test('extension requires complete matching predecessor references and original receipt digest', async () => {
  if (!success.body.predecessorDecisionId) {
    assert.equal(success.result.receipt.predecessorDecisionId, undefined);
    assert.equal(success.result.originalContext.predecessorReceipt, undefined); return;
  }
  for (const field of ['predecessorDecisionId', 'predecessorReceiptSha256']) {
    const changed = structuredClone(success.result); delete changed.receipt[field];
    await assert.rejects(verifyConflict(conflictResultSchema.parse(changed), owner, success.body));
  }
  const missing = structuredClone(success.result); delete missing.originalContext.predecessorReceipt;
  await assert.rejects(verifyConflict(conflictResultSchema.parse(missing), owner, success.body));
  const changed = structuredClone(success.result);
  changed.originalContext.predecessorReceipt.taskId = crypto.randomUUID();
  await assert.rejects(verifyConflict(conflictResultSchema.parse(changed), owner, success.body));
  await assert.rejects(verifyConflict(success.result, owner, { ...success.body, predecessorDecisionId: crypto.randomUUID() }));
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
test('confirmed original is retained before extension and pending result cannot be replaced', () => {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const command = { key: success.key, epoch: '1', body: success.body, receipt: success.result.receipt };
  saveConflict({ ...owner, version: 1, command });
  const next = prepareConflictExtension(owner, success.result.receipt);
  assert.deepEqual(next.history, [command]); assert.equal(next.command, null);
  assert.deepEqual(loadConflict(owner).predecessor, success.result.receipt);
  const pending = { key: crypto.randomUUID(), epoch: '1', body: { ...success.body, predecessorDecisionId: success.result.receipt.decisionId }, receipt: null };
  saveConflict({ ...next, command: pending });
  assert.throws(() => prepareConflictExtension(owner, success.result.receipt), /结果未明/);
  assert.deepEqual(loadConflict(owner).command, pending); assert.deepEqual(loadConflict(owner).history, [command]);
  assert.throws(() => saveConflict({ ...next, command: { ...pending, body: { ...pending.body, predecessorDecisionId: crypto.randomUUID() } } }), /原前驱范围/);
  assert.throws(() => saveConflict({ ...next, history: [{ ...command, receipt: null }] }), /未明结果/);
  assert.throws(() => saveConflict({ ...next, command }), /重复原键/);
});

test('receipt field order preserves equality while any signed byte change is refused', () => {
  const receipt = success.result.receipt;
  const reordered = Object.fromEntries(Object.entries(receipt).reverse());
  assert.equal(sameConflictReceipt(receipt, reordered), true);
  for (const field of ['signedProof', 'recordedAt', 'contextSha256']) {
    const changed = { ...receipt, [field]: field === 'recordedAt' ? '2000-01-01T00:00:00.000Z' : field === 'contextSha256' ? '0'.repeat(64) : 'changed' };
    assert.equal(sameConflictReceipt(receipt, changed), false);
  }
});

test('confirmed receipt mismatch leaves the stored original command unchanged', () => {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const command = { key: success.key, epoch: '1', body: success.body, receipt: success.result.receipt };
  saveConflict({ ...owner, version: 1, command });
  assert.throws(() => prepareConflictExtension(owner, { ...command.receipt, signedProof: 'changed' }), /读取决定不一致/);
  assert.deepEqual(loadConflict(owner).command, command);
});
