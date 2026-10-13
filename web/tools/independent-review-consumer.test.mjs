import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { reviewSchema, matchesOriginal } from '../src/independent-review/contracts.ts';

const evidence = process.env.INDEPENDENT_REVIEW_EVIDENCE;
if (!evidence) throw new Error('Requires actual isolated HTTP evidence');
const load = name => JSON.parse(readFileSync(`${evidence}/${name}`, 'utf8'));
const result = load('result.json');
const originalPage = result.final ?? load('candidates.json');
test('actual server candidate response is consumable without granting capabilities', () => {
  const data = reviewSchema.parse(originalPage);
  assert.equal(matchesOriginal(data, { relayId: data.original.relayId, warehouseId: data.original.warehouseId, section: 'candidates' }), true);
  assert.equal(data.associationAuthorized, false); assert.equal(data.stockAvailable, false);
});
test('actual original inventory response preserves unverified physical status', () => {
  const raw = load('inventory.json'); const data = reviewSchema.parse(raw);
  assert.equal(data.section, 'inventory'); assert.equal(data.summary.physicalVerified, false);
  for (const [index, item] of data.items.entries()) {
    if (!item.server) continue;
    for (const field of ['deliveryCode', 'model', 'name', 'weightGrams', 'positionVersion', 'upstreamPresent', 'positionSnapshotId', 'commitmentCycle']) {
      assert.deepEqual(item.server[field], raw.items[index].server[field]);
    }
  }
});
test('actual peer response retains unknown origin and cross device conflict', () => {
  const raw = load('peers.json'); const data = reviewSchema.parse(raw.after ?? raw);
  assert.equal(data.section, 'peers'); assert.equal(data.items.some(item => item.crossDeviceConflict), true);
});
test('altered capability and scope are rejected before display', () => {
  assert.equal(reviewSchema.safeParse({ ...originalPage, localDeletionAllowed: true }).success, false);
  assert.equal(matchesOriginal(reviewSchema.parse(originalPage), { relayId: crypto.randomUUID(),
    warehouseId: originalPage.original.warehouseId, section: 'candidates' }), false);
});
