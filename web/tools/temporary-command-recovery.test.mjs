import assert from 'node:assert/strict';
import test from 'node:test';
import { registerHooks } from 'node:module';
import { writeFileSync, realpathSync } from 'node:fs';
import { resolve, relative, isAbsolute, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { boundary, mountHook } from './temporary-command-recovery-fixture.mjs';

// Dedicated defect reproducer. Normal test discovery cannot accidentally run this known-red batch.
const enabled = process.env.TEMP_WEB_RECOVERY_VERIFY === 'true';
let evidence; let useTemporaryCommand;
if (enabled) {
  const local = realpathSync(fileURLToPath(new URL('../../.local', import.meta.url)));
  assert.ok(process.env.TEMP_WEB_RECOVERY_EVIDENCE, 'Explicit evidence directory required');
  evidence = realpathSync(resolve(process.env.TEMP_WEB_RECOVERY_EVIDENCE));
  const child = relative(local, evidence);
  assert.ok(child && !child.startsWith('..') && !isAbsolute(child), 'Evidence must be inside repository .local');
  writeFileSync(join(evidence, 'test-started.marker'), 'one invocation; no automatic retry', { flag: 'wx' });
  const api = 'data:text/javascript,' + encodeURIComponent(`
    export class ApiError extends Error {}
    export const request = (...args) => globalThis.temporaryRecoveryBoundary.request(...args);
    export const postRaw = (...args) => globalThis.temporaryRecoveryBoundary.postRaw(...args);
    export const currentRecoveryEpoch = () => globalThis.temporaryRecoveryBoundary.epoch();
    export const errorText = cause => cause.message;
  `);
  registerHooks({ resolve(specifier, context, nextResolve) {
    if (specifier === '../api' && context.parentURL?.endsWith('/useTemporaryCommand.ts')) return { url: api, shortCircuit: true };
    if (context.parentURL?.includes('/temporary-review/') && ['./command-contracts', './duplicate-contracts', './contracts'].includes(specifier))
      return nextResolve(`${specifier}.ts`, context);
    return nextResolve(specifier, context);
  } });
  ({ useTemporaryCommand } = await import('../src/temporary-review/useTemporaryCommand.ts'));
}

function scenario(name, options = {}) {
  const transport = boundary(options); const mounts = [];
  const mount = () => { const value = mountHook(useTemporaryCommand, transport.data); mounts.push(value); return value; };
  return { ...transport, mount, save() {
    writeFileSync(join(evidence, name + '.json'), JSON.stringify({ name, fixture: 'synthetic API boundary; real Vue hook',
      posts: transport.calls, storageEvents: transport.storageEvents,
      states: mounts.map(value => ({ error: value.state.error.value, receipt: value.state.receipt.value })) }, null, 2), { flag: 'wx' });
  }, close() { for (const value of mounts) value.stop(); transport.restore(); } };
}

const initial = { reason: 'Original reviewed reason', candidateContextSha256: 'c'.repeat(64) };
for (const kind of ['LINK', 'NORMAL_CONFIRM']) {
  test(`${kind}: unchanged same-page unknown retry preserves bytes (control)`, { skip: !enabled }, async () => {
    const f = scenario(kind + '-control');
    try {
      const { state } = f.mount(); await state.execute(kind, initial); await state.execute(kind, initial); f.save();
      assert.equal(f.calls.length, 2); assert.equal(f.calls[1].key, f.calls[0].key);
      assert.equal(f.calls[1].body, f.calls[0].body);
    } finally { f.close(); }
  });
  test(`${kind}: editing reason cannot alter an unknown command retry`, { skip: !enabled }, async () => {
    const f = scenario(kind + '-reason');
    try {
      const { state } = f.mount(); await state.execute(kind, initial);
      await state.execute(kind, { ...initial, reason: 'Edited while original result is unknown' }); f.save();
      assert.equal(f.calls.length, 2); assert.equal(f.calls[1].key, f.calls[0].key);
      assert.equal(f.calls[1].body, f.calls[0].body, 'Unknown original key must retain the exact original body');
    } finally { f.close(); }
  });
  test(`${kind}: remount with retained storage must not send a new key`, { skip: !enabled }, async () => {
    const f = scenario(kind + '-remount');
    try {
      const old = f.mount(); await old.state.execute(kind, initial); old.stop();
      await f.mount().state.execute(kind, initial); f.save();
      assert.equal(f.calls.length, 2);
      assert.equal(f.calls[1].key, f.calls[0].key, 'Page remount cannot replace an unresolved original command key');
    } finally { f.close(); }
  });
  test(`${kind}: unavailable browser storage must block sending`, { skip: !enabled }, async () => {
    const f = scenario(kind + '-storage', { blockedStorage: true });
    try {
      await f.mount().state.execute(kind, initial); f.save();
      assert.equal(f.calls.length, 0, 'No durable original request means no POST');
    } finally { f.close(); }
  });
}

test('LINK: refreshed candidate digest cannot alter an unknown command', { skip: !enabled }, async () => {
  const f = scenario('LINK-candidate');
  try {
    const { state } = f.mount(); await state.execute('LINK', initial);
    await state.execute('LINK', { ...initial, candidateContextSha256: 'd'.repeat(64) }); f.save();
    assert.equal(f.calls.length, 2); assert.equal(f.calls[1].key, f.calls[0].key);
    assert.equal(f.calls[1].body, f.calls[0].body, 'Candidate refresh must not overwrite the original expected values');
  } finally { f.close(); }
});

test('NORMAL_CONFIRM: recovery epoch change cannot rewrite an unknown command', { skip: !enabled }, async () => {
  const f = scenario('NORMAL_CONFIRM-epoch');
  try {
    const { state } = f.mount(); await state.execute('NORMAL_CONFIRM', initial); f.setEpoch('2');
    await state.execute('NORMAL_CONFIRM', initial); f.save();
    assert.equal(f.calls.length, 2); assert.equal(f.calls[1].key, f.calls[0].key);
    assert.equal(f.calls[1].body, f.calls[0].body, 'Recovery epoch change cannot rewrite the saved intent');
  } finally { f.close(); }
});
