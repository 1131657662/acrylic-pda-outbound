import { expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { z } from 'zod';
import { quarantineEvidence } from './quarantine-fixture';
export async function quarantineHistoryReview(page: Page): Promise<void> {
  if (!process.env.QUARANTINE_HISTORY_ATTEMPT) return;
  const originals = `${process.env.QUARANTINE_HISTORY_ATTEMPT}/accounting/returned-quarantine-history`;
  await expect(page.getByRole('region', { name: '历史出库与返库依据' })).toBeVisible();
  await expect(page.getByText('原周期 1', { exact: true })).toBeVisible();
  for (const [name, file] of [['下载原退货或撤销批准回执（周期1）', 'return-original'],
    ['下载原库存恢复批准回执（周期1）', 'restoration-original']] as const) {
    const downloaded = page.waitForEvent('download');
    await page.getByRole('button', { name, exact: true }).click();
    const download = await downloaded; const path = await download.path();
    expect(path).not.toBeNull(); if (!path) throw new Error('Original download absent');
    const actual = readFileSync(path); const receipt = z.object({ commandId: z.uuid() }).parse(JSON.parse(actual.toString()));
    const original = readFileSync(`${originals}/${receipt.commandId}-receipt.json`);
    expect(actual).toEqual(original); quarantineEvidence(`${file}.json`, actual);
  }
  const region = page.getByRole('region', { name: '历史出库与返库依据' });
  await expect(region).toContainText('0 包 / 0 克'); await expect(region).toContainText('实物退货');
  expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(false);
  await region.scrollIntoViewIfNeeded();
  await page.screenshot({ path: `${process.env.QUARANTINE_WEB_EVIDENCE}/mobile-history.png` });
}
