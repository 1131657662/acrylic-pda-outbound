import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
registerHooks({ resolve(specifier, context, next) {
  if (context.parentURL?.includes('/web/src/reconciliation/') && specifier.startsWith('./') && !specifier.endsWith('.ts'))
    return next(specifier + '.ts', context);
  return next(specifier, context);
} });
const { receiptSchema, commandSchema } = await import('../src/reconciliation/contracts.ts');
const { canonical, verifyResponse, savePending, loadPending } = await import('../src/reconciliation/pending.ts');
if (!process.env.P06_RECONCILE_ORIGINALS) throw new Error('Preserved actual HTTP originals required');
const root = resolve(process.env.P06_RECONCILE_ORIGINALS);
const command = commandSchema.parse(JSON.parse(readFileSync(resolve(root, 'approval-command.json'), 'utf8')));
const receipt = receiptSchema.parse(JSON.parse(readFileSync(resolve(root, 'approval-receipt.json'), 'utf8')));
const owner = { actor: receipt.actor.principalId, warehouse: receipt.actor.warehouseId };
const pending = { ...owner, body: canonical(command), receipt: null, rejection: null };
function storage() {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  return values;
}
test('actual Java original request digest binds a current-weight missing-debit signed receipt', async () => {
  await verifyResponse(pending, receipt); assert.equal(receipt.businessDecision.status, 'RESOLVED');
  assert.equal(receipt.businessDecision.quantityDelta, '-1'); assert.match(receipt.businessDecision.weightDeltaG, /^-[1-9][0-9]*$/);
});
test('mismatched body, actor, warehouse, result, case and expected facts cannot be consumed', async () => {
  for (const changed of [{ ...receipt, commandId: crypto.randomUUID() }, { ...receipt, commandSha256: '0'.repeat(64) },
    { ...receipt, actor: { ...receipt.actor, principalId: crypto.randomUUID() } },
    { ...receipt, actor: { ...receipt.actor, warehouseId: crypto.randomUUID() } }, { ...receipt, orderId: crypto.randomUUID() },
    { ...receipt, businessDecision: { ...receipt.businessDecision, caseId: crypto.randomUUID() } },
    { ...receipt, expected: { ...receipt.expected, factsSha256: '0'.repeat(64) } }]) await assert.rejects(verifyResponse(pending, changed));
});
test('original pending request and completed receipt survive remount without discarding archived bytes', () => {
  const values = storage(); savePending(pending); assert.equal(loadPending(owner).body, pending.body);
  savePending({ ...pending, receipt }); assert.deepEqual(loadPending(owner).receipt, receipt);
  const next = { ...pending, body: canonical({ ...command, commandId: crypto.randomUUID() }) }; savePending(next);
  assert.equal([...values.values()].map(JSON.parse).filter(value => value.receipt).length, 1);
});
test('unavailable durable storage and mismatched saved ownership reject before transmission', () => {
  storage(); assert.throws(() => savePending({ ...pending, warehouse: crypto.randomUUID() }));
  globalThis.localStorage = { getItem: () => null, setItem: () => { throw new Error('quota'); } };
  assert.throws(() => savePending(pending), /quota/);
});
test('capability escalation, unsigned response and mixed business commands are rejected at the boundary', () => {
  assert.equal(receiptSchema.safeParse({ ...receipt, signedReceipt: '' }).success, false);
  for (const field of Object.keys(receipt.capabilities)) assert.equal(receiptSchema.safeParse({ ...receipt,
    capabilities: { ...receipt.capabilities, [field]: true } }).success, false);
  assert.equal(commandSchema.safeParse({ ...command, dispatchId: crypto.randomUUID() }).success, false);
  assert.equal(commandSchema.safeParse({ ...command, commandType: 'RECONCILE_SUBMIT' }).success, false);
});
