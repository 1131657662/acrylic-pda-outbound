import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { temporaryReviewSchema, temporaryReviewItemSchema, temporaryScanSchema } from '../src/temporary-review/contracts.ts';

globalThis.window = { innerWidth: 1440 };
const { navigation, canNavigate, firstWorkspacePath } = await import('../src/navigation.ts');
const temporary = navigation.flatMap(group => group.items).find(item => item.path === '/temporary-reviews');
const reminders = navigation.flatMap(group => group.items).find(item => item.path === '/review-reminders');
const orders = navigation.flatMap(group => group.items).find(item => item.path === '/orders');

for (const [name, permissions, expected] of [
  ['temporary only', ['temporary_order:confirm'], [true, true, false]],
  ['formal only', ['submission:review'], [false, true, false]],
  ['both', ['submission:review', 'temporary_order:confirm'], [true, true, false]],
  ['neither', ['order:read'], [false, false, true]],
]) {
  test(`navigation and direct-page authorization: ${name}`, () => {
    assert.deepEqual([temporary, reminders, orders].map(item => canNavigate(item, permissions)), expected);
  });
}
test('confirmation-only identity lands on the independent inbox', () => {
  assert.equal(firstWorkspacePath(['temporary_order:confirm']), '/temporary-reviews');
});
test('actual MockMvc responses satisfy the web contracts', async () => {
  const sample = JSON.parse(await readFile(new URL('../../server/build/temporary-review-contract.json', import.meta.url), 'utf8'));
  assert.equal(temporaryReviewSchema.parse(sample.detail).releaseAllowed, false);
  sample.page.items.forEach(row => temporaryReviewItemSchema.parse(row));
  sample.scans.items.forEach(row => temporaryScanSchema.parse(row));
  assert.equal(sample.scans.items.length, 2);
});
test('a response that claims write confirmation or release is rejected', async () => {
  const sample = JSON.parse(await readFile(new URL('../../server/build/temporary-review-contract.json', import.meta.url), 'utf8'));
  assert.equal(temporaryReviewSchema.safeParse({ ...sample.detail, releaseAllowed: true }).success, false);
  assert.equal(temporaryReviewSchema.safeParse({ ...sample.detail, writeConfirmationAvailable: true }).success, false);
});
