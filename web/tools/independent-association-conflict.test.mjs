import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { registerHooks } from 'node:module';
const hook = registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier.startsWith('./') && !/\.[a-z]+$/.test(specifier)) return nextResolve(`${specifier}.ts`, context);
  return nextResolve(specifier, context);
} });
const { associationResultSchema, verifyAssociation } = await import('../src/independent-review/association-contracts.ts');
const { conflictResultSchema, verifyConflict } = await import('../src/independent-review/conflict-contracts.ts');
hook.deregister();
const file = process.env.INDEPENDENT_ASSOCIATION_CONFLICT_EVIDENCE;
if (!file) throw new Error('Requires retained actual conflict association HTTP evidence');
const success = JSON.parse(readFileSync(file, 'utf8'));
const owner = { userId: success.result.receipt.supervisorId, warehouseId: success.result.receipt.warehouseId, relayId: success.result.receipt.relayId };

test('actual conflict association binds all original physical evidence and explicit decision', async () => {
  await verifyAssociation(associationResultSchema.parse(success.result), owner, success.body);
  const conflict = conflictResultSchema.parse(success.result.conflictEvidence);
  await verifyConflict(conflict, owner);
});
test('absent, unrelated or stale conflict decision cannot be displayed as verified', async () => {
  for (const mutate of [r => { delete r.conflictEvidence; }, r => { delete r.evidence.conflictDecisionId; },
    r => { r.conflictEvidence.receipt.decisionId = crypto.randomUUID(); },
    r => { r.conflictEvidence.originalContext.selected.context.hidden = 'changed'; },
    r => { r.conflictEvidence.originalContext.peers[0].physical.evidence.reason = 'changed'; },
    r => { r.conflictEvidence.originalContext.peers[0].physical.receipt.relayId = crypto.randomUUID(); },
    r => { r.conflictEvidence.evidence.peerChecks[0].physicalCheckId = crypto.randomUUID(); }]) {
    const value = structuredClone(success.result); mutate(value);
    await assert.rejects(verifyAssociation(associationResultSchema.parse(value), owner, success.body));
  }
});
test('conflict response never grants business or deletion capability', () => {
  for (const field of ['associationAuthorized', 'newCollectionAuthorized', 'stockAvailable', 'businessProcessed', 'releaseAllowed', 'localDeletionAllowed']) {
    const value = structuredClone(success.result); value.conflictEvidence.receipt[field] = true;
    assert.equal(associationResultSchema.safeParse(value).success, false);
  }
});
test('peer task/reference mismatch refuses even a self-consistent digest', async () => {
  const { jsonDigest } = await import('../src/independent-review/physical-contracts.ts');
  const conflict = structuredClone(success.result.conflictEvidence);
  conflict.originalContext.peers[0].group.deviceId = crypto.randomUUID();
  conflict.receipt.contextSha256 = await jsonDigest(conflict.originalContext);
  await assert.rejects(verifyConflict(conflictResultSchema.parse(conflict), owner));
});
