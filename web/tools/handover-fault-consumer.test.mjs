import { registerHooks } from 'node:module';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import test from 'node:test';
registerHooks({resolve(specifier, context, next) {
  if (context.parentURL?.includes('/web/src/handover/') && specifier.startsWith('./') && !specifier.endsWith('.ts'))
    return next(specifier+'.ts', context);
  return next(specifier, context);
}});
const {faultCommandSchema, faultReceiptSchema}=await import('../src/handover/fault-contracts.ts');
const {saveFault,loadFault,bind,faultDigest,clearRefusedFault}=await import('../src/handover/pending-fault.ts');
const run=Number(process.argv[2]);
assert.ok(run>=1 && Number.isInteger(run));
const base=`D:/acrylic-pda-outbound/.local/e2a-cross-20260925-${run}/temporary-handover-stop/fault-fence`;
const receipt=faultReceiptSchema.parse(JSON.parse(readFileSync(base+'/fault-receipt.json','utf8')));
const snap=JSON.parse(readFileSync(base+'/fence-protection/before.json','utf8'));
const table=snap.handover_command_receipt;
const field=(row,name)=>Buffer.from(row[table.columns.indexOf(name)],'base64');
const row=table.rows.find(row=>field(row,'command_type').toString()==='FAULT_FENCE');
const json=field(row,'canonical_command_bytes').toString();
const command=faultCommandSchema.parse(JSON.parse(json));
const owner={userId:receipt.actor.principalId,warehouseId:command.warehouseId,caseId:command.caseId};
const entries=new Map();
globalThis.localStorage={getItem:key=>entries.get(key)??null,setItem:(key,value)=>entries.set(key,value),removeItem:key=>entries.delete(key)};
test('actual fault receipt binds canonical command digest and original owner',async()=>{
  bind(receipt,command,owner);assert.equal(await faultDigest(command),receipt.commandSha256);
});
test('false flags, missing capabilities and fictitious originating ACK cannot be consumed',()=>{
  for(const altered of [{...receipt,capabilities:{}},{...receipt,capabilities:{...receipt.capabilities,releaseAllowed:true}},
    {...receipt,businessDecision:{...receipt.businessDecision,oldDeviceAcknowledged:true}}])
    assert.equal(faultReceiptSchema.safeParse(altered).success,false);
});
test('exact persisted request survives reload and receipt while another owner cannot read it',()=>{
  entries.clear();const saved={...owner,version:1,json,receipt:null,refusal:null};
  saveFault(saved);assert.equal(loadFault(owner).json,json);
  assert.throws(()=>clearRefusedFault(owner),/未核明/);
  saveFault({...saved,receipt});assert.equal(loadFault(owner).json,json);
  assert.equal(loadFault({...owner,userId:crypto.randomUUID()}),null);
});
test('corrupt stored scope or receipt cannot produce a fresh request',()=>{
  entries.clear();saveFault({...owner,version:1,json,receipt:null,refusal:null});
  const key=[...entries.keys()][0];const saved=JSON.parse(entries.get(key));saved.caseId=crypto.randomUUID();
  entries.set(key,JSON.stringify(saved));assert.throws(()=>loadFault(owner),/无法读取/);
  assert.throws(()=>bind({...receipt,commandId:crypto.randomUUID()},command,owner),/不一致/);
});
test('failed durable write blocks the decision',()=>{
  const storage=globalThis.localStorage;
  globalThis.localStorage={...storage,setItem(){throw new Error('synthetic quota failure');}};
  try{assert.throws(()=>saveFault({...owner,version:1,json,receipt:null,refusal:null}),/无法保存/);}
  finally{globalThis.localStorage=storage;}
});
