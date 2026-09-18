import { test, expect, chromium } from '@playwright/test';
import { resolve } from 'node:path';
import { writeFileSync } from 'node:fs';
import { noPageOverflow } from './ui-fixture';
import { exceptionFixture, exceptionLogin, stopException, fillExceptionPlan } from './pc-exception-fixture';

test('PC exception manifest and confirmation remain reachable at desktop narrow and low heights', async ({ page }) => {
  test.setTimeout(90000); const f = exceptionFixture(5); await exceptionLogin(page, f); await stopException(page); await fillExceptionPlan(page, f);
  const measurements: object[] = [];
  for (const [width, height] of [[1366, 768], [1440, 900], [1024, 768], [1280, 500], [390, 844]]) {
    await page.setViewportSize({ width: width!, height: height! }); await noPageOverflow(page);
    await page.locator('.page-scroll').evaluate(el => { el.scrollTop = 0; });
    await page.screenshot({ path: `../.local/pc-exception-${width}x${height}.png`, animations: 'disabled' });
    const submit = page.getByRole('button', { name: '提交处置方案', exact: true }); await submit.scrollIntoViewIfNeeded();
    await expect(submit).toBeInViewport(); await expect(submit).toBeEnabled(); const rectangle = await submit.boundingBox();
    expect(rectangle!.height).toBeGreaterThanOrEqual(36); measurements.push({ width, height, confirmation: rectangle });
  }
  await page.screenshot({ path: '../.local/pc-exception-narrow-confirm.png', animations: 'disabled' });
  await page.getByRole('button', { name: '提交处置方案', exact: true }).click();
  await page.getByRole('button', { name: '批准处置方案', exact: true }).click();
  await page.setViewportSize({ width: 1280, height: 500 }); const dialog = page.getByRole('dialog', { name: '批准处置方案' });
  await dialog.getByLabel('现场核验记录与原因').fill('低高度下核对全部实物，确认按钮必须可达');
  await dialog.getByText('已核对本单、全部实物及人员交接影响').click();
  const approve = dialog.getByRole('button', { name: '批准处置方案', exact: true }); await approve.scrollIntoViewIfNeeded(); await expect(approve).toBeInViewport();
  await page.screenshot({ path: '../.local/pc-exception-low-dialog.png', animations: 'disabled' }); await approve.click();
  await expect(page.locator('.pc-exception-plan')).toContainText('已批准，待本人交接');
  writeFileSync('../.local/pc-exception-layout-evidence.json', JSON.stringify(measurements, null, 2));
});

test('PC exception native 200 percent zoom supports actual proposal and approval dialog', async () => {
  const extension = resolve('tests/zoom-extension');
  const context = await chromium.launchPersistentContext('', { channel: 'chromium', headless: true, viewport: { width: 1440, height: 900 },
    baseURL: process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:5177', args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`] });
  try {
    const worker = context.serviceWorkers()[0] ?? await context.waitForEvent('serviceworker');
    const page = context.pages()[0] ?? await context.newPage(); const f = exceptionFixture(6); await exceptionLogin(page, f); await stopException(page);
    const before = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio }));
    const zoom = await worker.evaluate(async () => {
      const api = (globalThis as unknown as { chrome: { tabs: {
        query(options: { url: string }): Promise<{ id: number }[]>;
        setZoom(id: number, value: number): Promise<void>; getZoom(id: number): Promise<number>;
      } } }).chrome.tabs;
      const id = (await api.query({ url: 'http://127.0.0.1/*' }))[0]!.id; await api.setZoom(id, 2); return api.getZoom(id);
    });
    expect(zoom).toBe(2); await expect.poll(() => page.evaluate(() => innerWidth)).toBe(before.width / 2);
    await fillExceptionPlan(page, f); await noPageOverflow(page);
    await page.getByRole('button', { name: '提交处置方案', exact: true }).scrollIntoViewIfNeeded();
    await page.screenshot({ path: '../.local/pc-exception-200-percent.png', animations: 'disabled' });
    const after = await page.evaluate(() => ({ width: innerWidth, dpr: devicePixelRatio })); expect(after.dpr).toBe(before.dpr * 2);
    await page.getByRole('button', { name: '提交处置方案', exact: true }).click(); await page.getByRole('button', { name: '批准处置方案', exact: true }).click();
    const back = page.getByRole('button', { name: '返回核对', exact: true }); await back.scrollIntoViewIfNeeded(); await expect(back).toBeInViewport(); await back.click();
    writeFileSync('../.local/pc-exception-zoom-evidence.json', JSON.stringify({ zoom, before, after }, null, 2));
  } finally { await context.close(); }
});
