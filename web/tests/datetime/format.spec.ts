import { test, expect } from '@playwright/test';
import { formatDateTime } from '../../src/shared/date-time';
import { localTime as orderTime } from '../../src/orders/contracts';
import { localTime as inventoryTime } from '../../src/inventory/contracts';

for (const [input, expected] of [
  ['2026-09-20T04:37:05Z', '2026-09-20 12:37:05'],
  ['2026-09-20T04:37:05.987654321Z', '2026-09-20 12:37:05'],
  ['2026-09-20T12:37:05+08:00', '2026-09-20 12:37:05'],
  ['2026-09-19T21:37:05-07:00', '2026-09-20 12:37:05'],
  ['2026-09-19T16:00:00Z', '2026-09-20 00:00:00'],
  ['2026-12-31T16:00:00Z', '2027-01-01 00:00:00'],
  ['2024-02-29T16:02:03.999Z', '2024-03-01 00:02:03'],
  ['2026-01-01T17:02:03Z', '2026-01-02 01:02:03'],
] as const) {
  test(`timestamp ${input} displays ${expected}`, () => {
    expect(formatDateTime(input)).toBe(expected);
    expect(orderTime(input)).toBe(expected);
    expect(inventoryTime(input)).toBe(expected);
  });
}

for (const input of [null, undefined, '', ' ', 'invalid', '2026-09-20', '2026-09-20T12:37:05',
  '2026-02-29T00:00:00Z', '2026-04-31T00:00:00Z', '2026-13-20T00:00:00Z',
  '2026-09-20T24:00:00Z', '2026-09-20T04:37:60Z', '2026-09-20T04:37:05+18:01']) {
  test(`missing or invalid timestamp ${String(input)} is not a fabricated time`, () => {
    expect(formatDateTime(input)).toBe('暂无记录');
    expect(orderTime(input)).toBe('暂无记录');
    expect(inventoryTime(input)).toBe('暂无记录');
  });
}
