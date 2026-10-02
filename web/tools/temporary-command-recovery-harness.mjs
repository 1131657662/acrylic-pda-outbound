import assert from 'node:assert/strict';
import { registerHooks } from 'node:module';
import { writeFileSync, realpathSync } from 'node:fs';
import { resolve, relative, isAbsolute, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { boundary, mountHook } from './temporary-command-recovery-fixture.mjs';

export const enabled = process.env.TEMP_WEB_RECOVERY_VERIFY === 'true';
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
    if (context.parentURL?.includes('/temporary-review/') && specifier.startsWith('./') && !specifier.endsWith('.ts'))
      return nextResolve(`${specifier}.ts`, context);
    return nextResolve(specifier, context);
  } });
  ({ useTemporaryCommand } = await import('../src/temporary-review/useTemporaryCommand.ts'));
}

export function scenario(name, options = {}) {
  const transport = boundary(options); const mounts = [];
  const mount = () => { const value = mountHook(useTemporaryCommand, transport.owner); mounts.push(value); return value; };
  return { ...transport, mount, get gets() { return transport.gets; }, save() {
    writeFileSync(join(evidence, name + '.json'), JSON.stringify({ name, fixture: 'synthetic API/storage/lock boundary; real Vue hook',
      posts: transport.calls, storageEvents: transport.storageEvents, stored: [...transport.entries],
      states: mounts.map(value => ({ error: value.state.error.value, receipt: value.state.receipt.value })) }, null, 2), { flag: 'wx' });
  }, close() { for (const value of mounts) value.stop(); transport.restore(); } };
}

export const initial = { reason: 'Original reviewed reason', candidateContextSha256: 'c'.repeat(64) };
export function sameOriginal(calls) {
  assert.equal(calls.length, 2); assert.equal(calls[1].key, calls[0].key);
  assert.equal(calls[1].body, calls[0].body); assert.equal(calls[1].epoch, calls[0].epoch);
  assert.ok(calls.every(call => call.persistedBeforePost));
}
