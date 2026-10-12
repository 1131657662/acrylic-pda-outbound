import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
import { createHash } from 'node:crypto';
registerHooks({ resolve(specifier, context, next) {
  if (context.parentURL?.includes('/web/src/reconciliation/') && specifier.startsWith('./') && !specifier.endsWith('.ts')) return next(specifier + '.ts', context);
  return next(specifier, context);
} });
const { receiptSchema, commandSchema } = await import('../src/reconciliation/contracts.ts');
const { canonical, verifyResponse } = await import('../src/reconciliation/pending.ts');
const { preparationSchema } = await import('../src/reconciliation/quarantine-contracts.ts');
if (!process.env.PC_QUARANTINE_ORIGINALS) throw new Error('Original PC HTTP commands required');
const root = resolve(process.env.PC_QUARANTINE_ORIGINALS);
const pairs = readdirSync(root).filter(name => name.endsWith('-command.json')).map(name => {
  const command = commandSchema.parse(JSON.parse(readFileSync(resolve(root,name),'utf8')));
  const receipt = receiptSchema.parse(JSON.parse(readFileSync(resolve(root,name.replace('-command','-receipt')),'utf8')));
  return { command, receipt, pending: { actor: receipt.actor.principalId, warehouse: receipt.actor.warehouseId, body: canonical(command), receipt: null, rejection: null } };
});
assert.equal(pairs.length,3);
const approved = pairs.find(pair => pair.command.commandType === 'RECONCILE_QUARANTINE_APPROVE');
assert.ok(approved);
const proof = JSON.parse(readFileSync(resolve(root,'original-pc-proof.json'),'utf8'));

test('three original PC Java responses preserve formal order identity', async () => {
  for (const pair of pairs) {
    await verifyResponse(pair.pending,pair.receipt);
    assert.equal(pair.receipt.orderId,proof.snapshot.orderId);
    const prepared = preparationSchema.parse({context:pair.receipt.context,expected:pair.receipt.expected,capabilities:pair.receipt.capabilities});
    assert.equal(prepared.context.facts.origin.sourceKind,'PC');
    assert.deepEqual(prepared.context.facts.origin.originalReceipt,proof);
  }
  assert.equal(approved.receipt.businessDecision.status,'RESOLVED');
  assert.equal(approved.receipt.businessDecision.bookStatus,'NOT_DEDUCTED');
});

test('PC original approvals are consumed without inventing a TEMP signature', () => {
  assert.equal(proof.proofType,'PC_DATABASE_ORIGINALS'); assert.equal(proof.signedReceipt,undefined);
  const sorted=value=>Array.isArray(value)?value.map(sorted):value&&typeof value==='object'?Object.fromEntries(Object.entries(value).sort(([a],[b])=>a<b?-1:a>b?1:0).map(([k,v])=>[k,sorted(v)])):value;
  assert.equal(proof.review.decision,'APPROVE');
  assert.equal(proof.plan.snapshotSha256,createHash('sha256').update(JSON.stringify(sorted(proof.snapshot))).digest('hex'));
  assert.equal(proof.commands.plan.result.planId,proof.commands.apply.result.planId);
  assert.equal(proof.commands.review.result.planId,proof.commands.apply.result.planId);
  assert.equal(proof.plan.caseId,proof.commands.stop.result.caseId);
  assert.equal(proof.plan.targetUserId,proof.snapshot.operatorId);
  assert.notEqual(proof.review.principalId,proof.plan.targetUserId);
});

test('wrong source type or an incomplete original review fails product preparation', () => {
  const valid = {context:approved.receipt.context,expected:approved.receipt.expected,capabilities:approved.receipt.capabilities};
  for (const origin of [{...valid.context.facts.origin,sourceKind:'TEMP'},
    {...valid.context.facts.origin,originalReceipt:{...proof,review:{...proof.review,decision:'REJECT'}}},
    {...valid.context.facts.origin,originalReceipt:{...proof,commands:{...proof.commands,apply:undefined}}}]) {
    assert.equal(preparationSchema.safeParse({...valid,context:{...valid.context,facts:{...valid.context.facts,origin}}}).success,false);
  }
});

test('PC receipt recovery rejects substituted command, original owner or case', async () => {
  const {receipt,pending} = approved;
  for (const changed of [{...receipt,commandSha256:'0'.repeat(64)}, {...receipt,orderId:crypto.randomUUID()},
    {...receipt,context:{...receipt.context,dispositionId:crypto.randomUUID()}},
    {...receipt,businessDecision:{...receipt.businessDecision,caseId:crypto.randomUUID()}},
    {...receipt,actor:{...receipt.actor,warehouseId:crypto.randomUUID()}}]) await assert.rejects(verifyResponse(pending,changed));
});
