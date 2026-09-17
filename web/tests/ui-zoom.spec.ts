import { chromium, test, expect } from '@playwright/test';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { noPageOverflow, queryOrders, uiLogin } from './ui-fixture';

test('native browser zoom at 200 percent keeps list, detail and return operable', async () => {
  const extension = resolve('tests/zoom-extension');
  const context = await chromium.launchPersistentContext('', { channel: 'chromium', headless: true,
    viewport: { width: 1440, height: 900 }, baseURL: process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:5174',
    args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`] });
  try {
    const worker = context.serviceWorkers()[0] ?? await context.waitForEvent('serviceworker');
    const page = context.pages()[0] ?? await context.newPage();
    await uiLogin(page); await queryOrders(page);
    const before = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio }));
    const zoom = await worker.evaluate(async () => {
      const api = (globalThis as unknown as { chrome: { tabs: {
        query(options: { url: string }): Promise<{ id: number }[]>;
        setZoom(id: number, value: number): Promise<void>; getZoom(id: number): Promise<number>;
      } } }).chrome.tabs;
      const tabs = await api.query({ url: 'http://127.0.0.1/*' }); const id = tabs[0]!.id;
      await api.setZoom(id, 2); return api.getZoom(id);
    });
    expect(zoom).toBe(2);
    await expect.poll(() => page.evaluate(() => innerWidth)).toBe(before.width / 2);
    const after = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio, visualScale: visualViewport?.scale }));
    expect(after.dpr).toBe(before.dpr * 2); expect(after.visualScale).toBe(1);
    writeFileSync('../.local/ui-orders-zoom-evidence.json', JSON.stringify({ zoom, before, after }, null, 2));
    await noPageOverflow(page);
    await page.screenshot({ path: '../.local/ui-orders-200-percent.png', animations: 'disabled' });
    await page.getByRole('button', { name: '下一页', exact: true }).click();
    await expect(page).toHaveURL(/page=2/);
    await page.getByRole('link', { name: /^OB/ }).first().click();
    await expect(page.getByRole('tab', { name: '产品计划', exact: true })).toBeVisible();
    await noPageOverflow(page);
    await page.screenshot({ path: '../.local/ui-detail-200-percent.png', animations: 'disabled' });
    await page.getByRole('tab', { name: '装车记录', exact: true }).click();
    await expect(page.getByRole('heading', { name: '后台收到的装车记录', exact: true })).toBeVisible();
    await page.getByRole('button', { name: '返回订单列表', exact: true }).click();
    await expect(page).toHaveURL(/page=2/);
  } finally { await context.close(); }
});
