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
const { canonical, verifyResponse } = await import('../src/reconciliation/pending.ts');
const { preparationSchema } = await import('../src/reconciliation/quarantine-contracts.ts');
const { historyReceiptBytes } = await import('../src/reconciliation/quarantine-history-download.ts');
assert.ok(process.env.QUARANTINE_HISTORY_ATTEMPT, 'Original isolated HTTP input required');
const attempt = resolve(process.env.QUARANTINE_HISTORY_ATTEMPT);
const root = resolve(attempt, 'quarantine-history-http');
const pairs = readdirSync(root).filter(name => name.endsWith('-command.json')).map(name => {
  const command = commandSchema.parse(JSON.parse(readFileSync(resolve(root, name), 'utf8')));
  const receipt = receiptSchema.parse(JSON.parse(readFileSync(resolve(root, name.replace('-command', '-receipt')), 'utf8')));
  return { command, receipt, pending: { actor: receipt.actor.principalId, warehouse: receipt.actor.warehouseId,
    body: canonical(command), receipt: null, rejection: null } };
});
assert.equal(pairs.length, 3);
function prepared(receipt) { return preparationSchema.parse({ context: receipt.context, expected: receipt.expected, capabilities: receipt.capabilities }); }
const approved = pairs.find(pair => pair.command.commandType === 'RECONCILE_QUARANTINE_APPROVE');
assert.ok(approved);
const history = prepared(approved.receipt).context.facts.history;
assert.equal(history.length, 1);

test('three Java HTTP responses preserve old zero contributions and actual restoration approval', async () => {
  for (const pair of pairs) {
    await verifyResponse(pair.pending, pair.receipt);
    const item = prepared(pair.receipt).context.facts.history[0];
    assert.equal(item.dispatch.count, '0'); assert.equal(item.dispatch.weightG, '0');
    assert.equal(item.dispatch.dispatchCycle, '1'); assert.equal(item.reopening.cycleAfter, '2');
    assert.equal(item.stockRestorationReceipt.businessDecision.bookStatus, 'NOT_DEDUCTED');
    assert.equal(item.ledger.length, 2);
  }
});

test('downloaded historical approval bytes match both actual HTTP originals exactly', async () => {
  const sourceQuery=`warehouseId=${approved.receipt.actor.warehouseId}&commandType=RECONCILE_QUARANTINE_APPROVE&caseId=${approved.receipt.businessDecision.caseId}`;
  for (const item of history) {
    for (const [kind, receipt] of [['return', item.reopening.approvalReceipt], ['restoration', item.stockRestorationReceipt]]) {
      const original = readFileSync(resolve(attempt, 'accounting/returned-quarantine-history', `${receipt.commandId}-receipt.json`));
      const fetch=globalThis.fetch;
      globalThis.fetch=async url=>{assert.equal(url,`/api/v1/corrections/quarantine/original-receipt?${sourceQuery}&historyDispatchId=${item.dispatch.dispatchId}&historyReceiptType=${kind}`);return new Response(original);};
      try{assert.deepEqual(Buffer.from(await historyReceiptBytes({ item, receipt: kind, sourceQuery })),original);}finally{globalThis.fetch=fetch;}
    }
  }
});

test('missing or substituted historical approvals cannot be downloaded as original bytes', async () => {
  const item=history[0],fetch=globalThis.fetch;
  try{
    globalThis.fetch=async()=>new Response('substituted original bytes');
    await assert.rejects(historyReceiptBytes({item,receipt:'return',sourceQuery:'isolated'}));
    globalThis.fetch=async()=>new Response('missing',{status:404});
    await assert.rejects(historyReceiptBytes({item,receipt:'restoration',sourceQuery:'isolated'}));
  }finally{globalThis.fetch=fetch;}
});

test('nonzero history, absent proof or unrestored inventory fails the product contract', () => {
  const receipt = approved.receipt; const valid = prepared(receipt); const item = history[0];
  for (const changed of [{ ...item, dispatch: { ...item.dispatch, count: '1' } },
    { ...item, reopening: { ...item.reopening, documentStatus: 'WITHDRAW_PENDING' } },
    { ...item, stockRestorationReceipt: undefined },
    { ...item, stockRestorationReceipt: { ...item.stockRestorationReceipt, businessDecision: { ...item.stockRestorationReceipt.businessDecision, bookStatus: 'DEDUCTED' } } }])
    assert.equal(preparationSchema.safeParse({ ...valid, context: { ...valid.context, facts: { ...valid.context.facts, history: [changed] } } }).success, false);
});
