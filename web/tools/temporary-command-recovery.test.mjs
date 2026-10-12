import assert from 'node:assert/strict';
import test from 'node:test';
import { randomUUID } from 'node:crypto';
import { enabled, scenario, initial, sameOriginal } from './temporary-command-recovery-harness.mjs';

function check(name, action, options = {}) {
  test(name, { skip: !enabled }, async () => {
    const f = scenario(name.replaceAll(':', '-'), options);
    try { await action(f); } finally { f.save(); f.close(); }
  });
}

const exclusion = { orderId: randomUUID(), revision: '1', rowVersion: '3', basis: 'CANCELLED',
  evidence: '主管逐单核验作废及无有效作业', withdrawalRequestId: null };
check('DUPLICATE_EXCLUDE: response loss keeps original candidate and evidence across remount', async f => {
  await f.mount().state.execute('DUPLICATE_EXCLUDE', { ...initial, exclusion });
  await f.mount().state.execute('DUPLICATE_EXCLUDE', { ...initial, exclusion: { ...exclusion, orderId: randomUUID(), evidence: '另一候选' } });
  sameOriginal(f.calls); assert.equal(JSON.parse(f.calls[0].body).exclusion.orderId, exclusion.orderId);
});
check('DUPLICATE_EXCLUDE: matched receipt requires explicit reset before next candidate', async f => {
  const { state } = f.mount(); await state.execute('DUPLICATE_EXCLUDE', { ...initial, exclusion });
  await state.execute('DUPLICATE_EXCLUDE', { ...initial, exclusion: { ...exclusion, orderId: randomUUID() } });
  assert.equal(f.calls.length, 1); await state.startOver('DUPLICATE_EXCLUDE');
  const next = randomUUID(); await state.execute('DUPLICATE_EXCLUDE', { ...initial, exclusion: { ...exclusion, orderId: next } });
  assert.equal(f.calls.length, 2); assert.equal(JSON.parse(f.calls[1].body).exclusion.orderId, next);
}, { success: true });
check('DUPLICATE_EXCLUDE: incomplete withdrawal cannot persist or send a command', async f => {
  await f.mount().state.execute('DUPLICATE_EXCLUDE', { ...initial, exclusion: { ...exclusion, basis: 'WITHDRAWN' } });
  assert.equal(f.calls.length, 0); assert.equal(f.entries.size, 0);
});
check('DUPLICATE_EXCLUDE: browser storage failure prevents sending', async f => {
  await f.mount().state.execute('DUPLICATE_EXCLUDE', { ...initial, exclusion }); assert.equal(f.calls.length, 0);
}, { blockedStorage: true });

for (const kind of ['LINK', 'NORMAL_CONFIRM']) {
  check(`${kind}: unchanged same-page unknown retry preserves bytes (control)`, async f => {
    const { state } = f.mount(); await state.execute(kind, initial); await state.execute(kind, initial); sameOriginal(f.calls);
  });
  check(`${kind}: editing reason cannot alter an unknown command retry`, async f => {
    const { state } = f.mount(); await state.execute(kind, initial);
    await state.execute(kind, { ...initial, reason: 'Edited while original result is unknown' }); sameOriginal(f.calls);
  });
  check(`${kind}: remount with retained storage must not send a new key`, async f => {
    const old = f.mount(); await old.state.execute(kind, initial); old.stop();
    await f.mount().state.execute(kind, initial); sameOriginal(f.calls);
  });
  check(`${kind}: unavailable browser storage must block sending`, async f => {
    await f.mount().state.execute(kind, initial); assert.equal(f.calls.length, 0);
  }, { blockedStorage: true });
  check(`${kind}: concurrent mounts preserve the first original request`, async f => {
    const a = f.mount().state; const b = f.mount().state;
    await Promise.all([a.execute(kind, initial), b.execute(kind, { ...initial, reason: 'Concurrent changed reason' })]);
    sameOriginal(f.calls);
  });
  check(`${kind}: duplicate clicks send only once`, async f => {
    const { state } = f.mount(); await Promise.all([state.execute(kind, initial), state.execute(kind, initial)]);
    assert.equal(f.calls.length, 1);
  });
  check(`${kind}: unresolved original cannot be reset`, async f => {
    const { state } = f.mount(); await state.execute(kind, initial); await state.startOver(kind);
    assert.match(state.error.value, /原操作结果尚未核对/);
    await state.execute(kind, initial); sameOriginal(f.calls);
  });
  check(`${kind}: matched receipt is retained until explicit reset`, async f => {
    const { state } = f.mount(); await state.execute(kind, initial); const key = f.calls[0].key;
    const remounted = f.mount().state; await remounted.execute(kind, initial);
    assert.equal(f.calls.length, 1); assert.equal(remounted.receipt.value.commandId, key);
    await remounted.startOver(kind); assert.equal(f.entries.size, 0);
    await remounted.execute(kind, initial); assert.equal(f.calls.length, 2); assert.notEqual(f.calls[1].key, key);
  }, { success: true });
  check(`${kind}: mismatched receipt preserves the unresolved original`, async f => {
    const { state } = f.mount(); await state.execute(kind, initial);
    assert.equal(state.receipt.value, null); assert.match(state.error.value, /回执与原键/);
    await state.execute(kind, initial); sameOriginal(f.calls);
  }, { success: true, mismatchedReceipt: true });
  check(`${kind}: saving receipt failure cannot replace the original`, async f => {
    f.options.beforeReceipt = () => { f.options.blockedStorage = true; };
    const { state } = f.mount(); await state.execute(kind, initial);
    assert.ok(state.receipt.value); assert.match(state.error.value, /无法保存/);
    f.options.blockedStorage = false; f.options.beforeReceipt = null;
    await state.startOver(kind); assert.match(state.error.value, /原操作结果尚未核对/);
    await state.execute(kind, initial); sameOriginal(f.calls);
  }, { success: true });
  check(`${kind}: cleanup failure blocks a new key`, async f => {
    const { state } = f.mount(); await state.execute(kind, initial); f.options.blockedRemove = true;
    await state.startOver(kind); assert.match(state.error.value, /无法清理/);
    await state.execute(kind, initial); assert.equal(f.calls.length, 1);
  }, { success: true });
}

