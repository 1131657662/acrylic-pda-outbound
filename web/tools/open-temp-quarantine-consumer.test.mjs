import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
import test from 'node:test';
registerHooks({ resolve(specifier, context, next) {
  if (context.parentURL?.includes('/web/src/reconciliation/') && specifier.startsWith('./') && !specifier.endsWith('.ts')) return next(specifier + '.ts', context);
  return next(specifier, context);
} });
const { receiptSchema, commandSchema } = await import('../src/reconciliation/contracts.ts');
const { canonical, verifyResponse, savePending, loadPending } = await import('../src/reconciliation/pending.ts');
const { preparationSchema } = await import('../src/reconciliation/quarantine-contracts.ts');
if (!process.env.OPEN_TEMP_QUARANTINE_ORIGINALS) throw new Error('Actual original HTTP commands required');
const root=resolve(process.env.OPEN_TEMP_QUARANTINE_ORIGINALS);
const pairs=readdirSync(root).filter(name=>name.endsWith('-command.json')).map(name=>{
  const command=commandSchema.parse(JSON.parse(readFileSync(resolve(root,name),'utf8')));
  const receipt=receiptSchema.parse(JSON.parse(readFileSync(resolve(root,name.replace('-command','-receipt')),'utf8')));
  return {command,receipt,pending:{actor:receipt.actor.principalId,warehouse:receipt.actor.warehouseId,body:canonical(command),receipt:null,rejection:null}};
});
assert.equal(pairs.length,3);const approved=pairs.find(pair=>pair.command.commandType==='RECONCILE_QUARANTINE_APPROVE');assert.ok(approved);
const originalBytes=readFileSync(resolve(root,'../original-physical-receipt.json'));
const original=JSON.parse(originalBytes.toString('utf8'));

test('actual open TEMP originals preserve per-bale disposition and original order identity',async()=>{
  for(const pair of pairs){
    await verifyResponse(pair.pending,pair.receipt);
    const prepared=preparationSchema.parse({context:pair.receipt.context,expected:pair.receipt.expected,capabilities:pair.receipt.capabilities});
    assert.equal(prepared.context.facts.origin.sourceKind,'TEMP');
    assert.deepEqual(prepared.context.facts.origin.originalReceipt,original);
    assert.equal(pair.receipt.orderId,original.originalContext.canonicalOrderId);
    assert.equal(Boolean(prepared.context.facts.origin.closeMode),false);
    assert.equal(Boolean(prepared.context.facts.origin.sourceClosed),false);
    assert.equal(prepared.context.facts.origin.sourceStatus,'REJECTED');
  }
  assert.equal(original.commandType,'PHYSICAL_DISPOSITION');assert.ok(original.signedReceipt);
  assert.equal(createHash('sha256').update(originalBytes).digest('hex'),approved.receipt.context.facts.origin.originalReceiptSha256);
  assert.equal(approved.receipt.businessDecision.status,'RESOLVED');
});
test('non-disposition receipts cannot be treated as the original quarantine proof',()=>{
  const valid={context:approved.receipt.context,expected:approved.receipt.expected,capabilities:approved.receipt.capabilities};
  for(const kind of ['NORMAL_CONFIRM','REOPEN_WORK','REJECT']) {
    const origin={...valid.context.facts.origin,originalReceipt:{...original,commandType:kind}};
    assert.equal(preparationSchema.safeParse({...valid,context:{...valid.context,facts:{...valid.context.facts,origin}}}).success,false);
  }
});
test('original request recovery rejects substituted order, owner and case',async()=>{
  const {receipt,pending}=approved;
  for(const changed of [{...receipt,orderId:null},{...receipt,commandSha256:'0'.repeat(64)},
    {...receipt,context:{...receipt.context,dispositionId:crypto.randomUUID()}},
    {...receipt,businessDecision:{...receipt.businessDecision,caseId:crypto.randomUUID()}}]) await assert.rejects(verifyResponse(pending,changed));
});
test('durable pending preserves the exact original intent and refuses storage failure',()=>{
  const values=new Map();globalThis.localStorage={getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value)};
  savePending(approved.pending);assert.equal(loadPending(approved.pending).body,approved.pending.body);
  savePending({...approved.pending,receipt:approved.receipt});assert.deepEqual(loadPending(approved.pending).receipt,approved.receipt);
  globalThis.localStorage={getItem:()=>null,setItem:()=>{throw new Error('quota');}};
  assert.throws(()=>savePending(approved.pending),/quota/);
});
