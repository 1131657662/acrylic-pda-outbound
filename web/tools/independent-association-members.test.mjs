import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
const hook = registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier.startsWith('./') && !/\.[a-z]+$/.test(specifier)) return nextResolve(`${specifier}.ts`, context);
  return nextResolve(specifier, context);
} });
const { associationResultSchema, associationEvidenceSchema, verifyAssociation } = await import('../src/independent-review/association-contracts.ts');
const { associationShortfall, checkAssociationGroupProducts } = await import('../src/independent-review/association-shortfall.ts');
const { selectedMembers, checkSelectedPackageUniqueness } = await import('../src/independent-review/association-selection.ts');
const { loadAssociation, saveAssociation } = await import('../src/independent-review/association-storage.ts');
hook.deregister();
const file = process.env.INDEPENDENT_ASSOCIATION_EVIDENCE;
if (!file) throw new Error('Requires retained actual multitask HTTP response');
const success = JSON.parse(readFileSync(file, 'utf8'));
const result = associationResultSchema.parse(success.result);
const owner = { userId: result.receipt.supervisorId, warehouseId: result.receipt.warehouseId, relayId: result.receipt.relayId };
const contexts = [result.originalContext, ...result.additionalEvidence.map(member => member.originalContext)];
const target = contexts[0].candidates.items.find(row => row.id === result.receipt.targetOrderId);
const reviews = contexts.map(context => ({ original: context.original, contextSha256: '',
  summary: { activeTotal: String(context.inventory.length), crossTaskConflicts: '0' } }));
const selection = result.additionalEvidence.map((member, index) => ({ data: { ...reviews[index + 1], contextSha256: member.evidence.contextSha256 },
  candidates: member.originalContext.candidates.items, physical: member.evidence.physicalCheckId,
  conflict: member.evidence.conflictDecisionId ?? '', exclusions: Object.fromEntries(member.evidence.candidateExclusions.map(row => [row.orderId, row.reason])) }));

test('actual group is readable under main and each explicitly bound original', async () => {
  await verifyAssociation(result, owner, success.body);
  for (const member of result.additionalEvidence) await verifyAssociation(result, { ...owner, relayId: member.evidence.relayId });
  await assert.rejects(verifyAssociation(result, { ...owner, relayId: crypto.randomUUID() }));
  checkSelectedPackageUniqueness(contexts); checkAssociationGroupProducts(contexts, target);
});

test('exact aggregated original and target shortages agree with the recorded request', () => {
  const facts = associationShortfall(reviews[0], target, reviews.slice(1));
  assert.equal(facts.original, success.body.shortfallApproval.originalShortfall);
  assert.equal(facts.target, success.body.shortfallApproval.targetShortfall);
  assert.deepEqual(selectedMembers({ main: reviews[0], members: selection, target: target.id, userId: owner.userId }), success.body.additionalTasks);
});

test('missing duplicate hidden or changed member facts fail closed', async () => {
  for (const mutate of [r => { delete r.additionalEvidence; }, r => { r.additionalEvidence.push(r.additionalEvidence[0]); },
    r => { r.additionalEvidence[0].originalContext.hiddenFact = 'changed'; }, r => { r.additionalEvidence[0].evidence.physicalCheckId = crypto.randomUUID(); },
    r => { r.additionalEvidence[0].physicalEvidence.receipt.taskId = crypto.randomUUID(); },
    r => { r.additionalEvidence[0].physicalEvidence.evidence.reason = 'changed'; },
    r => { r.additionalEvidence[0].evidence.candidateExclusions = []; }]) {
    const changed = structuredClone(result); mutate(changed);
    await assert.rejects(verifyAssociation(associationResultSchema.parse(changed), owner));
  }
});

test('explicit members exclude the target and cannot contain duplicates empty lists or powers', () => {
  for (const mutate of [b => { b.additionalTasks = []; }, b => { b.additionalTasks.push(b.additionalTasks[0]); },
    b => { b.additionalTasks[0].candidateExclusions = [{ orderId: b.targetOrderId, reason: 'wrong' }]; },
    b => { b.additionalTasks[0].releaseAllowed = true; }]) {
    const changed = structuredClone(success.body); mutate(changed); assert.equal(associationEvidenceSchema.safeParse(changed).success, false);
  }
});

test('duplicate task vehicle mismatch self review and unshared target cannot be selected', () => {
  for (const mutate of [s => { s[0].data.original = reviews[0].original; }, s => { s[0].data.original.task.vehicleNo = 'OTHER'; },
    s => { s[0].data.original.operatorId = owner.userId; }, s => { s[0].candidates = []; }]) {
    const changed = structuredClone(selection); mutate(changed);
    assert.throws(() => selectedMembers({ main: reviews[0], members: changed, target: target.id, userId: owner.userId }));
  }
});

test('overfill foreign products and repeated original packages cannot count twice', () => {
  const wrong = structuredClone(target); wrong.products[0].model = 'OTHER';
  assert.throws(() => checkAssociationGroupProducts(contexts, wrong));
  const fewer = structuredClone(target); fewer.products[0].plannedCount = '1';
  assert.throws(() => checkAssociationGroupProducts(contexts, fewer));
  assert.throws(() => checkSelectedPackageUniqueness([contexts[0], contexts[0]]));
});

test('all member references and aggregate approval survive original key reload', () => {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const saved = { ...owner, version: 1, command: { key: success.key, epoch: '1', body: success.body, receipt: null } };
  saveAssociation(saved); assert.deepEqual(loadAssociation(owner), saved);
  saved.command.receipt = result.receipt; saveAssociation(saved); assert.deepEqual(loadAssociation(owner), saved);
});