check('LINK: refreshed candidate digest cannot alter an unknown command', async f => {
  const { state } = f.mount(); await state.execute('LINK', initial);
  await state.execute('LINK', { ...initial, candidateContextSha256: 'd'.repeat(64) }); sameOriginal(f.calls);
});
check('NORMAL_CONFIRM: recovery epoch change cannot rewrite an unknown command', async f => {
  const { state } = f.mount(); await state.execute('NORMAL_CONFIRM', initial); f.setEpoch('2');
  await state.execute('NORMAL_CONFIRM', initial); sameOriginal(f.calls);
});
check('LINK: changed facts are not fetched for an unresolved retry', async f => {
  const { state } = f.mount(); await state.execute('LINK', initial); const gets = f.gets;
  f.data.expected.decisionVersion = '9'; f.data.workRound = '2';
  await state.execute('LINK', initial); sameOriginal(f.calls); assert.equal(f.gets, gets);
});
for (const [name, options] of [
  ['read failure', { blockedRead: true }], ['silent storage loss', { silentWrite: true }], ['missing browser locks', { noLocks: true }],
]) check(`LINK: ${name} blocks POST`, async f => { await f.mount().state.execute('LINK', initial); assert.equal(f.calls.length, 0); }, options);

for (const corruption of ['json', 'scope', 'kind', 'body']) check(`LINK: corrupted ${corruption} cannot send a replacement`, async f => {
  const { state } = f.mount(); await state.execute('LINK', initial); const [key, json] = [...f.entries][0];
  const saved = JSON.parse(json);
  if (corruption === 'scope') saved.userId = randomUUID();
  if (corruption === 'kind') saved.kind = 'NORMAL_CONFIRM';
  if (corruption === 'body') saved.json = '{}';
  f.entries.set(key, corruption === 'json' ? '{invalid' : JSON.stringify(saved));
  await state.execute('LINK', initial); assert.equal(f.calls.length, 1); assert.match(state.error.value, /无法读取/);
});
check('LINK: another identity cannot reuse the first identity command', async f => {
  const originalUser = f.owner().userId; const { state } = f.mount(); await state.execute('LINK', initial);
  f.setUser(randomUUID()); await state.execute('LINK', initial); assert.notEqual(f.calls[1].key, f.calls[0].key);
  f.setUser(originalUser); await state.execute('LINK', initial);
  assert.equal(f.calls[2].body, f.calls[0].body); assert.equal(f.entries.size, 2);
});
check('LINK: identity change during facts read blocks sending', async f => {
  f.options.beforeFacts = () => f.setUser(randomUUID());
  await f.mount().state.execute('LINK', initial); assert.equal(f.calls.length, 0); assert.equal(f.entries.size, 0);
});
check('LINK: missing identity blocks sending', async f => {
  f.setUser(''); await f.mount().state.execute('LINK', initial); assert.equal(f.calls.length, 0);
});
