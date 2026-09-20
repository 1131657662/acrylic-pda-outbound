import { test, expect } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

const seed = z.object({ username: z.string(), password: z.string(), warehouse: z.string().uuid(), order: z.string().uuid() })
  .parse(JSON.parse(readFileSync(resolve('../.local/r08/integration/files-preview/preview.json'), 'utf8')));
const evidence = resolve('../.local/r08/integration/browser-evidence'); mkdirSync(evidence, { recursive: true });
test.beforeAll(async ({ request }) => {
  await expect.poll(async () => {
    try { return (await request.get('/api/v1/auth/csrf')).status(); } catch { return 0; }
  }, { timeout: 30000 }).toBe(200);
});
test.beforeEach(async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('textbox', { name: '账号' }).fill(seed.username);
  await page.getByLabel('密码', { exact: true }).fill(seed.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login/);
  await page.goto(`/orders/${seed.order}?warehouse=${seed.warehouse}&tab=exports`);
  await expect(page.getByRole('button', { name: '生成 Excel' })).toBeEnabled();
});
test('actual API, separate Worker, Excel and PDF downloads survive reload', async ({ page }) => {
  for (const format of ['Excel', '扫码记录 Excel', 'PDF']) {
    const accepted = page.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/exports?'));
    await page.getByRole('button', { name: format === '扫码记录 Excel' ? '生成扫码记录 Excel' : `生成 ${format}`, exact: true }).click();
    const id = z.object({ data: z.object({ id: z.string().uuid() }) }).parse(await (await accepted).json()).data.id;
    const row = page.locator(`[data-export-id="${id}"]`);
    await expect(row.getByText('已生成', { exact: true })).toBeVisible({ timeout: 60000 });
    const downloading = page.waitForEvent('download'); await row.getByRole('button', { name: '下载', exact: true }).click();
    const file = await downloading;
    expect(file.suggestedFilename()).toMatch(format === 'PDF' ? /\.pdf$/ : /\.xlsx$/);
    await file.saveAs(resolve(evidence, format === '扫码记录 Excel' ? 'web-scan-records.xlsx' : `web-original.${format === 'Excel' ? 'xlsx' : 'pdf'}`));
  }
  await page.reload(); await expect.poll(() => page.getByRole('button', { name: '下载', exact: true }).count()).toBeGreaterThanOrEqual(2);
  await page.screenshot({ path: resolve(evidence, 'desktop.png'), fullPage: true, animations: 'disabled' });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('button', { name: '生成 PDF' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: resolve(evidence, 'mobile.png'), fullPage: true, animations: 'disabled' });
  await page.locator('.export-row').first().scrollIntoViewIfNeeded();
  await page.screenshot({ path: resolve(evidence, 'mobile-files.png'), animations: 'disabled' });
});
test('lost POST response replays the same accepted task after reload', async ({ page }) => {
  let accepted = ''; let requestKey = '';
  await page.route('**/api/v1/orders/*/exports?*', async route => {
    if (route.request().method() !== 'POST') { await route.continue(); return; }
    requestKey = route.request().headers()['idempotency-key'] ?? '';
    const response = await route.fetch(); const body: unknown = await response.json();
    accepted = z.object({ data: z.object({ id: z.string().uuid() }) }).parse(body).data.id;
    await route.abort('failed');
  });
  await page.getByRole('button', { name: '生成 Excel', exact: true }).click();
  await expect(page.getByRole('button', { name: '重试原请求' })).toBeVisible();
  await page.unrouteAll({ behavior: 'wait' }); await page.reload();
  const request = page.waitForRequest(r => r.method() === 'POST' && r.url().includes('/exports?'));
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/exports?'));
  await page.getByRole('button', { name: '重试原请求' }).click();
  expect((await request).headers()['idempotency-key']).toBe(requestKey);
  expect(z.object({ data: z.object({ id: z.string() }) }).parse(await (await response).json()).data.id).toBe(accepted);
  await expect(page.getByRole('button', { name: '重试原请求' })).toHaveCount(0);
});
