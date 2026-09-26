import { test, expect } from '@playwright/test';
import { mockRelease, releaseFixture } from '../pda-download-fixture';
import { id, mockPages, stamp } from './page-fixture';

test('download publication time is Shanghai time after signature verification', async ({ page }, info) => {
  const fixture = releaseFixture({ publishedAt: stamp });
  fixture.config.origin = 'https://127.0.0.1:5197';
  await mockRelease(page, fixture);

  await page.goto('/pda-download');

  await expect(page.locator('.download-facts')).toContainText('2026-09-20 12:37:05');
  await expect(page.locator('.download-facts')).not.toContainText(stamp);
  await page.setViewportSize({ width: 360, height: 800 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('download-mobile.png'), fullPage: true });
});

test('order table and mobile rows display seconds without changing their order', async ({ page }, info) => {
  const unexpected = await mockPages(page);
  await page.goto('/orders');

  const rows = page.locator('.el-table__body tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(0)).toContainText('2026-09-20 12:37:05');
  await expect(rows.nth(1)).toContainText('2026-09-20 00:00:00');
  await page.screenshot({ path: info.outputPath('orders-desktop.png'), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByText('2026-09-20 12:37:05', { exact: true }).last()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('orders-mobile.png'), fullPage: true });
  expect(unexpected).toEqual([]);
});

for (const path of ['/inventory/imports', '/devices', '/review-reminders']) {
  test(`${path} uses the shared date time display`, async ({ page }) => {
    const unexpected = await mockPages(page);
    await page.goto(path);
    await expect(page.locator('.el-table__body tr').first()).toContainText('2026-09-20 12:37:05');
    expect(unexpected).toEqual([]);
  });
}

test('continuation expiry displays Shanghai seconds while retaining the API timestamp', async ({ page }) => {
  const unexpected = await mockPages(page);
  const response = page.waitForResponse(url => new URL(url.url()).pathname.endsWith('/change-requests'));
  await page.goto(`/orders/${id}?tab=exceptions`);

  await expect(page.getByText(/^新资格截止/)).toHaveText('新资格截止 2026-09-20 12:37:05，原 PDA 须联网核验并激活后续扫。');
  expect((await response).ok()).toBe(true);
  expect(await (await response).text()).toContain(stamp);
  expect(unexpected).toEqual([]);
});
