import assert from 'node:assert/strict';
import test from 'node:test';
import { isFaultWithdrawalEnabled } from '../src/orders/fault-withdrawal-feature.ts';

test('fault withdrawal requires an explicit true opt-in', () => {
  assert.equal(isFaultWithdrawalEnabled(undefined), false);
  assert.equal(isFaultWithdrawalEnabled('false'), false);
  assert.equal(isFaultWithdrawalEnabled('TRUE'), false);
  assert.equal(isFaultWithdrawalEnabled('true'), true);
});
