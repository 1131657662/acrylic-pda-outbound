import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { registerHooks } from 'node:module';
import test from 'node:test';
import { createRenderer, nextTick, ref } from 'vue';

const apiBoundary = 'data:text/javascript,' + encodeURIComponent(`
  export const request = (url, schema) => globalThis.duplicateTestRequest(url).then(value => schema.parse(value));
  export const errorText = cause => cause.message;
`);
registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier === '../api' && context.parentURL?.endsWith('/useTemporaryDuplicates.ts')) return { url: apiBoundary, shortCircuit: true };
  if (context.parentURL?.includes('/temporary-review/') && ['./contracts', './duplicate-contracts'].includes(specifier))
    return nextResolve(`${specifier}.ts`, context);
  return nextResolve(specifier, context);
} });
const { useTemporaryDuplicates } = await import('../src/temporary-review/useTemporaryDuplicates.ts');
const sample = JSON.parse(await readFile(new URL('../../server/build/temporary-duplicate-contract.json', import.meta.url), 'utf8'));
const settle = async () => { await new Promise(resolve => setImmediate(resolve)); await nextTick(); };
const renderer = createRenderer({ patchProp() {}, insert() {}, remove() {}, createElement: () => ({}), createText: () => ({}),
  createComment: () => ({}), setText() {}, setElementText() {}, parentNode: () => null, nextSibling: () => null });
function mount(boundary) {
  globalThis.duplicateTestRequest = boundary;
  const source = ref({ submissionId: sample.context.submissionId, warehouseId: '01994300-0000-7000-8000-000000000001' });
  let state;
  const app = renderer.createApp({ setup() { state = useTemporaryDuplicates(() => source.value); return () => null; } });
  app.mount({});return { state, source, stop: () => app.unmount() };
}

test('edited dates are applied explicitly and prevent paging through the previous scope', async () => {
  const requests = [];const page = { ...sample, nextCursor: 'next-page', hasMore: true };
  const mounted = mount(async url => { requests.push(url); return page; });
  try {
    await settle();mounted.state.from.value = '2026-09-01';mounted.state.through.value = '2026-09-30';
    mounted.state.next();await settle();assert.equal(requests.length, 1);assert.equal(mounted.state.changed.value, true);
    mounted.state.search();await settle();assert.equal(requests.length, 2);
    assert.match(requests[1], /fromDate=2026-09-01/);assert.match(requests[1], /throughDate=2026-09-30/);
    assert.match(requests[1], /cursor=&/);
  } finally { mounted.stop(); }
});
test('a late previous-warehouse response cannot replace the current comparison', async () => {
  let finishOld;let calls = 0;
  const mounted = mount(() => ++calls === 1 ? new Promise(resolve => { finishOld = resolve; }) : Promise.resolve({ ...sample, total: 0, items: [] }));
  try {
    mounted.source.value = { ...mounted.source.value, warehouseId: '01994300-0000-7000-8000-000000000002' };
    await settle();assert.equal(mounted.state.data.value.total, 0);
    finishOld(sample);await settle();assert.equal(mounted.state.data.value.total, 0);
  } finally { mounted.stop(); }
});
test('context-change failure removes the old comparison and requires a fresh search', async () => {
  let calls = 0;const mounted = mount(() => ++calls === 1 ? Promise.resolve({ ...sample, nextCursor: 'old', hasMore: true }) : Promise.reject(new Error('候选已变化，请重新查询')));
  try {
    await settle();mounted.state.next();await settle();
    assert.equal(mounted.state.data.value, null);assert.match(mounted.state.error.value, /重新查询/);
  } finally { mounted.stop(); }
});
test('invalid input never sends a request or displays a false empty-candidate result', async () => {
  let calls = 0;const mounted = mount(async () => { calls++;return sample; });
  try {
    await settle();mounted.state.from.value = '2026-02-30';mounted.state.search();await settle();
    assert.equal(calls, 1);assert.equal(mounted.state.data.value, null);assert.match(mounted.state.error.value, /有效/);
  } finally { mounted.stop(); }
});
