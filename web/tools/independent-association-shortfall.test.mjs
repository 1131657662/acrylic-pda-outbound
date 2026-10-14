import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
const hook = registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier.startsWith('./') && !/\.[a-z]+$/.test(specifier)) return nextResolve(`${specifier}.ts`, context);
  return nextResolve(specifier, context);
} });
const { associationShortfall, approveAssociationShortfall, checkAssociationTargetProducts } = await import('../src/independent-review/association-shortfall.ts');
const { associationResultSchema, verifyAssociation } = await import('../src/independent-review/association-contracts.ts');
const { loadAssociation, saveAssociation } = await import('../src/independent-review/association-storage.ts');
hook.deregister();
const file = process.env.INDEPENDENT_ASSOCIATION_EVIDENCE;
if (!file) throw new Error('Requires actual shortfall association HTTP response');
const success = JSON.parse(readFileSync(file, 'utf8'));
const context = success.result.originalContext;
const target = context.candidates.items.find(row => row.id === success.body.targetOrderId);
if (!target) throw new Error('Actual selected candidate is missing');
const data = { original: context.original, summary: { activeTotal: String(context.inventory.length) } };
const owner = { userId: success.result.receipt.supervisorId, warehouseId: success.result.receipt.warehouseId, relayId: success.result.receipt.relayId };

test('actual signed request approves both exact shortages without changing original facts', async () => {
  const facts = associationShortfall(data, target);
  assert.deepEqual(facts, { original: '2', target: '2', needed: true });
  checkAssociationTargetProducts(context, target);
  const approval = approveAssociationShortfall({ facts, confirmed: true, reason: success.body.shortfallApproval.reason });
  assert.deepEqual(approval, { shortfallApproval: success.body.shortfallApproval });
  await verifyAssociation(associationResultSchema.parse(success.result), owner, success.body);
});
test('a checked box and nonempty bounded supervisor explanation are required', () => {
  const facts = associationShortfall(data, target);
  for (const choice of [{ confirmed: false, reason: 'approved' }, { confirmed: true, reason: ' ' }, { confirmed: true, reason: 'x'.repeat(501) }])
    assert.throws(() => approveAssociationShortfall({ facts, ...choice }), /须显式确认/);
});
test('zero shortages leave the old request body unchanged', () => {
  const ordinary = structuredClone(data); ordinary.original.closure.shortfall = '0';
  const candidate = structuredClone(target); candidate.plannedTotal = '2'; candidate.products[0].plannedCount = '2';
  const facts = associationShortfall(ordinary, candidate);
  assert.deepEqual(facts, { original: '0', target: '0', needed: false });
  assert.deepEqual(approveAssociationShortfall({ facts, confirmed: false, reason: '' }), {});
});
test('overfill, duplicate plans, invalid counts and inconsistent closure block approval', () => {
  for (const mutate of [c => { c.plannedTotal = '1'; }, c => { c.plannedTotal = '999999'; },
    c => { c.products.push({ ...c.products[0] }); }, c => { c.products[0].plannedCount = '01'; }]) {
    const candidate = structuredClone(target); mutate(candidate); assert.throws(() => associationShortfall(data, candidate));
  }
  const original = structuredClone(data); original.original.closure.active = '3'; assert.throws(() => associationShortfall(original, target));
  const closed = structuredClone(context); closed.original.closure.plannedTotal = '999';
  assert.throws(() => checkAssociationTargetProducts(closed, target), /不自洽/);
});
test('product mismatch and per product overfill cannot use aggregate shortage approval', () => {
  const foreign = structuredClone(target); foreign.products[0].model = 'OTHER';
  assert.throws(() => checkAssociationTargetProducts(context, foreign), /异产品/);
  const fewer = structuredClone(target); fewer.products[0].plannedCount = '1';
  assert.throws(() => checkAssociationTargetProducts(context, fewer), /超过目标产品计划/);
});
test('the original key, epoch, exact shortages and approval survive durable reload', () => {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const saved = { ...owner, version: 1, command: { key: success.key, epoch: '1', body: success.body, receipt: null } };
  saveAssociation(saved); assert.deepEqual(loadAssociation(owner), saved);
});
