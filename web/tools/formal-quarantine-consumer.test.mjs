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
if (!process.env.FORMAL_QUARANTINE_ORIGINALS) throw new Error('Actual original FORMAL HTTP commands required');
const root=resolve(process.env.FORMAL_QUARANTINE_ORIGINALS);
const pairs=readdirSync(root).filter(name=>name.endsWith('-command.json')).map(name=>{
  const command=commandSchema.parse(JSON.parse(readFileSync(resolve(root,name),'utf8')));
  const receipt=receiptSchema.parse(JSON.parse(readFileSync(resolve(root,name.replace('-command','-receipt')),'utf8')));
  return {command,receipt,pending:{actor:receipt.actor.principalId,warehouse:receipt.actor.warehouseId,body:canonical(command),receipt:null,rejection:null}};
});
assert.equal(pairs.length,3);const approved=pairs.find(pair=>pair.command.commandType==='RECONCILE_QUARANTINE_APPROVE');assert.ok(approved);
const proofBytes=readFileSync(resolve(root,'original-formal-proof.json'));
const proof=JSON.parse(proofBytes.toString('utf8'));

test('actual FORMAL Java responses preserve the stopped package and original application',async()=>{
  for(const pair of pairs){
    await verifyResponse(pair.pending,pair.receipt);const prepared=preparationSchema.parse({context:pair.receipt.context,expected:pair.receipt.expected,capabilities:pair.receipt.capabilities});
    assert.equal(prepared.context.facts.origin.sourceKind,'FORMAL');assert.deepEqual(prepared.context.facts.origin.originalReceipt,proof);
    assert.equal(pair.receipt.orderId,proof.recovery.body.orderId);
  }
  assert.equal(approved.receipt.businessDecision.status,'RESOLVED');assert.equal(approved.receipt.businessDecision.bookStatus,'NOT_DEDUCTED');
});
test('original physical approval stays unsigned while original STOP and application retain signatures',()=>{
  assert.equal(proof.proofType,'FORMAL_RECOVERY_APPLICATION_ORIGINALS');assert.equal(proof.signedReceipt,undefined);
  assert.equal(proof.recovery.stop.status,'STOPPED');assert.ok(proof.recovery.stop.signedReceipt);
  assert.equal(proof.application.approval.status,'APPROVED');assert.ok(proof.application.approval.signedReceipt);
  assert.equal(proof.application.activation.status,'ACTIVATED');assert.ok(proof.application.activation.signedReceipt);
  if(proof.application.receipt.status==='DISPOSITIONS_APPLIED') {
    assert.ok(proof.application.receipt.signedApplication);assert.equal(proof.application.receipt.signedReceipt,undefined);
    assert.equal(proof.application.receipt.localDeletionAllowed,false);
    assert.equal(proof.application.receipt.mayResumeScanning,false);assert.equal(proof.application.receipt.stockReleased,false);
  } else { assert.equal(proof.application.receipt.status,'SUCCEEDED');assert.ok(proof.application.receipt.signedReceipt); }
  assert.equal(proof.physicalApproval.command.payloadSha256,proof.recovery.payloadSha256);
  assert.equal(proof.application.receipt.payloadSha256,proof.application.payloadSha256);
  assert.equal(createHash('sha256').update(proofBytes).digest('hex'),approved.receipt.context.facts.origin.originalReceiptSha256);
});
test('missing original approval bytes or signed application is rejected by product preparation',()=>{
  const valid={context:approved.receipt.context,expected:approved.receipt.expected,capabilities:approved.receipt.capabilities};
  for(const changed of [{...proof,physicalApproval:{...proof.physicalApproval,command:undefined}},
    {...proof,recovery:{...proof.recovery,stop:{...proof.recovery.stop,signedReceipt:undefined}}},
    {...proof,application:{...proof.application,receipt:{...proof.application.receipt,status:'UNKNOWN'}}}]){
    const origin={...valid.context.facts.origin,originalReceipt:changed};
    assert.equal(preparationSchema.safeParse({...valid,context:{...valid.context,facts:{...valid.context.facts,origin}}}).success,false);
  }
});
test('restored responses reject substituted order, source owner and command hash',async()=>{
  const {receipt,pending}=approved;
  for(const changed of [{...receipt,orderId:crypto.randomUUID()},{...receipt,commandSha256:'0'.repeat(64)},
    {...receipt,context:{...receipt.context,dispositionId:crypto.randomUUID()}},
    {...receipt,businessDecision:{...receipt.businessDecision,caseId:crypto.randomUUID()}}]) await assert.rejects(verifyResponse(pending,changed));
});
