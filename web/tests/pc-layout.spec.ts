import { test, expect, chromium } from '@playwright/test';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { addPc, pcFixture, pcLogin, startPc } from './pc-fixture';
import { noPageOverflow } from './ui-fixture';

test('PC candidates and confirmation stay reachable at desktop, narrow and low heights', async ({ page }) => {
  test.setTimeout(90000); const f = pcFixture(5); await pcLogin(page, f); await startPc(page, f);
  await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.query);
  await expect(page.locator('.pc-candidate-group')).toHaveCount(3);
  const measurements: object[] = [];
  for (const [width, height] of [[1366, 768], [1440, 900], [1024, 768], [1280, 500], [390, 844]]) {
    await page.setViewportSize({ width: width!, height: height! }); await noPageOverflow(page);
    await page.locator('.page-scroll').evaluate(el => { el.scrollTop = 0; });
    await page.screenshot({ path: `../.local/pc-outbound-${width}x${height}.png`, animations: 'disabled' });
    const radio = page.locator('.el-radio').filter({ hasText: f.barcodes[0] });
    await radio.click(); const confirm = page.getByRole('button', { name: '确认计入本单', exact: true });
    await confirm.scrollIntoViewIfNeeded(); await expect(confirm).toBeInViewport(); await expect(confirm).toBeEnabled();
    const rectangle = await confirm.boundingBox(); expect(rectangle!.height).toBeGreaterThanOrEqual(36);
    measurements.push({ width, height, confirmation: rectangle });
    await page.getByRole('button', { name: '结单核对', exact: true }).scrollIntoViewIfNeeded();
  }
  writeFileSync('../.local/pc-layout-evidence.json', JSON.stringify(measurements, null, 2));
  await page.setViewportSize({ width: 390, height: 844 }); await addPc(page, f.barcodes[0]!);
  await page.getByRole('button', { name: '结单核对', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: '返回核对', exact: true }).click();
});

test('native 200 percent PC zoom supports package selection and confirmation', async () => {
  const extension = resolve('tests/zoom-extension');
  const context = await chromium.launchPersistentContext('', { channel: 'chromium', headless: true,
    viewport: { width: 1440, height: 900 }, baseURL: process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:5175',
    args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`] });
  try {
    const worker = context.serviceWorkers()[0] ?? await context.waitForEvent('serviceworker');
    const page = context.pages()[0] ?? await context.newPage(); const f = pcFixture(6);
    await pcLogin(page, f); await startPc(page, f);
    const before = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio }));
    const zoom = await worker.evaluate(async () => {
      const api = (globalThis as unknown as { chrome: { tabs: {
        query(options: { url: string }): Promise<{ id: number }[]>;
        setZoom(id: number, value: number): Promise<void>; getZoom(id: number): Promise<number>;
      } } }).chrome.tabs;
      const id = (await api.query({ url: 'http://127.0.0.1/*' }))[0]!.id;
      await api.setZoom(id, 2); return api.getZoom(id);
    });
    expect(zoom).toBe(2); await expect.poll(() => page.evaluate(() => innerWidth)).toBe(before.width / 2);
    await page.getByLabel('完整条码或条码片段', { exact: true }).fill(f.query); await expect(page.locator('.pc-candidate-group')).toHaveCount(3);
    await noPageOverflow(page); await page.locator('.page-scroll').evaluate(el => { el.scrollTop = 0; });
    await page.screenshot({ path: '../.local/pc-outbound-200-percent.png', animations: 'disabled' });
    const after = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio })); expect(after.dpr).toBe(before.dpr * 2);
    writeFileSync('../.local/pc-zoom-evidence.json', JSON.stringify({ zoom, before, after }, null, 2));
    await addPc(page, f.barcodes[0]!);
  } finally { await context.close(); }
});
