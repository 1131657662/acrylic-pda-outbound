import { test, expect, type Page } from '@playwright/test';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { z } from 'zod';

const seed = z.object({ username: z.string(), password: z.string(), warehouse: z.string().uuid() })
  .parse(JSON.parse(readFileSync(resolve('../.local/r08/integration/files-preview/preview.json'), 'utf8')));
const evidence = resolve('../.local/r08/integration/browser-evidence'); mkdirSync(evidence, { recursive: true });
const acceptedSchema = z.object({ data: z.object({ id: z.string().uuid(), snapshotId: z.string() }) });
async function summary(page: Page): Promise<void> {
  const response = page.waitForResponse(r => r.url().includes('/exports/summary?'));
  await page.getByRole('button', { name: '查询汇总', exact: true }).click();
  expect((await response).status()).toBe(200);
  await expect(page.getByRole('button', { name: '导出汇总 Excel' })).toBeEnabled();
}
test.beforeEach(async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('textbox', { name: '账号' }).fill(seed.username);
  await page.getByLabel('密码', { exact: true }).fill(seed.password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login/);
  await page.goto(`/exports?warehouse=${seed.warehouse}&fromDate=2026-09-20&throughDate=2026-09-20`);
  await expect(page.getByRole('heading', { name: '跨订单原确认汇总' })).toBeVisible();
});
test('center summary generates fixed files and retries the accepted snapshot', async ({ page }) => {
  await summary(page);
  const results: unknown[] = [];
  for (const format of ['Excel', 'PDF']) {
    const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/exports?'));
    await page.getByRole('button', { name: `导出汇总 ${format}` }).click();
    const original = acceptedSchema.parse(await (await response).json()).data; results.push(original);
    const row = page.locator(`[data-export-id="${original.id}"]`);
    await expect(row.getByText('已生成', { exact: true })).toBeVisible({ timeout: 60000 });
    const downloading = page.waitForEvent('download'); await row.getByRole('button', { name: '下载', exact: true }).click();
    await (await downloading).saveAs(resolve(evidence, `center-summary.${format === 'Excel' ? 'xlsx' : 'pdf'}`));
    if (format !== 'Excel') continue;
    const retried = page.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/retry?'));
    await row.getByRole('button', { name: '按原快照重生成' }).click();
    const next = acceptedSchema.parse(await (await retried).json()).data;
    expect(next.snapshotId).toBe(original.snapshotId); expect(next.id).not.toBe(original.id);
    await expect(page.locator(`[data-export-id="${next.id}"]`).getByText('已生成', { exact: true })).toBeVisible({ timeout: 60000 });
  }
  writeFileSync(resolve(evidence, 'center-accepted.json'), JSON.stringify(results, null, 2));
  await page.getByLabel('开始日期', { exact: true }).fill('2026-09-19');
  await expect(page.getByRole('button', { name: '导出汇总 Excel' })).toBeDisabled();
  await page.screenshot({ path: resolve(evidence, 'center-desktop.png'), fullPage: true, animations: 'disabled' });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: resolve(evidence, 'center-mobile.png'), fullPage: true, animations: 'disabled' });
});
test('lost center acceptance reuses the original key and range after reload', async ({ page }) => {
  await summary(page); let id = ''; let key = ''; let body = '';
  await page.route('**/api/v1/exports?*', async route => {
    if (route.request().method() !== 'POST') { await route.continue(); return; }
    key = route.request().headers()['idempotency-key'] ?? ''; body = route.request().postData() ?? '';
    const response = await route.fetch(); id = acceptedSchema.parse(await response.json()).data.id; await route.abort('failed');
  });
  await page.getByRole('button', { name: '导出汇总 Excel' }).click();
  await expect(page.getByRole('button', { name: '重试原申请' })).toBeVisible();
  await page.unrouteAll({ behavior: 'wait' }); await page.reload();
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/exports?'));
  await page.getByRole('button', { name: '重试原申请' }).click(); const replay = await response;
  expect(replay.request().headers()['idempotency-key']).toBe(key); expect(replay.request().postData()).toBe(body);
  expect(acceptedSchema.parse(await replay.json()).data.id).toBe(id);
  await expect(page.getByRole('button', { name: '重试原申请' })).toHaveCount(0);
});
test('center applies the selected format before listing tasks', async ({ page }) => {
  await page.locator('.center-filters .el-select').first().click();
  await page.getByRole('option', { name: 'PDF', exact: true }).click();
  const response = page.waitForResponse(r => r.url().includes('/exports?') && r.request().method() === 'GET');
  await page.getByRole('button', { name: '查询任务' }).click();
  expect(new URL((await response).url()).searchParams.get('format')).toBe('PDF');
  for (const label of await page.locator('.center-job strong').allTextContents()) expect(label).toContain('PDF');
});
