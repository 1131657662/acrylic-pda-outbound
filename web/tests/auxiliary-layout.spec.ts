import { test, expect, chromium, type Page } from '@playwright/test';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { uiLogin, uiFixture, noPageOverflow } from './ui-fixture';

async function inspectPages(page: Page, suffix: string): Promise<void> {
  for (const [path, name] of [['/inventory/bales', 'stock'], ['/inventory/imports', 'imports'],
    ['/inventory/mappings', 'mappings'], ['/policies', 'policies'], ['/devices', 'devices']]) {
    await page.goto(`${path}?warehouse=${uiFixture().warehouseId}`);
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0);
    await expect(page.getByRole('button', { name: '重试查询' })).toHaveCount(0);
    await noPageOverflow(page);
    await page.screenshot({ path: `../.local/r04-${name}-${suffix}.png`, animations: 'disabled' });
    if (name === 'imports') {
      await page.getByRole('button', { name: '查看', exact: true }).first().click();
      await expect(page.locator('.detail-panel')).toBeVisible();
      await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0); await noPageOverflow(page);
      await page.screenshot({ path: `../.local/r04-import-detail-${suffix}.png`, animations: 'disabled' });
      await page.getByRole('button', { name: '返回导入记录' }).click();
    }
    if (name === 'mappings') {
      await page.getByRole('button', { name: '历史', exact: true }).first().click();
      const drawer = page.getByRole('dialog'); await expect(drawer).toBeVisible();
      await expect(drawer.locator('.el-loading-mask:visible')).toHaveCount(0);
      expect((await drawer.boundingBox())!.width).toBeLessThanOrEqual(await page.evaluate(() => innerWidth));
      await page.screenshot({ path: `../.local/r04-mapping-history-${suffix}.png`, animations: 'disabled' });
      await page.keyboard.press('Escape'); await expect(drawer).toBeHidden();
    }
  }
}

test('auxiliary pages remain reachable at desktop low and narrow heights', async ({ page }) => {
  test.setTimeout(90000); await uiLogin(page);
  for (const [width, height] of [[1440, 900], [1280, 500], [390, 700]]) {
    await page.setViewportSize({ width: width!, height: height! });
    await inspectPages(page, `${width}x${height}`);
  }
});

test('auxiliary pages at native 200 percent browser zoom', async () => {
  test.setTimeout(90000);
  const extension = resolve('tests/zoom-extension');
  const context = await chromium.launchPersistentContext('', { channel: 'chromium', headless: true,
    viewport: { width: 1440, height: 900 }, baseURL: process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:5177',
    args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`] });
  try {
    const worker = context.serviceWorkers()[0] ?? await context.waitForEvent('serviceworker');
    const page = context.pages()[0] ?? await context.newPage(); await uiLogin(page);
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
    await inspectPages(page, 'zoom200');
    const after = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio }));
    expect(after.dpr).toBe(before.dpr * 2);
    writeFileSync('../.local/r04-native-zoom.json', JSON.stringify({ zoom, before, after }, null, 2));
  } finally { await context.close(); }
});
