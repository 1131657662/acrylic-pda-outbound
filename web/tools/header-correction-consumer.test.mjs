import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import { readFileSync } from 'node:fs';
import { resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
import test from 'node:test';
registerHooks({ resolve(specifier, context, next) {
  if (context.parentURL?.includes('/web/src/corrections/') && specifier.startsWith('./') && !specifier.endsWith('.ts'))
    return next(specifier + '.ts', context);
  return next(specifier, context);
} });
const { commandSchema, receiptSchema } = await import('../src/corrections/header-contracts.ts');
const { canonicalHeaderCommand, loadHeaderPending, saveHeaderPending, verifyHeaderResponse } = await import('../src/corrections/header-pending.ts');
if (!process.env.P06_HEADER_ORIGINALS) throw new Error('Set P06_HEADER_ORIGINALS to the preserved real HTTP command/receipt directory');
const root = pathToFileURL(resolve(process.env.P06_HEADER_ORIGINALS) + sep);
const command = commandSchema.parse(JSON.parse(readFileSync(new URL('confirmed-command.json', root), 'utf8')));
const receipt = receiptSchema.parse(JSON.parse(readFileSync(new URL('withdrawal.json', root), 'utf8')));
const owner = { actor: receipt.actor.principalId, warehouse: receipt.actor.warehouseId, order: receipt.orderId };
const original = { ...owner, body: canonicalHeaderCommand(command), receipt: null, rejection: null };
function storage() {
  const entries = new Map();
  globalThis.localStorage = { getItem: key => entries.get(key) ?? null, setItem: (key, value) => entries.set(key, value) };
  return entries;
}

test('actual Java canonical command digest binds the original compensated receipt', async () => {
  await verifyHeaderResponse(original, receipt);
  assert.deepEqual(receipt.businessDecision.headerRestoration.restoredFields, ['vehicle']);
});
test('body, expected version, actor, warehouse, document and response ownership tampering are rejected', async () => {
  for (const changed of [
    { ...receipt, commandSha256: '0'.repeat(64) }, { ...receipt, commandId: crypto.randomUUID() },
    { ...receipt, orderId: crypto.randomUUID() }, { ...receipt, documentId: crypto.randomUUID() },
    { ...receipt, actor: { ...receipt.actor, principalId: crypto.randomUUID() } },
    { ...receipt, actor: { ...receipt.actor, warehouseId: crypto.randomUUID() } },
    { ...receipt, expected: { ...receipt.expected, status: 'EFFECTIVE' } },
  ]) await assert.rejects(verifyHeaderResponse(original, changed));
});
test('unresolved original survives remount; completed and rejected originals remain archived', () => {
  const entries = storage(); saveHeaderPending(original);
  assert.equal(loadHeaderPending(owner).body, original.body);
  saveHeaderPending({ ...original, receipt });
  const next = { ...original, body: canonicalHeaderCommand({ ...command, commandId: crypto.randomUUID() }),
    rejection: { status: 409, message: 'Existing successor changes this field' } };
  saveHeaderPending(next);
  assert.equal(loadHeaderPending(owner).rejection.status, 409);
  const archived = [...entries.values()].map(JSON.parse).filter(value => value.receipt);
  assert.equal(archived.length, 1); assert.deepEqual(archived[0].receipt, receipt);
});
test('mismatched saved identity and unavailable storage reject before submission', () => {
  storage(); assert.throws(() => saveHeaderPending({ ...original, warehouse: crypto.randomUUID() }));
  globalThis.localStorage = { getItem: () => null, setItem: () => { throw new Error('quota'); } };
  assert.throws(() => saveHeaderPending(original), /quota/);
});
test('production capability escalation and unsigned responses cannot be consumed', () => {
  for (const changed of [{ ...receipt, capabilities: { ...receipt.capabilities, localDeletionAllowed: true } },
    { ...receipt, capabilities: { ...receipt.capabilities, releaseAllowed: true } }, { ...receipt, signedReceipt: '' }])
    assert.equal(receiptSchema.safeParse(changed).success, false);
});
