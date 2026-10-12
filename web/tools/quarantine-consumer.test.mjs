import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';
registerHooks({ resolve(specifier, context, next) {
  if (context.parentURL?.includes('/web/src/reconciliation/') && specifier.startsWith('./') && !specifier.endsWith('.ts')) return next(specifier + '.ts', context);
  return next(specifier, context);
} });
const { receiptSchema, commandSchema } = await import('../src/reconciliation/contracts.ts');
const { canonical, verifyResponse, savePending, loadPending } = await import('../src/reconciliation/pending.ts');
const { preparationSchema, listSchema } = await import('../src/reconciliation/quarantine-contracts.ts');
if (!process.env.QUARANTINE_ORIGINALS) throw new Error('Preserved actual HTTP originals required');
const root = resolve(process.env.QUARANTINE_ORIGINALS);
const pairs = readdirSync(root).filter(name => name.endsWith('-command.json')).map(name => {
  const command = commandSchema.parse(JSON.parse(readFileSync(resolve(root,name),'utf8')));
  const receipt = receiptSchema.parse(JSON.parse(readFileSync(resolve(root,name.replace('-command','-receipt')),'utf8')));
  return { command, receipt, pending: { actor: receipt.actor.principalId, warehouse: receipt.actor.warehouseId, body: canonical(command), receipt: null, rejection: null } };
});
const approved = pairs.find(pair => pair.command.commandType === 'RECONCILE_QUARANTINE_APPROVE');
if (pairs.length !== 3 || !approved) throw new Error('All three actual commands required');
function storage() {
  const values = new Map(); globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key,value) => values.set(key,value) }; return values;
}
test('three actual original Java commands bind nullable TEMP order identity and exact bytes', async () => {
  for (const pair of pairs) { await verifyResponse(pair.pending,pair.receipt); assert.equal(pair.receipt.orderId,null); }
  assert.equal(approved.receipt.businessDecision.status,'RESOLVED'); assert.equal(approved.receipt.businessDecision.bookStatus,'NOT_DEDUCTED');
  assert.equal(approved.receipt.businessDecision.availability,'REEVALUATE');
});
test('source identity, case, actor and facts cannot be substituted during recovery', async () => {
  const { receipt, pending } = approved;
  for (const changed of [{ ...receipt, commandId: crypto.randomUUID() }, { ...receipt, commandSha256: '0'.repeat(64) },
    { ...receipt, actor: { ...receipt.actor, principalId: crypto.randomUUID() } },
    { ...receipt, actor: { ...receipt.actor, warehouseId: crypto.randomUUID() } }, { ...receipt, orderId: crypto.randomUUID() },
    { ...receipt, context: { ...receipt.context, baleId: crypto.randomUUID() } },
    { ...receipt, context: { ...receipt.context, dispositionId: crypto.randomUUID() } },
    { ...receipt, businessDecision: { ...receipt.businessDecision, caseId: crypto.randomUUID() } },
    { ...receipt, expected: { ...receipt.expected, factsSha256: '0'.repeat(64) } }]) await assert.rejects(verifyResponse(pending,changed));
});
test('original pending command and completed bytes survive remount with archived previous requests', () => {
  const values = storage(); const { pending, receipt, command } = approved;
  savePending(pending); assert.equal(loadPending(pending).body,pending.body); savePending({ ...pending,receipt });
  assert.deepEqual(loadPending(pending).receipt,receipt); savePending({ ...pending,body:canonical({ ...command,commandId:crypto.randomUUID() }) });
  assert.equal([...values.values()].map(JSON.parse).filter(value => value.receipt).length,1);
});
test('unavailable durable storage prevents a new pending request', () => {
  globalThis.localStorage = { getItem: () => null, setItem: () => { throw new Error('quota'); } }; assert.throws(() => savePending(approved.pending),/quota/);
});
test('mixed fields, wrong physical observation and capability escalation reject at the boundary', () => {
  for (const pair of pairs) assert.equal(commandSchema.safeParse({ ...pair.command,dispatchId:crypto.randomUUID() }).success,false);
  const submitted = pairs.find(pair => pair.command.commandType.endsWith('_SUBMIT')).command;
  assert.equal(commandSchema.safeParse({ ...submitted,evidence:{ ...submitted.evidence,observation:'RETURNED_IN_WAREHOUSE' } }).success,false);
  assert.equal(receiptSchema.safeParse({ ...approved.receipt,signedReceipt:'' }).success,false);
  for (const field of Object.keys(approved.receipt.capabilities)) assert.equal(receiptSchema.safeParse({ ...approved.receipt,
    capabilities:{ ...approved.receipt.capabilities,[field]:true } }).success,false);
});
test('actual signed anchor is consumed as product preparation and independent pages stay bounded', () => {
  preparationSchema.parse({ context:approved.receipt.context,expected:approved.receipt.expected,capabilities:approved.receipt.capabilities });
  const list={ candidates:[],cases:[],nextCursor:null,nextCaseCursor:null,capabilities:approved.receipt.capabilities };
  assert.equal(listSchema.safeParse(list).success,true); assert.equal(listSchema.safeParse({ ...list,nextCaseCursor:undefined }).success,false);
});
