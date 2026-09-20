import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { registerHooks } from 'node:module';
import test from 'node:test';

// Node's native TypeScript runner needs the extension that the Vite source imports omit.
registerHooks({ resolve(specifier, context, nextResolve) {
  if (specifier === './contracts' && context.parentURL?.endsWith('/temporary-review/duplicate-contracts.ts'))
    return nextResolve('./contracts.ts', context);
  return nextResolve(specifier, context);
} });
const { duplicatePageSchema, duplicateRangeError, duplicateReason } = await import('../src/temporary-review/duplicate-contracts.ts');
const sample = JSON.parse(await readFile(new URL('../../server/build/temporary-duplicate-contract.json', import.meta.url), 'utf8'));

test('actual MySQL/MockMvc response preserves the original source and only offers comparison', () => {
  const response = duplicatePageSchema.parse(sample);
  assert.equal(response.total, 1);
  assert.equal(response.context.source.kind, 'TEMP');
  assert.equal(response.items[0].products.length, 2);
  assert.equal(response.items[0].linkAvailable, false);
  assert.equal(response.writeAvailable, false);
  assert.equal(response.releaseAllowed, false);
});
test('the client rejects a response that claims association, write confirmation or release', () => {
  assert.equal(duplicatePageSchema.safeParse({ ...sample, writeAvailable: true }).success, false);
  assert.equal(duplicatePageSchema.safeParse({ ...sample, releaseAllowed: true }).success, false);
  assert.equal(duplicatePageSchema.safeParse({ ...sample, items: [{ ...sample.items[0], linkAvailable: true }] }).success, false);
});
test('null current submission remains valid historical context', () => {
  const response = duplicatePageSchema.parse({ ...sample, context: { ...sample.context, currentSubmissionId: null, current: false } });
  assert.equal(response.context.current, false);
});
for (const [from, through] of [['', ''], ['2026-02-30', '2026-03-01'], ['2026-09-20', '2026-09-19'],
  ['1999-01-01', '1999-01-02'], ['2026-01-01', '2027-01-02'], ['2026-09-20', 'bad']]) {
  test(`invalid date scope is rejected: ${from} / ${through}`, () => assert.notEqual(duplicateRangeError(from, through), ''));
}
test('a range wider than 48 hours is valid; 366 days is a query bound', () => {
  assert.equal(duplicateRangeError('2026-09-10', '2026-09-20'), '');
  assert.equal(duplicateRangeError('2024-01-01', '2024-12-31'), '');
});
test('working and successful candidates explain why association is unavailable', () => {
  assert.match(duplicateReason('PDA_WORK_EXISTS'), /PDA/);
  assert.match(duplicateReason('PC_WORK_EXISTS'), /PC/);
  assert.match(duplicateReason('RESULT_EXISTS'), /不能关联成功事实/);
});
